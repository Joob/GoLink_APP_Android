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
    /** Cooldown do servidor ativo: o ecrã mostra os segundos que faltam. */
    val cooldown: Boolean = false,
)

@HiltViewModel
class OtpViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OtpUiState())
    val state: StateFlow<OtpUiState> = _state.asStateFlow()

    private var countdownJob: Job? = null
    private var cancelled = false

    init {
        sendCode(initial = true)
    }

    fun onCode(value: String) {
        // Expirado ou já a validar: ignora (evita pedidos duplicados).
        if (_state.value.expired || _state.value.isLoading) return
        val digits = value.filter { it.isDigit() }.take(6)
        _state.update { it.copy(code = digits, error = null) }
        if (digits.length == 6) validate()
    }

    /** Sair do ecrã OTP: termina a sessão pendente (o gate volta ao início). */
    fun cancel() {
        if (cancelled) return
        cancelled = true
        countdownJob?.cancel()
        viewModelScope.launch { authRepository.logout() }
    }

    /** 401 sem sessão (token pendente expirado/revogado): sair em vez de ficar preso. */
    private fun handleUnauthenticated(t: Throwable): Boolean {
        if (t !is AuthError.InvalidCredentials) return false
        _state.update {
            it.copy(isLoading = false, sending = false, code = "", error = "Sessão expirada. Inicia sessão novamente.".tr())
        }
        cancel()
        return true
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
                    if (handleUnauthenticated(t)) return@onFailure
                    if (t is AuthError.OtpCooldown) {
                        _state.update { it.copy(sending = false, codeSentOnce = true, code = "", info = null) }
                        if (t.rateLimited) startWait(t.remainingSeconds, t.message) else startCooldown(t.remainingSeconds, t.message)
                        return@onFailure
                    }
                    // Erro do servidor (5xx): mensagem clara em vez de "Server Error (HTTP 500)".
                    val msg = if (t is AuthError.Server && t.statusCode >= 500) {
                        "Não foi possível enviar o código. Tenta novamente daqui a pouco.".tr()
                    } else {
                        co.golink.tester.ui.i18n.ServerMessages.localize(t.message) ?: "Erro ao enviar código".tr()
                    }
                    _state.update { it.copy(sending = false, error = msg, codeSentOnce = true) }
                }
        }
    }

    // Matches the server-side 2-minute code validity. When it reaches zero the
    // entered code is cleared and the user is asked to request a new one.
    private fun startCountdown(seconds: Int = VALIDITY_SECONDS) {
        countdownJob?.cancel()
        _state.update { it.copy(remainingSeconds = seconds, expired = false, cooldown = false) }
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
    private fun startCooldown(seconds: Int, message: String?) {
        if (seconds <= 0) {
            // Throttle sem tempo conhecido: pelo menos mostrar o erro.
            _state.update {
                it.copy(error = co.golink.tester.ui.i18n.ServerMessages.localize(message) ?: "Demasiadas tentativas".tr())
            }
            return
        }
        countdownJob?.cancel()
        _state.update { it.copy(remainingSeconds = seconds, expired = true, cooldown = true, error = null) }
        countdownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000)
                remaining -= 1
                _state.update { it.copy(remainingSeconds = remaining) }
            }
            _state.update { it.copy(remainingSeconds = 0, cooldown = false) }
        }
    }

    /**
     * rate_limited no envio: não há código válido e ainda não se pode pedir
     * outro. Mostra a mensagem do servidor e trava só o reenvio.
     */
    private fun startWait(seconds: Int, message: String?) {
        countdownJob?.cancel()
        _state.update {
            it.copy(
                remainingSeconds = seconds.coerceAtLeast(0),
                expired = true,
                cooldown = false,
                error = co.golink.tester.ui.i18n.ServerMessages.localize(message) ?: "Aguarda antes de pedir outro código.".tr(),
            )
        }
        if (seconds <= 0) return
        countdownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000)
                remaining -= 1
                _state.update { it.copy(remainingSeconds = remaining) }
            }
            _state.update { it.copy(remainingSeconds = 0, error = null) }
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
                    if (handleUnauthenticated(t)) return@onFailure
                    // Cooldown do servidor (5 tentativas falhadas): trava a
                    // entrada e faz a contagem decrescente até poder tentar de novo.
                    if (t is AuthError.OtpCooldown) {
                        _state.update { it.copy(isLoading = false, code = "") }
                        startCooldown(t.remainingSeconds, t.message)
                        return@onFailure
                    }

                    val msg = when {
                        t is AuthError.OtpInvalid -> "Código inválido ou expirado".tr()
                        t is AuthError.Server && t.statusCode >= 500 ->
                            "Não foi possível validar o código. Tenta novamente daqui a pouco.".tr()
                        else -> co.golink.tester.ui.i18n.ServerMessages.localize(t.message)
                            ?: "Erro ao validar o código".tr()
                    }
                    _state.update { it.copy(isLoading = false, error = msg, code = "") }
                }
        }
    }

    companion object {
        private const val VALIDITY_SECONDS = 120
    }
}
