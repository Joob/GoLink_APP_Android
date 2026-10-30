package co.golink.tester.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import co.golink.tester.ui.common.relativeAgo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import co.golink.tester.domain.notifications.Notification
import co.golink.tester.ui.i18n.I18n
import co.golink.tester.ui.i18n.tr
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val DANGER = Color(0xFFE11D48)

/** Rótulos das linhas de detalhe (o servidor envia só a chave). */
private fun detailLabel(key: String): String = when (key) {
    "link" -> "Ligação".tr()
    "created_by" -> "Criado por".tr()
    "changed_by" -> "Alterado por".tr()
    "accepted_by" -> "Aceite por".tr()
    "device" -> "Dispositivo".tr()
    else -> key
}

/** "20 out. 2026, 02:51 · há 3 dias" na língua da app; sem ISO usa o texto do servidor. */
private fun formatWhen(n: Notification): String? {
    val iso = n.createdAtIso ?: return n.createdAt
    val date = n.createdAtInstant ?: return n.createdAt
    val absolute = date.atZone(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.forLanguageTag(I18n.lang)))
    // Mais de uma semana: o relativeAgo devolve só o dia, que já está no absoluto.
    val week = Duration.between(date, java.time.Instant.now()).toDays() >= 7
    return if (week) absolute else "$absolute · " + relativeAgo(iso)
}

@Composable
private fun Chip(text: String, fg: Color, bg: Color) {
    Text(
        text.uppercase(),
        color = fg,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

/**
 * Popup de detalhe (mesmo desenho da web): ícone, etiquetas, título, data,
 * caixa com remetente/mensagem ou linhas de detalhe, ✓ lida/não lida,
 * eliminar com confirmação e o botão principal da ação.
 */
@Composable
fun NotificationDetailDialog(
    notification: Notification,
    groupLabel: String,
    onDismiss: () -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit,
    onAction: (() -> Unit)?,
) {
    // Estado no momento em que abriu: a etiqueta não desaparece a meio.
    val wasUnread = remember(notification.id) { !notification.isRead }
    var confirmDelete by remember(notification.id) { mutableStateOf(false) }
    val soft = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val style = notificationIcon(notification.category)
    val body = notification.message?.takeIf { it.isNotBlank() } ?: notification.description

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
        ) {
            Box {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Fechar".tr(), tint = muted, modifier = Modifier.size(18.dp))
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = 640.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .background(style.bg, RoundedCornerShape(14.dp)),
                    ) {
                        Icon(style.icon, contentDescription = null, tint = style.fg, modifier = Modifier.size(22.dp))
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(groupLabel, muted, soft)
                        if (wasUnread) Chip("Por ler".tr(), primary, primary.copy(alpha = 0.12f))
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        notification.title.ifBlank { "Notificação".tr() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                    remember(notification.id, notification.createdAtIso) { formatWhen(notification) }?.let {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = muted, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(it, style = MaterialTheme.typography.labelSmall, color = muted)
                        }
                    }

                    if (body.isNotBlank() || notification.details.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(soft, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                        ) {
                            if (notification.sender == "support_team") {
                                Text(
                                    "Equipa de apoio".tr().uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp,
                                    color = muted,
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                            if (body.isNotBlank()) {
                                Text(body, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (notification.details.isNotEmpty()) {
                                if (body.isNotBlank()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    )
                                }
                                notification.details.forEach { row ->
                                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                        Text(
                                            detailLabel(row.key),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = muted,
                                            modifier = Modifier.width(104.dp),
                                        )
                                        Text(row.value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    if (confirmDelete) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DANGER.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                        ) {
                            Text("Eliminar esta notificação?".tr(), color = DANGER, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Esta ação não elimina o pedido nem a atividade a que se refere.".tr(),
                                color = DANGER.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onDelete,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DANGER, contentColor = Color.White),
                                    modifier = Modifier.weight(1f),
                                ) { Text("Eliminar".tr(), fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { confirmDelete = false },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f),
                                ) { Text("Cancelar".tr(), fontWeight = FontWeight.Bold) }
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SquareIconButton(
                                icon = if (notification.isRead) Icons.Filled.MarkEmailUnread else Icons.Filled.Done,
                                label = if (notification.isRead) "Marcar como não lida".tr() else "Marcar como lida".tr(),
                                bg = soft,
                                tint = muted,
                                onClick = onToggleRead,
                            )
                            SquareIconButton(
                                icon = Icons.Outlined.DeleteOutline,
                                label = "Eliminar".tr(),
                                bg = soft,
                                tint = muted,
                                onClick = { confirmDelete = true },
                            )
                            if (onAction != null) {
                                Button(
                                    onClick = onAction,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(44.dp),
                                ) {
                                    Text(notification.actionButton ?: "Abrir".tr(), fontWeight = FontWeight.Bold)
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(44.dp),
                                ) { Text("Fechar".tr(), fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SquareIconButton(icon: ImageVector, label: String, bg: Color, tint: Color, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .background(bg, RoundedCornerShape(12.dp)),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
    }
}
