package co.golink.tester.domain.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")

    private fun occ(start: String, end: String, repeat: String, until: String?, from: String, to: String, zone: ZoneId = lisbon) =
        Recurrence.occurrences(Instant.parse(start), Instant.parse(end), repeat, until, zone, Instant.parse(from), Instant.parse(to))
            .map { it.toString() }

    @Test
    fun `diario mantem a hora local depois da mudanca de hora`() {
        // 09:00 em Lisboa: verão = 08:00Z, inverno (depois de 25/10/2026) = 09:00Z.
        val list = occ("2026-10-24T08:00:00Z", "2026-10-24T09:00:00Z", "daily", null, "2026-10-24T00:00:00Z", "2026-10-27T00:00:00Z")
        assertEquals(listOf("2026-10-24T08:00:00Z", "2026-10-25T09:00:00Z", "2026-10-26T09:00:00Z"), list)
    }

    @Test
    fun `mensal no dia 31 fica no ultimo dia do mes`() {
        val list = occ("2026-01-31T10:00:00Z", "2026-01-31T11:00:00Z", "monthly", null, "2026-01-01T00:00:00Z", "2026-05-01T00:00:00Z", ZoneId.of("UTC"))
        assertEquals(listOf("2026-01-31T10:00:00Z", "2026-02-28T10:00:00Z", "2026-03-31T10:00:00Z", "2026-04-30T10:00:00Z"), list)
    }

    @Test
    fun `anual a 29 de fevereiro passa a 28`() {
        val list = occ("2028-02-29T10:00:00Z", "2028-02-29T11:00:00Z", "yearly", null, "2029-01-01T00:00:00Z", "2029-12-31T00:00:00Z", ZoneId.of("UTC"))
        assertEquals(listOf("2029-02-28T10:00:00Z"), list)
    }

    @Test
    fun `repeat_until inclui o proprio dia ate as 23h59`() {
        val list = occ("2026-03-02T22:00:00Z", "2026-03-02T23:00:00Z", "weekly", "2026-03-16", "2026-03-01T00:00:00Z", "2026-04-01T00:00:00Z", ZoneId.of("UTC"))
        assertEquals(listOf("2026-03-02T22:00:00Z", "2026-03-09T22:00:00Z", "2026-03-16T22:00:00Z"), list)
    }

    @Test
    fun `ocorrencia em curso no inicio do intervalo conta`() {
        // Começa às 23:00 do dia anterior e acaba à 01:00: sobrepõe-se a [from, to).
        val list = occ("2026-05-01T23:00:00Z", "2026-05-02T01:00:00Z", "daily", null, "2026-05-10T00:00:00Z", "2026-05-11T00:00:00Z", ZoneId.of("UTC"))
        assertEquals(listOf("2026-05-09T23:00:00Z", "2026-05-10T23:00:00Z"), list)
    }

    @Test
    fun `sem repeticao fora do intervalo nao aparece`() {
        assertEquals(emptyList<String>(), occ("2026-05-01T10:00:00Z", "2026-05-01T11:00:00Z", "none", null, "2026-05-02T00:00:00Z", "2026-05-03T00:00:00Z"))
    }

    @Test
    fun `dia inteiro arredonda ao dia UTC mais proximo`() {
        assertEquals(LocalDate.of(2026, 10, 28), CalendarDates.allDayFromIso("2026-10-28T00:00:00Z"))
        assertEquals(LocalDate.of(2026, 10, 28), CalendarDates.allDayFromIso("2026-10-27T23:00:00.000000Z"))
        assertEquals("2026-10-28T00:00:00Z", CalendarDates.allDayToIso(LocalDate.of(2026, 10, 28)))
    }

    @Test
    fun `grelha mensal comeca a segunda e tem 42 dias`() {
        val grid = CalendarDates.monthGrid(LocalDate.of(2026, 10, 28))
        assertEquals(42, grid.size)
        assertEquals(LocalDate.of(2026, 9, 28), grid.first())
    }
}
