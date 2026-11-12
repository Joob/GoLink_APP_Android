package co.golink.tester.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.domain.settings.SessionConnection
import co.golink.tester.domain.settings.SessionItem
import co.golink.tester.ui.common.formatDay
import co.golink.tester.ui.common.formatTime
import co.golink.tester.ui.common.parseIsoLocal
import co.golink.tester.ui.common.relativeAgo
import co.golink.tester.ui.components.dialogs.CreateSheetScaffold
import co.golink.tester.ui.i18n.tr
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/*
 * Definições › Sessões (redesign 2026-11, paridade com a web):
 * números, aviso de local novo, filtros, grupos (este dispositivo / ativas /
 * inativas), saída automática e detalhe numa folha inferior.
 */

private val DotNow = Color(0xFF10B981)
private val DotRecent = Color(0xFFF59E0B)
private val DotIdle = Color(0xFF9CA3AF)

private enum class SessionGroup { THIS, ACTIVE, INACTIVE }
private enum class SessionFilter { ALL, BROWSERS, APP, CONNECTIONS }

/** Linha pronta a mostrar (sessão ou ligação). */
private data class SessionView(
    val key: String,
    val sessionId: String?,
    val connectionId: Long?,
    val type: String, // browser | app | connection
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tone: Color,
    val tag: Pair<String, Color>,
    val tags: List<Pair<String, Color>>,
    val isCurrent: Boolean,
    val needsReview: Boolean,
    val lastActive: Instant?,
    val location: String?,
    val dot: Color,
    val group: SessionGroup,
    val timeLabel: String,
    val signedInLabel: String,
    val rows: List<Triple<String, String, Boolean>>, // rótulo, valor, monoespaçado
    val note: String,
)

@Composable
internal fun SessionsPane(
    sessions: List<SessionItem>,
    connections: List<SessionConnection>,
    autoSignoutDays: Int?,
    isLoading: Boolean,
    busyIds: Set<String>,
    onRefresh: () -> Unit,
    onRevoke: (String) -> Unit,
    onRevokeConnection: (Long) -> Unit,
    onConfirm: (String) -> Unit,
    onRevokeAll: () -> Unit,
    onRevokeInactive: () -> Unit,
    onAutoSignout: (Int?) -> Unit,
    onLogout: () -> Unit,
    onChangePassword: () -> Unit,
) {
    val views = remember(sessions, connections) { buildSessionViews(sessions, connections) }
    var filter by rememberSaveable { mutableStateOf(SessionFilter.ALL) }
    var showInactive by rememberSaveable { mutableStateOf(false) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var denying by remember { mutableStateOf(false) }
    var showRevokeAll by remember { mutableStateOf(false) }
    var showRevokeInactive by remember { mutableStateOf(false) }

    val filtered = when (filter) {
        SessionFilter.ALL -> views
        SessionFilter.BROWSERS -> views.filter { it.type == "browser" }
        SessionFilter.APP -> views.filter { it.type == "app" }
        SessionFilter.CONNECTIONS -> views.filter { it.type == "connection" }
    }
    val inactiveSessions = views.count { it.group == SessionGroup.INACTIVE && it.sessionId != null }
    val others = views.count { !it.isCurrent }
    val review = views.filter { it.needsReview }.maxByOrNull { it.lastActive ?: Instant.EPOCH }

    if (showRevokeAll) {
        SettingsDialog(
            title = "Terminar outras sessões?".tr(),
            message = "Todas as outras sessões serão revogadas. Continuarás autenticado neste dispositivo.".tr(),
            icon = Icons.Filled.Logout,
            destructive = true,
            confirmText = "Terminar todas".tr(),
            onConfirm = { onRevokeAll(); showRevokeAll = false },
            onDismiss = { showRevokeAll = false },
        )
    }
    if (showRevokeInactive) {
        SettingsDialog(
            title = "Terminar as sessões inativas?".tr(),
            message = "Terminam as %d sessões sem atividade há mais de 30 dias. Esta continua aberta.".tr().format(inactiveSessions),
            icon = Icons.Filled.Logout,
            destructive = true,
            confirmText = "Terminar todas".tr(),
            onConfirm = { onRevokeInactive(); showRevokeInactive = false },
            onDismiss = { showRevokeInactive = false },
        )
    }

    views.firstOrNull { it.key == selectedKey }?.let { v ->
        SessionDetailSheet(
            view = v,
            busy = (v.sessionId ?: "c:${v.connectionId}") in busyIds,
            onDismiss = { selectedKey = null },
            onConfirm = { v.sessionId?.let(onConfirm) },
            onSignOut = {
                selectedKey = null
                when {
                    v.connectionId != null -> onRevokeConnection(v.connectionId)
                    v.isCurrent -> onLogout()
                    v.sessionId != null -> onRevoke(v.sessionId)
                }
            },
        )
    }

    PaneColumn {
        // Números (2 × 2)
        val day = Instant.now().minus(Duration.ofDays(1))
        val stats = listOf(
            Triple(DotNow, views.size, "Com sessão iniciada".tr()),
            Triple(SettingsColors.Blue, views.count { it.isCurrent || (it.lastActive?.isAfter(day) == true) }, "Ativas nas últimas 24 h".tr()),
            Triple(DotIdle, views.count { it.group == SessionGroup.INACTIVE }, "Inativas há 30+ dias".tr()),
            Triple(DotRecent, views.count { it.needsReview }, "Para rever".tr()),
        )
        stats.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { (color, value, label) -> StatCard(color, value, label, Modifier.weight(1f)) }
            }
        }

        if (isLoading && views.isEmpty()) {
            PaneCard { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SettingsAccent) } }
        }

        // Local novo por rever
        review?.let { r ->
            ReviewBanner(
                view = r,
                denying = denying,
                busy = r.sessionId in busyIds,
                onYes = { r.sessionId?.let(onConfirm) },
                onNo = { denying = true },
                onCancel = { denying = false },
                onDeny = {
                    denying = false
                    r.sessionId?.let(onRevoke)
                    onChangePassword()
                },
            )
        }

        // Filtros
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            ) {
                listOf(
                    SessionFilter.ALL to ("Todas".tr() to views.size),
                    SessionFilter.BROWSERS to ("Browsers".tr() to views.count { it.type == "browser" }),
                    SessionFilter.APP to ("App Android".tr() to views.count { it.type == "app" }),
                    SessionFilter.CONNECTIONS to ("Ligações".tr() to views.count { it.type == "connection" }),
                ).forEach { (f, label) -> FilterPill(label.first, label.second, filter == f) { filter = f } }
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp).size(16.dp), strokeWidth = 2.dp, color = SettingsAccent)
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Atualizar".tr(), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }

        if (filtered.isEmpty() && !isLoading) {
            PaneCard {
                Text(
                    "Nada para mostrar nesta vista.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
            }
        }

        // Grupos
        listOf(
            SessionGroup.THIS to "Este dispositivo".tr(),
            SessionGroup.ACTIVE to "Ativas nos últimos 30 dias".tr(),
            SessionGroup.INACTIVE to "Inativas há 30+ dias".tr(),
        ).forEach { (group, label) ->
            val items = filtered.filter { it.group == group }
            if (items.isEmpty()) return@forEach

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(
                    "${label.uppercase()} · ${items.size}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                if (group == SessionGroup.INACTIVE) {
                    if (inactiveSessions > 0) {
                        SettingsActionText("Terminar todas".tr(), onClick = { showRevokeInactive = true }, color = SettingsColors.Red)
                    }
                    SettingsActionText(
                        if (showInactive) "Esconder".tr() else "Mostrar %d".tr().format(items.size),
                        onClick = { showInactive = !showInactive },
                    )
                }
            }
            if (group != SessionGroup.INACTIVE || showInactive) {
                PaneCard(padded = false) {
                    items.forEachIndexed { i, v ->
                        SessionViewRow(v) { selectedKey = v.key }
                        if (i < items.lastIndex) SettingsRowDivider(68.dp)
                    }
                }
            }
        }

        Text(
            "Os locais são aproximados, a partir do endereço IP".tr(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        // Saída automática
        PaneCard {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Terminar automaticamente as sessões inativas".tr(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("As sessões sem atividade durante este tempo terminam sozinhas.".tr(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsSegmented(
                options = listOf<Pair<Int?, String>>(
                    30 to "%d dias".tr().format(30),
                    90 to "%d dias".tr().format(90),
                    180 to "%d dias".tr().format(180),
                    null to "Nunca".tr(),
                ),
                selected = autoSignoutDays,
                onSelect = onAutoSignout,
            )
        }

        if (others > 0) {
            SettingsOutlinedButton(
                text = "Terminar todas as outras sessões".tr(),
                onClick = { showRevokeAll = true },
                destructive = true,
                icon = Icons.Filled.Logout,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun StatCard(dot: Color, value: Int, label: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = settingsCardColor(), border = settingsCardBorder(), modifier = modifier) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 8.dp).size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val dark = settingsIsDark()
    val bg = if (selected) (if (dark) Color(0xFFF3F4F6) else Color(0xFF111827)) else settingsCardColor()
    val fg = if (selected) (if (dark) Color(0xFF111827) else Color.White) else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(if (selected) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(50)))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = fg)
        Spacer(Modifier.width(5.dp))
        Text(count.toString(), style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.55f))
    }
}

@Composable
private fun ReviewBanner(
    view: SessionView,
    denying: Boolean,
    busy: Boolean,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onCancel: () -> Unit,
    onDeny: () -> Unit,
) {
    val dark = settingsIsDark()
    val amberText = if (dark) Color(0xFFFCD34D) else Color(0xFF92400E)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (dark) DotRecent.copy(alpha = 0.10f) else Color(0xFFFFFBEB),
        border = BorderStroke(1.dp, if (dark) DotRecent.copy(alpha = 0.3f) else Color(0xFFFDE68A)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                SettingsIconTile(Icons.Outlined.WarningAmber, SettingsColors.Amber, size = 34)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Novo início de sessão em %s".tr().format(view.location ?: "Local desconhecido".tr()),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = amberText,
                    )
                    Text(
                        if (denying) "Vamos terminar a sessão em %s e levar-te a mudar a password.".tr().format(view.title)
                        else "%s · %s · um local que ainda não tinhas usado. Foste tu?".tr().format(view.title, view.signedInLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = amberText.copy(alpha = 0.9f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = if (denying) onCancel else onYes,
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = settingsCardColor(), contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f),
                ) { Text(if (denying) "Cancelar".tr() else "Sim, fui eu".tr(), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Button(
                    onClick = if (denying) onDeny else onNo,
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SettingsColors.Red, contentColor = Color.White),
                    modifier = Modifier.weight(1f),
                ) { Text(if (denying) "Terminar e mudar a password".tr() else "Não · terminar e proteger".tr(), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun SessionViewRow(v: SessionView, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box {
            SettingsIconTile(v.icon, v.tone, size = 40)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(settingsCardColor())
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(v.dot),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    v.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                SettingsBadge(v.tag.first, v.tag.second)
            }
            Text(v.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(v.timeLabel, v.location ?: if (v.connectionId == null) "Local desconhecido".tr() else null).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (v.dot == DotNow) SettingsAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        SettingsChevron()
    }
}

@Composable
private fun SessionDetailSheet(
    view: SessionView,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onSignOut: () -> Unit,
) {
    var confirming by remember(view.key) { mutableStateOf(false) }
    val signOutLabel = when {
        view.connectionId != null -> "Revogar acesso".tr()
        view.isCurrent -> "Terminar sessão".tr()
        else -> "Terminar esta sessão".tr()
    }

    CreateSheetScaffold(
        title = view.title,
        onDismiss = onDismiss,
        confirmText = null,
        onConfirm = {},
        icon = { Icon(view.icon, contentDescription = null, tint = view.tone, modifier = Modifier.size(24.dp)) },
        iconBackground = view.tone.copy(alpha = if (settingsIsDark()) 0.22f else 0.13f),
        subtitle = listOfNotNull(view.timeLabel, view.location).joinToString(" · "),
        subtitleLock = false,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            view.tags.forEach { (label, color) -> SettingsBadge(label, color) }
        }

        if (view.sessionId != null) {
            SessionMap(view.location ?: "Local desconhecido".tr())
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            view.rows.forEach { (label, value, mono) ->
                Row {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        fontFamily = if (mono) FontFamily.Monospace else null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Text(
            view.note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(settingsInsetColor()).padding(12.dp),
        )

        Spacer(Modifier.height(6.dp))
        if (confirming) {
            Text(
                if (view.connectionId != null) "Revogar “%s”?".tr().format(view.title) else "Terminar a sessão em %s?".tr().format(view.title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    view.connectionId != null -> view.note
                    view.isCurrent -> "Vais sair da GoLink neste telemóvel.".tr()
                    else -> "Este dispositivo terá de voltar a iniciar sessão.".tr()
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            if (confirming) {
                SheetButton("Cancelar".tr(), destructive = false, enabled = !busy, modifier = Modifier.weight(1f)) { confirming = false }
                SheetButton(if (view.connectionId != null) "Revogar".tr() else "Terminar".tr(), destructive = true, filled = true, enabled = !busy, modifier = Modifier.weight(1f), onClick = onSignOut)
            } else {
                if (view.needsReview) {
                    SheetButton("Fui eu".tr(), destructive = false, enabled = !busy, modifier = Modifier.weight(1f), onClick = onConfirm)
                }
                SheetButton(signOutLabel, destructive = true, enabled = !busy, modifier = Modifier.weight(1f)) { confirming = true }
            }
        }
    }
}

@Composable
private fun SheetButton(
    text: String,
    destructive: Boolean,
    modifier: Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SettingsColors.Red, contentColor = Color.White),
            modifier = modifier.height(48.dp),
        ) { Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    } else {
        val color = if (destructive) SettingsColors.Red else MaterialTheme.colorScheme.onSurface
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (destructive) color.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
            modifier = modifier.height(48.dp),
        ) { Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** "Mapa" decorativo: grelha + ponto (sem tiles externos, como na web). */
@Composable
private fun SessionMap(label: String) {
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(settingsInsetColor()),
    ) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val step = 22.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f
            while (y < size.height) { drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f); y += step }
            val c = Offset(size.width * 0.58f, size.height * 0.32f)
            drawCircle(DotNow.copy(alpha = 0.22f), radius = 15.dp.toPx(), center = c)
            drawCircle(Color.White, radius = 8.dp.toPx(), center = c)
            drawCircle(DotRecent, radius = 6.dp.toPx(), center = c)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(settingsCardColor())
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// ── Modelo ─────────────────────────────────────────────────────────────

private fun buildSessionViews(sessions: List<SessionItem>, connections: List<SessionConnection>): List<SessionView> {
    val now = Instant.now()
    return (sessions.map { sessionView(it, now) } + connections.map { connectionView(it, now) })
        .sortedByDescending { it.lastActive ?: Instant.EPOCH }
}

private fun instantOf(iso: String?): Instant? = parseIsoLocal(iso)?.toInstant()

private fun whenLabel(iso: String?): String {
    val t = parseIsoLocal(iso) ?: return "—"
    val today = LocalDate.now()
    return when (t.toLocalDate()) {
        today -> "Hoje, %s".tr().format(formatTime(iso))
        today.minusDays(1) -> "Ontem, %s".tr().format(formatTime(iso))
        else -> formatDay(iso)
    }
}

private fun dotFor(isCurrent: Boolean, last: Instant?, now: Instant): Color {
    if (isCurrent) return DotNow
    if (last == null) return DotIdle
    val age = Duration.between(last, now)
    return when {
        age.toMinutes() < 15 -> DotNow
        age.toDays() < 30 -> DotRecent
        else -> DotIdle
    }
}

private fun timeLabelFor(isCurrent: Boolean, iso: String?): String {
    if (isCurrent) return "Ativa agora".tr()
    if (iso == null) return "Nunca usado".tr()
    return relativeAgo(iso).replaceFirstChar { it.uppercase() }
}

private fun methodLabel(method: String?): String {
    if (method.isNullOrBlank()) return "Não registado".tr()
    val parts = method.split("+")
    val first = parts[0]
    val base = when {
        first == "passkey" -> "Passkey".tr()
        first.startsWith("social") -> first.substringAfter(":", "").takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() } ?: "Login social".tr()
        else -> "Password".tr()
    }
    return when (parts.getOrNull(1)) {
        "email" -> "$base + " + "código por email".tr()
        "app" -> "$base + " + "2FA (app)".tr()
        else -> base
    }
}

private fun maskIp(ip: String?): String {
    if (ip.isNullOrBlank()) return "—"
    if (':' in ip) {
        val g = ip.split(':').filter { it.isNotEmpty() }
        return if (g.size > 2) "${g[0]}:${g[1]}:•••" else ip
    }
    val p = ip.split('.')
    return if (p.size == 4) "${p[0]}.${p[1]}.•••.•••" else ip
}

private fun abilitiesLabel(abilities: List<String>): String = when {
    "*" in abilities -> "acesso total".tr()
    abilities.any { Regex("write|create|update|delete", RegexOption.IGNORE_CASE).containsMatchIn(it) } -> "leitura e escrita".tr()
    else -> "só leitura".tr()
}

private fun sessionView(s: SessionItem, now: Instant): SessionView {
    val isApp = s.type == "app" || (s.type == null && ((s.platform ?: "web") != "web" || s.browser.orEmpty().contains("GoLink App")))
    val os = s.device?.takeIf { it.isNotBlank() && it != "Unknown Device" }
    val browserName = s.browser_name?.takeIf { it.isNotBlank() }
    val unknown = !isApp && browserName == null
    val lastIso = s.last_activity_at ?: s.login_at
    val last = instantOf(lastIso)

    val title = when {
        isApp -> "GoLink para Android".tr()
        unknown -> "Browser não reconhecido".tr()
        os != null -> "%s em %s".tr().format(browserName, os)
        else -> browserName!!
    }
    val appVersion = s.app_version?.let { "app %s".tr().format(it) }
    val deviceParts = when {
        isApp -> listOfNotNull(os, appVersion)
        unknown -> listOf("Não conseguimos ler o nome do browser".tr())
        else -> listOfNotNull(listOfNotNull(browserName, s.browser_version).joinToString(" "), os)
    }
    val signedIn = s.login_at?.let { "sessão iniciada %s".tr().format(whenLabel(it).replaceFirstChar { c -> c.lowercase() }) }

    val dot = dotFor(s.is_current, last, now)
    val group = when {
        s.is_current -> SessionGroup.THIS
        dot == DotIdle -> SessionGroup.INACTIVE
        else -> SessionGroup.ACTIVE
    }
    val typeTag = (if (isApp) "App Android".tr() else "Browser".tr()) to SettingsColors.Slate
    val tag = when {
        s.is_current -> "Este dispositivo".tr() to SettingsAccent
        s.needs_review -> "Local novo".tr() to SettingsColors.Amber
        else -> typeTag
    }
    val tags = buildList {
        add(if (isApp) "App Android".tr() to SettingsColors.Green else "Browser".tr() to SettingsColors.Blue)
        if (s.is_current) add("Este dispositivo".tr() to SettingsAccent)
        if (s.is_new_location) add("Local novo".tr() to SettingsColors.Amber)
    }
    val timeLabel = timeLabelFor(s.is_current, lastIso)
    val location = s.location?.takeIf { it.isNotBlank() }

    return SessionView(
        key = "s:${s.id}",
        sessionId = s.id,
        connectionId = null,
        type = if (isApp) "app" else "browser",
        title = title,
        subtitle = (deviceParts + listOfNotNull(signedIn)).joinToString(" · "),
        icon = if (isApp || (os != null && Regex("iphone|ipad|android|mobile|phone", RegexOption.IGNORE_CASE).containsMatchIn(os))) Icons.Outlined.Smartphone else Icons.Outlined.Laptop,
        tone = if (isApp) SettingsColors.Green else SettingsColors.Indigo,
        tag = tag,
        tags = tags,
        isCurrent = s.is_current,
        needsReview = s.needs_review,
        lastActive = last,
        location = location,
        dot = dot,
        group = group,
        timeLabel = timeLabel,
        signedInLabel = whenLabel(s.login_at),
        rows = listOf(
            Triple("Última atividade".tr(), timeLabel, false),
            Triple("Sessão iniciada".tr(), whenLabel(s.login_at), false),
            Triple("Como".tr(), methodLabel(s.auth_method), false),
            Triple("Dispositivo".tr(), deviceParts.joinToString(" · ").ifBlank { "—" }, false),
            Triple("Endereço IP".tr(), maskIp(s.ip_address), true),
            Triple("Local aproximado".tr(), location?.let { "%s (pelo IP)".tr().format(it) } ?: "Local desconhecido".tr(), false),
        ),
        note = "Se não foste tu, termina a sessão e muda a password. Terminar a sessão corta novos acessos; o que já foi descarregado nesse dispositivo fica lá.".tr(),
    )
}

private fun connectionView(c: SessionConnection, now: Instant): SessionView {
    val last = instantOf(c.last_used_at)
    val created = instantOf(c.created_at)
    val ref = last ?: created
    val recent = ref != null && Duration.between(ref, now).toDays() < 30
    val dot = when {
        last != null -> dotFor(false, last, now)
        recent -> DotRecent
        else -> DotIdle
    }
    val createdText = c.created_at?.let { "criado %s".tr().format(formatDay(it)) }
    val note = "Os programas que usam este token deixam de funcionar assim que o revogares.".tr()
    return SessionView(
        key = "c:${c.id}",
        sessionId = null,
        connectionId = c.id,
        type = "connection",
        title = "API token · “%s”".tr().format(c.name),
        subtitle = listOfNotNull(abilitiesLabel(c.abilities), createdText).joinToString(" · "),
        icon = Icons.Outlined.Terminal,
        tone = SettingsColors.Purple,
        tag = "Ligação".tr() to SettingsColors.Slate,
        tags = listOf("Ligação".tr() to SettingsColors.Purple),
        isCurrent = false,
        needsReview = false,
        lastActive = last,
        location = null,
        dot = dot,
        group = if (recent) SessionGroup.ACTIVE else SessionGroup.INACTIVE,
        timeLabel = timeLabelFor(false, c.last_used_at),
        signedInLabel = whenLabel(c.created_at),
        rows = listOf(
            Triple("Nome".tr(), c.name, false),
            Triple("Permissões".tr(), c.abilities.joinToString(", ").ifBlank { "—" }, false),
            Triple("Último uso".tr(), c.last_used_at?.let { whenLabel(it) } ?: "Nunca usado".tr(), false),
            Triple("Criado".tr(), whenLabel(c.created_at), false),
        ),
        note = note,
    )
}
