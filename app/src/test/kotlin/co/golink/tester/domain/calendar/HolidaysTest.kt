package co.golink.tester.domain.calendar

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HolidaysTest {
    @Test
    fun `pascoa 2025 a 2027`() {
        assertEquals(LocalDate.of(2025, 4, 20), Holidays.easter(2025))
        assertEquals(LocalDate.of(2026, 4, 5), Holidays.easter(2026))
        assertEquals(LocalDate.of(2027, 3, 28), Holidays.easter(2027))
    }

    @Test
    fun `portugal 2026 tem corpo de deus e 13 feriados`() {
        val list = Holidays.forYear("PT", 2026)
        assertEquals(13, list.size)
        assertTrue(list.any { it.date == LocalDate.of(2026, 6, 4) && it.name == "Corpo de Deus" })
    }

    @Test
    fun `natal ao sabado no reino unido passa para segunda e terca`() {
        val gb = Holidays.forYear("GB", 2027).associate { it.name to it.date }
        assertEquals(LocalDate.of(2027, 12, 27), gb["Christmas Day"])
        assertEquals(LocalDate.of(2027, 12, 28), gb["Boxing Day"])
    }

    @Test
    fun `pais cai para PT quando nao suportado`() {
        assertEquals("PT", Holidays.resolveCountry(null, "DE", "IT"))
        assertEquals(null, Holidays.resolveCountry("DE", "PT", "PT"))
        assertEquals("ES", Holidays.resolveCountry(null, "es", "PT"))
    }
}
