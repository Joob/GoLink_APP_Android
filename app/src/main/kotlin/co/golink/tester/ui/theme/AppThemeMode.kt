package co.golink.tester.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tema escolhido na app (botão Lua/Sol no menu lateral). Mesmo padrão do I18n:
 * `mode` é snapshot state, por isso mudar recompõe a app toda na hora, e a
 * escolha fica guardada. Até ao primeiro toque segue o tema do sistema.
 */
object AppThemeMode {
    enum class Mode { SYSTEM, LIGHT, DARK }

    var mode by mutableStateOf(Mode.SYSTEM)
        private set

    fun init(context: Context) {
        mode = runCatching { Mode.valueOf(prefs(context).getString(KEY, null) ?: Mode.SYSTEM.name) }
            .getOrDefault(Mode.SYSTEM)
    }

    fun set(context: Context, value: Mode) {
        prefs(context).edit().putString(KEY, value.name).apply()
        mode = value
    }

    @Composable
    fun isDark(): Boolean = when (mode) {
        Mode.LIGHT -> false
        Mode.DARK -> true
        Mode.SYSTEM -> isSystemInDarkTheme()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)

    private const val KEY = "mode"
}
