package co.golink.tester.data.files

import co.golink.tester.data.teams.TeamsRepository
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.NavFolder
import co.golink.tester.domain.browse.NavigationSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Um pedido de mover (lote) com o seu estado, para o painel "A mover…". */
data class MoveJob(
    val id: String,
    val items: List<BrowseItem>,
    val toId: String?,
    val toName: String,
    val state: State = State.Moving,
    val error: String? = null,
    val retryable: Boolean = true,
    val startedAt: Long = System.currentTimeMillis(),
) {
    enum class State { Moving, Done, Failed }
}

/**
 * Mover com progresso visível (como os uploads): cada lote é um job que
 * sobrevive à navegação, com Tentar de novo. O servidor só muda parent_id, mas
 * em lotes grandes/equipas o pedido demora e sem painel parecia parado.
 */
@Singleton
class MoveManager @Inject constructor(
    private val filesRepository: FilesRepository,
    private val teamsRepository: TeamsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var seq = 0

    private val _jobs = MutableStateFlow<List<MoveJob>>(emptyList())
    val jobs: StateFlow<List<MoveJob>> = _jobs.asStateFlow()

    /** Muda a cada lote movido com sucesso: os ecrãs recarregam a lista. */
    private val _completed = MutableStateFlow(0L)
    val completedTick: StateFlow<Long> = _completed.asStateFlow()

    fun move(items: List<BrowseItem>, toId: String?, toName: String): String? {
        if (items.isEmpty()) return null
        val job = MoveJob(id = "mv-${System.currentTimeMillis()}-${++seq}", items = items, toId = toId, toName = toName)
        _jobs.update { it + job }
        run(job.id)
        return job.id
    }

    fun retry(id: String) {
        _jobs.update { list -> list.map { if (it.id == id) it.copy(state = MoveJob.State.Moving, error = null, retryable = true, startedAt = System.currentTimeMillis()) else it } }
        run(id)
    }

    fun dismiss(id: String) = _jobs.update { list -> list.filterNot { it.id == id } }

    private fun run(id: String) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        scope.launch {
            filesRepository.move(job.items, job.toId)
                .onSuccess {
                    teamsRepository.invalidateClearedNames()
                    update(id) { it.copy(state = MoveJob.State.Done) }
                    _completed.value = System.currentTimeMillis()
                    delay(DONE_LINGER_MS)
                    if (_jobs.value.firstOrNull { it.id == id }?.state == MoveJob.State.Done) dismiss(id)
                }
                .onFailure { t ->
                    val msg = t.message.orEmpty()
                    // Recusas definitivas do servidor: sem "Tentar de novo".
                    val outOfTeam = msg.contains("within that team folder", ignoreCase = true)
                    val rootMove = msg.contains("team folders can't be moved", ignoreCase = true)
                    val teamInto = !outOfTeam && !rootMove && msg.contains("team folder", ignoreCase = true)
                    update(id) {
                        it.copy(
                            state = MoveJob.State.Failed,
                            error = when {
                                outOfTeam -> TEAM_SCOPE_MESSAGE
                                rootMove -> TEAM_ROOT_MESSAGE
                                teamInto -> TEAM_INTO_TEAM_MESSAGE
                                else -> msg
                            },
                            retryable = !outOfTeam && !teamInto && !rootMove,
                        )
                    }
                }
        }
    }

    private fun update(id: String, f: (MoveJob) -> MoveJob) =
        _jobs.update { list -> list.map { if (it.id == id) f(it) else it } }

    companion object {
        private const val DONE_LINGER_MS = 4_000L
        // Chaves i18n (o banner traduz com .tr()).
        const val TEAM_SCOPE_MESSAGE = "Os itens de uma pasta de equipa só podem ser movidos dentro dessa equipa."
        const val TEAM_ROOT_MESSAGE = "As pastas de equipa não se movem. Podes mover os itens dentro delas."
        const val TEAM_INTO_TEAM_MESSAGE = "Uma pasta de equipa não pode ir para dentro de outra."

        /** Nome da pasta destino na árvore de navegação (null = raiz). */
        fun folderName(sections: List<NavigationSection>, id: String?): String? {
            if (id == null) return null
            fun find(list: List<NavFolder>): String? {
                list.forEach { f ->
                    if (f.id == id) return f.name
                    find(f.folders)?.let { return it }
                }
                return null
            }
            sections.forEach { s -> find(s.folders)?.let { return it } }
            return null
        }
    }
}
