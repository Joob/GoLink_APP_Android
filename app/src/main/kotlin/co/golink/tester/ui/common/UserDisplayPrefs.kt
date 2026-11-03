package co.golink.tester.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.golink.tester.domain.user.DisplayPrefs
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Espelho Compose de [DisplayPrefs] (espelho de resources/js/helpers/userPrefs.js)
 * para os ecrãs recomporem quando mudam. A fonte é UserRepository.displayPrefs;
 * o App recolhe-a no Main e chama [set] — um só estado, escrito numa só thread,
 * para nenhum frame ver o formato novo com o início de semana antigo.
 */
object UserDisplayPrefs {
    var current by mutableStateOf(DisplayPrefs.DEFAULT)
        private set

    val dateFormat: String? get() = current.dateFormat
    val weekStart: DayOfWeek get() = current.weekStart

    fun set(prefs: DisplayPrefs) {
        current = prefs
    }

    /** Data no formato escolhido; null em modo automático (quem chama usa o seu padrão). */
    fun format(d: LocalDate, withYear: Boolean = true): String? = when (dateFormat) {
        "d MMM y" -> d.format(if (withYear) DateFormatters.localized("d MMM yyyy") else DateFormatters.localized("d MMM")).replace(".", "")
        "dd/MM/y" -> d.format(if (withYear) DMY else DM)
        "MM/dd/y" -> d.format(if (withYear) MDY else MD)
        "y-MM-dd" -> d.format(if (withYear) YMD else MD_ISO)
        else -> null
    }

    private val DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val DM = DateTimeFormatter.ofPattern("dd/MM")
    private val MDY = DateTimeFormatter.ofPattern("MM/dd/yyyy")
    private val MD = DateTimeFormatter.ofPattern("MM/dd")
    private val YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val MD_ISO = DateTimeFormatter.ofPattern("MM-dd")
}

/**
 * Formatadores reutilizados nas listas (atividade, notificações, segurança):
 * criar um DateTimeFormatter por linha e por recomposição pesa no scroll.
 * Os dependentes do idioma ficam em cache por (padrão, locale).
 */
object DateFormatters {
    val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private val cache = java.util.concurrent.ConcurrentHashMap<Pair<String, Locale>, DateTimeFormatter>()

    fun localized(pattern: String): DateTimeFormatter {
        val locale = appLocale()
        return cache.getOrPut(pattern to locale) { DateTimeFormatter.ofPattern(pattern, locale) }
    }
}

/** "14 out 2026" ou o formato escolhido pelo utilizador. */
fun prefDate(d: LocalDate): String =
    UserDisplayPrefs.format(d) ?: d.format(DateFormatters.localized("d MMM yyyy"))

/** "14 out" (sem ano) ou o formato escolhido pelo utilizador. */
fun prefDateShort(d: LocalDate): String =
    UserDisplayPrefs.format(d, withYear = false) ?: d.format(DateFormatters.localized("d MMM")).replace(".", "")
