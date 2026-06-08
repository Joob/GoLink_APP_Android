package co.golink.tester.ui.components

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
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Info
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
    onComingSoon: () -> Unit = {},
    onConvertToTeamFolder: () -> Unit = {},
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

            if (inTrash) {
                Spacer(Modifier.height(4.dp))
                ActionGroup {
                    ActionItem(
                        icon = Icons.Outlined.RestoreFromTrash,
                        label = "Restaurar",
                        iconBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        iconTint = MaterialTheme.colorScheme.primary,
                    ) { onRestore(); onDismiss() }
                }
                Spacer(Modifier.height(8.dp))
                ActionGroup {
                    ActionItem(
                        icon = Icons.Outlined.DeleteForever,
                        label = "Eliminar permanentemente",
                        iconBg = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                        iconTint = MaterialTheme.colorScheme.error,
                        labelColor = MaterialTheme.colorScheme.error,
                    ) { onPermanentDelete(); onDismiss() }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                ActionGroup {
                    ActionItem(
                        icon = Icons.Outlined.Edit,
                        label = "Editar item",
                        iconBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        iconTint = MaterialTheme.colorScheme.primary,
                    ) { onRename(); onDismiss() }
                    SheetDivider()
                    ActionItem(
                        icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                        label = "Mover",
                        iconBg = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                        iconTint = MaterialTheme.colorScheme.secondary,
                    ) { onMove(); onDismiss() }
                    SheetDivider()
                    ActionItem(
                        icon = Icons.Outlined.DeleteOutline,
                        label = "Eliminar",
                        iconBg = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                        iconTint = MaterialTheme.colorScheme.error,
                        labelColor = MaterialTheme.colorScheme.error,
                    ) { onDelete(); onDismiss() }
                }
                Spacer(Modifier.height(8.dp))
                ActionGroup {
                    ActionItem(
                        icon = Icons.Outlined.Download,
                        label = "Descarregar",
                        iconBg = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                        iconTint = MaterialTheme.colorScheme.tertiary,
                    ) { onDownload(); onDismiss() }
                    SheetDivider()
                    ActionItem(
                        icon = Icons.Outlined.Share,
                        label = if (isShared) "Editar partilha" else "Partilhar",
                        iconBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        iconTint = MaterialTheme.colorScheme.primary,
                    ) { onShare(); onDismiss() }
                    if (item is BrowseItem.File) {
                        SheetDivider()
                        ActionItem(
                            icon = Icons.Outlined.Info,
                            label = "Detalhes",
                            iconBg = MaterialTheme.colorScheme.surfaceVariant,
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        ) { onDetails(); onDismiss() }
                    }
                    if (item is BrowseItem.Folder) {
                        SheetDivider()
                        ActionItem(
                            icon = if (isFavourite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            label = if (isFavourite) "Remover dos favoritos" else "Adicionar aos favoritos",
                            iconBg = Color(0xFFFFF3E0),
                            iconTint = Color(0xFFFF8F00),
                        ) { onToggleFavourite(); onDismiss() }
                        SheetDivider()
                        ActionItem(
                            icon = Icons.Outlined.Groups,
                            label = "Converter em pasta de equipa",
                            iconBg = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                            iconTint = MaterialTheme.colorScheme.secondary,
                        ) { onConvertToTeamFolder(); onDismiss() }
                        SheetDivider()
                        ActionItem(
                            icon = Icons.Outlined.NoteAdd,
                            label = "Pedido de ficheiros",
                            iconBg = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                            iconTint = MaterialTheme.colorScheme.tertiary,
                        ) { onComingSoon(); onDismiss() }
                    }
                }
            }
        }
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
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
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
                            tint = MaterialTheme.colorScheme.primary,
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
                        Icon(
                            Icons.Filled.InsertDriveFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp),
                        )
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
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 20.dp),
    )
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

private fun buildSubtitle(item: BrowseItem): String {
    val parts = mutableListOf<String>()
    when (item) {
        is BrowseItem.Folder -> {
            item.itemCount?.let { parts += "$it itens" }
            item.filesize?.let { parts += it }
        }
        is BrowseItem.File -> {
            item.type?.let { parts += it }
            item.filesize?.let { parts += it }
        }
    }
    val date = item.createdAt ?: item.updatedAt
    if (!date.isNullOrBlank()) parts += date.take(10)
    return parts.joinToString(" · ")
}
