package co.golink.tester.ui.screens.legacy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.domain.legacy.LegacyItem
import co.golink.tester.ui.theme.LocalDarkTheme

// ---- kit do redesign (maquetas de 2026-11-08) ----

val LegacyHeroBrush = Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)))
val LegacyAlarmBrush = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706)))
val LegacyDangerBrush = Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFB91C1C)))
val LegacyIdleBrush = Brush.linearGradient(listOf(Color(0xFF64748B), Color(0xFF475569)))
val LegacyGreen = Color(0xFF10B981)
val LegacyAmber = Color(0xFFF59E0B)

/** Fundo cinza discreto (pílulas, segmentados). */
@Composable
fun legacyMuted(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (LocalDarkTheme.current) 0.08f else 0.05f)

/** Segmentado de topo: pílula cinza, segmento ativo em branco. */
@Composable
fun <T> LegacySegmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(legacyMuted())
            .padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .then(if (on) Modifier.shadow(1.dp, RoundedCornerShape(10.dp)) else Modifier)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 8.dp, horizontal = 6.dp),
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Chips de filtro (All 5 · Files 2 …): o ativo fica escuro. */
@Composable
fun <T> LegacyFilterChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, counts: Map<T, Int> = emptyMap()) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            val fg = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (on) Color.Transparent else MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                    .clickable { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg)
                counts[value]?.let { Text(" $it", fontSize = 12.sp, color = fg.copy(alpha = 0.6f)) }
            }
        }
    }
}

/** Rótulo de secção em versaletes (WHAT HAPPENS IF YOU STOP). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(legacyLocale()),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

/** Avatares sobrepostos dos destinatários. */
@Composable
fun StackedAvatars(people: List<Pair<String, String>>, size: Int = 22, max: Int = 3) {
    Row {
        people.take(max).forEachIndexed { i, (name, seed) ->
            Box(
                Modifier
                    .offset(x = (-6 * i).dp)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            ) { LegacyAvatar(name, seed, size) }
        }
        if (people.size > max) {
            Text("+${people.size - max}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.offset(x = (-6 * max + 4).dp).align(Alignment.CenterVertically))
        }
    }
}

/** Anel dos dias que faltam (hero do Overview). */
@Composable
fun RingProgress(progress: Float, value: String, caption: String, size: Int = 92) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size.dp)) {
        Canvas(Modifier.size(size.dp)) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(Color.White.copy(alpha = 0.25f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(Color.White, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 28.sp)
            Text(caption, fontSize = 9.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        }
    }
}

/** Ícone e cor por tipo de item (Items, folha, destinatários). */
data class ItemLook(val icon: ImageVector, val fg: Color, val bg: Color)

@Composable
fun itemLook(item: LegacyItem): ItemLook {
    val dark = LocalDarkTheme.current
    fun look(icon: ImageVector, c: Color) = ItemLook(icon, c, c.copy(alpha = if (dark) 0.18f else 0.12f))
    val password = item.isPassword()
    val mime = item.meta.str("mime").orEmpty()
    val name = itemName(item).lowercase()
    return when {
        item.dto.type == "message" -> look(Icons.Outlined.MailOutline, Color(0xFFE11D48))
        password -> look(Icons.Outlined.Key, Color(0xFFD97706))
        item.dto.type == "folder" -> look(Icons.Outlined.Folder, Color(0xFF2563EB))
        item.dto.type == "note" -> look(Icons.Outlined.Description, Color(0xFF7C3AED))
        mime.startsWith("image/") || name.substringAfterLast('.') in setOf("jpg", "jpeg", "png", "heic", "webp", "gif") -> look(Icons.Outlined.Image, Color(0xFF059669))
        mime == "application/pdf" || name.endsWith(".pdf") -> look(Icons.Outlined.PictureAsPdf, Color(0xFFDC2626))
        else -> look(Icons.Outlined.InsertDriveFile, Color(0xFF0891B2))
    }
}

@Composable
fun ItemIcon(item: LegacyItem, size: Int = 36) {
    val l = itemLook(item)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(l.bg)) {
        Icon(l.icon, contentDescription = null, tint = l.fg, modifier = Modifier.size((size * 0.5).dp))
    }
}

/** Caixa de ícone com cor própria (stats, registo). */
@Composable
fun TintIcon(icon: ImageVector, color: Color, size: Int = 32) {
    val dark = LocalDarkTheme.current
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size.dp).clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = if (dark) 0.18f else 0.12f))) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size((size * 0.5).dp))
    }
}

/** Folha inferior do Legacy: cabeçalho livre, corpo com scroll, rodapé de botões. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegacySheet(
    onDismiss: () -> Unit,
    header: @Composable () -> Unit,
    footer: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        },
    ) {
        Column(Modifier.imePadding().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)) { header() }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
            if (footer != null) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    content = footer,
                )
            } else Spacer(Modifier.height(20.dp))
        }
    }
}

/** Separadores da barra inferior. */
enum class LegacyTab { Overview, Vault, People, Settings, Guarding }

/** Barra inferior do Legacy: ícone + rótulo, ativo dentro de uma pílula. */
@Composable
fun LegacyTabBar(tabs: List<Triple<LegacyTab, ImageVector, String>>, selected: LegacyTab, badge: Map<LegacyTab, Int> = emptyMap(), onSelect: (LegacyTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
            tabs.forEach { (tab, icon, label) ->
                val on = tab == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(tab) }
                        .padding(vertical = 4.dp),
                ) {
                    Box {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(52.dp)
                                .height(30.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent),
                        ) {
                            Icon(icon, contentDescription = null, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        badge[tab]?.takeIf { it > 0 }?.let {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error),
                            ) { Text(it.coerceAtMost(9).toString(), fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Text(
                        label,
                        fontSize = 11.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
