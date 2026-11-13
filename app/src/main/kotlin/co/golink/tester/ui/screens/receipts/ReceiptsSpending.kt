package co.golink.tester.ui.screens.receipts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.data.receipts.Aggregate
import co.golink.tester.data.receipts.IrsRules
import co.golink.tester.data.receipts.RcCategories
import co.golink.tester.data.receipts.RcFormat
import co.golink.tester.data.receipts.Receipt
import co.golink.tester.data.receipts.ReceiptsState
import co.golink.tester.ui.i18n.rt
import co.golink.tester.ui.i18n.rtc
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

private val KINDS = listOf("day", "week", "month", "year")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpendingPane(
    vm: ReceiptsViewModel,
    state: ReceiptsState,
    kind: String,
    anchor: LocalDate,
    onKind: (String) -> Unit,
    onAnchor: (LocalDate) -> Unit,
    years: List<Int>,
    onOpenReceipt: (String) -> Unit,
    onOpenIrs: (Int) -> Unit,
    onOpenBudget: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
    openMenu: (() -> Unit)?,
) {
    val p = LocalRc.current
    val today = LocalDate.now()
    val weekStart = RcDates.weekStart()
    val s by rememberComputed(state.items, state.meta, kind, anchor, weekStart) {
        Aggregate.spendingSummary(state.items, kind, anchor, state.meta.household, state.meta.budgets, weekStart)
    }
    fun go(k: String, d: LocalDate) { onKind(k); onAnchor(if (d.isAfter(today)) today else d) }
    val canNext = Aggregate.periodRange(kind, Aggregate.shiftAnchor(kind, anchor, 1), weekStart).start <= today

    Column(Modifier.fillMaxSize()) {
        RcTopBar(rt("spending.title"), openMenu) {
            if (years.size > 1) RcYearPill(anchor.year, years) { y -> go("year", LocalDate.of(y, if (y == today.year) today.monthValue else 12, 1)) }
        }
        val sum = s
        if (sum == null) return@Column
        val range = sum.range
        val lastDay = range.end.minusDays(1)
        val periodLabel = when (kind) {
            "day" -> if (range.start == today) "${rt("today")} · ${RcDates.short(range.start)}" else "${RcDates.weekday(range.start)} · ${RcDates.short(range.start)}"
            "week" -> rt("app.range", "start" to RcDates.short(range.start), "end" to RcDates.short(lastDay))
            "month" -> "${RcDates.month(range.start.monthValue - 1)} ${range.start.year}"
            else -> if (range.start.year == today.year) rt("spending.year_so_far", "year" to range.start.year) else range.start.year.toString()
        }
        val periodShort = when (kind) {
            "day" -> if (range.start == today) rt("spending.this_day") else RcDates.short(range.start)
            "week" -> rt("spending.this_week")
            "month" -> RcDates.monthShort(range.start.monthValue - 1)
            else -> range.start.year.toString()
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            item(key = "seg") {
                RcSegmented(KINDS.map { it to rt("spending.$it") }, kind, { k -> go(k, if (anchor.isAfter(today)) today else anchor) })
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { go(kind, Aggregate.shiftAnchor(kind, anchor, -1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, rt("prev"), tint = p.text) }
                    Text(periodLabel, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { go(kind, Aggregate.shiftAnchor(kind, anchor, 1)) }, enabled = canNext) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, rt("next"), tint = if (canNext) p.text else p.soft)
                    }
                }
            }

            // Cartão "Spent" (+ Per day / Receipts / For IRS ou Dec forecast).
            item(key = "spent") {
                RcCard(padding = 18.dp) {
                    Text(rt("spending.spent"), fontSize = 13.sp, color = p.muted, fontWeight = FontWeight.SemiBold)
                    Text(RcFormat.money(sum.spent), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
                    val sub = if (kind == "year") {
                        val end = if (range.start.year == today.year) today else lastDay
                        rt("app.range", "start" to RcDates.short(range.start), "end" to RcDates.short(end))
                    } else sum.delta?.let { d ->
                        val pct = abs((d * 100).roundToInt())
                        "${if (d > 0) "▲" else "▼"} ${rt("spending.vs_last_$kind", "pct" to pct)}"
                    } ?: "—"
                    val subColor = if (kind == "year" || sum.delta == null) p.muted else if (sum.delta > 0) p.warn else p.good
                    Text(sub, fontSize = 13.sp, color = subColor, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(14.dp))
                    RcDivider()
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Kpi(if (kind == "day") rt("spending.receipts") else rt("app.per_day"), if (kind == "day") sum.count.toString() else RcFormat.money(sum.dailyAvg, whole = true), Modifier.weight(1f))
                        if (kind == "year" && sum.isCurrentYear) Kpi(rt("app.dec_forecast"), RcFormat.money(sum.decForecast ?: 0, whole = true), Modifier.weight(1f))
                        else Kpi(rt("spending.receipts"), sum.count.toString(), Modifier.weight(1f))
                        Kpi(rt("app.for_irs"), RcFormat.money(sum.irs.spent, whole = true), Modifier.weight(1f), color = p.accent)
                    }
                }
            }

            // Gráfico do período.
            item(key = "chart") {
                Spacer(Modifier.height(12.dp))
                RcCard(padding = 16.dp) {
                    val title = when (kind) {
                        "day" -> rt("app.hour_by_hour")
                        "week" -> rt("app.each_day")
                        "month" -> rt("app.every_day")
                        else -> rt("app.each_month")
                    }
                    val hint = when (kind) {
                        "day" -> rtc("spending.n_receipts", sum.count) + " · " + RcFormat.money(sum.spent)
                        "week" -> rt("spending.tap_day")
                        "month" -> rt("spending.darker_more")
                        else -> if (sum.isCurrentYear) rt("app.dec_is_forecast") else rt("spending.tap_month")
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.text, modifier = Modifier.weight(1f))
                        Text(hint, fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    when (kind) {
                        "day" -> if (sum.list.isEmpty()) {
                            Text(rt("spending.empty_day"), Modifier.fillMaxWidth().padding(vertical = 28.dp), color = p.muted, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        } else {
                            RcHourTimeline(
                                sum.points.map { RcPointData(it.id, it.hour, it.total, spendColor(it.cat, p.dark), RcFormat.money(it.total, whole = true)) },
                                onOpenReceipt,
                            )
                        }
                        "month" -> RcMonthHeatmap(
                            sum.cells.map { RcHeatCell(it.date, it.total, it.future, if (it.future) "" else if (it.total > 0) RcFormat.money(it.total, whole = true) else "") },
                            sum.levels, weekStart, today,
                        ) { d -> go("day", d) }
                        else -> {
                            val bars = sum.bars.map { b ->
                                val segs = b.segments.map { spendColor(it.key, p.dark) to it.total }
                                if (kind == "week") RcBarData(RcDates.weekday(b.date!!), b.total, segs, RcFormat.money(b.total, whole = true), selected = b.date == today, future = b.future)
                                else RcBarData(RcDates.monthShort(b.month), b.total, segs, if (b.forecast) "" else RcFormat.money(b.total, whole = true), selected = b.current, forecast = b.forecast)
                            }
                            RcStackedBars(bars, sum.avg, rt("spending.avg", "amount" to RcFormat.money(sum.avg, whole = true)), if (kind == "year") 220.dp else 200.dp) { i ->
                                val b = sum.bars[i]
                                if (kind == "week") b.date?.let { if (!b.future) go("day", it) }
                                else if (!b.forecast) go("month", LocalDate.of(range.start.year, b.month + 1, 1))
                            }
                        }
                    }
                }
            }

            // Recibos do dia.
            if (kind == "day" && sum.list.isNotEmpty()) {
                item(key = "daylist") {
                    Spacer(Modifier.height(12.dp))
                    RcCard {
                        sum.list.forEachIndexed { i, r ->
                            if (i > 0) RcDivider(Modifier.padding(start = 16.dp))
                            DayRow(r, state, onOpenReceipt)
                        }
                    }
                }
            }

            // Por categoria.
            item(key = "cats") {
                RcSectionLabel(rt("spending.by_category"), trailing = if (kind == "day") periodShort else rt("spending.budget_scale"))
                RcCard(padding = 16.dp) {
                    if (sum.cats.isEmpty()) {
                        Text(rt("spending.nothing_yet"), Modifier.fillMaxWidth().padding(vertical = 18.dp), color = p.muted, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    } else {
                        RcSegBar(sum.cats.map { spendColor(it.key, p.dark) to it.spent })
                        Spacer(Modifier.height(10.dp))
                        sum.cats.forEach { c -> CatRowView(c, onClick = { if (c.budget != null && kind == "month") onOpenBudget(c.key) else onOpenCategory(c.key) }) }
                    }
                }
            }

            // Onde foi.
            item(key = "places") {
                RcSectionLabel(rt("spending.where"), trailing = rt("spending.top_places", "period" to periodShort))
                RcCard {
                    if (sum.places.isEmpty()) Text(rt("spending.nothing_yet"), Modifier.fillMaxWidth().padding(vertical = 24.dp), color = p.muted, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    sum.places.forEachIndexed { i, pl ->
                        if (i > 0) RcDivider(Modifier.padding(start = 16.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", Modifier.width(18.dp), fontSize = 12.sp, color = p.soft)
                            RcAvatar(pl.merchant, pl.cat, 36.dp)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(pl.merchant, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(rtc("spending.visits", pl.visits), fontSize = 12.sp, color = p.muted)
                            }
                            Text(RcFormat.money(pl.total, whole = true), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.text)
                        }
                    }
                }
            }

            // Conta para o IRS (cartão escuro).
            item(key = "irs") {
                Spacer(Modifier.height(16.dp))
                IrsHero(sum, periodShort) { onOpenIrs(sum.irs.year) }
            }
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, modifier: Modifier, color: Color? = null) {
    val p = LocalRc.current
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = color ?: p.text, maxLines = 1)
    }
}

@Composable
private fun DayRow(r: Receipt, state: ReceiptsState, onOpen: (String) -> Unit) {
    val p = LocalRc.current
    Row(Modifier.fillMaxWidth().clickable { onOpen(r.id) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(r.time.ifEmpty { "—" }, Modifier.width(48.dp), fontSize = 13.sp, color = p.muted)
        RcAvatar(r.title, r.spendCat, 32.dp)
        Text(r.title, Modifier.weight(1f).padding(horizontal = 10.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val st = IrsRules.efStatus(r, state.meta.household)
        if (st == "no_nif") RcPill(rt("status_short.no_nif"), "bad") else RcTag(irsPt(r.irsCat), irsColor(r.irsCat, p.dark))
        Text(RcFormat.money(r.total), Modifier.padding(start = 10.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.text)
    }
}

@Composable
private fun CatRowView(c: Aggregate.CatRow, onClick: () -> Unit) {
    val p = LocalRc.current
    val color = spendColor(c.key, p.dark)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp)) { RcDot(color, 10.dp) }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(catLongLabel(c.key), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (c.irs.isNotEmpty()) RcTag(c.irs.joinToString(" + ") { irsPt(it) }, irsColor(c.irs.first(), p.dark))
            }
            val budget = c.budget
            if (budget != null && budget > 0) {
                val over = c.spent > budget
                RcProgress(c.spent.toFloat() / budget, if (over) p.bad else p.accent, height = 5.dp, modifier = Modifier.widthIn(max = 220.dp))
                Text(
                    if (over) rt("spending.over_budget", "amount" to RcFormat.money(c.spent - budget, whole = true))
                    else rt("spending.left_of", "left" to RcFormat.money(budget - c.spent, whole = true), "budget" to RcFormat.money(budget, whole = true)),
                    fontSize = 12.sp, color = if (over) p.bad else p.muted, fontWeight = if (over) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(RcFormat.money(c.spent, whole = true), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.text)
            Text("${(c.pct * 100).roundToInt()}%", fontSize = 12.sp, color = p.muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IrsHero(sum: Aggregate.Summary, periodShort: String, onOpenIrs: () -> Unit) {
    val p = LocalRc.current
    val white = Color.White
    val muted = Color.White.copy(alpha = 0.72f)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(p.heroFrom, p.heroTo))).padding(18.dp),
    ) {
        Text(rt("spending.irs_card_title", "period" to periodShort), fontSize = 12.5.sp, color = muted, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(RcFormat.money(sum.irs.spent), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = white)
            Spacer(Modifier.width(8.dp))
            Text(rt("spending.of_total", "total" to RcFormat.money(sum.spent), "pct" to (sum.irs.pct * 100).roundToInt()), fontSize = 13.sp, color = muted, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text(
            rt("spending.irs_card_text", "raw" to RcFormat.money(sum.irs.raw), "year" to sum.irs.year) + " " + RcFormat.money(sum.irs.yearEstimate, whole = true) + ".",
            fontSize = 13.sp, color = muted, lineHeight = 18.sp,
        )
        val chips = RcCategories.IRS.filter { (sum.irs.chips[it] ?: 0) > 0 }
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { k ->
                    Text(
                        "${irsPt(k)} ${RcFormat.money(sum.irs.chips[k] ?: 0, whole = true)}",
                        fontSize = 12.sp, color = white, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("${rt("spending.open_irs")} →", fontSize = 14.sp, color = white, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onOpenIrs))
    }
}
