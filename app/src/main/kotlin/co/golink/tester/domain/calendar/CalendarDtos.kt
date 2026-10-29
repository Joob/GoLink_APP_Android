package co.golink.tester.domain.calendar

import kotlinx.serialization.Serializable

// Respostas da API do calendário (routes/calendar.php). Os pedidos de escrita
// vão como JsonObject: o servidor distingue "campo ausente" de "null"
// (ex.: guests/files só se mexem quando vêm no corpo).

@Serializable
data class CalendarListResponse(val data: List<CalendarDto> = emptyList())

@Serializable
data class CalendarDto(
    val id: String,
    val owner_id: String? = null,
    val kind: String = "personal",
    val color: String = "green",
    val name_encrypted: String? = null,
    val position: Int = 0,
    val permission: String = "can-view",
    val wrapped_key: String? = null,
    val visible: Boolean = true,
    val members: List<CalendarMemberDto> = emptyList(),
)

@Serializable
data class CalendarMemberDto(
    val user_id: String,
    val name: String? = null,
    val email: String? = null,
    val permission: String = "can-view",
)

@Serializable
data class CreatedIdResponse(val data: CreatedId? = null)

@Serializable
data class CreatedId(val id: String)

@Serializable
data class EventListResponse(val data: List<EventDto> = emptyList())

@Serializable
data class EventResponse(val data: EventDto? = null, val type: String? = null)

@Serializable
data class EventDto(
    val id: String,
    val calendar_id: String,
    val created_by: String? = null,
    val organizer: EventPersonDto? = null,
    val starts_at: String,
    val ends_at: String,
    val all_day: Boolean = false,
    val timezone: String = "UTC",
    val repeat: String = "none",
    val repeat_until: String? = null,
    val alerts: List<Int>? = null,
    val guests_can_edit: Boolean = false,
    val payload: String? = null,
    val key_cal: String? = null,
    val version: Int = 1,
    val role: String = "member",
    val can_edit: Boolean = false,
    val can_manage: Boolean = false,
    val me: EventMeDto? = null,
    val guests: List<EventGuestDto> = emptyList(),
    val files: List<EventFileDto> = emptyList(),
    val updated_at: String? = null,
)

@Serializable
data class EventPersonDto(val id: String? = null, val name: String? = null, val email: String? = null)

@Serializable
data class EventMeDto(val id: String? = null, val status: String = "pending", val wrapped_key: String? = null)

@Serializable
data class EventGuestDto(
    val email: String,
    val user_id: String? = null,
    val name: String? = null,
    val status: String = "pending",
    val sealed: Boolean = false,
)

@Serializable
data class EventFileDto(val file_id: String, val share_token: String? = null)

@Serializable
data class LookupKeysRequest(val emails: List<String>)

@Serializable
data class LookupKeysResponse(val data: Map<String, LookupKey?> = emptyMap())

@Serializable
data class LookupKey(val user_id: String, val public_key: String)

/** Conteúdo cifrado do evento (payload). `notes`, não `description`. */
@Serializable
data class EventPayload(
    val title: String = "",
    val location: String = "",
    val notes: String = "",
    val attachments: List<EventAttachment> = emptyList(),
)

@Serializable
data class EventAttachment(
    val file_id: String,
    val name: String = "",
    val mime: String? = null,
    val parent_id: String? = null,
    val share_url: String? = null,
)
