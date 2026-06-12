package co.golink.tester.data.news

import co.golink.tester.data.auth.AuthState
import co.golink.tester.data.auth.SessionManager
import co.golink.tester.domain.news.ImportantNews
import co.golink.tester.domain.news.UpdateSettingRequest
import co.golink.tester.network.NewsApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class NewsRepository @Inject constructor(
    private val api: NewsApi,
    private val sessionManager: SessionManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _news = MutableStateFlow<ImportantNews?>(null)
    val news: StateFlow<ImportantNews?> = _news.asStateFlow()

    // Em memória de propósito: ao reabrir a app a notícia volta a aparecer.
    private val _dismissed = MutableStateFlow(false)
    val dismissed: StateFlow<Boolean> = _dismissed.asStateFlow()

    init {
        scope.launch {
            sessionManager.state.collect { s ->
                if (s is AuthState.Authenticated) {
                    _dismissed.value = false
                    refresh()
                } else {
                    _news.value = null
                }
            }
        }
    }

    fun dismiss() {
        _dismissed.value = true
    }

    suspend fun refresh(): Result<ImportantNews?> = runCatching {
        val response = api.newsSettings()
        if (!response.isSuccessful) error("HTTP ${response.code()}")
        val map = response.body() ?: error("Resposta vazia")
        val allowed = map["allowed_news"].let { it == "1" || it == "true" }
        val message = map["news_message"].orEmpty()
        ImportantNews(allowed = allowed, message = message).also { _news.value = it }
    }

    suspend fun save(message: String, allowed: Boolean): Result<Unit> = runCatching {
        listOf(
            UpdateSettingRequest("news_message", message),
            UpdateSettingRequest("allowed_news", if (allowed) "1" else "0"),
        ).forEach { body ->
            val response = api.updateSetting(body)
            if (!response.isSuccessful) error("HTTP ${response.code()}")
        }
        _news.value = ImportantNews(allowed = allowed, message = message)
        _dismissed.value = false
    }
}
