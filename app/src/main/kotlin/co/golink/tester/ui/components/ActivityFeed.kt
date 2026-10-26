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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.OpenInFull
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
    onTrashDuplicates: (List<BrowseItem.File>) -> Unit = {},
    onOpenDuplicateFolder: (parentId: String?, name: String) -> Unit = { _, _ -> },
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
        DuplicatesSheet(
            groups = summary.duplicates,
            onDismiss = { reviewDuplicates = false },
            onOpenFolder = { parentId, name -> reviewDuplicates = false; onOpenDuplicateFolder(parentId, name) },
            onTrash = onTrashDuplicates,
        )
    }
}

// ---- Resumo ---------------------------------------------------------------------

@Composable
private fun SummaryCards(summary: ActivitySummary, onReview: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences(DUPLICATES_PREFS, android.content.Context.MODE_PRIVATE) }
    // Esconder vale para este conjunto de duplicados; grupos novos voltam a mostrar o cartão.
    val signature = remember(summary.duplicates) { summary.duplicates.map { "${it.size}|${it.name.lowercase()}" }.sorted().joinToString(",") }
    var dismissedSig by remember { mutableStateOf(prefs.getString(DUPLICATES_DISMISSED, null)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard {
            CardTitle(Icons.Outlined.BarChart, MaterialTheme.colorScheme.primary, "Últimos 7 dias".tr())
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox(summary.uploaded7d, "ficheiros carregados".tr(), Modifier.weight(1f))
                StatBox(summary.foldersChanged7d, "pastas alteradas".tr(), Modifier.weight(1f))
            }
            if (summary.uploadsByDay.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                UploadBars(summary.uploadsByDay)
            }
        }
        summary.duplicates.firstOrNull()?.takeIf { dismissedSig != signature }?.let { first ->
            SummaryCard {
                CardTitle(Icons.Outlined.ContentCopy, AMBER, "Possíveis duplicados".tr()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).clickable {
                            dismissedSig = signature
                            prefs.edit().putString(DUPLICATES_DISMISSED, signature).apply()
                        },
                    ) { Icon(Icons.Outlined.Close, contentDescription = "Esconder".tr(), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)) }
                }
                Spacer(Modifier.height(8.dp))
                val where = first.folders.take(2)
                Text(
                    buildAnnotatedString {
                        append("%d ficheiros com o mesmo nome".tr().format(first.files.size) + " " + "em".tr() + " ")
                        where.forEachIndexed { k, f ->
                            if (k > 0) append(" " + "e".tr() + " ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append(f) }
                        }
                        append(".")
                    },
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val more = summary.duplicates.size - 1
                if (more > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        (if (more == 1) "+%d outro grupo" else "+%d outros grupos").tr().format(more),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AMBER_TEXT,
                        modifier = Modifier.background(AMBER.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onReview),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Rever duplicados".tr(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.surface)
                    }
                }
            }
        }
    }
}

private const val DUPLICATES_PREFS = "activity"
private const val DUPLICATES_DISMISSED = "duplicates_dismissed"
private val AMBER = Color(0xFFF59E0B)
private val AMBER_TEXT = Color(0xFFB45309)
private val GREEN = Color(0xFF15803D)

@Composable
private fun SummaryCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        color = MaterialTheme.colorScheme.surface,
    ) { Column(Modifier.fillMaxWidth().padding(14.dp), content = content) }
}

@Composable
private fun CardTitle(icon: ImageVector, tint: Color, title: String, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(26.dp).background(tint.copy(alpha = 0.14f), RoundedCornerShape(8.dp)),
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp)) }
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun StatBox(value: Int, label: String, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(10.dp)).padding(12.dp)) {
        Text(value.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Carregamentos por dia, hoje à direita e destacado (como na web). */
@Composable
private fun UploadBars(days: List<Pair<String, Int>>) {
    val max = maxOf(1, days.maxOf { it.second })
    val theme = MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().height(72.dp)) {
        days.forEachIndexed { i, (date, count) ->
            val label = runCatching {
                LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE", appLocale())).trimEnd('.')
            }.getOrDefault("")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f).fillMaxSize(),
            ) {
                // Mínimo visível para os dias sem carregamentos.
                val fraction = maxOf(0.08f, count.toFloat() / max)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .background(if (i == days.lastIndex) theme else theme.copy(alpha = 0.22f), RoundedCornerShape(4.dp)),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
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
            append("   $secondary")
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

private enum class RowButtonStyle { Plain, Outline, Theme, Dark, Danger }

@Composable
private fun RowButton(label: String, style: RowButtonStyle = RowButtonStyle.Plain, onClick: () -> Unit) {
    val green = Color(0xFF15803D)
    val (bg, fg) = when (style) {
        RowButtonStyle.Plain -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) to MaterialTheme.colorScheme.onSurface
        RowButtonStyle.Outline -> Color.Transparent to MaterialTheme.colorScheme.onSurface
        RowButtonStyle.Theme -> green.copy(alpha = 0.12f) to green
        RowButtonStyle.Dark -> MaterialTheme.colorScheme.onSurface to MaterialTheme.colorScheme.surface
        RowButtonStyle.Danger -> Color.Transparent to Color(0xFFE11D48)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = when (style) {
            RowButtonStyle.Outline -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            RowButtonStyle.Danger -> BorderStroke(1.dp, Color(0xFFE11D48).copy(alpha = 0.3f))
            else -> null
        },
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
        "version_restored" -> Icons.Outlined.History to gray
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
    "version_restored" -> "Restauraste uma versão de".tr()
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

/**
 * Rever duplicados (igual à web, em coluna): filtros, grupos, pré-visualização
 * com ampliação e as cópias, com escolha da que fica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuplicatesSheet(
    groups: List<DuplicateGroup>,
    onDismiss: () -> Unit,
    onOpenFolder: (parentId: String?, name: String) -> Unit,
    onTrash: (List<BrowseItem.File>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filter by remember { mutableStateOf("all") }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var keepId by remember { mutableStateOf<String?>(null) }
    var enlarged by remember { mutableStateOf(false) }

    val visible = remember(groups, filter) {
        when (filter) {
            "image" -> groups.filter { it.type() == "image" }
            "other" -> groups.filter { it.type() != "image" }
            else -> groups
        }
    }
    val selected = visible.firstOrNull { it.key() == selectedKey } ?: visible.firstOrNull()
    // Grupo novo (ou a cópia a manter saiu): fica a primeira.
    val keepFileId = selected?.files?.firstOrNull { it.id == keepId }?.id ?: selected?.files?.firstOrNull()?.id
    val totalFree = groups.sumOf { it.freeBytes() }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "head") {
                Column {
                    Text("Possíveis duplicados".tr(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        buildAnnotatedString {
                            append((if (groups.size == 1) "%d grupo de ficheiros idênticos" else "%d grupos de ficheiros idênticos").tr().format(groups.size))
                            if (totalFree > 0) {
                                append(" · ")
                                val text = "podes libertar cerca de %s".tr()
                                val at = text.indexOf("%s")
                                append(text.substring(0, at))
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = GREEN)) { append(formatBytes(totalFree)) }
                                append(text.substring(at + 2))
                            }
                        },
                        fontSize = 12.sp,
                        color = muted,
                    )
                }
            }
            if (groups.isEmpty()) {
                item(key = "empty") {
                    Text("Sem duplicados por rever.".tr(), fontSize = 13.sp, color = muted, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
                }
                return@LazyColumn
            }
            // Filtros
            item(key = "filters") {
                val images = groups.count { it.type() == "image" }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("all" to "Todos".tr() to groups.size, "image" to "Imagens".tr() to images, "other" to "Outros".tr() to groups.size - images)
                        .forEach { (kl, count) ->
                            val (key, label) = kl
                            val active = filter == key
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
                                border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { filter = key },
                            ) {
                                Text(
                                    "$label $count",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                }
            }
            // Grupos (deslizar na horizontal)
            item(key = "groups") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visible, key = { it.key() }) { g ->
                        val active = g.key() == selected?.key()
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) GREEN.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (active) GREEN.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.width(220.dp).clip(RoundedCornerShape(12.dp)).clickable { selectedKey = g.key(); enlarged = false },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                Box(
                                    Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val f = g.files.first()
                                    if (f.type == "image" || f.type == "video") FilePreview(f, iconSize = 18.dp, modifier = Modifier.fillMaxSize())
                                    else FileTypeIcon(f, Modifier.height(22.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(g.name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(g.size, copiesLabel(g.files.size)).joinToString(" · "),
                                        fontSize = 11.sp,
                                        color = muted,
                                        maxLines = 1,
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                FreeBadge(g.freeBytes())
                            }
                        }
                    }
                }
            }
            if (selected != null) {
                // Pré-visualização + detalhes
                item(key = "preview-${selected.key()}") {
                    val first = selected.files.first()
                    val media = first.type == "image" || first.type == "video"
                    Column {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = media) { enlarged = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (media) {
                                FilePreview(first, iconSize = 48.dp, modifier = Modifier.fillMaxSize())
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(32.dp)
                                        .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(8.dp)),
                                ) { Icon(Icons.Outlined.OpenInFull, contentDescription = "Ampliar".tr(), tint = Color(0xFF111827), modifier = Modifier.size(16.dp)) }
                            } else FileTypeIcon(first, Modifier.height(64.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(selected.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        MetaLine("Tipo".tr(), typeLabel(selected))
                        MetaLine("Tamanho".tr(), "%s cada".tr().format(selected.size.orEmpty()))
                        MetaLine("Cópias".tr(), selected.files.size.toString())
                        MetaLine("Espaço a libertar".tr(), formatBytes(selected.freeBytes()), GREEN)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Estes ficheiros têm o mesmo nome e tamanho. Escolhe qual manter; as outras cópias vão para o lixo e podes restaurá-las.".tr(),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = GREEN,
                            modifier = Modifier.fillMaxWidth().background(GREEN.copy(alpha = 0.07f), RoundedCornerShape(10.dp)).padding(10.dp),
                        )
                    }
                }
                // Onde está guardado
                item(key = "where-${selected.key()}") {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Onde está guardado".tr().uppercase(appLocale()), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = muted, modifier = Modifier.weight(1f))
                            Text(
                                "Manter só a cópia selecionada".tr(),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = GREEN,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GREEN.copy(alpha = 0.1f))
                                    .clickable { onTrash(selected.files.filter { it.id != keepFileId }) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Column {
                                selected.files.forEachIndexed { i, f ->
                                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                                    val keep = f.id == keepFileId
                                    val folder = selected.fileFolders.getOrNull(i) ?: selected.folders.firstOrNull().orEmpty()
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .background(if (keep) GREEN.copy(alpha = 0.05f) else Color.Transparent)
                                            .clickable { keepId = f.id }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                Modifier
                                                    .size(18.dp)
                                                    .border(1.5.dp, if (keep) GREEN else muted, CircleShape)
                                                    .padding(4.dp)
                                                    .background(if (keep) GREEN else Color.Transparent, CircleShape),
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Icon(Icons.Outlined.Folder, contentDescription = null, tint = GREEN, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(folder, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    buildAnnotatedString {
                                                        append(parseIsoLocal(f.createdAtIso)?.toLocalDate()?.format(DateTimeFormatter.ofPattern("d MMM yyyy", appLocale())).orEmpty())
                                                        if (keep) {
                                                            append(" · ")
                                                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = GREEN)) { append("A manter".tr()) }
                                                        }
                                                    },
                                                    fontSize = 11.sp,
                                                    color = muted,
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 54.dp, top = 8.dp)) {
                                            RowButton("Abrir pasta".tr(), RowButtonStyle.Outline) { onOpenFolder(f.parentId, folder) }
                                            RowButton("Mover para o lixo".tr(), RowButtonStyle.Danger) { onTrash(listOf(f)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Ampliação por cima da folha (sem sair da revisão).
    if (enlarged && selected != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { enlarged = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).clickable { enlarged = false }.padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                FilePreview(selected.files.first(), iconSize = 64.dp, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.align(Alignment.TopEnd).size(36.dp).background(Color.White.copy(alpha = 0.15f), CircleShape),
                ) { Icon(Icons.Outlined.Close, contentDescription = "Fechar".tr(), tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable
private fun MetaLine(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
private fun FreeBadge(bytes: Long) {
    Text(
        "−" + formatBytes(bytes),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        color = GREEN,
        maxLines = 1,
        modifier = Modifier.background(GREEN.copy(alpha = 0.1f), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

private fun DuplicateGroup.key() = "${files.firstOrNull()?.filesizeBytes}|${name.lowercase()}"
private fun DuplicateGroup.type() = files.firstOrNull()?.type
private fun DuplicateGroup.freeBytes() = (files.firstOrNull()?.filesizeBytes ?: 0L) * (files.size - 1)

private fun copiesLabel(n: Int) = (if (n == 1) "%d cópia" else "%d cópias").tr().format(n)

private fun typeLabel(g: DuplicateGroup): String {
    val kind = when (g.type()) {
        "image" -> "Imagem"
        "video" -> "Vídeo"
        "audio" -> "Áudio"
        else -> "Ficheiro"
    }.tr()
    val ext = g.name.substringAfterLast('.', "").uppercase()
    return if (ext.isNotEmpty()) "$kind $ext" else kind
}
