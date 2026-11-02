package co.golink.tester.ui.screens.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.ui.i18n.tr

/**
 * Anexar do Drive (DrivePickerPopup.vue): navega pastas, escolhe vários ficheiros.
 * Com [onPickItems] (Legacy) também se escolhem pastas inteiras e é esse callback que recebe.
 */
@Composable
fun DrivePickerDialog(
    exclude: Set<String>,
    load: suspend (String?) -> Result<List<BrowseItem>>,
    onDismiss: () -> Unit,
    onPick: (List<BrowseItem.File>) -> Unit = {},
    title: String = "Anexar do Drive".tr(),
    confirmLabel: (Int) -> String = { n -> if (n == 0) "Anexar".tr() else "Anexar (%d)".tr().format(n) },
    onPickItems: ((List<BrowseItem>) -> Unit)? = null,
) {
    val pickFolders = onPickItems != null
    // Caminho: (id, nome); null = raiz.
    val path = remember { mutableStateListOf<Pair<String?, String>>(null to "O meu drive".tr()) }
    val selected = remember { mutableStateMapOf<String, BrowseItem>() }
    // Pastas acima de cada escolha: escolher uma pasta absorve o que já se tinha escolhido lá dentro.
    val ancestors = remember { mutableMapOf<String, List<String>>() }
    var items by remember { mutableStateOf<List<BrowseItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    val current = path.last()
    // Dentro de uma pasta já escolhida tudo vai com ela: escolher à parte duplicava itens.
    val covered = pickFolders && path.any { it.first != null && it.first in selected }

    fun toggle(item: BrowseItem, on: Boolean) {
        if (covered) return
        if (!on) {
            selected.remove(item.id)
            ancestors.remove(item.id)
            return
        }
        if (item is BrowseItem.Folder) {
            ancestors.filterValues { item.id in it }.keys.forEach { selected.remove(it); ancestors.remove(it) }
        }
        selected[item.id] = item
        ancestors[item.id] = path.mapNotNull { it.first }
    }

    LaunchedEffect(current.first) {
        loading = true
        failed = false
        filter = ""
        load(current.first)
            .onSuccess { items = it }
            .onFailure { items = emptyList(); failed = true }
        loading = false
    }

    BackHandler { if (path.size > 1) path.removeAt(path.lastIndex) else onDismiss() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    IconButton(onClick = { if (path.size > 1) path.removeAt(path.lastIndex) else onDismiss() }) {
                        Icon(if (path.size > 1) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Close, contentDescription = null)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            path.joinToString(" / ") { it.second }, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // No topo (como o Guardar do editor): em baixo ficava atrás da barra de navegação.
                    Button(
                        onClick = {
                            val picked = selected.values.toList()
                            if (onPickItems != null) onPickItems(picked) else onPick(picked.filterIsInstance<BrowseItem.File>())
                        },
                        enabled = selected.isNotEmpty(),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(confirmLabel(selected.size), fontWeight = FontWeight.SemiBold)
                    }
                }
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    singleLine = true,
                    placeholder = { Text("Pesquisar".tr()) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp)) else Spacer(Modifier.height(2.dp))

                val shown = items.filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) }
                    .filter { it !is BrowseItem.File || it.id !in exclude }
                    .sortedWith(compareBy<BrowseItem> { it !is BrowseItem.Folder }.thenBy { it.name.lowercase() })
                LazyColumn(Modifier.weight(1f)) {
                    if (shown.isEmpty() && !loading) {
                        item {
                            Text(
                                if (failed) "Não foi possível concluir. Tenta de novo.".tr() else "Pasta vazia".tr(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                    items(shown, key = { it.id }) { item ->
                        when (item) {
                            is BrowseItem.Folder -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { path.add(item.id to item.name) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(14.dp))
                                Text(item.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (pickFolders) {
                                    Checkbox(checked = covered || item.id in selected, enabled = !covered, onCheckedChange = { toggle(item, it) })
                                }
                            }
                            is BrowseItem.File -> {
                                val on = covered || item.id in selected
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                        .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.07f) else Color.Transparent)
                                        .clickable(enabled = !covered) { toggle(item, !on) }
                                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                ) {
                                    FileBadge(item.name, item.mimetype, 32.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        item.filesize?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                    Checkbox(checked = on, enabled = !covered, onCheckedChange = { toggle(item, it) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
