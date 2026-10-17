package co.golink.tester.ui.components

import androidx.compose.foundation.layout.fillMaxHeight
import co.golink.tester.ui.i18n.tr
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.TeamMember

// Candidatos a preview, por ordem: thumbnail do servidor e, como fallback, o
// próprio ficheiro. Logo após um upload o thumbnail ainda não foi gerado
// (null ou 404) — sem o fallback o ícone ficava genérico até nova visita.
// Para vídeos o VideoFrameDecoder do Coil extrai um frame do ficheiro.
private fun previewCandidates(item: BrowseItem.File): List<String> = when (item.type) {
    "image", "video" -> listOfNotNull(
        item.thumbnailUrl?.takeIf { it.isNotBlank() },
        item.fileUrl?.takeIf { it.isNotBlank() },
    )
    else -> emptyList()
}

// Preview de imagem/vídeo com fallback: se um candidato falhar (thumbnail
// ainda por gerar, URL assinado expirado), tenta o seguinte; esgotados todos,
// volta ao ícone genérico do tipo.
@Composable
internal fun FilePreview(
    item: BrowseItem.File,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    // E2E: thumbnail cifrado — decifra em memória (não há URL do servidor).
    if (item.encrypted && (item.type == "image" || item.type == "video")) {
        Box(modifier = modifier) {
            EncryptedThumbnail(
                fileId = item.id,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                fallback = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        FileTypeIcon(item, Modifier.height(iconSize))
                    }
                },
            )
            if (item.type == "video") {
                PlayBadge(Modifier.align(Alignment.Center))
            }
        }
        return
    }

    val candidates = remember(item.id, item.thumbnailUrl, item.fileUrl) { previewCandidates(item) }
    var index by remember(candidates) { mutableIntStateOf(0) }
    if (index >= candidates.size) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            FileTypeIcon(item, Modifier.height(iconSize))
        }
    } else {
        Box(modifier = modifier) {
            val ctx = LocalContext.current
            val url = candidates[index]
            val req = remember(url) {
                ImageRequest.Builder(ctx)
                    .data(url)
                    .setHeader("Accept", "*/*")
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = req,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { state -> if (state is AsyncImagePainter.State.Error) index++ },
                modifier = Modifier.fillMaxSize(),
            )
            if (item.type == "video") {
                PlayBadge(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun PlayBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseItemRow(
    item: BrowseItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onMoreClick: () -> Unit = onLongClick,
    onMembersClick: ((List<TeamMember>) -> Unit)? = null,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    // Itens partilhados: botão de copiar link ao lado do ⋮ (null = sem botão).
    onCopyLinkClick: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = selectionContainer(selected && selectionMode)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = selectionBorder(selected && selectionMode),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 2.dp, top = 10.dp, bottom = 10.dp),
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(6.dp))
            }
            Leading(item)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                val members = (item as? BrowseItem.Folder)?.members.orEmpty()
                if (item is BrowseItem.Folder && members.isNotEmpty()) {
                    MemberAvatarRow(
                        members = members,
                        onClick = onMembersClick?.let { cb -> { cb(members) } },
                    )
                } else {
                    Text(
                        metaFor(item),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onCopyLinkClick != null && !selectionMode) {
                CopyLinkButton(onCopyLinkClick, Modifier.size(40.dp), iconSize = 18.dp)
            }
            IconButton(onClick = onMoreClick, enabled = !selectionMode, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Mais opções".tr(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseItemGridCard(
    item: BrowseItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onMoreClick: () -> Unit = onLongClick,
    onMembersClick: ((List<TeamMember>) -> Unit)? = null,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onCopyLinkClick: (() -> Unit)? = null,
    // Secção "Pastas": cartão horizontal compacto (ícone · nome · "2 itens · 8 MB" · ⋮).
    compactFolder: Boolean = false,
) {
    if (compactFolder && item is BrowseItem.Folder) {
        CompactFolderCard(item, onClick, onLongClick, onMoreClick, selected, selectionMode)
        return
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = selectionContainer(selected && selectionMode)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = selectionBorder(selected && selectionMode),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.align(Alignment.TopStart).size(24.dp),
                    )
                }
                if (onCopyLinkClick != null && !selectionMode) {
                    CopyLinkButton(
                        onCopyLinkClick,
                        Modifier.align(Alignment.TopEnd).padding(end = 30.dp).size(28.dp),
                        iconSize = 16.dp,
                    )
                }
                IconButton(
                    onClick = onMoreClick,
                    enabled = !selectionMode,
                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Mais opções".tr(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Área de pré-visualização mais alta (era 1.6f) para caberem
                    // ícones/miniaturas maiores no modo grelha.
                    .aspectRatio(1.25f)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when (item) {
                    is BrowseItem.Folder -> {
                        val tint = parseHexColor(item.color) ?: MaterialTheme.colorScheme.primary
                        if (!item.emoji.isNullOrBlank()) {
                            Text(item.emoji, fontSize = 56.sp)
                        } else {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(80.dp),
                            )
                        }
                        // Member avatars overlay at bottom-start
                        if (item.members.isNotEmpty()) {
                            MemberAvatarRow(
                                members = item.members,
                                size = 20.dp,
                                onClick = onMembersClick?.let { cb -> { cb(item.members) } },
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 4.dp, bottom = 4.dp),
                            )
                        }
                    }
                    is BrowseItem.File -> {
                        // Documentos: "página" com a etiqueta da extensão (como na web).
                        if (item.type == "image" || item.type == "video" || item.type == "audio") {
                            FilePreview(
                                item = item,
                                iconSize = 80.dp,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            DocPlaceholder(item, Modifier.fillMaxSize())
                        }
                        if (!item.filesize.isNullOrBlank()) {
                            SizeBadge(item.filesize, Modifier.align(Alignment.BottomEnd))
                        }
                    }
                }
                if (item.share != null) {
                    ShareBadge(modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                item.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (item is BrowseItem.File) gridMetaFor(item) else metaFor(item),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Grelha: o tamanho já vai no selo da miniatura → "Vídeo · 16 out 2026".
private fun gridMetaFor(item: BrowseItem.File): String {
    val date = (item.capturedAtIso ?: item.createdAtIso)?.let { co.golink.tester.ui.common.formatDay(it) }
        ?.takeIf { it.isNotEmpty() } ?: item.createdAt
    return listOfNotNull(fileTypeLabel(item), date).joinToString(" · ")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactFolderCard(
    item: BrowseItem.Folder,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    selected: Boolean,
    selectionMode: Boolean,
) {
    val tint = parseHexColor(item.color) ?: MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = selectionContainer(selected && selectionMode)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = selectionBorder(selected && selectionMode),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 0.dp),
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(6.dp))
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                if (!item.emoji.isNullOrBlank()) {
                    Text(item.emoji, fontSize = 18.sp)
                } else {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }
                if (item.share != null) ShareBadge(Modifier.align(Alignment.BottomEnd).size(14.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    metaFor(item),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onMoreClick, enabled = !selectionMode, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Mais opções".tr(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// Miniatura de documento: ícone colorido da extensão (PDF, TXT, APK…), como na web.
@Composable
private fun DocPlaceholder(item: BrowseItem.File, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        FileTypeIcon(item, Modifier.fillMaxHeight(0.62f))
    }
}

@Composable
internal fun MemberAvatarRow(
    members: List<TeamMember>,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 24.dp,
    maxVisible: Int = 4,
    onClick: (() -> Unit)? = null,
) {
    val visible = members.take(maxVisible)
    val overflow = members.size - maxVisible
    val overlap = (size * 0.35f)

    Row(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.height(size)) {
            visible.forEachIndexed { i, member ->
                MemberAvatar(
                    member = member,
                    size = size,
                    modifier = Modifier.offset(x = overlap * i),
                )
            }
            if (overflow > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = overlap * visible.size)
                        .size(size)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+$overflow",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = (size.value * 0.38f).sp,
                    )
                }
            }
        }
        val extraWidth = overlap * (visible.size - 1) + if (overflow > 0) overlap else 0.dp
        Spacer(Modifier.width(extraWidth + 6.dp))
        if (onClick != null) {
            Text(
                "${members.size} membro${if (members.size != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun MemberAvatar(
    member: TeamMember,
    size: androidx.compose.ui.unit.Dp = 28.dp,
    modifier: Modifier = Modifier,
) {
    val borderMod = Modifier
        .size(size)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surface)
    // Avatar que não carrega (link expirado, ficheiro em falta): mostra as iniciais.
    var avatarFailed by remember(member.avatarUrl) { androidx.compose.runtime.mutableStateOf(false) }
    Box(modifier = modifier.then(borderMod).padding(1.dp)) {
        if (!member.avatarUrl.isNullOrBlank() && !avatarFailed) {
            val ctx = LocalContext.current
            val req = remember(member.avatarUrl) {
                ImageRequest.Builder(ctx)
                    .data(member.avatarUrl)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = req,
                contentDescription = member.name,
                contentScale = ContentScale.Crop,
                onError = { avatarFailed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        } else {
            val bg = parseHexColor(member.color) ?: MaterialTheme.colorScheme.primary
            val initials = member.name
                ?.split(" ")
                ?.mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                ?.take(2)
                ?.joinToString("") ?: member.email.take(1).uppercase()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initials,
                    color = Color.White,
                    fontSize = (size.value * 0.38f).sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun Leading(item: BrowseItem) {
    val boxSize = 44.dp
    val iconSize = 38.dp
    Box(modifier = Modifier.size(boxSize)) {
        when (item) {
            is BrowseItem.Folder -> {
                val tint = parseHexColor(item.color) ?: MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!item.emoji.isNullOrBlank()) {
                        Text(item.emoji, fontSize = 28.sp)
                    } else {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(iconSize),
                        )
                    }
                }
            }
            is BrowseItem.File -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    val hasPreview = previewCandidates(item).isNotEmpty()
                    if (hasPreview) {
                        FilePreview(
                            item = item,
                            iconSize = iconSize,
                            modifier = Modifier
                                .size(boxSize)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        FileTypeIcon(item, Modifier.height(iconSize))
                    }
                }
            }
        }
        if (item.share != null) {
            ShareBadge(modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}

// Seleção (multi-select): fundo verde suave + contorno do tema, como na web.
@Composable
private fun selectionContainer(active: Boolean): Color =
    if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surface

@Composable
private fun selectionBorder(active: Boolean) = androidx.compose.foundation.BorderStroke(
    1.dp,
    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
)

@Composable
private fun CopyLinkButton(onClick: () -> Unit, modifier: Modifier, iconSize: Dp) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            Icons.Outlined.Link,
            contentDescription = "Copiar link".tr(),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun ShareBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Link,
            contentDescription = "Partilhado".tr(),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(11.dp),
        )
    }
}

internal fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val cleaned = hex.removePrefix("#")
    return runCatching {
        when (cleaned.length) {
            6 -> Color(0xFF000000 or cleaned.toLong(16))
            8 -> Color(cleaned.toLong(16))
            else -> null
        }
    }.getOrNull()
}

// Sub-linha (como na web): "Vídeo · 13.51 MB · 16 out 2026" (data da captura
// EXIF, senão do upload); pastas: "3 itens · 8.74 MB" ou o caminho no telemóvel.
internal fun metaFor(item: BrowseItem): String = when (item) {
    is BrowseItem.Folder -> {
        val count = item.itemCount?.let { if (it == 1) "1 item".tr() else "%d itens".tr().format(it) }
        listOfNotNull(item.devicePath, count, item.filesize?.takeIf { item.filesizeBytes > 0 || item.devicePath == null })
            .joinToString(" · ")
    }
    is BrowseItem.File -> {
        val date = (item.capturedAtIso ?: item.createdAtIso)?.let { co.golink.tester.ui.common.formatDay(it) }
            ?.takeIf { it.isNotEmpty() } ?: item.createdAt
        listOfNotNull(fileTypeLabel(item), item.filesize, date).joinToString(" · ")
    }
}

internal fun fileTypeLabel(item: BrowseItem.File): String = when (item.type) {
    "image" -> "Imagem".tr()
    "video" -> "Vídeo".tr()
    "audio" -> "Áudio".tr()
    else -> item.name.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 5 }?.uppercase() ?: "Ficheiro".tr()
}

// Pílula escura com o tamanho, no canto da miniatura (grelha, como na web).
@Composable
private fun SizeBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = modifier
            .padding(6.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
