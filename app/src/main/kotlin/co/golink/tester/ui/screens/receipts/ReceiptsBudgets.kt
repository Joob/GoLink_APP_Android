package co.golink.tester.ui.screens.receipts

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.data.receipts.Budget
import co.golink.tester.data.receipts.Budgets
import co.golink.tester.data.receipts.IrsRules
import co.golink.tester.data.receipts.RcCategories
import co.golink.tester.data.receipts.RcFormat
import co.golink.tester.data.receipts.ReceiptsState
import co.golink.tester.ui.i18n.rt
import co.golink.tester.ui.i18n.rtc
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private fun monthOf(ym: String): Int = ym.substring(5, 7).toInt() - 1
private fun monthLabel(ym: String) = "${RcDates.month(monthOf(ym))} ${ym.take(4)}"
private fun statusTone(st: String) = when (st) { "over" -> "bad"; "close" -> "warn"; else -> "good" }

/** Folha aberta: null fechada; cat null = novo orçamento. */
private data class SheetReq(val cat: String?, val amount: Long? = null)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetsPane(vm: ReceiptsViewModel, state: ReceiptsState, onOpenBudget: (String) -> Unit, openMenu: (() -> Unit)?) {
    val p = LocalRc.current
    val curYm = Budgets.currentYm()
    var ym by rememberSaveable { mutableStateOf(curYm) }
    var sheet by remember { mutableStateOf<SheetReq?>(null) }
    val budgets = state.meta.budgets
    val sum by rememberComputed(state.items, budgets, ym) { Budgets.summary(state.active, budgets, ym) }
    val track by rememberComputed(state.items, budgets, ym.take(4)) { Budgets.trackRecord(state.active, budgets, ym.take(4).toInt()) }
    val s = sum ?: return
    val isCurrent = ym == curYm

    Column(Modifier.fillMaxSize()) {
        RcTopBar(rt("budgets.title"), openMenu) {
            if (s.cards.size < RcCategories.SPEND_KEYS.size) IconButton(onClick = { sheet = SheetReq(null) }) { Icon(Icons.Outlined.Add, rt("budgets.new"), tint = p.text) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { ym = Budgets.shiftYm(ym, -1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, rt("prev"), tint = p.text) }
            val title = if (isCurrent) monthLabel(ym) else rt("app.month_final", "month" to RcDates.month(monthOf(ym))) + if (s.totalSpent <= s.totalBudget && s.cards.isNotEmpty()) " ✓" else ""
            Text(title, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.text, textAlign = TextAlign.Center)
            IconButton(onClick = { ym = Budgets.shiftYm(ym, 1) }, enabled = ym < curYm) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, rt("next"), tint = if (ym < curYm) p.text else p.soft)
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            if (s.cards.isEmpty()) {
                item(key = "empty") {
                    RcCard(padding = 20.dp) {
                        Text(rt("budgets.empty_title"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = p.text)
                        Text(rt("budgets.empty_text"), fontSize = 13.5.sp, color = p.muted)
                        Spacer(Modifier.height(14.dp))
                        RcPrimaryButton(rt("budgets.new"), Icons.Outlined.Add, Modifier.fillMaxWidth()) { sheet = SheetReq(null) }
                    }
                }
            } else {
                // Resumo do mês.
                item(key = "hero") {
                    RcCard(padding = 18.dp) {
                        Text(if (isCurrent) rt("budgets.so_far", "day" to s.prog.day, "total" to s.prog.total) else rt("budgets.spent_in", "month" to monthLabel(ym)), fontSize = 13.sp, color = p.muted)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(RcFormat.money(s.totalSpent, whole = true), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
                            Spacer(Modifier.width(8.dp))
                            Text(rt("budgets.of", "amount" to RcFormat.money(s.totalBudget, whole = true)), fontSize = 15.sp, color = p.muted, modifier = Modifier.padding(bottom = 5.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        val over = s.totalSpent > s.totalBudget
                        RcProgress(if (s.totalBudget > 0) s.totalSpent.toFloat() / s.totalBudget else 0f, if (over) p.bad else p.accent, pace = if (isCurrent) s.prog.day.toFloat() / s.prog.total else null, height = 10.dp)
                        val (text, color) = when {
                            !isCurrent -> {
                                val diff = s.totalBudget - s.totalSpent
                                if (diff >= 0) rt("app.stayed_within", "amount" to RcFormat.money(diff, whole = true)) to p.good
                                else rt("budgets.ended_over", "amount" to RcFormat.money(-diff, whole = true)) to p.bad
                            }
                            s.totalSpent > s.totalBudget -> rt("budgets.over_now", "amount" to RcFormat.money(s.totalSpent - s.totalBudget, whole = true)) to p.bad
                            s.spare >= 0 -> rt("budgets.on_track_spare", "amount" to RcFormat.money(s.spare, whole = true)) to p.good
                            else -> rt("budgets.heading_over", "amount" to RcFormat.money(-s.spare, whole = true)) to p.warn
                        }
                        Text(text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = color)
                        if (isCurrent) {
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Legend(p.accent, rt("budgets.legend_spent"))
                                Legend(p.text, rt("budgets.legend_pace"), bar = true)
                                Text(rt("budgets.legend_projected", "amount" to RcFormat.money(s.projected, whole = true)), fontSize = 11.5.sp, color = p.muted)
                            }
                        }
                    }
                }
                if (isCurrent) item(key = "safe") {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.accentSoft).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rt("budgets.safe"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                            Text(rtc("budgets.safe_sub", s.prog.left, "n" to s.prog.left), fontSize = 12.sp, color = p.muted)
                        }
                        Text(RcFormat.money(s.safePerDay, whole = true), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = p.accent)
                    }
                }
                item(key = "list-h") { RcSectionLabel(if (isCurrent) rt("app.your_budgets", "n" to s.cards.size) else rt("app.how_ended")) }
                item(key = "list") {
                    RcCard {
                        s.cards.forEachIndexed { i, c ->
                            if (i > 0) RcDivider(Modifier.padding(start = 16.dp))
                            BudgetRow(c, isCurrent, s.prog.left) { onOpenBudget(c.cat) }
                        }
                    }
                }
            }
            // Sem orçamento (sugestões).
            val without = s.without.filter { it.avg > 0 }
            if (s.cards.isNotEmpty() && without.isNotEmpty() && isCurrent) {
                item(key = "without-h") { RcSectionLabel(rt("budgets.no_budget_yet")) }
                item(key = "without") {
                    RcCard {
                        without.forEachIndexed { i, w ->
                            if (i > 0) RcDivider(Modifier.padding(start = 16.dp))
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RcDot(spendColor(w.cat, p.dark), 10.dp)
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(catLongLabel(w.cat), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                                    Text(rt("budgets.avg_mo", "amount" to RcFormat.money(w.avg, whole = true)), fontSize = 12.sp, color = p.muted)
                                }
                                Text(
                                    rt("budgets.set", "amount" to RcFormat.money(w.suggest, whole = true)), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.accent,
                                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(p.accentSoft).clickable { sheet = SheetReq(w.cat, w.suggest) }.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
            // Histórico do ano.
            track?.let { t ->
                if (t.total > 0 || s.cards.isNotEmpty()) {
                    item(key = "track") {
                        RcSectionLabel(rt("budgets.track"))
                        RcCard(padding = 16.dp) {
                            Text(if (t.total > 0) rt("budgets.track_text", "n" to t.within, "total" to t.total) else rt("budgets.track_empty"), fontSize = 13.5.sp, color = p.text)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                t.months.forEach { m ->
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        val shape = RoundedCornerShape(5.dp)
                                        val mod = Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
                                        Box(
                                            when (m.state) {
                                                "within" -> mod.background(p.accent.copy(alpha = 0.55f))
                                                "over" -> mod.background(p.bad.copy(alpha = 0.45f))
                                                "current" -> mod.border(1.5.dp, p.accent, shape)
                                                "empty" -> mod.background(p.track)
                                                else -> mod.background(p.track.copy(alpha = 0.4f))
                                            }.clickable(enabled = m.state == "within" || m.state == "over" || m.state == "current") { ym = m.ym },
                                        )
                                        Text(RcDates.monthLetter(monthOf(m.ym)), fontSize = 10.sp, color = if (m.ym == ym) p.text else p.muted, fontWeight = if (m.ym == ym) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (isCurrent) item(key = "alerts") {
                RcSectionLabel(rt("budgets.alerts"))
                AlertsCard(vm, state)
            }
            item(key = "irs") {
                Spacer(Modifier.height(14.dp))
                IrsTip(state, ym.take(4).toInt())
            }
        }
    }

    sheet?.let { req ->
        BudgetSheet(vm, state, req.cat, req.amount, ym) { sheet = null }
    }
}

@Composable
private fun Legend(color: Color, label: String, bar: Boolean = false) {
    val p = LocalRc.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(if (bar) 2.dp else 8.dp, if (bar) 10.dp else 8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.5.sp, color = p.muted)
    }
}

@Composable
private fun BudgetRow(c: Budgets.Card, current: Boolean, daysLeft: Int, onClick: () -> Unit) {
    val p = LocalRc.current
    val color = spendColor(c.cat, p.dark)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RcDot(color, 10.dp)
            Text(catLongLabel(c.cat), Modifier.weight(1f).padding(horizontal = 10.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (current) RcPill(rt("budgets.st_${c.status}"), statusTone(c.status))
            else RcPill(if (c.left >= 0) rt("app.within") else rt("budgets.st_over"), if (c.left >= 0) "good" else "bad")
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(RcFormat.money(c.spent, whole = true), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = p.text)
            Text(" " + rt("budgets.of", "amount" to RcFormat.money(c.budget, whole = true)), fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(bottom = 2.dp))
            if (c.budget != c.base) Text("  " + rt("budgets.incl_rollover", "amount" to RcFormat.money(c.budget - c.base, whole = true)), fontSize = 11.5.sp, color = p.soft, modifier = Modifier.padding(bottom = 2.dp))
        }
        RcProgress(if (c.budget > 0) c.spent.toFloat() / c.budget else 0f, if (c.status == "over") p.bad else color, pace = if (current) c.pace.toFloat() else null)
        val text = when {
            c.left < 0 -> if (current) rt("budgets.over_by", "amount" to RcFormat.money(-c.left, whole = true)) else rt("app.over_budget", "amount" to RcFormat.money(-c.left, whole = true))
            current -> rt("app.left_a_day", "left" to RcFormat.money(c.left, whole = true), "day" to RcFormat.money(c.perDay, whole = true), "n" to daysLeft)
            else -> rt("app.left_month_end", "amount" to RcFormat.money(c.left, whole = true))
        }
        Text(text, fontSize = 12.5.sp, color = if (c.left < 0) p.bad else p.muted, fontWeight = if (c.left < 0) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Avisos (definições do servidor) + esconder valores no ecrã bloqueado (local). */
@Composable
fun AlertsCard(vm: ReceiptsViewModel, state: ReceiptsState) {
    val p = LocalRc.current
    val hide by vm.hideAmounts.collectAsStateWithLifecycle()
    val s = state.settings
    RcCard {
        RcToggleRow(rt("app.warn_at_pct", "pct" to Budgets.DEFAULT_WARN), rt("budgets.alert_close_text"), s.alertClose) { vm.toggleSetting(alertClose = it) }
        RcDivider(Modifier.padding(start = 16.dp))
        RcToggleRow(rt("budgets.alert_over"), rt("budgets.alert_over_text"), s.alertOver) { vm.toggleSetting(alertOver = it) }
        RcDivider(Modifier.padding(start = 16.dp))
        RcToggleRow(rt("budgets.alert_weekly"), rt("budgets.alert_weekly_text"), s.weekly) { vm.toggleSetting(weekly = it) }
        RcDivider(Modifier.padding(start = 16.dp))
        RcToggleRow(rt("app.hide_amounts"), rt("app.hide_amounts_text"), hide) { vm.setHideAmounts(it) }
    }
    Row(Modifier.padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Lock, null, tint = p.soft, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(rt("budgets.alerts_private"), fontSize = 12.sp, color = p.muted)
    }
}

/** Dica: categoria com dedução IRS sem orçamento (= irsTip da web). */
@Composable
private fun IrsTip(state: ReceiptsState, year: Int) {
    val p = LocalRc.current
    val tip by rememberComputed(state.items, state.meta, year) {
        val est = IrsRules.estimate(state.items.filter { !it.isTrashed }, year, state.meta.household)
        val map = mapOf("health" to "saude", "education" to "educacao")
        val pick = map.keys.filter { (state.meta.budgets[it]?.amount ?: 0) == 0L }
            .map { c -> Triple(c, state.active.filter { !it.locked && it.spendCat == c && it.date.startsWith(year.toString()) }.sumOf { it.total }, est.rows.firstOrNull { it.key == map[c] }) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
        if (pick == null) rt("budgets.irs_generic_title") to rt("budgets.irs_generic_text")
        else {
            val cat = rt("cat.${pick.first}")
            rt("budgets.irs_tip_title", "cat" to cat) to rt(
                "budgets.irs_tip_text",
                "amount" to RcFormat.money(pick.second, whole = true),
                "cat" to cat.lowercase(),
                "pct" to ((pick.third?.rate ?: 0.0) * 100).roundToInt(),
                "left" to RcFormat.money(pick.third?.leftToLimit ?: 0, whole = true),
            )
        }
    }
    val (title, text) = tip ?: return
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.card).padding(16.dp)) {
        Icon(Icons.Outlined.Lightbulb, null, tint = p.warn, modifier = Modifier.size(20.dp))
        Column(Modifier.padding(start = 10.dp)) {
            Text(rt("budgets.irs_title").uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = p.muted, letterSpacing = 0.6.sp)
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = p.text)
            Text(text, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp)
        }
    }
}

// ---- Detalhe do orçamento ----------------------------------------------------------------

@Composable
fun BudgetDetailPage(vm: ReceiptsViewModel, state: ReceiptsState, cat: String, onBack: () -> Unit, onOpenReceipt: (String) -> Unit) {
    val p = LocalRc.current
    val ym = Budgets.currentYm()
    var edit by remember { mutableStateOf(false) }
    val sum by rememberComputed(state.items, state.meta.budgets, ym) { Budgets.summary(state.active, state.meta.budgets, ym) }
    val s = sum ?: return
    val c = s.cards.firstOrNull { it.cat == cat }
    val color = spendColor(cat, p.dark)
    val (y, m0) = Budgets.splitYm(ym)
    val days = RcFormat.daysInMonth(y, m0)
    val monthReceipts = remember(state.items, cat, ym) {
        state.active.filter { !it.locked && it.spendCat == cat && it.date.startsWith(ym) }.sortedByDescending { it.date + it.time }
    }
    val daily = remember(monthReceipts, days) {
        val arr = LongArray(days)
        monthReceipts.forEach { r -> r.date.substring(8, 10).toIntOrNull()?.let { d -> if (d in 1..days) arr[d - 1] += r.total } }
        arr.toList()
    }

    Column(Modifier.fillMaxSize()) {
        RcBackBar(catLongLabel(cat), onBack) {
            IconButton(onClick = { edit = true }) { Icon(Icons.Outlined.Edit, rt("edit"), tint = p.text) }
        }
        if (c == null) {
            Column(Modifier.padding(16.dp)) {
                RcCard(padding = 20.dp) {
                    Text(rt("budgets.empty_text"), color = p.muted)
                    Spacer(Modifier.height(12.dp))
                    RcPrimaryButton(rt("budgets.new"), Icons.Outlined.Add, Modifier.fillMaxWidth()) { edit = true }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
                item(key = "ring") {
                    RcCard(padding = 20.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val frac = if (c.budget > 0) c.spent.toFloat() / c.budget else 0f
                            RcRing(frac, if (c.status == "over") p.bad else color, 116.dp) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${(frac * 100).roundToInt()}%", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
                                    Text(rt("budgets.st_${c.status}"), fontSize = 11.sp, color = when (c.status) { "over" -> p.bad; "close" -> p.warn; else -> p.good }, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(Modifier.weight(1f).padding(start = 18.dp)) {
                                Text(RcFormat.money(c.spent, whole = true), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
                                Text(rt("budgets.of", "amount" to RcFormat.money(c.budget, whole = true)), fontSize = 14.sp, color = p.muted)
                                Spacer(Modifier.height(6.dp))
                                // Diferença face a um ritmo constante.
                                val expected = c.budget * c.pace
                                if (expected > 0) {
                                    val diff = ((c.spent - expected) / expected * 100).roundToInt()
                                    Text(
                                        if (diff <= 0) rt("app.under_pace", "pct" to abs(diff)) else rt("app.over_pace", "pct" to diff),
                                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (diff <= 0) p.good else p.warn,
                                    )
                                }
                                Text(
                                    if (c.left < 0) rt("budgets.over_by", "amount" to RcFormat.money(-c.left, whole = true))
                                    else rt("app.left_day", "left" to RcFormat.money(c.left, whole = true), "day" to RcFormat.money(c.perDay, whole = true)),
                                    fontSize = 13.sp, color = if (c.left < 0) p.bad else p.muted,
                                )
                            }
                        }
                    }
                }
                item(key = "pace") {
                    RcSectionLabel(rt("app.day_by_day"), trailing = rt("app.running_vs_pace"))
                    RcCard(padding = 16.dp) {
                        RcPaceChart(daily, c.budget, days, LocalDate.now().dayOfMonth, color)
                    }
                }
                item(key = "hist") {
                    RcSectionLabel(rt("app.last6"), trailing = rt("app.within_n_of", "n" to c.within, "total" to 5))
                    RcCard(padding = 16.dp) {
                        BudgetBars(c.history.map { h -> Triple(RcDates.monthShort(monthOf(h.ym)), h.spent, h.current) }, c.base, color)
                        Text(rt("budgets.within_last5", "n" to c.within, "avg" to RcFormat.money(c.avg, whole = true)), fontSize = 12.5.sp, color = p.muted, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                item(key = "rcpts-h") { RcSectionLabel(rt("app.receipts_in_budget"), trailing = monthReceipts.size.toString()) }
                item(key = "rcpts") {
                    RcCard {
                        if (monthReceipts.isEmpty()) Text(rt("spending.nothing_yet"), Modifier.padding(20.dp), color = p.muted)
                        monthReceipts.forEachIndexed { i, r ->
                            if (i > 0) RcDivider(Modifier.padding(start = 70.dp))
                            ReceiptRow(RcRow(r, IrsRules.efStatus(r, state.meta.household)), showDate = true) { onOpenReceipt(r.id) }
                        }
                    }
                }
            }
        }
    }
    if (edit) BudgetSheet(vm, state, cat, null, ym) { edit = false }
}

/** Barras dos últimos meses com a linha do orçamento (BudgetBars da web). */
@Composable
private fun BudgetBars(bars: List<Triple<String, Long, Boolean>>, budget: Long, color: Color, projectedLast: Boolean = false) {
    val p = LocalRc.current
    val tm = rememberTextMeasurer()
    val maxV = max(bars.maxOfOrNull { it.second } ?: 0L, budget).coerceAtLeast(1) * 1.15f
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val labelH = 18.dp.toPx()
        val h = size.height - labelH
        val slot = size.width / bars.size.coerceAtLeast(1)
        val bw = slot * 0.55f
        bars.forEachIndexed { i, (label, spent, current) ->
            val bh = spent / maxV * (h - 14.dp.toPx())
            val x = i * slot + (slot - bw) / 2
            val over = budget > 0 && spent > budget
            val fill = when {
                current && projectedLast -> color.copy(alpha = 0.35f)
                over -> p.bad.copy(alpha = if (current) 1f else 0.7f)
                else -> color.copy(alpha = if (current) 1f else 0.55f)
            }
            drawRoundRect(fill, Offset(x, h - bh), Size(bw, bh), CornerRadius(5.dp.toPx()))
            if (current && projectedLast) drawRoundRect(color, Offset(x, h - bh), Size(bw, bh), CornerRadius(5.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))))
            val t = tm.measure(label, TextStyle(fontSize = 10.5.sp, color = if (current) p.text else p.muted, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal))
            drawText(t, topLeft = Offset(i * slot + (slot - t.size.width) / 2, h + 3.dp.toPx()))
        }
        if (budget > 0) {
            val y = h - budget / maxV * (h - 14.dp.toPx())
            drawLine(p.text, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        }
    }
}

// ---- Folha: novo / editar --------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BudgetSheet(vm: ReceiptsViewModel, state: ReceiptsState, initialCat: String?, initialAmount: Long?, ym: String, onClose: () -> Unit) {
    val p = LocalRc.current
    val scope = rememberCoroutineScope()
    val budgets = state.meta.budgets
    val receipts = state.active
    val isNew = initialCat == null || (budgets[initialCat]?.amount ?: 0) == 0L
    val choices = RcCategories.SPEND_KEYS.filter { (budgets[it]?.amount ?: 0) == 0L || it == initialCat }
    var cat by remember { mutableStateOf(initialCat ?: choices.firstOrNull()) }
    val start = cat?.let { budgets[it] }
    var amountText by remember { mutableStateOf(((initialAmount ?: start?.amount ?: 0L) / 100).takeIf { it > 0 }?.toString().orEmpty()) }
    var warn by remember { mutableIntStateOf(start?.warn?.takeIf { it > 0 } ?: Budgets.DEFAULT_WARN) }
    var warnOn by remember { mutableStateOf((start?.warn ?: Budgets.DEFAULT_WARN) < 100) }
    var rollover by remember { mutableStateOf(start?.rollover ?: false) }
    var saving by remember { mutableStateOf(false) }
    val amount = RcFormat.parseMoney(amountText) ?: 0L
    val valid = amount in 1..100_000_000L
    val prog = Budgets.monthProgress(ym)

    // Ao trocar de categoria (novo): valor sugerido dos últimos 3 meses.
    fun pick(c: String) {
        cat = c
        val avg = Budgets.averageBefore(receipts, c, ym, 3)
        val sug = Budgets.suggestBudget(avg)
        if (sug > 0) amountText = (sug / 100).toString()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState, containerColor = p.card) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(if (isNew) rt("budgets.new") else rt("budgets.sheet_title", "cat" to catLongLabel(cat)), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = p.text)
            if (isNew) {
                Text(rt("app.new_hint"), fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(top = 2.dp))
                if (choices.isEmpty()) {
                    Text(rt("app.all_budgeted"), Modifier.padding(vertical = 20.dp), color = p.muted)
                    return@Column
                }
                Spacer(Modifier.height(10.dp))
                choices.forEach { c ->
                    val avg = Budgets.averageBefore(receipts, c, ym, 3)
                    val sug = Budgets.suggestBudget(avg)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { pick(c) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(cat == c, { pick(c) }, colors = RadioButtonDefaults.colors(selectedColor = p.accent))
                        RcDot(spendColor(c, p.dark), 10.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(catLongLabel(c), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                            if (avg > 0) Text(rt("app.avg_month", "amount" to RcFormat.money(avg, whole = true)), fontSize = 12.sp, color = p.muted)
                        }
                        if (sug > 0) RcPill(rt("app.suggest", "amount" to RcFormat.money(sug, whole = true)), "good")
                    }
                }
            }
            val c = cat ?: return@Column
            Spacer(Modifier.height(14.dp))
            Text(rt("app.monthly_amount"), fontSize = 13.sp, color = p.muted, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            val step = if (amount >= 100_00) 10L else 5L
            RcStepper(
                value = amountText,
                onMinus = { amountText = max(0L, amount / 100 - step).toString() },
                onPlus = { amountText = (amount / 100 + step).toString() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("€", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = p.muted)
                    BasicTextField(
                        amountText, { v -> amountText = v.filter { it.isDigit() }.take(7) }, singleLine = true,
                        textStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = p.text, textAlign = TextAlign.Center),
                        cursorBrush = SolidColor(p.accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.widthIn(min = 60.dp, max = 160.dp),
                    )
                }
            }
            if (amount > 0) Text(rt("app.per_day_approx", "amount" to RcFormat.money(jsRoundL(amount.toDouble() / RcFormat.daysInMonth(Budgets.splitYm(ym).first, Budgets.splitYm(ym).second)), whole = true)), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 12.5.sp, color = p.muted)

            // Atalhos.
            val avg = Budgets.averageBefore(receipts, c, ym, 3)
            val last = Budgets.spentIn(receipts, c, Budgets.shiftYm(ym, -1))
            val presets = listOf(
                rt("app.preset_avg3", "amount" to RcFormat.money(avg, whole = true)) to avg,
                rt("app.preset_last", "amount" to RcFormat.money(last, whole = true)) to last,
                rt("app.preset_minus") to jsRoundL(avg * 0.9),
            ).filter { it.second > 0 }
            if (presets.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { (label, v) -> RcChip(label, null, amount == (v / 100) * 100) { amountText = (v / 100).toString() } }
                }
            }

            // Últimos 6 meses ("teria cabido").
            val hist = Budgets.history(receipts, c, ym, 6)
            val bars = hist.mapIndexed { i, h ->
                val projected = i == 5 && prog.current
                val spent = if (projected && prog.day != 0) jsRoundL(h.spent.toDouble() / prog.day * prog.total) else h.spent
                Triple(RcDates.monthShort(monthOf(h.ym)) + if (projected) "*" else "", spent, i == 5)
            }
            Spacer(Modifier.height(14.dp))
            BudgetBars(bars, amount, spendColor(c, p.dark), projectedLast = prog.current)
            if (amount > 0) {
                val within = bars.take(5).count { it.second <= amount }
                Text(rt("app.would_fit", "n" to within), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.text, modifier = Modifier.padding(top = 6.dp))
            }
            if (prog.current) Text(rt("budgets.projection", "month" to RcDates.month(monthOf(ym))), fontSize = 11.5.sp, color = p.soft)

            // Aviso e roll over.
            Spacer(Modifier.height(10.dp))
            if (isNew) {
                RcToggleRow(rt("app.warn_at_pct", "pct" to Budgets.DEFAULT_WARN), rt("app.change_later"), warnOn) { warnOn = it }
            } else {
                Text(rt("budgets.warn_at") + " · ${warn}%", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                Slider(
                    value = warn.toFloat(), onValueChange = { warn = (it / 5).roundToInt() * 5 }, valueRange = 50f..100f, steps = 9,
                    colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent),
                )
                Text(rt("app.warn_note"), fontSize = 12.sp, color = p.muted)
                Spacer(Modifier.height(6.dp))
                RcToggleRow(rt("budgets.rollover"), rt("budgets.rollover_text"), rollover) { rollover = it }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isNew) RcSecondaryButton(rt("budgets.remove"), null, Modifier.weight(1f), danger = true, enabled = !saving) {
                    saving = true
                    scope.launch { if (vm.saveBudget(c, null)) onClose(); saving = false }
                }
                RcPrimaryButton(
                    if (isNew) rt("app.create_budget", "cat" to catLongLabel(c)) else rt("budgets.save"), null, Modifier.weight(1.6f), enabled = valid && !saving,
                ) {
                    saving = true
                    val w = if (isNew) (if (warnOn) Budgets.DEFAULT_WARN else 100) else warn
                    scope.launch { if (vm.saveBudget(c, Budget(amount, w, rollover))) onClose(); saving = false }
                }
            }
        }
    }
}

private fun jsRoundL(v: Double): Long = kotlin.math.floor(v + 0.5).roundToLong()

/** Página da gaveta "Alerts". */
@Composable
fun ReceiptsAlertsPage(vm: ReceiptsViewModel, state: ReceiptsState, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        RcBackBar(rt("budgets.alerts"), onBack)
        Column(Modifier.padding(horizontal = 16.dp)) { AlertsCard(vm, state) }
    }
}

