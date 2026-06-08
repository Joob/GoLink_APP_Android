package co.golink.tester.domain.notifications

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class NotificationsResponse(
    val data: List<NotificationEnvelope> = emptyList(),
)

@Serializable
data class NotificationEnvelope(
    val data: NotificationData,
)

@Serializable
data class NotificationData(
    val id: String,
    val type: String,
    val attributes: NotificationAttributes,
)

@Serializable
data class NotificationAttributes(
    val category: String? = null,
    val title: String? = null,
    val description: String? = null,
    val action: JsonElement? = null,
    val created_at: String? = null,
    val read_at: String? = null,
)

data class Notification(
    val id: String,
    val category: String?,
    val title: String,
    val description: String,
    val actionType: String?,
    val createdAt: String?,
    val isRead: Boolean,
) {
    companion object {
        fun fromEnvelope(envelope: NotificationEnvelope): Notification {
            val attrs = envelope.data.attributes
            return Notification(
                id = envelope.data.id,
                category = attrs.category,
                title = attrs.title.orEmpty(),
                description = attrs.description.orEmpty(),
                actionType = extractActionType(attrs.action),
                createdAt = attrs.created_at,
                isRead = attrs.read_at != null,
            )
        }

        private fun extractActionType(action: JsonElement?): String? = when (action) {
            null -> null
            is JsonPrimitive -> if (action.isString) runCatching { action.content }.getOrNull() else null
            is JsonObject -> (action["type"] as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.let { runCatching { it.content }.getOrNull() }
            else -> null
        }
    }
}
