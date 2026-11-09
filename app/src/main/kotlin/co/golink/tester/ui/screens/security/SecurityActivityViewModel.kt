package co.golink.tester.ui.screens.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.domain.security.SecurityEvent
import co.golink.tester.network.SecurityEventsApi
import co.golink.tester.ui.i18n.tr
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SecurityActivityUiState(
    val isLoading: Boolean = false,
    val events: List<SecurityEvent> = emptyList(),
    val toast: String? = null,
)

@HiltViewModel
class SecurityActivityViewModel @Inject constructor(
    private val api: SecurityEventsApi,
) : ViewModel() {

    private val _state = MutableStateFlow(SecurityActivityUiState())
    val state: StateFlow<SecurityActivityUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            runCatching {
                val response = api.list()
                if (!response.isSuccessful) error("HTTP ${response.code()}")
                val body = response.body() ?: error("Resposta vazia")
                body.data.map { SecurityEvent.fromEnvelope(it) }
            }
                .onSuccess { events -> _state.update { it.copy(isLoading = false, events = events) } }
                .onFailure { t ->
                    // Detalhe (HTTP 500, IOException…) só para o log; o toast é traduzido.
                    android.util.Log.w("SecurityActivity", "Falha a carregar: ${t.message}")
                    val msg = if (t is java.io.IOException) "Sem ligação ao servidor. Tenta novamente.".tr()
                    else "Não foi possível carregar a atividade de segurança.".tr()
                    _state.update { it.copy(isLoading = false, toast = msg) }
                }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }
}
