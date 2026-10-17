package co.golink.tester.data.encryption

import android.content.Context
import java.io.File

/**
 * Ficheiros em claro (desencriptados) que o viewer precisa de pôr em disco:
 * vídeo/áudio E2E para o ExoPlayer e PDFs para o PdfRenderer. Ficam em
 * cacheDir (fora de backups) e têm de ser apagados assim que deixam de ser
 * precisos — e varridos no arranque, para o caso de a app ter morrido antes.
 */
object PlaintextCache {
    // e2e_ cobre e2e_view_ (claro) e e2e_*.enc (ciphertext temporário dos uploads).
    private val PREFIXES = listOf("e2e_", "viewer_pdf_", "newtext_")

    /** Apaga restos de sessões anteriores (crash, processo terminado). */
    fun sweep(context: Context, olderThan: Long = System.currentTimeMillis()) {
        context.cacheDir.listFiles()?.forEach { file ->
            // Só restos anteriores a este arranque — um backup que comece em
            // paralelo cria temporários novos que não podem ser apagados.
            if (file.isFile && file.lastModified() < olderThan && PREFIXES.any { file.name.startsWith(it) }) {
                wipe(file)
            }
        }
    }

    /** Sobrescreve com zeros (best-effort) e apaga. */
    fun wipe(file: File) {
        runCatching {
            if (file.exists() && file.length() in 1..(64L * 1024 * 1024)) {
                file.outputStream().use { out ->
                    val zeros = ByteArray(64 * 1024)
                    var left = file.length()
                    while (left > 0) {
                        val n = minOf(left, zeros.size.toLong()).toInt()
                        out.write(zeros, 0, n)
                        left -= n
                    }
                }
            }
        }
        file.delete()
    }
}
