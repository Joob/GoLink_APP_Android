package co.golink.tester.ui.screens.notifications

import co.golink.tester.ui.i18n.tr
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.notifications.NotificationsRepository
import co.golink.tester.domain.notifications.Notification
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val isLoading: Boolean = false,
    val toast: String? = null,
)

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

    val items: StateFlow<List<Notification>> = repository.items
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            repository.refresh()
                .onSuccess { _state.update { it.copy(isLoading = false) } }
                .onFailure { t -> _state.update { it.copy(isLoading = false, toast = t.message) } }
        }
    }

    fun markAllRead() = viewModelScope.launch {
        repository.markAllRead().onFailure { t -> _state.update { it.copy(toast = t.message) } }
    }

    fun markRead(id: String) = viewModelScope.launch {
        repository.markRead(id)
    }

    /** Alterna lida / não lida (botão por notificação, como na web). */
    fun toggleRead(n: Notification) = viewModelScope.launch {
        val result = if (n.isRead) repository.markUnread(n.id) else repository.markRead(n.id)
        result.onFailure { t -> _state.update { it.copy(toast = t.message) } }
    }

    fun delete(id: String) = viewModelScope.launch {
        repository.delete(id).onFailure { t -> _state.update { it.copy(toast = t.message) } }
    }

    fun clearRead() = viewModelScope.launch {
        repository.deleteRead().onFailure { t -> _state.update { it.copy(toast = t.message) } }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }
}
