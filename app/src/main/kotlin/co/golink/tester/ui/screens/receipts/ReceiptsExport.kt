package co.golink.tester.ui.screens.receipts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.data.receipts.ReceiptsState
import co.golink.tester.ui.i18n.rt

/** Exportar (gaveta): PDF do IRS, CSV de 14 colunas e ZIP das fotos, gerados no aparelho. */
@Composable
fun ReceiptsExportPage(vm: ReceiptsViewModel, state: ReceiptsState, year: Int, years: List<Int>, onBack: () -> Unit) {
    val p = LocalRc.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    var y by remember { mutableIntStateOf(year) }
    var pending by remember { mutableStateOf<String?>(null) }

    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> if (uri != null) vm.export("pdf", uri, y) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) vm.export("csv", uri, y) }
    val zip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) vm.export("zip", uri, y) }

    Column(Modifier.fillMaxSize()) {
        RcBackBar(rt("efatura.export"), onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(rt("efatura.export_sub"), fontSize = 13.5.sp, color = p.muted, modifier = Modifier.padding(4.dp))
            if (years.size > 1) {
                Spacer(Modifier.height(8.dp))
                RcSegmented(years.sorted().map { it.toString() to it.toString() }, y.toString(), { y = it.toInt() })
            }
            Spacer(Modifier.height(12.dp))
            val none = state.active.none { !it.locked && it.date.startsWith(y.toString()) }
            if (none) Text(rt("app.no_receipts_year"), Modifier.padding(8.dp), color = p.muted)
            RcCard {
                ExportRow("PDF", Color(0xFFE11D48), rt("efatura.x_pdf", "year" to y), rt("efatura.x_pdf_text"), enabled = !busy && !none, running = busy && pending == "pdf") {
                    pending = "pdf"; vm.expectExternal(); pdf.launch("irs-$y-summary.pdf")
                }
                RcDivider(Modifier.padding(start = 70.dp))
                ExportRow("CSV", Color(0xFF059669), rt("efatura.x_csv"), rt("efatura.x_csv_text"), enabled = !busy && !none, running = busy && pending == "csv") {
                    pending = "csv"; vm.expectExternal(); csv.launch("receipts-$y.csv")
                }
                RcDivider(Modifier.padding(start = 70.dp))
                ExportRow("ZIP", Color(0xFF0284C7), rt("efatura.x_zip"), progress ?: rt("efatura.x_zip_text"), enabled = !busy && !none, running = busy && pending == "zip") {
                    if (!vm.hasPhotos(y)) vm.toast(rt("efatura.no_photos"))
                    else { pending = "zip"; vm.expectExternal(); zip.launch("receipt-photos-$y.zip") }
                }
            }
            Row(Modifier.padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, tint = p.soft, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(rt("app.read_on_phone"), fontSize = 12.sp, color = p.muted)
            }
        }
    }
}

@Composable
private fun ExportRow(badge: String, color: Color, title: String, text: String, enabled: Boolean, running: Boolean, onClick: () -> Unit) {
    val p = LocalRc.current
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = if (p.dark) 0.2f else 0.1f)), contentAlignment = Alignment.Center) {
            Text(badge, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (enabled || running) p.text else p.soft)
            Text(text, fontSize = 12.5.sp, color = p.muted)
        }
        if (running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = p.accent)
    }
}
