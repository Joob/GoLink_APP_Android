package co.golink.tester.ui.screens.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.data.calendar.AndroidQuickLocaleSource
import co.golink.tester.data.calendar.EventForm
import co.golink.tester.domain.calendar.CalInfo
import co.golink.tester.domain.calendar.CalendarConst
import co.golink.tester.domain.calendar.EventAttachment
import co.golink.tester.domain.calendar.RepeatRule
import co.golink.tester.domain.calendar.QuickCalendar
import co.golink.tester.domain.calendar.QuickEventParser
import co.golink.tester.domain.calendar.QuickParse
import co.golink.tester.ui.common.appLocale
import co.golink.tester.ui.i18n.tr
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Repetir/alertas/convidados/notas/anexos já resolvidos (frase + botões); vão para o "Mais opções" também. */
data class QuickExtras(
    val repeat: String = "none",
    val rule: RepeatRule = RepeatRule(),
    val repeatUntil: LocalDate? = null,
    val alerts: List<Int> = listOf(15),
    val guests: List<String> = emptyList(),
    val guestsCanEdit: Boolean = false,
    val notes: String = "",
    val attachments: List<EventAttachment> = emptyList(),
)

/**
 * Evento Rápido (paridade com QuickEventPopover.vue): uma frase → evento,
 * interpretada no aparelho e gravada cifrada pelo caminho normal. Repetir,
 * alertas, convidados, notas e anexos vêm da frase ou dos botões — o que se
 * muda à mão ganha à frase.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickEventSheet(
    viewModel: CalendarViewModel,
    calendars: List<CalInfo>,
    ownEmail: String?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onAttach: () -> Unit,
    pendingAttachments: List<EventAttachment>,
    onPendingConsumed: () -> Unit,
) {
    val ready by viewModel.quickReady.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    // Feriados são eventos virtuais (calendar_id "holidays"): nunca entram aqui.
    val editable = remember(calendars) { calendars.filter { it.canEdit && it.id != "holidays" } }
    val fallback = editable.firstOrNull { it.visible && !it.isBirthdays } ?: editable.firstOrNull()
    val locales = remember { viewModel.quickLocales() }
    val dayFirst = remember { AndroidQuickLocaleSource.dayFirst() }
    // Fora da thread principal: cada tecla cancela a anterior; o resultado diz a que texto pertence.
    val parsedFor by produceState<Pair<String, QuickParse>?>(null, text, ready, editable) {
        if (!ready) {
            value = null
            return@produceState
        }
        val t = text
        value = t to withContext(Dispatchers.Default) {
            viewModel.quickParser.parse(
                t,
                locales = locales,
                dayFirst = dayFirst,
                calendars = editable.map { QuickCalendar(it.id, it.name) },
                defaultCalendarId = fallback?.id,
                selfEmail = ownEmail,
            )
        }
    }
    val parsed: QuickParse? = parsedFor?.second
    val calendar = editable.firstOrNull { it.id == parsed?.calendarId } ?: fallback
    // Só se grava o que corresponde ao texto atual (não o da tecla anterior).
    val canAdd = !saving && parsed?.ok == true && parsedFor?.first == text && calendar != null

    // Escolhas à mão (botões). Repetir/alertas/notas: depois de mexer, ganham à frase.
    var panel by remember { mutableStateOf<String?>(null) }
    var manual by remember { mutableStateOf(QuickExtras()) }
    var touchedRepeat by remember { mutableStateOf(false) }
    var touchedAlerts by remember { mutableStateOf(false) }
    var touchedNotes by remember { mutableStateOf(false) }
    var removedGuests by remember { mutableStateOf(setOf<String>()) }
    val pr = parsed?.repeat
    val fromText = !touchedRepeat && pr != null
    val extras = manual.copy(
        repeat = if (fromText) pr!!.repeat else manual.repeat,
        rule = if (fromText) RepeatRule(interval = pr!!.interval, days = pr.days) else manual.rule,
        repeatUntil = if (fromText) parsed?.repeatUntil else manual.repeatUntil,
        alerts = (if (touchedAlerts) manual.alerts else parsed?.alerts ?: manual.alerts).take(MAX_ALERTS),
        guests = (manual.guests + parsed?.guests.orEmpty().filter { it !in removedGuests && !it.equals(ownEmail, true) }).distinct(),
        notes = if (touchedNotes) manual.notes else parsed?.notes?.ifEmpty { null } ?: manual.notes,
    )
    val startDay = parsed?.startDay ?: LocalDate.now()

    // Anexos escolhidos no Drive entram aqui.
    LaunchedEffect(pendingAttachments) {
        if (pendingAttachments.isEmpty()) return@LaunchedEffect
        val known = manual.attachments.map { it.file_id }.toSet()
        manual = manual.copy(attachments = manual.attachments + pendingAttachments.filterNot { it.file_id in known })
        onPendingConsumed()
    }

    // Erro ao vivo, mas sem piscar a cada tecla.
    LaunchedEffect(text) {
        showError = false
        delay(700)
        showError = true
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun submit() {
        val p = parsed
        if (!canAdd || p == null || calendar == null) {
            showError = true
            return
        }
        viewModel.quickSave(toForm(p, calendar.id, extras))
    }

    val examples = remember(editable) {
        val tag = { i: Int -> (editable.getOrNull(i) ?: editable.firstOrNull())?.let { " #" + it.name.split(' ').first().lowercase() }.orEmpty() }
        listOf("Dentista sexta 9:30".tr() + tag(0), "Ginásio todas as segundas 7h 10 min antes".tr(), "Férias do dia 1 ao dia 7 de fevereiro".tr())
    }
    val errorText = parsed?.takeIf { showError && !it.ok && it.error != "empty" }?.error?.let(::errorLabel)
    val primary = MaterialTheme.colorScheme.primary
    var alertsDialog by remember { mutableStateOf(false) }
    var untilDialog by remember { mutableStateOf(false) }

    fun setRepeat(change: (QuickExtras) -> QuickExtras) {
        // Ao mexer pela 1.ª vez, parte do que a frase deu.
        val from = if (touchedRepeat) manual else manual.copy(repeat = extras.repeat, rule = extras.rule, repeatUntil = extras.repeatUntil)
        manual = change(from)
        touchedRepeat = true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(bottom = 12.dp),
        ) {
            Text("Evento rápido".tr(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(300) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text("ex.: Almoço com a Ana amanhã 13h @Cais".tr(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null, tint = primary) },
                trailingIcon = if (!ready) ({ CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }) else null,
                singleLine = true,
                isError = errorText != null,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primary, cursorColor = primary),
            )
            Text(
                errorText ?: "Escreve como dirias — dia, hora, #calendário, @local. Junta emails para convidar, \"todas as segundas\", \"10 min antes\" ou // notas. Funciona em qualquer língua.".tr(),
                fontSize = 12.sp,
                color = if (errorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (errorText != null) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.padding(start = 2.dp, top = 6.dp),
            )

            // Repetir · Alertas · Convidados · Notas · Ficheiros
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                val tool = @Composable { id: String, icon: ImageVector, label: String, set: Boolean, count: Int ->
                    ToolButton(icon, label, set = set, open = panel == id, count = count, modifier = Modifier.weight(1f)) {
                        if (id == "alerts") alertsDialog = true else panel = if (panel == id) null else id
                    }
                }
                tool("repeat", Icons.Outlined.Repeat, "Repetir".tr(), extras.repeat != "none", 0)
                tool("alerts", Icons.Outlined.NotificationsNone, "Alertas".tr(), touchedAlerts || parsed?.alerts != null, 0)
                tool("guests", Icons.Outlined.PersonAddAlt, "Convidados".tr(), extras.guests.isNotEmpty(), extras.guests.size)
                tool("notes", Icons.Outlined.Notes, "Notas".tr(), extras.notes.isNotEmpty(), 0)
                tool("files", Icons.Outlined.AttachFile, "Ficheiros do Drive".tr(), extras.attachments.isNotEmpty(), extras.attachments.size)
            }

            when (panel) {
                "repeat" -> Panel("Repetir".tr()) {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        PanelPick(repeatLabel(extras.repeat, extras.rule, extras.repeatUntil != null)) { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            CalendarConst.REPEATS.forEach { r ->
                                DropdownMenuItem(text = { Text(repeatLabel(r)) }, onClick = {
                                    // Outro tipo repõe a regra (como o editor).
                                    if (r != extras.repeat) setRepeat { it.copy(repeat = r, rule = RepeatRule(), repeatUntil = it.repeatUntil.takeIf { r != "none" }) }
                                    menu = false
                                })
                            }
                        }
                    }
                    if (extras.repeat != "none") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Até".tr(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            PanelPick(extras.repeatUntil?.let(::untilLabel) ?: "Nunca".tr()) { untilDialog = true }
                            if (extras.repeatUntil != null) {
                                IconButton(onClick = { setRepeat { it.copy(repeatUntil = null) } }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
                "guests" -> Panel("Convidados".tr()) {
                    GuestsBlock(
                        guests = extras.guests,
                        statuses = emptyMap(),
                        ownEmail = ownEmail,
                        onChange = { list ->
                            val gone = extras.guests.filterNot { it in list }
                            removedGuests = (removedGuests + gone) - list.toSet()
                            // Só os escritos à mão: os da frase saem se se apagarem da frase.
                            val fromText = parsed?.guests.orEmpty().toSet()
                            manual = manual.copy(guests = list.filterNot { it in fromText })
                        },
                    )
                    if (extras.guests.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { manual = manual.copy(guestsCanEdit = !manual.guestsCanEdit) },
                        ) {
                            Checkbox(checked = manual.guestsCanEdit, onCheckedChange = { manual = manual.copy(guestsCanEdit = it) })
                            Text("Os convidados podem editar o evento".tr(), fontSize = 13.sp)
                        }
                    }
                }
                "notes" -> Panel("Notas".tr()) {
                    OutlinedTextField(
                        value = extras.notes,
                        onValueChange = { touchedNotes = true; manual = manual.copy(notes = it.take(20000)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Adicionar notas".tr()) },
                        minLines = 3,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primary, cursorColor = primary),
                    )
                }
                "files" -> Panel("Ficheiros do Drive".tr()) {
                    extras.attachments.forEach { a ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(a.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { manual = manual.copy(attachments = manual.attachments.filterNot { it.file_id == a.file_id }) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    TextButton(onClick = onAttach) {
                        Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Anexar do Drive".tr(), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (parsed?.ok == true) {
                SectionLabel("Percebido".tr())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(Icons.Outlined.CalendarToday, dateLabel(parsed))
                    Chip(Icons.Outlined.Schedule, timeLabel(parsed))
                    calendar?.let { Chip(Icons.Outlined.LocalOffer, it.name) }
                    if (parsed.location.isNotEmpty()) Chip(Icons.Outlined.Place, parsed.location)
                    if (extras.repeat != "none") Chip(Icons.Outlined.Repeat, repeatLabel(extras.repeat, extras.rule, extras.repeatUntil != null))
                    if (touchedAlerts || parsed.alerts != null) {
                        Chip(Icons.Outlined.NotificationsNone, if (extras.alerts.isEmpty()) "Sem alerta".tr() else extras.alerts.sorted().joinToString(", ") { alertLabel(it) })
                    }
                    if (extras.guests.isNotEmpty()) Chip(Icons.Outlined.PersonAddAlt, countLabel(extras.guests.size, "%d convidado", "%d convidados"))
                    if (extras.notes.isNotEmpty()) Chip(Icons.Outlined.Notes, extras.notes)
                    if (extras.attachments.isNotEmpty()) Chip(Icons.Outlined.AttachFile, countLabel(extras.attachments.size, "%d ficheiro", "%d ficheiros"))
                }
                parsed.unknownTag?.let { tag ->
                    Text(
                        "Nenhum calendário corresponde a #%s — vai para %s.".tr().format(tag, calendar?.name.orEmpty()),
                        fontSize = 12.sp, color = Color(0xFFB45309), modifier = Modifier.padding(top = 6.dp),
                    )
                }
                // Pré-visualização com a cor do calendário.
                Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)),
                ) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(calendarColor(calendar?.color)))
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(parsed.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${dateLabel(parsed)} · ${timeLabel(parsed)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val meta = listOfNotNull(calendar?.name, parsed.location.ifEmpty { null }).joinToString(" · ")
                        if (meta.isNotEmpty()) Text(meta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else if (panel == null) {
                SectionLabel("Experimenta".tr())
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    examples.forEach { ex ->
                        Text(
                            ex, fontSize = 12.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(50))
                                .clickable { text = ex }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val draft = parsed?.takeIf { it.ok && parsedFor?.first == text }
                        ?: QuickParse(title = QuickEventParser.stripExtras(text), calendarId = calendar?.id)
                    viewModel.quickMoreOptions(draft, extras)
                }) {
                    Text("Mais opções".tr(), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = ::submit,
                    enabled = canAdd,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = Color.White),
                ) { Text(if (extras.guests.isNotEmpty()) "Guardar e convidar".tr() else "Adicionar".tr(), fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    if (alertsDialog) {
        AlertsDialog(extras.alerts, onDismiss = { alertsDialog = false }) {
            manual = manual.copy(alerts = it)
            touchedAlerts = true
            alertsDialog = false
        }
    }
    if (untilDialog) {
        DateDialog(extras.repeatUntil ?: startDay, onDismiss = { untilDialog = false }) { d ->
            setRepeat { it.copy(repeatUntil = if (d.isBefore(startDay)) startDay else d) }
            untilDialog = false
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, set: Boolean, open: Boolean, count: Int, modifier: Modifier, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val tint = if (set || open) primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(36.dp).clip(RoundedCornerShape(9.dp))
            .background(if (set) primary.copy(alpha = 0.08f) else Color.Transparent)
            .border(if (open) 1.5.dp else 1.dp, if (open) primary else if (set) primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp))
            .clickable(onClickLabel = label, onClick = onClick),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(17.dp))
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text("$count", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tint)
        }
    }
}

@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)).padding(10.dp),
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
private fun PanelPick(label: String, onClick: () -> Unit) {
    Text(
        label, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private fun countLabel(n: Int, one: String, many: String) = (if (n == 1) one else many).tr().format(n)

private fun untilLabel(d: LocalDate): String = co.golink.tester.ui.common.prefDate(d)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(appLocale()), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun Chip(icon: ImageVector, label: String) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(primary.copy(alpha = 0.08f))
            .border(1.dp, primary.copy(alpha = 0.3f), RoundedCornerShape(50)).padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = primary, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun errorLabel(code: String): String = when (code) {
    "no_day" -> "Falta o dia do mês.".tr()
    "invalid_date" -> "Essa data não existe.".tr()
    "no_title" -> "Escreve também o nome do evento.".tr()
    else -> "Indica o dia e o mês (ex.: 2 de fevereiro, amanhã, sexta).".tr()
}

/** "Amanhã, 3 nov." / "sex., 6 nov." (+ ano se não for o atual). */
private fun dateLabel(p: QuickParse): String {
    val d = p.startDay ?: return ""
    val loc = appLocale()
    val today = LocalDate.now()
    val fmt = { x: LocalDate -> x.format(DateTimeFormatter.ofPattern(if (x.year == today.year) "d MMM" else "d MMM yyyy", loc)) }
    val short = fmt(d)
    // Intervalo ("1 fev – 7 fev"): vários dias.
    p.endDay?.takeIf { it != d }?.let { return "$short – ${fmt(it)}" }
    val rel = when (java.time.temporal.ChronoUnit.DAYS.between(today, d)) {
        0L -> "Hoje".tr()
        1L -> "Amanhã".tr()
        -1L -> "Ontem".tr()
        else -> null
    }
    return if (rel != null) "$rel, $short" else "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, loc).replaceFirstChar { it.titlecase(loc) }}, $short"
}

private fun timeLabel(p: QuickParse): String {
    if (p.allDay) return "Todo o dia".tr()
    val f = DateTimeFormatter.ofPattern("HH:mm")
    return "${p.startTime?.format(f)} – ${p.endTime?.format(f)}"
}

/** Mesmo formato que o editor grava (dia inteiro: [00:00 do dia, 00:00 do dia seguinte)). */
private fun toForm(p: QuickParse, calendarId: String, x: QuickExtras): EventForm {
    val zone = ZoneId.systemDefault()
    val sd = p.startDay!!
    val ed = p.endDay ?: sd
    val (start, end) = if (p.allDay) {
        sd.atStartOfDay(zone).toInstant() to ed.plusDays(1).atStartOfDay(zone).toInstant()
    } else {
        sd.atTime(p.startTime).atZone(zone).toInstant() to ed.atTime(p.endTime).atZone(zone).toInstant()
    }
    return EventForm(
        calendarId = calendarId, start = start, end = end, allDay = p.allDay, timezone = zone.id,
        repeat = x.repeat,
        repeatUntil = if (x.repeat == "none") null else x.repeatUntil?.toString(),
        rule = if (x.repeat == "none") RepeatRule() else x.rule,
        alerts = x.alerts.distinct().sorted(),
        guestsCanEdit = x.guestsCanEdit && x.guests.isNotEmpty(),
        title = p.title, location = p.location, notes = x.notes, attachments = x.attachments, guests = x.guests,
    )
}
