package co.golink.tester.ui.screens.notifications

import co.golink.tester.ui.i18n.tr
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.notifications.NotificationsRepository
import co.golink.tester.domain.notifications.Notification
import co.golink.tester.domain.notifications.NotificationStats
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val isLoading: Boolean = false,
    val toast: String? = null,
    /** O carregamento falhou e não há lista para mostrar (estado de erro). */
    val loadFailed: Boolean = false,
    /** Puxar para atualizar a decorrer. */
    val refreshing: Boolean = false,
    val loadingOlder: Boolean = false,
)

/** Seleção múltipla; [allInFilter]: todas as do filtro no servidor (como no Gmail). */
data class InboxSelection(val ids: Set<String> = emptySet(), val allInFilter: Boolean = false)

/** Erro genérico das ações (nunca a mensagem crua da exceção). */
private const val ACTION_ERROR = "Não foi possível concluir. Tenta de novo."

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationsRepository,
    private val teams: co.golink.tester.data.teams.TeamsRepository,
    private val calendarApi: co.golink.tester.network.CalendarApi,
) : ViewModel() {

    /** Ids das notificações com uma resposta a caminho (botões desativados). */
    private val _answering = MutableStateFlow<Set<String>>(emptySet())
    val answering: StateFlow<Set<String>> = _answering.asStateFlow()

    /** Aceitar/Recusar um convite (equipa ou evento) direto no Inbox. */
    fun answer(n: Notification, yes: Boolean) {
        if (n.id in _answering.value || !n.pendingAnswer) return
        _answering.update { it + n.id }
        viewModelScope.launch {
            val invitation = n.invitationId
            val event = n.rsvpEventId
            val result = when {
                invitation != null -> if (yes) teams.acceptInvitation(invitation) else teams.declineInvitation(invitation)
                event != null -> runCatching {
                    val body = kotlinx.serialization.json.buildJsonObject {
                        put("status", kotlinx.serialization.json.JsonPrimitive(if (yes) "accepted" else "declined"))
                    }
                    val response = calendarApi.rsvp(event, body)
                    check(response.isSuccessful) { "HTTP ${response.code()}" }
                }
                else -> Result.success(Unit)
            }
            result
                .onSuccess {
                    if (!n.isRead) repository.markRead(n.id)
                    if (invitation != null) {
                        _state.update { it.copy(toast = if (yes) "Aceitaste o convite. A pasta está em Partilhado comigo.".tr() else "Recusaste o convite.".tr()) }
                    }
                }
                .onFailure { t ->
                    val gone = (t as? co.golink.tester.data.teams.TeamsRepository.InvitationError)?.code in setOf(404, 410, 422)
                    _state.update { it.copy(toast = if (gone) "Este convite já foi usado ou expirou.".tr() else "Não foi possível enviar a resposta.".tr()) }
                }
            // O servidor tira os botões (equipa) ou grava a resposta (evento): recarregar.
            repository.refresh()
            _answering.update { it - n.id }
        }
    }

    // Eliminadas com "Desfazer" à espera: escondidas cá, o pedido só sai ao fechar o aviso.
    private val hidden = MutableStateFlow<Set<String>>(emptySet())

    val items: StateFlow<List<Notification>> = combine(repository.items, hidden) { list, h ->
        if (h.isEmpty()) list else list.filterNot { it.id in h }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hasMoreOlder: StateFlow<Boolean> = repository.hasMoreOlder

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    private val _selection = MutableStateFlow<InboxSelection?>(null)
    val selection: StateFlow<InboxSelection?> = _selection.asStateFlow()

    private val _stats = MutableStateFlow<NotificationStats?>(null)
    val stats: StateFlow<NotificationStats?> = _stats.asStateFlow()

    /** Id da eliminação que ainda se pode desfazer (aviso com "Desfazer"). */
    private val _undoable = MutableStateFlow<String?>(null)
    val undoable: StateFlow<String?> = _undoable.asStateFlow()

    init {
        refresh()
    }

    private fun fail() = _state.update { it.copy(toast = ACTION_ERROR.tr()) }

    fun refresh() {
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            repository.refresh()
                .onSuccess { _state.update { it.copy(isLoading = false, loadFailed = false) } }
                .onFailure {
                    // Sem lista: estado de erro com "Tentar de novo"; com lista, só o aviso.
                    val empty = repository.items.value.isEmpty()
                    _state.update { s -> s.copy(isLoading = false, loadFailed = empty, toast = if (empty) s.toast else ACTION_ERROR.tr()) }
                }
        }
    }

    /** Puxar para atualizar. */
    fun pullRefresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val result = repository.refresh()
            _state.update { s ->
                s.copy(
                    refreshing = false,
                    loadFailed = result.isFailure && repository.items.value.isEmpty(),
                    toast = if (result.isFailure && repository.items.value.isNotEmpty()) ACTION_ERROR.tr() else s.toast,
                )
            }
        }
    }

    /** "Carregar mais": lidas anteriores. */
    fun loadOlder() {
        if (_state.value.loadingOlder) return
        _state.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            repository.loadOlder().onFailure { fail() }
            _state.update { it.copy(loadingOlder = false) }
        }
    }

    /** Marcar todas lidas no chip ativo: Todas/Não lidas pela rota de sempre, um grupo por scope. */
    fun markAllRead(filter: String, matches: (Notification) -> Boolean) = viewModelScope.launch {
        val result = if (filter == "all" || filter == "unread") repository.markAllRead()
        else repository.bulkScope("read", filter, matches)
        result.onFailure { fail() }
    }

    fun markRead(id: String) = viewModelScope.launch {
        repository.markRead(id)
    }

    /** Alterna lida / não lida (botão por notificação, como na web). */
    fun toggleRead(n: Notification) = viewModelScope.launch {
        val result = if (n.isRead) repository.markUnread(n.id) else repository.markRead(n.id)
        result.onFailure { fail() }
    }

    /** Eliminar já confirmado (popup de detalhe). */
    fun delete(id: String) = viewModelScope.launch {
        repository.delete(id).onFailure { fail() }
    }

    /** ⋮ ou deslizar: some já e só vai ao servidor quando o aviso fecha sem "Desfazer". */
    fun deleteWithUndo(id: String) {
        if (id in hidden.value) return
        _undoable.value?.let { commitDelete(it) }
        hidden.update { it + id }
        _undoable.value = id
    }

    fun undoDelete(id: String) {
        hidden.update { it - id }
        if (_undoable.value == id) _undoable.value = null
    }

    fun commitDelete(id: String) {
        if (_undoable.value == id) _undoable.value = null
        if (id !in hidden.value) return
        viewModelScope.launch {
            // A remoção local do repositório acontece antes do pedido; só depois
            // se tira daqui (sem piscar). Se falhar, o rollback devolve-a.
            val result = repository.delete(id)
            hidden.update { it - id }
            result.onFailure { fail() }
        }
    }

    /** "Limpar lidas": Todas/Não lidas pela rota de sempre; num grupo, as lidas carregadas dele. */
    fun clearRead(filter: String, groupReadIds: List<String>) = viewModelScope.launch {
        val result = if (filter == "all" || filter == "unread") repository.deleteRead()
        else repository.bulk("delete", groupReadIds)
        result.onFailure { fail() }
    }

    // --- Seleção múltipla ---

    fun startSelection(id: String) {
        _selection.value = InboxSelection(setOf(id))
    }

    /** Toque numa linha em seleção: alterna; sem nenhuma, sai da seleção. */
    fun toggleSelected(id: String) {
        val sel = _selection.value ?: return
        val ids = if (id in sel.ids) sel.ids - id else sel.ids + id
        _selection.value = if (ids.isEmpty()) null else InboxSelection(ids)
    }

    /** Caixa da secção: marca ou desmarca as dela. */
    fun setSelected(ids: Collection<String>, select: Boolean) {
        val sel = _selection.value ?: InboxSelection()
        val next = if (select) sel.ids + ids else sel.ids - ids.toSet()
        _selection.value = if (next.isEmpty()) null else InboxSelection(next)
    }

    /** Selecionar tudo (as carregadas do filtro); pede os totais para o "Selecionar todas as N". */
    fun selectAll(ids: Collection<String>) {
        if (ids.isEmpty()) return
        _selection.value = InboxSelection(ids.toSet())
        _stats.value = null
        viewModelScope.launch {
            repository.stats().onSuccess { _stats.value = it }
        }
    }

    fun selectAllInFilter() {
        _selection.update { it?.copy(allInFilter = true) }
    }

    fun clearSelection() {
        _selection.value = null
    }

    /** Tira da seleção as que já não existem (poll/eliminadas noutro lado). */
    fun pruneSelection(present: Set<String>) {
        val sel = _selection.value ?: return
        if (sel.allInFilter) return
        val ids = sel.ids.filterTo(HashSet()) { it in present }
        if (ids.size != sel.ids.size) _selection.value = if (ids.isEmpty()) null else InboxSelection(ids)
    }

    /** Ação da doca sobre a seleção ("read", "unread" ou "delete"). */
    fun bulkSelected(action: String, filter: String, matches: (Notification) -> Boolean) {
        val sel = _selection.value ?: return
        _selection.value = null
        viewModelScope.launch {
            val result = if (sel.allInFilter) repository.bulkScope(action, filter, matches)
            else repository.bulk(action, sel.ids)
            result.onFailure { fail() }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }

    override fun onCleared() {
        // Ecrã fechado com um "Desfazer" no ar: a eliminação segue na mesma.
        hidden.value.forEach { repository.deleteDetached(it) }
        super.onCleared()
    }
}
