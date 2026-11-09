package co.golink.tester.ui.screens.legacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.data.legacy.LegacyState
import co.golink.tester.ui.i18n.lt
import co.golink.tester.domain.legacy.LegacyTimelineDto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Settings › Check-in & timing. */
@Composable
fun LegacyTimingPane(legacy: LegacyState, busy: Boolean, vm: LegacyViewModel) {
    val vault = legacy.vault ?: return
    val alarmed = vault.status in setOf("notified", "released")
    val editable = legacy.editable && !busy
    var confirmDisarm by remember { mutableStateOf(false) }
    var pausing by remember { mutableStateOf(false) }
    var pauseUntil by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LegacyCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${vault.timeline.opens_day}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(lt("days_without"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 5.dp))
            }
            Text(lt("days_without_text", "days" to vault.release_after_days, "wait" to vault.wait_days), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            TimelineBar(vault.timeline, vault.release_after_days)
        }

        SectionLabel(lt("inactive_for"))
        DaysChoice(listOf(30, 60, 90, 180), vault.release_after_days, editable) { d ->
            vm.save(buildJsonObject { put("release_after_days", d) })
        }
        SectionLabel(lt("then_wait"))
        DaysChoice(listOf(3, 7, 14), vault.wait_days, editable) { d ->
            vm.save(buildJsonObject { put("wait_days", d) })
        }
        Text(lt("wait_hint"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionLabel(lt("counts_title"))
        LegacyCard(contentPadding = 0) {
            listOf(
                "web" to vault.checkin_web,
                "android" to vault.checkin_android,
                "email" to vault.checkin_email,
            ).forEachIndexed { i, (channel, on) ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(lt("channel_$channel"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text(lt("channel_${channel}_hint"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = on,
                        enabled = editable,
                        onCheckedChange = { value -> vm.save(buildJsonObject { put("checkin_$channel", value) }) },
                    )
                }
            }
        }
        Text(lt("counts_text"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionLabel(lt("switch_title"))
        LegacyCard {
            val switchText = when {
                vault.paused_until != null -> lt("paused_until", "date" to pickedDate(vault.paused_until))
                vault.status == "armed" || alarmed -> lt("switch_${vault.status}")
                else -> lt("switch_idle")
            }
            Text(switchText, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vault.status == "armed" || alarmed) StillHereButton(enabled = !busy, onClick = vm::checkin)
                if (vault.paused_until != null) {
                    OutlinedButton(onClick = vm::resume, enabled = !busy, shape = RoundedCornerShape(12.dp)) { Text(lt("resume")) }
                } else if (vault.status == "armed") {
                    OutlinedButton(onClick = { pausing = true }, enabled = !busy, shape = RoundedCornerShape(12.dp)) { Text(lt("pause")) }
                }
            }
            if (vault.status == "armed" || alarmed) {
                TextButton(onClick = { confirmDisarm = true }, enabled = !busy, modifier = Modifier.padding(top = 4.dp)) {
                    Text(lt("disarm"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // Desarmar e pausar pedem step-up (password ou passkey).
    if (confirmDisarm) {
        LegacyStepUpDialog(LegacyStepUpRequest.Disarm(revoke = false), vm, onDismiss = { confirmDisarm = false })
    }
    if (pausing) {
        PauseDialog(onDismiss = { pausing = false }, onPause = { day -> pausing = false; pauseUntil = day })
    }
    pauseUntil?.let { day ->
        LegacyStepUpDialog(LegacyStepUpRequest.Pause(day), vm, onDismiss = { pauseUntil = null })
    }
}

/** Barra Today → Reminder → Wait starts → Opens, à escala dos dias. */
@Composable
private fun TimelineBar(t: LegacyTimelineDto, releaseAfter: Int) {
    val total = t.opens_day.coerceAtLeast(1)
    val reminder = t.reminders.firstOrNull()
    data class Mark(val label: String, val day: Int, val color: Color)
    val marks = listOfNotNull(
        Mark(lt("sim_today"), t.days_since.coerceIn(0, total), LegacyGreen),
        reminder?.let { Mark(lt("node_reminder"), it, Color(0xFF8B5CF6)) },
        Mark(lt("bar_wait"), releaseAfter, LegacyAmber),
        Mark(lt("bar_opens"), total, Color(0xFF6D28D9)),
    )
    val track = MaterialTheme.colorScheme.outline
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth
        val dot = 12.dp
        fun x(day: Int) = (w - dot) * (day.toFloat() / total).coerceIn(0f, 1f)
        Column {
            Box(Modifier.fillMaxWidth().height(dot)) {
                Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(track))
                // Espera em âmbar, o que já passou em verde.
                Box(Modifier.align(Alignment.CenterStart).offset(x = x(releaseAfter) + dot / 2).width(w - dot - x(releaseAfter)).height(6.dp).background(LegacyAmber.copy(alpha = 0.45f)))
                Box(Modifier.align(Alignment.CenterStart).width(x(t.days_since) + dot / 2).height(6.dp).clip(RoundedCornerShape(3.dp)).background(LegacyGreen.copy(alpha = 0.6f)))
                marks.forEach { m ->
                    Box(Modifier.offset(x = x(m.day)).size(dot).clip(CircleShape).background(m.color).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                marks.forEachIndexed { i, m ->
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = when (i) {
                            0 -> Alignment.Start
                            marks.size - 1 -> Alignment.End
                            else -> Alignment.CenterHorizontally
                        },
                    ) {
                        Text(if (i == 0) lt("sim_today") else lt("day_n", "day" to m.day), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(m.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun DaysChoice(options: List<Int>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    LegacySegmented(
        options = options.map { it to lt("days_short", "days" to it) },
        selected = selected,
        onSelect = { if (enabled && it != selected) onSelect(it) },
    )
}

private const val MAX_PAUSE_DAYS = 179L // o servidor aceita 180 a contar de agora

/** LegacyPausePopup.vue. O dia grava-se às 00:00 do servidor (UTC): a oeste, "amanhã" à noite já lá passou. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PauseDialog(onDismiss: () -> Unit, onPause: (String) -> Unit) {
    val today = LocalDate.now()
    val minDay = maxOf(LocalDate.now(ZoneOffset.UTC).plusDays(1), today.plusDays(1))
    val maxDay = today.plusDays(MAX_PAUSE_DAYS)
    fun millis(d: LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = millis(today.plusDays(14)),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in millis(minDay)..millis(maxDay)
            override fun isSelectableYear(year: Int): Boolean = year in minDay.year..maxDay.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    val day = Instant.ofEpochMilli(state.selectedDateMillis ?: return@TextButton).atZone(ZoneOffset.UTC).toLocalDate()
                    onPause(day.toString())
                },
            ) { Text(lt("pause")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(lt("cancel")) } },
    ) {
        Column {
            Text(lt("pause_title"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp))
            Text(lt("pause_text"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
            DatePicker(state = state, title = null, headline = null, showModeToggle = false)
        }
    }
}
