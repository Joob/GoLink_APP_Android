package co.golink.tester.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.BrowseStats
import co.golink.tester.ui.common.formatBytes
import co.golink.tester.ui.common.relativeAgo
import co.golink.tester.ui.i18n.tr

/** Estado da pasta mostrado antes das contagens ("🔒 Privada", "Equipa"…). */
data class FolderBadge(val icon: ImageVector, val label: String, val tone: Tone) {
    enum class Tone { Gray, Green, Blue }
}

/** Ícone da raiz de cada secção (na caixa do cabeçalho). */
enum class HeaderSection(val icon: ImageVector) {
    Drive(Icons.Outlined.Storage),
    Team(Icons.Outlined.Groups),
    SharedWithMe(Icons.Outlined.PersonAddAlt),
    Links(Icons.Outlined.Link),
    Trash(Icons.Outlined.DeleteOutline),
}

/**
 * Cabeçalho das listagens (igual à web): ‹ voltar + caminho, ícone em caixa,
 * título, ★ favorito, menu "Pasta ⌄", linha de estado e chips de tipo.
 */
@Composable
fun FolderHeader(
    title: String,
    crumbs: List<String>,
    section: HeaderSection,
    folder: BrowseItem.Folder?,
    badge: FolderBadge?,
    stats: BrowseStats?,
    typeFilter: String?,
    isFavourite: Boolean,
    canFavourite: Boolean,
    canFolderMenu: Boolean,
    onBack: (() -> Unit)?,
    onCrumb: (Int) -> Unit,
    onToggleFavourite: () -> Unit,
    onFolderMenu: () -> Unit,
    onTypeFilter: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
        // ‹ + caminho (O meu Drive › Pasta 1 › release)
        if (onBack != null || crumbs.size > 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(26.dp).clickable(onClick = onBack),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Voltar".tr(), modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    crumbs.forEachIndexed { i, name ->
                        if (i > 0) Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp).padding(horizontal = 1.dp),
                        )
                        val last = i == crumbs.lastIndex
                        Text(
                            name,
                            fontSize = 12.5.sp,
                            fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 160.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .then(if (!last) Modifier.clickable { onCrumb(i) } else Modifier)
                                .padding(horizontal = 3.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderTile(folder = folder, section = section)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (canFavourite) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onToggleFavourite),
                        ) {
                            Icon(
                                if (isFavourite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = (if (isFavourite) "Remover dos favoritos" else "Adicionar aos favoritos").tr(),
                                tint = if (isFavourite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                    if (canFolderMenu) {
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.clickable(onClick = onFolderMenu),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                            ) {
                                Text("Pasta".tr(), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                StatusLine(badge = badge, stats = stats, updatedIso = folder?.updatedAtIso)
            }
        }

        TypeChips(stats = stats, typeFilter = typeFilter, onTypeFilter = onTypeFilter)
    }
}

@Composable
private fun HeaderTile(folder: BrowseItem.Folder?, section: HeaderSection) {
    val primary = MaterialTheme.colorScheme.primary
    val tint = parseHexColor(folder?.color) ?: primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(13.dp)),
    ) {
        when {
            folder != null && !folder.emoji.isNullOrBlank() -> Text(folder.emoji, fontSize = 24.sp)
            folder != null -> Icon(Icons.Filled.Folder, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            else -> Icon(section.icon, contentDescription = null, tint = primary, modifier = Modifier.size(24.dp))
        }
    }
}

// "🔒 Privada · 1 pasta, 2 documentos · 8.74 MB · atualizada há 1 h"
@Composable
private fun StatusLine(badge: FolderBadge?, stats: BrowseStats?, updatedIso: String?) {
    val counts = buildList {
        stats?.let { s ->
            if (s.folders > 0) add(plural(s.folders, "%d pasta", "%d pastas"))
            if (s.video > 0) add(plural(s.video, "%d vídeo", "%d vídeos"))
            if (s.image > 0) add(plural(s.image, "%d imagem", "%d imagens"))
            if (s.audio > 0) add(plural(s.audio, "%d áudio", "%d áudios"))
            if (s.file > 0) add(plural(s.file, "%d documento", "%d documentos"))
        }
    }.joinToString(", ")
    val parts = buildList {
        if (counts.isNotEmpty()) add(counts)
        stats?.bytes?.takeIf { it > 0 }?.let { add(formatBytes(it)) }
        updatedIso?.let { relativeAgo(it) }?.takeIf { it.isNotEmpty() }?.let { add("atualizada %s".tr().format(it)) }
    }
    if (badge == null && parts.isEmpty()) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 3.dp),
    ) {
        if (badge != null) {
            val color = when (badge.tone) {
                FolderBadge.Tone.Green -> MaterialTheme.colorScheme.primary
                FolderBadge.Tone.Blue -> Color(0xFF2563EB)
                FolderBadge.Tone.Gray -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(badge.icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
            Text(badge.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
            if (parts.isNotEmpty()) Text("  ·  ", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        }
        Text(
            parts.joinToString(" · "),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Todos · Vídeos · Imagens · Documentos (só os tipos que existem, ou o ativo).
@Composable
private fun TypeChips(stats: BrowseStats?, typeFilter: String?, onTypeFilter: (String?) -> Unit) {
    val s = stats ?: return
    data class Chip(val type: String?, val label: String, val count: Int)
    val chips = buildList {
        add(Chip(null, "Todos".tr(), s.folders + s.files))
        if (s.video > 0 || typeFilter == "video") add(Chip("video", "Vídeos".tr(), s.video))
        if (s.image > 0 || typeFilter == "image") add(Chip("image", "Imagens".tr(), s.image))
        if (s.file > 0 || typeFilter == "file") add(Chip("file", "Documentos".tr(), s.file))
    }
    if (chips.size < 2 && typeFilter == null) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .padding(top = 12.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        chips.forEach { chip ->
            val active = chip.type == typeFilter
            val onSurface = MaterialTheme.colorScheme.onSurface
            Surface(
                shape = RoundedCornerShape(50),
                color = if (active) onSurface else MaterialTheme.colorScheme.surface,
                border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onTypeFilter(chip.type) },
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        chip.label,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) MaterialTheme.colorScheme.surface else onSurface,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        chip.count.toString(),
                        fontSize = 12.5.sp,
                        color = if (active) MaterialTheme.colorScheme.surface.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun plural(n: Int, one: String, many: String): String =
    (if (n == 1) one else many).tr().format(n)
