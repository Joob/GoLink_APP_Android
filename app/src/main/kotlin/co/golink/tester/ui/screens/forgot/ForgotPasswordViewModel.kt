package co.golink.tester.ui.screens.forgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val sent: Boolean = false,
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    fun onEmail(v: String) = _state.update { it.copy(email = v.trim(), emailError = null, error = null) }

    fun submit() {
        val s = _state.value
        if (s.email.isBlank()) {
            _state.update { it.copy(emailError = "Email obrigatório") }; return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.recoverPassword(s.email)
                .onSuccess { _state.update { it.copy(isLoading = false, sent = true) } }
                .onFailure { t -> _state.update { it.copy(isLoading = false, error = t.message ?: "Erro") } }
        }
    }
}
