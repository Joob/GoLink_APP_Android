package co.golink.tester.data.settings

import android.content.Context
import co.golink.tester.domain.user.User
import co.golink.tester.ui.i18n.I18n
import co.golink.tester.ui.theme.AppThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Idioma e tema iguais aos da web (Settings › Preferences → user_settings).
 *
 * - Escolha local (gaveta, ecrã de entrada, Preferências) → PATCH ao servidor.
 *   Sem sessão (ou sem rede) fica marcada como pendente e vai no próximo
 *   utilizador carregado.
 * - Idioma: é da conta (emails, push) — sem pendentes, segue o do servidor.
 * - Tema: só segue o do servidor enquanto não foi escolhido neste aparelho.
 */
@Singleton
class AccountPrefsSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy { context.getSharedPreferences("account_prefs_sync", Context.MODE_PRIVATE) }

    fun languageChanged(code: String) {
        prefs.edit().putBoolean(KEY_LANG_DIRTY, true).apply()
        scope.launch { push("language", code, KEY_LANG_DIRTY) }
    }

    fun themeChanged(mode: AppThemeMode.Mode) {
        prefs.edit().putBoolean(KEY_THEME_DIRTY, true).putBoolean(KEY_THEME_LOCAL, true).apply()
        scope.launch { push("theme_mode", mode.toServer(), KEY_THEME_DIRTY) }
    }

    /** Utilizador (re)carregado: envia pendentes ou adota os valores da conta. */
    suspend fun onUser(user: User) {
        if (prefs.getBoolean(KEY_LANG_DIRTY, false) || user.language.isNullOrBlank()) {
            scope.launch { push("language", I18n.lang, KEY_LANG_DIRTY) }
        } else if (user.language != I18n.lang && user.language in SUPPORTED) {
            withContext(Dispatchers.Main) { I18n.applyFromAccount(context, user.language) }
        }

        if (prefs.getBoolean(KEY_THEME_DIRTY, false)) {
            scope.launch { push("theme_mode", AppThemeMode.mode.toServer(), KEY_THEME_DIRTY) }
        } else if (!prefs.getBoolean(KEY_THEME_LOCAL, false)) {
            val server = user.themeMode?.let(::fromServer)
            if (server != null && server != AppThemeMode.mode) {
                withContext(Dispatchers.Main) { AppThemeMode.applyFromAccount(context, server) }
            }
        }
    }

    private suspend fun push(name: String, value: String, dirtyKey: String) {
        repository.updateProfileField(name, value).onSuccess {
            prefs.edit().putBoolean(dirtyKey, false).apply()
        }
    }

    private fun AppThemeMode.Mode.toServer(): String = when (this) {
        AppThemeMode.Mode.LIGHT -> "light"
        AppThemeMode.Mode.DARK -> "dark"
        AppThemeMode.Mode.SYSTEM -> "system"
    }

    private fun fromServer(v: String): AppThemeMode.Mode? = when (v) {
        "light" -> AppThemeMode.Mode.LIGHT
        "dark" -> AppThemeMode.Mode.DARK
        "system" -> AppThemeMode.Mode.SYSTEM
        else -> null
    }

    private companion object {
        const val KEY_LANG_DIRTY = "lang_dirty"
        const val KEY_THEME_DIRTY = "theme_dirty"
        const val KEY_THEME_LOCAL = "theme_local"
        val SUPPORTED = listOf(I18n.PT, I18n.EN, I18n.FR, I18n.ES)
    }
}
