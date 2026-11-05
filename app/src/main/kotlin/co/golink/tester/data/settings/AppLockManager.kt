package co.golink.tester.data.settings

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AppLockManager @Inject constructor(
    private val securityPrefs: AppSecurityPreferences,
) {
    /** Bloqueio ligado (PIN ou biometria): também esconde a app nos recentes. */
    val enabled: Boolean get() = securityPrefs.pinEnabled || securityPrefs.biometricEnabled

    // Cold start: lock immediately if PIN or biometric is enabled.
    private val _locked = MutableStateFlow(enabled)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    // Ecrã externo aberto pela própria app (câmara, seletor de ficheiros…):
    // voltar dele não pede o PIN. Expira para não ficar aberto indefinidamente.
    private var expectReturnUntil = 0L

    fun expectExternalReturn() {
        if (enabled) expectReturnUntil = SystemClock.elapsedRealtime() + 10 * 60_000L
    }

    /** Sair da app (home, trocar de app, ecrã desligado): bloqueia logo. */
    fun onBackground() {
        if (!enabled) return
        if (SystemClock.elapsedRealtime() < expectReturnUntil) return
        _locked.value = true
    }

    fun onForeground() {
        expectReturnUntil = 0L
    }

    fun lock() { _locked.value = true }
    fun unlock() { _locked.value = false }
}
