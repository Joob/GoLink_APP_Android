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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import co.golink.tester.domain.calendar.QuickCalendar
import co.golink.tester.domain.calendar.QuickParse
import co.golink.tester.ui.common.appLocale
import co.golink.tester.ui.i18n.tr
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlinx.coroutines.delay

/**
 * Evento Rápido (paridade com QuickEventPopover.vue): uma frase → evento,
 * interpretada no aparelho e gravada cifrada pelo caminho normal.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickEventSheet(
    viewModel: CalendarViewModel,
    calendars: List<CalInfo>,
    saving: Boolean,
    onDismiss: () -> Unit,
) {
    val ready by viewModel.quickReady.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    val editable = remember(calendars) { calendars.filter { it.canEdit } }
    val fallback = editable.firstOrNull { it.visible && !it.isBirthdays } ?: editable.firstOrNull()
    val locales = remember { viewModel.quickLocales() }
    val dayFirst = remember { AndroidQuickLocaleSource.dayFirst() }
    val parsed: QuickParse? = remember(text, ready, editable) {
        if (!ready) null
        else viewModel.quickParser.parse(
            text,
            locales = locales,
            dayFirst = dayFirst,
            calendars = editable.map { QuickCalendar(it.id, it.name) },
            defaultCalendarId = fallback?.id,
        )
    }
    val calendar = editable.firstOrNull { it.id == parsed?.calendarId } ?: fallback
    val canAdd = !saving && parsed?.ok == true && calendar != null

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
        viewModel.quickSave(toForm(p, calendar.id))
    }

    val examples = remember(editable) {
        val tag = { i: Int -> (editable.getOrNull(i) ?: editable.firstOrNull())?.let { " #" + it.name.split(' ').first().lowercase() }.orEmpty() }
        listOf("Dentista sexta 9:30".tr() + tag(0), "Chamada com a equipa amanhã às 15h durante 30 min".tr() + tag(1), "Aniversário da Ana 2 de fevereiro".tr())
    }
    val errorText = parsed?.takeIf { showError && !it.ok && it.error != "empty" }?.error?.let(::errorLabel)
    val primary = MaterialTheme.colorScheme.primary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 12.dp)) {
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
                errorText ?: "Escreve como dirias — dia, hora, #calendário, @local. Funciona em qualquer língua.".tr(),
                fontSize = 12.sp,
                color = if (errorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (errorText != null) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.padding(start = 2.dp, top = 6.dp),
            )

            if (parsed?.ok == true) {
                SectionLabel("Percebido".tr())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(Icons.Outlined.CalendarToday, dateLabel(parsed))
                    Chip(Icons.Outlined.Schedule, timeLabel(parsed))
                    calendar?.let { Chip(Icons.Outlined.LocalOffer, it.name) }
                    if (parsed.location.isNotEmpty()) Chip(Icons.Outlined.Place, parsed.location)
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
            } else {
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
                TextButton(onClick = { viewModel.quickMoreOptions(parsed?.takeIf { it.ok } ?: QuickParse(title = text.trim(), calendarId = calendar?.id)) }) {
                    Text("Mais opções".tr(), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = ::submit,
                    enabled = canAdd,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = Color.White),
                ) { Text("Adicionar".tr(), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

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
    val pattern = if (d.year == today.year) "d MMM" else "d MMM yyyy"
    val short = d.format(DateTimeFormatter.ofPattern(pattern, loc))
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
    val plus = if (p.endDay != null && p.endDay != p.startDay) " (+1)" else ""
    return "${p.startTime?.format(f)} – ${p.endTime?.format(f)}$plus"
}

/** Mesmo formato que o editor grava (dia inteiro: [00:00 do dia, 00:00 do dia seguinte)). */
private fun toForm(p: QuickParse, calendarId: String): EventForm {
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
        repeat = "none", repeatUntil = null, alerts = listOf(15), guestsCanEdit = false,
        title = p.title, location = p.location, notes = "", attachments = emptyList(), guests = emptyList(),
    )
}
