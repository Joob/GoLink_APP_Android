package co.golink.tester.ui.screens.lock

import androidx.lifecycle.ViewModel
import co.golink.tester.data.AppLogger
import co.golink.tester.data.settings.AppLockManager
import co.golink.tester.data.settings.AppSecurityPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class LockViewModel @Inject constructor(
    private val securityPrefs: AppSecurityPreferences,
    private val lockManager: AppLockManager,
    private val logger: AppLogger,
) : ViewModel() {

    val biometricEnabled: Boolean get() = securityPrefs.biometricEnabled
    val pinEnabled: Boolean get() = securityPrefs.pinEnabled

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun verifyPin(pin: String): Boolean {
        return if (securityPrefs.verifyPin(pin)) {
            logger.log("Security", "Desbloqueado com PIN")
            lockManager.unlock()
            true
        } else {
            _error.value = "PIN incorrecto"
            false
        }
    }

    fun onBiometricSuccess() {
        logger.log("Security", "Desbloqueado com biometria")
        lockManager.unlock()
    }

    fun consumeError() = _error.update { null }
}
