package co.golink.tester.data.backup

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import co.golink.tester.data.AppLogger
import co.golink.tester.data.upload.UploadManager
import co.golink.tester.domain.backup.BackupKeysCheckBody
import co.golink.tester.network.BrowseApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Ficheiros que o scan por cursor não vê: movidos para uma pasta do backup ou
 * editados no próprio ficheiro mantêm o `_ID` (abaixo do cursor).
 *
 * Por pasta guarda-se um índice local do que já foi enviado (id → assinatura
 * de nome+tamanho, os mesmos campos da upload_key). De tempos a tempos a pasta
 * é relida até ao cursor e o que não está no índice, ou mudou de assinatura,
 * é candidato. Antes de enviar bytes pergunta-se ao servidor que upload_keys
 * já existem (POST mobile-backup/check); só as outras entram no conjunto de
 * falhas da pasta, que o worker já retoma.
 *
 * Sem reenvio em massa depois da atualização: a 1.ª vez que uma pasta com
 * cursor é vista, tudo o que está até ao cursor (e fora das falhas) entra no
 * índice como já enviado. Um ficheiro movido entre duas pastas do backup é
 * reconhecido pelo índice da outra pasta (mesmo id e assinatura).
 */
@Singleton
class BackupReconciler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: MediaScanner,
    private val browseApi: BrowseApi,
    private val uploadManager: UploadManager,
    private val logger: AppLogger,
) {
    /** Uma pasta escolhida, com o estado lido no início da execução. */
    class Folder(
        val name: String,
        val cursor: Long,
        val failed: Map<Long, Int>,
        val excludeMedia: Set<BackupCollection>,
    )

    private class Entry(
        val ids: HashMap<Long, Long>,
        var reconciledAt: Long,
        var generation: String,
        var dirty: Boolean = false,
    )

    private val dir = File(context.filesDir, "backup_index")
    private val entries = HashMap<String, Entry>()
    private var lastFlushAt = 0L

    /**
     * Reconcilia as pastas de uma colecção. Devolve, por pasta, o conjunto de
     * falhas com os candidatos acrescentados (só as pastas que mudaram).
     */
    suspend fun reconcile(collection: BackupCollection, folders: List<Folder>, force: Boolean): Map<String, Map<Long, Int>> {
        val now = System.currentTimeMillis()
        val due = ArrayList<Folder>()
        synchronized(this) {
            for (f in folders) {
                val key = keyFor(collection, f.name)
                val entry = load(key)
                when {
                    // Pasta nova (ou cursores repostos): o scan por cursor envia tudo.
                    f.cursor <= 0L -> if (entry == null || entry.ids.isNotEmpty()) {
                        entries[key] = Entry(HashMap(), now, "", dirty = true)
                    }
                    entry == null -> {
                        // 1.ª vez depois da atualização: o que está até ao cursor já foi enviado.
                        val items = runCatching { scanner.scanUpTo(collection, f.cursor, setOf(f.name), f.excludeMedia) }.getOrNull() ?: continue
                        val ids = HashMap<Long, Long>(items.size * 2)
                        items.filter { !it.pending && it.mediaStoreId !in f.failed }
                            .forEach { ids[it.mediaStoreId] = signature(it) }
                        entries[key] = Entry(ids, now, generation().orEmpty(), dirty = true)
                        logger.log("AutoBackup", "Índice de ${collection.name}/${f.name}: ${ids.size} ficheiro(s) já enviados")
                    }
                    force || now - entry.reconciledAt !in 0 until INTERVAL_MS -> due += f
                }
            }
        }
        if (due.isEmpty()) {
            flush(force = true)
            return emptyMap()
        }

        // Nada mudou no MediaStore desde a última vez (Android 11+): não relê as pastas.
        val generation = generation()
        if (!force && generation != null) synchronized(this) {
            val unchanged = due.all { entries[keyFor(collection, it.name)]?.generation == generation }
            if (unchanged) {
                due.forEach { f -> entries[keyFor(collection, f.name)]?.let { it.reconciledAt = now; it.dirty = true } }
                flush(force = true)
                return emptyMap()
            }
        }

        // Id+assinatura conhecidos em qualquer pasta desta colecção (antes de podar):
        // um ficheiro movido entre duas pastas do backup já foi enviado.
        val known = HashMap<Long, Long>()
        synchronized(this) {
            for (f in folders) entries[keyFor(collection, f.name)]?.ids?.let { known.putAll(it) }
        }

        val candidates = LinkedHashMap<String, MutableList<BackupItem>>()
        for (f in due) {
            val items = try {
                scanner.scanUpTo(collection, f.cursor, setOf(f.name), f.excludeMedia)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                continue
            }
            synchronized(this) {
                val entry = entries[keyFor(collection, f.name)] ?: return@synchronized
                val present = items.mapTo(HashSet()) { it.mediaStoreId }
                // Apagados ou movidos para fora: saem do índice (se voltarem, o servidor confirma).
                if (entry.ids.keys.retainAll(present)) entry.dirty = true
                for (item in items) {
                    if (item.pending || item.mediaStoreId in f.failed) continue
                    val sig = signature(item)
                    if (entry.ids[item.mediaStoreId] == sig) continue
                    if (known[item.mediaStoreId] == sig) {
                        entry.ids[item.mediaStoreId] = sig
                        entry.dirty = true
                        continue
                    }
                    candidates.getOrPut(f.name) { ArrayList() } += item
                }
            }
        }

        val all = candidates.values.flatten()
        val keyOf = all.associateWith { uploadManager.backupUploadKey(it.uri, it.displayName, it.sizeBytes) }
        // null = não deu para perguntar agora (rede/servidor): tenta daqui a pouco.
        val existing: Set<String>? = if (all.isEmpty()) emptySet() else existingKeys(keyOf.values.distinct())
        if (existing == null) {
            synchronized(this) {
                due.forEach { f -> entries[keyFor(collection, f.name)]?.let { it.reconciledAt = now - INTERVAL_MS + RETRY_MS; it.dirty = true } }
            }
            flush(force = true)
            return emptyMap()
        }

        val out = HashMap<String, Map<Long, Int>>()
        synchronized(this) {
            for (f in due) {
                val entry = entries[keyFor(collection, f.name)] ?: continue
                entry.reconciledAt = now
                entry.generation = generation.orEmpty()
                entry.dirty = true
                val list = candidates[f.name].orEmpty()
                if (list.isEmpty()) continue
                val failed = f.failed.toMutableMap()
                for (item in list) {
                    if (keyOf.getValue(item) in existing) entry.ids[item.mediaStoreId] = signature(item)
                    else failed.putIfAbsent(item.mediaStoreId, 0)
                }
                if (failed.size != f.failed.size) {
                    out[f.name] = failed
                    logger.log("AutoBackup", "${failed.size - f.failed.size} ficheiro(s) movidos/editados em ${collection.name}/${f.name} por enviar")
                }
            }
        }
        flush(force = true)
        return out
    }

    /** Enviados com sucesso (ou já existentes): entram no índice da pasta. */
    fun markUploaded(collection: BackupCollection, folder: String, items: List<BackupItem>) {
        if (items.isEmpty()) return
        synchronized(this) {
            val key = keyFor(collection, folder)
            // Sem índice ainda (a linha de base falhou): fica para a linha de base.
            val entry = load(key) ?: return
            items.forEach { entry.ids[it.mediaStoreId] = signature(it) }
            entry.dirty = true
        }
        flush(force = false)
    }

    /** Grava os índices alterados (no máximo a cada 30 s, salvo [force]). */
    fun flush(force: Boolean = true) {
        synchronized(this) {
            val now = System.currentTimeMillis()
            if (!force && now - lastFlushAt < FLUSH_INTERVAL_MS) return
            lastFlushAt = now
            entries.filterValues { it.dirty }.forEach { (key, entry) ->
                if (save(key, entry)) entry.dirty = false
            }
        }
    }

    private suspend fun existingKeys(keys: List<String>): Set<String>? {
        val found = HashSet<String>()
        for (batch in keys.chunked(MAX_KEYS_PER_REQUEST)) {
            val response = try {
                browseApi.checkBackupUploadKeys(BackupKeysCheckBody(batch))
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                logger.log("AutoBackup", "Verificação de upload_keys falhou: ${t.message}")
                return null
            }
            when {
                response.isSuccessful -> response.body()?.existing?.let { found.addAll(it) }
                // Servidor antigo sem o endpoint: envia — o upload continua idempotente pela upload_key.
                response.code() == 404 || response.code() == 405 -> return emptySet()
                else -> {
                    logger.log("AutoBackup", "Verificação de upload_keys: HTTP ${response.code()}")
                    return null
                }
            }
        }
        return found
    }

    /** Nome e tamanho: os campos (além do uri) que mudam a upload_key. */
    private fun signature(item: BackupItem): Long {
        val d = java.security.MessageDigest.getInstance("SHA-256")
            .digest("${item.sizeBytes}|${item.displayName}".toByteArray())
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (d[i].toLong() and 0xff)
        return v
    }

    /** Geração do MediaStore (Android 11+): muda com qualquer alteração. null antes do 11. */
    private fun generation(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            MediaStore.getExternalVolumeNames(context).sorted()
                .joinToString(";") { "$it:${MediaStore.getGeneration(context, it)}" }
        }.getOrNull()
    }

    private fun keyFor(collection: BackupCollection, folder: String): String {
        val d = java.security.MessageDigest.getInstance("SHA-1").digest(folder.toByteArray())
        return collection.name + "_" + d.take(10).joinToString("") { "%02x".format(it) }
    }

    private fun load(key: String): Entry? {
        entries[key]?.let { return it }
        val file = File(dir, "$key.bin")
        if (!file.exists()) return null
        return runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                require(input.readInt() == FORMAT_VERSION)
                val reconciledAt = input.readLong()
                val generation = input.readUTF()
                val count = input.readInt()
                val ids = HashMap<Long, Long>(count * 2)
                repeat(count) { ids[input.readLong()] = input.readLong() }
                Entry(ids, reconciledAt, generation)
            }
        }.getOrNull()?.also { entries[key] = it }
    }

    private fun save(key: String, entry: Entry): Boolean = runCatching {
        dir.mkdirs()
        val tmp = File(dir, "$key.tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { out ->
            out.writeInt(FORMAT_VERSION)
            out.writeLong(entry.reconciledAt)
            out.writeUTF(entry.generation)
            out.writeInt(entry.ids.size)
            entry.ids.forEach { (id, sig) -> out.writeLong(id); out.writeLong(sig) }
        }
        tmp.renameTo(File(dir, "$key.bin"))
    }.getOrDefault(false)

    private companion object {
        const val FORMAT_VERSION = 1
        // Releitura das pastas no máximo a este ritmo (o "Fazer backup agora" força).
        const val INTERVAL_MS = 30L * 60 * 1000
        // Servidor/rede indisponível na verificação: tenta de novo daqui a 5 min.
        const val RETRY_MS = 5L * 60 * 1000
        const val FLUSH_INTERVAL_MS = 30_000L
        // Igual ao limite do servidor (CheckBackupUploadKeysController::MAX_KEYS).
        const val MAX_KEYS_PER_REQUEST = 500
    }
}
