package co.golink.tester.ui.screens.calendar

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.calendar.EventAttachment
import co.golink.tester.ui.components.FilePreview
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.theme.LocalDarkTheme

/** "512 × 512 · 48 KB" (imagens) ou "820 KB" — attachmentMeta da web. */
private fun attachmentMeta(file: BrowseItem.File?): String = listOfNotNull(
    file?.imageWidth?.let { w -> file.imageHeight?.let { h -> "$w × $h" } },
    file?.filesize?.takeIf { it.isNotBlank() },
).joinToString(" · ")

/**
 * Ficheiros do Drive de um evento (AttachmentsField.vue): imagens em cartões
 * com miniatura, o resto em linhas com 👁 e ⋮; "Ver todos" abre o viewer.
 */
@Composable
fun EventAttachmentsSection(
    attachments: List<EventAttachment>,
    files: Map<String, BrowseItem.File>,
    onOpen: ((EventAttachment) -> Unit)?,
    onRemove: ((EventAttachment) -> Unit)? = null,
    onAttach: (() -> Unit)? = null,
    /** Com convidados: avisa os anexos que ficaram sem link. */
    guestsMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val images = attachments.filter { fileKind(it.name, it.mime).first == "IMG" }
    val others = attachments.filter { fileKind(it.name, it.mime).first != "IMG" }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                ("Ficheiros do Drive".tr() + if (attachments.isNotEmpty()) " · ${attachments.size}" else "").uppercase(),
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            if (attachments.size > 1 && onOpen != null) {
                Text(
                    "Ver todos".tr(), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = primary,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onOpen(attachments.first()) }.padding(4.dp),
                )
            }
        }

        images.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { a -> ImageCard(a, files[a.file_id], onOpen, onRemove, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        others.forEach { a ->
            val noLink = guestsMode && a.share_url.isNullOrBlank() && files[a.file_id] == null
            DocRow(a, files[a.file_id], if (noLink) "Sem link para convidados".tr() else null, onOpen, onRemove)
        }

        if (onAttach != null) AttachButton(attachments.isEmpty(), onAttach)
    }
}

@Composable
private fun ImageCard(
    a: EventAttachment,
    file: BrowseItem.File?,
    onOpen: ((EventAttachment) -> Unit)?,
    onRemove: ((EventAttachment) -> Unit)?,
    modifier: Modifier,
) {
    val dark = LocalDarkTheme.current
    val checker = if (dark) Color(0xFF2C2F35) else Color(0xFFEEF0F3)
    val base = if (dark) Color(0xFF24262B) else Color.White
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .then(if (onOpen != null) Modifier.clickable { onOpen(a) } else Modifier),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .background(base)
                // Xadrez: as transparências do PNG ficam visíveis.
                .drawBehind {
                    val cell = 7.dp.toPx()
                    var y = 0f
                    var row = 0
                    while (y < size.height) {
                        var x = if (row % 2 == 0) 0f else cell
                        while (x < size.width) {
                            drawRect(checker, Offset(x, y), Size(cell, cell))
                            x += cell * 2
                        }
                        y += cell
                        row++
                    }
                },
        ) {
            if (file != null && (file.encrypted || !file.thumbnailUrl.isNullOrBlank())) {
                FilePreview(file, iconSize = 36.dp, modifier = Modifier.fillMaxSize())
            } else {
                FileBadge(a.name, a.mime, 44.dp)
            }
            AttachmentMenu(a, onOpen, onRemove, Modifier.align(Alignment.TopEnd).padding(4.dp), overlay = true)
        }
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp)) {
            Text(a.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                attachmentMeta(file).ifEmpty { " " }, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DocRow(
    a: EventAttachment,
    file: BrowseItem.File?,
    hint: String?,
    onOpen: ((EventAttachment) -> Unit)?,
    onRemove: ((EventAttachment) -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .then(if (onOpen != null) Modifier.clickable { onOpen(a) } else Modifier)
            .padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
    ) {
        DocIcon(a)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                hint ?: attachmentMeta(file).ifEmpty { fileKind(a.name, a.mime).first },
                fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onOpen != null) {
            IconButton(onClick = { onOpen(a) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Visibility, contentDescription = "Pré-visualizar".tr(), modifier = Modifier.size(18.dp))
            }
        }
        AttachmentMenu(a, onOpen, onRemove)
    }
}

/** Folha com linhas e etiqueta colorida (PDF, DOC, XLS…), como na web. */
@Composable
private fun DocIcon(a: EventAttachment) {
    val (label, color) = fileKind(a.name, a.mime)
    val line = MaterialTheme.colorScheme.outline
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .size(width = 36.dp, height = 44.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, line, RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                val left = 7.dp.toPx()
                for (i in 0 until 3) {
                    val y = 8.dp.toPx() + i * 5.dp.toPx()
                    drawRect(line, Offset(left, y), Size(size.width - left * 2, 2.dp.toPx()))
                }
            }
            .padding(3.dp),
    ) {
        Text(
            label, fontSize = 7.5.sp, fontWeight = FontWeight.ExtraBold, color = color,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(color.copy(alpha = 0.14f)).padding(vertical = 1.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun AttachmentMenu(
    a: EventAttachment,
    onOpen: ((EventAttachment) -> Unit)?,
    onRemove: ((EventAttachment) -> Unit)?,
    modifier: Modifier = Modifier,
    overlay: Boolean = false,
) {
    if (onOpen == null && onRemove == null) return
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.size(if (overlay) 28.dp else 36.dp).then(
                if (overlay) Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) else Modifier,
            ),
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Mais ações".tr(), modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onOpen != null) {
                DropdownMenuItem(
                    text = { Text("Pré-visualizar".tr()) },
                    leadingIcon = { Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = { open = false; onOpen(a) },
                )
            }
            if (onRemove != null) {
                DropdownMenuItem(
                    text = { Text("Remover".tr(), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                    onClick = { open = false; onRemove(a) },
                )
            }
        }
    }
}

@Composable
private fun AttachButton(empty: Boolean, onAttach: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(primary.copy(alpha = 0.05f))
            .drawBehind {
                drawRoundRect(
                    color = primary.copy(alpha = 0.45f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    ),
                )
            }
            .clickable(onClick = onAttach),
    ) {
        Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = primary)
        Spacer(Modifier.width(6.dp))
        Text(
            if (empty) "Anexar do Drive".tr() else "Anexar mais ficheiros".tr(),
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = primary,
        )
    }
}

/** "Convidados podem ver os anexos" (só quem gere o evento, com convidados e anexos). */
@Composable
fun GuestsSeeFilesRow(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Convidados podem ver os anexos".tr(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Recebem um link só de ver destes ficheiros, não da pasta inteira.".tr(),
                fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
