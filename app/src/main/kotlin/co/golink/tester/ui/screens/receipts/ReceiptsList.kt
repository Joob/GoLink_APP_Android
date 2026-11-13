package co.golink.tester.ui.screens.receipts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.data.receipts.IrsRules
import co.golink.tester.data.receipts.RcCategories
import co.golink.tester.data.receipts.RcFormat
import co.golink.tester.data.receipts.Receipt
import co.golink.tester.data.receipts.ReceiptsState
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.i18n.rt
import co.golink.tester.ui.i18n.rtc
import java.time.LocalDate
import java.time.LocalDateTime

/** Recibo com o estado e-Fatura já calculado (a lista não o recalcula por linha). */
data class RcRow(val r: Receipt, val status: String)

private data class RcListModel(val rows: List<RcRow>, val counts: Map<String, Int>, val irsCounts: Map<String, Int>, val catCount: Int, val yearCount: Int)

/** Filtro da lista: all | validate | no_nif | pending | irs-<k> | cat-<k> (= ?status/?irs/?cat da web). */
private fun matches(row: RcRow, filter: String): Boolean = when {
    filter == "all" -> true
    filter.startsWith("irs-") -> row.r.irsCat == filter.removePrefix("irs-")
    filter.startsWith("cat-") -> row.r.spendCat == filter.removePrefix("cat-")
    else -> row.status == filter
}

@Composable
fun ReceiptsListPane(
    vm: ReceiptsViewModel,
    state: ReceiptsState,
    year: Int,
    years: List<Int>,
    onYear: (Int) -> Unit,
    filter: String,
    onFilter: (String) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    onOpen: (String) -> Unit,
    openMenu: (() -> Unit)?,
) {
    val p = LocalRc.current
    val model by rememberComputed(state.items, state.meta.household, year, query, filter) {
        val now = LocalDateTime.now()
        val y = year.toString()
        val yearList = state.active.filter { it.locked || it.date.startsWith(y) }
            .map { RcRow(it, if (it.locked) "pending" else IrsRules.efStatus(it, state.meta.household, now)) }
        val q = query.trim().lowercase().replace(Regex("\\s+"), " ")
        val digits = q.filter { it.isDigit() }
        val base = if (q.isEmpty()) yearList else yearList.filter {
            it.r.merchant.lowercase().contains(q) || (digits.length >= 3 && it.r.merchantNif.contains(digits)) || it.r.atcud.lowercase().contains(q)
        }
        RcListModel(
            rows = base.filter { matches(it, filter) }.sortedByDescending { it.r.date + it.r.time },
            counts = listOf("validate", "no_nif", "pending").associateWith { st -> base.count { it.status == st } } + ("all" to base.size),
            irsCounts = RcCategories.IRS.associateWith { k -> base.count { it.r.irsCat == k } },
            catCount = filter.takeIf { it.startsWith("cat-") }?.removePrefix("cat-")?.let { c -> base.count { it.r.spendCat == c } } ?: 0,
            yearCount = yearList.size,
        )
    }

    Column(Modifier.fillMaxSize()) {
        RcTopBar(rt("list.title"), openMenu) {
            RcYearPill(year, years.ifEmpty { listOf(year) }, onYear)
        }
        RcSearchField(query, onQuery, rt("list.search"), Modifier.padding(horizontal = 16.dp))
        val m = model ?: return@Column
        Spacer(Modifier.height(10.dp))
        RcChipRow(Modifier.padding(horizontal = 16.dp)) {
            RcChip(rt("list.f_all"), m.counts["all"], filter == "all") { onFilter("all") }
            listOf("validate", "no_nif", "pending").forEach { st ->
                RcChip(rt("list.f_$st"), m.counts[st], filter == st) { onFilter(if (filter == st) "all" else st) }
            }
            RcCategories.IRS.forEach { k ->
                val n = m.irsCounts[k] ?: 0
                if (n > 0 || filter == "irs-$k") RcChip(irsLabel(k), n, filter == "irs-$k", irsColor(k, p.dark)) { onFilter(if (filter == "irs-$k") "all" else "irs-$k") }
            }
            if (filter.startsWith("cat-")) {
                val c = filter.removePrefix("cat-")
                RcChip(catLongLabel(c), m.catCount, true, spendColor(c, p.dark)) { onFilter("all") }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (m.rows.isEmpty()) {
            Text(
                if (query.isNotBlank() || filter != "all") rt("list.no_match") else rt("list.empty"),
                Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp), color = p.muted, fontSize = 14.sp, textAlign = TextAlign.Center,
            )
            return@Column
        }
        val groups = remember(m.rows) { m.rows.groupBy { it.r.date } }
        val today = LocalDate.now()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            groups.forEach { (date, rows) ->
                item(key = "h-$date") {
                    val d = RcFormat.parseYmd(date)
                    RcSectionLabel(
                        when {
                            d == null -> "—"
                            d == today -> rt("today")
                            else -> "${RcDates.weekday(d)}, ${RcDates.short(d)}"
                        },
                        trailing = RcFormat.money(rows.sumOf { it.r.total }),
                    )
                }
                item(key = "g-$date") {
                    RcCard {
                        rows.forEachIndexed { i, row ->
                            if (i > 0) RcDivider(Modifier.padding(start = 70.dp))
                            ReceiptRow(row) { onOpen(row.r.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RcSearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val p = LocalRc.current
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.card).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = p.muted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            if (value.isEmpty()) Text(placeholder, color = p.soft, fontSize = 15.sp)
            BasicTextField(
                value, onChange,
                singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 15.sp),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) Icon(Icons.Outlined.Close, rt("close"), tint = p.muted, modifier = Modifier.size(20.dp).clickable { onChange("") })
    }
}

/** Linha da lista: avatar · comerciante · hora · categoria · valor · estado. */
@Composable
fun ReceiptRow(row: RcRow, showDate: Boolean = false, onClick: () -> Unit) {
    val p = LocalRc.current
    val r = row.r
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RcAvatar(r.title, r.spendCat)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(r.title.ifBlank { "—" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                if (showDate) RcDates.short(RcFormat.parseYmd(r.date)) else null,
                r.time.ifEmpty { null },
                if (r.locked) null else catLabel(r.spendCat),
            ).joinToString(" · ")
            Text(meta, fontSize = 12.5.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(if (r.locked) "—" else RcFormat.money(r.total), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.text)
            when (row.status) {
                "validate" -> RcPill(rt("list.f_validate"), "warn")
                "no_nif" -> RcPill(rt("list.f_no_nif"), "bad")
                "pending" -> if (!r.locked) RcPill(rt("list.f_pending"), "muted")
                else -> RcTag(irsPt(r.irsCat), irsColor(r.irsCat, p.dark))
            }
        }
    }
}

/** Lixo: restaurar / apagar de vez / esvaziar. */
@Composable
fun ReceiptsTrashPage(vm: ReceiptsViewModel, state: ReceiptsState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val p = LocalRc.current
    var confirmEmpty by remember { mutableStateOf(false) }
    val rows = remember(state.items) { state.trash.sortedByDescending { it.deletedAt }.map { RcRow(it, "pending") } }
    Column(Modifier.fillMaxSize()) {
        RcBackBar(rt("nav.trash"), onBack) {
            if (rows.isNotEmpty()) IconButton(onClick = { confirmEmpty = true }) { Icon(Icons.Outlined.DeleteSweep, rt("empty_trash"), tint = p.bad) }
        }
        Text(rt("list.trash_sub", "days" to state.trashDays), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), fontSize = 13.sp, color = p.muted)
        if (rows.isEmpty()) {
            Text(rt("list.trash_empty"), Modifier.fillMaxWidth().padding(top = 48.dp), color = p.muted, textAlign = TextAlign.Center)
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            item {
                RcCard {
                    rows.forEachIndexed { i, row ->
                        if (i > 0) RcDivider(Modifier.padding(start = 70.dp))
                        ReceiptRow(row, showDate = true) { onOpen(row.r.id) }
                    }
                }
            }
        }
    }
    if (confirmEmpty) {
        ConfirmDialog(
            title = rt("empty_trash_title"),
            message = rtc("empty_trash_text", rows.size),
            confirmText = rt("empty_trash"),
            destructive = true,
            onDismiss = { confirmEmpty = false },
            onConfirm = { confirmEmpty = false; vm.emptyTrash() },
        )
    }
}

