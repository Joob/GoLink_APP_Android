package co.golink.tester.domain.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.DateFormatSymbols
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Fonte de teste: meses/dias/AM-PM do CLDR da JVM; o que no Android vem do
 * ICU (relativos, sufixos, unidades) fica escrito à mão para as línguas testadas.
 */
internal object JvmSourceForReview : QuickLocaleSource by JvmSource

private object JvmSource : QuickLocaleSource {
    private val rel = mapOf(
        "pt" to listOf("ontem" to -1, "hoje" to 0, "amanhã" to 1, "depois de amanhã" to 2),
        "en" to listOf("yesterday" to -1, "today" to 0, "tomorrow" to 1),
        "de" to listOf("gestern" to -1, "heute" to 0, "morgen" to 1, "übermorgen" to 2),
        "fr" to listOf("hier" to -1, "aujourd’hui" to 0, "demain" to 1, "après-demain" to 2),
        "es" to listOf("ayer" to -1, "hoy" to 0, "mañana" to 1, "pasado mañana" to 2),
        "it" to listOf("ieri" to -1, "oggi" to 0, "domani" to 1, "dopodomani" to 2),
        "zh" to listOf("昨天" to -1, "今天" to 0, "明天" to 1, "后天" to 2),
    )
    private val hour = mapOf("de" to " Uhr", "fr" to " h", "ja" to "時", "zh" to "时", "ko" to "시")
    private val dayAffix = mapOf("de" to ". ", "ja" to "日", "zh" to "日", "ko" to "일")
    private val yearAffix = mapOf("ja" to "年", "zh" to "年", "ko" to "년")
    private val ampm = mapOf("ja" to listOf("午前", "午後"), "zh" to listOf("上午", "下午"), "ko" to listOf("오전", "오후"))

    override fun load(locale: String): QuickLocaleRaw {
        val loc = Locale.forLanguageTag(locale)
        val months = listOf("LLLL", "LLL", "MMMM", "MMM").flatMap { p ->
            val f = DateTimeFormatter.ofPattern(p, loc)
            (1..12).map { f.format(LocalDate.of(2027, it, 2)) to it }
        }
        val days = (0..6).map { LocalDate.of(2024, 1, 1).plusDays(it.toLong()) }
        val symbols = ampm[locale] ?: DateFormatSymbols.getInstance(loc).amPmStrings.toList()
        return QuickLocaleRaw(
            months = months,
            weekdays = days.mapIndexed { i, d -> DateTimeFormatter.ofPattern("EEEE", loc).format(d) to i + 1 },
            weekdaysShort = days.mapIndexed { i, d -> DateTimeFormatter.ofPattern("EEE", loc).format(d) to i + 1 },
            relative = rel[locale].orEmpty(),
            am = listOf(symbols[0]),
            pm = listOf(symbols[1]),
            hourSuffix = listOfNotNull(hour[locale]),
            dayAffix = listOfNotNull(dayAffix[locale]),
            yearAffix = listOfNotNull(yearAffix[locale]),
            minuteUnits = if (locale == "en") listOf("minute", "minutes", "min") else if (locale == "pt") listOf("minuto", "minutos", "min") else emptyList(),
            hourUnits = if (locale == "en") listOf("hour", "hours", "hr") else if (locale == "pt") listOf("hora", "horas") else emptyList(),
        )
    }
}

class QuickEventParserTest {
    private val parser = QuickEventParser(JvmSource)
    private val now = LocalDateTime.of(2026, 11, 2, 10, 0) // segunda
    private val cals = listOf(QuickCalendar("p", "Personal"), QuickCalendar("w", "Work"), QuickCalendar("f", "Família"))

    private fun p(loc: String, text: String) = parser.parse(text, now, listOf(loc), dayFirst = true, calendars = cals, defaultCalendarId = "p")

    private fun check(loc: String, text: String, title: String, day: String, start: String? = null, end: String? = null) {
        val r = p(loc, text)
        assertTrue("$text → ${r.error}", r.ok)
        assertEquals(text, title, r.title)
        assertEquals(text, LocalDate.parse(day), r.startDay)
        if (start == null) assertTrue(text, r.allDay)
        else {
            assertEquals(text, LocalTime.parse(start), r.startTime)
            assertEquals(text, LocalTime.parse(end), r.endTime)
        }
    }

    @Test fun portuguese() {
        check("pt", "Jantar amanhã às 20h @Porto #família", "Jantar", "2026-11-03", "20:00", "21:00")
        assertEquals("Porto", p("pt", "Jantar amanhã às 20h @Porto #família").location)
        assertEquals("f", p("pt", "Jantar amanhã às 20h @Porto #família").calendarId)
        check("pt", "Aniversário da Ana no dia 2 de fevereiro de 2027", "Aniversário da Ana", "2027-02-02")
        check("pt", "Dentista sexta 9:30 #personal", "Dentista", "2026-11-06", "09:30", "10:30")
        check("pt", "Jantar 2 dez 20h30", "Jantar", "2026-12-02", "20:30", "21:30")
        check("pt", "Voo para Paris dia 10/12 às 6:45 #work", "Voo para Paris", "2026-12-10", "06:45", "07:45")
        check("pt", "Mar e sol domingo", "Mar e sol", "2026-11-08")
    }

    @Test fun english() {
        check("en", "Lunch with John tomorrow 13h @Cais #fam", "Lunch with John", "2026-11-03", "13:00", "14:00")
        check("en", "Apple WWDC Event on 7th June 2021", "Apple WWDC Event", "2021-06-07")
        check("en", "Meeting from 2pm to 4pm on March 5", "Meeting", "2027-03-05", "14:00", "16:00")
        check("en", "Meeting Dec 5 at 10am", "Meeting", "2026-12-05", "10:00", "11:00")
        check("en", "Call tomorrow 3pm for 30 min", "Call", "2026-11-03", "15:00", "15:30")
        // Segunda às 10h: "Monday 9:15" já passou → a próxima.
        check("en", "Standup Monday 9:15 - 9:30", "Standup", "2026-11-09", "09:15", "09:30")
        check("en", "Party 2027-01-01 22:00", "Party", "2027-01-01", "22:00", "23:00")
    }

    @Test fun otherLanguages() {
        check("de", "Geburtstag Anna 2. Februar 2027", "Geburtstag Anna", "2027-02-02")
        check("de", "Treffen am 5. März um 13 Uhr", "Treffen", "2027-03-05", "13:00", "14:00")
        check("de", "Arzt morgen 14:30", "Arzt", "2026-11-03", "14:30", "15:30")
        check("fr", "Dîner le 14 juillet à 20h", "Dîner", "2027-07-14", "20:00", "21:00")
        check("es", "Cena el 3 de diciembre a las 21:00", "Cena", "2026-12-03", "21:00", "22:00")
        check("it", "Cena domani alle 20:30", "Cena", "2026-11-03", "20:30", "21:30")
        check("ru", "День рождения 2 февраля", "День рождения", "2027-02-02")
        check("ja", "2月2日 誕生日", "誕生日", "2027-02-02")
        check("ja", "2027年3月15日 午後3時 会議", "会議", "2027-03-15", "15:00", "16:00")
        check("zh", "明天 下午3点 开会", "开会", "2026-11-03", "15:00", "16:00")
        check("ko", "2월 2일 생일", "생일", "2027-02-02")
        check("ar", "اجتماع ٢ فبراير", "اجتماع", "2027-02-02")
        check("pl", "Spotkanie 5 marca 14:00", "Spotkanie", "2027-03-05", "14:00", "15:00")
        // Língua não prioritária: nomes longos continuam a valer.
        check("pt", "Geburtstag 2. Februar", "Geburtstag", "2027-02-02")
    }

    @Test fun errors() {
        assertEquals("empty", p("pt", "  ").error)
        assertEquals("no_date", p("pt", "Reunião").error)
        assertEquals("no_date", p("en", "Call with team at 3pm for 30 min").error)
        assertEquals("no_day", p("pt", "Reunião em março").error)
        assertEquals("invalid_date", p("pt", "Dia 30 de fevereiro festa").error)
        assertEquals("no_title", p("pt", "amanhã 13h").error)
        assertFalse(p("pt", "amanhã 13h").ok)
    }

    @Test fun unknownCalendarKeepsDefault() {
        val r = p("en", "Gym tomorrow 7am #sport")
        assertEquals("p", r.calendarId)
        assertEquals("sport", r.unknownTag)
    }
}

class QuickEventParserReviewTest {
    private val parser = QuickEventParser(JvmSourceForReview)
    private val now = LocalDateTime.of(2026, 11, 2, 10, 0)
    private fun p(loc: String, text: String) = parser.parse(text, now, listOf(loc), dayFirst = true, defaultCalendarId = "p")

    @Test fun foreignMonthNamesDoNotBeatLocalOnes() {
        val r = p("pt", "Almoço com a Marta 5 de maio")
        assertEquals(LocalDate.of(2027, 5, 5), r.startDay)
        assertEquals("Almoço com a Marta", r.title)
        assertEquals(LocalDate.of(2026, 11, 3), p("pt", "Jantar com a Marta amanhã").startDay)
        assertEquals("no_date", p("pt", "Jantar com a Marta").error)
    }

    @Test fun hoursAsClockTime() {
        val r = p("pt", "Reunião amanhã às 10 horas durante 30 min")
        assertEquals(LocalTime.of(10, 0), r.startTime)
        assertEquals(LocalTime.of(10, 30), r.endTime)
        assertEquals("Reunião", r.title)
    }

    @Test fun dottedTimeAtEndOfSentence() {
        val r = p("pt", "Reunião amanhã às 10.30.")
        assertEquals(LocalDate.of(2026, 11, 3), r.startDay)
        assertEquals(LocalTime.of(10, 30), r.startTime)
    }

    @Test fun placeKeepsItsNumbers() {
        val r = p("pt", "Jantar amanhã @Avenida 5 de Outubro")
        assertEquals(LocalDate.of(2026, 11, 3), r.startDay)
        assertEquals("Avenida 5 de Outubro", r.location)
        assertEquals("Cais", p("pt", "Jantar @Cais amanhã 13h").location)
    }

    @Test fun koreanAffixesAndLeapDay() {
        assertEquals("생일", p("ko", "2027년 2월 2일 생일").title)
        assertEquals(LocalDate.of(2027, 2, 2), p("ko", "2월2일 생일").startDay)
        assertEquals(LocalDate.of(2028, 2, 29), p("pt", "Aniversário 29 fevereiro").startDay)
    }
}
