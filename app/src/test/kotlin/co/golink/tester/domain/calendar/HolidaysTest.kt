package co.golink.tester.domain.calendar

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HolidaysTest {
    @Test
    fun `pais segue a web - perfil, faturacao, aparelho, PT`() {
        assertEquals("DE", Holidays.resolveCountry(null, "DE", "IT"))
        assertEquals("DE", Holidays.resolveCountry("de", "PT", "PT"))
        assertEquals("IT", Holidays.resolveCountry("", null, "IT"))
        assertEquals("PT", Holidays.resolveCountry(null, null, ""))
    }

    @Test
    fun `sobreposicao inclui feriados de varios dias vindos do ano anterior`() {
        val list = Holidays.parse(listOf(
            Holidays.HolidayDto("2026-12-31", 3, "Passagem"),
            Holidays.HolidayDto("2027-01-05", 1, "Fora"),
        ))
        val hit = Holidays.overlapping(list, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 5))
        assertEquals(listOf("Passagem"), hit.map { it.name })
    }
}
