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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        val translations = parseTranslations(map["news_message_translations"])
        ImportantNews(allowed = allowed, message = message, translations = translations).also { _news.value = it }
    }

    // Formato gravado pelo painel web: {"pt": {"message": "..."}, "en": {...}}
    private fun parseTranslations(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            Json.parseToJsonElement(raw).jsonObject.mapNotNull { (lang, value) ->
                value.jsonObject["message"]?.jsonPrimitive?.contentOrNull?.let { lang to it }
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    suspend fun save(message: String, allowed: Boolean): Result<Unit> = runCatching {
        val previous = _news.value
        // Mensagem alterada: as traduções deixam de corresponder e apagam-se
        // (mesma regra do painel web).
        val messageChanged = previous == null || previous.message != message
        buildList {
            add(UpdateSettingRequest("news_message", message))
            add(UpdateSettingRequest("allowed_news", if (allowed) "1" else "0"))
            if (messageChanged) add(UpdateSettingRequest("news_message_translations", ""))
        }.forEach { body ->
            val response = api.updateSetting(body)
            if (!response.isSuccessful) error("HTTP ${response.code()}")
        }
        _news.value = ImportantNews(
            allowed = allowed,
            message = message,
            translations = if (messageChanged) emptyMap() else previous?.translations.orEmpty(),
        )
        _dismissed.value = false
    }
}
