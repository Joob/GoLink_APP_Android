package co.golink.tester.ui.screens.lock

import co.golink.tester.ui.i18n.tr
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.golink.tester.data.AppLogger
import co.golink.tester.data.auth.AuthRepository
import co.golink.tester.data.settings.AppLockBiometricKey
import co.golink.tester.data.settings.AppLockManager
import co.golink.tester.data.settings.AppSecurityPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class LockViewModel @Inject constructor(
    private val securityPrefs: AppSecurityPreferences,
    private val lockManager: AppLockManager,
    private val authRepository: AuthRepository,
    private val logger: AppLogger,
    private val bioKey: AppLockBiometricKey,
) : ViewModel() {

    /** Biometria ligada e utilizável neste momento. */
    val biometricUsable: Boolean get() = lockManager.biometricUsable
    val pinEnabled: Boolean get() = securityPrefs.pinEnabled

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Segundos até poder voltar a tentar o PIN (0 = livre). */
    private val _lockoutSeconds = MutableStateFlow(0)
    val lockoutSeconds: StateFlow<Int> = _lockoutSeconds.asStateFlow()

    private val _verifying = MutableStateFlow(false)
    val verifying: StateFlow<Boolean> = _verifying.asStateFlow()

    private var countdown: kotlinx.coroutines.Job? = null

    init { startLockoutCountdown() }

    /** Verifica fora da main thread (PBKDF2); conta falhas e aplica esperas. */
    fun submitPin(pin: String) {
        if (_verifying.value || _lockoutSeconds.value > 0) return
        _verifying.value = true
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) { securityPrefs.verifyPin(pin) }
            if (ok) {
                securityPrefs.resetFailures()
                // PIN certo prova a identidade: a biometria invalidada pode voltar a valer.
                if (bioKey.invalidated) bioKey.reset()
                logger.log("Security", "Desbloqueado com PIN")
                lockManager.unlock()
            } else {
                val n = securityPrefs.registerFailure()
                logger.log("Security", "PIN errado ($n)")
                if (n >= AppSecurityPreferences.MAX_FAILURES) {
                    logger.log("Security", "Demasiados PINs errados: sessão terminada")
                    securityPrefs.resetFailures()
                    authRepository.logout()
                    lockManager.unlock()
                } else {
                    _error.value = "PIN incorrecto".tr()
                    startLockoutCountdown()
                }
            }
            _verifying.value = false
        }
    }

    /** Chave do Keystore para o CryptoObject (ou invalidada / sem suporte). */
    fun biometricKeyState(): AppLockBiometricKey.State = bioKey.state()

    fun resetBiometricKey(): AppLockBiometricKey.State {
        bioKey.reset()
        return bioKey.state()
    }

    /**
     * [cipher] = o do CryptoObject autenticado; [usedCrypto] = o prompt levou CryptoObject.
     * Devolve false se a chave não abriu (o ecrã volta a mostrar o pedido).
     */
    fun onBiometricSuccess(cipher: javax.crypto.Cipher?, usedCrypto: Boolean): Boolean {
        if (usedCrypto && !bioKey.verify(cipher)) {
            logger.log("Security", "Biometria sem CryptoObject válido")
            return false
        }
        securityPrefs.resetFailures()
        logger.log("Security", "Desbloqueado com biometria")
        lockManager.unlock()
        return true
    }

    /** Sem PIN e biometria invalidada: o código do telemóvel prova a identidade. */
    fun onDeviceCredentialSuccess() {
        bioKey.reset()
        securityPrefs.resetFailures()
        logger.log("Security", "Desbloqueado com o código do telemóvel (biometria alterada)")
        lockManager.unlock()
    }

    fun consumeError() = _error.update { null }

    private fun startLockoutCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            while (isActive) {
                val left = securityPrefs.lockoutRemainingMs()
                _lockoutSeconds.value = ((left + 999) / 1000).toInt()
                if (left <= 0) break
                delay(1000)
            }
        }
    }
}
