package co.golink.tester.data.receipts

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.min

/** Porte de services/receipts/aggregate.js — agregações do Spending (day|week|month|year). */
object Aggregate {
    const val AVG_MONTH_DAYS = 365.0 / 12

    /** [start, end) */
    data class Range(val start: LocalDate, val end: LocalDate) {
        fun contains(d: LocalDate) = !d.isBefore(start) && d.isBefore(end)
    }

    /** weekStart: 0 = domingo, 1 = segunda (como Date.getDay()). */
    private fun jsDay(d: LocalDate): Int = d.dayOfWeek.value % 7

    fun periodRange(kind: String, anchor: LocalDate, weekStart: Int = 1): Range = when (kind) {
        "day" -> Range(anchor, anchor.plusDays(1))
        "week" -> {
            val off = (jsDay(anchor) - weekStart + 7) % 7
            val start = anchor.minusDays(off.toLong())
            Range(start, start.plusDays(7))
        }
        "month" -> anchor.withDayOfMonth(1).let { Range(it, it.plusMonths(1)) }
        else -> LocalDate.of(anchor.year, 1, 1).let { Range(it, it.plusYears(1)) }
    }

    fun shiftAnchor(kind: String, anchor: LocalDate, step: Int): LocalDate = when (kind) {
        "day" -> anchor.plusDays(step.toLong())
        "week" -> anchor.plusDays(step * 7L)
        "month" -> anchor.withDayOfMonth(1).plusMonths(step.toLong()).withDayOfMonth(min(anchor.dayOfMonth, 28))
        else -> LocalDate.of(anchor.year + step, anchor.monthValue, 1)
    }

    fun inRange(r: Receipt, range: Range): Boolean = RcFormat.parseYmd(r.date)?.let(range::contains) ?: false

    fun active(all: List<Receipt>): List<Receipt> = all.filter { !it.isTrashed && !it.locked }

    /** Dias decorridos do período (até hoje inclusive; passados = todos). */
    fun elapsedDays(range: Range, today: LocalDate = LocalDate.now()): Int {
        if (today.isBefore(range.start)) return 0
        val end = if (!today.isBefore(range.end)) range.end else today.plusDays(1)
        return ChronoUnit.DAYS.between(range.start, end).toInt()
    }

    data class CatRow(val key: String, val spent: Long, val count: Int, val irs: List<String>, val pct: Double, val budget: Long?)
    data class Segment(val key: String, val total: Long)
    data class Place(val key: String, val merchant: String, val cat: String, val total: Long, val visits: Int)

    private fun byCategory(list: List<Receipt>): List<Triple<String, Pair<Long, Int>, LinkedHashSet<String>>> {
        val spent = LinkedHashMap<String, Long>()
        val count = HashMap<String, Int>()
        val irs = HashMap<String, LinkedHashSet<String>>()
        RcCategories.SPEND_KEYS.forEach { spent[it] = 0; count[it] = 0; irs[it] = LinkedHashSet() }
        for (r in list) {
            val k = if (r.spendCat in RcCategories.SPEND_KEYS) r.spendCat else "shopping"
            spent[k] = spent.getValue(k) + r.total
            count[k] = count.getValue(k) + 1
            if (r.irsCat.isNotEmpty() && r.irsCat != "none") irs.getValue(k).add(r.irsCat)
        }
        return spent.keys.map { Triple(it, spent.getValue(it) to count.getValue(it), irs.getValue(it)) }
    }

    fun segmentsOf(list: List<Receipt>): List<Segment> =
        byCategory(list).filter { it.second.first > 0 }.map { Segment(it.first, it.second.first) }

    fun topPlaces(list: List<Receipt>, n: Int = 6): List<Place> {
        val m = LinkedHashMap<String, Place>()
        for (r in list) {
            val k = r.merchantNif.ifEmpty { r.merchant.lowercase() }.ifEmpty { r.id }
            val row = m[k] ?: Place(k, r.merchant.ifEmpty { r.merchantNif }.ifEmpty { "—" }, r.spendCat, 0, 0)
            m[k] = row.copy(total = row.total + r.total, visits = row.visits + 1)
        }
        return m.values.sortedByDescending { it.total }.take(n)
    }

    private fun budgetScale(kind: String, range: Range, today: LocalDate): Double? = when (kind) {
        "month" -> 1.0
        "week" -> 7 / AVG_MONTH_DAYS
        "year" -> {
            val days = elapsedDays(range, today)
            val full = ChronoUnit.DAYS.between(range.start, range.end).toInt()
            if (days >= full) 12.0 else days / AVG_MONTH_DAYS
        }
        else -> null
    }

    data class Point(val id: String, val hour: Double, val total: Long, val cat: String, val merchant: String, val time: String)
    data class Bar(val date: LocalDate?, val month: Int, val total: Long, val segments: List<Segment>, val future: Boolean, val forecast: Boolean, val current: Boolean)
    data class Cell(val date: LocalDate, val total: Long, val future: Boolean)
    data class Irs(val spent: Long, val pct: Double, val raw: Long, val chips: Map<String, Long>, val yearEstimate: Long, val year: Int)

    data class Summary(
        val kind: String,
        val range: Range,
        val list: List<Receipt>,
        val spent: Long,
        val count: Int,
        val prevSpent: Long,
        val delta: Double?,
        val days: Int,
        val dailyAvg: Long,
        val scannedToday: Int,
        val cats: List<CatRow>,
        val biggest: CatRow?,
        val irs: Irs,
        val places: List<Place>,
        val points: List<Point> = emptyList(),
        val bars: List<Bar> = emptyList(),
        val avg: Long = 0,
        val cells: List<Cell> = emptyList(),
        val levels: List<Long> = emptyList(),
        val decForecast: Long? = null,
        val isCurrentYear: Boolean = false,
    )

    private fun localDateOf(iso: String?, zone: ZoneId): LocalDate? =
        iso?.let { runCatching { Instant.parse(it).atZone(zone).toLocalDate() }.getOrNull() }

    fun spendingSummary(
        all: List<Receipt>,
        kind: String,
        anchor: LocalDate,
        household: List<Member>,
        budgets: Map<String, Budget>,
        weekStart: Int = 1,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Summary {
        val receipts = active(all)
        val range = periodRange(kind, anchor, weekStart)
        val list = receipts.filter { inRange(it, range) }.sortedBy { it.date + it.time }
        val prevRange = periodRange(kind, shiftAnchor(kind, anchor, -1), weekStart)
        val spent = list.sumOf { it.total }
        val prevSpent = receipts.filter { inRange(it, prevRange) }.sumOf { it.total }
        val days = elapsedDays(range, today)

        val scale = budgetScale(kind, range, today)
        val ym = Budgets.ymOf(range.start.year, range.start.monthValue - 1)
        val cats = byCategory(list).map { (key, sc, irs) ->
            val b = budgets[key]
            val budget = if (scale != null && b != null && b.amount != 0L) {
                val monthly = if (kind == "month") Budgets.budgetForMonth(receipts, budgets, key, ym) else b.amount
                jsRound(monthly * scale)
            } else null
            CatRow(key, sc.first, sc.second, irs.toList(), if (spent != 0L) sc.first.toDouble() / spent else 0.0, budget)
        }
        val shownCats = cats.filter { it.spent > 0 || (it.budget ?: 0) != 0L }.sortedByDescending { it.spent }
        val biggest = shownCats.firstOrNull()?.takeIf { it.spent > 0 }

        val irsList = list.filter { IrsRules.countsForIrs(it, household, now) }
        val irsSpent = irsList.sumOf { it.total }
        val chips = LinkedHashMap<String, Long>()
        for (r in irsList) chips.merge(r.irsCat, r.total, Long::plus)
        val year = range.start.year
        val rawDed = jsRound(irsList.sumOf { IrsRules.rawDeduction(it, year) })
        val yearEstimate = IrsRules.estimate(receipts, year, household, now = now).total

        val base = Summary(
            kind = kind, range = range, list = list, spent = spent, count = list.size, prevSpent = prevSpent,
            delta = if (prevSpent > 0) (spent - prevSpent).toDouble() / prevSpent else null,
            days = days,
            dailyAvg = if (days != 0) jsRound(spent.toDouble() / days) else 0,
            scannedToday = receipts.count { localDateOf(it.createdAt, zone) == today },
            cats = shownCats, biggest = biggest,
            irs = Irs(irsSpent, if (spent != 0L) irsSpent.toDouble() / spent else 0.0, rawDed, chips, yearEstimate, year),
            places = topPlaces(list),
        )

        return when (kind) {
            "day" -> base.copy(points = list.map { r ->
                val parts = r.time.ifEmpty { "12:00" }.split(':')
                val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                Point(r.id, h + m / 60.0, r.total, r.spendCat, r.merchant, r.time)
            })
            "week" -> {
                val byDate = list.groupBy { it.date }
                val bars = (0 until 7).map { i ->
                    val d = range.start.plusDays(i.toLong())
                    val dl = byDate[RcFormat.ymd(d)].orEmpty()
                    Bar(d, -1, dl.sumOf { it.total }, segmentsOf(dl), d.isAfter(today), forecast = false, current = false)
                }
                val withData = bars.filter { it.total > 0 }
                base.copy(bars = bars, avg = if (withData.isNotEmpty()) jsRound(withData.sumOf { it.total }.toDouble() / withData.size) else 0)
            }
            "month" -> {
                val y = range.start.year
                val m = range.start.monthValue - 1
                val byDate = list.groupBy { it.date }
                val cells = (1..RcFormat.daysInMonth(y, m)).map { day ->
                    val d = LocalDate.of(y, m + 1, day)
                    Cell(d, byDate[RcFormat.ymd(d)].orEmpty().sumOf { it.total }, d.isAfter(today))
                }
                val vals = cells.map { it.total }.filter { it > 0 }.sorted()
                fun q(p: Double): Long = if (vals.isEmpty()) 0 else vals[min(vals.size - 1, kotlin.math.floor(p * vals.size).toInt())]
                base.copy(cells = cells, levels = listOf(q(0.25), q(0.5), q(0.75)))
            }
            else -> {
                val y = range.start.year
                val isCurrent = y == today.year
                val pace = if (isCurrent && days != 0) spent.toDouble() / days else 0.0
                val byMonth = list.groupBy { it.date.substring(5, 7).toIntOrNull() ?: 0 }
                val bars = (0 until 12).map { i ->
                    val ml = byMonth[i + 1].orEmpty()
                    val future = isCurrent && i > today.monthValue - 1
                    Bar(
                        date = null, month = i,
                        total = if (future) jsRound(pace * RcFormat.daysInMonth(y, i)) else ml.sumOf { it.total },
                        segments = if (future) emptyList() else segmentsOf(ml),
                        future = future, forecast = future,
                        current = isCurrent && i == today.monthValue - 1,
                    )
                }
                val real = bars.filter { !it.forecast && it.total > 0 }
                base.copy(
                    bars = bars,
                    avg = if (real.isNotEmpty()) jsRound(real.sumOf { it.total }.toDouble() / real.size) else 0,
                    decForecast = if (isCurrent) jsRound(pace * 31) else null,
                    isCurrentYear = isCurrent,
                )
            }
        }
    }

    /** Anos com recibos (+ o atual), do mais recente para o mais antigo. */
    fun yearsWithReceipts(all: List<Receipt>, today: LocalDate = LocalDate.now()): List<Int> {
        val set = sortedSetOf(today.year)
        for (r in active(all)) r.date.take(4).toIntOrNull()?.takeIf { it > 0 }?.let(set::add)
        return set.toList().reversed()
    }
}
