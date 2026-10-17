package co.golink.tester.ui.components.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.files.FolderAppearance
import co.golink.tester.ui.i18n.tr

/** Cores de pasta (iguais a helpers/folderColors.js da web). null = tema. */
val FOLDER_COLORS: List<String?> = listOf(
    null, "#3b82f6", "#6366f1", "#8b5cf6", "#ec4899", "#ef4444",
    "#f97316", "#f59e0b", "#84cc16", "#14b8a6", "#0ea5e9", "#64748b",
)

private val FOLDER_EMOJIS = listOf(
    "📁", "📂", "⭐", "❤️", "🔥", "✅", "📌", "📷", "🎬", "🎵", "📄", "📊",
    "💼", "🏠", "✈️", "🎓", "💡", "🔒", "🛒", "🎮", "📚", "🧾", "🏖️", "👶",
)

private fun hex(color: String?): Color? = color?.removePrefix("#")?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatches(selected: String?, onSelect: (String?) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FOLDER_COLORS.forEach { c ->
            val color = hex(c) ?: primary
            val active = c == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .then(if (active) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .padding(3.dp)
                    .background(color, CircleShape)
                    .clickable { onSelect(c) },
            ) {
                if (active) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun FolderPreview(color: String?, emoji: String?) {
    val tint = hex(color) ?: MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(48.dp).background(tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
    ) {
        if (!emoji.isNullOrBlank()) Text(emoji, fontSize = 24.sp)
        else Icon(Icons.Filled.Folder, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
    }
}

/** Nova pasta: pré-visualização + nome + cor (o emoji escolhe-se depois). */
@Composable
fun CreateFolderDialog(location: String, onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf<String?>(null) }
    DialogScaffold(
        title = "Nova pasta".tr(),
        subtitle = "Em %s".tr().format(location),
        onDismiss = onDismiss,
        confirmText = "Criar pasta".tr(),
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onCreate(name.trim(), color); onDismiss() },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FolderPreview(color, null)
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome da pasta".tr()) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            )
        }
        SectionLabel("Cor".tr(), "podes adicionar um emoji depois".tr())
        ColorSwatches(color) { color = it }
    }
}

private data class TextType(val ext: String, val label: String)

/** Novo ficheiro de texto: nome + extensão + tipos + "abrir o editor". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateTextFileDialog(location: String, onDismiss: () -> Unit, onCreate: (String, Boolean) -> Unit) {
    val types = listOf(
        TextType("txt", "Texto".tr()), TextType("md", "Markdown"), TextType("json", "Dados".tr()),
        TextType("csv", "Tabela".tr()), TextType("log", "Registo".tr()),
    )
    var name by remember { mutableStateOf("") }
    var ext by remember { mutableStateOf("txt") }
    var openEditor by remember { mutableStateOf(true) }
    // "notas.md" escolhe a extensão; o nome fica sem ela.
    val typedExt = name.substringAfterLast('.', "").lowercase().takeIf { t -> types.any { it.ext == t } }
    val base = (if (typedExt != null) name.substringBeforeLast('.') else name).trim().ifEmpty { "Sem nome".tr() }
    val filename = "$base.${typedExt ?: ext}"

    DialogScaffold(
        title = "Novo ficheiro de texto".tr(),
        subtitle = "Em %s".tr().format(location),
        onDismiss = onDismiss,
        confirmText = "Criar ficheiro".tr(),
        onConfirm = { onCreate(filename, openEditor); onDismiss() },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; name.substringAfterLast('.', "").lowercase().let { t -> if (types.any { it.ext == t }) ext = t } },
            label = { Text("Nome do ficheiro".tr()) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            suffix = { Text(".$ext", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Vai ser criado como %s".tr().format(filename),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            types.forEach { t ->
                val active = (typedExt ?: ext) == t.ext
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { ext = t.ext },
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(".${t.ext}", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text(t.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { openEditor = !openEditor },
        ) {
            Checkbox(checked = openEditor, onCheckedChange = { openEditor = it })
            Text("Abrir o editor depois de criar".tr(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Editar item: nome; nas pastas também cor ou emoji (como "Editar pasta" na web). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditItemDialog(item: BrowseItem, onDismiss: () -> Unit, onSave: (String, FolderAppearance?) -> Unit) {
    val folder = item as? BrowseItem.Folder
    var name by remember { mutableStateOf(item.name) }
    var useEmoji by remember { mutableStateOf(!folder?.emoji.isNullOrBlank()) }
    var color by remember { mutableStateOf(folder?.color) }
    var emoji by remember { mutableStateOf(folder?.emoji) }
    val appearance = folder?.let { if (useEmoji && !emoji.isNullOrBlank()) FolderAppearance(null, emoji) else FolderAppearance(color, null) }
    val changed = name.trim() != item.name || (folder != null && appearance != FolderAppearance(folder.color, folder.emoji?.takeIf { it.isNotBlank() }))

    DialogScaffold(
        title = (if (folder != null) "Editar pasta" else "Editar item").tr(),
        subtitle = item.name,
        onDismiss = onDismiss,
        confirmText = "Guardar".tr(),
        confirmEnabled = changed && name.isNotBlank(),
        onConfirm = {
            val folderChanged = folder != null && appearance != FolderAppearance(folder.color, folder.emoji?.takeIf { it.isNotBlank() })
            onSave(name.trim(), if (folderChanged) appearance else null)
            onDismiss()
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (folder != null) {
                FolderPreview(if (useEmoji) null else color, if (useEmoji) emoji else null)
                Spacer(Modifier.width(12.dp))
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome".tr()) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            )
        }
        if (folder != null) {
            // Cor | Emoji (controlo segmentado)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(3.dp),
            ) {
                listOf(false to "Cor".tr(), true to "Emoji").forEach { (isEmoji, label) ->
                    val active = useEmoji == isEmoji
                    Text(
                        label,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { useEmoji = isEmoji }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            if (useEmoji) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FOLDER_EMOJIS.forEach { e ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (e == emoji) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable { emoji = e },
                        ) { Text(e, fontSize = 20.sp) }
                    }
                }
            } else {
                ColorSwatches(color) { color = it }
            }
        }
    }
}

private data class LinkRow(val index: Int, val valid: Boolean, val url: String?, val name: String, val meta: String, val badge: String, val dupName: Boolean)

/** Importar a partir de links: um por linha, pré-visualização, inválidos e repetidos. */
@Composable
fun RemoteUploadDialog(location: String, onDismiss: () -> Unit, onImport: (List<String>) -> Unit) {
    var text by remember { mutableStateOf("") }
    val rows = remember(text) { parseLinks(text) }
    val valid = rows.filter { it.valid }.mapNotNull { it.url }
    val invalid = rows.count { !it.valid }
    val tooMany = valid.size > MAX_LINKS

    DialogScaffold(
        title = "Importar a partir de links".tr(),
        subtitle = "Os ficheiros são guardados em %s".tr().format(location),
        onDismiss = onDismiss,
        confirmText = if (valid.isEmpty()) "Importar".tr() else (if (valid.size == 1) "Importar 1 ficheiro".tr() else "Importar %d ficheiros".tr().format(valid.size)),
        confirmEnabled = valid.isNotEmpty() && !tooMany,
        footerNote = when {
            invalid > 0 -> "Os links inválidos são ignorados.".tr()
            tooMany -> "Máximo de %d links de cada vez.".tr().format(MAX_LINKS)
            else -> null
        },
        onConfirm = { onImport(valid); onDismiss() },
    ) {
        SectionLabel("Links dos ficheiros".tr(), "Um link por linha".tr())
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("https://exemplo.com/ficheiro.pdf") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            minLines = 4,
            maxLines = 8,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Os links têm de abrir diretamente o ficheiro, sem pedir login.".tr(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (rows.isNotEmpty()) {
            val counts = buildList {
                add(if (valid.size == 1) "1 ficheiro".tr() else "%d ficheiros".tr().format(valid.size))
                if (invalid > 0) add(if (invalid == 1) "1 link inválido".tr() else "%d links inválidos".tr().format(invalid))
            }.joinToString(" · ")
            SectionLabel("Vão ser importados".tr() + " · " + counts)
            Surface(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column {
                    rows.forEach { r ->
                        val danger = !r.valid
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (danger) co.golink.tester.ui.theme.Danger.copy(alpha = 0.06f) else Color.Transparent)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(32.dp).background(
                                    if (danger) co.golink.tester.ui.theme.Danger.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp),
                                ),
                            ) {
                                Text(r.badge, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (danger) co.golink.tester.ui.theme.Danger else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (danger) co.golink.tester.ui.theme.Danger else MaterialTheme.colorScheme.onSurface)
                                Text(r.meta, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = when {
                                        danger -> co.golink.tester.ui.theme.Danger
                                        r.dupName -> Color(0xFFB45309)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    })
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).clickable {
                                    text = text.lines().filterIndexed { i, _ -> i != r.index }.joinToString("\n")
                                },
                            ) { Icon(Icons.Filled.Close, contentDescription = "Remover".tr(), modifier = Modifier.size(14.dp)) }
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_LINKS = 20

// Mesmas regras da web: http(s), domínio com ponto, nome = último segmento.
private fun parseLinks(text: String): List<LinkRow> {
    val seenUrls = mutableSetOf<String>()
    val seenNames = mutableSetOf<String>()
    return text.lines().mapIndexedNotNull { index, raw ->
        val line = raw.trim()
        if (line.isEmpty()) return@mapIndexedNotNull null
        val uri = runCatching { java.net.URI(line) }.getOrNull()
        val host = uri?.host
        val okScheme = uri?.scheme?.lowercase() in setOf("http", "https")
        if (uri == null || !okScheme || host == null || !host.contains('.')) {
            return@mapIndexedNotNull LinkRow(index, false, null, line, "Link inválido · tem de começar por https:// e apontar para um ficheiro".tr(), "!", false)
        }
        val segment = uri.path.orEmpty().split('/').lastOrNull { it.isNotEmpty() }.orEmpty()
        val name = runCatching { java.net.URLDecoder.decode(segment, "UTF-8") }.getOrDefault(segment).ifEmpty { host }
        val ext = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 4 }?.uppercase() ?: "URL"
        val cleanHost = host.removePrefix("www.")
        if (!seenUrls.add(line)) {
            return@mapIndexedNotNull LinkRow(index, false, null, name, "Link repetido · só é importado uma vez".tr() + " · " + cleanHost, ext, false)
        }
        val dup = !seenNames.add(name.lowercase())
        LinkRow(index, true, line, name, if (dup) "Nome repetido".tr() + " · " + cleanHost else cleanHost, ext, dup)
    }
}
