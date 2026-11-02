package co.golink.tester.ui.screens.legacy

import androidx.compose.foundation.layout.Arrangement
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
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.i18n.lt
import co.golink.tester.ui.screens.admin.AdminSegmented
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Timing.vue */
@Composable
fun LegacyTimingPane(legacy: LegacyState, busy: Boolean, vm: LegacyViewModel) {
    val vault = legacy.vault ?: return
    val alarmed = vault.status in setOf("notified", "released")
    val editable = legacy.editable && !busy
    var confirmDisarm by remember { mutableStateOf(false) }
    var pausing by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LegacyHeader(lt("timing"), lt("timing_text")) {
            if (vault.status == "armed" || alarmed) StillHereButton(enabled = !busy, onClick = vm::checkin)
        }

        LegacyTimeline(vault.timeline)

        LegacyCard {
            CardTitle(lt("release_after_title"))
            Text(lt("release_after_text"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            DaysChoice(listOf(30, 60, 90, 180), vault.release_after_days, editable) { d ->
                vm.save(buildJsonObject { put("release_after_days", d) })
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outline)
            CardTitle(lt("wait_title"))
            Text(lt("wait_text"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            DaysChoice(listOf(3, 7, 14), vault.wait_days, editable) { d ->
                vm.save(buildJsonObject { put("wait_days", d) })
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outline)
            CardTitle(lt("counts_title"))
            Text(lt("counts_text"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
            listOf(
                "web" to vault.checkin_web,
                "android" to vault.checkin_android,
                "email" to vault.checkin_email,
            ).forEach { (channel, on) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(lt("channel_$channel"), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
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

        LegacyCard {
            CardTitle(lt("switch_title"))
            val switchText = when {
                vault.paused_until != null -> lt("paused_until", "date" to pickedDate(vault.paused_until))
                vault.status == "armed" || alarmed -> lt("switch_${vault.status}")
                else -> lt("switch_idle")
            }
            Text(switchText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vault.paused_until != null) {
                    OutlinedButton(onClick = vm::resume, enabled = !busy, shape = RoundedCornerShape(12.dp)) { Text(lt("resume")) }
                } else if (vault.status == "armed") {
                    OutlinedButton(onClick = { pausing = true }, enabled = !busy, shape = RoundedCornerShape(12.dp)) { Text(lt("pause")) }
                }
                if (vault.status == "armed" || alarmed) {
                    Button(
                        onClick = { confirmDisarm = true },
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text(lt("disarm")) }
                }
            }
        }
    }

    if (confirmDisarm) {
        ConfirmDialog(
            title = lt("disarm_title"),
            message = lt("disarm_text"),
            confirmText = lt("disarm"),
            destructive = true,
            onDismiss = { confirmDisarm = false },
            onConfirm = { confirmDisarm = false; vm.disarm() },
        )
    }
    if (pausing) {
        PauseDialog(onDismiss = { pausing = false }, onPause = { day -> pausing = false; vm.pause(day) })
    }
}

@Composable
private fun DaysChoice(options: List<Int>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    AdminSegmented(
        options = options.map { it to lt("days_n", "days" to it) },
        selected = selected,
        onSelect = { if (enabled && it != selected) onSelect(it) },
    )
}

private const val MAX_PAUSE_DAYS = 179L // o servidor aceita 180 a contar de agora

/** LegacyPausePopup.vue. O dia grava-se às 00:00 do servidor (UTC): a oeste, "amanhã" à noite já lá passou. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PauseDialog(onDismiss: () -> Unit, onPause: (String) -> Unit) {
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
