package co.golink.tester.ui.screens.receipts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Agregação fora da thread de UI; mantém o valor anterior enquanto recalcula. */
@Composable
fun <T> rememberComputed(vararg keys: Any?, compute: () -> T): State<T?> =
    produceState<T?>(null, *keys) { value = withContext(Dispatchers.Default) { compute() } }

data class RcBarData(
    val label: String,
    val total: Long,
    val segments: List<Pair<Color, Long>>,
    val short: String,
    val selected: Boolean = false,
    val forecast: Boolean = false,
    val future: Boolean = false,
)

/** Barras empilhadas por categoria (semana / ano) com a média tracejada; toque → [onSelect]. */
@Composable
fun RcStackedBars(bars: List<RcBarData>, avg: Long, avgLabel: String?, height: Dp, onSelect: (Int) -> Unit) {
    val p = LocalRc.current
    val tm = rememberTextMeasurer()
    val maxV = max(bars.maxOfOrNull { it.total } ?: 0L, avg).coerceAtLeast(1L) * 1.18f
    // Semana/ano seguinte tem o mesmo nº de barras: sem isto o toque usava o período antigo.
    val onSelectNow by androidx.compose.runtime.rememberUpdatedState(onSelect)
    Canvas(
        Modifier.fillMaxWidth().height(height).pointerInput(bars.size) {
            detectTapGestures { o ->
                if (bars.isEmpty()) return@detectTapGestures
                val slot = size.width.toFloat() / bars.size
                val i = (o.x / slot).toInt().coerceIn(0, bars.size - 1)
                onSelectNow(i)
            }
        },
    ) {
        val labelH = 20.dp.toPx()
        val chartH = size.height - labelH
        val slot = size.width / bars.size.coerceAtLeast(1)
        val bw = slot * 0.56f
        val small = TextStyle(fontSize = 10.sp, color = p.muted)
        bars.forEachIndexed { i, b ->
            val x = i * slot + (slot - bw) / 2
            val h = (b.total / maxV) * (chartH - 16.dp.toPx())
            if (b.forecast) {
                drawRoundRect(p.accent.copy(alpha = 0.10f), Offset(x, chartH - h), Size(bw, h), CornerRadius(6.dp.toPx()))
                drawRoundRect(p.accent, Offset(x, chartH - h), Size(bw, h), CornerRadius(6.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
            } else {
                var y = chartH
                val total = b.segments.sumOf { it.second }.coerceAtLeast(1)
                b.segments.forEachIndexed { si, (c, v) ->
                    val sh = h * v / total
                    val last = si == b.segments.lastIndex
                    if (last) drawRoundRect(c, Offset(x, y - sh), Size(bw, sh), CornerRadius(5.dp.toPx()))
                    else drawRect(c, Offset(x, y - sh), Size(bw, sh))
                    y -= sh
                }
                if (b.total == 0L && !b.future) drawRect(p.track, Offset(x, chartH - 2.dp.toPx()), Size(bw, 2.dp.toPx()))
            }
            if (b.short.isNotEmpty() && b.total > 0) {
                val t = tm.measure(b.short, small)
                if (t.size.width <= slot) drawText(t, topLeft = Offset(i * slot + (slot - t.size.width) / 2, chartH - h - t.size.height - 2.dp.toPx()))
            }
            val lt = tm.measure(b.label, TextStyle(fontSize = 11.sp, color = if (b.selected) p.text else p.muted, fontWeight = if (b.selected) FontWeight.Bold else FontWeight.Normal))
            drawText(lt, topLeft = Offset(i * slot + (slot - lt.size.width) / 2, chartH + 4.dp.toPx()))
        }
        if (avg > 0) {
            val y = chartH - (avg / maxV) * (chartH - 16.dp.toPx())
            drawLine(p.muted, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            if (avgLabel != null) {
                val t = tm.measure(avgLabel, TextStyle(fontSize = 10.sp, color = p.muted, fontWeight = FontWeight.SemiBold))
                drawRect(p.card, Offset(size.width - t.size.width - 6.dp.toPx(), y - t.size.height - 2.dp.toPx()), Size(t.size.width + 6.dp.toPx(), t.size.height.toFloat()))
                drawText(t, topLeft = Offset(size.width - t.size.width - 3.dp.toPx(), y - t.size.height - 2.dp.toPx()))
            }
        }
    }
}

data class RcPointData(val id: String, val hour: Double, val total: Long, val color: Color, val label: String)

/** Linha do dia (0–24 h): um círculo por recibo, tamanho pelo valor; toque abre o recibo. */
@Composable
fun RcHourTimeline(points: List<RcPointData>, onSelect: (String) -> Unit) {
    val p = LocalRc.current
    val tm = rememberTextMeasurer()
    val maxT = points.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
    Canvas(
        Modifier.fillMaxWidth().height(120.dp).pointerInput(points) {
            detectTapGestures { o ->
                val w = size.width.toFloat()
                points.minByOrNull { abs(it.hour / 24 * w - o.x) }?.takeIf { abs(it.hour / 24 * w - o.x) < 28.dp.toPx() }?.let { onSelect(it.id) }
            }
        },
    ) {
        val base = size.height - 22.dp.toPx()
        drawLine(p.line, Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
        for (h in listOf(0, 6, 12, 18, 24)) {
            val x = (h / 24f) * size.width
            drawLine(p.line, Offset(x, base), Offset(x, base + 4.dp.toPx()), 1.dp.toPx())
            val t = tm.measure("${h}h", TextStyle(fontSize = 10.sp, color = p.muted))
            drawText(t, topLeft = Offset((x - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), base + 6.dp.toPx()))
        }
        val placed = mutableListOf<Pair<Float, Float>>()
        points.sortedByDescending { it.total }.forEach { pt ->
            val r = (6.dp.toPx() + 12.dp.toPx() * sqrt(pt.total.toFloat() / maxT))
            val x = (pt.hour / 24 * size.width).toFloat().coerceIn(r, size.width - r)
            var cy = base - r - 6.dp.toPx()
            // Afasta para cima os que se sobrepõem.
            while (placed.any { (px, py) -> abs(px - x) < r * 1.6f && abs(py - cy) < r * 1.6f } && cy > r) cy -= r
            placed += x to cy
            drawCircle(pt.color.copy(alpha = 0.85f), r, Offset(x, cy))
            val t = tm.measure(pt.label, TextStyle(fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold))
            if (t.size.width < r * 2 - 2) drawText(t, topLeft = Offset(x - t.size.width / 2, cy - t.size.height / 2))
        }
    }
}

data class RcHeatCell(val date: LocalDate, val total: Long, val future: Boolean, val label: String)

/** Calendário do mês: cor pelo gasto do dia (4 níveis por quartis); toque abre o dia. */
@Composable
fun RcMonthHeatmap(cells: List<RcHeatCell>, levels: List<Long>, weekStart: Int, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    val p = LocalRc.current
    if (cells.isEmpty()) return
    val (a, b, c) = (levels + listOf(0L, 0L, 0L)).take(3)
    val first = cells.first().date
    val dow = first.dayOfWeek.value % 7 // 0 = domingo (como getDay)
    val lead = (dow - weekStart + 7) % 7
    val slots: List<RcHeatCell?> = List(lead) { null } + cells
    val weeks = slots.chunked(7)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            (0 until 7).forEach { i ->
                val d = java.time.DayOfWeek.of(((weekStart + i + 6) % 7) + 1)
                Text(RcDates.weekdayLetter(d), Modifier.weight(1f), fontSize = 11.sp, color = p.muted, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        weeks.forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                (0 until 7).forEach { i ->
                    val cell = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (cell != null) {
                            val level = when {
                                cell.total <= 0 -> 0
                                cell.total <= a -> 1
                                cell.total <= b -> 2
                                cell.total <= c -> 3
                                else -> 4
                            }
                            val bg = when (level) {
                                0 -> if (cell.future) Color.Transparent else p.track
                                else -> p.accent.copy(alpha = listOf(0f, 0.2f, 0.42f, 0.68f, 0.95f)[level])
                            }
                            val fg = if (level >= 3) Color.White else p.text
                            Column(
                                Modifier.matchParentSizeCompat().clip(RoundedCornerShape(8.dp)).background(bg)
                                    .then(if (cell.date == today) Modifier.border(1.5.dp, p.text, RoundedCornerShape(8.dp)) else Modifier)
                                    .clickable(enabled = !cell.future) { onSelect(cell.date) }
                                    .padding(3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(cell.date.dayOfMonth.toString(), fontSize = 10.sp, color = if (cell.future) p.soft else fg.copy(alpha = 0.75f), fontWeight = if (cell.date == today) FontWeight.ExtraBold else FontWeight.Medium)
                                Spacer(Modifier.weight(1f))
                                if (cell.label.isNotEmpty() && cell.total > 0) Text(cell.label, fontSize = 9.5.sp, color = fg, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(co.golink.tester.ui.i18n.rt("spending.less"), fontSize = 10.5.sp, color = p.muted)
            listOf(0.2f, 0.42f, 0.68f, 0.95f).forEach { al -> Box(Modifier.padding(horizontal = 2.dp).size(11.dp).clip(RoundedCornerShape(3.dp)).background(p.accent.copy(alpha = al))) }
            Text(co.golink.tester.ui.i18n.rt("spending.more"), fontSize = 10.5.sp, color = p.muted)
        }
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.then(Modifier.fillMaxWidth().aspectRatio(1f))

/** Barra de segmentos (cores por categoria) — "By category". */
@Composable
fun RcSegBar(parts: List<Pair<Color, Long>>, height: Dp = 10.dp) {
    val p = LocalRc.current
    val total = parts.sumOf { it.second }
    Row(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(999.dp)).background(p.track), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (total > 0) parts.filter { it.second > 0 }.forEach { (c, v) -> Box(Modifier.weight(v.toFloat()).height(height).background(c)) }
    }
}

/** Gasto acumulado vs ritmo constante (detalhe do orçamento). */
@Composable
fun RcPaceChart(daily: List<Long>, budget: Long, daysInMonth: Int, today: Int?, color: Color) {
    val p = LocalRc.current
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val maxV = max(budget, daily.sum()).coerceAtLeast(1) * 1.1f
        val w = size.width
        val h = size.height
        fun x(d: Int) = (d.toFloat() / daysInMonth) * w
        fun y(v: Long) = h - (v / maxV) * h
        // Ritmo constante: 0 → orçamento no fim do mês.
        drawLine(p.muted, Offset(0f, h), Offset(w, y(budget)), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        drawLine(p.line, Offset(0f, y(budget)), Offset(w, y(budget)), 1.dp.toPx())
        var acc = 0L
        var prev = Offset(0f, h)
        val until = today ?: daysInMonth
        for (d in 1..until.coerceAtMost(daily.size)) {
            acc += daily[d - 1]
            val pt = Offset(x(d), y(acc))
            drawLine(if (acc > budget) p.bad else color, prev, pt, 2.5.dp.toPx())
            prev = pt
        }
        drawCircle(if (acc > budget) p.bad else color, 4.dp.toPx(), prev)
    }
}
