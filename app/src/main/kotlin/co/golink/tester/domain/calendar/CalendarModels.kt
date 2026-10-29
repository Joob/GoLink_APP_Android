package co.golink.tester.domain.calendar

import java.time.Instant
import kotlinx.serialization.Serializable
import java.time.ZoneId

/** Calendário já decifrado (K_cal em memória; limpa no lock do E2E). */
data class CalInfo(
    val id: String,
    val ownerId: String?,
    val kind: String,
    val color: String,
    val name: String,
    val position: Int,
    val permission: String,
    val visible: Boolean,
    val members: List<CalendarMemberDto>,
    val locked: Boolean,
    val key: ByteArray?,
) {
    val isOwner: Boolean get() = permission == "owner"
    val canEdit: Boolean get() = !locked && (permission == "owner" || permission == "can-edit")
    val isBirthdays: Boolean get() = kind == "birthdays"

    // Chave fora da igualdade: o Compose só recompõe quando muda o que se vê.
    override fun equals(other: Any?): Boolean =
        other is CalInfo && other.id == id && other.color == color && other.name == name &&
            other.visible == visible && other.permission == permission && other.members == members &&
            other.locked == locked && other.position == position

    override fun hashCode(): Int = id.hashCode()
}

/** Evento decifrado. Dia inteiro: [start]/[end] = meia-noite local, fim exclusivo. */
data class CalEvent(
    val raw: EventDto,
    val start: Instant,
    val end: Instant,
    val data: EventPayload,
    val locked: Boolean,
    val key: ByteArray?,
    /** Feriado (só leitura, não vem do servidor). */
    val holiday: Boolean = false,
) {
    val id: String get() = raw.id
    val calendarId: String get() = raw.calendar_id
    val allDay: Boolean get() = raw.all_day
    val repeat: String get() = raw.repeat
    val repeatUntil: String? get() = raw.repeat_until
    val repeatRule: RepeatRule by lazy { Recurrence.rule(raw.repeat, raw.repeat_interval, raw.repeat_days, raw.repeat_count) }
    val canEdit: Boolean get() = !holiday && raw.can_edit && !locked
    val canManage: Boolean get() = !holiday && raw.can_manage && !locked
    val isGuest: Boolean get() = raw.role == "guest"

    /** Dia inteiro conta no fuso do aparelho; o resto no fuso do evento. */
    val zone: ZoneId get() = if (allDay) ZoneId.systemDefault() else CalendarDates.zoneOf(raw.timezone)

    override fun equals(other: Any?): Boolean =
        other is CalEvent && other.raw == raw && other.data == data && other.locked == locked &&
            other.start == start && other.end == end && other.holiday == holiday

    override fun hashCode(): Int = raw.id.hashCode()
}

/** Uma ocorrência desenhada no ecrã. */
data class CalInstance(val event: CalEvent, val start: Instant, val end: Instant, val key: String)

object CalendarConst {
    val ALERTS = listOf(0, 5, 15, 60, 1440)
    val REPEATS = listOf("none", "daily", "weekly", "monthly", "yearly")
    val COLORS = listOf("green", "blue", "pink", "orange", "purple", "slate", "red", "teal", "yellow")
}

/** Um alerta agendado no aparelho. O título decifrado fica só no índice cifrado. */
@Serializable
data class CalendarAlarm(
    /** "{eventId}@{inícioMs}#{minutos}" — único por ocorrência e alerta. */
    val id: String,
    val eventId: String,
    val date: String,
    val fireAt: Long,
    val startAt: Long,
    val minutes: Int,
    val allDay: Boolean,
    val title: String,
)
