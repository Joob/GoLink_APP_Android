package co.golink.tester.ui.components

import androidx.compose.material.icons.outlined.GroupRemove
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.ContentCopy
import co.golink.tester.ui.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import co.golink.tester.domain.browse.BrowseItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemActionsSheet(
    item: BrowseItem,
    isFavourite: Boolean,
    isShared: Boolean = false,
    inTrash: Boolean = false,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onToggleFavourite: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit = {},
    onPermanentDelete: () -> Unit = {},
    onDetails: () -> Unit = {},
    // Itens partilhados: copia o link (com #k= quando é preciso).
    onCopyLink: (() -> Unit)? = null,
    // false = só leitura (membro can-view): esconde editar/mover/eliminar.
    canEdit: Boolean = true,
    onComingSoon: () -> Unit = {},
    onConvertToTeamFolder: () -> Unit = {},
    onFileRequest: () -> Unit = {},
    showFolderCollaboration: Boolean = true,
    // Pastas de equipa (raiz): dono dissolve, membro sai — como na web.
    onDissolveTeam: (() -> Unit)? = null,
    onLeaveTeam: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        },
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            SheetHeader(item)

            val folder = item as? BrowseItem.Folder
            val danger = MaterialTheme.colorScheme.error

            if (inTrash) {
                // Lixo: restaurar, detalhes e eliminar de vez.
                QuickTiles(
                    listOf(
                        Tile(Icons.Outlined.RestoreFromTrash, "Restaurar".tr(), primary = true) { onRestore(); onDismiss() },
                        Tile(Icons.Outlined.Info, "Detalhes".tr()) { onDetails(); onDismiss() },
                    ),
                )
                DangerButton("Eliminar para sempre".tr()) { onPermanentDelete(); onDismiss() }
            } else {
                // Ações rápidas em quadrados (Partilhar em destaque), como no mockup.
                val tiles = buildList {
                    add(Tile(Icons.Outlined.Link, (if (isShared) "Partilha" else "Partilhar").tr(), primary = true) { onShare(); onDismiss() })
                    add(Tile(Icons.Outlined.Download, "Transferir".tr()) { onDownload(); onDismiss() })
                    when {
                        folder != null -> add(
                            Tile(if (isFavourite) Icons.Outlined.Star else Icons.Outlined.StarBorder, "Favorito".tr(), active = isFavourite) {
                                onToggleFavourite(); onDismiss()
                            },
                        )
                        isShared && onCopyLink != null -> add(Tile(Icons.Outlined.ContentCopy, "Copiar link".tr()) { onCopyLink(); onDismiss() })
                    }
                    add(Tile(Icons.Outlined.Info, "Detalhes".tr()) { onDetails(); onDismiss() })
                }
                QuickTiles(tiles)

                // Lista: editar, mover, colaborar, equipa.
                val rows = buildList<@Composable () -> Unit> {
                    if (canEdit) {
                        add { ActionRow(Icons.Outlined.Edit, (if (folder != null) "Editar nome e ícone" else "Editar nome").tr()) { onRename(); onDismiss() } }
                        add { ActionRow(Icons.AutoMirrored.Outlined.DriveFileMove, "Mover".tr()) { onMove(); onDismiss() } }
                    }
                    if (folder != null && isShared && onCopyLink != null) {
                        add { ActionRow(Icons.Outlined.ContentCopy, "Copiar link".tr()) { onCopyLink(); onDismiss() } }
                    }
                    if (folder != null && showFolderCollaboration && canEdit) {
                        add { ActionRow(Icons.Outlined.Upload, "Pedir ficheiros".tr(), "Outros carregam para esta pasta".tr()) { onFileRequest(); onDismiss() } }
                        if (!folder.isTeamFolder) {
                            add { ActionRow(Icons.Outlined.Groups, "Transformar em pasta de equipa".tr(), "Trabalhar com outras pessoas".tr()) { onConvertToTeamFolder(); onDismiss() } }
                        }
                    }
                    onLeaveTeam?.let { leave -> add { ActionRow(Icons.AutoMirrored.Outlined.Logout, "Sair da pasta de equipa".tr(), tint = danger) { leave(); onDismiss() } } }
                    onDissolveTeam?.let { dissolve -> add { ActionRow(Icons.Outlined.GroupRemove, "Dissolver equipa".tr(), tint = danger) { dissolve(); onDismiss() } } }
                }
                if (rows.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    ActionGroup { rows.forEach { it() } }
                }

                if (canEdit) {
                    DangerButton((if (folder != null) "Apagar pasta" else "Apagar ficheiro").tr()) { onDelete(); onDismiss() }
                }
            }
        }
    }
}

private class Tile(
    val icon: ImageVector,
    val label: String,
    val primary: Boolean = false,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

// Quadrados de ação rápida (o primeiro em destaque na cor do tema).
@Composable
private fun QuickTiles(tiles: List<Tile>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        tiles.forEach { tile ->
            val primary = MaterialTheme.colorScheme.primary
            val bg = if (tile.primary) primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            val fg = when {
                tile.primary -> MaterialTheme.colorScheme.onPrimary
                tile.active -> Color(0xFFF59E0B)
                else -> MaterialTheme.colorScheme.onSurface
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .clickable(onClick = tile.onClick)
                    .padding(horizontal = 4.dp),
            ) {
                Icon(tile.icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    tile.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (tile.primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (subtitle != null) 10.dp else 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Botão vermelho a toda a largura (Apagar / Eliminar para sempre).
@Composable
private fun DangerButton(label: String, onClick: () -> Unit) {
    val danger = MaterialTheme.colorScheme.error
    Spacer(Modifier.height(10.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(danger.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = danger, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = danger)
    }
}

@Composable
private fun SheetHeader(item: BrowseItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(((item as? BrowseItem.Folder)?.color?.let { parseHexColor(it) } ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            when (item) {
                is BrowseItem.Folder -> {
                    if (!item.emoji.isNullOrBlank()) {
                        Text(item.emoji, style = MaterialTheme.typography.titleLarge)
                    } else {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            tint = parseHexColor(item.color) ?: MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                is BrowseItem.File -> {
                    if (!item.thumbnailUrl.isNullOrBlank() && item.type == "image") {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        FileTypeIcon(item, Modifier.height(36.dp))
                    }
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = buildSubtitle(item)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ActionGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        content()
    }
}

@Composable
private fun SheetDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        modifier = Modifier.padding(start = 60.dp, end = 16.dp),
    )
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    iconBg: Color,
    iconTint: Color,
    labelColor: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (labelColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else labelColor,
        )
    }
}

// "2 itens · 56.03 GB · Privada" / "Vídeo · 13.51 MB · Partilhado"
private fun buildSubtitle(item: BrowseItem): String {
    val parts = mutableListOf<String>()
    when (item) {
        is BrowseItem.Folder -> {
            item.itemCount?.let { parts += if (it == 1) "1 item".tr() else "%d itens".tr().format(it) }
            item.filesize?.takeIf { item.filesizeBytes > 0 }?.let { parts += it }
            parts += when {
                item.isTeamFolder -> "Equipa".tr()
                item.share != null -> "Partilhada".tr()
                else -> "Privada".tr()
            }
        }
        is BrowseItem.File -> {
            parts += fileTypeLabel(item)
            item.filesize?.let { parts += it }
            if (item.share != null) parts += "Partilhado".tr()
        }
    }
    return parts.joinToString(" · ")
}
