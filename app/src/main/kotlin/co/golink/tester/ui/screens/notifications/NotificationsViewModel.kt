package co.golink.tester.ui.screens.notifications

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
) : ViewModel() {

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

    fun delete(id: String) = viewModelScope.launch {
        repository.delete(id).onFailure { t -> _state.update { it.copy(toast = t.message) } }
    }

    fun flushAll() = viewModelScope.launch {
        repository.flushAll().onFailure { t -> _state.update { it.copy(toast = t.message) } }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }
}
