package co.golink.tester.ui.screens.legacy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.domain.legacy.LegacyItem
import co.golink.tester.domain.legacy.LegacyMeta
import co.golink.tester.domain.legacy.LegacyTimelineDto
import co.golink.tester.domain.legacy.LegacyVaultDto
import co.golink.tester.ui.i18n.I18n
import co.golink.tester.ui.i18n.lt
import co.golink.tester.ui.theme.LocalDarkTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// ---- formatação (legacyUi.js) ----

val LegacyViolet = Color(0xFF7C3AED)
private val AVATAR_COLORS = listOf(0xFF7C3AED, 0xFFE11D48, 0xFF2563EB, 0xFFD97706, 0xFF0F766E, 0xFF0891B2, 0xFFC026D3)

fun legacyLocale(): Locale = Locale.forLanguageTag(I18n.lang)

fun initials(name: String?, fallback: String = "?"): String {
    val parts = (name ?: "").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return fallback
    return (parts[0].take(1) + (parts.getOrNull(1)?.take(1) ?: "")).uppercase()
}

fun avatarColor(seed: String?): Color = Color(AVATAR_COLORS[(seed ?: "").sumOf { it.code } % AVATAR_COLORS.size])

fun personName(meta: LegacyMeta, email: String): String = meta.str("name")?.takeIf { it.isNotBlank() } ?: email

fun itemName(item: LegacyItem): String = item.meta.str("name") ?: item.meta.str("subject") ?: ""

/** Base 1024, como o format_bytes() do servidor. */
fun formatSize(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024
        i++
    }
    return (if (i > 0 && value < 10) String.format(Locale.ROOT, "%.1f", value) else Math.round(value).toString()) + " " + units[i]
}

private fun parseInstant(iso: String): Instant? = runCatching { java.time.OffsetDateTime.parse(iso).toInstant() }.getOrNull()
    ?: runCatching { Instant.parse(iso) }.getOrNull()

private fun dateFormatter() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(legacyLocale())

fun shortDate(iso: String?): String {
    val at = iso?.let(::parseInstant) ?: return ""
    return at.atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter())
}

/** Dia escolhido num campo de data: vale o dia gravado, não o fuso de quem lê. */
fun pickedDate(iso: String?): String {
    val day = iso?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return ""
    return day.format(dateFormatter())
}

fun dateTime(iso: String): String {
    val at = parseInstant(iso) ?: return iso
    return at.atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(legacyLocale()))
}

/** Como LegacyItem::availableAt: o mais tarde entre a abertura + dias e o "não antes de". */
fun releaseLabel(item: LegacyItem, openedAt: String?): String {
    val days = item.dto.delay_days
    val date = pickedDate(item.dto.deliver_at)
    if (openedAt != null) {
        val at = parseInstant(openedAt)?.atZone(ZoneId.systemDefault())?.toLocalDate()?.plusDays(days.toLong()) ?: return date
        val deliver = item.dto.deliver_at?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return if (deliver != null && deliver.isAfter(at)) date else at.format(dateFormatter())
    }
    if (days > 0 && date.isNotEmpty()) return lt("release_after_not_before", "days" to days, "date" to date)
    if (date.isNotEmpty()) return date
    return if (days > 0) lt("release_after", "days" to days) else lt("release_opens")
}

// ---- componentes ----

@Composable
fun LegacyCard(
    modifier: Modifier = Modifier,
    border: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, border ?: MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun amberBorder(): Color = if (LocalDarkTheme.current) Color(0x66F59E0B) else Color(0xFFFCD34D)

@Composable
fun redBorder(): Color = if (LocalDarkTheme.current) Color(0x66EF4444) else Color(0xFFFCA5A5)

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun LegacyHeader(title: String, subtitle: String, actions: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        if (subtitle.isNotEmpty()) {
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        androidx.compose.foundation.layout.FlowRow(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = { actions() },
        )
    }
}

@Composable
fun CardTitle(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
fun CardText(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(top = 4.dp))
}

/** Botão verde "Ainda estou aqui" (lg-btn-ok). */
@Composable
fun StillHereButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669), contentColor = Color.White),
    ) {
        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(lt("still_here"), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun LegacyAvatar(name: String, seed: String, size: Int = 30) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size.dp).clip(CircleShape).background(avatarColor(seed)),
    ) {
        Text(initials(name), color = Color.White, fontSize = (size * 0.38).sp, fontWeight = FontWeight.SemiBold)
    }
}

enum class ChipTone { Ok, Warn, Danger, Neutral }

@Composable
fun LegacyChip(text: String, tone: ChipTone) {
    val dark = LocalDarkTheme.current
    val color = when (tone) {
        ChipTone.Ok -> if (dark) Color(0xFF34D399) else Color(0xFF047857)
        ChipTone.Warn -> if (dark) Color(0xFFFBBF24) else Color(0xFFB45309)
        ChipTone.Danger -> if (dark) Color(0xFFF87171) else Color(0xFFB91C1C)
        ChipTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun LegacyIconBox(icon: ImageVector, size: Int = 32) {
    val dark = LocalDarkTheme.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (dark) LegacyViolet.copy(alpha = 0.15f) else Color(0xFFEDE9FE)),
    ) {
        Icon(icon, contentDescription = null, tint = if (dark) Color(0xFFC4B5FD) else Color(0xFF6D28D9), modifier = Modifier.size((size * 0.45).dp))
    }
}

/** Sinal de vida no fundo da gaveta (LegacyStatusCard.vue). */
@Composable
fun LegacyStatusCard(vault: LegacyVaultDto) {
    val counting = vault.status == "armed" && vault.paused_until == null
    val dot = when {
        counting -> Color(0xFF10B981)
        vault.status in setOf("notified", "released", "opened") -> Color(0xFFEF4444)
        else -> Color(0xFF9CA3AF)
    }
    val title = when {
        vault.paused_until != null -> lt("status_paused")
        counting -> lt("status_active")
        else -> lt("status_${vault.status}")
    }
    val text = if (!counting) {
        lt(if (vault.paused_until != null) "card_paused" else "card_${vault.status}")
    } else {
        val t = vault.timeline
        val last = if (t.days_since == 0) lt("last_today") else lt("last_days", "days" to t.days_since)
        val next = t.next_reminder?.let { lt("next_reminder", "days" to it) } ?: lt("opens_after", "days" to t.opens_day)
        "$last $next"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(text, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

/** LegacyTimeline.vue. Simular é só visual: não fala com o servidor nem muda o temporizador. */
@Composable
fun LegacyTimeline(timeline: LegacyTimelineDto) {
    var simulated by remember(timeline) { mutableStateOf<Int?>(null) }
    val day = simulated ?: timeline.days_since
    val r = timeline.reminders
    data class Node(val key: String, val day: Int, val icon: ImageVector)
    val nodes = listOf(
        Node("today", 0, Icons.Outlined.Check),
        Node("reminder", r.getOrElse(0) { 0 }, Icons.Outlined.NotificationsNone),
        Node("second", r.getOrElse(1) { 0 }, Icons.Outlined.NotificationsNone),
        Node("final", r.getOrElse(2) { 0 }, Icons.Outlined.Schedule),
        Node("guardians", timeline.guardians_day, Icons.Outlined.Shield),
        Node("opens", timeline.opens_day, Icons.Outlined.LockOpen),
    )
    val spots = nodes.size - 1
    // Os nós estão igualmente espaçados mas os dias não: interpola por troço.
    val progress: Float = run {
        val d = minOf(day, nodes[spots].day)
        var i = 0
        while (i < spots - 1 && d >= nodes[i + 1].day) i++
        val span = (nodes[i + 1].day - nodes[i].day).coerceAtLeast(1)
        ((i + minOf((d - nodes[i].day).toFloat() / span, 1f)) / spots).coerceIn(0f, 1f)
    }
    val stage = when {
        day >= timeline.opens_day -> "opens"
        day >= timeline.guardians_day -> "guardians"
        r.isNotEmpty() && day >= r[0] -> "reminders"
        else -> "quiet"
    }
    val sims = listOf(
        null to lt("sim_today"),
        (r.getOrElse(0) { 0 } + 1) to lt("day_n", "day" to r.getOrElse(0) { 0 } + 1),
        (timeline.guardians_day + 1) to lt("day_n", "day" to timeline.guardians_day + 1),
        timeline.opens_day to lt("day_n", "day" to timeline.opens_day),
    )

    LegacyCard {
        CardTitle(lt("timeline_title"))
        Text(lt("timeline_text", "days" to timeline.opens_day), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        co.golink.tester.ui.screens.admin.AdminSegmented(
            options = sims.mapIndexed { i, (_, label) -> i to label },
            selected = sims.indexOfFirst { it.first == simulated }.coerceAtLeast(0),
            onSelect = { simulated = sims[it].first },
        )
        Spacer(Modifier.height(14.dp))

        BoxWithConstraints(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            val width = maxOf(maxWidth, 540.dp)
            val nodeW = 84.dp
            val track = width - nodeW
            val primary = MaterialTheme.colorScheme.primary
            Column(Modifier.width(width)) {
                // "Estás aqui": encosta às margens nas pontas (centrada ficava cortada).
                val labelX = (nodeW / 2 + track * progress - nodeW / 2).coerceIn(0.dp, width - nodeW)
                Text(
                    lt(if (simulated != null) "timeline_simulated" else "timeline_here"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(nodeW).offset(x = labelX),
                )
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .padding(start = nodeW / 2, top = 15.dp)
                            .width(track)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outline),
                    )
                    Box(
                        Modifier
                            .padding(start = nodeW / 2, top = 15.dp)
                            .width(track * progress)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(primary),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        nodes.forEachIndexed { i, node ->
                            val done = day >= node.day
                            Column(Modifier.width(nodeW), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (done) primary else MaterialTheme.colorScheme.surface)
                                        .border(2.dp, if (done) primary else MaterialTheme.colorScheme.outline, CircleShape),
                                ) {
                                    Icon(node.icon, contentDescription = null, tint = if (done) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                                }
                                Text(
                                    lt("node_${node.key}"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                Text(
                                    if (i == 0) lt("day_zero") else lt("day_n", "day" to node.day),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        Text(
            lt("stage_$stage", "days" to day),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
