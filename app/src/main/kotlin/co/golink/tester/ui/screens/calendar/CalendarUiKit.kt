package co.golink.tester.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.domain.calendar.CalEvent
import co.golink.tester.domain.calendar.CalInfo
import co.golink.tester.ui.common.appLocale
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.theme.LocalDarkTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

// Cores dos calendários (a chave fica no servidor; o hex só existe nos clientes,
// igual a services/calendarColors.js).
private val CALENDAR_COLORS = mapOf(
    "green" to Color(0xFF0F8A5F),
    "blue" to Color(0xFF2563EB),
    "pink" to Color(0xFFE5486F),
    "orange" to Color(0xFFD97706),
    "purple" to Color(0xFF8B5CF6),
    "slate" to Color(0xFF64748B),
    "red" to Color(0xFFDC2626),
    "teal" to Color(0xFF0D9488),
    "yellow" to Color(0xFFCA8A04),
)

fun calendarColor(key: String?): Color = CALENDAR_COLORS[key] ?: CALENDAR_COLORS.getValue("green")

/** Feriados: roxo, como na web. */
val HolidayColor = Color(0xFF8B5CF6)

fun eventColor(event: CalEvent, calendars: List<CalInfo>): Color =
    if (event.holiday) HolidayColor else calendarColor(calendars.firstOrNull { it.id == event.calendarId }?.color)

/** Fundo tingido (cartões da agenda, chips de dia inteiro). */
@Composable
fun tint(color: Color): Color = color.copy(alpha = if (LocalDarkTheme.current) 0.22f else 0.12f)

fun eventTitle(event: CalEvent): String = when {
    event.locked -> "Evento encriptado (sem chave)".tr()
    event.data.title.isBlank() -> "(sem título)".tr()
    else -> event.data.title
}

private fun String.cap(): String = replaceFirstChar { it.titlecase(appLocale()) }

fun fmtTime(i: Instant): String =
    i.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm", appLocale()))

/** "Outubro". */
fun fmtMonthName(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("LLLL", appLocale())).cap()

/** "Quarta, 28 de outubro" (PT) / "Wednesday, 28 October". */
fun fmtLongDay(d: LocalDate): String {
    val wd = d.dayOfWeek.getDisplayName(TextStyle.FULL, appLocale()).cap().substringBefore('-')
    val rest = d.format(DateTimeFormatter.ofPattern(if (appLocale().language == "pt") "d 'de' MMMM" else "d MMMM", appLocale()))
    return "$wd, $rest"
}

/** "QUA" (curto, maiúsculas). */
fun fmtWeekdayShort(d: LocalDate): String =
    d.dayOfWeek.getDisplayName(TextStyle.SHORT, appLocale()).replace(".", "").uppercase(appLocale())

/** "Quarta, 28 outubro" para a folha de detalhe. */
fun fmtDayMonth(d: LocalDate): String {
    val wd = d.dayOfWeek.getDisplayName(TextStyle.FULL, appLocale()).cap().substringBefore('-')
    return "$wd, " + d.format(DateTimeFormatter.ofPattern("d MMMM", appLocale()))
}

/** Letras dos dias (S T Q Q S S D), a começar à segunda. */
fun weekdayLetters(): List<String> =
    DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, appLocale()).uppercase(appLocale()) }

/** "08:30 – 09:30", "Todo o dia" ou com datas quando passa a meia-noite. */
fun fmtSpan(event: CalEvent, start: Instant, end: Instant): String {
    val zone = ZoneId.systemDefault()
    if (event.allDay) {
        val first = start.atZone(zone).toLocalDate()
        val last = end.atZone(zone).toLocalDate().minusDays(1)
        return if (!last.isAfter(first)) "Todo o dia".tr() else "${shortDate(first)} – ${shortDate(last)}"
    }
    val sd = start.atZone(zone).toLocalDate()
    val ed = end.atZone(zone).toLocalDate()
    return if (sd == ed || (ed == sd.plusDays(1) && end == ed.atStartOfDay(zone).toInstant())) {
        "${fmtTime(start)} – ${fmtTime(end)}"
    } else {
        "${shortDate(sd)} ${fmtTime(start)} – ${shortDate(ed)} ${fmtTime(end)}"
    }
}

fun shortDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM", appLocale()))

fun repeatLabel(repeat: String): String = when (repeat) {
    "daily" -> "Diário"
    "weekly" -> "Semanal"
    "monthly" -> "Mensal"
    "yearly" -> "Anual"
    else -> "Não se repete"
}.tr()

fun alertLabel(minutes: Int, long: Boolean = false): String = when (minutes) {
    0 -> if (long) "Na hora do evento" else "Na hora"
    5 -> if (long) "5 minutos antes" else "5 min antes"
    15 -> if (long) "15 minutos antes" else "15 min antes"
    60 -> "1 hora antes"
    1440 -> "1 dia antes"
    else -> "$minutes min"
}.tr()

/** Segmentado (Mês/Agenda/Dia), igual ao .ui-segmented da web. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(value) },
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
