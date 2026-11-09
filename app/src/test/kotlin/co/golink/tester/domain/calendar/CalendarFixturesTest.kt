package co.golink.tester.domain.calendar

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Casos partilhados com a web e o PHP (tests/fixtures/calendar no servidor).
 * Os JSON em test/resources/calendar são cópias: atualizar com
 *   node ops/calendar/fixtures.mjs --copy <android>/app/src/test/resources/calendar
 */
class CalendarFixturesTest {
    private fun fixture(name: String): JsonObject =
        Json.parseToJsonElement(
            javaClass.classLoader!!.getResourceAsStream("calendar/$name")!!.use { it.readBytes().toString(Charsets.UTF_8) },
        ).jsonObject

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    @Test
    fun recurrenceMatchesWebAndServer() {
        val failures = mutableListOf<String>()
        val cases = fixture("recurrence-cases.json")["cases"]!!.jsonArray
        assertTrue(cases.isNotEmpty())
        for (c in cases.map { it.jsonObject }) {
            val rule = RepeatRule(
                interval = c["interval"]?.jsonPrimitive?.intOrNull ?: 1,
                days = (c["days"] as? JsonArray)?.map { it.jsonPrimitive.int } ?: emptyList(),
                count = c["count"]?.jsonPrimitive?.intOrNull,
            )
            val got = Recurrence.occurrences(
                Instant.parse(c.str("start")!!), Instant.parse(c.str("end")!!), c.str("repeat")!!, c.str("until"),
                ZoneId.of(c.str("timezone")!!), Instant.parse(c.str("from")!!), Instant.parse(c.str("to")!!), rule,
            ).map { it.toString() }
            val want = c["expected"]!!.jsonArray.map { it.jsonPrimitive.content }
            if (got != want) failures += "${c.str("name")}: esperado $want, obtido $got"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun quickEventMatchesWeb() {
        val root = fixture("quick-event-cases.json")
        val parser = QuickEventParser(JvmSourceForReview)
        val calendars = root["calendars"]!!.jsonArray.map { QuickCalendar(it.jsonObject.str("id")!!, it.jsonObject.str("name")!!) }
        val failures = mutableListOf<String>()
        for (c in root["cases"]!!.jsonArray.map { it.jsonObject }) {
            val text = c.str("text")!!
            val r = parser.parse(
                text,
                now = LocalDateTime.parse(c.str("now") ?: root.str("now")!!),
                locales = listOf(c.str("locale")!!),
                dayFirst = (c["dayFirst"] as? JsonPrimitive)?.booleanOrNull ?: true,
                calendars = calendars,
                defaultCalendarId = root.str("defaultCalendarId"),
                selfEmail = root.str("selfEmail"),
            )
            for ((field, want) in c["expect"]!!.jsonObject) {
                val got = actual(r, field)
                if (normalize(want) != got) failures += "${c.str("locale")} \"$text\" · $field: esperado $want, obtido $got"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** O resultado Kotlin no mesmo formato do JS (datas "AAAA-MM-DD", horas "HH:MM"). */
    private fun actual(r: QuickParse, field: String): Any? = when (field) {
        "ok" -> r.ok
        "error" -> r.error
        "title" -> r.title
        "location" -> r.location
        "calendarId" -> r.calendarId
        "unknownTag" -> r.unknownTag
        "allDay" -> r.allDay
        "startDay" -> r.startDay?.toString()
        "startTime" -> r.startTime?.toString()
        "endDay" -> r.endDay?.toString()
        "endTime" -> r.endTime?.toString()
        "relative" -> r.relative
        "repeat" -> r.repeat?.let { mapOf("repeat" to it.repeat, "interval" to it.interval, "days" to it.days) }
        "repeatUntil" -> r.repeatUntil?.toString()
        "alerts" -> r.alerts
        "guests" -> r.guests
        "notes" -> r.notes
        else -> error("Campo desconhecido no fixture: $field")
    }

    private fun normalize(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonPrimitive -> if (e.isString) e.content else e.booleanOrNull ?: e.intOrNull
        is JsonArray -> e.map { normalize(it) }
        is JsonObject -> e.mapValues { normalize(it.value) }
    }
}
