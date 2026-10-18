package co.golink.tester.ui.screens.security

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.ui.common.appLocale
import co.golink.tester.ui.common.parseIsoLocal
import co.golink.tester.ui.i18n.tr
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Evento de segurança para a UI (vem de dois endpoints/DTOs diferentes). */
data class SecurityItemUi(
    val id: String,
    val event: String,
    val createdAt: String?,
    val ip: String?,
    val userAgent: String?,
)

/** Eventos que indicam potencial compromisso da conta — destacados a vermelho. */
private val ALERTS = setOf(
    "account.new_device",
    "account.password_changed",
    "account.e2e_secret_rotated",
    "account.email_changed",
    "account.email_reverted",
    "account.deletion_scheduled",
    "auth.login_failed",
    "auth.login_failed_repeated",
    "auth.otp_locked",
    "auth.otp_failed_repeated",
)

private val ACCENT = Color(0xFF17784A)
private val BLUE = Color(0xFF2563EB)

fun securityEventLabel(event: String): String = when (event) {
    "auth.login" -> "Início de sessão".tr()
    "auth.login_failed" -> "Tentativa de início de sessão falhada".tr()
    "auth.login_failed_repeated" -> "Várias tentativas de início de sessão falhadas".tr()
    "auth.otp_failed" -> "Código de segurança incorreto".tr()
    "auth.otp_failed_repeated" -> "Vários códigos de segurança incorretos".tr()
    "auth.otp_locked" -> "Conta temporariamente bloqueada".tr()
    "account.password_changed" -> "Password alterada".tr()
    "account.e2e_secret_rotated" -> "Segredo de encriptação alterado".tr()
    "account.new_device" -> "Novo dispositivo ou localização".tr()
    "account.email_changed" -> "Email alterado".tr()
    "account.email_reverted" -> "Email reposto".tr()
    "account.deletion_scheduled" -> "Eliminação da conta agendada".tr()
    "account.deletion_cancelled" -> "Eliminação da conta cancelada".tr()
    "share.created" -> "Link de partilha criado".tr()
    "share.updated" -> "Link de partilha alterado".tr()
    "team.invitation_accepted" -> "Convite de equipa aceite".tr()
    else -> "Evento de segurança".tr()
}

private fun eventIcon(event: String): ImageVector = when {
    event == "auth.login" -> Icons.AutoMirrored.Outlined.Login
    event.startsWith("auth.login_failed") -> Icons.Outlined.ErrorOutline
    event.startsWith("auth.otp") -> Icons.Outlined.Pin
    event == "account.password_changed" -> Icons.Outlined.Lock
    event == "account.e2e_secret_rotated" -> Icons.Outlined.VpnKey
    event == "account.new_device" -> Icons.Outlined.Devices
    event.startsWith("account.email") -> Icons.Outlined.Email
    event.startsWith("account.deletion") -> Icons.Outlined.Delete
    event.startsWith("share.") -> Icons.Outlined.Link
    event.startsWith("team.") -> Icons.Outlined.Group
    else -> Icons.Outlined.Shield
}

/** User-Agent → "Chrome em Windows" / "App GoLink em Android". */
private fun shortAgent(ua: String?): String? {
    if (ua.isNullOrBlank()) return null
    val os = when {
        ua.contains("Android", true) -> "Android"
        ua.contains("iPhone", true) || ua.contains("iPad", true) -> "iOS"
        ua.contains("Windows", true) -> "Windows"
        ua.contains("Mac OS", true) || ua.contains("Macintosh", true) -> "macOS"
        ua.contains("Linux", true) -> "Linux"
        else -> null
    }
    val client = when {
        ua.contains("GoLink", true) || ua.contains("okhttp", true) -> "App GoLink".tr()
        ua.contains("Edg/", true) -> "Edge"
        ua.contains("OPR/", true) -> "Opera"
        ua.contains("Firefox", true) -> "Firefox"
        ua.contains("Chrome", true) -> "Chrome"
        ua.contains("Safari", true) -> "Safari"
        else -> null
    }
    return when {
        client != null && os != null -> "%s em %s".tr().format(client, os)
        client != null -> client
        os != null -> os
        else -> ua.take(40)
    }
}

/**
 * Atividade de segurança (Definições e ecrã próprio): filtros, um cartão por
 * dia com a hora à esquerda, ícone por tipo (alertas a vermelho) e
 * "IP · Chrome em Windows".
 */
@Composable
fun SecurityActivityContent(
    items: List<SecurityItemUi>,
    isLoading: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf("all") }
    val visible = remember(items, filter) {
        when (filter) {
            "logins" -> items.filter { it.event.startsWith("auth.") }
            "alerts" -> items.filter { it.event in ALERTS }
            else -> items
        }
    }
    val days = remember(visible) { visible.groupBy { parseIsoLocal(it.createdAt)?.toLocalDate() } }
    val weekAgo = LocalDate.now().minusDays(7)
    val recentAlerts = items.count { it.event in ALERTS && (parseIsoLocal(it.createdAt)?.toLocalDate()?.isAfter(weekAgo) == true) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "intro") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Inícios de sessão e alterações importantes na conta. Se vires algo que não reconheces, muda a password e revê as sessões ativas.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                // Resumo: alertas dos últimos 7 dias (ou "tudo calmo").
                if (items.isNotEmpty()) {
                    val alert = recentAlerts > 0
                    val tint = if (alert) MaterialTheme.colorScheme.error else ACCENT
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = tint.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, tint.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                            Box(
                                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(if (alert) Icons.Outlined.ErrorOutline else Icons.Outlined.Shield, null, tint = tint, modifier = Modifier.size(18.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    if (alert) "%d alertas nos últimos 7 dias".tr().format(recentAlerts) else "Sem alertas nos últimos 7 dias".tr(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    if (alert) "Confirma que foste tu; se não, muda a password.".tr() else "Nada de anormal na tua conta.".tr(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "all" to "Tudo".tr(),
                        "logins" to "Inícios de sessão".tr(),
                        "alerts" to "Alertas".tr(),
                    ).forEach { (key, label) ->
                        val count = when (key) {
                            "logins" -> items.count { it.event.startsWith("auth.") }
                            "alerts" -> items.count { it.event in ALERTS }
                            else -> items.size
                        }
                        val active = key == filter
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
                            border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { filter = key },
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.width(5.dp))
                                Text("$count${if (hasMore && count > 0) "+" else ""}", fontSize = 12.5.sp, color = if (active) MaterialTheme.colorScheme.surface.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        if (isLoading && items.isEmpty()) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp) }
            }
        } else if (visible.isEmpty()) {
            item(key = "empty") {
                Text(
                    if (filter == "alerts") "Sem alertas registados.".tr() else "Ainda não há atividade de segurança registada.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 4.dp),
                )
            }
        }

        days.forEach { (day, dayItems) ->
            item(key = "day-$day") {
                Column {
                    DayTitle(day)
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            dayItems.forEachIndexed { i, ev ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                                EventRow(ev)
                            }
                        }
                    }
                }
            }
        }

        if (hasMore) {
            item(key = "more") {
                TextButton(onClick = onLoadMore, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isLoading) "A carregar…".tr() else "Carregar mais".tr(), color = ACCENT, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DayTitle(day: LocalDate?) {
    val today = LocalDate.now()
    val primary = when (day) {
        null -> "Sem data".tr()
        today -> "Hoje".tr()
        today.minusDays(1) -> "Ontem".tr()
        else -> day.format(DateTimeFormatter.ofPattern("d MMM yyyy", appLocale()))
    }
    val secondary = day?.format(DateTimeFormatter.ofPattern(if (day == today || day == today.minusDays(1)) "EEEE, d MMM yyyy" else "EEEE", appLocale()))
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) { append(primary) }
            if (secondary != null) append(" · $secondary")
        },
        fontSize = 12.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, top = 6.dp),
    )
}

@Composable
private fun EventRow(ev: SecurityItemUi) {
    val alert = ev.event in ALERTS
    val tint = when {
        alert -> MaterialTheme.colorScheme.error
        ev.event == "auth.login" -> ACCENT
        ev.event.startsWith("share.") || ev.event.startsWith("team.") -> BLUE
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val time = parseIsoLocal(ev.createdAt)?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: ""
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp)) {
        Text(time, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(38.dp))
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(eventIcon(ev.event), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                securityEventLabel(ev.event),
                fontSize = 13.5.sp,
                fontWeight = if (alert) FontWeight.SemiBold else FontWeight.Medium,
                color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(shortAgent(ev.userAgent), ev.ip?.takeIf { it.isNotBlank() }).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
