package co.golink.tester.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.domain.activity.ActivityEntry
import co.golink.tester.domain.activity.ActivityPage
import co.golink.tester.domain.activity.ActivitySection
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.TeamMember
import co.golink.tester.ui.i18n.I18n
import co.golink.tester.ui.i18n.tr
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---- Textos ---------------------------------------------------------------------

private val VISITOR_ACTIONS = setOf("share_viewed", "downloaded")

internal fun activityActorName(e: ActivityEntry): String = when {
    e.isMine -> "Tu".tr()
    !e.actorName.isNullOrBlank() -> e.actorName
    !e.hasActor && (e.via == "share" || e.action in VISITOR_ACTIONS) -> "Alguém com o link".tr()
    else -> "Utilizador eliminado".tr()
}

internal fun activityVerb(action: String): String = when (action) {
    "uploaded" -> "carregou".tr()
    "folder_created" -> "criou".tr()
    "renamed" -> "mudou o nome de".tr()
    "moved" -> "moveu".tr()
    "trashed" -> "moveu para o lixo".tr()
    "restored" -> "restaurou".tr()
    "deleted" -> "eliminou".tr()
    "content_updated" -> "editou".tr()
    "shared" -> "criou um link de partilha para".tr()
    "share_updated" -> "atualizou o link de partilha de".tr()
    "share_revoked" -> "revogou o link de partilha de".tr()
    "share_viewed" -> "abriu".tr()
    "downloaded" -> "transferiu".tr()
    "team_converted" -> "converteu em Team Folder".tr()
    "team_member_added" -> "entrou em".tr()
    "team_member_removed" -> "removeu um membro de".tr()
    "team_member_left" -> "saiu de".tr()
    "shared_with_me" -> "partilhou contigo".tr()
    else -> action
}

private fun sectionTitle(s: ActivitySection) = when (s) {
    ActivitySection.MY_UPLOADS -> "Os meus uploads recentes".tr()
    ActivitySection.SHARED_WITH_ME -> "Partilhado comigo".tr()
    ActivitySection.TEAM_ACTIVITY -> "Atividade nas Team Folders".tr()
    ActivitySection.SHARE_ACTIVITY -> "Atividade nas partilhas".tr()
}

private fun sectionEmpty(s: ActivitySection) = when (s) {
    ActivitySection.MY_UPLOADS -> "Não fizeste uploads recentemente".tr()
    ActivitySection.SHARED_WITH_ME -> "Ainda ninguém partilhou nada contigo".tr()
    ActivitySection.TEAM_ACTIVITY -> "Sem atividade recente nas tuas Team Folders".tr()
    ActivitySection.SHARE_ACTIVITY -> "Ainda não há atividade nos teus links partilhados".tr()
}

private fun sectionIcon(s: ActivitySection): ImageVector = when (s) {
    ActivitySection.MY_UPLOADS -> Icons.Outlined.CloudUpload
    ActivitySection.SHARED_WITH_ME -> Icons.Outlined.PersonAddAlt
    ActivitySection.TEAM_ACTIVITY -> Icons.Outlined.Group
    ActivitySection.SHARE_ACTIVITY -> Icons.Outlined.Link
}

// ---- Datas (no fuso do dispositivo, na língua da app) ---------------------------

private fun appLocale(): Locale = Locale.forLanguageTag(I18n.lang)

private fun parseIso(iso: String?) = iso?.let {
    runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull()
}

internal fun activityRelative(iso: String?): String {
    val t = parseIso(iso) ?: return ""
    val mins = java.time.Duration.between(t.toInstant(), java.time.Instant.now()).toMinutes()
    return when {
        mins < 1 -> "agora".tr()
        mins < 60 -> "$mins min"
        mins < 24 * 60 -> "${mins / 60} h"
        t.toLocalDate() == LocalDate.now().minusDays(1) -> "Ontem".tr()
        else -> t.format(DateTimeFormatter.ofPattern("d MMM yyyy", appLocale()))
    }
}

internal fun activityTime(iso: String?): String =
    parseIso(iso)?.format(DateTimeFormatter.ofPattern("HH:mm", appLocale())).orEmpty()

internal fun activityDayLabel(iso: String?): String {
    val d = parseIso(iso)?.toLocalDate() ?: return ""
    val today = LocalDate.now()
    return when (d) {
        today -> "Hoje".tr()
        today.minusDays(1) -> "Ontem".tr()
        else -> d.format(DateTimeFormatter.ofPattern("d MMM yyyy", appLocale()))
    }
}

// ---- Peças partilhadas ----------------------------------------------------------

@Composable
private fun ActorAvatar(e: ActivityEntry, size: androidx.compose.ui.unit.Dp) {
    if (e.hasActor) {
        MemberAvatar(
            member = TeamMember(
                id = "",
                email = e.actorName.orEmpty(),
                name = e.actorName,
                avatarUrl = e.actorAvatar,
                color = e.actorColor,
                permission = null,
            ),
            size = size,
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

@Composable
private fun ItemIcon(item: BrowseItem?, size: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        when (item) {
            is BrowseItem.Folder -> if (!item.emoji.isNullOrBlank()) {
                Text(item.emoji, fontSize = (size.value * 0.7f).sp)
            } else {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = null,
                    tint = parseHexColor(item.color) ?: MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(size),
                )
            }
            // Miniatura (cifrada ou não), como nas listas; sem ela, o ícone do tipo.
            is BrowseItem.File -> FilePreview(
                item = item,
                iconSize = size * 0.9f,
                modifier = Modifier
                    .size(size)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
            )
            null -> Unit
        }
    }
}

// ---- Ecrã: Atividade recente ----------------------------------------------------

@Composable
fun RecentActivityContent(
    sections: Map<ActivitySection, ActivityPage>,
    loadingMore: Set<ActivitySection>,
    onOpen: (ActivityEntry) -> Unit,
    onMore: (ActivityEntry) -> Unit,
    onManageShare: (ActivityEntry) -> Unit,
    onLoadMore: (ActivitySection) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showInfo by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Atividade recente".tr(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .clickable { showInfo = !showInfo },
                    )
                }
                if (showInfo) {
                    Text(
                        "O histórico de atividade é guardado durante 90 dias e depois eliminado automaticamente. Só o registo de atividade é apagado — os teus ficheiros, pastas e partilhas nunca são afetados.".tr(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
        ActivitySection.entries.forEach { section ->
            item(key = section.key) {
                ActivitySectionCard(
                    section = section,
                    page = sections[section],
                    loadingMore = section in loadingMore,
                    onOpen = onOpen,
                    onMore = onMore,
                    onManageShare = onManageShare,
                    onLoadMore = { onLoadMore(section) },
                )
            }
        }
    }
}

@Composable
private fun ActivitySectionCard(
    section: ActivitySection,
    page: ActivityPage?,
    loadingMore: Boolean,
    onOpen: (ActivityEntry) -> Unit,
    onMore: (ActivityEntry) -> Unit,
    onManageShare: (ActivityEntry) -> Unit,
    onLoadMore: () -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, outline, RoundedCornerShape(12.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(sectionIcon(section), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                sectionTitle(section),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            val count = page?.entries?.size ?: 0
            if (count > 0) {
                Text(
                    "$count${if (page?.hasMore == true) "+" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        val entries = page?.entries.orEmpty()
        if (entries.isEmpty()) {
            HorizontalDivider(color = outline)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            ) {
                Icon(sectionIcon(section), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(sectionEmpty(section), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            entries.forEach { entry ->
                HorizontalDivider(color = outline)
                ActivityEntryRow(
                    entry = entry,
                    isShareSection = section == ActivitySection.SHARE_ACTIVITY,
                    onOpen = { onOpen(entry) },
                    onMore = { onMore(entry) },
                    onManageShare = { onManageShare(entry) },
                )
            }
        }
        if (page?.hasMore == true) {
            HorizontalDivider(color = outline)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !loadingMore, onClick = onLoadMore)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (loadingMore) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Text("Ver mais".tr(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ActivityEntryRow(
    entry: ActivityEntry,
    isShareSection: Boolean,
    onOpen: () -> Unit,
    onMore: () -> Unit,
    onManageShare: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (isShareSection && !entry.itemTrashed) onManageShare else onOpen)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
    ) {
        ItemIcon(entry.item, 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.item?.name.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.itemTrashed) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "No lixo".tr().uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActorAvatar(entry, 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                            append(activityActorName(entry))
                        }
                        append(" ")
                        append(activityVerb(entry.action))
                        append(" · ")
                        append(activityRelative(entry.createdAtIso))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onMore, enabled = entry.item != null) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções".tr(), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

// ---- Separador Atividade (Detalhes) ---------------------------------------------

@Composable
fun ItemActivityTimeline(
    itemKey: String,
    fetch: suspend (Int) -> Result<ActivityPage>,
    onOpenChild: ((ActivityEntry) -> Unit)? = null,
) {
    val entries = remember(itemKey) { mutableStateListOf<ActivityEntry>() }
    var page by remember(itemKey) { mutableStateOf(0) }
    var hasMore by remember(itemKey) { mutableStateOf(false) }
    var loading by remember(itemKey) { mutableStateOf(true) }
    var failed by remember(itemKey) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load(next: Int) {
        loading = true
        fetch(next)
            .onSuccess { p ->
                val seen = entries.map { it.id }.toSet()
                entries += p.entries.filter { it.id !in seen }
                hasMore = p.hasMore
                page = p.page
                failed = false
            }
            .onFailure { failed = entries.isEmpty() }
        loading = false
    }

    LaunchedEffect(itemKey) { load(1) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val groups = entries.groupBy { activityDayLabel(it.createdAtIso) }
        groups.forEach { (label, rows) ->
            Column {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                ) {
                    rows.forEachIndexed { i, e ->
                        if (i > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        TimelineRow(e, onOpenChild)
                    }
                }
            }
        }

        when {
            loading -> Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
            entries.isEmpty() -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
            ) {
                Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    if (failed) "Não foi possível carregar".tr() else "Ainda sem atividade".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            hasMore -> Text(
                "Ver mais".tr(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { scope.launch { load(page + 1) } }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun TimelineRow(e: ActivityEntry, onOpenChild: ((ActivityEntry) -> Unit)?) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
        ActorAvatar(e, 28.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                        append(activityActorName(e))
                    }
                    append(" ")
                    append(activityVerb(e.action))
                    if (e.via == "share" && e.hasActor) {
                        append(" · ")
                        append("via link de partilha".tr())
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (e.isChild && e.item != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .then(if (onOpenChild != null) Modifier.clickable { onOpenChild(e) } else Modifier)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    ItemIcon(e.item, 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        e.item.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                activityTime(e.createdAtIso),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
