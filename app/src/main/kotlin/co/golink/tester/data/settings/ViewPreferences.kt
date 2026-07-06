package co.golink.tester.data.settings

import android.content.Context
import androidx.core.content.edit
import co.golink.tester.ui.screens.browse.SortMode
import co.golink.tester.ui.screens.browse.ViewMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guarda a preferência de vista (grelha/lista) e ordenação por ecrã, para que
 * fique como o utilizador a deixou depois de fechar/reabrir a app. O "scope"
 * separa os ecrãs (ex.: "browse" vs "backup"), que têm predefinições distintas.
 */
@Singleton
class ViewPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("ui_view_prefs", Context.MODE_PRIVATE)

    fun viewMode(scope: String, default: ViewMode): ViewMode =
        prefs.getString("${scope}_view", null)
            ?.let { runCatching { ViewMode.valueOf(it) }.getOrNull() } ?: default

    fun setViewMode(scope: String, mode: ViewMode) =
        prefs.edit { putString("${scope}_view", mode.name) }

    fun sortMode(scope: String, default: SortMode): SortMode =
        prefs.getString("${scope}_sort", null)
            ?.let { runCatching { SortMode.valueOf(it) }.getOrNull() } ?: default

    fun setSortMode(scope: String, mode: SortMode) =
        prefs.edit { putString("${scope}_sort", mode.name) }

    companion object {
        const val SCOPE_BROWSE = "browse"
        const val SCOPE_BACKUP = "backup"
    }
}
