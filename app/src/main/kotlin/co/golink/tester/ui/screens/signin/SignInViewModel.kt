package co.golink.tester.ui.screens.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.auth.AuthRepository
import co.golink.tester.data.config.ConfigRepository
import co.golink.tester.domain.auth.AuthError
import co.golink.tester.domain.config.SocialLogins
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val socialLogins: SocialLogins = SocialLogins(),
    val registrationAllowed: Boolean = true,
    val loginSucceeded: Boolean = false,
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val configRepository: ConfigRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            configRepository.load().onSuccess { cfg ->
                _state.update {
                    it.copy(
                        socialLogins = cfg.social_logins ?: SocialLogins(),
                        registrationAllowed = cfg.registration?.isAllowed ?: true,
                    )
                }
            }
        }
    }

    fun onEmail(value: String) = _state.update { it.copy(email = value.trim(), emailError = null, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, passwordError = null, error = null) }
    fun togglePasswordVisibility() = _state.update { it.copy(showPassword = !it.showPassword) }

    fun login() {
        val s = _state.value
        if (s.email.isBlank()) {
            _state.update { it.copy(emailError = "Email obrigatório") }; return
        }
        if (s.password.isBlank()) {
            _state.update { it.copy(passwordError = "Password obrigatória") }; return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.login(s.email, s.password)
                .onSuccess {
                    _state.update { it.copy(isLoading = false, loginSucceeded = true) }
                }
                .onFailure { t ->
                    _state.update { it.copy(isLoading = false, error = readableError(t)) }
                }
        }
    }

    private fun readableError(t: Throwable): String = when (t) {
        is AuthError.InvalidCredentials -> "Email ou password incorrectos"
        is AuthError.AccountNotFound -> "Conta não encontrada"
        is AuthError.Validation -> t.message ?: "Dados inválidos"
        is AuthError.Network -> "Falha de rede"
        is AuthError -> t.message ?: "Erro"
        else -> t.message ?: "Erro inesperado"
    }
}
