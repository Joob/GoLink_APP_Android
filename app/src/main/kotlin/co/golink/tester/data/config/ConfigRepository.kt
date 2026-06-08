package co.golink.tester.data.config

import co.golink.tester.domain.config.AppConfigResponse
import co.golink.tester.network.ConfigApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ConfigRepository @Inject constructor(
    private val api: ConfigApi,
) {
    private val mutex = Mutex()
    private val _config = MutableStateFlow<AppConfigResponse?>(null)
    val config: StateFlow<AppConfigResponse?> = _config.asStateFlow()

    suspend fun load(force: Boolean = false): Result<AppConfigResponse> = mutex.withLock {
        if (!force) _config.value?.let { return@withLock Result.success(it) }
        runCatching {
            val response = api.config()
            check(response.isSuccessful) { "Falha ao obter config (HTTP ${response.code()})" }
            val body = response.body() ?: error("Resposta vazia")
            _config.value = body
            body
        }
    }

    fun invalidate() { _config.value = null }
}
