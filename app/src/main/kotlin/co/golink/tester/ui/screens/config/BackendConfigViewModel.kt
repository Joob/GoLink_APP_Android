package co.golink.tester.ui.screens.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.config.BackendConfigRepository
import co.golink.tester.network.ApiServiceFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackendConfigUiState(
    val url: String = "",
    val status: ConnectionStatus = ConnectionStatus.Idle,
    val errorMessage: String? = null,
    val saved: Boolean = false,
)

sealed interface ConnectionStatus {
    data object Idle : ConnectionStatus
    data object Testing : ConnectionStatus
    data object Ok : ConnectionStatus
    data object Failed : ConnectionStatus
}

@HiltViewModel
class BackendConfigViewModel @Inject constructor(
    private val repository: BackendConfigRepository,
    private val factory: ApiServiceFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(BackendConfigUiState())
    val state: StateFlow<BackendConfigUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = repository.currentUrl()
            _state.update { it.copy(url = existing) }
        }
    }

    fun onUrlChange(value: String) {
        _state.update { it.copy(url = value, status = ConnectionStatus.Idle, errorMessage = null) }
    }

    fun testConnection() {
        val raw = _state.value.url
        if (raw.isBlank()) {
            _state.update { it.copy(status = ConnectionStatus.Failed, errorMessage = "URL vazio") }
            return
        }
        val normalized = BackendConfigRepository.normalize(raw)
        _state.update { it.copy(status = ConnectionStatus.Testing, errorMessage = null) }
        viewModelScope.launch {
            runCatching { factory.create(normalized).ping() }
                .onSuccess { response ->
                    if (response.isSuccessful) {
                        _state.update {
                            it.copy(url = normalized, status = ConnectionStatus.Ok, errorMessage = null)
                        }
                    } else {
                        _state.update {
                            it.copy(
                                status = ConnectionStatus.Failed,
                                errorMessage = "HTTP ${response.code()}"
                            )
                        }
                    }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(
                            status = ConnectionStatus.Failed,
                            errorMessage = t.message ?: t::class.simpleName.orEmpty()
                        )
                    }
                }
        }
    }

    fun save(onSaved: () -> Unit) {
        val raw = _state.value.url
        if (raw.isBlank()) {
            _state.update { it.copy(errorMessage = "URL vazio") }
            return
        }
        viewModelScope.launch {
            repository.setBackendUrl(raw)
            _state.update { it.copy(saved = true) }
            onSaved()
        }
    }
}
