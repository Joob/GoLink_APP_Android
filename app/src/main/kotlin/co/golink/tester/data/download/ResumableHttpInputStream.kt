package co.golink.tester.data.download

import java.io.IOException
import java.io.InputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Corpo de um download que se retoma sozinho: quando a ligação cai a meio
 * (troca de rede, Wi-Fi a falhar), volta a pedir com `Range: bytes=<lidos>-` e
 * continua do mesmo byte. Quem lê (cópia direta ou decifra E2E em stream) nem
 * dá pela falha — por isso serve tanto para ficheiros normais como cifrados.
 *
 * [onBytes] recebe o total lido até agora (para a % da notificação).
 */
class ResumableHttpInputStream(
    private val client: OkHttpClient,
    private val url: String,
    first: Response,
    private val maxRetries: Int = 6,
    private val onBytes: (Long) -> Unit = {},
) : InputStream() {
    private var response: Response = first
    private var input: InputStream = first.body?.byteStream() ?: throw IOException("Resposta vazia")
    private var position = 0L
    private var retries = 0

    /** Tamanho total (Content-Length do primeiro pedido); -1 se desconhecido. */
    val totalSize: Long = first.body?.contentLength() ?: -1L

    override fun read(): Int {
        val one = ByteArray(1)
        val n = read(one, 0, 1)
        return if (n == -1) -1 else one[0].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        while (true) {
            try {
                val n = input.read(b, off, len)
                if (n > 0) {
                    position += n
                    retries = 0
                    onBytes(position)
                }
                return n
            } catch (e: IOException) {
                if (Thread.currentThread().isInterrupted || retries >= maxRetries) throw e
                if (totalSize in 1..position) throw e
                retries++
                reopen()
            }
        }
    }

    private fun reopen() {
        runCatching { response.close() }
        // Backoff curto: 1, 2, 4, 8… s (máx. 15 s) até a rede voltar.
        Thread.sleep(minOf(15_000L, 1000L shl (retries - 1)))
        val next = client.newCall(
            Request.Builder().url(url).header("Range", "bytes=$position-").build(),
        ).execute()
        // Só um 206 a começar no byte certo é uma retoma válida — um 200
        // recomeçava do zero e corrompia o ficheiro.
        val start = next.header("Content-Range")?.let { Regex("""bytes (\d+)-""").find(it)?.groupValues?.get(1)?.toLongOrNull() }
        if (next.code != 206 || start != position) {
            next.close()
            throw IOException("O servidor não permitiu retomar o download (HTTP ${next.code})")
        }
        response = next
        input = next.body?.byteStream() ?: throw IOException("Resposta vazia")
    }

    override fun close() {
        runCatching { input.close() }
        runCatching { response.close() }
    }
}
