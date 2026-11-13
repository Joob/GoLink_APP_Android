package co.golink.tester.domain.calendar

import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * Feriados nacionais vindos do servidor (GET api/calendar/holidays), gerados com a
 * mesma date-holidays da web: mesmas datas, dias de substituição e nomes no idioma da app.
 */
object Holidays {
    @Serializable
    data class HolidayDto(val date: String, val days: Int = 1, val name: String)

    @Serializable
    data class HolidayListResponse(val data: List<HolidayDto> = emptyList())

    @Serializable
    data class CountryListResponse(val data: List<String> = emptyList())

    data class Holiday(val date: LocalDate, val days: Int, val name: String) {
        val end: LocalDate get() = date.plusDays(days.coerceAtLeast(1).toLong())
    }

    fun parse(list: List<HolidayDto>): List<Holiday> =
        list.mapNotNull { h -> runCatching { Holiday(LocalDate.parse(h.date), h.days, h.name) }.getOrNull() }

    /** Feriados que se sobrepõem a [from, to) (datas). */
    fun overlapping(list: List<Holiday>, from: LocalDate, to: LocalDate): List<Holiday> =
        list.filter { it.date.isBefore(to) && it.end.isAfter(from) }

    /** Como o getter holidayCountry da web: perfil → faturação → aparelho → PT. */
    fun resolveCountry(profile: String?, billing: String?, device: String?): String =
        listOf(profile, billing, device).firstOrNull { it != null && Regex("^[A-Za-z]{2}$").matches(it) }?.uppercase() ?: "PT"
}
