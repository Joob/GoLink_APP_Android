package co.golink.tester.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.domain.admin.AnalyticsResponse
import co.golink.tester.domain.admin.RankedItem
import co.golink.tester.ui.i18n.tr
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// Mesmas cores da web (Analytics/Overview.vue) para o relatório ler igual nos dois lados.
internal val VisitorsColor = Color(0xFF3B6CFF)
internal val VisitsColor = Color(0xFFF5365C)

private val RANGES = listOf("24h", "7d", "30d", "90d")

@Composable
fun AnalyticsPane(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        RangeSelector(selected = state.range, onSelect = viewModel::setRange)

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
            state.error != null -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    state.error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = viewModel::load, shape = RoundedCornerShape(12.dp)) {
                    Text("Tentar de novo".tr())
                }
            }
            else -> state.data?.let { AnalyticsContent(it) }
        }
    }
}

@Composable
private fun AnalyticsContent(d: AnalyticsResponse) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        OverviewCard(d)

        Spacer(Modifier.height(16.dp))
        BehaviorCard(newVisitors = d.behavior.new, returning = d.behavior.returning)

        RankedCard("Top países".tr(), d.countries, "País".tr(), flags = true)
        RankedCard("Dispositivos".tr(), d.devices, "Dispositivo".tr())
        RankedCard("Browsers", d.browsers, "Browser")
        RankedCard("Sistemas".tr(), d.os, "Sistema".tr())
        RankedCard("Referências".tr(), d.referrers, "Website")
        RankedCard("Eventos".tr(), d.events, "Evento".tr(), barColor = VisitsColor)

        Spacer(Modifier.height(32.dp))
    }
}

// ===========================================================================
// Overview: cartões de métrica + gráfico de tendência
// ===========================================================================

@Composable
private fun OverviewCard(d: AnalyticsResponse) {
    val labels = d.series.labels
    val visitors = d.series.visitors
    val visits = d.series.visits
    // Índice destacado pelo toque no gráfico; -1 = mostra os totais do período.
    var selected by remember(labels) { mutableIntStateOf(-1) }

    AnalyticsCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            MetricCounter(
                label = "Visitantes".tr(),
                value = selected.takeIf { it >= 0 }?.let { visitors.getOrNull(it) } ?: d.cards.visitors,
                change = d.cards.visitorsChange.takeIf { selected < 0 },
                color = VisitorsColor,
                modifier = Modifier.weight(1f),
            )
            MetricCounter(
                label = "Visitas".tr(),
                value = selected.takeIf { it >= 0 }?.let { visits.getOrNull(it) } ?: d.cards.visits,
                change = d.cards.visitsChange.takeIf { selected < 0 },
                color = VisitsColor,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            if (selected >= 0) formatLabel(labels.getOrNull(selected), d.granularity, long = true)
            else "Período: %s".tr().format(d.range ?: "7d"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        if (labels.size >= 2) {
            TrendChart(
                labels = labels.map { formatLabel(it, d.granularity) },
                visitors = visitors,
                visits = visits,
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot("Visitantes".tr(), VisitorsColor)
                LegendDot("Visitas".tr(), VisitsColor)
            }
        } else {
            EmptyHint("Sem dados neste período".tr())
        }

        if (d.cards.botsFiltered > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                "%d bots filtrados".tr().format(d.cards.botsFiltered),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun MetricCounter(
    label: String,
    value: Int,
    change: Double?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (change != null && change != 0.0) {
                Spacer(Modifier.width(6.dp))
                ChangePill(change)
            }
        }
    }
}

@Composable
private fun ChangePill(change: Double) {
    val up = change > 0
    val tint = if (up) Color(0xFF12B76A) else VisitsColor
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = tint.copy(alpha = 0.12f),
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        Text(
            (if (up) "▲ " else "▼ ") + "${abs(change).roundToInt()}%",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
    }
}

/**
 * Gráfico de área (visitantes) + linha (visitas) desenhado à mão, sem
 * dependências extra. Tocar ou arrastar destaca um ponto da série.
 */
@Composable
private fun TrendChart(
    labels: List<String>,
    visitors: List<Int>,
    visits: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = labels.size
    val maxValue = max(1, max(visitors.maxOrNull() ?: 0, visits.maxOrNull() ?: 0))
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(700),
        label = "trend",
    )
    val density = LocalDensity.current
    val axisTextSize = with(density) { 10.sp.toPx() }

    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Canvas(
                modifier = Modifier.fillMaxSize()
                    .pointerInput(count) {
                        detectTapGestures { offset ->
                            onSelect(indexAt(offset.x, size.width.toFloat(), count))
                        }
                    }
                    .pointerInput(count) {
                        detectHorizontalDragGestures(
                            onDragEnd = { onSelect(-1) },
                            onDragCancel = { onSelect(-1) },
                        ) { change, _ ->
                            onSelect(indexAt(change.position.x, size.width.toFloat(), count))
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                val stepX = if (count > 1) w / (count - 1) else w

                // Grelha horizontal + valor máximo como referência.
                repeat(4) { i ->
                    val y = h * i / 3f
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
                    )
                }

                fun pointY(v: Int) = h - (v.toFloat() / maxValue) * h * 0.92f * progress

                val visitorsPath = Path()
                val areaPath = Path()
                visitors.forEachIndexed { i, v ->
                    val x = i * stepX
                    val y = pointY(v)
                    if (i == 0) {
                        visitorsPath.moveTo(x, y)
                        areaPath.moveTo(x, h)
                        areaPath.lineTo(x, y)
                    } else {
                        visitorsPath.lineTo(x, y)
                        areaPath.lineTo(x, y)
                    }
                }
                areaPath.lineTo((count - 1) * stepX, h)
                areaPath.close()

                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        listOf(VisitorsColor.copy(alpha = 0.28f), VisitorsColor.copy(alpha = 0f)),
                    ),
                )
                drawPath(visitorsPath, color = VisitorsColor, style = Stroke(width = 3f))

                val visitsPath = Path()
                visits.forEachIndexed { i, v ->
                    val x = i * stepX
                    val y = pointY(v)
                    if (i == 0) visitsPath.moveTo(x, y) else visitsPath.lineTo(x, y)
                }
                drawPath(
                    path = visitsPath,
                    color = VisitsColor,
                    style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))),
                )

                if (selected in 0 until count) {
                    val x = selected * stepX
                    drawLine(
                        color = axisColor.copy(alpha = 0.4f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1.5f,
                    )
                    listOf(
                        visitors.getOrNull(selected) to VisitorsColor,
                        visits.getOrNull(selected) to VisitsColor,
                    ).forEach { (v, c) ->
                        if (v != null) {
                            drawCircle(Color.White, radius = 7f, center = Offset(x, pointY(v)))
                            drawCircle(c, radius = 5f, center = Offset(x, pointY(v)))
                        }
                    }
                }

                // Máximo do eixo, canto superior esquerdo.
                drawContext.canvas.nativeCanvas.drawText(
                    maxValue.toString(),
                    2f,
                    axisTextSize,
                    android.graphics.Paint().apply {
                        this.color = axisColor.copy(alpha = 0.6f).toArgb()
                        textSize = axisTextSize
                        isAntiAlias = true
                    },
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOfNotNull(
                labels.firstOrNull(),
                labels.getOrNull(count / 2).takeIf { count > 3 },
                labels.lastOrNull().takeIf { count > 1 },
            ).forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun indexAt(x: Float, width: Float, count: Int): Int {
    if (count <= 1 || width <= 0f) return 0
    val step = width / (count - 1)
    return (x / step).roundToInt().coerceIn(0, count - 1)
}

// ===========================================================================
// Comportamento: donut novos vs. recorrentes
// ===========================================================================

@Composable
private fun BehaviorCard(newVisitors: Int, returning: Int) {
    val total = (newVisitors + returning).coerceAtLeast(1)
    val newFraction = newVisitors.toFloat() / total
    val sweep by animateFloatAsState(
        targetValue = newFraction * 360f,
        animationSpec = tween(700),
        label = "donut",
    )
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    AnalyticsCard {
        Text(
            "Novos vs. recorrentes".tr(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 22f)
                    val inset = 12f
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(
                        color = track,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = stroke,
                    )
                    drawArc(
                        color = VisitorsColor,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = stroke,
                    )
                    drawArc(
                        color = VisitsColor,
                        startAngle = -90f + sweep,
                        sweepAngle = 360f - sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = stroke,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(newFraction * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "novos".tr(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column {
                LegendValue("Novos".tr(), newVisitors, VisitorsColor)
                Spacer(Modifier.height(10.dp))
                LegendValue("Recorrentes".tr(), returning, VisitsColor)
            }
        }
    }
}

@Composable
internal fun LegendValue(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ===========================================================================
// Tabelas com barra de proporção (equivalente ao RankedTable da web)
// ===========================================================================

@Composable
private fun RankedCard(
    title: String,
    items: List<RankedItem>,
    columnLabel: String,
    flags: Boolean = false,
    barColor: Color = VisitorsColor,
    max: Int = 8,
) {
    if (items.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val visible = if (expanded) items else items.take(max)
    val top = items.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1

    Spacer(Modifier.height(16.dp))
    AnalyticsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                columnLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.height(10.dp))
        visible.forEach { item ->
            RankedRow(item = item, top = top, flags = flags, barColor = barColor)
        }
        if (items.size > max) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (expanded) "Ver menos".tr() else "Ver mais (%d)".tr().format(items.size - max),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .pointerInput(Unit) { detectTapGestures { expanded = !expanded } }
                    .padding(vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RankedRow(item: RankedItem, top: Int, flags: Boolean, barColor: Color) {
    val fraction by animateFloatAsState(
        targetValue = (item.value.toFloat() / top).coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "bar",
    )
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        // A barra vive por trás do texto, como na web: dá a proporção sem roubar espaço.
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(barColor.copy(alpha = 0.16f)),
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (flags) {
                Text(flagEmoji(item.code), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                item.label ?: "—",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.value.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${item.percentage}%",
                modifier = Modifier.width(52.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

// ===========================================================================
// Peças comuns
// ===========================================================================

@Composable
private fun RangeSelector(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RANGES.forEach { range ->
            val active = range == selected
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.pointerInput(range) { detectTapGestures { onSelect(range) } },
            ) {
                Text(
                    range,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun AnalyticsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** "2026-09-24" -> "24/09"; "2026-09-24 14:00:00" -> "14h". */
private fun formatLabel(raw: String?, granularity: String?, long: Boolean = false): String {
    if (raw.isNullOrBlank()) return "—"
    return if (granularity == "hour") {
        val hour = raw.substringAfter(' ', "").take(2)
        if (long) "${raw.substring(8, 10)}/${raw.substring(5, 7)} · ${hour}h" else "${hour}h"
    } else {
        val day = raw.substring(8, 10)
        val month = raw.substring(5, 7)
        if (long) "$day/$month/${raw.take(4)}" else "$day/$month"
    }
}

/** ISO-3166 alpha-2 -> emoji de bandeira (indicadores regionais). */
private fun flagEmoji(code: String?): String {
    val c = code?.trim()?.uppercase()
    if (c == null || c.length != 2 || !c.all { it in 'A'..'Z' }) return "🌐"
    val base = 0x1F1E6 - 'A'.code
    return String(Character.toChars(base + c[0].code)) + String(Character.toChars(base + c[1].code))
}
