package co.golink.tester.data.receipts

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Porte de services/receipts/budgets.js — orçamentos mensais por categoria de
 * despesa no meta cifrado: budgets[cat] = {amount (cêntimos), warn (50–100 %), rollover}.
 * Meses em texto 'YYYY-MM' (comparáveis como strings).
 */
object Budgets {
    const val DEFAULT_WARN = 85

    /** m = 0..11. */
    fun ymOf(y: Int, m: Int): String = "$y-${RcFormat.pad(m + 1)}"

    fun splitYm(ym: String): Pair<Int, Int> {
        val (y, m) = ym.split('-').map { it.toInt() }
        return y to (m - 1)
    }

    fun shiftYm(ym: String, step: Int): String {
        val (y, m) = splitYm(ym)
        val d = LocalDate.of(y, m + 1, 1).plusMonths(step.toLong())
        return ymOf(d.year, d.monthValue - 1)
    }

    fun currentYm(today: LocalDate = LocalDate.now()): String = ymOf(today.year, today.monthValue - 1)

    /**
     * Totais por mês e categoria numa só passagem, guardados pela identidade da
     * lista (o repositório cria uma lista nova sempre que os recibos mudam).
     */
    private var cacheKey: List<Receipt>? = null
    private var cacheVal: Map<String, Long> = emptyMap()

    @Synchronized
    private fun totalsOf(receipts: List<Receipt>): Map<String, Long> {
        if (cacheKey === receipts) return cacheVal
        val t = HashMap<String, Long>()
        for (r in receipts) {
            if (r.isTrashed || r.locked) continue
            val ym = r.date.take(7)
            val v = r.total
            t.merge("$ym|*", v, Long::plus)
            t.merge("$ym|${r.spendCat}", v, Long::plus)
        }
        cacheKey = receipts
        cacheVal = t
        return t
    }

    /** Gasto de uma categoria (ou de todas, se `cat` null) num mês. */
    fun spentIn(receipts: List<Receipt>, cat: String?, ym: String): Long = totalsOf(receipts)["$ym|${cat ?: "*"}"] ?: 0

    /** Orçamento efetivo do mês: base + o que sobrou do mês anterior (se "roll over"). */
    fun budgetForMonth(receipts: List<Receipt>, budgets: Map<String, Budget>, cat: String, ym: String): Long {
        val b = budgets[cat] ?: return 0
        if (b.amount == 0L) return 0
        if (!b.rollover) return b.amount
        return b.amount + max(0, b.amount - spentIn(receipts, cat, shiftYm(ym, -1)))
    }

    /** none · over · close · on_track */
    fun statusOf(spent: Long, budget: Long, warn: Int = DEFAULT_WARN): String = when {
        budget == 0L -> "none"
        spent > budget -> "over"
        spent >= budget.toDouble() * warn / 100 -> "close"
        else -> "on_track"
    }

    data class Progress(val day: Int, val total: Int, val left: Int, val past: Boolean = false, val future: Boolean = false, val current: Boolean = false)

    /** Dia do mês "hoje" para o mês pedido (passados = completo, futuros = 0). */
    fun monthProgress(ym: String, today: LocalDate = LocalDate.now()): Progress {
        val (y, m) = splitYm(ym)
        val total = RcFormat.daysInMonth(y, m)
        val cur = currentYm(today)
        if (ym < cur) return Progress(total, total, 0, past = true)
        if (ym > cur) return Progress(0, total, total, future = true)
        val day = today.dayOfMonth
        return Progress(day, total, total - day + 1, current = true)
    }

    data class MonthSpent(val ym: String, val spent: Long)

    /** N meses que terminam em `ym` (inclusive). */
    fun history(receipts: List<Receipt>, cat: String?, ym: String, n: Int = 6): List<MonthSpent> =
        (0 until n).map { i -> shiftYm(ym, i - n + 1).let { MonthSpent(it, spentIn(receipts, cat, it)) } }

    /** Média dos últimos `n` meses completos antes de `ym` (0 se nenhum tem gastos). */
    fun averageBefore(receipts: List<Receipt>, cat: String?, ym: String, n: Int = 3): Long {
        val h = history(receipts, cat, shiftYm(ym, -1), n)
        if (h.none { it.spent > 0 }) return 0
        return jsRound(h.sumOf { it.spent }.toDouble() / n)
    }

    /** Média +10 %, arredondada às dezenas de euro (para cima); €50 sem histórico. */
    fun suggestBudget(avg: Long): Long {
        if (avg == 0L) return 5000
        return ceil(avg * 1.1 / 1000).toLong() * 1000
    }

    data class HistoryBar(val ym: String, val spent: Long, val budget: Long, val current: Boolean)

    data class Card(
        val cat: String,
        val budget: Long,
        val base: Long,
        val warn: Int,
        val rollover: Boolean,
        val spent: Long,
        val left: Long,
        val perDay: Long,
        val pace: Double,
        val status: String,
        val history: List<HistoryBar>,
        val within: Int,
        val avg: Long,
    )

    data class Without(val cat: String, val avg: Long, val suggest: Long)

    data class Summary(
        val prog: Progress,
        val cards: List<Card>,
        val without: List<Without>,
        val totalBudget: Long,
        val totalSpent: Long,
        val projected: Long,
        val spare: Long,
        val safePerDay: Long,
    )

    fun summary(receipts: List<Receipt>, budgets: Map<String, Budget>, ym: String, today: LocalDate = LocalDate.now()): Summary {
        val prog = monthProgress(ym, today)
        val cards = mutableListOf<Card>()
        val without = mutableListOf<Without>()
        for (cat in RcCategories.SPEND_KEYS) {
            val b = budgets[cat]
            val spent = spentIn(receipts, cat, ym)
            if (b == null || b.amount == 0L) {
                val avg = averageBefore(receipts, cat, ym, 3)
                without += Without(cat, avg, suggestBudget(avg))
                continue
            }
            val budget = budgetForMonth(receipts, budgets, cat, ym)
            val warn = if (b.warn != 0) b.warn else DEFAULT_WARN
            val hist = history(receipts, cat, ym, 6)
            val prev5 = hist.take(5)
            cards += Card(
                cat = cat, budget = budget, base = b.amount, warn = warn, rollover = b.rollover, spent = spent,
                left = budget - spent,
                perDay = if (prog.left != 0) max(0, floor((budget - spent).toDouble() / prog.left).toLong()) else 0,
                pace = if (prog.current) prog.day.toDouble() / prog.total else if (prog.past) 1.0 else 0.0,
                status = statusOf(spent, budget, warn),
                history = hist.mapIndexed { i, h -> HistoryBar(h.ym, h.spent, if (i == 5) budget else b.amount, i == 5) },
                within = prev5.count { it.spent <= b.amount },
                avg = jsRound(prev5.sumOf { it.spent }.toDouble() / 5),
            )
        }
        val totalBudget = cards.sumOf { it.budget }
        val totalSpent = cards.sumOf { it.spent }
        val projected = if (prog.current && prog.day != 0) jsRound(totalSpent.toDouble() / prog.day * prog.total) else totalSpent
        return Summary(
            prog = prog,
            cards = cards,
            without = without.sortedByDescending { it.avg },
            totalBudget = totalBudget,
            totalSpent = totalSpent,
            projected = projected,
            spare = totalBudget - projected,
            safePerDay = if (prog.left != 0 && totalBudget != 0L) max(0, floor((totalBudget - totalSpent).toDouble() / prog.left).toLong()) else 0,
        )
    }

    data class TrackMonth(val ym: String, val state: String)
    data class Track(val months: List<TrackMonth>, val within: Int, val total: Int)

    /** Cada mês do ano: within · over · empty · current · future (com os orçamentos atuais). */
    fun trackRecord(receipts: List<Receipt>, budgets: Map<String, Budget>, year: Int, today: LocalDate = LocalDate.now()): Track {
        val cats = RcCategories.SPEND_KEYS.filter { (budgets[it]?.amount ?: 0) != 0L }
        val cur = currentYm(today)
        val months = (0 until 12).map { m ->
            val key = ymOf(year, m)
            when {
                key > cur || cats.isEmpty() -> TrackMonth(key, if (key == cur) "current" else "future")
                key == cur -> TrackMonth(key, "current")
                else -> {
                    val any = cats.any { spentIn(receipts, it, key) > 0 } || spentIn(receipts, null, key) > 0
                    if (!any) TrackMonth(key, "empty")
                    else {
                        val over = cats.any { spentIn(receipts, it, key) > budgetForMonth(receipts, budgets, it, key) }
                        TrackMonth(key, if (over) "over" else "within")
                    }
                }
            }
        }
        val done = months.filter { it.state == "within" || it.state == "over" }
        return Track(months, done.count { it.state == "within" }, done.size)
    }

    data class Alert(val cat: String, val kind: String, val key: String)

    /** Categorias que passaram agora um limite no mês (dedup pelas chaves já avisadas). */
    fun pendingAlerts(
        receipts: List<Receipt>,
        budgets: Map<String, Budget>,
        ym: String,
        alerted: Map<String, Int>,
        settings: ReceiptsSettings,
    ): List<Alert> {
        val out = mutableListOf<Alert>()
        for (cat in RcCategories.SPEND_KEYS) {
            val b = budgets[cat] ?: continue
            if (b.amount == 0L) continue
            val budget = budgetForMonth(receipts, budgets, cat, ym)
            val st = statusOf(spentIn(receipts, cat, ym), budget, if (b.warn != 0) b.warn else DEFAULT_WARN)
            val over = "$ym:$cat:over"
            val close = "$ym:$cat:close"
            if (st == "over" && settings.alertOver && alerted[over] == null) out += Alert(cat, "over", over)
            else if (st == "close" && settings.alertClose && alerted[close] == null && alerted[over] == null) out += Alert(cat, "close", close)
        }
        return out
    }
}
