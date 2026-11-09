package co.golink.tester.data.config

import android.content.Context
import co.golink.tester.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * URL do backend. SharedPreferences simples (leitura síncrona, sem bloquear
 * o arranque num DataStore). Nenhuma versão chegou a gravar o DataStore
 * `backend_config` antigo (o ecrã de configuração nunca esteve numa rota), por
 * isso não há nada a migrar de lá.
 */
@Singleton
class BackendConfigRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("backend_url", Context.MODE_PRIVATE)

    fun currentUrl(): String = prefs.getString(KEY_URL, null) ?: DEFAULT_URL

    /** Só https (http apenas em debug, para um servidor local). */
    fun setBackendUrl(url: String) {
        val normalized = normalize(url)
        require(normalized.startsWith("https://") || BuildConfig.DEBUG) { "https obrigatório" }
        prefs.edit().putString(KEY_URL, normalized).commit()
    }

    fun clear() {
        prefs.edit().remove(KEY_URL).commit()
    }

    companion object {
        const val DEFAULT_URL = "https://golink.co"
        private const val KEY_URL = "backend_url"

        fun normalize(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
            else "https://$trimmed"
        }
    }
}
