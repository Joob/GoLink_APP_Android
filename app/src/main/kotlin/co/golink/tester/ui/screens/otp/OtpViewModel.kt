package co.golink.tester.ui.screens.otp

import co.golink.tester.ui.i18n.tr
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.auth.AuthRepository
import co.golink.tester.domain.auth.AuthError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val remainingSeconds: Int = 0,
    val expired: Boolean = false,
)

@HiltViewModel
class OtpViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OtpUiState())
    val state: StateFlow<OtpUiState> = _state.asStateFlow()

    private var countdownJob: Job? = null

    init {
        sendCode(initial = true)
    }

    fun onCode(value: String) {
        if (_state.value.expired) return
        val digits = value.filter { it.isDigit() }.take(6)
        _state.update { it.copy(code = digits, error = null) }
        if (digits.length == 6) validate()
    }

    fun sendCode(initial: Boolean = false) {
        // Block resend while the current code is still valid (matches countdown).
        if (!initial && _state.value.remainingSeconds > 0) return
        _state.update { it.copy(sending = true, error = null, info = null) }
        viewModelScope.launch {
            authRepository.sendOtp()
                .onSuccess {
                    _state.update {
                        it.copy(
                            sending = false,
                            info = if (initial) "Enviámos um código para o teu email".tr() else "Novo código enviado".tr(),
                            codeSentOnce = true,
                            code = "",
                        )
                    }
                    // Seed the countdown from the server's real remaining validity:
                    // a resend within the 2-min window is a no-op server-side
                    // (still-valid code), so the timer must reflect the actual
                    // time left, not restart at 2:00.
                    val remaining = authRepository.otpStatus().getOrNull()
                        ?.remaining_validity?.toInt()
                        ?.takeIf { it > 0 }
                        ?: VALIDITY_SECONDS
                    startCountdown(remaining)
                }
                .onFailure { t ->
                    _state.update { it.copy(sending = false, error = t.message ?: "Erro ao enviar código".tr(), codeSentOnce = true) }
                }
        }
    }

    // Matches the server-side 2-minute code validity. When it reaches zero the
    // entered code is cleared and the user is asked to request a new one.
    private fun startCountdown(seconds: Int = VALIDITY_SECONDS) {
        countdownJob?.cancel()
        _state.update { it.copy(remainingSeconds = seconds, expired = false) }
        countdownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000)
                remaining -= 1
                _state.update { it.copy(remainingSeconds = remaining) }
            }
            _state.update { it.copy(expired = true, remainingSeconds = 0, code = "", error = null) }
        }
    }

    /**
     * Cooldown imposto pelo servidor após 5 tentativas falhadas. Marca o código
     * como expirado (bloqueia a entrada) e só liberta o reenvio no fim.
     */
    private fun startCooldown(seconds: Int) {
        if (seconds <= 0) return
        countdownJob?.cancel()
        _state.update { it.copy(remainingSeconds = seconds, expired = true) }
        countdownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000)
                remaining -= 1
                _state.update { it.copy(remainingSeconds = remaining) }
            }
            _state.update { it.copy(remainingSeconds = 0) }
        }
    }

    fun validate() {
        val s = _state.value
        if (s.code.length != 6) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.validateOtp(s.code)
                .onSuccess {
                    countdownJob?.cancel()
                    _state.update { it.copy(isLoading = false, validated = true) }
                }
                .onFailure { t ->
                    // Cooldown do servidor (5 tentativas falhadas): trava a
                    // entrada e faz a contagem decrescente até poder tentar de novo.
                    if (t is AuthError.OtpCooldown) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                code = "",
                                error = "Demasiadas tentativas. Aguarda %d s.".tr().format(t.remainingSeconds),
                            )
                        }
                        startCooldown(t.remainingSeconds)
                        return@onFailure
                    }

                    val msg = if (t is AuthError.OtpInvalid) "Código inválido ou expirado".tr() else t.message
                    _state.update { it.copy(isLoading = false, error = msg, code = "") }
                }
        }
    }

    companion object {
        private const val VALIDITY_SECONDS = 120
    }
}
