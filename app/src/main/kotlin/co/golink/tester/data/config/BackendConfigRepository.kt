package co.golink.tester.data.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.configDataStore by preferencesDataStore(name = "backend_config")

@Singleton
class BackendConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val backendUrl: Flow<String> =
        context.configDataStore.data.map { it[KEY_URL] ?: DEFAULT_URL }

    suspend fun currentUrl(): String =
        context.configDataStore.data.map { it[KEY_URL] ?: DEFAULT_URL }.first()

    suspend fun setBackendUrl(url: String) {
        val normalized = normalize(url)
        context.configDataStore.edit { it[KEY_URL] = normalized }
    }

    suspend fun clear() {
        context.configDataStore.edit { it.remove(KEY_URL) }
    }

    companion object {
        const val DEFAULT_URL = "https://golink.co"
        private val KEY_URL = stringPreferencesKey("backend_url")

        fun normalize(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
            else "https://$trimmed"
        }
    }
}
