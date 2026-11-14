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
        get() = securityPrefs.biometricEnabled && biometricHw

    // canAuthenticate() é um binder: em cache, renovado a cada ON_START (impressões
    // registadas/removidas nas definições do sistema obrigam a sair da app).
    @Volatile private var biometricHw = canUseBiometric()

    private fun refreshBiometric() { biometricHw = canUseBiometric() }

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

    // Camada opaca sobre o conteúdo entre ON_PAUSE e ON_RESUME: o sistema tira a
    // captura da tarefa ao sair e mostrava-a ao voltar antes do bloqueio desenhar.
    private val _covered = MutableStateFlow(false)
    val covered: StateFlow<Boolean> = _covered.asStateFlow()

    fun onPause() {
        // Já bloqueada: a janela do bloqueio tapa tudo. O BiometricPrompt pausa a
        // atividade e a camada opaca deixava o desfoque sem nada por trás.
        if (enabled && !_locked.value) _covered.value = true
    }

    /** De volta sem passar por onStop (diálogos translúcidos): fecha a exceção. */
    fun onResume() {
        expectReturnUntil = 0L
        _covered.value = false
    }

    /** Esconder a app nos recentes: bloqueio ligado e a opção ativa. */
    val hideInRecents: Boolean get() = enabled && securityPrefs.hideInRecents

    /** Sem atraso: sair bloqueia sempre, por isso a captura nunca deve mostrar conteúdo. */
    val locksOnLeave: Boolean get() = enabled && securityPrefs.lockDelayMs <= 0L

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
        refreshBiometric()
        // O bloqueio deixou de existir (impressões removidas, sem PIN): sem isto ficava
        // preso no ecrã de impressão digital sem saída (antes do Android 11).
        if (!enabled) _locked.value = false
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

    // Desbloqueio feito no ecrã do preenchimento (Pass): vale uns minutos para os pedidos
    // seguintes, sem destrancar a app em si.
    @Volatile private var autofillGraceUntil = 0L

    /**
     * Preenchimento automático noutra app: o bloqueio conta como ativo se a app está
     * bloqueada ou se já passou o atraso desde que foi para segundo plano (o onForeground
     * que o aplicaria ainda não correu). Arranque a frio pelo serviço: [_locked] = [enabled].
     */
    fun lockedForAutofill(): Boolean {
        if (!enabled) return false
        val now = SystemClock.elapsedRealtime()
        if (!_locked.value) {
            val since = backgroundedAt
            val limit = if (externalOnBackground) EXTERNAL_GRACE_MS else securityPrefs.lockDelayMs
            if (since > 0L && now - since >= limit) _locked.value = true
        }
        return _locked.value && now >= autofillGraceUntil
    }

    fun grantAutofill() {
        if (enabled) autofillGraceUntil = SystemClock.elapsedRealtime() + AUTOFILL_GRACE_MS
    }

    private fun canUseBiometric(): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    private companion object {
        // Câmara/seletor abertos pela app: 2 min (antes 10) para voltar sem PIN.
        const val EXTERNAL_GRACE_MS = 2 * 60_000L
        const val AUTOFILL_GRACE_MS = 2 * 60_000L
    }
}
