package co.golink.tester.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.ui.components.BrowseItemRow
import co.golink.tester.ui.i18n.tr

/** Separador do ecrã de pesquisa; [slug] = filtro do servidor (null = Tudo). */
private data class SearchTab(val slug: String?, val label: String, val needsBackup: Boolean = false)

private val SEARCH_TABS = listOf(
    SearchTab(null, "Tudo"),
    SearchTab("files", "Ficheiros"),
    SearchTab("folders", "Pastas"),
    SearchTab("shared", "Partilhados"),
    SearchTab("team", "Equipa"),
    SearchTab("mobile_backup", "Backup Mobile", needsBackup = true),
)

/**
 * Pesquisa em ecrã próprio (paridade com o popup da web): campo, separadores
 * (Backup Mobile bloqueado com o backup desligado) e Recentes com o campo vazio.
 */
@Composable
fun SearchSheet(
    state: SearchSheetState,
    mobileBackupEnabled: Boolean,
    onQueryChange: (String) -> Unit,
    onTabChange: (String?) -> Unit,
    onOpenItem: (BrowseItem, List<BrowseItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    var lockedHint by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                // Campo + fechar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        if (state.query.isEmpty()) {
                            Text(
                                "Pesquisar".tr(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                (if (state.query.isBlank()) state.recents else state.results).firstOrNull()?.let {
                                    onOpenItem(it, if (state.query.isBlank()) state.recents else state.results)
                                }
                            }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar".tr())
                    }
                }

                // Separadores
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(SEARCH_TABS, key = { it.slug ?: "all" }) { tab ->
                        val locked = tab.needsBackup && !mobileBackupEnabled
                        val active = state.tab == tab.slug
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable {
                                    if (locked) lockedHint = true
                                    else { lockedHint = false; onTabChange(tab.slug) }
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            if (locked) Icon(
                                Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                tab.label.tr(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                color = when {
                                    locked -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    active -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
                if (lockedHint) Text(
                    "O Backup Mobile está desligado".tr(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
                if (state.query.isNotBlank() && state.chips.isNotEmpty()) Text(
                    "Filtros".tr() + ": " + state.chips.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                if (state.loading || (state.query.isBlank() && state.recentsLoading)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                }

                val showRecents = state.query.isBlank()
                val list = if (showRecents) state.recents else state.results
                val busy = if (showRecents) state.recentsLoading else state.loading
                val tabLabel = SEARCH_TABS.firstOrNull { it.slug == state.tab }?.label?.tr().orEmpty()

                if (list.isEmpty() && !busy) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                    ) {
                        Text(
                            when {
                                showRecents && state.tab != null -> "Sem itens recentes em %s".tr().format(tabLabel)
                                showRecents -> "Sem itens recentes".tr()
                                state.query.trim().length < 2 && state.chips.isEmpty() -> ""
                                state.tab != null -> "Sem resultados em %s".tr().format(tabLabel)
                                else -> "Nada encontrado".tr()
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.tab != null) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { onTabChange(null) }) { Text("Pesquisar em tudo".tr()) }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (showRecents) item(key = "recents-header") {
                            Text(
                                "Recentes".tr(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        items(list, key = { it.id }) { item ->
                            BrowseItemRow(
                                item = item,
                                onClick = { onOpenItem(item, list) },
                            )
                        }
                    }
                }
            }
        }
    }
}
