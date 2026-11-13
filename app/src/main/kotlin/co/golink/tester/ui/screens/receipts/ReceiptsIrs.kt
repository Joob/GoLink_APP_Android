package co.golink.tester.ui.screens.receipts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.data.receipts.AtQr
import co.golink.tester.data.receipts.IrsRules
import co.golink.tester.data.receipts.Member
import co.golink.tester.data.receipts.RcFormat
import co.golink.tester.data.receipts.Receipt
import co.golink.tester.data.receipts.ReceiptsState
import co.golink.tester.ui.common.appLocale
import co.golink.tester.ui.i18n.rt
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

private data class EfModel(
    val counts: Map<String, Int>,
    val toValidate: List<Receipt>,
    val wontCount: List<Receipt>,
)

fun memberName(m: Member): String = if (m.id == "me") rt("household.you") else m.name.ifBlank { rt("household.unnamed") }

/** IRS: Overview | e-Fatura check · N (2.º separador, como no mockup). */
@Composable
fun IrsPane(
    vm: ReceiptsViewModel,
    state: ReceiptsState,
    year: Int,
    years: List<Int>,
    onYear: (Int) -> Unit,
    tab: String,
    onTab: (String) -> Unit,
    todo: Int,
    onOpenReceipt: (String) -> Unit,
    onOpenHousehold: () -> Unit,
    onFilter: (String) -> Unit,
    openMenu: (() -> Unit)?,
) {
    Column(Modifier.fillMaxSize()) {
        RcTopBar("IRS $year", openMenu) {
            RcYearPill(year, (years + year).distinct().sortedDescending(), onYear)
        }
        RcSegmented(
            listOf("overview" to rt("app.overview"), "efatura" to rt("app.efatura_tab", "n" to todo)),
            tab, onTab, Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        if (tab == "efatura") EfaturaCheck(vm, state, year, onOpenReceipt)
        else IrsOverview(state, year, todo, onOpenHousehold, onOpenEfatura = { onTab("efatura") }, onFilter = onFilter)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IrsOverview(state: ReceiptsState, year: Int, todo: Int, onOpenHousehold: () -> Unit, onOpenEfatura: () -> Unit, onFilter: (String) -> Unit) {
    val p = LocalRc.current
    val household = state.meta.household
    var memberId by rememberSaveable { mutableStateOf("all") }
    LaunchedEffect(household) { if (memberId != "all" && household.none { it.id == memberId }) memberId = "all" }
    val est by rememberComputed(state.items, household, year, memberId) {
        IrsRules.estimate(state.items.filter { !it.isTrashed }, year, household, memberId)
    }
    val counts by rememberComputed(state.items, household, year) {
        val now = LocalDateTime.now()
        state.active.filter { !it.locked && it.date.startsWith(year.toString()) }.groupingBy { IrsRules.efStatus(it, household, now) }.eachCount()
    }
    val needsNif = household.firstOrNull { it.id == "me" }?.nif.isNullOrEmpty()
    val rules = IrsRules.rulesFor(year)
    val e = est ?: return

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
        // Agregado: todos ou um membro.
        item(key = "members") {
            RcChipRow {
                RcChip(rt("irs_page.household") + if (household.size > 1) " · ${household.size}" else "", null, memberId == "all") { memberId = "all" }
                if (household.size > 1) household.forEach { m -> RcChip(memberName(m), null, memberId == m.id) { memberId = m.id } }
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, p.line, RoundedCornerShape(999.dp)).clickable(onClick = onOpenHousehold).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Groups, null, tint = p.text, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(rt("irs_page.manage"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (needsNif) item(key = "nif") {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp)).background(p.warn.copy(alpha = if (p.dark) 0.15f else 0.1f)).clickable(onClick = onOpenHousehold).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Info, null, tint = p.warn, modifier = Modifier.size(18.dp))
                Text(rt("irs_page.set_nif"), Modifier.weight(1f).padding(horizontal = 8.dp), fontSize = 13.sp, color = p.text)
                Text(rt("irs_page.set_nif_btn"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.warn)
            }
        }
        // Estimativa (cartão escuro).
        item(key = "hero") {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(p.heroFrom, p.heroTo))).padding(20.dp)) {
                val muted = Color.White.copy(alpha = 0.72f)
                Text(rt("app.estimated_title", "year" to year), fontSize = 13.sp, color = muted)
                Text(RcFormat.money(e.total), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text(rt("irs_page.from_expenses", "amount" to RcFormat.money(e.expenses), "count" to e.count), fontSize = 13.sp, color = muted)
                val parts = e.rows.filter { it.deduction > 0 }
                if (parts.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.15f)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        parts.forEach { r -> Box(Modifier.weight(r.deduction.toFloat()).height(10.dp).background(irsColor(r.key, p.dark))) }
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        parts.forEach { r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RcCircle(irsColor(r.key, p.dark), 8.dp)
                                Spacer(Modifier.width(5.dp))
                                Text(irsLabel(r.key), fontSize = 12.sp, color = muted)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(rt("irs_page.disclaimer"), fontSize = 12.sp, color = muted, lineHeight = 16.sp)
            }
        }
        // "13 To validate" + prazo.
        item(key = "todo") {
            Spacer(Modifier.height(12.dp))
            val deadline = DateTimeFormatter.ofPattern("MMM yyyy", appLocale()).format(IrsRules.validationDeadline(year)).replace(".", "")
            if (todo > 0) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.warn.copy(alpha = if (p.dark) 0.16f else 0.1f))
                        .border(1.dp, p.warn.copy(alpha = 0.3f), RoundedCornerShape(18.dp)).clickable(onClick = onOpenEfatura).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(todo.toString(), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = p.warn)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(rt("app.to_validate_banner"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.text)
                        Text(rt("app.by_end", "date" to deadline), fontSize = 12.5.sp, color = p.muted)
                    }
                    Text("${rt("irs_page.show")} →", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.accent)
                }
            } else {
                RcCard(padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CalendarMonth, null, tint = p.warn)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(rt("irs_page.deadline", "date" to deadline), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                            Text(rt("irs_page.deadline_sub"), fontSize = 12.5.sp, color = p.muted)
                        }
                    }
                }
            }
            // Sem NIF / ainda não chegou → lista filtrada.
            val c = counts.orEmpty()
            listOf("no_nif" to rt("irs_page.row_no_nif"), "pending" to rt("irs_page.row_pending")).forEach { (k, title) ->
                val n = c[k] ?: 0
                if (n > 0) {
                    Spacer(Modifier.height(8.dp))
                    RcCard {
                        Row(Modifier.fillMaxWidth().clickable { onFilter(k) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(n.toString(), Modifier.width(36.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (k == "no_nif") p.bad else p.muted)
                            Column(Modifier.weight(1f)) {
                                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                                Text(rt("irs_page.row_${k}_text"), fontSize = 12.sp, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text("${if (k == "no_nif") rt("irs_page.see") else rt("irs_page.track")} →", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.accent)
                        }
                    }
                }
            }
        }
        // Por categoria IRS.
        item(key = "rows") {
            RcSectionLabel(rt("app.by_irs_category"))
            RcCard {
                e.rows.forEachIndexed { i, r ->
                    if (i > 0) RcDivider(Modifier.padding(start = 16.dp))
                    val color = irsColor(r.key, p.dark)
                    val pct = (r.rate * 100).roundToInt()
                    val cap = RcFormat.money(if (r.perTaxpayer) rules[r.key]?.cap ?: r.cap else r.cap, whole = true)
                    val spent = if (r.key == "iva") RcFormat.money(r.vat, whole = true) else RcFormat.money(r.spent, whole = true)
                    val line = when {
                        r.ofVat -> rt("app.rule_line_vat", "pct" to pct, "cap" to cap, "spent" to spent)
                        r.perTaxpayer -> rt("app.rule_line_each", "pct" to pct, "cap" to cap, "spent" to spent)
                        else -> rt("app.rule_line", "pct" to pct, "cap" to cap, "spent" to spent)
                    }
                    Column(Modifier.fillMaxWidth().clickable { onFilter("irs-${r.key}") }.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RcDot(color, 10.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text("${irsLabel(r.key)} · ${irsPt(r.key)}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(line, fontSize = 12.sp, color = p.muted)
                            }
                            Text(RcFormat.money(r.deduction), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.text)
                        }
                        Spacer(Modifier.height(8.dp))
                        RcProgress(r.fill.toFloat(), color, height = 7.dp)
                        Text(
                            if (r.reached) rt("app.limit_reached") else rt("app.left", "amount" to RcFormat.money(r.leftToLimit)),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (r.reached) p.warn else p.muted,
                        )
                    }
                }
            }
            val e2 = { c: Long -> RcFormat.money(c, whole = true) }
            Text(
                rt(
                    "irs_page.footnote", "year" to year,
                    "gerais" to e2(rules["gerais"]?.cap ?: 0), "saude" to e2(rules["saude"]?.cap ?: 0), "educacao" to e2(rules["educacao"]?.cap ?: 0),
                    "rendas" to e2(rules["rendas"]?.cap ?: 0), "iva" to e2(rules["iva"]?.cap ?: 0),
                ),
                Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp), fontSize = 11.5.sp, color = p.soft, lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun EfaturaCheck(vm: ReceiptsViewModel, state: ReceiptsState, year: Int, onOpenReceipt: (String) -> Unit) {
    val p = LocalRc.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val household = state.meta.household
    // Marcados nesta visita: ficam (riscados) até sair, como na web.
    val ticked = remember { mutableStateMapOf<String, Boolean>() }
    val model by rememberComputed(state.items, household, year, ticked.toMap()) {
        val now = LocalDateTime.now()
        val sorted = state.active.filter { !it.locked && it.date.startsWith(year.toString()) }.sortedByDescending { it.date }
        val statuses = sorted.associate { it.id to IrsRules.efStatus(it, household, now) }
        EfModel(
            counts = statuses.values.groupingBy { it }.eachCount(),
            toValidate = sorted.filter { r ->
                r.multiActivity && (!r.validated || ticked[r.id] == true) &&
                    IrsRules.efStatus(r.copy(payload = JsonObjectWith(r.payload, "validated", false)), household, now) == "validate"
            },
            wontCount = sorted.filter { statuses[it.id] == "no_nif" },
        )
    }
    val m = model ?: return
    fun copy(nif: String) {
        clipboard.setText(AnnotatedString(nif))
        vm.toast(rt("efatura.copied"))
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
        item(key = "kpis") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val c = m.counts
                EfKpi(((c["in"] ?: 0) + (c["pending"] ?: 0)).toString(), rt("efatura.k_in"), p.good, Modifier.weight(1f))
                EfKpi((c["validate"] ?: 0).toString(), rt("efatura.k_validate"), Color(0xFFF59E0B), Modifier.weight(1f))
                EfKpi((c["no_nif"] ?: 0).toString(), rt("efatura.k_no_nif"), p.bad, Modifier.weight(1f))
            }
        }
        item(key = "todo-h") { RcSectionLabel(rt("efatura.to_validate_h", "n" to m.toValidate.count { !it.validated })) }
        item(key = "todo") {
            RcCard {
                if (m.toValidate.isEmpty()) Text(rt("efatura.none_to_validate"), Modifier.fillMaxWidth().padding(20.dp), color = p.muted, fontSize = 14.sp)
                m.toValidate.forEachIndexed { i, r ->
                    if (i > 0) RcDivider(Modifier.padding(start = 56.dp))
                    Row(Modifier.fillMaxWidth().clickable { onOpenReceipt(r.id) }.padding(start = 6.dp, end = 14.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
                        IconButton(onClick = {
                            ticked[r.id] = true
                            vm.patch(r.id, "validated" to JsonPrimitive(!r.validated))
                        }) {
                            Icon(if (r.validated) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, rt("efatura.tick"), tint = if (r.validated) p.accent else p.muted)
                        }
                        Column(Modifier.weight(1f).padding(top = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    r.title, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (r.validated) p.muted else p.text,
                                    style = if (r.validated) TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else TextStyle.Default,
                                )
                                Text(RcFormat.money(r.total), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.text)
                            }
                            Text("${RcDates.short(RcFormat.parseYmd(r.date))} · ${reason(r)}", fontSize = 12.sp, color = p.muted)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RcTag(rt("app.pick_cat", "cat" to portalCategory(r)), irsColor(r.irsCat, p.dark))
                                Row(
                                    Modifier.clip(RoundedCornerShape(6.dp)).clickable { copy(r.merchantNif) }.padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("NIF ${RcFormat.spacedNif(r.merchantNif)}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = p.text)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Outlined.ContentCopy, null, tint = p.accent, modifier = Modifier.size(13.dp))
                                    Text(rt("app.copy"), fontSize = 12.sp, color = p.accent, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "open") {
            Spacer(Modifier.height(14.dp))
            RcPrimaryButton(rt("app.open_browser"), Icons.Outlined.OpenInNew, Modifier.fillMaxWidth()) { openEfatura(context, vm) }
            Row(Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Lock, null, tint = p.soft, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                Spacer(Modifier.width(6.dp))
                Text(rt("app.cant_sign_in"), fontSize = 12.sp, color = p.muted, lineHeight = 16.sp)
            }
        }
        if (m.wontCount.isNotEmpty()) {
            item(key = "wont-h") { RcSectionLabel(rt("efatura.wont_count_h", "n" to m.wontCount.size)) }
            item(key = "wont") {
                RcCard {
                    m.wontCount.forEachIndexed { i, r ->
                        if (i > 0) RcDivider(Modifier.padding(start = 16.dp))
                        Row(Modifier.fillMaxWidth().clickable { onOpenReceipt(r.id) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(noNifText(r, year), fontSize = 12.sp, color = p.bad)
                            }
                            Text(RcFormat.money(r.total), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.text)
                        }
                    }
                }
                Text(rt("efatura.ask_next_time"), Modifier.padding(top = 8.dp, start = 4.dp), fontSize = 12.sp, color = p.muted)
            }
        }
        item(key = "how") {
            RcSectionLabel(rt("efatura.how"))
            RcCard(padding = 16.dp) {
                (1..4).forEach { n ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("$n", Modifier.width(22.dp), fontWeight = FontWeight.Bold, color = p.accent)
                        Text(rt("efatura.how_$n"), fontSize = 13.5.sp, color = p.text)
                    }
                }
            }
        }
    }
}

/** Cópia do payload com um campo trocado (para perguntar o estado "sem o validado"). */
private fun JsonObjectWith(o: kotlinx.serialization.json.JsonObject, key: String, value: Boolean) =
    kotlinx.serialization.json.JsonObject(o + (key to JsonPrimitive(value)))

private fun portalCategory(r: Receipt): String = rt(
    "portal." + when {
        r.irsCat == "iva" && r.spendCat == "transport" -> "car_repair"
        r.irsCat == "iva" && r.spendCat == "restaurants" -> "restaurants"
        else -> r.irsCat.ifEmpty { "none" }
    },
)

private fun reason(r: Receipt): String = rt("efatura.reason_${if (r.irsCat in setOf("saude", "educacao", "iva")) r.irsCat else "other"}")

private fun noNifText(r: Receipt, year: Int): String {
    val lost = IrsRules.lostBenefit(r, year)
    val base = if (r.buyerNif.isNotEmpty()) rt("efatura.other_nif") else rt("efatura.no_nif_on")
    if (lost <= 0) return base
    return "$base · ${rt(if (r.irsCat == "iva") "efatura.lost_vat" else "efatura.lost_ded", "amount" to RcFormat.money(lost))}"
}

@Composable
private fun EfKpi(value: String, label: String, color: Color, modifier: Modifier) {
    val p = LocalRc.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(p.card).padding(14.dp)) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 12.sp, color = p.muted, maxLines = 2, lineHeight = 15.sp)
    }
}

/** Agregado familiar (nome + NIF + sujeito passivo/dependente), cifrado no meta. */
@Composable
fun HouseholdPage(vm: ReceiptsViewModel, state: ReceiptsState, onBack: () -> Unit) {
    val p = LocalRc.current
    val scope = rememberCoroutineScope()
    val members = remember { mutableStateListOf<Member>().apply { addAll(state.meta.household) } }
    var saving by remember { mutableStateOf(false) }
    val allValid = members.all { it.nif.isEmpty() || AtQr.validNif(it.nif) }

    Column(Modifier.fillMaxSize()) {
        RcBackBar(rt("household.title"), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(rt("household.text"), fontSize = 13.5.sp, color = p.muted, modifier = Modifier.padding(4.dp))
            Spacer(Modifier.height(8.dp))
            members.forEachIndexed { i, m ->
                RcCard(Modifier.padding(bottom = 10.dp), padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (m.id == "me") rt("household.you") else rt("household.member", "n" to i + 1), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.muted, modifier = Modifier.weight(1f))
                        if (m.id != "me") Text(rt("household.remove"), fontSize = 13.sp, color = p.bad, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { members.removeAt(i) })
                    }
                    Spacer(Modifier.height(8.dp))
                    PlainField(m.name, if (m.id == "me") rt("household.your_name") else rt("household.name")) { v -> members[i] = m.copy(name = v.take(40)) }
                    Spacer(Modifier.height(8.dp))
                    PlainField(m.nif, rt("field.nif"), mono = true, keyboard = KeyboardType.Number) { v -> members[i] = m.copy(nif = v.filter { it.isDigit() }.take(9)) }
                    if (m.nif.isNotEmpty() && !AtQr.validNif(m.nif)) Text(rt("scan.err_nif"), fontSize = 12.sp, color = p.bad, modifier = Modifier.padding(top = 4.dp))
                    if (m.id != "me") {
                        Spacer(Modifier.height(8.dp))
                        RcSegmented(listOf("taxpayer" to rt("household.taxpayer"), "dependent" to rt("household.dependent")), m.role, { r -> members[i] = m.copy(role = r) })
                    }
                }
            }
            if (members.size < 8) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, p.line, RoundedCornerShape(14.dp))
                        .clickable { members.add(Member(UUID.randomUUID().toString().take(8), "", "", "taxpayer")) }.padding(14.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Add, null, tint = p.accent)
                    Spacer(Modifier.width(6.dp))
                    Text(rt("household.add"), color = p.accent, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.padding(vertical = 14.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, tint = p.soft, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(rt("household.private"), fontSize = 12.sp, color = p.muted)
            }
        }
        Row(Modifier.fillMaxWidth().background(p.card).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RcSecondaryButton(rt("cancel"), null, Modifier.weight(1f)) { onBack() }
            RcPrimaryButton(rt("save"), null, Modifier.weight(1.4f), enabled = !saving && allValid) {
                saving = true
                val clean = members.map { it.copy(name = it.name.trim(), nif = it.nif.filter { c -> c.isDigit() }) }
                scope.launch {
                    if (vm.saveHousehold(clean)) onBack()
                    saving = false
                }
            }
        }
    }
}

@Composable
fun PlainField(value: String, placeholder: String, mono: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    val p = LocalRc.current
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.bg).padding(horizontal = 12.dp, vertical = 11.dp)) {
        if (value.isEmpty()) Text(placeholder, color = p.soft, fontSize = 15.sp)
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(color = p.text, fontSize = 15.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default),
            cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
