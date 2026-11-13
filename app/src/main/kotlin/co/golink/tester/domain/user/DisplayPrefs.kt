package co.golink.tester.domain.user

import java.time.DayOfWeek

/**
 * Preferências de apresentação (web: Settings › Preferences), derivadas do
 * utilizador carregado. Um único valor imutável: formato e início da semana
 * mudam juntos, nunca um sem o outro.
 *
 * dateFormat: null = automático pelo idioma; senão 'd MMM y' | 'dd/MM/y' | 'MM/dd/y' | 'y-MM-dd'.
 * weekStart: servidor 0 = domingo, 1 = segunda (omissão), 6 = sábado.
 */
data class DisplayPrefs(
    val dateFormat: String? = null,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) {
    companion object {
        val DEFAULT = DisplayPrefs()

        private val FORMATS = setOf("d MMM y", "dd/MM/y", "MM/dd/y", "y-MM-dd")

        fun from(user: User?): DisplayPrefs = DisplayPrefs(
            dateFormat = user?.dateFormat?.takeIf { it in FORMATS },
            weekStart = when (user?.weekStart) {
                0 -> DayOfWeek.SUNDAY
                6 -> DayOfWeek.SATURDAY
                else -> DayOfWeek.MONDAY
            },
        )
    }
}
