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

/** Anexar do Drive (DrivePickerPopup.vue): navega pastas, escolhe vários ficheiros. */
@Composable
fun DrivePickerDialog(
    exclude: Set<String>,
    load: suspend (String?) -> Result<List<BrowseItem>>,
    onDismiss: () -> Unit,
    onPick: (List<BrowseItem.File>) -> Unit,
) {
    // Caminho: (id, nome); null = raiz.
    val path = remember { mutableStateListOf<Pair<String?, String>>(null to "O meu drive".tr()) }
    val selected = remember { mutableStateMapOf<String, BrowseItem.File>() }
    var items by remember { mutableStateOf<List<BrowseItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    val current = path.last()

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
                        Text("Anexar do Drive".tr(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            path.joinToString(" / ") { it.second }, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // No topo (como o Guardar do editor): em baixo ficava atrás da barra de navegação.
                    Button(
                        onClick = { onPick(selected.values.toList()) },
                        enabled = selected.isNotEmpty(),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(if (selected.isEmpty()) "Anexar".tr() else "Anexar (%d)".tr().format(selected.size), fontWeight = FontWeight.SemiBold)
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
                                Text(item.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            is BrowseItem.File -> {
                                val on = item.id in selected
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                        .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.07f) else Color.Transparent)
                                        .clickable { if (on) selected.remove(item.id) else selected[item.id] = item }
                                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                ) {
                                    FileBadge(item.name, item.mimetype, 32.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        item.filesize?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                    Checkbox(checked = on, onCheckedChange = { if (it) selected[item.id] = item else selected.remove(item.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
