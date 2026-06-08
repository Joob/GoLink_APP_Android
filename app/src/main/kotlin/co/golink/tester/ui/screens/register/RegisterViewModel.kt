package co.golink.tester.ui.screens.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.auth.AuthRepository
import co.golink.tester.data.config.ConfigRepository
import co.golink.tester.domain.auth.AuthError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val invitationToken: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val requiresVerification: Boolean = false,
    val success: Boolean = false,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val configRepository: ConfigRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            configRepository.load().onSuccess { cfg ->
                _state.update {
                    it.copy(requiresVerification = cfg.registration?.requiresVerification ?: false)
                }
            }
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v, fieldErrors = it.fieldErrors - "name", error = null) }
    fun onEmail(v: String) = _state.update { it.copy(email = v.trim(), fieldErrors = it.fieldErrors - "email", error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, fieldErrors = it.fieldErrors - "password", error = null) }
    fun onPasswordConfirmation(v: String) = _state.update { it.copy(passwordConfirmation = v, fieldErrors = it.fieldErrors - "password_confirmation", error = null) }
    fun onInvitationToken(v: String) = _state.update { it.copy(invitationToken = v) }

    fun submit() {
        val s = _state.value
        val errors = mutableMapOf<String, String>()
        if (s.name.isBlank()) errors["name"] = "Nome obrigatório"
        if (s.email.isBlank()) errors["email"] = "Email obrigatório"
        if (s.password.length < 8) errors["password"] = "Mínimo 8 caracteres"
        if (s.password != s.passwordConfirmation) errors["password_confirmation"] = "Não coincide"
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.register(
                name = s.name.trim(),
                email = s.email.trim(),
                password = s.password,
                passwordConfirmation = s.passwordConfirmation,
                invitationToken = s.invitationToken.takeIf { it.isNotBlank() },
            )
                .onSuccess { _state.update { it.copy(isLoading = false, success = true) } }
                .onFailure { t ->
                    when (t) {
                        is AuthError.Validation -> _state.update {
                            it.copy(
                                isLoading = false,
                                fieldErrors = t.fields.mapValues { (_, msgs) -> msgs.firstOrNull().orEmpty() },
                                error = if (t.fields.isEmpty()) t.message else null,
                            )
                        }
                        is AuthError.RegistrationDisabled -> _state.update {
                            it.copy(isLoading = false, error = t.message)
                        }
                        else -> _state.update { it.copy(isLoading = false, error = t.message) }
                    }
                }
        }
    }
}
