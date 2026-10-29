package co.golink.tester.ui.screens.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.golink.tester.data.calendar.EventForm
import co.golink.tester.domain.calendar.CalEvent
import co.golink.tester.domain.calendar.CalInfo
import co.golink.tester.domain.calendar.CalendarConst
import co.golink.tester.domain.calendar.EventAttachment
import co.golink.tester.domain.calendar.RepeatRule
import co.golink.tester.ui.common.appLocale
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.i18n.tr
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/** Campos do formulário (dia + hora em separado, como calendarForm.js). */
private data class FormFields(
    val calendarId: String?,
    val title: String,
    val startDay: LocalDate,
    val startTime: LocalTime,
    /** Dia inteiro: último dia (inclusivo). */
    val endDay: LocalDate,
    val endTime: LocalTime,
    val allDay: Boolean,
    val timezone: String,
    val repeat: String,
    val repeatUntil: LocalDate?,
    /** Regra da web (a cada N, dias, vezes); mudar o tipo repõe a de omissão. */
    val rule: RepeatRule = RepeatRule(),
    val alerts: List<Int>,
    val location: String,
    val notes: String,
    val guests: List<String>,
    val guestsCanEdit: Boolean,
    val attachments: List<EventAttachment>,
) {
    /** Início/fim finais; o fim nunca antes do início (formDates da web). */
    fun dates(zone: ZoneId = ZoneId.systemDefault()): Pair<Instant, Instant> {
        if (allDay) {
            val last = if (endDay.isBefore(startDay)) startDay else endDay
            return startDay.atStartOfDay(zone).toInstant() to last.plusDays(1).atStartOfDay(zone).toInstant()
        }
        val s = LocalDateTime.of(startDay, startTime).atZone(zone).toInstant()
        var e = LocalDateTime.of(endDay, endTime).atZone(zone).toInstant()
        if (e.isBefore(s)) e = s.plusSeconds(3600)
        return s to e
    }
}

private fun defaultFields(calendarId: String?, start: Instant?, day: LocalDate): FormFields {
    val zone = ZoneId.systemDefault()
    val s = start?.atZone(zone)?.toLocalDateTime() ?: run {
        val now = LocalDateTime.now()
        // Próxima hora certa (hoje) ou 09:00 (outro dia).
        if (day == now.toLocalDate()) now.withMinute(0).withSecond(0).withNano(0).plusHours(1)
        else LocalDateTime.of(day, LocalTime.of(9, 0))
    }
    val e = s.plusHours(1)
    return FormFields(
        calendarId = calendarId, title = "",
        startDay = s.toLocalDate(), startTime = s.toLocalTime(),
        endDay = e.toLocalDate(), endTime = e.toLocalTime(),
        allDay = false, timezone = zone.id,
        repeat = "none", repeatUntil = null, alerts = listOf(15),
        location = "", notes = "", guests = emptyList(), guestsCanEdit = false, attachments = emptyList(),
    )
}

private fun fieldsFrom(ev: CalEvent): FormFields {
    val zone = ZoneId.systemDefault()
    val s = ev.start.atZone(zone).toLocalDateTime()
    val e = ev.end.atZone(zone).toLocalDateTime()
    return FormFields(
        calendarId = ev.calendarId, title = ev.data.title,
        startDay = s.toLocalDate(), startTime = s.toLocalTime(),
        endDay = if (ev.allDay) e.toLocalDate().minusDays(1) else e.toLocalDate(), endTime = e.toLocalTime(),
        allDay = ev.allDay, timezone = ev.raw.timezone.ifBlank { zone.id },
        repeat = ev.repeat, repeatUntil = ev.repeatUntil?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() },
        rule = ev.repeatRule,
        alerts = ev.raw.alerts.orEmpty(),
        location = ev.data.location, notes = ev.data.notes,
        guests = ev.raw.guests.map { it.email }, guestsCanEdit = ev.raw.guests_can_edit,
        attachments = ev.data.attachments,
    )
}

/** "1 h", "30 min", "1 h 30 min", "2 d". */
private fun durationLabel(f: FormFields): String {
    val (s, e) = f.dates()
    if (f.allDay) {
        val days = java.time.Duration.between(s, e).toHours().let { (it + 12) / 24 }
        return if (days <= 1) "Todo o dia".tr() else "$days d"
    }
    val minutes = java.time.Duration.between(s, e).toMinutes()
    val h = minutes / 60
    val m = minutes % 60
    return listOfNotNull(if (h > 0) "$h h" else null, if (m > 0) "$m min" else null).joinToString(" ").ifEmpty { "0 min" }
}

private fun fmtDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", appLocale()))
private fun fmtClock(t: LocalTime): String = t.format(DateTimeFormatter.ofPattern("HH:mm"))

/**
 * Novo evento / editar (mockup: × · título · Guardar). Quem só edita (convidado
 * com "podem editar") não mexe no calendário, convidados nem anexos.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventEditor(
    event: CalEvent?,
    start: Instant?,
    day: LocalDate,
    calendars: List<CalInfo>,
    ownEmail: String?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (EventForm) -> Unit,
    onAttach: (() -> Unit)? = null,
    pendingAttachments: List<EventAttachment> = emptyList(),
    onPendingConsumed: () -> Unit = {},
) {
    val editable = calendars.filter { it.canEdit }
    val defaultCal = editable.firstOrNull { !it.isBirthdays } ?: editable.firstOrNull()
    val initial = remember(event?.raw?.version, event?.id) { event?.let(::fieldsFrom) ?: defaultFields(defaultCal?.id, start, day) }
    var f by remember(initial) { mutableStateOf(initial) }
    val manages = event == null || event.canManage

    // Anexos escolhidos no Drive (Fase 3) entram aqui.
    androidx.compose.runtime.LaunchedEffect(pendingAttachments) {
        if (pendingAttachments.isEmpty()) return@LaunchedEffect
        val known = f.attachments.map { it.file_id }.toSet()
        f = f.copy(attachments = f.attachments + pendingAttachments.filterNot { it.file_id in known })
        onPendingConsumed()
    }

    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = f != initial
    val close = { if (dirty) confirmDiscard = true else onDismiss() }
    BackHandler(onBack = close)

    val calColor = calendarColor(calendars.firstOrNull { it.id == f.calendarId }?.color)
    val canSave = !saving && f.calendarId != null && (event == null || event.canEdit)

    fun submit() {
        val calId = f.calendarId ?: return
        val (s, e) = f.dates()
        onSave(
            EventForm(
                calendarId = calId, start = s, end = e, allDay = f.allDay, timezone = f.timezone,
                repeat = f.repeat,
                repeatUntil = if (f.repeat == "none") null else f.repeatUntil?.toString(),
                rule = if (f.repeat == "none") RepeatRule() else f.rule,
                alerts = f.alerts.distinct().sorted(),
                guestsCanEdit = f.guestsCanEdit && f.guests.isNotEmpty(),
                title = f.title.trim(), location = f.location.trim(), notes = f.notes,
                attachments = f.attachments,
                shareTokens = event?.raw?.files.orEmpty().mapNotNull { r -> r.share_token?.let { r.file_id to it } }.toMap(),
                guests = f.guests,
            ),
        )
    }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                // Topo: × · Novo evento · Guardar, com a linha na cor do calendário.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 6.dp)) {
                    IconButton(onClick = close) { Icon(Icons.Filled.Close, contentDescription = null) }
                    Text(
                        if (event == null) "Novo evento".tr() else "Editar evento".tr(),
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = ::submit,
                        enabled = canSave,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White),
                    ) {
                        val newGuests = f.guests.any { g -> event?.raw?.guests?.none { it.email.equals(g, true) } ?: true }
                        Text(if (newGuests && manages) "Guardar e convidar".tr() else "Guardar".tr(), fontWeight = FontWeight.SemiBold)
                    }
                }
                Box(Modifier.fillMaxWidth().height(3.dp).background(calColor))

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    // Título
                    BasicTextField(
                        value = f.title,
                        onValueChange = { f = f.copy(title = it.take(200)) },
                        textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        decorationBox = { inner ->
                            Box {
                                if (f.title.isEmpty()) Text("Adicionar título".tr(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                inner()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 20.dp))

                    // Calendários (chips)
                    if (manages) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            editable.forEach { cal ->
                                val c = calendarColor(cal.color)
                                val sel = cal.id == f.calendarId
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (sel) tint(c) else Color.Transparent)
                                        .border(1.dp, if (sel) c else MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                                        .clickable { f = f.copy(calendarId = cal.id) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                ) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                                    Spacer(Modifier.width(6.dp))
                                    Text(cal.name, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, color = if (sel) c else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    EditorDivider()

                    // Todo o dia
                    EditorRow(Icons.Outlined.AccessTime) {
                        Text("Todo o dia".tr(), fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = f.allDay,
                            onCheckedChange = { on ->
                                f = if (on) f.copy(allDay = true, endDay = maxOf(f.startDay, f.endDay)) else f.copy(allDay = false)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                        )
                    }

                    // Início
                    var pickDate by remember { mutableStateOf<String?>(null) }
                    var pickTime by remember { mutableStateOf<String?>(null) }
                    EditorRow(null) {
                        PickerText(fmtDate(f.startDay), Modifier.weight(1f)) { pickDate = "start" }
                        if (!f.allDay) PickerText(fmtClock(f.startTime)) { pickTime = "start" }
                    }
                    // Fim: "Duração 2 h 30 min · até 22:30"
                    EditorRow(null) {
                        Text("Duração %s".tr().format(durationLabel(f)), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text("até".tr(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        if (f.allDay || f.endDay != f.startDay) PickerText(shortDate(f.endDay)) { pickDate = "end" }
                        if (!f.allDay) {
                            Spacer(Modifier.width(8.dp))
                            PickerText(fmtClock(f.endTime)) { pickTime = "end" }
                        }
                        if (!f.allDay && f.endDay == f.startDay) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp).clickable { pickDate = "end" }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    EditorDivider()

                    // Repetição
                    var repeatMenu by remember { mutableStateOf(false) }
                    EditorRow(Icons.Outlined.Repeat, onClick = { repeatMenu = true }) {
                        Box(Modifier.weight(1f)) {
                            Text(repeatLabel(f.repeat, f.rule, f.repeatUntil != null), fontSize = 14.sp)
                            DropdownMenu(expanded = repeatMenu, onDismissRequest = { repeatMenu = false }) {
                                CalendarConst.REPEATS.forEach { r ->
                                    DropdownMenuItem(text = { Text(repeatLabel(r)) }, onClick = {
                                        // Como os presets da web: outro tipo repõe a regra (mantém o "após N vezes").
                                        if (r != f.repeat) f = f.copy(repeat = r, rule = RepeatRule(count = f.rule.count.takeIf { r != "none" }))
                                        repeatMenu = false
                                    })
                                }
                            }
                        }
                        Text("tocar para mudar".tr(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (f.repeat != "none") {
                        EditorRow(null) {
                            Text("Até".tr(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            PickerText(f.repeatUntil?.let(::fmtDate) ?: f.rule.count?.let(::timesLabel) ?: "Nunca".tr()) { pickDate = "until" }
                            if (f.repeatUntil != null || f.rule.count != null) {
                                IconButton(onClick = { f = f.copy(repeatUntil = null, rule = f.rule.copy(count = null)) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    EditorDivider()

                    // Alertas
                    var alertsDialog by remember { mutableStateOf(false) }
                    EditorRow(Icons.Outlined.NotificationsNone, onClick = { alertsDialog = true }) {
                        Text(
                            if (f.alerts.isEmpty()) "Sem alerta".tr() else f.alerts.sorted().joinToString(", ") { alertLabel(it, long = true) },
                            fontSize = 14.sp, modifier = Modifier.weight(1f),
                        )
                    }
                    EditorDivider()

                    // Local
                    EditorRow(Icons.Outlined.Place) {
                        PlainField(f.location, "Local ou ligação".tr(), Modifier.weight(1f)) { f = f.copy(location = it.take(500)) }
                    }
                    EditorDivider()

                    // Convidados
                    if (manages) {
                        GuestsBlock(
                            guests = f.guests,
                            statuses = event?.raw?.guests.orEmpty().associate { it.email.lowercase() to it.status },
                            ownEmail = ownEmail,
                            onChange = { f = f.copy(guests = it) },
                        )
                        if (f.guests.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { f = f.copy(guestsCanEdit = !f.guestsCanEdit) }.padding(start = 44.dp, end = 16.dp),
                            ) {
                                Checkbox(checked = f.guestsCanEdit, onCheckedChange = { f = f.copy(guestsCanEdit = it) })
                                Text("Os convidados podem editar o evento".tr(), fontSize = 13.sp)
                            }
                        }
                        EditorDivider()
                    }

                    // Notas
                    EditorRow(Icons.Outlined.Notes) {
                        PlainField(f.notes, "Adicionar notas".tr(), Modifier.weight(1f), singleLine = false) { f = f.copy(notes = it.take(20000)) }
                    }

                    // Anexos (como a secção "Ficheiros do Drive" da web)
                    if (f.attachments.isNotEmpty() || (manages && onAttach != null)) {
                        EditorDivider()
                        AttachmentsBlock(
                            attachments = f.attachments,
                            onRemove = if (manages) { a -> f = f.copy(attachments = f.attachments.filterNot { it.file_id == a.file_id }) } else null,
                            onAttach = if (manages) onAttach else null,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            (if (f.guests.isNotEmpty()) "Encriptado ponta a ponta — só tu e os convidados conseguem ler este evento."
                            else "Encriptado ponta a ponta — só tu consegues ler este evento.").tr(),
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(24.dp))

                    // Diálogos de data/hora/alertas
                    pickDate?.let { which ->
                        val current = when (which) {
                            "end" -> f.endDay
                            "until" -> f.repeatUntil ?: f.startDay
                            else -> f.startDay
                        }
                        DateDialog(current, onDismiss = { pickDate = null }) { d ->
                            f = when (which) {
                                "start" -> {
                                    // Mover o início arrasta o fim (mantém a duração).
                                    val shift = java.time.temporal.ChronoUnit.DAYS.between(f.startDay, d)
                                    f.copy(startDay = d, endDay = f.endDay.plusDays(shift))
                                }
                                "end" -> f.copy(endDay = if (d.isBefore(f.startDay)) f.startDay else d)
                                // Data ou contagem, nunca as duas (como a web).
                                else -> f.copy(repeatUntil = d, rule = f.rule.copy(count = null))
                            }
                            pickDate = null
                        }
                    }
                    pickTime?.let { which ->
                        TimeDialog(if (which == "start") f.startTime else f.endTime, onDismiss = { pickTime = null }) { t ->
                            f = if (which == "start") {
                                val (s, e) = f.dates()
                                val dur = java.time.Duration.between(s, e)
                                val ns = LocalDateTime.of(f.startDay, t)
                                val ne = ns.plus(dur)
                                f.copy(startTime = t, endDay = ne.toLocalDate(), endTime = ne.toLocalTime())
                            } else {
                                f.copy(endTime = t)
                            }
                            pickTime = null
                        }
                    }
                    if (alertsDialog) {
                        AlertsDialog(f.alerts, onDismiss = { alertsDialog = false }) { f = f.copy(alerts = it); alertsDialog = false }
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Descartar alterações?".tr(),
            message = "As alterações a este evento não foram guardadas.".tr(),
            confirmText = "Descartar".tr(),
            destructive = true,
            onDismiss = { confirmDiscard = false },
            onConfirm = { confirmDiscard = false; onDismiss() },
        )
    }
}

@Composable
private fun EditorDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))

@Composable
private fun EditorRow(icon: ImageVector?, onClick: (() -> Unit)? = null, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Box(Modifier.width(28.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

@Composable
private fun PickerText(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun PlainField(value: String, placeholder: String, modifier: Modifier, singleLine: Boolean = true, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                inner()
            }
        },
        modifier = modifier,
    )
}

/** Ficheiros anexados (cartões com o tipo) + "Anexar do Drive" tracejado. */
@Composable
private fun AttachmentsBlock(attachments: List<EventAttachment>, onRemove: ((EventAttachment) -> Unit)?, onAttach: (() -> Unit)?) {
    val primary = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Ficheiros do Drive".tr().uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            if (attachments.isNotEmpty()) Text("${attachments.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        attachments.forEach { a ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            ) {
                FileBadge(a.name, a.mime)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        a.name.substringAfterLast('.', "").uppercase().ifEmpty { "Ficheiro".tr() },
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onRemove != null) {
                    IconButton(onClick = { onRemove(a) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Remover".tr(), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        if (onAttach != null) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(primary.copy(alpha = 0.05f))
                    .drawBehind {
                        drawRoundRect(
                            color = primary.copy(alpha = 0.45f),
                            cornerRadius = CornerRadius(12.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                        )
                    }
                    .clickable(onClick = onAttach),
            ) {
                Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = primary)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (attachments.isEmpty()) "Anexar do Drive".tr() else "Anexar mais ficheiros".tr(),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = primary,
                )
            }
        }
    }
}

/** Convidados por email: separa por espaços/vírgulas/; e ignora o próprio email (GuestsField.vue). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuestsBlock(guests: List<String>, statuses: Map<String, String>, ownEmail: String?, onChange: (List<String>) -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun add() {
        val emails = input.split(Regex("[\\s,;]+")).map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (emails.isEmpty()) return
        if (emails.any { !EMAIL.matches(it) }) { error = "Email inválido".tr(); return }
        val known = guests.map { it.lowercase() }.toSet()
        val added = emails.filter { it !in known && !it.equals(ownEmail, ignoreCase = true) }.distinct()
        onChange(guests + added)
        input = ""
        // O próprio email e os repetidos não entram: diz porquê em vez de não fazer nada.
        error = when {
            added.isNotEmpty() -> null
            emails.any { it.equals(ownEmail, ignoreCase = true) } -> "Esse é o teu email — já és o organizador do evento.".tr()
            else -> "Já está na lista de convidados.".tr()
        }
    }
    EditorRow(Icons.Outlined.PersonAddAlt) {
        Column(Modifier.weight(1f)) {
            BasicTextField(
                value = input,
                onValueChange = { input = it; error = null },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) Text("Adicionar convidados (email)".tr(), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        inner()
                    }
                },
            )
            error?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
        }
        TextButton(onClick = ::add, enabled = input.isNotBlank()) { Text("Adicionar".tr(), fontWeight = FontWeight.SemiBold) }
    }
    if (guests.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 44.dp, end = 16.dp, bottom = 10.dp),
        ) {
            guests.forEachIndexed { i, email ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .padding(start = 4.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(22.dp).clip(CircleShape).background(Color(listOf(0xFF0F8A5F, 0xFF2563EB, 0xFFE5486F, 0xFFD97706)[i % 4])),
                    ) {
                        Text(email.take(2).uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(email, fontSize = 12.sp)
                    statuses[email.lowercase()]?.let { st ->
                        Text(" · ${guestStatus(st)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onChange(guests.filterNot { it == email }) }, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(current: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    // O DatePicker trabalha em UTC.
    val state = rememberDatePickerState(initialSelectedDateMillis = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar".tr()) } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(current: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar".tr()) } },
        text = { TimePicker(state = state) },
    )
}

@Composable
private fun AlertsDialog(current: List<Int>, onDismiss: () -> Unit, onDone: (List<Int>) -> Unit) {
    val picked = remember { mutableStateListOf<Int>().apply { addAll(current) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alertas".tr()) },
        text = {
            Column {
                // Os personalizados da web também aparecem (para se poderem tirar).
                (CalendarConst.ALERTS + current).distinct().sorted().forEach { m ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { if (m in picked) picked.remove(m) else picked.add(m) },
                    ) {
                        Checkbox(checked = m in picked, onCheckedChange = { if (it) picked.add(m) else picked.remove(m) })
                        Text(alertLabel(m, long = true))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(picked.distinct().sorted()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar".tr()) } },
    )
}
