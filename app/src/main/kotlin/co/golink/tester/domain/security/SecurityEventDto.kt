package co.golink.tester.domain.security

import kotlinx.serialization.Serializable

@Serializable
data class SecurityEventsResponse(
    val data: List<SecurityEventEnvelope> = emptyList(),
)

@Serializable
data class SecurityEventEnvelope(
    val data: SecurityEventData,
)

@Serializable
data class SecurityEventData(
    val id: String,
    val attributes: SecurityEventAttributes,
)

@Serializable
data class SecurityEventAttributes(
    val event: String? = null,
    val ip: String? = null,
    val user_agent: String? = null,
    val created_at: String? = null,
)

data class SecurityEvent(
    val id: String,
    val event: String,
    val ip: String?,
    val userAgent: String?,
    val createdAt: String?,
) {
    companion object {
        fun fromEnvelope(envelope: SecurityEventEnvelope): SecurityEvent {
            val attrs = envelope.data.attributes
            return SecurityEvent(
                id = envelope.data.id,
                event = attrs.event.orEmpty(),
                ip = attrs.ip,
                userAgent = attrs.user_agent,
                createdAt = attrs.created_at,
            )
        }
    }
}
