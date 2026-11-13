package co.golink.tester.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import co.golink.tester.domain.browse.BrowseItem

// Cor por família de ficheiro — a mesma tabela do FileIconThumbnail da web,
// para o mesmo ficheiro ter o mesmo ícone nos dois clientes.
private val FAMILIES: List<Pair<Color, Set<String>>> = listOf(
    Color(0xFFE5484D) to setOf("pdf"),
    Color(0xFF3B82F6) to setOf("doc", "docx", "odt", "rtf", "pages"),
    Color(0xFF16A34A) to setOf("xls", "xlsx", "ods", "csv", "tsv", "numbers"),
    Color(0xFFF97316) to setOf("ppt", "pptx", "odp", "key"),
    Color(0xFF8B5CF6) to setOf(
        "json", "xml", "yml", "yaml", "js", "ts", "jsx", "tsx", "vue", "php", "py", "rb", "go", "rs",
        "java", "kt", "c", "cpp", "h", "cs", "html", "htm", "css", "scss", "sql", "sh", "bat", "ini", "env", "toml",
    ),
    Color(0xFFD97706) to setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "iso", "dmg"),
    Color(0xFFDB2777) to setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus"),
    Color(0xFF4F46E5) to setOf("mp4", "mov", "avi", "mkv", "webm", "wmv", "m4v"),
    Color(0xFF0EA5E9) to setOf("jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "heic", "tif", "tiff", "avif", "psd", "ai"),
    Color(0xFF64748B) to setOf("txt", "md", "log", "rtx", "nfo"),
)
private val DEFAULT_COLOR = Color(0xFF94A3B8)

/**
 * Extensão do ficheiro: a do nome é mais fiável que o mimetype (que às vezes
 * chega como "vnd.openxmlformats-…"); cai para o mimetype quando não há.
 */
internal fun fileExtension(name: String, mimetype: String?): String {
    val fromName = Regex("\\.([a-z0-9]{1,8})$", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)
    val fromMime = mimetype?.split('/', '.', '+', '-')?.lastOrNull()
    return (fromName ?: fromMime ?: "").lowercase()
}

/**
 * Ícone de documento: folha com o canto dobrado, tingida pela cor da família,
 * e a extensão numa etiqueta. Desenhado numa grelha 48×64 (a mesma do SVG da
 * web) e escalado ao tamanho pedido — a etiqueta acompanha sempre.
 */
@Composable
fun FileTypeIcon(name: String, mimetype: String?, modifier: Modifier = Modifier) {
    val ext = remember(name, mimetype) { fileExtension(name, mimetype) }
    val label = remember(ext) { ext.ifBlank { "file" }.take(4).uppercase() }
    val color = remember(ext) { FAMILIES.firstOrNull { ext in it.second }?.first ?: DEFAULT_COLOR }
    val measurer = rememberTextMeasurer()

    Canvas(modifier = modifier.aspectRatio(48f / 64f)) {
        val unit = size.width / 48f

        scale(unit, unit, pivot = Offset.Zero) {
            // Folha (contorno com o canto cortado)
            val sheet = Path().apply {
                moveTo(8f, 0f)
                lineTo(30f, 0f)
                lineTo(48f, 18f)
                lineTo(48f, 56f)
                quadraticBezierTo(48f, 64f, 40f, 64f)
                lineTo(8f, 64f)
                quadraticBezierTo(0f, 64f, 0f, 56f)
                lineTo(0f, 8f)
                quadraticBezierTo(0f, 0f, 8f, 0f)
                close()
            }
            drawPath(sheet, color.copy(alpha = 0.13f))

            // Canto dobrado
            val fold = Path().apply {
                moveTo(30f, 0f)
                lineTo(30f, 12f)
                quadraticBezierTo(30f, 18f, 36f, 18f)
                lineTo(48f, 18f)
                close()
            }
            drawPath(fold, color.copy(alpha = 0.35f))

            // Etiqueta
            drawRoundRect(color, topLeft = Offset(5f, 37f), size = Size(38f, 17f), cornerRadius = CornerRadius(4.5f))
        }

        // Texto fora do scale: medido em px reais para ficar nítido.
        val fontPx = (if (label.length > 3) 8f else 9.5f) * unit
        val layout = measurer.measure(
            label,
            TextStyle(
                color = Color.White,
                fontSize = (fontPx / density / fontScale).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (0.3f * unit / density / fontScale).sp,
            ),
        )
        drawText(
            layout,
            topLeft = Offset(
                24f * unit - layout.size.width / 2f,
                45.5f * unit - layout.size.height / 2f,
            ),
        )
    }
}

@Composable
fun FileTypeIcon(item: BrowseItem.File, modifier: Modifier = Modifier) {
    FileTypeIcon(item.name, item.mimetype, modifier)
}
