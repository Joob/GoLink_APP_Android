package co.golink.tester.domain.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Feriados nacionais (sem regionais), offline. A web usa a npm date-holidays;
 * aqui uma tabela para os países suportados: datas fixas + móveis a partir da
 * Páscoa. Nomes na língua do país, como num calendário local.
 */
object Holidays {
    val COUNTRIES = listOf("PT", "ES", "FR", "BR", "US", "GB")

    data class Holiday(val date: LocalDate, val name: String)

    /** Domingo de Páscoa (algoritmo de Meeus/Jones/Butcher, calendário gregoriano). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    private fun nth(year: Int, month: Int, dow: DayOfWeek, n: Int): LocalDate =
        LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, dow))

    private fun last(year: Int, month: Int, dow: DayOfWeek): LocalDate =
        LocalDate.of(year, month, 1).with(TemporalAdjusters.lastInMonth(dow))

    fun forYear(country: String, year: Int): List<Holiday> {
        val e = easter(year)
        fun d(m: Int, day: Int) = LocalDate.of(year, m, day)
        val list = when (country) {
            "PT" -> listOf(
                d(1, 1) to "Ano Novo",
                e.minusDays(2) to "Sexta-feira Santa",
                e to "Páscoa",
                d(4, 25) to "Dia da Liberdade",
                d(5, 1) to "Dia do Trabalhador",
                e.plusDays(60) to "Corpo de Deus",
                d(6, 10) to "Dia de Portugal",
                d(8, 15) to "Assunção de Nossa Senhora",
                d(10, 5) to "Implantação da República",
                d(11, 1) to "Dia de Todos-os-Santos",
                d(12, 1) to "Restauração da Independência",
                d(12, 8) to "Imaculada Conceição",
                d(12, 25) to "Natal",
            )
            "ES" -> listOf(
                d(1, 1) to "Año Nuevo",
                d(1, 6) to "Epifanía del Señor",
                e.minusDays(2) to "Viernes Santo",
                d(5, 1) to "Fiesta del Trabajo",
                d(8, 15) to "Asunción de la Virgen",
                d(10, 12) to "Fiesta Nacional de España",
                d(11, 1) to "Todos los Santos",
                d(12, 6) to "Día de la Constitución",
                d(12, 8) to "Inmaculada Concepción",
                d(12, 25) to "Navidad",
            )
            "FR" -> listOf(
                d(1, 1) to "Jour de l’an",
                e.plusDays(1) to "Lundi de Pâques",
                d(5, 1) to "Fête du Travail",
                d(5, 8) to "Victoire 1945",
                e.plusDays(39) to "Ascension",
                e.plusDays(50) to "Lundi de Pentecôte",
                d(7, 14) to "Fête nationale",
                d(8, 15) to "Assomption",
                d(11, 1) to "Toussaint",
                d(11, 11) to "Armistice 1918",
                d(12, 25) to "Noël",
            )
            "BR" -> listOfNotNull(
                d(1, 1) to "Confraternização Universal",
                e.minusDays(2) to "Sexta-feira Santa",
                d(4, 21) to "Tiradentes",
                d(5, 1) to "Dia do Trabalho",
                d(9, 7) to "Independência do Brasil",
                d(10, 12) to "Nossa Senhora Aparecida",
                d(11, 2) to "Finados",
                d(11, 15) to "Proclamação da República",
                if (year >= 2024) d(11, 20) to "Dia da Consciência Negra" else null,
                d(12, 25) to "Natal",
            )
            "US" -> listOfNotNull(
                d(1, 1) to "New Year's Day",
                nth(year, 1, DayOfWeek.MONDAY, 3) to "Martin Luther King Jr. Day",
                nth(year, 2, DayOfWeek.MONDAY, 3) to "Washington's Birthday",
                last(year, 5, DayOfWeek.MONDAY) to "Memorial Day",
                if (year >= 2021) d(6, 19) to "Juneteenth" else null,
                d(7, 4) to "Independence Day",
                nth(year, 9, DayOfWeek.MONDAY, 1) to "Labor Day",
                nth(year, 10, DayOfWeek.MONDAY, 2) to "Columbus Day",
                d(11, 11) to "Veterans Day",
                nth(year, 11, DayOfWeek.THURSDAY, 4) to "Thanksgiving Day",
                d(12, 25) to "Christmas Day",
            )
            "GB" -> gb(year, e)
            else -> emptyList()
        }
        return list.map { (date, name) -> Holiday(date, name) }.sortedBy { it.date }
    }

    /** Inglaterra e País de Gales: com dias de substituição ao fim de semana. */
    private fun gb(year: Int, e: LocalDate): List<Pair<LocalDate, String>> {
        fun weekday(d: LocalDate) = d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY
        fun nextMonday(d: LocalDate) = d.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val newYear = LocalDate.of(year, 1, 1).let { if (weekday(it)) it else nextMonday(it) }
        val xmas = LocalDate.of(year, 12, 25)
        val boxing = LocalDate.of(year, 12, 26)
        val (xmasObs, boxingObs) = when (xmas.dayOfWeek) {
            DayOfWeek.SATURDAY -> xmas.plusDays(2) to xmas.plusDays(3)
            DayOfWeek.SUNDAY -> xmas.plusDays(2) to xmas.plusDays(1)
            DayOfWeek.FRIDAY -> xmas to xmas.plusDays(3)
            else -> xmas to boxing
        }
        return listOf(
            newYear to "New Year's Day",
            e.minusDays(2) to "Good Friday",
            e.plusDays(1) to "Easter Monday",
            nth(year, 5, DayOfWeek.MONDAY, 1) to "Early May bank holiday",
            last(year, 5, DayOfWeek.MONDAY) to "Spring bank holiday",
            last(year, 8, DayOfWeek.MONDAY) to "Summer bank holiday",
            xmasObs to "Christmas Day",
            boxingObs to "Boxing Day",
        )
    }

    /** Feriados que tocam [from, to) (datas). */
    fun between(country: String, from: LocalDate, to: LocalDate): List<Holiday> =
        (from.year..to.year).flatMap { forYear(country, it) }.filter { !it.date.isBefore(from) && it.date.isBefore(to) }

    /**
     * País: o do perfil, senão o da faturação, senão o do aparelho, senão PT.
     * Um país escolhido na web que aqui não existe → null (sem feriados, em vez de outros).
     */
    fun resolveCountry(profile: String?, billing: String?, device: String?): String? {
        profile?.uppercase()?.takeIf { it.isNotBlank() }?.let { return it.takeIf { c -> c in COUNTRIES } }
        return listOf(billing, device).firstNotNullOfOrNull { c -> c?.uppercase()?.takeIf { it in COUNTRIES } } ?: "PT"
    }
}
