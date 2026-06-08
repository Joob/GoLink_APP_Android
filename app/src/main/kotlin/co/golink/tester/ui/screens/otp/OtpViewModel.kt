package co.golink.tester.ui.screens.otp

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

data class OtpUiState(
    val code: String = "",
    val isLoading: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val validated: Boolean = false,
    val codeSentOnce: Boolean = false,
)

@HiltViewModel
class OtpViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OtpUiState())
    val state: StateFlow<OtpUiState> = _state.asStateFlow()

    init {
        sendCode(initial = true)
    }

    fun onCode(value: String) {
        val digits = value.filter { it.isDigit() }.take(6)
        _state.update { it.copy(code = digits, error = null) }
        if (digits.length == 6) validate()
    }

    fun sendCode(initial: Boolean = false) {
        _state.update { it.copy(sending = true, error = null, info = null) }
        viewModelScope.launch {
            authRepository.sendOtp()
                .onSuccess {
                    _state.update {
                        it.copy(
                            sending = false,
                            info = if (initial) "Enviámos um código para o teu email" else "Novo código enviado",
                            codeSentOnce = true,
                        )
                    }
                }
                .onFailure { t ->
                    _state.update { it.copy(sending = false, error = t.message ?: "Erro ao enviar código", codeSentOnce = true) }
                }
        }
    }

    fun validate() {
        val s = _state.value
        if (s.code.length != 6) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.validateOtp(s.code)
                .onSuccess { _state.update { it.copy(isLoading = false, validated = true) } }
                .onFailure { t ->
                    val msg = if (t is AuthError.OtpInvalid) "Código inválido ou expirado" else t.message
                    _state.update { it.copy(isLoading = false, error = msg, code = "") }
                }
        }
    }
}
