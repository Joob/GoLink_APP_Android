package co.golink.tester.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.domain.calendar.CalInfo
import co.golink.tester.domain.calendar.CalInstance
import co.golink.tester.domain.calendar.CalendarDates
import co.golink.tester.ui.i18n.tr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Ocorrências por dia local (as de vários dias aparecem em cada um). */
fun instancesByDay(instances: List<CalInstance>): Map<LocalDate, List<CalInstance>> {
    val zone = ZoneId.systemDefault()
    val out = HashMap<LocalDate, MutableList<CalInstance>>()
    instances.forEach { inst ->
        var d = inst.start.atZone(zone).toLocalDate()
        // Fim exclusivo: um evento até às 00:00 não conta no dia seguinte.
        val lastInstant = if (inst.end.isAfter(inst.start)) inst.end.minusMillis(1) else inst.start
        val last = lastInstant.atZone(zone).toLocalDate()
        var guard = 0
        while (!d.isAfter(last) && guard++ < 62) {
            out.getOrPut(d) { mutableListOf() }.add(inst)
            d = d.plusDays(1)
        }
    }
    // Dia inteiro primeiro, depois por hora.
    return out.mapValues { (_, v) -> v.sortedWith(compareByDescending<CalInstance> { it.event.allDay }.thenBy { it.start }) }
}

private fun dayLabel(d: LocalDate): String =
    if (d == LocalDate.now()) "${"Hoje".tr()} · ${fmtLongDay(d)}" else fmtLongDay(d)

// ---- Mês ---------------------------------------------------------------------

@Composable
fun MonthView(
    cursor: LocalDate,
    instances: List<CalInstance>,
    calendars: List<CalInfo>,
    onSelect: (LocalDate) -> Unit,
    onOpen: (CalInstance) -> Unit,
    contentPadding: PaddingValues,
) {
    val byDay = remember(instances) { instancesByDay(instances) }
    val grid = remember(cursor.year, cursor.monthValue) { CalendarDates.monthGrid(cursor) }
    val today = LocalDate.now()
    val dayList = byDay[cursor].orEmpty()

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    weekdayLetters().forEach {
                        Text(
                            it, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                        )
                    }
                }
                grid.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            val colors = byDay[day].orEmpty().map { eventColor(it.event, calendars) }.distinct().take(3)
                            MonthCell(
                                day = day,
                                inMonth = day.monthValue == cursor.monthValue,
                                today = day == today,
                                selected = day == cursor,
                                dots = colors,
                                onClick = { onSelect(day) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 6.dp))
            Text(
                dayLabel(cursor),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
            )
            if (dayList.isEmpty()) {
                Text(
                    "Sem eventos".tr(),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        items(dayList, key = { it.key }) { inst ->
            DayListCard(inst, calendars, onClick = { onOpen(inst) })
        }
    }
}

@Composable
private fun MonthCell(
    day: LocalDate,
    inMonth: Boolean,
    today: Boolean,
    selected: Boolean,
    dots: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(top = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .then(
                    when {
                        today -> Modifier.background(primary)
                        selected -> Modifier.border(1.5.dp, primary, CircleShape)
                        else -> Modifier
                    },
                ),
        ) {
            Text(
                day.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = if (today || selected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    today -> Color.White
                    !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 3.dp)) {
            dots.forEach { c ->
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (inMonth) c else c.copy(alpha = 0.45f)))
            }
        }
    }
}

/** Cartão da lista do dia (vista Mês): barra de cor, horas, título e "Calendário · Local". */
@Composable
private fun DayListCard(inst: CalInstance, calendars: List<CalInfo>, onClick: () -> Unit) {
    val ev = inst.event
    val color = eventColor(ev, calendars)
    val calName = if (ev.holiday) "Feriado".tr() else calendars.firstOrNull { it.id == ev.calendarId }?.name ?: "Convite".tr()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(Modifier.width(3.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.width(48.dp)) {
            if (ev.allDay) {
                Text("Todo o dia".tr(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            } else {
                Text(fmtTime(inst.start), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(fmtTime(inst.end), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                eventTitle(ev), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                listOf(calName, ev.data.location).filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Agenda --------------------------------------------------------------------

@Composable
fun AgendaView(
    cursor: LocalDate,
    days: Long,
    instances: List<CalInstance>,
    calendars: List<CalInfo>,
    loading: Boolean,
    onOpen: (CalInstance) -> Unit,
    onLoadMore: () -> Unit,
    contentPadding: PaddingValues,
) {
    val byDay = remember(instances) { instancesByDay(instances) }
    val list = remember(byDay, cursor, days) {
        byDay.keys.filter { !it.isBefore(cursor) && it.isBefore(cursor.plusDays(days)) }.sorted()
    }
    val state = rememberLazyListState()
    val atEnd by remember { derivedStateOf { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index == state.layoutInfo.totalItemsCount - 1 } }
    LaunchedEffect(atEnd, list.size) { if (atEnd && !loading) onLoadMore() }

    LazyColumn(state = state, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        if (list.isEmpty() && !loading) {
            item {
                Text(
                    "Sem eventos".tr(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        items(list, key = { it.toString() }) { day ->
            AgendaDay(day, byDay[day].orEmpty(), calendars, onOpen)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AgendaDay(day: LocalDate, list: List<CalInstance>, calendars: List<CalInfo>, onOpen: (CalInstance) -> Unit) {
    val today = day == LocalDate.now()
    val primary = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 10.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp).padding(top = 4.dp)) {
            Text(
                fmtWeekdayShort(day), fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                color = if (today) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .then(if (today) Modifier.background(primary) else Modifier),
            ) {
                Text(
                    day.dayOfMonth.toString(), fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = if (today) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            list.forEach { inst ->
                val color = eventColor(inst.event, calendars)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint(color))
                        .clickable { onOpen(inst) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        eventTitle(inst.event), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(fmtSpan(inst.event, inst.start, inst.end), inst.event.data.location).filter { it.isNotBlank() }.joinToString(" · "),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ---- Dia -------------------------------------------------------------------------

private val HOUR: Dp = 52.dp

private data class Block(val inst: CalInstance, val top: Float, val bottom: Float, var col: Int = 0, var cols: Int = 1)

/** Colunas para eventos sobrepostos (clusters → largura repartida), como TimeGridView.vue. */
private fun layout(list: List<CalInstance>, from: Instant, to: Instant): List<Block> {
    val hourMs = 3_600_000f
    val items = list.map { inst ->
        val s = maxOf(inst.start.toEpochMilli(), from.toEpochMilli())
        val e = minOf(maxOf(inst.end.toEpochMilli(), inst.start.toEpochMilli() + 20 * 60_000), to.toEpochMilli())
        Block(inst, (s - from.toEpochMilli()) / hourMs, (e - from.toEpochMilli()) / hourMs)
    }.sortedWith(compareBy<Block> { it.top }.thenByDescending { it.bottom })

    val cluster = mutableListOf<Block>()
    var clusterEnd = -1f
    fun flush() {
        val cols = maxOf(1, cluster.maxOfOrNull { it.col + 1 } ?: 1)
        cluster.forEach { it.cols = cols }
        cluster.clear()
    }
    for (item in items) {
        if (item.top >= clusterEnd) {
            flush()
            clusterEnd = -1f
        }
        val used = cluster.filter { it.bottom > item.top }.map { it.col }.toSet()
        var col = 0
        while (col in used) col++
        item.col = col
        cluster.add(item)
        clusterEnd = maxOf(clusterEnd, item.bottom)
    }
    flush()
    return items
}

@Composable
fun DayView(
    cursor: LocalDate,
    instances: List<CalInstance>,
    calendars: List<CalInfo>,
    onSelect: (LocalDate) -> Unit,
    onOpen: (CalInstance) -> Unit,
    onCreateAt: (Instant) -> Unit,
    contentPadding: PaddingValues,
) {
    val zone = ZoneId.systemDefault()
    val byDay = remember(instances) { instancesByDay(instances) }
    val dayList = byDay[cursor].orEmpty()
    val allDay = dayList.filter { it.event.allDay }
    val timed = dayList.filterNot { it.event.allDay }
    val from = CalendarDates.dayStart(cursor, zone)
    val to = CalendarDates.dayStart(cursor.plusDays(1), zone)
    val blocks = remember(timed, cursor) { layout(timed, from, to) }
    val today = LocalDate.now()
    val primary = MaterialTheme.colorScheme.primary

    Column(Modifier.fillMaxSize()) {
        // Tira da semana.
        val week = CalendarDates.startOfWeek(cursor).let { s -> List(7) { s.plusDays(it.toLong()) } }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            week.forEachIndexed { i, d ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { onSelect(d) }.padding(vertical = 4.dp),
                ) {
                    Text(weekdayLetters()[i], fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .then(
                                when {
                                    d == cursor -> Modifier.background(primary)
                                    d == today -> Modifier.border(1.5.dp, primary, CircleShape)
                                    else -> Modifier
                                },
                            ),
                    ) {
                        Text(
                            d.dayOfMonth.toString(), fontSize = 14.sp,
                            fontWeight = if (d == cursor) FontWeight.Bold else FontWeight.Medium,
                            color = if (d == cursor) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        Text(
            dayLabel(cursor), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        )
        if (allDay.isNotEmpty()) {
            Column(Modifier.padding(start = 56.dp, end = 12.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                allDay.forEach { inst ->
                    val c = eventColor(inst.event, calendars)
                    Text(
                        eventTitle(inst.event), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(tint(c))
                            .clickable { onOpen(inst) }.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        val scroll = rememberScrollState()
        val density = LocalDensity.current
        LaunchedEffect(cursor) {
            // Abre perto da hora atual (hoje) ou às 08:00.
            val hour = if (cursor == today) (java.time.LocalTime.now().hour - 1).coerceAtLeast(0) else 8
            scroll.scrollTo(with(density) { (HOUR * hour).roundToPx() })
        }
        Box(Modifier.fillMaxSize().verticalScroll(scroll).padding(contentPadding)) {
            Column {
                for (h in 0 until 24) {
                    Row(
                        Modifier.fillMaxWidth().height(HOUR).clickable {
                            onCreateAt(CalendarDates.dayStart(cursor, zone).plusSeconds(h * 3600L))
                        },
                    ) {
                        Text(
                            "%02d:00".format(h), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(52.dp).padding(start = 12.dp),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            BoxWithConstraints(Modifier.padding(start = 56.dp, end = 8.dp).fillMaxWidth().height(HOUR * 24)) {
                val width = maxWidth
                blocks.forEach { b ->
                    val c = eventColor(b.inst.event, calendars)
                    val colW = width / b.cols
                    Column(
                        modifier = Modifier
                            .offset(x = colW * b.col, y = HOUR * b.top + 6.dp)
                            .width(colW - 2.dp)
                            .height((HOUR * (b.bottom - b.top) - 2.dp).coerceAtLeast(18.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(tint(c))
                            .clickable { onOpen(b.inst) },
                    ) {
                        Row(Modifier.fillMaxHeight()) {
                            Box(Modifier.width(3.dp).fillMaxHeight().background(c))
                            Column(Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
                                Text(
                                    eventTitle(b.inst.event), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                if (b.bottom - b.top >= 0.75f) {
                                    Text(
                                        "${fmtTime(b.inst.start)} – ${fmtTime(b.inst.end)}", fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
