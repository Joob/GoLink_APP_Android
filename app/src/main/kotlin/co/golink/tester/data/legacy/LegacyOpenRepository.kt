package co.golink.tester.data.legacy

import co.golink.tester.data.download.FileDownloader
import co.golink.tester.data.encryption.CalendarCrypto
import co.golink.tester.data.encryption.EncryptedFileCodec
import co.golink.tester.domain.legacy.LegacyInviteDto
import co.golink.tester.domain.legacy.LegacyOpenDto
import co.golink.tester.domain.legacy.LegacyOpenItemDto
import co.golink.tester.network.LegacyApi
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Um item aberto com o código: payload null = ainda bloqueado ou [unreadable]. */
data class LegacyOpenedItem(val dto: LegacyOpenItemDto, val payload: JsonObject?, val key: ByteArray?, val unreadable: Boolean = false)

/**
 * Páginas públicas do Legacy (sem sessão): convite do guardião, check-in do email e
 * acesso do recipient. O código nunca sai do aparelho; os ficheiros chegam cifrados.
 */
@Singleton
class LegacyOpenRepository @Inject constructor(
    private val api: LegacyApi,
    private val downloader: FileDownloader,
) {
    suspend fun invite(id: String): LegacyInviteDto? = withContext(Dispatchers.IO) {
        runCatching { api.publicInvite(id).body()?.data }.getOrNull()
    }

    /** "counted" | "not_needed" | "expired" | "failed". */
    suspend fun checkin(token: String): String = withContext(Dispatchers.IO) {
        val response = runCatching { api.publicCheckin(token) }.getOrNull() ?: return@withContext "failed"
        when {
            response.isSuccessful -> if (response.body()?.counted == true) "counted" else "not_needed"
            response.code() == 410 -> "expired"
            else -> "failed"
        }
    }

    /** null = link expirado ou inválido (404). Rede ou 5xx lançam: o ecrã deixa tentar de novo. */
    suspend fun vault(token: String): LegacyOpenDto? = withContext(Dispatchers.IO) {
        val response = api.publicOpen(token)
        if (response.code() == 404) null else response.legacyBody().data
    }

    /**
     * Item a item: um estragado não esconde os outros. Lança (código errado) só se
     * nenhum dos que já podiam abrir abriu.
     */
    fun unlock(vault: LegacyOpenDto, code: String): List<LegacyOpenedItem> {
        val normalized = LegacyCrypto.normalizeCode(code) ?: throw LegacyException("legacy_bad_code")
        val recipientKey = LegacyCrypto.deriveRecipientKey(normalized, vault.recipient_id)
        val opened = vault.items.map { item ->
            if (item.locked || item.wrapped_key == null || item.payload_enc == null) return@map LegacyOpenedItem(item, null, null)
            runCatching {
                val key = CalendarCrypto.unwrapKey(item.wrapped_key, recipientKey)
                LegacyOpenedItem(item, LegacyCrypto.decryptJson(item.payload_enc, key), key)
            }.getOrElse { LegacyOpenedItem(item, null, null, unreadable = true) }
        }
        val openable = opened.filter { !it.dto.locked && it.dto.wrapped_key != null }
        if (openable.isNotEmpty() && openable.all { it.unreadable }) throw LegacyException("legacy_bad_code")
        return opened
    }

    private data class FileRow(val id: String, val path: String, val mime: String?, val size: Long, val dataKey: ByteArray)

    private suspend fun fileRows(token: String, item: LegacyOpenedItem): List<FileRow> {
        val kItem = item.key ?: throw LegacyException("legacy_item_locked")
        val rows = mutableListOf<FileRow>()
        var page = 1
        while (true) {
            val body = api.publicFiles(token, item.dto.id, page).body() ?: throw LegacyException("legacy_files_failed")
            body.data.forEach { r ->
                val meta = LegacyCrypto.decryptJson(r.meta_enc, kItem)
                val str = { k: String -> (meta[k] as? JsonPrimitive)?.takeIf { it.isString }?.content }
                rows += FileRow(
                    r.file_id,
                    str("path") ?: str("name") ?: r.file_id,
                    str("mime"),
                    r.filesize?.longOrNull ?: r.filesize?.content?.toLongOrNull() ?: 0L,
                    CalendarCrypto.unwrapKey(r.wrapped_key, kItem),
                )
            }
            if (!body.has_more) break
            page++
        }
        return rows
    }

    /** Conta os bytes decifrados (o progresso é sobre o tamanho em claro, como na web). */
    private class Counting(out: OutputStream, private val onBytes: (Long) -> Unit) : FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            onBytes(len.toLong())
        }
        override fun write(b: Int) {
            out.write(b)
            onBytes(1)
        }
        // O ZipOutputStream fecha-se à parte: cada ficheiro só fecha a entrada.
        override fun close() = flush()
    }

    private suspend fun decryptInto(token: String, itemId: String, row: FileRow, out: OutputStream) {
        val response = api.publicDownload(token, itemId, row.id)
        if (!response.isSuccessful) throw LegacyException("legacy_download_${response.code()}", response.code())
        response.body()!!.byteStream().use { EncryptedFileCodec.decryptStream(it, out, row.dataKey) }
    }

    /** Grava em Downloads: um ficheiro tal como é, uma pasta (ou vários) num .zip. @return nome gravado. */
    suspend fun download(token: String, item: LegacyOpenedItem, onProgress: (Long, Long) -> Unit): String = withContext(Dispatchers.IO) {
        val rows = fileRows(token, item)
        val total = rows.sumOf { it.size }
        var received = 0L
        var reportedPct = -1L
        var reportedAt = 0L
        // Um aviso por ponto percentual ou a cada 250 ms: por bloco eram dezenas de milhares.
        val count = { n: Long ->
            received += n
            val pct = if (total > 0) received * 100 / total else 0
            val now = android.os.SystemClock.elapsedRealtime()
            if (pct != reportedPct || now - reportedAt > 250) {
                reportedPct = pct
                reportedAt = now
                onProgress(received, total)
            }
        }
        val baseName = (item.payload?.get("name") as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: "legacy"
        if (item.dto.type == "file" && rows.size == 1) {
            val row = rows[0]
            downloader.writeToDownloads(baseName, row.mime ?: "application/octet-stream") { out ->
                decryptInto(token, item.dto.id, row, Counting(out, count))
            }
            baseName
        } else {
            val name = "$baseName.zip"
            downloader.writeToDownloads(name, "application/zip") { out ->
                val zip = ZipOutputStream(out)
                val used = HashSet<String>()
                rows.forEach { row ->
                    zip.putNextEntry(ZipEntry(uniquePath(row.path, used)))
                    decryptInto(token, item.dto.id, row, Counting(zip, count))
                    zip.closeEntry()
                }
                zip.finish()
            }
            name
        }
    }

    private fun uniquePath(path: String, used: HashSet<String>): String {
        if (used.add(path)) return path
        val dot = path.lastIndexOf('.')
        val base = if (dot > 0) path.substring(0, dot) else path
        val ext = if (dot > 0) path.substring(dot) else ""
        var i = 1
        while (!used.add("$base ($i)$ext")) i++
        return "$base ($i)$ext"
    }
}
