package co.golink.tester.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Preferências de apresentação do utilizador (web: Settings › Preferences),
 * espelho de resources/js/helpers/userPrefs.js. Atualizadas pelo
 * UserRepository sempre que o utilizador é carregado. Estado Compose para os
 * ecrãs abertos recomporem quando mudam.
 *
 * date_format: null = automático pelo idioma; senão 'd MMM y' | 'dd/MM/y' | 'MM/dd/y' | 'y-MM-dd'.
 * week_start: 0 = domingo, 1 = segunda (omissão), 6 = sábado.
 */
object UserDisplayPrefs {
    var dateFormat by mutableStateOf<String?>(null)
        private set
    var weekStart by mutableStateOf(DayOfWeek.MONDAY)
        private set

    fun update(dateFormat: String?, weekStart: Int?) {
        this.dateFormat = dateFormat?.takeIf { it in FORMATS }
        this.weekStart = when (weekStart) {
            0 -> DayOfWeek.SUNDAY
            6 -> DayOfWeek.SATURDAY
            else -> DayOfWeek.MONDAY
        }
    }

    fun clear() = update(null, null)

    /** Data no formato escolhido; null em modo automático (quem chama usa o seu padrão). */
    fun format(d: LocalDate, withYear: Boolean = true): String? = when (dateFormat) {
        "d MMM y" -> d.format(DateTimeFormatter.ofPattern(if (withYear) "d MMM yyyy" else "d MMM", appLocale())).replace(".", "")
        "dd/MM/y" -> d.format(DateTimeFormatter.ofPattern(if (withYear) "dd/MM/yyyy" else "dd/MM"))
        "MM/dd/y" -> d.format(DateTimeFormatter.ofPattern(if (withYear) "MM/dd/yyyy" else "MM/dd"))
        "y-MM-dd" -> d.format(DateTimeFormatter.ofPattern(if (withYear) "yyyy-MM-dd" else "MM-dd"))
        else -> null
    }

    private val FORMATS = setOf("d MMM y", "dd/MM/y", "MM/dd/y", "y-MM-dd")
}

/** "14 out 2026" ou o formato escolhido pelo utilizador. */
fun prefDate(d: LocalDate): String =
    UserDisplayPrefs.format(d) ?: d.format(DateTimeFormatter.ofPattern("d MMM yyyy", appLocale()))
