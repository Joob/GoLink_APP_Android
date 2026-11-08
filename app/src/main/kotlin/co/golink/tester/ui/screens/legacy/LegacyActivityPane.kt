package co.golink.tester.ui.screens.legacy

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.data.legacy.LegacyCrypto
import co.golink.tester.data.legacy.LegacyEventRow
import co.golink.tester.ui.i18n.lt
import co.golink.tester.ui.screens.admin.AdminLoadMore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CATEGORIES = listOf("all", "checkin", "vault", "people", "settings")

/** Activity.vue. Cada linha vem selada à chave pública do dono: o servidor só vê a categoria e a data. */
@Composable
fun LegacyActivityPane(vm: LegacyActivityViewModel, checkins: Int, onToast: (LegacyToast) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.start() }
    // "Ainda estou aqui" noutro pane: junta a linha nova ao topo.
    LaunchedEffect(checkins) { if (checkins > 0) vm.refresh() }

    // Gerado no aparelho: o servidor não consegue ler o registo. Inclui as páginas por carregar.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            vm.setExporting(true)
            runCatching {
                val rows = vm.allRows()
                val header = listOf(lt("csv_date"), lt("csv_category"), lt("csv_event"))
                val lines = listOf(header) + rows.map { listOf(dateTime(it.createdAt), lt("log_${it.category}"), eventText(it)) }
                val csv = lines.joinToString("\r\n") { line -> line.joinToString(",") { LegacyCrypto.csvCell(it) } }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: error("sem saída")
                }
            }.onFailure { onToast(LegacyToast("error", true)) }
            vm.setExporting(false)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                LegacyFilterChips(
                    options = CATEGORIES.map { it to lt("log_$it") },
                    selected = state.category,
                    onSelect = { if (it != state.category) vm.setCategory(it) },
                )
            }
            IconButton(
                onClick = {
                    val name = if (state.category == "all") "legacy-activity.csv" else "legacy-activity-${state.category}.csv"
                    exportLauncher.launch(name)
                },
                enabled = state.rows.isNotEmpty() && !state.exporting,
            ) {
                Icon(Icons.Outlined.FileDownload, contentDescription = lt("export_csv"), modifier = Modifier.size(20.dp))
            }
        }

        when {
            state.loading && state.rows.isEmpty() -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
            state.failed -> LegacyCard {
                Text(lt("error"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { vm.load() }, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 10.dp)) { Text(lt("retry")) }
            }
            state.rows.isEmpty() -> LegacyCard { Text(lt("no_activity"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else -> {
                val today = LocalDate.now()
                state.rows.groupBy { groupOf(it.createdAt, today) }.forEach { (group, rows) ->
                    SectionLabel(group.second, Modifier.padding(top = 6.dp))
                    rows.forEach { row -> ActivityRow(row, today) }
                }
            }
        }

        if (state.cursor != null) AdminLoadMore(loading = state.loading) { vm.load(state.cursor) }
    }
}

private fun localDate(iso: String): LocalDate? = runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
    ?: runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()

/** Hoje / Esta semana / mês e ano. A chave ordena como as linhas (mais recentes primeiro). */
private fun groupOf(iso: String, today: LocalDate): Pair<String, String> {
    val day = localDate(iso) ?: return "?" to ""
    return when {
        day == today -> "today" to lt("group_today")
        !day.isBefore(today.minusDays(6)) -> "week" to lt("group_week")
        else -> day.withDayOfMonth(1).toString() to day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "LLLL" else "LLLL yyyy", legacyLocale()))
    }
}

@Composable
private fun ActivityRow(row: LegacyEventRow, today: LocalDate) {
    val (icon, color) = when (row.category) {
        "checkin" -> Icons.Outlined.CheckCircle to LegacyGreen
        "people" -> Icons.Outlined.People to Color(0xFF7C3AED)
        "settings" -> Icons.Outlined.Settings to Color(0xFF64748B)
        else -> Icons.Outlined.Lock to Color(0xFFE11D48)
    }
    val day = localDate(row.createdAt)
    val whenText = runCatching {
        val at = OffsetDateTime.parse(row.createdAt).atZoneSameInstant(ZoneId.systemDefault())
        if (day == today) at.format(DateTimeFormatter.ofPattern("HH:mm")) else at.format(DateTimeFormatter.ofPattern("d MMM", legacyLocale()))
    }.getOrElse { dateTime(row.createdAt) }
    LegacyCard(contentPadding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TintIcon(icon, color, size = 34)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(eventText(row), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(lt("log_${row.category}"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Text(whenText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** text() do Activity.vue. */
fun eventText(row: LegacyEventRow): String {
    val data = row.data.mapValues { (_, v) -> (v as? JsonPrimitive)?.content ?: v.toString() }
    val where = listOfNotNull(data["browser"], data["device"]).filter { it.isNotBlank() }.joinToString(" · ")
    val until = data["until"]?.let { pickedDate(it) }.orEmpty()
    val params = (data - "kind" + mapOf("until" to until, "where" to if (where.isNotEmpty()) "($where)" else ""))
        .map { (k, v) -> k to v }.toTypedArray()
    return when (row.kind) {
        "checkin" -> lt("ev_checkin_${data["source"]}", *params).trim()
        "reminder" -> lt("ev_reminder", *params)
        else -> lt("ev_${row.kind}", *params)
    }
}
