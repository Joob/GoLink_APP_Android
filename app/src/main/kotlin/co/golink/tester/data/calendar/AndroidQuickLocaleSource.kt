package co.golink.tester.data.calendar

import android.icu.text.DateFormat
import android.icu.text.MeasureFormat
import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.SimpleDateFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.ULocale
import co.golink.tester.domain.calendar.QuickLocaleRaw
import co.golink.tester.domain.calendar.QuickLocaleSource
import java.text.DateFormatSymbols
import java.time.LocalDate
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Dados CLDR do sistema (java.time + ICU) para o [QuickEventParser] — o mesmo
 * que a web tira do Intl. Nada sai do aparelho.
 */
object AndroidQuickLocaleSource : QuickLocaleSource {

    override fun load(locale: String): QuickLocaleRaw {
        val loc = Locale.forLanguageTag(locale)
        val uloc = ULocale.forLocale(loc)
        val months = mutableListOf<Pair<String, Int>>()
        for (pattern in listOf("LLLL", "LLL", "MMMM", "MMM")) {
            val f = runCatching { DateTimeFormatter.ofPattern(pattern, loc) }.getOrNull() ?: continue
            for (m in 1..12) months += f.format(LocalDate.of(2027, m, 2)) to m
        }
        val weekdays = mutableListOf<Pair<String, Int>>()
        val weekdaysShort = mutableListOf<Pair<String, Int>>()
        val long = DateTimeFormatter.ofPattern("EEEE", loc)
        val short = DateTimeFormatter.ofPattern("EEE", loc)
        for (i in 0..6) {
            val d = LocalDate.of(2024, 1, 1).plusDays(i.toLong()) // segunda
            weekdays += long.format(d) to i + 1
            weekdaysShort += short.format(d) to i + 1
        }
        val relative = runCatching {
            val rf = RelativeDateTimeFormatter.getInstance(uloc)
            listOf(
                RelativeDateTimeFormatter.Direction.LAST to -1,
                RelativeDateTimeFormatter.Direction.THIS to 0,
                RelativeDateTimeFormatter.Direction.NEXT to 1,
                RelativeDateTimeFormatter.Direction.NEXT_2 to 2,
            ).mapNotNull { (dir, n) ->
                runCatching { rf.format(dir, RelativeDateTimeFormatter.AbsoluteUnit.DAY) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { it to n }
            }
        }.getOrElse { emptyList() }
        val ampm = runCatching { DateFormatSymbols.getInstance(loc).amPmStrings }.getOrNull()
        return QuickLocaleRaw(
            months = months,
            weekdays = weekdays,
            weekdaysShort = weekdaysShort,
            relative = relative,
            am = listOfNotNull(ampm?.getOrNull(0)),
            pm = listOfNotNull(ampm?.getOrNull(1)),
            hourSuffix = listOfNotNull(literalAfter(skeleton("H", uloc), 'H')),
            dayAffix = listOfNotNull(literalAfter(skeleton("MMMMd", uloc), 'd')),
            yearAffix = listOfNotNull(literalAfter(skeleton("yMMMMd", uloc), 'y')),
            minuteUnits = units(uloc, MeasureUnit.MINUTE),
            hourUnits = units(uloc, MeasureUnit.HOUR),
        )
    }

    /** Ordem das datas numéricas (dia antes do mês?) na língua do aparelho. */
    fun dayFirst(locale: Locale = Locale.getDefault()): Boolean = runCatching {
        val p = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale)
        p.indexOf('d') < p.indexOf('M')
    }.getOrDefault(true)

    private fun skeleton(skel: String, uloc: ULocale): String? =
        runCatching { (DateFormat.getInstanceForSkeleton(skel, uloc) as? SimpleDateFormat)?.toPattern() }.getOrNull()

    /** Texto literal logo a seguir ao campo [letter] num padrão ICU ("H 'Uhr'" → " Uhr", "M月d日" → "日"). */
    private fun literalAfter(pattern: String?, letter: Char): String? {
        if (pattern == null) return null
        var i = pattern.indexOf(letter)
        if (i < 0) return null
        while (i < pattern.length && pattern[i] == letter) i++
        val out = StringBuilder()
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '\'') {
                val end = pattern.indexOf('\'', i + 1)
                if (end < 0) break
                out.append(if (end == i + 1) "'" else pattern.substring(i + 1, end))
                i = end + 1
            } else if (c.isLetter() && c.code < 128) {
                break
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString().takeIf { it.isNotBlank() }
    }

    /** Nome da unidade sem o número ("30 minutos" → "minutos"), formas longa e curta. */
    private fun units(uloc: ULocale, unit: MeasureUnit): List<String> = runCatching {
        listOf(MeasureFormat.FormatWidth.WIDE, MeasureFormat.FormatWidth.SHORT).flatMap { width ->
            val mf = MeasureFormat.getInstance(uloc, width)
            listOf(1, 2, 5, 30).map { n ->
                mf.format(Measure(n, unit)).filterNot { Character.isDigit(it) }.trim { it.isWhitespace() || Character.isSpaceChar(it) }
            }
        }.filter { it.isNotEmpty() }.distinct()
    }.getOrElse { emptyList() }
}
