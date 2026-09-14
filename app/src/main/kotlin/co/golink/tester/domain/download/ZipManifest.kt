package co.golink.tester.domain.download

import kotlinx.serialization.Serializable

@Serializable
data class ZipManifestResponse(
    val files: List<ZipManifestEntry> = emptyList(),
    // E2E: nomes cifrados por id (ficheiros + pastas do caminho). No servidor os
    // nomes são o placeholder, por isso o zip saía todo com '•'.
    val names: Map<String, String> = emptyMap(),
)

@Serializable
data class ZipManifestEntry(
    val id: String,
    val name: String,
    val path: String,
    // Ids das pastas do caminho, para recompor o path com os nomes decifrados.
    val path_ids: List<String> = emptyList(),
    val encrypted: Boolean = false,
    val mimetype: String? = null,
    val filesize: Long = 0,
)
