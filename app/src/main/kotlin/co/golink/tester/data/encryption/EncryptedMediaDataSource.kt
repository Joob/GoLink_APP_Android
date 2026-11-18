package co.golink.tester.data.encryption

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.security.GeneralSecurityException
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response

/**
 * Toca media E2E em streaming: cada leitura do ExoPlayer vira pedidos Range de
 * ciphertext, decifrados bloco a bloco. O vídeo arranca sem esperar pelo ficheiro
 * todo, o seek só pede os blocos necessários e nada em claro vai para o disco.
 */
@UnstableApi
class EncryptedMediaDataSource private constructor(private val source: Factory) : BaseDataSource(/* isNetwork = */ true) {

    class Factory(
        // Call.Factory (não OkHttpClient): o viewer passa uma que cancela tudo ao sair.
        private val client: Call.Factory,
        internal val url: String,
        private val token: String?,
        private val dataKey: ByteArray,
    ) : DataSource.Factory {
        @Volatile private var layout: EncryptedFileCodec.BlockLayout? = null

        override fun createDataSource(): DataSource = EncryptedMediaDataSource(this)

        /** Partilhado entre as fontes do mesmo player: cada open custava mais um pedido. */
        internal fun layout(): EncryptedFileCodec.BlockLayout = layout ?: request(0, HEAD_LEN - 1L).use { resp ->
            val objectSize = resp.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                ?: throw IOException("Content-Range em falta")
            val head = resp.body?.bytes() ?: throw IOException("vazio")
            try {
                EncryptedFileCodec.blockLayout(head, objectSize, dataKey)
            } catch (e: IllegalArgumentException) {
                throw IOException(e)
            }.also { layout = it }
        }

        internal fun request(from: Long, to: Long): Response {
            // Authorization no HEADER: o ?token= da query não é aceite pelo backend.
            val req = Request.Builder().url(url)
                .header("Range", "bytes=$from-$to")
                .header("Accept-Encoding", "identity")
                .apply { token?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
                .build()
            val resp = client.newCall(req).execute()
            if (resp.code != 206) {
                resp.close()
                throw IOException("HTTP ${resp.code}")
            }
            return resp
        }
    }

    private var uri: Uri? = null
    private var opened = false
    private var layout: EncryptedFileCodec.BlockLayout? = null
    private var response: Response? = null
    private var input: InputStream? = null
    private var block = 0
    private var windowLast = -1
    private var plain = ByteArray(0)
    private var plainOff = 0
    private var skip = 0
    private var remaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        seek(dataSpec.position, dataSpec.length)
        uri = dataSpec.uri
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    /** Posiciona a leitura no plaintext. internal: DataSpec não se constrói fora do Android. */
    internal fun seek(position: Long, length: Long) {
        val l = source.layout()
        if (position > l.plainLength) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        val available = l.plainLength - position
        remaining = if (length == C.LENGTH_UNSET.toLong()) available else minOf(length, available)
        layout = l
        block = (position / l.chunkSize).toInt()
        skip = (position % l.chunkSize).toInt()
        plain = ByteArray(0)
        plainOff = 0
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        if (plainOff >= plain.size) loadBlock()
        val n = minOf(minOf(length, plain.size - plainOff).toLong(), remaining).toInt()
        System.arraycopy(plain, plainOff, buffer, offset, n)
        plainOff += n
        remaining -= n
        bytesTransferred(n)
        return n
    }

    private fun loadBlock() {
        val l = layout ?: throw IOException("fonte fechada")
        val stream = input ?: openWindow(l)
        val ct = ByteArray(l.lengthOf(block))
        var off = 0
        while (off < ct.size) {
            val r = stream.read(ct, off, ct.size - off)
            if (r < 0) throw EOFException("bloco truncado")
            off += r
        }
        plain = try {
            l.open(ct, block)
        } catch (e: GeneralSecurityException) {
            throw IOException(e)
        } catch (e: IllegalArgumentException) {
            // Bloco malformado (ex.: último bloco menor que a tag): erro de leitura, não de programa.
            throw IOException(e)
        }
        plainOff = skip
        skip = 0
        if (block == windowLast) closeWindow()
        block++
    }

    private fun openWindow(l: EncryptedFileCodec.BlockLayout): InputStream {
        windowLast = minOf(block + (WINDOW_BYTES / l.ctFull).coerceAtLeast(1) - 1, l.lastIndex)
        val resp = source.request(l.offsetOf(block), l.offsetOf(windowLast) + l.lengthOf(windowLast) - 1)
        response = resp
        return (resp.body?.byteStream() ?: throw IOException("vazio")).also { input = it }
    }

    private fun closeWindow() {
        response?.close()
        response = null
        input = null
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        closeWindow()
        // Bloco em claro: apaga-o antes de o largar.
        plain.fill(0)
        plain = ByteArray(0)
        plainOff = 0
        layout = null
        uri = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private companion object {
        /** Janela de ciphertext por pedido: segura menos um worker do servidor e conta só o tráfego pedido. */
        const val WINDOW_BYTES = 8 * 1024 * 1024
        /** Chega para detetar o header duplicado de ficheiros antigos. */
        const val HEAD_LEN = EncryptedFileCodec.HEADER_LEN * 2
    }
}
