package co.golink.tester.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.GroupRemove
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.ui.i18n.tr

private val Amber = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemActionsSheet(
    item: BrowseItem,
    isFavourite: Boolean,
    isShared: Boolean = false,
    canFavourite: Boolean = true,
    inTrash: Boolean = false,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    /** null: não é o dono (a partilha exige-o). */
    onShare: (() -> Unit)?,
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
    // false = raiz de equipa de outro dono (só o dono a move; mover termina a equipa).
    canMove: Boolean = true,
    onConvertToTeamFolder: () -> Unit = {},
    onFileRequest: () -> Unit = {},
    showFolderCollaboration: Boolean = true,
    // Pastas de equipa (raiz): dono dissolve, membro sai — como na web.
    onDissolveTeam: (() -> Unit)? = null,
    onLeaveTeam: (() -> Unit)? = null,
    // Dono, na raiz: adicionar/remover membros e mudar permissões.
    onEditTeam: (() -> Unit)? = null,
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
            val folder = item as? BrowseItem.Folder
            val danger = MaterialTheme.colorScheme.error
            // Partilhas sem link público (equipa/utilizador) ou expiradas/esgotadas não têm o que copiar.
            val copyLink = onCopyLink?.takeIf { item.share?.let { s -> s.active && !s.link.isNullOrBlank() } == true }

            SheetHeader(
                item = item,
                isFavourite = isFavourite,
                onToggleFavourite = if (!inTrash && folder != null && canFavourite) ({ onToggleFavourite(); onDismiss() }) else null,
                onEditIcon = if (!inTrash && canEdit) ({ onRename(); onDismiss() }) else null,
                onClose = onDismiss,
            )

            if (inTrash) {
                // Lixo: restaurar, detalhes e eliminar de vez.
                QuickTiles(
                    listOf(
                        Tile(Icons.Outlined.RestoreFromTrash, "Restaurar".tr(), primary = true) { onRestore(); onDismiss() },
                        Tile(Icons.Outlined.Info, "Detalhes".tr()) { onDetails(); onDismiss() },
                    ),
                )
                DangerFooter(Icons.Outlined.DeleteOutline, "Eliminar para sempre".tr(), danger) { onPermanentDelete(); onDismiss() }
            } else {
                // Ações rápidas em quadrados (Partilhar em destaque), como no mockup.
                val tiles = buildList {
                    onShare?.let { share -> add(Tile(Icons.Outlined.Share, (if (isShared) "Partilha" else "Partilhar").tr(), primary = true) { share(); onDismiss() }) }
                    // Sem partilhar (não é o dono): Transferir fica em destaque.
                    add(Tile(Icons.Outlined.Download, "Transferir".tr(), primary = onShare == null) { onDownload(); onDismiss() })
                    copyLink?.let { copy -> add(Tile(Icons.Outlined.ContentCopy, "Copiar link".tr()) { copy(); onDismiss() }) }
                    add(Tile(Icons.Outlined.Info, "Detalhes".tr()) { onDetails(); onDismiss() })
                }
                QuickTiles(tiles)

                // Organizar: nome/ícone e mover.
                if (canEdit) {
                    SectionLabel("Organizar".tr())
                    if (folder != null) {
                        ActionRow(Icons.Outlined.Edit, "Nome e ícone".tr(), "Nome, cor ou emoji".tr()) { onRename(); onDismiss() }
                    } else {
                        ActionRow(Icons.Outlined.Edit, "Editar nome".tr()) { onRename(); onDismiss() }
                    }
                    if (canMove) ActionRow(Icons.AutoMirrored.Outlined.DriveFileMove, "Mover ou copiar".tr(), "Para outra pasta".tr()) { onMove(); onDismiss() }
                }

                // Trabalhar com outros: pedidos de ficheiros e equipa.
                val collab = buildList<@Composable () -> Unit> {
                    if (folder != null && showFolderCollaboration && canEdit) {
                        add { ActionRow(Icons.Outlined.Download, "Pedir ficheiros".tr(), "Outros carregam, mas não veem a pasta".tr()) { onFileRequest(); onDismiss() } }
                        if (!folder.isTeamFolder) {
                            add { ActionRow(Icons.Outlined.PersonAddAlt, "Transformar em pasta de equipa".tr(), "Os membros recebem as suas próprias chaves de acesso".tr()) { onConvertToTeamFolder(); onDismiss() } }
                        }
                    }
                    onEditTeam?.let { editTeam -> add { ActionRow(Icons.Outlined.GroupAdd, "Gerir membros".tr(), "Adicionar pessoas e mudar permissões".tr()) { editTeam(); onDismiss() } } }
                    onLeaveTeam?.let { leave -> add { ActionRow(Icons.AutoMirrored.Outlined.Logout, "Sair da pasta de equipa".tr(), tint = danger) { leave(); onDismiss() } } }
                    onDissolveTeam?.let { dissolve -> add { ActionRow(Icons.Outlined.GroupRemove, "Dissolver equipa".tr(), tint = danger) { dissolve(); onDismiss() } } }
                }
                if (collab.isNotEmpty()) {
                    SectionLabel("Trabalhar com outros".tr())
                    collab.forEach { it() }
                }

                if (canEdit) DangerFooter(Icons.Outlined.DeleteOutline, "Mover para o lixo".tr(), danger) { onDelete(); onDismiss() }
            }
        }
    }
}

private class Tile(
    val icon: ImageVector,
    val label: String,
    val primary: Boolean = false,
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
            val bg = if (tile.primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            val fg = if (tile.primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(84.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .clickable(onClick = tile.onClick)
                    .padding(horizontal = 4.dp),
            ) {
                Icon(tile.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    tile.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val isDanger = tint != Color.Unspecified
    val color = if (isDanger) tint else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDanger) tint.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        ) {
            Icon(icon, contentDescription = null, tint = if (isDanger) tint else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!isDanger) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// Ação destrutiva separada no fim (lixo / eliminar de vez).
@Composable
private fun DangerFooter(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
    )
    ActionRow(icon, label, tint = tint, onClick = onClick)
}

@Composable
private fun SheetHeader(
    item: BrowseItem,
    isFavourite: Boolean,
    onToggleFavourite: (() -> Unit)?,
    onEditIcon: (() -> Unit)?,
    onClose: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
    ) {
        Box {
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
                        // Mesmo preview da linha (cifrados decifram em memória).
                        if (item.type == "image" && (item.encrypted || !item.thumbnailUrl.isNullOrBlank())) {
                            FilePreview(item, iconSize = 36.dp, modifier = Modifier.fillMaxSize())
                        } else {
                            FileTypeIcon(item, Modifier.height(36.dp))
                        }
                    }
                }
            }
            // Lápis no canto: atalho para nome/ícone.
            if (onEditIcon != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable(onClick = onEditIcon),
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Editar nome".tr(), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(11.dp))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val (meta, state) = buildSubtitle(item)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                if (meta.isNotBlank()) {
                    Text(
                        "$meta · ",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                val isPrivate = !(item is BrowseItem.Folder && item.isTeamFolder) && item.share == null
                val stateIcon = when {
                    item is BrowseItem.Folder && item.isTeamFolder -> Icons.Outlined.Groups
                    item.share != null -> Icons.Outlined.Public
                    else -> Icons.Outlined.Lock
                }
                Icon(stateIcon, contentDescription = null, tint = if (isPrivate) Amber else muted, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text(state, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1)
            }
        }
        if (onToggleFavourite != null) {
            IconButton(onClick = onToggleFavourite) {
                Icon(
                    if (isFavourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Favorito".tr(),
                    tint = if (isFavourite) Amber else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable(onClick = onClose),
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "Fechar".tr(), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        }
    }
}

// ("2 itens · 56.03 GB", "Privada") / ("Vídeo · 13.51 MB", "Partilhado")
private fun buildSubtitle(item: BrowseItem): Pair<String, String> {
    val parts = mutableListOf<String>()
    val state: String
    when (item) {
        is BrowseItem.Folder -> {
            item.itemCount?.let { parts += if (it == 1) "1 item".tr() else "%d itens".tr().format(it) }
            item.filesize?.takeIf { item.filesizeBytes > 0 }?.let { parts += it }
            state = when {
                item.isTeamFolder -> "Equipa".tr()
                item.share != null -> "Partilhada".tr()
                else -> "Privada".tr()
            }
        }
        is BrowseItem.File -> {
            parts += fileTypeLabel(item)
            item.filesize?.let { parts += it }
            state = (if (item.share != null) "Partilhado" else "Privado").tr()
        }
    }
    return parts.joinToString(" · ") to state
}
