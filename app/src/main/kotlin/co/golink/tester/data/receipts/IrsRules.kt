package co.golink.tester.data.receipts

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Porte de services/receipts/irsRules.js — deduções à coleta por ano fiscal
 * (cêntimos). Estimativa: o valor real depende do rendimento e da coleta.
 */
object IrsRules {
    data class Rule(val rate: Double, val cap: Long, val perTaxpayer: Boolean = false, val ofVat: Boolean = false)

    private val RULES: Map<Int, Map<String, Rule>> = mapOf(
        2025 to mapOf(
            "gerais" to Rule(0.35, 25000, perTaxpayer = true),
            "saude" to Rule(0.15, 100000),
            "educacao" to Rule(0.3, 80000),
            "rendas" to Rule(0.15, 70000),
            "iva" to Rule(0.15, 25000, ofVat = true),
        ),
        2026 to mapOf(
            "gerais" to Rule(0.35, 25000, perTaxpayer = true),
            "saude" to Rule(0.15, 100000),
            "educacao" to Rule(0.3, 80000),
            "rendas" to Rule(0.15, 90000),
            "iva" to Rule(0.15, 25000, ofVat = true),
        ),
    )

    fun rulesFor(year: Int): Map<String, Rule> {
        val years = RULES.keys.sorted()
        val y = years.lastOrNull { it <= year } ?: years.first()
        return RULES.getValue(y)
    }

    /** Prazo de validação no e-Fatura (o que a web mostra: 28 de fevereiro do ano seguinte). */
    fun validationDeadline(year: Int): LocalDate = LocalDate.of(year + 1, 2, 28)

    /** Até quando o comerciante pode comunicar: dia 12 do mês seguinte, 23:59:59. */
    fun merchantDeadline(date: String?): LocalDateTime? {
        val d = RcFormat.parseYmd(date) ?: return null
        return d.withDayOfMonth(1).plusMonths(1).withDayOfMonth(12).atTime(23, 59, 59)
    }

    private fun digits(nif: String?) = (nif ?: "").filter { it.isDigit() }

    fun householdNifs(household: List<Member>): List<String> = household.map { digits(it.nif) }.filter { it.isNotEmpty() }

    /** A quem pertence o recibo (id do membro) ou null. */
    fun memberOf(r: Receipt, household: List<Member>): String? {
        val nif = digits(r.buyerNif)
        if (nif.isEmpty()) return null
        household.firstOrNull { digits(it.nif) == nif }?.let { return it.id }
        return if (householdNifs(household).isNotEmpty()) null else household.firstOrNull()?.id ?: "me"
    }

    /** no_nif · validate · pending · in (a AT não tem API: é derivado). */
    fun efStatus(r: Receipt, household: List<Member>, now: LocalDateTime = LocalDateTime.now()): String {
        val nif = digits(r.buyerNif)
        val nifs = householdNifs(household)
        if (nif.isEmpty() || (nifs.isNotEmpty() && nif !in nifs)) return "no_nif"
        if (r.multiActivity && !r.validated) return "validate"
        val deadline = merchantDeadline(r.date)
        if (deadline != null && !now.isAfter(deadline) && !r.validated) return "pending"
        return "in"
    }

    fun countsForIrs(r: Receipt, household: List<Member>, now: LocalDateTime = LocalDateTime.now()): Boolean =
        r.irsCat.isNotEmpty() && r.irsCat != "none" && efStatus(r, household, now) != "no_nif"

    /** Dedução "em bruto" de um recibo, antes dos limites anuais. */
    fun rawDeduction(r: Receipt, year: Int): Double {
        val rule = rulesFor(year)[r.irsCat] ?: return 0.0
        return (if (rule.ofVat) r.vat else r.total) * rule.rate
    }

    fun lostBenefit(r: Receipt, year: Int): Long = jsRound(rawDeduction(r, year))

    data class Row(
        val key: String,
        val rate: Double,
        val cap: Long,
        val perTaxpayer: Boolean,
        val ofVat: Boolean,
        val spent: Long,
        val vat: Long,
        val count: Int,
        val raw: Double,
        val deduction: Long,
        val reached: Boolean,
        val leftToLimit: Long,
        val fill: Double,
    )

    data class Estimate(val rows: List<Row>, val total: Long, val rawTotal: Long, val expenses: Long, val count: Int)

    /** Estimativa anual do agregado (ou de um membro). Gerais: limite por sujeito passivo. */
    fun estimate(
        receipts: List<Receipt>,
        year: Int,
        household: List<Member>,
        memberId: String = "all",
        now: LocalDateTime = LocalDateTime.now(),
    ): Estimate {
        val rules = rulesFor(year)
        val taxpayers = household.filter { !it.isDependent }
        val firstTaxpayer = taxpayers.firstOrNull()?.id ?: household.firstOrNull()?.id ?: "me"
        val yearStr = year.toString()

        val counted = receipts.filter { r ->
            if (r.isTrashed || r.locked || !countsForIrs(r, household, now)) return@filter false
            if (r.date.take(4) != yearStr) return@filter false
            memberId == "all" || memberOf(r, household) == memberId
        }

        class Acc { var spent = 0L; var vat = 0L; var count = 0; var raw = 0.0 }
        val acc = RcCategories.IRS.associateWith { Acc() }
        val geraisBy = LinkedHashMap<String, Double>()
        for (r in counted) {
            val a = acc[r.irsCat] ?: continue
            val d = rawDeduction(r, year)
            a.spent += r.total
            a.vat += r.vat
            a.count++
            a.raw += d
            if (r.irsCat == "gerais") {
                val m = memberOf(r, household)
                val who = if (household.firstOrNull { it.id == m }?.isDependent == true) firstTaxpayer else m ?: firstTaxpayer
                geraisBy[who] = (geraisBy[who] ?: 0.0) + d
            }
        }

        val nTaxpayers = if (memberId == "all") max(1, taxpayers.size) else 1
        val rows = RcCategories.IRS.map { key ->
            val rule = rules.getValue(key)
            val a = acc.getValue(key)
            val cap: Long
            val deduction: Long
            if (rule.perTaxpayer) {
                cap = rule.cap * nTaxpayers
                var d = 0.0
                for (v in geraisBy.values) d += min(v, rules.getValue("gerais").cap.toDouble())
                deduction = jsRound(min(d, cap.toDouble()))
            } else {
                cap = rule.cap
                deduction = jsRound(min(a.raw, cap.toDouble()))
            }
            val need = cap - a.raw
            Row(
                key = key, rate = rule.rate, cap = cap, perTaxpayer = rule.perTaxpayer, ofVat = rule.ofVat,
                spent = a.spent, vat = a.vat, count = a.count, raw = a.raw, deduction = deduction,
                reached = a.raw >= cap,
                leftToLimit = if (need > 0) jsRound(need / rule.rate) else 0,
                fill = if (cap != 0L) min(1.0, a.raw / cap) else 0.0,
            )
        }
        return Estimate(
            rows = rows,
            total = rows.sumOf { it.deduction },
            rawTotal = jsRound(rows.sumOf { it.raw }),
            expenses = counted.sumOf { it.total },
            count = counted.size,
        )
    }
}

/** Math.round do JS (meio arredonda para +∞, também nos negativos). */
internal fun jsRound(v: Double): Long = kotlin.math.floor(v + 0.5).roundToLong()
