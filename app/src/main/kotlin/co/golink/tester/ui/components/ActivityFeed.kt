package co.golink.tester.ui.components

import androidx.compose.runtime.key
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.domain.activity.ActivityEntry
import co.golink.tester.domain.activity.ActivitySummary
import co.golink.tester.domain.activity.DuplicateGroup
import co.golink.tester.domain.activity.FeedEntry
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.ui.common.appLocale
import co.golink.tester.ui.common.formatBytes
import co.golink.tester.ui.common.formatTime
import co.golink.tester.ui.common.parseIsoLocal
import co.golink.tester.ui.i18n.tr
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Atividade recente (igual à web): resumo dos últimos 7 dias + duplicados,
 * separadores Todos / Os meus uploads / Partilhado comigo / Pastas de equipa,
 * grupos por dia e frases na 2.ª pessoa ("Carregaste 3 vídeos para Camera").
 */
@Composable
fun ActivityFeedContent(
    entries: List<FeedEntry>,
    tab: String,
    hasMore: Boolean,
    loadingMore: Boolean,
    summary: ActivitySummary?,
    onTab: (String) -> Unit,
    onOpen: (ActivityEntry) -> Unit,
    onOpenFolder: (BrowseItem.Folder, String?) -> Unit,
    onRestore: (BrowseItem) -> Unit,
    onMore: (ActivityEntry) -> Unit,
    onLoadMore: () -> Unit,
) {
    var reviewDuplicates by remember { mutableStateOf(false) }
    val visible = remember(entries, tab) {
        when (tab) {
            "all" -> entries
            "team" -> entries.filter { it.category == "team" }
            else -> entries.filter { it.category == tab }
        }
    }
    val days = remember(visible) { visible.groupBy { parseIsoLocal(it.base.createdAtIso)?.toLocalDate() } }

    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 4
        }
    }
    LaunchedEffect(nearEnd, hasMore) { if (nearEnd && hasMore && !loadingMore) onLoadMore() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("O que mudou no teu Drive e no que partilham contigo.".tr(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (summary != null) SummaryCards(summary, onReview = { reviewDuplicates = true })
                FeedTabs(entries, tab, hasMore, onTab)
            }
        }
        if (visible.isEmpty()) {
            item(key = "empty") {
                Text(
                    when (tab) {
                        "my_uploads" -> "Não fizeste uploads recentemente".tr()
                        "downloads" -> "Ainda não transferiste nada.".tr()
                        "shared_with_me" -> "Ainda ninguém partilhou nada contigo".tr()
                        "team" -> "Sem atividade recente nas tuas pastas de equipa".tr()
                        else -> "Ainda não há atividade".tr()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp).fillMaxWidth(),
                )
            }
        }
        // Um cartão por dia, com as entradas separadas por linhas (como na web).
        days.forEach { (day, dayEntries) ->
            item(key = "day-$day", contentType = "day") {
                Column {
                    DayLabel(day)
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            dayEntries.forEachIndexed { i, entry ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                                key(entry.id) { FeedRow(entry, onOpen, onOpenFolder, onRestore, onMore) }
                            }
                        }
                    }
                }
            }
        }
        if (loadingMore) {
            item(key = "more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
            }
        }
    }

    if (reviewDuplicates && summary != null) {
        DuplicatesSheet(summary.duplicates, onDismiss = { reviewDuplicates = false }, onOpen = { reviewDuplicates = false; onOpen(it) })
    }
}

// ---- Resumo ---------------------------------------------------------------------

@Composable
private fun SummaryCards(summary: ActivitySummary, onReview: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("Últimos 7 dias".tr(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row {
                    StatBox(summary.uploaded7d, "ficheiros carregados".tr(), Modifier.weight(1f))
                    StatBox(summary.foldersChanged7d, "pastas alteradas".tr(), Modifier.weight(1f))
                }
            }
        }
        summary.duplicates.firstOrNull()?.let { first ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("Possíveis duplicados".tr(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    val where = first.folders.take(2)
                    val more = summary.duplicates.size - 1
                    Text(
                        buildAnnotatedString {
                            append("%d ficheiros com o mesmo nome".tr().format(first.files.size) + " " + "em".tr() + " ")
                            where.forEachIndexed { k, f ->
                                if (k > 0) append(" " + "e".tr() + " ")
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) { append(f) }
                            }
                            if (more > 0) append(", " + "e mais %d casos.".tr().format(more)) else append(".")
                        },
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onReview,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp),
                    ) { Text("Rever duplicados".tr(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
    }
}

@Composable
private fun StatBox(value: Int, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Separadores e dias -------------------------------------------------------------

@Composable
private fun FeedTabs(entries: List<FeedEntry>, tab: String, hasMore: Boolean, onTab: (String) -> Unit) {
    val tabs = listOf(
        "all" to "Tudo".tr(),
        "my_uploads" to "Carregamentos".tr(),
        "downloads" to "Transferências".tr(),
        "shared_with_me" to "Partilhado comigo".tr(),
        "team" to "Pastas de equipa".tr(),
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEach { (key, label) ->
            val count = if (key == "all") entries.size else entries.count { it.category == key }
            val active = key == tab
            Surface(
                shape = RoundedCornerShape(50),
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
                border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onTab(key) },
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "$count${if (hasMore && count > 0) "+" else ""}",
                        fontSize = 12.5.sp,
                        color = if (active) MaterialTheme.colorScheme.surface.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// "Hoje · sexta, 16 out" / "Ontem · quinta, 15 out" / "12 out 2026 · segunda".
@Composable
private fun DayLabel(day: LocalDate?) {
    if (day == null) return
    val today = LocalDate.now()
    val primary = when (day) {
        today -> "Hoje".tr()
        today.minusDays(1) -> "Ontem".tr()
        else -> day.format(DateTimeFormatter.ofPattern("d MMM yyyy", appLocale()))
    }
    val secondary = if (day == today || day == today.minusDays(1)) {
        day.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", appLocale()))
    } else day.format(DateTimeFormatter.ofPattern("EEEE", appLocale()))
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) { append(primary) }
            append(" · $secondary")
        },
        fontSize = 12.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, top = 10.dp, bottom = 2.dp),
    )
}

// ---- Cartão ---------------------------------------------------------------------------

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FeedRow(
    entry: FeedEntry,
    onOpen: (ActivityEntry) -> Unit,
    onOpenFolder: (BrowseItem.Folder, String?) -> Unit,
    onRestore: (BrowseItem) -> Unit,
    onMore: (ActivityEntry) -> Unit,
) {
    val e = entry.base
    val (icon, tint) = actionIcon(entry)
    val isBatch = entry.kind == "batch"
    val isDownloadBatch = isBatch && e.action == "downloaded"
    var expanded by remember(entry.id) { mutableStateOf(false) }
    // Pasta para "Abrir pasta"/"Ver ficheiros": a do lote, ou a do 1.º ficheiro.
    val folderTarget: BrowseItem.Folder? = entry.folder

    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    when {
                        isBatch -> expanded = !expanded
                        else -> onOpen(e)
                    }
                },
                onLongClick = { if (!isBatch && e.item != null) onMore(e) },
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatTime(e.createdAtIso),
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(38.dp),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(30.dp).background(tint.copy(alpha = 0.12f), RoundedCornerShape(9.dp)),
            ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(sentence(entry), fontSize = 13.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val m = meta(entry)
                if (m.isNotEmpty()) Text(m, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Miniaturas dos lotes de carregamentos.
            if (isBatch && !isDownloadBatch && entry.files.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    entry.files.take(3).forEach { f ->
                        Box(
                            Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (f is BrowseItem.File && (f.type == "image" || f.type == "video")) {
                                FilePreview(f, iconSize = 16.dp, modifier = Modifier.fillMaxSize())
                            } else if (f is BrowseItem.File) {
                                FileTypeIcon(f, Modifier.height(18.dp))
                            }
                        }
                    }
                }
            }
        }
        // Ações (por baixo do texto, alinhadas com ele).
        val item = e.item
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 80.dp, top = 8.dp),
        ) {
            when {
                isDownloadBatch -> {
                    RowButton(if (expanded) "Esconder".tr() else "Ver %d".tr().format(entry.count), RowButtonStyle.Outline) { expanded = !expanded }
                    folderTarget?.let { f -> RowButton("Ver ficheiros".tr()) { onOpenFolder(f, entry.folderOwnerId) } }
                }
                isBatch -> {
                    RowButton(if (expanded) "Esconder".tr() else "Ver ficheiros".tr(), RowButtonStyle.Outline) { expanded = !expanded }
                    folderTarget?.let { f -> RowButton("Abrir pasta".tr(), RowButtonStyle.Theme) { onOpenFolder(f, entry.folderOwnerId) } }
                }
                e.itemTrashed && item != null && e.isMine -> RowButton("Restaurar".tr(), RowButtonStyle.Dark) { onRestore(item) }
                item is BrowseItem.Folder && !e.itemTrashed -> RowButton("Abrir pasta".tr(), RowButtonStyle.Theme) { onOpen(e) }
                item != null && !e.itemTrashed -> RowButton("Abrir".tr()) { onOpen(e) }
            }
        }
        // Lista dos ficheiros do lote.
        if (isBatch && expanded) {
            Column(Modifier.padding(start = 80.dp, top = 8.dp)) {
                entry.files.forEach { f ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpen(e.copy(item = f, itemTrashed = false)) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        if (f is BrowseItem.File) FileTypeIcon(f, Modifier.height(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(f.name, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        (f as? BrowseItem.File)?.filesize?.let {
                            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val hidden = entry.count - entry.files.size
                if (hidden > 0) Text("e mais %d".tr().format(hidden), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
            }
        }
    }
}

private enum class RowButtonStyle { Plain, Outline, Theme, Dark }

@Composable
private fun RowButton(label: String, style: RowButtonStyle = RowButtonStyle.Plain, onClick: () -> Unit) {
    val green = Color(0xFF15803D)
    val (bg, fg) = when (style) {
        RowButtonStyle.Plain -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) to MaterialTheme.colorScheme.onSurface
        RowButtonStyle.Outline -> Color.Transparent to MaterialTheme.colorScheme.onSurface
        RowButtonStyle.Theme -> green.copy(alpha = 0.12f) to green
        RowButtonStyle.Dark -> MaterialTheme.colorScheme.onSurface to MaterialTheme.colorScheme.surface
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = if (style == RowButtonStyle.Outline) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun actionIcon(entry: FeedEntry): Pair<ImageVector, Color> {
    val primary = MaterialTheme.colorScheme.primary
    val gray = MaterialTheme.colorScheme.onSurfaceVariant
    return when (entry.base.action) {
        "uploaded" -> Icons.Outlined.Upload to Color(0xFF15803D)
        "folder_created" -> Icons.Outlined.CreateNewFolder to primary
        "renamed", "content_updated" -> Icons.Outlined.Edit to gray
        "moved" -> Icons.Outlined.DriveFileMove to gray
        "trashed", "deleted" -> Icons.Outlined.DeleteOutline to co.golink.tester.ui.theme.Danger
        "restored" -> Icons.Outlined.RestoreFromTrash to primary
        "shared", "share_updated", "share_revoked" -> Icons.Outlined.Link to primary
        "share_viewed" -> Icons.Outlined.Visibility to gray
        "downloaded" -> Icons.Outlined.Download to Color(0xFF2563EB)
        "shared_with_me" -> Icons.Outlined.PersonAddAlt to Color(0xFF2563EB)
        "team_converted", "team_member_added", "team_member_removed", "team_member_left" -> Icons.Outlined.Group to Color(0xFF2563EB)
        else -> Icons.Outlined.History to gray
    }
}

// ---- Frases (2.ª pessoa quando és tu) -------------------------------------------------

private fun myVerb(action: String): String = when (action) {
    "uploaded" -> "Carregaste".tr()
    "folder_created" -> "Criaste".tr()
    "renamed" -> "Mudaste o nome de".tr()
    "moved" -> "Moveste".tr()
    "trashed" -> "Moveste para o lixo".tr()
    "restored" -> "Restauraste".tr()
    "deleted" -> "Eliminaste".tr()
    "content_updated" -> "Editaste".tr()
    "shared" -> "Criaste um link de partilha para".tr()
    "share_updated" -> "Atualizaste o link de partilha de".tr()
    "share_revoked" -> "Revogaste o link de partilha de".tr()
    "share_viewed" -> "Abriste".tr()
    "downloaded" -> "Transferiste".tr()
    "team_converted" -> "Converteste em pasta de equipa".tr()
    "team_member_added" -> "Entraste em".tr()
    "team_member_removed" -> "Removeste um membro de".tr()
    "team_member_left" -> "Saíste de".tr()
    else -> activityVerb(action)
}

// "3 vídeos", "1 documento", "5 ficheiros"
private fun batchWhat(entry: FeedEntry): String {
    val n = entry.count
    val type = entry.types.keys.singleOrNull()
    return when (type) {
        "video" -> if (n == 1) "1 vídeo".tr() else "%d vídeos".tr().format(n)
        "image" -> if (n == 1) "1 imagem".tr() else "%d imagens".tr().format(n)
        "audio" -> if (n == 1) "1 áudio".tr() else "%d áudios".tr().format(n)
        "file" -> if (n == 1) "1 documento".tr() else "%d documentos".tr().format(n)
        else -> if (n == 1) "1 ficheiro".tr() else "%d ficheiros".tr().format(n)
    }
}

@Composable
private fun sentence(entry: FeedEntry) = buildAnnotatedString {
    val e = entry.base
    val bold = SpanStyle(fontWeight = FontWeight.SemiBold)
    val who = if (e.isMine) null else activityActorName(e)
    when (entry.kind) {
        "batch" -> if (e.action == "downloaded") {
            append("Transferiste".tr() + " ")
            withStyle(bold) { append(batchWhat(entry)) }
        } else {
            if (who != null) { withStyle(bold) { append(who) }; append(" " + "carregou".tr() + " ") } else append("Carregaste".tr() + " ")
            withStyle(bold) {
                append(batchWhat(entry))
                entry.base.parentName?.let { append(" " + "para".tr() + " " + it) }
            }
        }
        "shared_with_me" -> {
            withStyle(bold) { append(who ?: "") }
            append(" " + "partilhou contigo".tr() + " ")
            withStyle(bold) { append(e.item?.name.orEmpty()) }
        }
        else -> {
            if (who != null) { withStyle(bold) { append(who) }; append(" " + activityVerb(e.action) + " ") } else append(myVerb(e.action) + " ")
            withStyle(bold) { append(e.item?.name ?: "um item".tr()) }
        }
    }
}

private fun meta(entry: FeedEntry): String {
    val e = entry.base
    return when {
        // Transferências: "JSON · PDF · em New Folder".
        entry.kind == "batch" && e.action == "downloaded" -> buildList {
            addAll(entry.extensions.take(3))
            entry.folder?.name?.let { add("em".tr() + " " + it) }
        }.joinToString(" · ")
        entry.kind == "batch" -> formatBytes(entry.totalBytes)
        else -> buildList {
            (e.item as? BrowseItem.File)?.name?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() && it.length <= 5 }?.let { add(it.uppercase()) }
            e.parentName?.let { add("em".tr() + " " + it) }
            if (e.itemTrashed) add("No lixo".tr())
        }.joinToString(" · ")
    }
}

// ---- Duplicados --------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuplicatesSheet(groups: List<DuplicateGroup>, onDismiss: () -> Unit, onOpen: (ActivityEntry) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Possíveis duplicados".tr(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Ficheiros com o mesmo nome e tamanho em pastas diferentes.".tr(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(groups, key = { it.name + it.size }) { g ->
                Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(g.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(g.size, "%d cópias".tr().format(g.files.size)).joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        g.files.forEachIndexed { i, f ->
                            Text(
                                "• " + (g.fileFolders.getOrNull(i) ?: g.folders.firstOrNull().orEmpty()),
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                                    onOpen(
                                        ActivityEntry(
                                            id = f.id, action = "uploaded", isChild = false, isMine = true, createdAtIso = f.createdAtIso,
                                            actorName = null, actorAvatar = null, actorColor = null, hasActor = true, via = null, permission = null,
                                            itemOwnerId = null, itemTrashed = false, item = f, parentName = null,
                                        ),
                                    )
                                }.padding(vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
