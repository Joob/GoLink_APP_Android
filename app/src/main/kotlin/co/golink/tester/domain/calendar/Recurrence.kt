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
 * Espelho de src/Domain/Calendar/Support/Recurrence.php e de
 * services/calendarRecurrence.js — os três TÊM de dar as mesmas ocorrências.
 * Contas na hora de parede do fuso do evento ("todos os dias às 09:00" continua
 * às 09:00 depois da mudança de hora); dia inteiro no fuso do aparelho.
 * Mensal/anual sem overflow (31 → último dia, 29/02 → 28/02).
 */
object Recurrence {
    private const val MAX = 2000
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

    private fun firstIndex(repeat: String, start: Instant, fromMs: Long): Long {
        val diff = fromMs - start.toEpochMilli()
        if (diff <= 0) return 0
        val period = when (repeat) {
            "daily" -> DAY_MS
            "weekly" -> 7 * DAY_MS
            "monthly" -> 31 * DAY_MS
            "yearly" -> 366 * DAY_MS
            else -> return 0
        }
        return maxOf(0, diff / period - 1)
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
    ): List<Instant> {
        val duration = maxOf(0L, end.toEpochMilli() - start.toEpochMilli())
        fun overlaps(occ: Instant) =
            occ.toEpochMilli() + duration > from.toEpochMilli() || (duration == 0L && !occ.isBefore(from))

        if (repeat.isBlank() || repeat == "none") {
            return if (start.isBefore(to) && overlaps(start)) listOf(start) else emptyList()
        }

        val until = repeatUntil?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
            ?.let { ZonedDateTime.of(it, LocalTime.of(23, 59, 59), zone).toInstant() }

        val out = ArrayList<Instant>()
        var n = firstIndex(repeat, start, from.toEpochMilli() - duration)
        for (i in 0 until MAX) {
            val occ = nth(repeat, start, n, zone)
            if (!occ.isBefore(to) || (until != null && occ.isAfter(until))) break
            if (overlaps(occ)) out.add(occ)
            n++
        }
        return out
    }

    fun expand(events: List<CalEvent>, from: Instant, to: Instant): List<CalInstance> =
        events.flatMap { ev ->
            val duration = Duration.between(ev.start, ev.end)
            occurrences(ev.start, ev.end, ev.repeat, ev.repeatUntil, ev.zone, from, to).map { s ->
                CalInstance(ev, s, s.plus(duration), "${ev.id}@${s.toEpochMilli()}")
            }
        }.sortedWith(compareBy<CalInstance> { it.start }.thenByDescending { Duration.between(it.start, it.end) })
}
