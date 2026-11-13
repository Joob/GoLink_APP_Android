package co.golink.tester.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Estado da ligação para toda a app: alimenta o aviso "Estás offline" e as
 * esperas dos uploads (esperar que a rede volte, contagem decrescente até à
 * próxima tentativa, botão "Ligar agora").
 */
@Singleton
class ConnectivityMonitor @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var unvalidatedSince = 0L

    private val _online = MutableStateFlow(currentlyOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    /** Instante (ms) da próxima tentativa automática, enquanto há uma espera em curso. */
    private val _retryAt = MutableStateFlow<Long?>(null)
    val retryAt: StateFlow<Long?> = _retryAt.asStateFlow()

    private val retryNowSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { _online.value = currentlyOnline() }
                override fun onLost(network: Network) { _online.value = currentlyOnline() }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _online.value = isUsable(caps)
                }
            })
        }
    }

    fun isOnline(): Boolean = currentlyOnline()

    private fun currentlyOnline(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps == null) {
            unvalidatedSince = 0L
            return false
        }
        return isUsable(caps)
    }

    /**
     * Online = INTERNET e VALIDATED (um captive portal não conta). Recurso: há
     * redes onde a validação do Android nunca chega (sondagem bloqueada); sem
     * portal detetado, passam a contar ao fim de [VALIDATION_GRACE_MS].
     */
    private fun isUsable(caps: NetworkCapabilities): Boolean {
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            unvalidatedSince = 0L
            return false
        }
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            unvalidatedSince = 0L
            return true
        }
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) return false
        val now = SystemClock.elapsedRealtime()
        if (unvalidatedSince == 0L) {
            unvalidatedSince = now
            // Reavaliar quando acabar a espera (o sistema pode não voltar a avisar).
            mainHandler.postDelayed({ _online.value = currentlyOnline() }, VALIDATION_GRACE_MS)
        }
        return now - unvalidatedSince >= VALIDATION_GRACE_MS
    }

    /** Espera que a rede volte. false se passar o limite. */
    suspend fun awaitOnline(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) { online.first { it } } != null

    /**
     * Espera [ms] antes de uma nova tentativa — termina mais cedo se o
     * utilizador tocar em "Ligar agora" ou se a rede acabar de voltar.
     */
    suspend fun delayOrRetryNow(ms: Long) {
        val target = System.currentTimeMillis() + ms
        _retryAt.value = maxOf(_retryAt.value ?: 0L, target)
        try {
            withTimeoutOrNull(ms) {
                // drop(1): ignora o estado atual; só conta a rede a VOLTAR.
                merge(retryNowSignal, online.drop(1).filter { it }.map { }).first()
            }
        } finally {
            if (_retryAt.value == target) _retryAt.value = null
        }
    }

    fun retryNow() {
        retryNowSignal.tryEmit(Unit)
    }

    private companion object {
        const val VALIDATION_GRACE_MS = 10_000L
    }
}
