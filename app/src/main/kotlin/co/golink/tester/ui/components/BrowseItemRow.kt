package co.golink.tester.ui.components

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
private fun FilePreview(
    item: BrowseItem.File,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val candidates = remember(item.id, item.thumbnailUrl, item.fileUrl) { previewCandidates(item) }
    var index by remember(candidates) { mutableIntStateOf(0) }
    if (index >= candidates.size) {
        val (icon, tint) = fileIcon(item.type)
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
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
) {
    val containerColor = MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
                Spacer(Modifier.width(4.dp))
            }
            Leading(item)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                val members = (item as? BrowseItem.Folder)?.members.orEmpty()
                if (item is BrowseItem.Folder && members.isNotEmpty()) {
                    MemberAvatarRow(
                        members = members,
                        onClick = onMembersClick?.let { cb -> { cb(members) } },
                    )
                } else {
                    Text(
                        metaFor(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onMoreClick) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Mais opções",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
) {
    val containerColor = MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
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
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Mais opções",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when (item) {
                    is BrowseItem.Folder -> {
                        val tint = parseHexColor(item.color) ?: MaterialTheme.colorScheme.primary
                        if (!item.emoji.isNullOrBlank()) {
                            Text(item.emoji, fontSize = 40.sp)
                        } else {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(56.dp),
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
                        FilePreview(
                            item = item,
                            iconSize = 56.dp,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (item.share != null) {
                    ShareBadge(modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
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
        .background(Color.White)
    Box(modifier = modifier.then(borderMod).padding(1.dp)) {
        if (!member.avatarUrl.isNullOrBlank()) {
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
    val boxSize = 56.dp
    val iconSize = 48.dp
    Box(modifier = Modifier.size(boxSize)) {
        when (item) {
            is BrowseItem.Folder -> {
                val tint = parseHexColor(item.color) ?: MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!item.emoji.isNullOrBlank()) {
                        Text(item.emoji, fontSize = 36.sp)
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
                val (icon, tint) = fileIcon(item.type)
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
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(iconSize),
                        )
                    }
                }
            }
        }
        if (item.share != null) {
            ShareBadge(modifier = Modifier.align(Alignment.BottomEnd))
        }
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
            contentDescription = "Partilhado",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(11.dp),
        )
    }
}

@Composable
internal fun fileIcon(type: String): Pair<ImageVector, Color> = when (type) {
    "image" -> Icons.Filled.Image to Color(0xFF9D66FE)
    "video" -> Icons.Filled.Movie to Color(0xFFFE6057)
    "audio" -> Icons.Filled.Audiotrack to Color(0xFFFE66A1)
    "document" -> Icons.Filled.Description to Color(0xFF5578EB)
    "pdf" -> Icons.Filled.PictureAsPdf to Color(0xFFFE6057)
    else -> Icons.Filled.InsertDriveFile to Color(0xFFFFBD2D)
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

internal fun metaFor(item: BrowseItem): String = when (item) {
    is BrowseItem.Folder -> {
        val count = item.itemCount?.let { "$it itens" }
        listOfNotNull(count, item.filesize, item.createdAt).joinToString(" • ")
    }
    is BrowseItem.File -> listOfNotNull(item.filesize, item.createdAt).joinToString(" • ")
}
