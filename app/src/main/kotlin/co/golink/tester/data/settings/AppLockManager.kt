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

    /** Esconder a app nos recentes: bloqueio ligado e a opção ativa. */
    val hideInRecents: Boolean get() = enabled && securityPrefs.hideInRecents

    // Momento em que a app foi para segundo plano (só com atraso > 0).
    private var backgroundedAt = 0L

    /**
     * Sair da app (home, trocar de app, ecrã desligado): sem atraso bloqueia
     * logo; com atraso guarda o momento e decide ao voltar (onForeground).
     */
    fun onBackground() {
        backgroundedAt = 0L
        if (!enabled) return
        if (SystemClock.elapsedRealtime() < expectReturnUntil) return
        if (securityPrefs.lockDelayMs <= 0L) {
            _locked.value = true
        } else {
            backgroundedAt = SystemClock.elapsedRealtime()
        }
    }

    fun onForeground() {
        val since = backgroundedAt
        backgroundedAt = 0L
        if (enabled && since > 0L && SystemClock.elapsedRealtime() - since >= securityPrefs.lockDelayMs) {
            _locked.value = true
        }
        expectReturnUntil = 0L
    }

    fun lock() { _locked.value = true }
    fun unlock() { _locked.value = false }
}
