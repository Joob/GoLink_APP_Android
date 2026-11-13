package co.golink.tester.domain.news

import kotlinx.serialization.Serializable

/**
 * Notícias importantes — usa as settings do VueFileManager:
 *  GET   /api/settings?column=allowed_news|news_message|news_message_translations  (público)
 *  PATCH /api/admin/settings {name, value}               (admin)
 */
@Serializable
data class UpdateSettingRequest(
    val name: String,
    val value: String?,
)

data class ImportantNews(
    val allowed: Boolean,
    val message: String,
    // Traduções feitas no painel web: código da língua -> mensagem
    val translations: Map<String, String> = emptyMap(),
) {
    /** Mensagem na língua da app; sem tradução cai para o original. */
    fun messageFor(lang: String): String = translations[lang]?.takeIf { it.isNotBlank() } ?: message
}
