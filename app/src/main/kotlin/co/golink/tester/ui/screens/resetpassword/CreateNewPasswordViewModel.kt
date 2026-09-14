package co.golink.tester.ui.screens.resetpassword

import co.golink.tester.ui.i18n.tr
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.auth.AuthRepository
import co.golink.tester.domain.auth.AuthError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateNewPasswordUiState(
    val email: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val token: String = "",
    val showPassword: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val success: Boolean = false,
)

@HiltViewModel
class CreateNewPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateNewPasswordUiState())
    val state: StateFlow<CreateNewPasswordUiState> = _state.asStateFlow()

    /** Token vem do link do email (deep link) — definido uma vez. */
    fun setToken(token: String) = _state.update { if (it.token.isBlank()) it.copy(token = token) else it }

    fun onEmail(v: String) = _state.update { it.copy(email = v.trim(), fieldErrors = it.fieldErrors - "email", error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, fieldErrors = it.fieldErrors - "password", error = null) }
    fun onPasswordConfirmation(v: String) = _state.update { it.copy(passwordConfirmation = v, fieldErrors = it.fieldErrors - "password_confirmation", error = null) }
    fun togglePasswordVisibility() = _state.update { it.copy(showPassword = !it.showPassword) }

    fun submit() {
        val s = _state.value
        val errors = mutableMapOf<String, String>()
        if (s.email.isBlank()) errors["email"] = "Email obrigatório".tr()
        if (s.password.length < 8) errors["password"] = "Mínimo 8 caracteres".tr()
        if (s.password != s.passwordConfirmation) errors["password_confirmation"] = "Não coincide".tr()
        if (s.token.isBlank()) errors["email"] = "Link inválido ou expirado".tr()
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.resetPassword(
                email = s.email.trim(),
                token = s.token,
                password = s.password,
                passwordConfirmation = s.passwordConfirmation,
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
                        else -> _state.update { it.copy(isLoading = false, error = t.message) }
                    }
                }
        }
    }
}
