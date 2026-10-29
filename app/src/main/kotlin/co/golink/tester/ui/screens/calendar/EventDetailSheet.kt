package co.golink.tester.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.domain.calendar.CalInfo
import co.golink.tester.domain.calendar.CalInstance
import co.golink.tester.domain.calendar.EventAttachment
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.i18n.tr
import java.time.ZoneId

private val AVATAR_COLORS = listOf(0xFF0F8A5F, 0xFFE5486F, 0xFFD97706, 0xFF2563EB, 0xFF8B5CF6, 0xFF0D9488)

private fun initials(nameOrEmail: String): String {
    val base = nameOrEmail.substringBefore('@').replace('.', ' ').replace('_', ' ').trim()
    val parts = base.split(' ').filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0][0]}${parts[1][0]}"
        base.length >= 2 -> base.take(2)
        else -> base.ifEmpty { "?" }
    }.uppercase()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailSheet(
    instance: CalInstance,
    calendars: List<CalInfo>,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
    onRsvp: (String) -> Unit,
    onOpenAttachment: ((EventAttachment) -> Unit)?,
) {
    val ev = instance.event
    val color = eventColor(ev, calendars)
    val calendar = calendars.firstOrNull { it.id == ev.calendarId }
    var confirmDelete by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outlineVariant),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 7.dp).size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(eventTitle(ev), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    val day = instance.start.atZone(ZoneId.systemDefault()).toLocalDate()
                    Text(
                        "${fmtDayMonth(day)} · ${fmtSpan(ev, instance.start, instance.end)}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onEdit != null && ev.canEdit) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).clickable(onClick = onEdit),
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            if (ev.locked) {
                InfoRow(Icons.Outlined.Lock) { Text("Evento encriptado (sem chave)".tr(), fontSize = 13.sp) }
            }
            InfoRow(null, dot = color) {
                Text(
                    when {
                        ev.holiday -> "Feriado".tr()
                        calendar != null -> calendar.name
                        else -> "Convite".tr()
                    },
                    fontSize = 13.sp,
                )
            }
            if (ev.data.location.isNotBlank()) {
                val link = ev.data.location.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
                InfoRow(Icons.Outlined.Place) {
                    Text(
                        ev.data.location, fontSize = 13.sp,
                        color = if (link != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = if (link != null) Modifier.clickable { runCatching { uri.openUri(link) } } else Modifier,
                    )
                }
            }
            if (!ev.holiday) {
                InfoRow(Icons.Outlined.Repeat) { Text(repeatLabel(ev.repeat, ev.repeatRule, ev.repeatUntil != null), fontSize = 13.sp) }
                val alerts = ev.raw.alerts.orEmpty()
                InfoRow(Icons.Outlined.NotificationsNone) {
                    Text(if (alerts.isEmpty()) "Sem alerta".tr() else alerts.joinToString(", ") { alertLabel(it, long = true) }, fontSize = 13.sp)
                }
            }
            if (ev.raw.guests.isNotEmpty()) {
                InfoRow(Icons.Outlined.PeopleOutline) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                ev.raw.guests.take(4).forEachIndexed { i, g ->
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.offset(x = (i * 20).dp).size(28.dp).clip(CircleShape)
                                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                            .background(Color(AVATAR_COLORS[i % AVATAR_COLORS.size])),
                                    ) {
                                        Text(initials(g.name ?: g.email), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                            Spacer(Modifier.width((minOf(4, ev.raw.guests.size) * 20 + 14).dp))
                            Text("%d convidados".tr().format(ev.raw.guests.size), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ev.raw.guests.forEach { g ->
                            Text(
                                "${g.name ?: g.email} · ${guestStatus(g.status)}",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
            if (ev.data.notes.isNotBlank()) {
                InfoRow(Icons.Outlined.Notes) { Text(ev.data.notes, fontSize = 13.sp) }
            }
            ev.data.attachments.forEach { a ->
                AttachmentCard(a, onOpen = onOpenAttachment?.let { open -> { open(a) } })
            }

            // Convidado registado: Vais? Sim / Talvez / Não.
            if (ev.isGuest && ev.raw.me != null) {
                Spacer(Modifier.height(10.dp))
                Text("Vais?".tr(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    listOf("accepted" to "Sim", "tentative" to "Talvez", "declined" to "Não").forEach { (status, label) ->
                        val active = ev.raw.me.status == status
                        OutlinedButton(
                            onClick = { onRsvp(status) },
                            shape = RoundedCornerShape(12.dp),
                            colors = if (active) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) else ButtonDefaults.outlinedButtonColors(),
                            modifier = Modifier.weight(1f),
                        ) { Text(label.tr()) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ev.canManage) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.height(46.dp),
                    ) { Text("Apagar".tr(), fontWeight = FontWeight.SemiBold) }
                }
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.weight(1f).height(46.dp),
                ) { Text("Fechar".tr(), fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Apagar este evento?".tr(),
            message = "O evento é apagado para todos e os convidados recebem um aviso de cancelamento.".tr(),
            confirmText = "Apagar".tr(),
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}

fun guestStatus(status: String): String = when (status) {
    "accepted" -> "Aceitou"
    "tentative" -> "Talvez"
    "declined" -> "Recusou"
    else -> "Pendente"
}.tr()

@Composable
private fun InfoRow(icon: ImageVector?, dot: Color? = null, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Box(Modifier.width(30.dp).padding(top = 1.dp), contentAlignment = Alignment.CenterStart) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (dot != null) Box(Modifier.padding(start = 4.dp, top = 4.dp).size(9.dp).clip(CircleShape).background(dot))
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun AttachmentCard(a: EventAttachment, onOpen: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        FileBadge(a.name, a.mime, 32.dp)
        Spacer(Modifier.width(10.dp))
        Text(a.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (onOpen != null) {
            Text(
                "Abrir".tr(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpen).padding(start = 8.dp),
            )
        }
    }
}
