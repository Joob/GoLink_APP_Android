package co.golink.tester.domain.browse

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Assinatura leve do conteúdo directo de uma pasta, usada para polling.
 * Muda sempre que algo na pasta é enviado/apagado/movido/renomeado.
 */
@Serializable
data class FolderFingerprint(
    val folders: Int = 0,
    val files: Int = 0,
    @SerialName("last_modified") val lastModified: String? = null,
) {
    /** Chave estável para comparar entre sondagens. */
    val signature: String get() = "$folders:$files:${lastModified.orEmpty()}"
}
