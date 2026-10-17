package co.golink.tester.domain.support

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

// Espelha Domain\Support\Resources (cada item vem embrulhado em { data: {...} }).

@Serializable
data class TicketEnvelope(val data: TicketData)

@Serializable
data class TicketData(
    val id: String,
    val attributes: TicketAttributes,
    @Serializable(with = TicketRelationshipsSerializer::class)
    val relationships: TicketRelationships? = null,
    /** Triagem sugerida pelo copiloto (Claude) — só staff; null sem triagem. */
    val ai: TicketAi? = null,
)

/** Só sugestão: o agente decide se aplica categoria/prioridade. */
@Serializable
data class TicketAi(
    val category: String? = null,
    val priority: String? = null,
    val language: String? = null,
    val summary: String? = null,
    val triaged_at: String? = null,
)

@Serializable
data class AiDraftResponse(val draft: String = "")

/** Resposta rápida a partir do centro de ajuda (nada é guardado no servidor). */
@Serializable
data class SupportAssistRequest(val subject: String, val body: String)

@Serializable
data class SupportAssistResponse(
    val answer: String = "",
    val confident: Boolean = false,
    val faq_ids: List<String> = emptyList(),
)

@Serializable
data class AiSummaryResponse(val summary: String = "")

/**
 * O PHP serializa um array vazio como `[]`: servidores antigos mandam
 * `"relationships": []` nas listagens (sem mensagens carregadas). Trata `[]`
 * como objeto vazio para não rebentar a lista.
 */
object TicketRelationshipsSerializer :
    JsonTransformingSerializer<TicketRelationships>(TicketRelationships.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonArray) JsonObject(emptyMap()) else element
}

object MessageRelationshipsSerializer :
    JsonTransformingSerializer<MessageRelationships>(MessageRelationships.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonArray) JsonObject(emptyMap()) else element
}

@Serializable
data class TicketAttributes(
    val subject: String = "",
    val status: String = "open",
    val priority: String = "normal",
    val category: String = "other",
    val user_id: String? = null,
    val user_name: String? = null,
    val user_email: String? = null,
    val user_avatar: String? = null,
    val assigned_to: String? = null,
    val assigned_to_name: String? = null,
    val assigned_to_avatar: String? = null,
    val assigned_to_public_name: String? = null,
    val first_response_at: String? = null,
    val messages_count: Int = 0,
    val unread_count: Int = 0,
    val last_message_at: String? = null,
    val closed_at: String? = null,
    val created_at: String? = null,
    /** Fecho automático se o utilizador não responder (só "pending"). */
    val auto_close_at: String? = null,
    /** A partir daqui fica trancado: não aceita respostas, é preciso abrir outro. */
    val locks_at: String? = null,
    val is_locked: Boolean = false,
)

@Serializable
data class TicketRelationships(
    val messages: List<MessageEnvelope> = emptyList(),
)

@Serializable
data class MessageEnvelope(val data: MessageData)

@Serializable
data class MessageData(
    val id: String,
    val attributes: MessageAttributes,
    @Serializable(with = MessageRelationshipsSerializer::class)
    val relationships: MessageRelationships? = null,
)

@Serializable
data class MessageAttributes(
    val body: String? = null,
    val author_role: String = "user",
    val author_name: String? = null,
    val author_title: String? = null,
    val author_avatar: String? = null,
    val author_is_team: Boolean = false,
    val created_at: String? = null,
    val message_type: String? = null,
    val deletion_request: DeletionRequestState? = null,
    /** Língua detetada e tradução automática (Claude) na língua do agente — só staff. */
    val language: String? = null,
    val translation: String? = null,
)

@Serializable
data class DeletionRequestState(
    val id: String,
    val status: String,
    val expires_at: String? = null,
    val scheduled_for: String? = null,
)

@Serializable
data class MessageRelationships(
    val attachments: List<AttachmentEnvelope> = emptyList(),
)

@Serializable
data class AttachmentEnvelope(val data: AttachmentData)

@Serializable
data class AttachmentData(
    val id: String,
    val attributes: AttachmentAttributes,
)

@Serializable
data class AttachmentAttributes(
    val filename: String = "",
    val mimetype: String? = null,
    val filesize: Long = 0,
    val is_image: Boolean = false,
    val download_url: String = "",
)

@Serializable
data class TicketPage(
    val data: List<TicketEnvelope> = emptyList(),
    val meta: PageMeta? = null,
)

@Serializable
data class PageMeta(
    val current_page: Int = 1,
    val last_page: Int = 1,
)

@Serializable
data class ConfirmDeletionRequest(val password: String)

@Serializable
data class ConfirmDeletionResponse(val scheduled_for: String? = null)

@Serializable
data class UpdateTicketRequest(
    val status: String? = null,
    val priority: String? = null,
    val category: String? = null,
)

@Serializable
data class SupportAgentOption(
    val id: String,
    val name: String,
    val title: String? = null,
    val avatar: String? = null,
)

@Serializable
data class SupportAgentsResponse(val data: List<SupportAgentOption> = emptyList())

object SupportOptions {
    val STATUSES = listOf("open", "pending", "closed")
    val PRIORITIES = listOf("low", "normal", "high")
    val CATEGORIES = listOf("billing", "technical", "other")
}

// --- Support Desk: contagens dos separadores (GET api/admin/support/counts) ---

@Serializable
data class SupportCounts(
    val open: Int = 0,
    val pending: Int = 0,
    val closed: Int = 0,
    val unread: Int = 0,
    val mine: Int = 0,
    val unassigned: Int = 0,
)

// --- Support Desk: dashboard (GET api/admin/support/dashboard?range=7|30|90) ---

@Serializable
data class SupportDashboard(
    val range: Int = 30,
    val kpis: SupportKpis = SupportKpis(),
    val times: SupportTimes = SupportTimes(),
    val series: SupportSeries = SupportSeries(),
    val by_category: Map<String, Int> = emptyMap(),
    val by_priority: Map<String, Int> = emptyMap(),
    val stale_tickets: List<StaleTicket> = emptyList(),
    val agents: List<AgentStats>? = null,
)

@Serializable
data class SupportKpis(
    val awaiting_support: Int = 0,
    val awaiting_user: Int = 0,
    val unassigned: Int = 0,
    val mine: Int = 0,
    val unread: Int = 0,
    val high_priority: Int = 0,
    val stale: Int = 0,
    val created_today: Int = 0,
    val created: Int = 0,
    val closed: Int = 0,
)

@Serializable
data class SupportTimes(
    val avg_first_response_minutes: Int? = null,
    val median_first_response_minutes: Int? = null,
    val responded_within_24h_pct: Int? = null,
    val avg_resolution_hours: Double? = null,
)

@Serializable
data class SupportSeries(
    val labels: List<String> = emptyList(),
    val created: List<Int> = emptyList(),
    val closed: List<Int> = emptyList(),
)

@Serializable
data class StaleTicket(
    val id: String,
    val subject: String = "",
    val priority: String = "normal",
    val user_name: String? = null,
    val last_message_at: String? = null,
)

@Serializable
data class AgentStats(
    val user_id: String,
    val name: String = "",
    val title: String? = null,
    val avatar: String? = null,
    val is_admin: Boolean = false,
    val is_active: Boolean = true,
    val open_assigned: Int = 0,
    val replies: Int = 0,
    val closed: Int = 0,
    val avg_first_response_minutes: Int? = null,
)
