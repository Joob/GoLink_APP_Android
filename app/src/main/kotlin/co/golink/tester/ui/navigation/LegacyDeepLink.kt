package co.golink.tester.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Convite de guardião aberto sem sessão: depois do login, ir direto ao separador Guarding. */
object LegacyDeepLink {
    private const val TTL_MS = 10 * 60_000L

    private val _guarding = MutableStateFlow(false)
    val guarding: StateFlow<Boolean> = _guarding
    @Volatile private var requestedAt = 0L

    fun requestGuarding() {
        requestedAt = android.os.SystemClock.elapsedRealtime()
        _guarding.value = true
    }

    /** @return se o pedido ainda vale: um login desistido não pode saltar para o Guarding muito depois. */
    fun consume(): Boolean {
        val fresh = _guarding.value && android.os.SystemClock.elapsedRealtime() - requestedAt < TTL_MS
        _guarding.value = false
        return fresh
    }
}
