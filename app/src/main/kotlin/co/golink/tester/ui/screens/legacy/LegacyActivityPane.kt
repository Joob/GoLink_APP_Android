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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.data.legacy.LegacyCrypto
import co.golink.tester.data.legacy.LegacyEventRow
import co.golink.tester.ui.i18n.lt
import co.golink.tester.ui.screens.admin.AdminLoadMore
import co.golink.tester.ui.screens.admin.AdminSegmented
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive

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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LegacyHeader(lt("activity"), lt("activity_text")) {
            OutlinedButton(
                onClick = {
                    val name = if (state.category == "all") "legacy-activity.csv" else "legacy-activity-${state.category}.csv"
                    exportLauncher.launch(name)
                },
                enabled = state.rows.isNotEmpty() && !state.exporting,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(lt("export_csv"))
            }
        }

        AdminSegmented(
            options = CATEGORIES.map { it to lt("log_$it") },
            selected = state.category,
            onSelect = { if (it != state.category) vm.setCategory(it) },
        )

        when {
            state.loading && state.rows.isEmpty() -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
            state.failed -> LegacyCard {
                Text(lt("error"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { vm.load() }, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 10.dp)) { Text(lt("retry")) }
            }
            state.rows.isEmpty() -> LegacyCard { Text(lt("no_activity"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else -> LegacyCard {
                state.rows.forEachIndexed { i, row ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        LegacyIconBox(
                            when (row.category) {
                                "checkin" -> Icons.Outlined.CheckCircle
                                "people" -> Icons.Outlined.People
                                "settings" -> Icons.Outlined.Settings
                                else -> Icons.Outlined.Lock
                            },
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(eventText(row), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(dateTime(row.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (state.cursor != null) AdminLoadMore(loading = state.loading) { vm.load(state.cursor) }
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
