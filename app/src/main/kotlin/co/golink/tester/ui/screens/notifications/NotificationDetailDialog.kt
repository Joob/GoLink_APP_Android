package co.golink.tester.ui.screens.notifications

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.domain.notifications.Notification
import co.golink.tester.domain.notifications.NotificationDetail
import co.golink.tester.ui.common.relativeAgo
import co.golink.tester.ui.i18n.I18n
import co.golink.tester.ui.i18n.tr
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
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
    val zoned = date.atZone(ZoneId.systemDefault())
    val absolute = co.golink.tester.ui.common.UserDisplayPrefs.format(zoned.toLocalDate())?.let { "$it, " + zoned.format(co.golink.tester.ui.common.DateFormatters.HM) }
        ?: zoned.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.forLanguageTag(I18n.lang)))
    // Mais de uma semana: o relativeAgo devolve só o dia, que já está no absoluto.
    val week = Duration.between(date, java.time.Instant.now()).toDays() >= 7
    return if (week) absolute else "$absolute · " + relativeAgo(iso)
}

/** Cabeçalho: "09:00" se for de hoje, senão o dia curto ("12 nov."). */
private fun headerTime(n: Notification): String? {
    val zoned = n.createdAtInstant?.atZone(ZoneId.systemDefault()) ?: return null
    return if (zoned.toLocalDate() == LocalDate.now()) zoned.format(co.golink.tester.ui.common.DateFormatters.HM)
    else zoned.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag(I18n.lang)))
}

/** Data do evento no URL do calendário (/calendar?event=…&date=YYYY-MM-DD). */
private fun eventDate(n: Notification): LocalDate? {
    val url = n.actionUrl ?: return null
    val m = Regex("[?&]date=(\\d{4}-\\d{2}-\\d{2})").find(url) ?: return null
    return runCatching { LocalDate.parse(m.groupValues[1]) }.getOrNull()
}

/**
 * Iniciais de quem convidou (o texto começa sempre pelo nome: ":name invite you…").
 * A 1.ª palavra conta sempre (nomes em minúsculas, ex. "joob"); a 2.ª só se
 * começar por maiúscula, para não apanhar o verbo ("joob invite" → "J").
 */
private fun initialsOf(text: String): String? {
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val first = words.firstOrNull()?.firstOrNull { it.isLetterOrDigit() } ?: return null
    val second = words.getOrNull(1)?.firstOrNull()?.takeIf { it.isUpperCase() }
    return listOfNotNull(first, second).joinToString("") { it.uppercase() }
}

private enum class CardKind { DEVICE, EVENT, PERSON, PLAIN }

/**
 * Folha de detalhe (desenho do mockup das 4 notificações): cabeçalho
 * "Categoria · hora", título, data, cartão por tipo, ação principal verde
 * e mosaicos (Recusar / lida / Eliminar com confirmação) em baixo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDetailDialog(
    notification: Notification,
    groupLabel: String,
    onDismiss: () -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit,
    onAction: (() -> Unit)?,
    answering: Boolean = false,
    /** Convite por responder: true = Aceitar, false = Recusar. */
    onAnswer: ((Boolean) -> Unit)? = null,
) {
    val n = notification
    // Estado no momento em que abriu: a etiqueta não desaparece a meio.
    val wasUnread = remember(n.id) { !n.isRead }
    var confirmDelete by remember(n.id) { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val muted = scheme.onSurfaceVariant
    val primary = scheme.primary
    val style = notificationIcon(n.category)
    val body = n.message?.takeIf { it.isNotBlank() } ?: n.description
    val device = n.details.firstOrNull { it.key == "device" }
    val date = remember(n.id, n.actionUrl) { if (n.category == "calendar") eventDate(n) else null }
    val sharing = groupOf(n.category) == "sharing" || n.pendingAnswer
    val kind = when {
        device != null -> CardKind.DEVICE
        date != null -> CardKind.EVENT
        sharing && body.isNotBlank() -> CardKind.PERSON
        else -> CardKind.PLAIN
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = scheme.surface,
        dragHandle = {
            Box(
                Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(scheme.outlineVariant),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
                .navigationBarsPadding(),
        ) {
            // Cabeçalho: ícone · "Grupo · hora" · Por ler · X
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(style.bg),
                ) { Icon(style.icon, contentDescription = null, tint = style.fg, modifier = Modifier.size(17.dp)) }
                Spacer(Modifier.width(10.dp))
                val time = remember(n.id, n.createdAtIso) { headerTime(n) }
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = style.fg, fontWeight = FontWeight.Bold)) { append(groupLabel) }
                        if (time != null) withStyle(SpanStyle(color = muted)) { append(" · $time") }
                    },
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (wasUnread) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Por ler".tr().uppercase(),
                        color = primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier
                            .background(primary.copy(alpha = 0.12f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(scheme.surfaceVariant).clickable(onClick = onDismiss),
                ) { Icon(Icons.Filled.Close, contentDescription = "Fechar".tr(), tint = muted, modifier = Modifier.size(16.dp)) }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                n.title.ifBlank { "Notificação".tr() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 28.sp,
            )
            remember(n.id, n.createdAtIso) { formatWhen(n) }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = muted)
            }

            // Cartão por tipo
            val others = n.details.filter { it !== device }
            if (body.isNotBlank() || n.details.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                when (kind) {
                    CardKind.DEVICE -> {
                        if (body.isNotBlank()) {
                            Text(body, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                        }
                        DeviceCard(device!!.value, others)
                    }
                    CardKind.EVENT -> EventCard(date!!, body, style.fg, style.bg, others)
                    CardKind.PERSON -> PersonCard(n, body, style.fg, style.bg, style.icon, others)
                    CardKind.PLAIN -> CardBox {
                        if (n.sender == "support_team") {
                            Text(
                                "Equipa de apoio".tr().uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                                color = muted,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodyMedium)
                        DetailRows(n.details, divider = body.isNotBlank())
                    }
                }
            }

            // Ação principal
            val primaryClick: (() -> Unit)? = when {
                onAnswer != null -> ({ onAnswer(true) })
                else -> onAction
            }
            if (primaryClick != null && !confirmDelete) {
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = primaryClick,
                    enabled = !answering,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (onAnswer != null) {
                        if (answering) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
                        else Text("Aceitar".tr(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    } else {
                        Text(n.actionButton ?: "Abrir".tr(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
                // Convite de evento por responder: o Aceitar ocupa o botão verde,
                // mas o evento continua a poder ser aberto antes de responder.
                if (onAnswer != null && onAction != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onAction,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) {
                        Text(n.actionButton ?: "Abrir".tr(), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(if (primaryClick != null && !confirmDelete) 10.dp else 20.dp))
            if (confirmDelete) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DANGER.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onAnswer != null) {
                        ActionTile(
                            icon = Icons.Filled.Close,
                            label = "Recusar".tr(),
                            tint = DANGER,
                            enabled = !answering,
                            onClick = { onAnswer(false) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ActionTile(
                        icon = if (n.isRead) Icons.Filled.MarkEmailUnread else Icons.Filled.Done,
                        label = if (n.isRead) "Marcar como não lida".tr() else "Marcar como lida".tr(),
                        tint = scheme.onSurface,
                        onClick = onToggleRead,
                        modifier = Modifier.weight(1f),
                    )
                    ActionTile(
                        icon = Icons.Outlined.DeleteOutline,
                        label = "Eliminar".tr(),
                        tint = DANGER,
                        onClick = { confirmDelete = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CardBox(background: Color? = null, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = background ?: MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun DetailRows(rows: List<NotificationDetail>, divider: Boolean) {
    if (rows.isEmpty()) return
    if (divider) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        )
    }
    rows.forEach { row ->
        Row(modifier = Modifier.padding(vertical = 3.dp)) {
            Text(
                detailLabel(row.key),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(104.dp),
            )
            Text(row.value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
    }
}

/** Sessão/aparelho: mosaico com o tipo de aparelho, nome a negrito e IP. */
@Composable
private fun DeviceCard(value: String, others: List<NotificationDetail>) {
    val scheme = MaterialTheme.colorScheme
    val parts = value.split(" · ")
    val name = parts.first()
    val ip = parts.drop(1).joinToString(" · ").takeIf { it.isNotBlank() }
    val mobile = Regex("android|iphone|ipad|mobile", RegexOption.IGNORE_CASE).containsMatchIn(value)
    CardBox(background = scheme.surfaceVariant.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(scheme.primary.copy(alpha = 0.12f)),
            ) {
                Icon(
                    if (mobile) Icons.Outlined.PhoneAndroid else Icons.Outlined.Computer,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                if (ip != null) {
                    Text(ip, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
            }
        }
        DetailRows(others, divider = true)
    }
}

/** Evento: bloco de data (mês + dia) ao lado do texto. */
@Composable
private fun EventCard(date: LocalDate, body: String, fg: Color, bg: Color, others: List<NotificationDetail>) {
    val month = remember(date, I18n.lang) {
        date.format(DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag(I18n.lang))).trimEnd('.').uppercase()
    }
    CardBox(background = bg.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Text(
                    month,
                    color = MaterialTheme.colorScheme.surface,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().background(fg).padding(vertical = 2.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    date.dayOfMonth.toString(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        DetailRows(others, divider = true)
    }
}

/** Partilhas e convites: avatar com iniciais (ou o ícone) ao lado do texto. */
@Composable
private fun PersonCard(n: Notification, body: String, fg: Color, bg: Color, icon: ImageVector, others: List<NotificationDetail>) {
    // Só o convite de equipa começa pelo nome de quem convida (":name invite you…");
    // nos outros ("On ficheiro.jpg: …") as iniciais seriam lixo.
    val initials = remember(n.id) { if (n.category == "team-invitation") initialsOf(body) else null }
    CardBox {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(bg),
            ) {
                if (initials != null) Text(initials, color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                else Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(top = 7.dp))
        }
        DetailRows(others, divider = true)
    }
}

/** Mosaico de ação: ícone por cima do rótulo. */
@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = modifier.height(64.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                color = tint,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
