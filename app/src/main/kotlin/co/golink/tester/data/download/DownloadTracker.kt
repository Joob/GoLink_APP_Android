package co.golink.tester.data.download

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class DownloadPhase { Preparing, Downloading, Done, Failed, Cancelled }

/** Uma transferência em curso/recente, para o painel dentro da app (como na web). */
data class DownloadJob(
    val id: Int,
    val name: String,
    val isZip: Boolean,
    val encrypted: Boolean = false,
    val phase: DownloadPhase = DownloadPhase.Preparing,
    val loaded: Long = 0L,
    val total: Long = 0L,
    val startedAt: Long = System.currentTimeMillis(),
    /** Início da fase de transferência (para a velocidade/tempo restante). */
    val transferStartedAt: Long = 0L,
    val message: String? = null,
)

/**
 * Estado das transferências, alimentado pelo [ZipDownloadService]. O painel
 * ([co.golink.tester.ui.components.DownloadPanel]) só lê este estado.
 */
@Singleton
class DownloadTracker @Inject constructor() {
    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    fun start(id: Int, name: String, isZip: Boolean, encrypted: Boolean) {
        _jobs.update { it + DownloadJob(id = id, name = name, isZip = isZip, encrypted = encrypted) }
    }

    fun markEncrypted(id: Int) = update(id) { it.copy(encrypted = true) }

    fun progress(id: Int, loaded: Long, total: Long) = update(id) {
        val now = System.currentTimeMillis()
        it.copy(
            phase = if (it.phase == DownloadPhase.Preparing && loaded > 0) DownloadPhase.Downloading else it.phase,
            loaded = loaded,
            total = total,
            transferStartedAt = if (it.transferStartedAt == 0L && loaded > 0) now else it.transferStartedAt,
        )
    }

    fun finish(id: Int, phase: DownloadPhase, message: String? = null) = update(id) {
        it.copy(phase = phase, message = message)
    }

    fun dismiss(id: Int) = _jobs.update { list -> list.filterNot { it.id == id } }

    private fun update(id: Int, block: (DownloadJob) -> DownloadJob) {
        _jobs.update { list -> list.map { if (it.id == id) block(it) else it } }
    }
}
