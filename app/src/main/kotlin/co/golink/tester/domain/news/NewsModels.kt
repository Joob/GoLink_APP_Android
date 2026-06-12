package co.golink.tester.domain.news

import kotlinx.serialization.Serializable

/**
 * Notícias importantes — usa as settings do VueFileManager:
 *  GET   /api/settings?column=allowed_news|news_message  (público)
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
)
