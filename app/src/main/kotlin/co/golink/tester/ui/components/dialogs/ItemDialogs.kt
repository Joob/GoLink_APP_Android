package co.golink.tester.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.files.FolderAppearance
import co.golink.tester.domain.files.OfficeFormat
import co.golink.tester.domain.files.OfficeKind
import co.golink.tester.domain.files.createFormats
import co.golink.tester.domain.files.TEXT_EXTENSIONS
import co.golink.tester.domain.files.VISIBLE_TEXT_EXTENSIONS
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.theme.Danger

/** Cores de pasta (iguais a helpers/folderColors.js da web). null = tema. */
val FOLDER_COLORS: List<String?> = listOf(
    null, "#3b82f6", "#6366f1", "#8b5cf6", "#ec4899", "#ef4444",
    "#f97316", "#f59e0b", "#84cc16", "#14b8a6", "#0ea5e9", "#64748b",
)

private val FOLDER_EMOJIS = listOf(
    "📁", "📂", "⭐", "❤️", "🔥", "✅", "📌", "📷", "🎬", "🎵", "📄", "📊",
    "💼", "🏠", "✈️", "🎓", "💡", "🔒", "🛒", "🎮", "📚", "🧾", "🏖️", "👶",
)

private val DocBlue = Color(0xFF3B82F6)
private val SheetGreen = Color(0xFF16A34A)

private fun hex(color: String?): Color? = color?.removePrefix("#")?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }

/** Grelha 6×2 de cores (círculos distribuídos; o escolhido com ✓ e anel). */
@Composable
private fun ColorSwatches(selected: String?, onSelect: (String?) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        FOLDER_COLORS.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                row.forEach { c ->
                    val color = hex(c) ?: primary
                    val active = c == selected
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .then(if (active) Modifier.border(2.dp, color, CircleShape) else Modifier)
                            .padding(if (active) 4.dp else 2.dp)
                            .background(color, CircleShape)
                            .clickable { onSelect(c) },
                    ) {
                        if (active) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/** Grelha de emojis (7 colunas); o primeiro "Aa" = sem emoji (fica o ícone da pasta). */
@Composable
private fun EmojiGrid(selected: String?, onSelect: (String?) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val cells: List<String?> = listOf<String?>(null) + FOLDER_EMOJIS
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        cells.chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { e ->
                    val active = if (e == null) selected.isNullOrBlank() else e == selected
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) primary.copy(alpha = 0.10f) else Color.Transparent)
                            .then(if (active) Modifier.border(1.5.dp, primary, RoundedCornerShape(10.dp)) else Modifier)
                            .clickable { onSelect(e) },
                    ) {
                        if (e == null) {
                            Text("Aa", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (active) primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(e, fontSize = 20.sp)
                        }
                    }
                }
                repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Ícone do cabeçalho das pastas (muda ao vivo com a cor/emoji). */
@Composable
private fun FolderHeaderIcon(color: String?, emoji: String?) {
    val tint = hex(color) ?: MaterialTheme.colorScheme.primary
    if (!emoji.isNullOrBlank()) Text(emoji, fontSize = 24.sp)
    else Icon(Icons.Filled.Folder, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
}

/** Cor | Emoji (controlo segmentado à direita do rótulo "Aparência"). */
@Composable
private fun AppearanceToggle(useEmoji: Boolean, onChange: (Boolean) -> Unit) {
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
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onChange(isEmoji) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/** Secção "Aparência" partilhada por Nova pasta e Editar pasta. */
@Composable
private fun AppearanceSection(
    useEmoji: Boolean,
    onUseEmoji: (Boolean) -> Unit,
    color: String?,
    onColor: (String?) -> Unit,
    emoji: String?,
    onEmoji: (String?) -> Unit,
) {
    FieldLabel("Aparência".tr(), Modifier.padding(top = 6.dp)) { AppearanceToggle(useEmoji, onUseEmoji) }
    if (useEmoji) {
        EmojiGrid(emoji, onEmoji)
        HelperText("Escolhe um emoji ou mantém o ícone da pasta".tr())
    } else {
        ColorSwatches(color, onColor)
        HelperText("A cor é usada no ícone da pasta".tr())
    }
}

private fun folderAppearance(useEmoji: Boolean, color: String?, emoji: String?): FolderAppearance =
    if (useEmoji && !emoji.isNullOrBlank()) FolderAppearance(null, emoji) else FolderAppearance(color, null)

/** Nova pasta: nome + cor ou emoji (o ícone do topo muda ao vivo). */
@Composable
fun CreateFolderDialog(location: String, onDismiss: () -> Unit, onCreate: (String, String?, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var useEmoji by remember { mutableStateOf(false) }
    var color by remember { mutableStateOf<String?>(null) }
    var emoji by remember { mutableStateOf<String?>(null) }
    val finalName = name.trim().ifEmpty { "Nova pasta".tr() }
    val appearance = folderAppearance(useEmoji, color, emoji)
    val tint = hex(appearance.color) ?: MaterialTheme.colorScheme.primary

    CreateSheetScaffold(
        title = "Nova pasta".tr(),
        subtitle = inLocation(location),
        icon = { FolderHeaderIcon(appearance.color, appearance.emoji) },
        iconBackground = tint.copy(alpha = 0.12f),
        onDismiss = onDismiss,
        confirmText = "Criar pasta".tr(),
        onConfirm = { onCreate(finalName, appearance.color, appearance.emoji); onDismiss() },
    ) {
        FieldLabel("Nome".tr())
        NameField(name, { name = it }, placeholder = "Nova pasta".tr())
        HelperText("Vai ser criado como %s".tr().format(finalName))
        AppearanceSection(useEmoji, { useEmoji = it }, color, { color = it }, emoji, { emoji = it })
    }
}

/** Novo ficheiro de texto: nome + 5 formatos numa linha + "abrir no editor". */
@Composable
fun CreateTextFileDialog(location: String, onDismiss: () -> Unit, onCreate: (String, Boolean) -> Unit) {
    val labels = mapOf("txt" to "Texto".tr(), "md" to "Markdown", "json" to "Dados".tr(), "csv" to "Tabela".tr(), "log" to "Registo".tr())
    val hints = mapOf(
        "txt" to "Texto simples".tr(),
        "md" to "Markdown · títulos, listas e ligações".tr(),
        "json" to "Dados estruturados".tr(),
        "csv" to "Tabela · valores separados por vírgulas".tr(),
        "log" to "Registo · uma entrada por linha".tr(),
    )
    val types = VISIBLE_TEXT_EXTENSIONS.map { FormatOption(it, labels[it] ?: it) }
    var name by remember { mutableStateOf("") }
    var ext by remember { mutableStateOf("txt") }
    var openEditor by remember { mutableStateOf(true) }
    // "notas.md" escolhe a extensão ao escrever; o cartão escolhido depois manda
    // (como na web). O nome final fica sempre sem a extensão de texto escrita.
    val trimmed = name.trim().trimEnd('.')
    val typedExt = trimmed.substringAfterLast('.', "").lowercase().takeIf { t -> t in TEXT_EXTENSIONS }
    val base = (if (typedExt != null) trimmed.substringBeforeLast('.') else trimmed).trim().ifEmpty { "Sem nome".tr() }
    val filename = "$base.$ext"

    CreateSheetScaffold(
        title = "Novo ficheiro de texto".tr(),
        subtitle = inLocation(location),
        icon = { Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) },
        iconBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        onDismiss = onDismiss,
        confirmText = "Criar ficheiro".tr(),
        onConfirm = { onCreate(filename, openEditor); onDismiss() },
    ) {
        FieldLabel("Nome".tr())
        NameField(
            value = name,
            onValueChange = { name = it; name.substringAfterLast('.', "").lowercase().let { t -> if (types.any { it.ext == t }) ext = t } },
            placeholder = "notas".tr(),
            // Sem sufixo quando o nome já acaba nele ("notas.md.md").
            suffixExt = if (typedExt == ext) null else ext,
        )
        HelperText("Vai ser criado como %s".tr().format(filename))
        FieldLabel("Formato".tr())
        FormatCards(types, ext) { ext = it }
        hints[ext]?.let { FormatHint(it) }
        OpenEditorSwitch(openEditor) { openEditor = it }
    }
}

/** Novo documento ou folha de cálculo, no formato escolhido (lembrado), aberto depois no editor. */
@Composable
fun CreateDocumentDialog(
    kind: OfficeKind,
    location: String,
    onDismiss: () -> Unit,
    onCreate: (String, OfficeFormat, Boolean) -> Unit,
    /** false com E2E: odt converte-se no servidor e não abriria no editor. */
    allowServerConverted: Boolean = true,
) {
    val doc = kind == OfficeKind.DOC
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("office_editor", android.content.Context.MODE_PRIVATE) }
    val formats = remember(kind, allowServerConverted) { createFormats(kind).filter { allowServerConverted || !it.serverConverted } }
    var format by remember {
        mutableStateOf(formats.firstOrNull { it.extension == prefs.getString("create_format_${kind.name}", null) } ?: formats.first())
    }
    var name by remember { mutableStateOf("") }
    var openEditor by remember { mutableStateOf(true) }
    val placeholder = (if (doc) "Documento sem título" else "Folha sem título").tr()
    val base = name.trim().let { typed ->
        formats.firstOrNull { typed.lowercase().endsWith(".${it.extension}") }?.let { typed.dropLast(it.extension.length + 1) } ?: typed
    }.trim().ifEmpty { placeholder }
    val filename = "$base.${format.extension}"
    val accent = if (doc) DocBlue else SheetGreen

    CreateSheetScaffold(
        title = (if (doc) "Novo documento" else "Nova folha de cálculo").tr(),
        subtitle = inLocation(location),
        icon = { Icon(if (doc) Icons.Outlined.Description else Icons.Outlined.TableChart, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp)) },
        iconBackground = accent.copy(alpha = 0.12f),
        onDismiss = onDismiss,
        confirmText = (if (doc) "Criar documento" else "Criar folha de cálculo").tr(),
        onConfirm = {
            prefs.edit().putString("create_format_${kind.name}", format.extension).apply()
            onCreate(filename, format, openEditor)
            onDismiss()
        },
    ) {
        FieldLabel("Nome".tr())
        NameField(name, { name = it }, placeholder = placeholder, suffixExt = format.extension)
        HelperText("Vai ser criado como %s".tr().format(filename))
        if (formats.size == 1) {
            // Um só formato: só informa (sem cartão solto para escolher).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp),
            ) {
                ExtChip(format.extension, highlighted = true)
                Spacer(Modifier.width(12.dp))
                Text(officeFormatInfo(format.extension), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            FieldLabel("Formato".tr())
            FormatCards(formats.map { FormatOption(it.extension, officeFormatLabel(it.extension)) }, format.extension) { ext ->
                formats.firstOrNull { it.extension == ext }?.let { format = it }
            }
            FormatHint(officeFormatHint(format.extension))
        }
        OpenEditorSwitch(openEditor) { openEditor = it }
    }
}

private fun officeFormatLabel(extension: String): String = when (extension) {
    "docx" -> "Word"
    "xlsx" -> "Excel"
    "odt", "ods" -> "OpenDocument"
    "csv" -> "CSV"
    else -> extension.uppercase()
}

private fun officeFormatHint(extension: String): String = when (extension) {
    "docx" -> "Word · o mais compatível"
    "xlsx" -> "Excel · o mais compatível"
    "odt", "ods" -> "OpenDocument · melhor para LibreOffice"
    "csv" -> "Só dados · uma folha"
    else -> ""
}.tr()

private fun officeFormatInfo(extension: String): String = when (extension) {
    "docx" -> "Documento Word · abre no Word, LibreOffice e Google Docs"
    "xlsx" -> "Folha Excel · abre no Excel, LibreOffice e Google Sheets"
    else -> officeFormatHint(extension)
}.tr()

/** Editar item: mesmo componente de criar; Guardar só ativo com alterações. */
@Composable
fun EditItemDialog(item: BrowseItem, onDismiss: () -> Unit, onSave: (String, FolderAppearance?) -> Unit, location: String? = null) {
    val folder = item as? BrowseItem.Folder
    var name by remember { mutableStateOf(item.name) }
    var useEmoji by remember { mutableStateOf(!folder?.emoji.isNullOrBlank()) }
    var color by remember { mutableStateOf(folder?.color) }
    var emoji by remember { mutableStateOf(folder?.emoji?.takeIf { it.isNotBlank() }) }
    val appearance = folder?.let { folderAppearance(useEmoji, color, emoji) }
    val folderChanged = folder != null && appearance != FolderAppearance(folder.color, folder.emoji?.takeIf { it.isNotBlank() })
    val nameChanged = name.trim() != item.name
    val changed = nameChanged || folderChanged
    val tint = hex(appearance?.color) ?: MaterialTheme.colorScheme.primary

    CreateSheetScaffold(
        title = (if (folder != null) "Editar pasta" else "Editar ficheiro").tr(),
        subtitle = if (location != null) inLocation(location) else item.name,
        icon = {
            if (folder != null) FolderHeaderIcon(appearance?.color, appearance?.emoji)
            else Icon(Icons.Outlined.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        },
        iconBackground = if (folder != null) tint.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        onDismiss = onDismiss,
        confirmText = "Guardar".tr(),
        confirmEnabled = changed && name.isNotBlank(),
        onConfirm = {
            onSave(name.trim(), if (folderChanged) appearance else null)
            onDismiss()
        },
    ) {
        FieldLabel("Nome".tr())
        NameField(name, { name = it }, placeholder = item.name, clearable = true)
        HelperText(
            when {
                name.isBlank() -> "O nome não pode ficar vazio".tr()
                !changed -> "Ainda sem alterações".tr()
                nameChanged -> "Vai ser guardado como %s".tr().format(name.trim())
                else -> "Aparência alterada".tr()
            },
            color = if (name.isBlank()) Danger else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (folder != null) {
            AppearanceSection(useEmoji, { useEmoji = it }, color, { color = it }, emoji, { emoji = it })
        }
    }
}

private data class LinkRow(val index: Int, val valid: Boolean, val url: String?, val name: String, val meta: String, val badge: String, val dupName: Boolean)

// Cor do selo pela extensão (PDF vermelho, vídeo roxo, imagem verde…).
@Composable
private fun badgeColor(badge: String): Color = when (badge) {
    "PDF" -> Color(0xFFDC2626)
    "MP4", "MOV", "MKV", "WEBM", "AVI" -> Color(0xFF7C3AED)
    "JPG", "JPEG", "PNG", "GIF", "WEBP", "HEIC" -> SheetGreen
    "MP3", "WAV", "FLAC", "M4A" -> Color(0xFFDB2777)
    "ZIP", "RAR", "7Z" -> Color(0xFFD97706)
    "DOC", "DOCX" -> DocBlue
    else -> MaterialTheme.colorScheme.primary
}

/** Importar a partir de links: um por linha, cada link verificado num cartão. */
@Composable
fun RemoteUploadDialog(location: String, onDismiss: () -> Unit, onImport: (List<String>) -> Unit) {
    var text by remember { mutableStateOf("") }
    val rows = remember(text) { parseLinks(text) }
    val valid = rows.filter { it.valid }.mapNotNull { it.url }
    val tooMany = valid.size > MAX_LINKS
    val clipboard = LocalClipboardManager.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    CreateSheetScaffold(
        title = "Importar a partir de links".tr(),
        subtitle = inLocation(location, "Guardado em"),
        icon = { Icon(Icons.Outlined.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp)) },
        iconBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        onDismiss = onDismiss,
        confirmText = if (valid.isEmpty()) "Importar".tr() else (if (valid.size == 1) "Importar 1 ficheiro".tr() else "Importar %d ficheiros".tr().format(valid.size)),
        confirmEnabled = valid.isNotEmpty() && !tooMany,
        onConfirm = { onImport(valid); onDismiss() },
    ) {
        FieldLabel("Links dos ficheiros".tr() + " · " + "um por linha".tr()) {
            Text(
                "Colar".tr(),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .clickable {
                        val pasted = clipboard.getText()?.text?.trim().orEmpty()
                        if (pasted.isNotEmpty()) text = if (text.isBlank()) pasted else text.trimEnd() + "\n" + pasted
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("https://exemplo.com/ficheiro.pdf", fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
            textStyle = MaterialTheme.typography.bodyMedium,
            minLines = 4,
            maxLines = 8,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        rows.forEach { r ->
            val danger = !r.valid
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (danger) Danger.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                val bc = if (danger) Danger else badgeColor(r.badge)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(34.dp).background(bc.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                ) {
                    Text(r.badge, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = bc, maxLines = 1)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (danger) Danger else MaterialTheme.colorScheme.onSurface)
                    Text(
                        if (danger || r.dupName) r.meta else "de %s".tr().format(r.meta),
                        fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = when {
                            danger -> Danger
                            r.dupName -> Color(0xFFB45309)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(30.dp).clip(CircleShape).clickable {
                        text = text.lines().filterIndexed { i, _ -> i != r.index }.joinToString("\n")
                    },
                ) { Icon(Icons.Filled.Close, contentDescription = "Remover".tr(), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp)) }
            }
        }
        if (tooMany) HelperText("Máximo de %d links de cada vez.".tr().format(MAX_LINKS), color = Danger)
        // Aviso: o servidor descarrega o ficheiro (passa pelo GoLink antes de ser cifrado).
        val amberText = if (dark) Color(0xFFFCD34D) else Color(0xFF92400E)
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (dark) Color(0xFF451A03).copy(alpha = 0.55f) else Color(0xFFFFFBEB))
                .border(1.dp, if (dark) Color(0xFF78350F) else Color(0xFFFDE68A), RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = amberText, modifier = Modifier.padding(top = 1.dp).size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Os links têm de abrir o ficheiro diretamente, sem login. O nosso servidor descarrega-os, por isso o ficheiro passa pelo GoLink antes de ser cifrado.".tr(),
                fontSize = 12.sp,
                color = amberText,
                lineHeight = 17.sp,
            )
        }
        Spacer(Modifier.height(2.dp))
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
            return@mapIndexedNotNull LinkRow(index, false, null, line, "Não é um link · tem de começar por https://".tr(), "!", false)
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
