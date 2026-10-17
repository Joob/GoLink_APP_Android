package co.golink.tester.ui.screens.viewer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.theme.LocalDarkTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

// Acima disto mostra-se sem cores (milhares de linhas com spans pesam).
private const val HIGHLIGHT_MAX_CHARS = 200_000

private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }
private val TOKEN = Regex("(\"(?:[^\"\\\\]|\\\\.)*\")(\\s*:)?|(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)|\\b(true|false|null)\\b")

private data class CodeColors(val key: Color, val str: Color, val num: Color, val lit: Color, val text: Color, val gutter: Color)

@Composable
private fun codeColors(): CodeColors = if (LocalDarkTheme.current) {
    CodeColors(Color(0xFF93C5FD), Color(0xFF86EFAC), Color(0xFFFDBA74), Color(0xFFC4B5FD), Color(0xFFE5E7EB), Color(0xFF4B5563))
} else {
    CodeColors(Color(0xFF2563EB), Color(0xFF15803D), Color(0xFFC2410C), Color(0xFF7C3AED), Color(0xFF1F2937), Color(0xFFB6BCC6))
}

private fun highlightJson(line: String, c: CodeColors): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in TOKEN.findAll(line)) {
        append(line.substring(last, m.range.first))
        val (str, colon, num, lit) = m.destructured
        when {
            str.isNotEmpty() -> {
                withStyle(SpanStyle(color = if (colon.isNotEmpty()) c.key else c.str)) { append(str) }
                append(colon)
            }
            num.isNotEmpty() -> withStyle(SpanStyle(color = c.num)) { append(num) }
            else -> withStyle(SpanStyle(color = c.lit)) { append(lit) }
        }
        last = m.range.last + 1
    }
    append(line.substring(last))
}

private fun markMatches(line: String, q: String): AnnotatedString = buildAnnotatedString {
    val lower = line.lowercase()
    var last = 0
    while (true) {
        val idx = lower.indexOf(q, last)
        if (idx < 0) break
        append(line.substring(last, idx))
        withStyle(SpanStyle(background = Color(0x8CFACC15))) { append(line.substring(idx, idx + q.length)) }
        last = idx + q.length
    }
    append(line.substring(last))
}

private fun formatJsonOrNull(text: String): String? = runCatching {
    prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(text))
}.getOrNull()

/**
 * Leitor/editor de ficheiros de texto (como na web): barra com Formatar,
 * Quebrar linhas, Copiar e Procurar; números de linha; cores no JSON;
 * rodapé com JSON válido, linhas, tipo e encriptação.
 */
@Composable
internal fun TextReader(
    name: String,
    content: String?,
    loading: Boolean,
    error: String?,
    encrypted: Boolean,
    editing: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
) {
    val ext = name.substringAfterLast('.', "").lowercase()
    val isJson = ext == "json"
    val colors = codeColors()
    var wrap by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(name) { mutableStateOf("") }
    var formatted by remember(name, content) { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    androidx.compose.runtime.LaunchedEffect(copied) {
        if (copied) { kotlinx.coroutines.delay(1500); copied = false }
    }

    val shown = if (editing) draft else formatted ?: content.orEmpty()
    val jsonValid = remember(shown, isJson) { !isJson || runCatching { Json.parseToJsonElement(shown) }.isSuccess }
    val lines = remember(shown) { shown.split('\n') }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator()
            error != null -> Text("Erro: $error")
            content == null && !editing -> Text("Sem conteúdo".tr())
            else -> Surface(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column {
                    // Barra
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (editing) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                    ) {
                        if (isJson) Tool("Formatar".tr(), enabled = jsonValid) {
                            formatJsonOrNull(shown)?.let { pretty ->
                                if (editing) onDraftChange(pretty) else formatted = pretty.takeIf { it != content }
                            }
                        }
                        Tool("Quebrar linhas".tr(), active = wrap) { wrap = !wrap }
                        Tool(if (copied) "Copiado".tr() else "Copiar".tr()) {
                            clipboard.setText(AnnotatedString(shown))
                            copied = true
                        }
                        if (!editing) {
                            Spacer(Modifier.width(6.dp))
                            SearchField(query, onChange = { query = it })
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                    Box(Modifier.weight(1f)) {
                        if (editing) {
                            EditorWithGutter(draft, onDraftChange, wrap, colors)
                        } else {
                            val q = query.trim().lowercase()
                            val colorize = isJson && shown.length <= HIGHLIGHT_MAX_CHARS
                            val hScroll = rememberScrollState()
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (wrap) Modifier else Modifier.horizontalScroll(hScroll)),
                            ) {
                                itemsIndexed(lines) { i, line ->
                                    val hit = q.isNotEmpty() && line.lowercase().contains(q)
                                    Row(Modifier.background(if (hit) Color(0x1FFACC15) else Color.Transparent)) {
                                        Text(
                                            "${i + 1}",
                                            style = codeStyle(colors.gutter, 11.sp),
                                            modifier = Modifier.width(40.dp).padding(end = 10.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                        )
                                        Text(
                                            when {
                                                hit -> markMatches(line, q)
                                                colorize -> highlightJson(line, colors)
                                                else -> AnnotatedString(line)
                                            },
                                            style = codeStyle(colors.text),
                                            softWrap = wrap,
                                            modifier = if (wrap) Modifier.weight(1f).padding(end = 12.dp) else Modifier.padding(end = 16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Rodapé
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        if (isJson) {
                            Text(
                                "● " + (if (jsonValid) "JSON válido" else "JSON inválido").tr(),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (jsonValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        val n = lines.size
                        Text(
                            (if (n == 1) "1 linha" else "%d linhas").tr().let { if (n == 1) it else it.format(n) } + " · " + typeLabel(ext),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        if (encrypted) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                (if (editing) "Volta a ser encriptado ao guardar" else "Encriptado").tr(),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun typeLabel(ext: String) = when (ext) {
    "json" -> "JSON"
    "md" -> "Markdown"
    "csv" -> "CSV"
    "log" -> "Log"
    "", "txt" -> "Texto".tr()
    else -> ext.uppercase()
} + " · UTF-8"

private fun codeStyle(color: Color, size: androidx.compose.ui.unit.TextUnit = 12.5.sp) =
    TextStyle(fontFamily = FontFamily.Monospace, fontSize = size, lineHeight = 20.sp, color = color)

@Composable
private fun Tool(label: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            active -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(min = 150.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Box {
            if (value.isEmpty()) Text("Procurar no ficheiro".tr(), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.widthIn(min = 110.dp),
            )
        }
    }
}

// Editor com os números de linha ao lado (scroll vertical partilhado).
@Composable
private fun EditorWithGutter(draft: String, onChange: (String) -> Unit, wrap: Boolean, colors: CodeColors) {
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val count = remember(draft) { draft.count { it == '\n' } + 1 }
    Row(Modifier.fillMaxSize().verticalScroll(vScroll)) {
        Column(Modifier.width(40.dp).padding(end = 10.dp, top = 0.dp)) {
            for (n in 1..count) {
                Text("$n", style = codeStyle(colors.gutter, 11.sp), modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        BasicTextField(
            value = draft,
            onValueChange = onChange,
            textStyle = codeStyle(colors.text),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .then(if (wrap) Modifier else Modifier.horizontalScroll(hScroll))
                .padding(end = 12.dp),
        )
    }
}
