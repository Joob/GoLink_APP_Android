package co.golink.tester.data.settings

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AppLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityPrefs: AppSecurityPreferences,
) {
    /** Biometria ligada e utilizável agora (sem impressões registadas não conta). */
    val biometricUsable: Boolean
        get() = securityPrefs.biometricEnabled && canUseBiometric()

    /**
     * Bloqueio ligado: PIN, ou biometria utilizável. Biometria sem impressões
     * e sem PIN não bloqueia (seria um bloqueio sem saída) — igual ao que as
     * Definições mostram. Também esconde a app nos recentes.
     */
    val enabled: Boolean get() = securityPrefs.pinEnabled || biometricUsable

    // Cold start: lock immediately if PIN or biometric is enabled.
    private val _locked = MutableStateFlow(enabled)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    // Ecrã externo aberto pela própria app (câmara, seletor de ficheiros…):
    // voltar dele dentro do prazo não pede o PIN; depois do prazo pede.
    private var expectReturnUntil = 0L
    private var externalOnBackground = false

    fun expectExternalReturn() {
        if (enabled) expectReturnUntil = SystemClock.elapsedRealtime() + EXTERNAL_GRACE_MS
    }

    /** De volta sem passar por onStop (diálogos translúcidos): fecha a exceção. */
    fun onResume() {
        expectReturnUntil = 0L
    }

    /** Esconder a app nos recentes: bloqueio ligado e a opção ativa. */
    val hideInRecents: Boolean get() = enabled && securityPrefs.hideInRecents

    // Momento em que a app foi para segundo plano (0 = já bloqueada / sem bloqueio).
    private var backgroundedAt = 0L

    /**
     * Sair da app (home, trocar de app, ecrã desligado): sem atraso bloqueia
     * logo; com atraso, ou num ecrã externo da própria app, guarda o momento
     * e decide ao voltar (onForeground).
     */
    fun onBackground() {
        backgroundedAt = 0L
        externalOnBackground = false
        if (!enabled) return
        val now = SystemClock.elapsedRealtime()
        externalOnBackground = now < expectReturnUntil
        expectReturnUntil = 0L
        if (!externalOnBackground && securityPrefs.lockDelayMs <= 0L) {
            _locked.value = true
        } else {
            backgroundedAt = now
        }
    }

    fun onForeground() {
        val since = backgroundedAt
        val limit = if (externalOnBackground) EXTERNAL_GRACE_MS else securityPrefs.lockDelayMs
        backgroundedAt = 0L
        externalOnBackground = false
        expectReturnUntil = 0L
        if (enabled && since > 0L && SystemClock.elapsedRealtime() - since >= limit) {
            _locked.value = true
        }
    }

    fun unlock() { _locked.value = false }

    private fun canUseBiometric(): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    private companion object {
        const val EXTERNAL_GRACE_MS = 10 * 60_000L
    }
}
