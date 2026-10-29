package co.golink.tester.domain.calendar

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Datas do calendário (espelho de services/calendarDates.js). Semanas a começar
 * à segunda-feira; "dia inteiro" são datas guardadas à meia-noite UTC.
 */
object CalendarDates {
    private const val DAY_MS = 86_400_000L

    fun parseInstant(iso: String): Instant =
        runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrElse { Instant.parse(iso) }

    /** Meia-noite UTC do servidor → data (arredonda ao dia UTC mais próximo, como Recurrence::dayOf). */
    fun allDayFromIso(iso: String): LocalDate =
        parseInstant(iso).plusMillis(DAY_MS / 2).atOffset(ZoneOffset.UTC).toLocalDate()

    fun allDayToIso(date: LocalDate): String = date.atStartOfDay(ZoneOffset.UTC).toInstant().toString()

    fun zoneOf(tz: String?): ZoneId =
        tz?.takeIf { it.isNotBlank() }?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

    fun startOfWeek(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** 42 dias (6 semanas) da grelha mensal. */
    fun monthGrid(d: LocalDate): List<LocalDate> {
        val first = startOfWeek(d.withDayOfMonth(1))
        return List(42) { first.plusDays(it.toLong()) }
    }

    fun dayStart(d: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Instant = d.atStartOfDay(zone).toInstant()
}

/**
 * "A cada N", dias ISO de um semanal (1 = segunda) e "termina após N vezes".
 * Criar com [Recurrence.rule] para vir já normalizada.
 */
data class RepeatRule(val interval: Int = 1, val days: List<Int> = emptyList(), val count: Int? = null)

/**
 * Espelho de src/Domain/Calendar/Support/Recurrence.php e de
 * services/calendarRecurrence.js — os três TÊM de dar as mesmas ocorrências.
 * Contas na hora de parede do fuso do evento ("todos os dias às 09:00" continua
 * às 09:00 depois da mudança de hora); dia inteiro no fuso do aparelho.
 * Mensal/anual sem overflow (31 → último dia, 29/02 → 28/02). O início do
 * evento é sempre a 1.ª ocorrência, mesmo fora dos dias escolhidos.
 */
object Recurrence {
    private const val MAX = 2000
    private const val MAX_INTERVAL = 99
    private const val MAX_COUNT = 999
    private const val DAY_MS = 86_400_000L

    fun nth(repeat: String, start: Instant, n: Long, zone: ZoneId): Instant {
        val wall = start.atZone(zone).toLocalDateTime()
        val target = when (repeat) {
            "daily" -> wall.plusDays(n)
            "weekly" -> wall.plusWeeks(n)
            // plusMonths/plusYears já prendem o dia ao último do mês.
            "monthly" -> wall.plusMonths(n)
            "yearly" -> wall.plusYears(n)
            else -> return start
        }
        return ZonedDateTime.of(target, zone).toInstant()
    }

    fun interval(value: Int?): Int = (value ?: 1).coerceIn(1, MAX_INTERVAL)

    fun count(value: Int?): Int? = value?.takeIf { it > 0 }?.coerceAtMost(MAX_COUNT)

    fun rule(repeat: String, interval: Int?, days: List<Int>?, count: Int?): RepeatRule =
        RepeatRule(interval(interval), days(repeat, days), count(count))

    /** Dias ISO de um semanal, ordenados; vazio = o dia do início. */
    fun days(repeat: String, days: List<Int>?): List<Int> =
        if (repeat != "weekly") emptyList() else days.orEmpty().filter { it in 1..7 }.distinct().sorted()

    /** Ocorrências do período p (0 = o do início), por ordem. */
    fun period(repeat: String, start: Instant, p: Long, interval: Int, days: List<Int>, zone: ZoneId): List<Instant> {
        if (repeat != "weekly" || days.isEmpty()) return listOf(nth(repeat, start, p * interval, zone))

        val wall = start.atZone(zone).toLocalDateTime()
        val dow = wall.dayOfWeek.value
        val monday = wall.minusDays((dow - 1).toLong()).plusWeeks(p * interval)
        val out = ArrayList<Instant>()
        if (p == 0L && dow !in days) out.add(start)
        for (day in days) {
            if (p == 0L && day < dow) continue
            out.add(ZonedDateTime.of(monday.plusDays((day - 1).toLong()), zone).toInstant())
        }
        return out
    }

    /**
     * Ocorrências antes do período p: 1 por período, ou, no semanal com dias, as
     * do 1.º período (início + dias a partir do dele) e depois days.size cada.
     */
    fun countBefore(p: Long, startDow: Int, days: List<Int>): Long {
        if (p == 0L || days.isEmpty()) return p
        val first = (if (startDow in days) 0 else 1) + days.count { it >= startDow }
        return first + (p - 1) * days.size
    }

    private fun periodMs(repeat: String, interval: Int): Long = interval * when (repeat) {
        "daily" -> DAY_MS
        "weekly" -> 7 * DAY_MS
        "monthly" -> 31 * DAY_MS
        "yearly" -> 366 * DAY_MS
        else -> 0L
    }

    private fun firstPeriod(repeat: String, interval: Int, start: Instant, fromMs: Long): Long {
        val diff = fromMs - start.toEpochMilli()
        val len = periodMs(repeat, interval)
        if (diff <= 0 || len == 0L) return 0
        return maxOf(0, diff / len - 1)
    }

    /** Inícios das ocorrências que se sobrepõem a [from, to). */
    fun occurrences(
        start: Instant,
        end: Instant,
        repeat: String,
        repeatUntil: String?,
        zone: ZoneId,
        from: Instant,
        to: Instant,
        rule: RepeatRule = RepeatRule(),
    ): List<Instant> {
        val duration = maxOf(0L, end.toEpochMilli() - start.toEpochMilli())
        fun overlaps(occ: Instant) =
            occ.toEpochMilli() + duration > from.toEpochMilli() || (duration == 0L && !occ.isBefore(from))

        if (repeat.isBlank() || repeat == "none") {
            return if (start.isBefore(to) && overlaps(start)) listOf(start) else emptyList()
        }

        val until = repeatUntil?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
            ?.let { ZonedDateTime.of(it, LocalTime.of(23, 59, 59), zone).toInstant() }
        val interval = interval(rule.interval)
        val days = days(repeat, rule.days)
        val count = count(rule.count)

        val out = ArrayList<Instant>()
        // Salta para perto de `from`; a contagem soma as ocorrências dos períodos saltados.
        var p = firstPeriod(repeat, interval, start, from.toEpochMilli() - duration)
        var seen = countBefore(p, start.atZone(zone).dayOfWeek.value, days)
        for (i in 0 until MAX) {
            for (occ in period(repeat, start, p, interval, days, zone)) {
                if (!occ.isBefore(to) || (until != null && occ.isAfter(until)) || (count != null && seen >= count)) return out
                seen++
                if (overlaps(occ)) out.add(occ)
            }
            p++
        }
        return out
    }

    fun expand(events: List<CalEvent>, from: Instant, to: Instant): List<CalInstance> =
        events.flatMap { ev ->
            val duration = Duration.between(ev.start, ev.end)
            occurrences(ev.start, ev.end, ev.repeat, ev.repeatUntil, ev.zone, from, to, ev.repeatRule).map { s ->
                CalInstance(ev, s, s.plus(duration), "${ev.id}@${s.toEpochMilli()}")
            }
        }.sortedWith(compareBy<CalInstance> { it.start }.thenByDescending { Duration.between(it.start, it.end) })
}
