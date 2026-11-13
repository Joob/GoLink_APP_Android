package co.golink.tester.data.legacy

import android.content.Context
import androidx.core.content.edit
import co.golink.tester.data.auth.TokenStore
import co.golink.tester.network.LegacyApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Sinal de vida do Legacy: conta só quando o utilizador abre a app. Os backups em
 * segundo plano não passam por aqui, senão um telemóvel ligado adiaria o cofre para
 * sempre. Sem cofre, o servidor ignora o pedido.
 */
@Singleton
class LegacyCheckin @Inject constructor(
    @ApplicationContext context: Context,
    private val api: LegacyApi,
    private val tokenStore: TokenStore,
) {
    private val prefs = context.getSharedPreferences("legacy_checkin", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Versões antigas guardavam uma chave `last_<token>` por sessão e nunca a limpavam.
        scope.launch {
            val stale = prefs.all.keys.filter { it.startsWith("last_") }
            if (stale.isNotEmpty()) prefs.edit { stale.forEach(::remove) }
        }
    }

    fun onForeground() {
        val session = tokenStore.state.value
        val token = session.token ?: return
        if (!session.otpValidated) return

        // Um só registo, da sessão atual: outra conta (ou um novo login) não herda o
        // intervalo da anterior e nada se acumula.
        val owner = token.hashCode()
        val now = System.currentTimeMillis()
        val last = if (prefs.getInt(KEY_OWNER, 0) == owner) prefs.getLong(KEY_LAST, 0L) else 0L
        if (now - last < THROTTLE_MS) return

        scope.launch {
            val sent = runCatching { api.checkin(buildJsonObject { put("source", "android") }).isSuccessful }
                .getOrDefault(false)
            if (sent) prefs.edit { putInt(KEY_OWNER, owner); putLong(KEY_LAST, now) }
        }
    }

    private companion object {
        const val THROTTLE_MS = 6 * 60 * 60 * 1000L
        const val KEY_OWNER = "owner"
        const val KEY_LAST = "last"
    }
}
