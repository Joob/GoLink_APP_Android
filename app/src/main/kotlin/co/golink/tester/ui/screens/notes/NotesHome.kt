package co.golink.tester.ui.screens.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Surface
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import co.golink.tester.ui.components.SelectionAction
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.withStyle
import kotlin.math.roundToInt
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.data.notes.NoteItem
import co.golink.tester.data.notes.NoteNotebook
import co.golink.tester.data.notes.NoteTask
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.i18n.I18n
import co.golink.tester.ui.i18n.nt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal val NotesAmber = Color(0xFFD97706)
private val Rose = Color(0xFFE11D48)
private val Blue = Color(0xFF2563EB)
private val Emerald = Color(0xFF059669)

/** Cores dos cadernos (NoteNotebook::COLORS / notesUi.js). */
internal val NOTES_NB_COLORS = listOf(
    "blue" to 0xFF2563EB, "purple" to 0xFF7C3AED, "green" to 0xFF16A34A,
    "red" to 0xFFDC2626, "orange" to 0xFFEA580C, "pink" to 0xFFDB2777,
    "teal" to 0xFF0D9488, "yellow" to 0xFFCA8A04, "slate" to 0xFF475569,
)

internal fun nbColor(name: String?) = Color(NOTES_NB_COLORS.firstOrNull { it.first == name }?.second ?: 0xFF2563EB)

/** Plural à vue-i18n: "a | b" (1 → a) ou "zero | um | vários". */
internal fun ntp(key: String, n: Int, vararg params: Pair<String, Any?>): String {
    val parts = nt(key, "n" to n, *params).split(" | ")
    return when (parts.size) {
        1 -> parts[0]
        2 -> if (n == 1) parts[0] else parts[1]
        else -> parts[minOf(n, 2)]
    }
}

private fun locale(): Locale = when (I18n.lang) {
    I18n.EN -> Locale.ENGLISH
    I18n.FR -> Locale.FRENCH
    I18n.ES -> Locale("es")
    else -> Locale("pt", "PT")
}

/** "agora", "2 min", "1 h", "ter.", "2 semanas", "out." — relativeTime de notesUi.js. */
internal fun notesAgo(iso: String?): String {
    val date = iso?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return ""
    val diff = (System.currentTimeMillis() - date.toEpochMilli()) / 1000
    val zoned = date.atZone(ZoneId.systemDefault())
    return when {
        diff < 60 -> nt("just_now")
        diff < 3600 -> nt("minutes_short", "n" to diff / 60)
        diff < 86400 -> nt("hours_short", "n" to diff / 3600)
        diff < 7 * 86400 -> zoned.dayOfWeek.getDisplayName(TextStyle.SHORT, locale())
        diff < 30 * 86400 -> ntp("weeks_short", (diff / (7 * 86400)).toInt())
        else -> zoned.month.getDisplayName(TextStyle.SHORT, locale())
    }
}

private fun formatDue(due: String): Pair<String, Color> {
    val today = LocalDate.now()
    val d = runCatching { LocalDate.parse(due) }.getOrNull() ?: return due to Color.Gray
    return when {
        d.isBefore(today) -> nt("overdue") to Rose
        d == today -> nt("today") to NotesAmber
        d == today.plusDays(1) -> nt("tomorrow") to Blue
        else -> d.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale())) to Color.Gray
    }
}

// ---- casca -----------------------------------------------------------------------

/**
 * Notes nativas (telemóvel): topo, chips de secção, Afixadas/Recentes, FAB e a
 * barra inferior Notes · Checklists · Pesquisar · Histórico — o redesign mobile da web.
 */
@Composable
fun NotesHome(
    vm: NotesHomeViewModel,
    showMenuButton: Boolean,
    onMenu: () -> Unit,
    onOpenNote: (NoteItem) -> Unit,
    onNewNote: (notebookId: String?) -> Unit,
    onNotebookDialog: (NotebookAction) -> Unit,
) {
    val section by vm.section.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<NoteItem?>(null) }
    var confirmDelete by remember { mutableStateOf<NoteItem?>(null) }
    val selection by vm.selection.collectAsStateWithLifecycle()
    val trash by vm.trash.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    // Seleção múltipla (como no Drive): só vale no ecrã onde começou.
    val sel = selection?.takeIf {
        (it.scope == "list" && section.isList) || (it.scope == "trash" && section == NotesSection.History && history.tab == "trash")
    }
    val selOrder = when (sel?.scope) {
        "list" -> visibleNotes(state.notes, section).map { it.id }
        "trash" -> trash.orEmpty().map { it.id }
        else -> emptyList()
    }
    val selIds = sel?.ids.orEmpty()
    var confirmBulk by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = sel != null) { vm.clearSelection() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val title = when (val s = section) {
            NotesSection.All -> nt("title")
            NotesSection.Pinned -> nt("pinned")
            NotesSection.Shared -> nt("shared")
            is NotesSection.Notebook -> state.notebooks.firstOrNull { it.id == s.id }?.name ?: nt("notebook")
            is NotesSection.Tag -> "#${s.name}"
            NotesSection.Checklists -> nt("checklists")
            NotesSection.Search -> nt("search")
            NotesSection.History -> nt("history")
        }
        if (sel != null) {
            val single = if (sel.scope == "list" && sel.ids.size == 1) state.notes.firstOrNull { it.id in sel.ids } else null
            SelectionTopBar(
                count = sel.ids.size,
                allSelected = selOrder.isNotEmpty() && sel.ids.containsAll(selOrder),
                onClose = vm::clearSelection,
                onToggleAll = { if (selOrder.isNotEmpty() && sel.ids.containsAll(selOrder)) vm.clearSelection() else vm.selectAll(selOrder) },
                onMore = single?.let { n -> { vm.clearSelection(); actionsFor = n } },
            )
        } else {
            TopBar(
                title, showMenuButton, onMenu,
                onSearch = if (section.isList) ({ vm.open(NotesSection.Search) }) else null,
                onSelect = if (section.isList && visibleNotes(state.notes, section).isNotEmpty()) ({ vm.startSelection("list") }) else null,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (section) {
                NotesSection.Checklists -> ChecklistsPage(vm, onOpenNote = { id -> state.notes.firstOrNull { it.id == id }?.let(onOpenNote) })
                NotesSection.Search -> SearchPage(vm, onOpenNote)
                NotesSection.History -> HistoryPage(vm)
                else -> {
                    NotesListPage(vm, section, state.loaded, state.notes, state.notebooks, sel?.ids, onOpenNote, onNewNote = {
                        onNewNote((section as? NotesSection.Notebook)?.id)
                    })
                    if (sel == null) {
                        Fab(
                            Modifier.align(Alignment.BottomEnd).padding(16.dp),
                            onClick = { onNewNote((section as? NotesSection.Notebook)?.id) },
                        )
                    }
                }
            }
        }
        when (sel?.scope) {
            "list" -> SelectionDock {
                SelectionAction(Icons.Outlined.DeleteOutline, nt("delete"), { confirmBulk = "delete" }, danger = true, enabled = selIds.isNotEmpty())
            }
            "trash" -> SelectionDock {
                SelectionAction(Icons.Outlined.Restore, nt("restore"), vm::restoreSelected, enabled = selIds.isNotEmpty())
                SelectionAction(Icons.Outlined.DeleteForever, nt("delete_forever"), { confirmBulk = "force" }, danger = true, enabled = selIds.isNotEmpty())
            }
            else -> TabBar(section, vm::open)
        }
    }

    when (confirmBulk) {
        "delete" -> {
            val ids = selIds
            val n = ids.size
            val t = state.notes.count { it.id in ids && it.isOwner }
            val l = n - t
            ConfirmDialog(
                title = when { l == 0 -> ntp("trash_many_title", n); t == 0 -> ntp("leave_many_title", n); else -> ntp("bulk_delete_title", n) },
                message = when { l == 0 -> nt("trash_many_text", "days" to state.trashDays); t == 0 -> nt("leave_many_text"); else -> nt("bulk_delete_mixed_text", "t" to t, "l" to l, "days" to state.trashDays) },
                confirmText = when { l == 0 -> nt("move_to_trash"); t == 0 -> nt("leave_note"); else -> nt("delete") },
                destructive = true,
                onDismiss = { confirmBulk = null },
                onConfirm = { vm.deleteSelected(); confirmBulk = null },
            )
        }
        "force" -> {
            val n = selIds.size
            ConfirmDialog(
                title = ntp("delete_forever_many_title", n),
                message = nt("delete_forever_many_text"),
                confirmText = nt("delete_forever"),
                destructive = true,
                onDismiss = { confirmBulk = null },
                onConfirm = { vm.forceSelected(); confirmBulk = null },
            )
        }
    }

    actionsFor?.let { note ->
        NoteActionsSheet(
            note = note,
            notebooks = state.notebooks,
            onDismiss = { actionsFor = null },
            onPin = { vm.setPinned(note, !note.pinned); actionsFor = null },
            onMove = { nb -> vm.move(note, nb); actionsFor = null },
            onDelete = { actionsFor = null; confirmDelete = note },
            onNewNotebook = { actionsFor = null; onNotebookDialog(NotebookAction.Create) },
        )
    }
    confirmDelete?.let { note ->
        ConfirmDialog(
            title = if (note.isOwner) nt("trash_title") else nt("leave_title"),
            message = if (note.isOwner) nt("trash_text", "days" to state.trashDays) else nt("leave_text"),
            confirmText = if (note.isOwner) nt("move_to_trash") else nt("leave_note"),
            destructive = true,
            onDismiss = { confirmDelete = null },
            onConfirm = { vm.delete(note); confirmDelete = null },
        )
    }
}

/** Pedido ao ecrã (que tem as folhas de caderno). */
sealed interface NotebookAction {
    data object Create : NotebookAction
}

@Composable
private fun TopBar(title: String, showMenu: Boolean, onMenu: () -> Unit, onSearch: (() -> Unit)?, onSelect: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showMenu) IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = I18n.t("Menu")) } else Spacer(Modifier.width(12.dp))
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        if (onSelect != null) IconButton(onClick = onSelect) { Icon(Icons.Outlined.CheckBox, contentDescription = nt("select")) }
        if (onSearch != null) IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = nt("search")) }
    }
}

/** Topo em modo seleção: ✕, contagem, Selecionar tudo/Desmarcar e ⋮ (só com 1 nota). */
@Composable
private fun SelectionTopBar(count: Int, allSelected: Boolean, onClose: () -> Unit, onToggleAll: () -> Unit, onMore: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = nt("cancel_selection")) }
        Text(
            ntp("n_selected", count),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        Text(
            if (allSelected) nt("deselect_all") else nt("select_all"),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFB45309),
            modifier = Modifier.padding(end = 4.dp).clip(RoundedCornerShape(50)).background(NotesAmber.copy(alpha = 0.14f)).clickable(onClick = onToggleAll).padding(horizontal = 14.dp, vertical = 8.dp),
        )
        if (onMore != null) IconButton(onClick = onMore) { Icon(Icons.Outlined.MoreVert, contentDescription = nt("more")) }
    }
}

/** Doca escura da seleção (a mesma do Drive), no lugar da barra inferior. */
@Composable
private fun SelectionDock(actions: @Composable RowScope.() -> Unit) {
    Surface(
        color = Color(0xFF111827),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** Círculo de seleção dos cartões. */
@Composable
private fun SelectCircle(checked: Boolean) {
    Box(
        Modifier.size(22.dp).clip(CircleShape)
            .background(if (checked) NotesAmber else Color.Transparent)
            .border(1.5.dp, if (checked) NotesAmber else MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

/** Notas da secção, pela ordem do ecrã (a seleção usa a mesma ordem). */
private fun visibleNotes(all: List<NoteItem>, section: NotesSection): List<NoteItem> = when (section) {
    NotesSection.Pinned -> all.filter { it.pinned }
    NotesSection.Shared -> all.filter { it.shared }
    is NotesSection.Notebook -> all.filter { it.notebookId == section.id }
    is NotesSection.Tag -> all.filter { section.name in it.meta.tags }
    else -> all
}.sortedByDescending { it.updatedAt }

private fun Modifier.selectedCard(selected: Boolean, shape: RoundedCornerShape) =
    if (selected) this.border(2.dp, NotesAmber, shape).background(NotesAmber.copy(alpha = 0.08f), shape) else this

@Composable
private fun Fab(modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NotesAmber)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(nt("new_note"), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun TabBar(section: NotesSection, open: (NotesSection) -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(Modifier.fillMaxWidth().height(60.dp)) {
            TabItem(Icons.Outlined.Description, nt("title"), section.isList, Modifier.weight(1f)) { open(NotesSection.All) }
            TabItem(Icons.Outlined.CheckBox, nt("checklists"), section == NotesSection.Checklists, Modifier.weight(1f)) { open(NotesSection.Checklists) }
            TabItem(Icons.Outlined.Search, nt("search"), section == NotesSection.Search, Modifier.weight(1f)) { open(NotesSection.Search) }
            TabItem(Icons.Outlined.History, nt("history"), section == NotesSection.History, Modifier.weight(1f)) { open(NotesSection.History) }
        }
    }
}

@Composable
private fun TabItem(icon: ImageVector, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (active) NotesAmber else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(if (active) NotesAmber.copy(alpha = 0.14f) else Color.Transparent).padding(horizontal = 14.dp, vertical = 3.dp),
        ) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp)) }
        Text(label, fontSize = 11.sp, color = color, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

// ---- lista ------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesListPage(
    vm: NotesHomeViewModel,
    section: NotesSection,
    loaded: Boolean,
    all: List<NoteItem>,
    notebooks: List<NoteNotebook>,
    selected: Set<String>?,
    onOpen: (NoteItem) -> Unit,
    onNewNote: () -> Unit,
) {
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val visible = remember(all, section) { visibleNotes(all, section) }
    // Seleção: toque alterna; pressão longa entra em seleção (já não abre a folha de ações).
    val tap: (NoteItem) -> Unit = { n -> if (selected != null) vm.toggleSelected("list", n.id) else onOpen(n) }
    val long: (NoteItem) -> Unit = { n ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (selected != null) vm.toggleSelected("list", n.id) else vm.startSelection("list", n.id)
    }
    val pinned = if (section == NotesSection.Pinned) emptyList() else visible.filter { it.pinned }
    val recent = if (section == NotesSection.Pinned) visible else visible.filterNot { it.pinned }
    val nbById = remember(notebooks) { notebooks.associateBy { it.id } }

    Column(Modifier.fillMaxSize()) {
        Chips(section, all, notebooks, vm::open)
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
                when {
                    !loaded -> items(5) {
                        Box(Modifier.padding(vertical = 5.dp).fillMaxWidth().height(78.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)))
                    }
                    visible.isEmpty() -> item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(24.dp))
                            Text(nt("empty_section"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                            TextButton(onClick = onNewNote) { Text("+ " + nt("new_note"), color = NotesAmber, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    else -> {
                        if (pinned.isNotEmpty()) {
                            item { SectionLabel(nt("pinned"), amber = true) }
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
                                    items(pinned, key = { it.id }) { n -> PinCard(n, nbById[n.notebookId], selected?.contains(n.id), { tap(n) }, { long(n) }) }
                                }
                            }
                        }
                        if (recent.isNotEmpty()) {
                            item {
                                val label = if (pinned.isNotEmpty() || section == NotesSection.All) nt("recent") else when (section) {
                                    NotesSection.Pinned -> nt("pinned")
                                    NotesSection.Shared -> nt("shared")
                                    is NotesSection.Notebook -> nbById[section.id]?.name ?: nt("notebook")
                                    is NotesSection.Tag -> "#${section.name}"
                                    else -> nt("recent")
                                }
                                SectionLabel(label, count = ntp("n_notes", recent.size))
                            }
                            items(recent, key = { it.id }) { n -> RecentCard(n, nbById[n.notebookId], selected?.contains(n.id), { tap(n) }, { long(n) }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chips(section: NotesSection, all: List<NoteItem>, notebooks: List<NoteNotebook>, open: (NotesSection) -> Unit) {
    val tags = remember(all) {
        all.flatMap { it.meta.tags }.groupingBy { it }.eachCount().toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip(nt("chip_all"), all.size, section == NotesSection.All) { open(NotesSection.All) }
        Chip(nt("pinned"), all.count { it.pinned }, section == NotesSection.Pinned) { open(NotesSection.Pinned) }
        Chip(nt("shared"), all.count { it.shared }, section == NotesSection.Shared) { open(NotesSection.Shared) }
        notebooks.forEach { nb ->
            Chip(nb.name, all.count { it.notebookId == nb.id }, (section as? NotesSection.Notebook)?.id == nb.id, dot = nbColor(nb.color)) { open(NotesSection.Notebook(nb.id)) }
        }
        tags.forEach { (tag, n) -> Chip("#$tag", n, (section as? NotesSection.Tag)?.name == tag) { open(NotesSection.Tag(tag)) } }
    }
}

@Composable
private fun Chip(label: String, count: Int?, on: Boolean, dot: Color? = null, onClick: () -> Unit) {
    val bg = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface
    val fg = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (on) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(label, fontSize = 13.sp, color = fg, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = if (dot != null) 6.dp else 0.dp), maxLines = 1)
        if (count != null) Text(" $count", fontSize = 12.sp, color = fg.copy(alpha = 0.6f))
    }
}

@Composable
private fun SectionLabel(text: String, amber: Boolean = false, count: String? = null) {
    Row(Modifier.padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (amber) Icon(Icons.Outlined.PushPin, contentDescription = null, tint = NotesAmber, modifier = Modifier.size(12.dp).padding(end = 2.dp))
        Text(text.uppercase(locale()), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = if (amber) NotesAmber else MaterialTheme.colorScheme.onSurfaceVariant)
        if (count != null) Text(" · $count", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
    }
}

private fun cardModifier(onClick: () -> Unit, onLong: () -> Unit) = @OptIn(ExperimentalFoundationApi::class) Modifier.combinedClickable(onClick = onClick, onLongClick = onLong)

@Composable
private fun PinCard(n: NoteItem, nb: NoteNotebook?, selected: Boolean?, onClick: () -> Unit, onLong: () -> Unit) {
    val stripe = nb?.let { nbColor(it.color) } ?: NotesAmber
    Column(
        Modifier
            .width(168.dp)
            .heightIn(min = 150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(nb?.let { nbColor(it.color).copy(alpha = 0.10f) } ?: MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .selectedCard(selected == true, RoundedCornerShape(14.dp))
            .then(cardModifier(onClick, onLong))
            .drawBehind { drawRect(stripe, size = androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx())) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(n.meta.title.ifBlank { nt("untitled") }, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            if (selected != null) Box(Modifier.padding(start = 6.dp)) { SelectCircle(selected) }
        }
        Text(n.meta.snippet.ifBlank { "—" }, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 3, minLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        if (n.meta.taskTotal > 0) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                val pct = n.meta.taskDone.toFloat() / n.meta.taskTotal
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxWidth(pct).fillMaxHeight().background(NotesAmber))
                }
                Text("${n.meta.taskDone}/${n.meta.taskTotal}", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Spacer(Modifier.weight(1f, fill = false).height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (nb != null) NotebookTag(nb)
            Spacer(Modifier.weight(1f))
            if (n.shared) Avatars(n)
        }
    }
}

@Composable
private fun RecentCard(n: NoteItem, nb: NoteNotebook?, selected: Boolean?, onClick: () -> Unit, onLong: () -> Unit) {
    Column(
        Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .selectedCard(selected == true, RoundedCornerShape(14.dp))
            .then(cardModifier(onClick, onLong))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (n.pinned) Icon(Icons.Outlined.PushPin, contentDescription = null, tint = NotesAmber, modifier = Modifier.size(13.dp).padding(end = 3.dp))
            if (n.locked) Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(13.dp).padding(end = 3.dp))
            Text(n.meta.title.ifBlank { nt("untitled") }, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(notesAgo(n.updatedAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 8.dp))
            if (selected != null) Box(Modifier.padding(start = 8.dp)) { SelectCircle(selected) }
        }
        Text(n.meta.snippet.ifBlank { "—" }, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        val hasBadges = nb != null || n.meta.taskTotal > 0 || n.shared || n.commentsCount > 0 || n.meta.hasFiles || n.filesCount > 0
        if (hasBadges) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (nb != null) NotebookTag(nb)
                if (n.meta.taskTotal > 0) Badge(Icons.Outlined.Check, "${n.meta.taskDone}/${n.meta.taskTotal}")
                if (n.shared) Badge(Icons.Outlined.People, "${n.members.size - 1}")
                if (n.commentsCount > 0) Badge(Icons.Outlined.ChatBubbleOutline, "${n.commentsCount}")
                if (n.meta.hasFiles || n.filesCount > 0) Badge(Icons.Outlined.AttachFile, null)
            }
        }
    }
}

@Composable
private fun Badge(icon: ImageVector, text: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
        if (text != null) Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
    }
}

@Composable
private fun NotebookTag(nb: NoteNotebook) {
    val c = nbColor(nb.color)
    Text(
        nb.name,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = c,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 110.dp).clip(RoundedCornerShape(6.dp)).background(c.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun initials(name: String) = name.split(Regex("[\\s@.]+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

@Composable
private fun Avatars(n: NoteItem) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        n.members.take(3).forEach { m ->
            Box(
                Modifier.size(20.dp).clip(CircleShape).background(runCatching { Color(android.graphics.Color.parseColor(m.color ?: "#94a3b8")) }.getOrDefault(Color.Gray))
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (m.avatar != null) coil.compose.AsyncImage(model = m.avatar, contentDescription = m.name, modifier = Modifier.fillMaxSize().clip(CircleShape))
                else Text(initials(m.name), fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteActionsSheet(
    note: NoteItem,
    notebooks: List<NoteNotebook>,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onMove: (String?) -> Unit,
    onDelete: () -> Unit,
    onNewNotebook: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(note.meta.title.ifBlank { nt("untitled") }, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            SheetRow(Icons.Outlined.PushPin, if (note.pinned) nt("pinned_on") + " ✓" else nt("pin"), onClick = onPin)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(nt("notebook").uppercase(locale()), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            SheetRow(Icons.Outlined.Book, nt("no_notebook"), selected = note.notebookId == null) { onMove(null) }
            notebooks.forEach { nb -> SheetRow(Icons.Outlined.Book, nb.name, tint = nbColor(nb.color), selected = note.notebookId == nb.id) { onMove(nb.id) } }
            SheetRow(Icons.Outlined.Edit, nt("new_notebook"), onClick = onNewNotebook)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            if (note.isOwner) SheetRow(Icons.Outlined.Delete, nt("move_to_trash"), tint = Rose, onClick = onDelete)
            else SheetRow(Icons.Outlined.ExitToApp, nt("leave_note"), tint = Rose, onClick = onDelete)
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, tint: Color? = null, selected: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 14.sp, color = if (tint == Rose) Rose else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f).padding(start = 16.dp))
        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = NotesAmber, modifier = Modifier.size(18.dp))
    }
}

// ---- peças comuns ---------------------------------------------------------------------

/** Cartão branco das páginas (notes-share-card). */
@Composable
private fun Modifier.notesCard(): Modifier = this
    .clip(RoundedCornerShape(14.dp))
    .background(MaterialTheme.colorScheme.surface)
    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(14.dp))

private val AVATAR_COLORS = listOf(0xFF7C3AED, 0xFFE11D48, 0xFF2563EB, 0xFF0F766E, 0xFFCA8A04, 0xFFDB2777, 0xFF0891B2, 0xFF4F46E5)

/** = avatarColor() de notesUi.js. */
private fun avatarColor(id: String?): Color {
    var h = 0L
    for (c in id.orEmpty()) h = (h * 31 + c.code) and 0xFFFFFFFFL
    return Color(AVATAR_COLORS[(h % AVATAR_COLORS.size).toInt()])
}

@Composable
private fun Avatar(name: String?, color: Color, size: Int = 24, url: String? = null) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        if (url != null) coil.compose.AsyncImage(model = url, contentDescription = name, modifier = Modifier.fillMaxSize().clip(CircleShape))
        else Text(initials(name.orEmpty()), fontSize = (size * 0.38f).sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun GroupLabel(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, dot: Color? = null, count: Int? = null, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) Box(Modifier.padding(end = 7.dp).size(6.dp).clip(CircleShape).background(dot))
        Text(text.uppercase(locale()), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = color)
        if (count != null) Text(" · $count", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** Estado do prazo (= dueStatus de notesUi.js). */
private fun dueStatus(due: String): String {
    val today = LocalDate.now()
    val d = runCatching { LocalDate.parse(due) }.getOrNull() ?: return "later"
    return when {
        d.isBefore(today) -> "overdue"
        d == today -> "today"
        d == today.plusDays(1) -> "tomorrow"
        else -> "later"
    }
}

@Composable
private fun DuePill(due: String) {
    val (label, _) = formatDue(due)
    val (bg, fg) = when (dueStatus(due)) {
        "overdue" -> Rose.copy(alpha = 0.12f) to Rose
        "today" -> NotesAmber.copy(alpha = 0.14f) to Color(0xFFB45309)
        else -> Blue.copy(alpha = 0.10f) to Blue
    }
    Text(
        label,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ---- Checklists ---------------------------------------------------------------------

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChecklistsPage(vm: NotesHomeViewModel, onOpenNote: (String) -> Unit) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    var collapsed by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(setOf("done")) }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (tasks == null) vm.loadTasks() }
    val list = tasks
    val notesById = state.notes.associateBy { it.id }
    val nbById = state.notebooks.associateBy { it.id }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            when {
                list == null -> items(4) {
                    Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)))
                }
                list.isEmpty() -> item {
                    Column(Modifier.fillMaxWidth().heightIn(min = 260.dp).notesCard().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Outlined.CheckBox, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(26.dp))
                        Text(nt("checklists_empty"), fontSize = 13.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                else -> {
                    val done = list.count { it.done }
                    val noteCount = list.map { it.noteId }.toSet().size
                    item { ProgressHeader(done, list.size, noteCount) }
                    val today = LocalDate.now().toString()
                    val open = list.filterNot { it.done }
                    data class G(val key: String, val label: String, val color: Color, val items: List<NoteTask>)
                    val groups = listOf(
                        G("overdue", nt("overdue"), Rose, open.filter { it.dueDate != null && it.dueDate < today }.sortedBy { it.dueDate }),
                        G("today", nt("today"), NotesAmber, open.filter { it.dueDate == today }),
                        G("upcoming", nt("upcoming"), Blue, open.filter { it.dueDate != null && it.dueDate > today }.sortedBy { it.dueDate }),
                        G("nodate", nt("no_date"), Color(0xFF6B7280), open.filter { it.dueDate == null }),
                        G("done", nt("done"), Emerald, list.filter { it.done }),
                    )
                    groups.filter { it.items.isNotEmpty() }.forEach { g ->
                        val isCollapsed = g.key in collapsed
                        item(key = "h-${g.key}") {
                            GroupLabel(
                                g.label, color = g.color, dot = g.color, count = g.items.size,
                                modifier = Modifier.clickable { collapsed = if (isCollapsed) collapsed - g.key else collapsed + g.key },
                            ) {
                                Icon(
                                    Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = if (isCollapsed) -90f else 0f },
                                )
                            }
                        }
                        if (!isCollapsed) item(key = "g-${g.key}") {
                            Column(Modifier.fillMaxWidth().notesCard()) {
                                g.items.forEachIndexed { i, t ->
                                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    val note = notesById[t.noteId]
                                    val nb = note?.notebookId?.let(nbById::get)
                                    val member = t.assigneeId?.let { a -> note?.members?.firstOrNull { it.userId == a } }
                                    TaskRow(t, nb, member, onToggle = { vm.toggleTask(t) }, onOpen = { onOpenNote(t.noteId) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(done: Int, total: Int, notes: Int) {
    val pct = if (total > 0) (done * 100f / total).roundToInt() else 0
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Row(Modifier.fillMaxWidth().notesCard().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val stroke = 5.dp.toPx()
                val inset = stroke / 2 + 1.dp.toPx()
                val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                if (pct > 0) drawArc(
                    NotesAmber, -90f, 360f * pct / 100f, false, topLeft, arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
            Text("$pct%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(nt("x_of_y_done", "x" to done, "y" to total), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(ntp("from_n_notes", notes) + " · " + nt("ticking_hint"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TaskRow(t: NoteTask, nb: NoteNotebook?, assignee: co.golink.tester.data.notes.NoteMember?, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Caixa (note-task-box): quadrado arredondado, âmbar quando feita.
        Box(
            Modifier
                .padding(top = 1.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (t.done) NotesAmber else Color.Transparent)
                .border(1.6.dp, if (t.done) NotesAmber else MaterialTheme.colorScheme.outline.copy(alpha = if (t.canEdit) 0.8f else 0.35f), RoundedCornerShape(6.dp))
                .clickable(enabled = t.canEdit, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (t.done) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                t.text.ifBlank { "—" },
                fontSize = 13.5.sp,
                fontWeight = if (t.done) FontWeight.Normal else FontWeight.Medium,
                color = if (t.done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (t.done) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(nb?.let { nbColor(it.color) } ?: Color(0xFF9CA3AF)))
                Text(t.noteTitle.ifBlank { nt("untitled") }, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (t.dueDate != null && !t.done) Box(Modifier.padding(start = 8.dp, top = 1.dp)) { DuePill(t.dueDate) }
        if (assignee != null) Box(Modifier.padding(start = 6.dp)) { Avatar(assignee.name, avatarColor(assignee.userId), 24, assignee.avatar) }
    }
}

// ---- Pesquisa -------------------------------------------------------------------------

@Composable
private fun SearchPage(vm: NotesHomeViewModel, onOpen: (NoteItem) -> Unit) {
    val s by vm.search.collectAsStateWithLifecycle()
    val notebooks = vm.state.collectAsState().value.notebooks.associateBy { it.id }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, if (s.query.isNotEmpty()) NotesAmber else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(17.dp))
                Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    if (s.query.isEmpty()) Text(nt("search_placeholder"), fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                    BasicTextField(
                        value = s.query,
                        onValueChange = vm::setQuery,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                        cursorBrush = SolidColor(NotesAmber),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (s.query.isNotEmpty()) Icon(Icons.Outlined.Close, contentDescription = nt("clear"), tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp).clip(CircleShape).clickable { vm.setQuery("") })
            }
        }
        item {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(nt("filter_all"), null, s.filter == "all") { vm.setFilter("all") }
                Chip(nt("shared"), null, s.filter == "shared") { vm.setFilter("shared") }
                Chip(nt("filter_files"), null, s.filter == "files") { vm.setFilter("files") }
            }
        }
        if (s.query.isNotBlank() && s.indexed < s.total) item {
            Text(nt("search_indexing", "x" to s.indexed, "y" to s.total), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
        when {
            s.query.isBlank() -> item {
                Text(nt("search_hint"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp).fillMaxWidth().notesCard().padding(24.dp))
            }
            s.results.isEmpty() -> if (s.indexed >= s.total) item {
                Text(nt("no_results"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp).fillMaxWidth().notesCard().padding(24.dp))
            }
            else -> {
                item { GroupLabel(ntp("n_notes", s.results.size)) }
                items(s.results, key = { it.note.id }) { hit -> SearchCard(hit, notebooks[hit.note.notebookId]) { onOpen(hit.note) } }
            }
        }
        item {
            Row(
                Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Emerald.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Emerald, modifier = Modifier.padding(top = 1.dp).size(14.dp))
                Text(nt("search_privacy"), fontSize = 12.sp, color = Color(0xFF047857), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SearchCard(hit: SearchHit, nb: NoteNotebook?, onClick: () -> Unit) {
    val n = hit.note
    val mark = Color(0xFFFDE68A)
    Column(Modifier.padding(vertical = 4.dp).fillMaxWidth().notesCard().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(n.meta.title.ifBlank { nt("untitled") }, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            if (nb != null) Box(Modifier.padding(start = 8.dp)) { NotebookTag(nb) }
        }
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                hit.parts.forEach { (text, isHit) ->
                    if (isHit) withStyle(androidx.compose.ui.text.SpanStyle(background = mark, color = Color(0xFF1F2937), fontWeight = FontWeight.SemiBold)) { append(text) }
                    else append(text)
                }
            },
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            nt("edited_ago", "time" to notesAgo(n.updatedAt)) + n.meta.tags.joinToString("") { " · #$it" },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ---- Histórico ------------------------------------------------------------------------

@Composable
private fun HistoryPage(vm: NotesHomeViewModel) {
    val h by vm.history.collectAsStateWithLifecycle()
    val trash by vm.trash.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { if (trash == null) vm.loadTrash() }
    Column(Modifier.fillMaxSize()) {
        // Controlo segmentado Versões | Lixo · N (notes-seg).
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(3.dp),
        ) {
            listOf("versions" to nt("versions"), "trash" to nt("trash") + " · " + (trash?.size ?: 0)).forEach { (key, label) ->
                val on = h.tab == key
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                        .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { vm.setHistoryTab(key) }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (h.tab == "trash") TrashTab(vm) else VersionsTab(vm, h)
    }
}

@Composable
private fun VersionsTab(vm: NotesHomeViewModel, h: HistoryState) {
    val state by vm.state.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    var confirmRestore by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    val notes = state.notes.filter { !it.locked }.sortedByDescending { it.updatedAt }
    // Como a web: abre logo na nota mais recente.
    androidx.compose.runtime.LaunchedEffect(h.noteId, notes.isEmpty()) {
        if ((h.noteId == null || notes.none { it.id == h.noteId }) && notes.isNotEmpty()) vm.pickHistoryNote(notes.first().id)
    }
    if (notes.isEmpty()) {
        Text(nt("no_notes_history"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp).fillMaxWidth().notesCard().padding(24.dp))
        return
    }
    val note = notes.firstOrNull { it.id == h.noteId }
    val nb = note?.notebookId?.let { id -> state.notebooks.firstOrNull { it.id == id } }
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
        item {
            Box {
                Row(Modifier.fillMaxWidth().notesCard().clickable { picker = true }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(nb?.let { nbColor(it.color) } ?: NotesAmber))
                    Text(note?.meta?.title?.ifBlank { nt("untitled") } ?: nt("untitled"), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    Text(nt("change"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
                androidx.compose.material3.DropdownMenu(expanded = picker, onDismissRequest = { picker = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                    notes.forEach { n ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(n.meta.title.ifBlank { nt("untitled") }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (n.id == h.noteId) FontWeight.SemiBold else FontWeight.Normal) },
                            onClick = { picker = false; if (n.id != h.noteId) vm.pickHistoryNote(n.id) },
                        )
                    }
                }
            }
            Text(nt("kept_days", "n" to h.keepDays), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp))
        }
        // Linha do tempo: "Agora" + versões; a escolhida abre as diferenças por baixo.
        item(key = "now") {
            TimelineItem(lineColor, last = h.versions.isNullOrEmpty() && h.versions != null, avatar = { Avatar(me?.name, Color(0xFF111827), 24, me?.avatar) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(nt("now"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        nt("current").uppercase(locale()), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = Color(0xFF047857),
                        modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(4.dp)).background(Emerald.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                Text(nt("you") + " · " + nt("current_version"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val versions = h.versions
        when {
            versions == null -> items(3) { Box(Modifier.padding(start = 36.dp, top = 6.dp, bottom = 6.dp).fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))) }
            versions.isEmpty() -> item {
                Text(nt("no_versions"), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
            }
            else -> items(versions, key = { it.id }) { v ->
                val open = v.id == h.versionId
                TimelineItem(
                    lineColor,
                    last = v == versions.last(),
                    avatar = { Avatar(v.authorName, avatarColor(v.authorId), 24) },
                    onClick = { vm.pickVersion(if (open) null else v.id) },
                    below = if (!open) null else ({ DiffCard(h, note?.canEdit == true) { confirmRestore = true } }),
                ) {
                    Text(versionStamp(v.createdAt), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text((v.authorName ?: "—") + " · " + ntp("n_changes", v.changes), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (confirmRestore) {
        ConfirmDialog(
            title = nt("restore_version") + "?",
            message = nt("restore_keeps"),
            confirmText = nt("restore_version"),
            onDismiss = { confirmRestore = false },
            onConfirm = { confirmRestore = false; vm.restoreVersion() },
        )
    }
}

/** "Hoje 09:40", "Ontem 18:12", "3 Nov 11:05" (= stamp() de History.vue). */
private fun versionStamp(iso: String?): String {
    val d = iso?.let { runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()) }.getOrNull() } ?: return "—"
    val time = d.format(DateTimeFormatter.ofPattern("HH:mm"))
    val today = LocalDate.now()
    return when (d.toLocalDate()) {
        today -> nt("today") + " " + time
        today.minusDays(1) -> nt("yesterday") + " " + time
        else -> d.format(DateTimeFormatter.ofPattern("d MMM", locale())) + " " + time
    }
}

@Composable
private fun TimelineItem(
    lineColor: Color,
    last: Boolean,
    avatar: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        // Coluna do avatar com a linha vertical a ligar os itens.
        Box(Modifier.width(24.dp).fillMaxHeight()) {
            if (!last) Box(Modifier.align(Alignment.TopCenter).padding(top = 12.dp).width(2.dp).fillMaxHeight().background(lineColor))
            Box(Modifier.padding(top = 8.dp)) { avatar() }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 7.dp, horizontal = 4.dp),
            ) { content() }
            if (below != null) Box(Modifier.padding(bottom = 8.dp)) { below() }
        }
    }
}

@Composable
private fun DiffCard(h: HistoryState, canEdit: Boolean, onRestore: () -> Unit) {
    val diff = h.diff
    Column(Modifier.fillMaxWidth().notesCard().padding(12.dp)) {
        if (diff == null) {
            repeat(4) { Box(Modifier.padding(vertical = 3.dp).fillMaxWidth().height(18.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))) }
            return@Column
        }
        val ops = diff.mapNotNull { (it as? JsonObject) }
        val changed = ops.filter { it["op"]?.jsonPrimitive?.contentOrNull != "=" }
        val added = changed.count { it["op"]?.jsonPrimitive?.contentOrNull == "+" }
        val removed = changed.size - added
        Row(Modifier.padding(bottom = 8.dp)) {
            Text("+$added ${nt("added")}", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Emerald)
            Text("−$removed ${nt("removed")}", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Rose, modifier = Modifier.padding(start = 8.dp))
        }
        if (changed.isEmpty()) {
            Text(nt("no_differences"), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
        }
        ops.forEach { d ->
            val op = d["op"]?.jsonPrimitive?.contentOrNull ?: "="
            val line = d["line"] as? JsonObject
            val text = line?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
            val heading = line?.get("kind")?.jsonPrimitive?.contentOrNull == "heading"
            val (bg, fg) = when (op) {
                "-" -> Rose.copy(alpha = 0.09f) to Color(0xFFBE123C)
                "+" -> Emerald.copy(alpha = 0.10f) to Color(0xFF047857)
                else -> Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(Modifier.padding(vertical = 1.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 3.dp)) {
                Text(if (op == "=") "" else if (op == "-") "−" else "+", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = fg, textAlign = TextAlign.Center, modifier = Modifier.width(12.dp))
                Text(
                    text, fontSize = 12.5.sp, color = fg,
                    fontWeight = if (heading) FontWeight.SemiBold else FontWeight.Normal,
                    textDecoration = if (op == "-") TextDecoration.LineThrough else null,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        if (canEdit && h.versionId != null) {
            Box(
                Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (h.restoring) NotesAmber.copy(alpha = 0.5f) else NotesAmber)
                    .clickable(enabled = !h.restoring, onClick = onRestore).padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(nt("restore_version"), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
            }
            Text(nt("restore_keeps"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }
}

@Composable
private fun TrashTab(vm: NotesHomeViewModel) {
    val trash by vm.trash.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmForever by remember { mutableStateOf<NoteItem?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val selection by vm.selection.collectAsStateWithLifecycle()
    val selected = selection?.takeIf { it.scope == "trash" }?.ids
    val haptic = LocalHapticFeedback.current
    val list = trash
    val keep = state.trashDays
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().notesCard().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 1.dp).size(15.dp))
                Text(nt("trash_info", "n" to keep), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
            }
        }
        item {
            GroupLabel(ntp("n_notes", list?.size ?: 0)) {
                if (!list.isNullOrEmpty() && selected == null) Text(
                    nt("select"), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB45309),
                    modifier = Modifier.padding(end = 10.dp).clip(RoundedCornerShape(6.dp)).clickable { vm.startSelection("trash") }.padding(horizontal = 4.dp, vertical = 2.dp),
                )
                if (!list.isNullOrEmpty() && selected == null) Text(nt("empty_trash"), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Rose, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { confirmEmpty = true }.padding(horizontal = 4.dp, vertical = 2.dp))
            }
        }
        when {
            list == null -> items(3) { Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(110.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))) }
            list.isEmpty() -> item {
                Text(nt("trash_empty"), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().notesCard().padding(24.dp))
            }
            else -> items(list, key = { it.id }) { n ->
                val deleted = n.deletedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
                val days = deleted?.let { ((System.currentTimeMillis() - it.toEpochMilli()) / 86_400_000L).toInt() } ?: 0
                val left = (keep - days).coerceAtLeast(0)
                val urgent = left <= 7
                val tone = if (urgent) Rose else NotesAmber
                val isSel = selected?.contains(n.id)
                Column(
                    Modifier.padding(vertical = 4.dp).fillMaxWidth().notesCard()
                        .selectedCard(isSel == true, RoundedCornerShape(14.dp))
                        .then(
                            cardModifier(
                                onClick = { if (selected != null) vm.toggleSelected("trash", n.id) },
                                onLong = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (selected != null) vm.toggleSelected("trash", n.id) else vm.startSelection("trash", n.id)
                                },
                            ),
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.meta.title.ifBlank { nt("untitled") }, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(ntp("days_left", left), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tone, modifier = Modifier.padding(start = 8.dp))
                        if (isSel != null) Box(Modifier.padding(start = 8.dp)) { SelectCircle(isSel) }
                    }
                    Text(n.meta.snippet.ifBlank { "—" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))) {
                        Box(Modifier.fillMaxWidth(if (keep > 0) left.toFloat() / keep else 0f).fillMaxHeight().background(tone))
                    }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(ntp("deleted_days_ago", days), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (selected == null) Text(
                            nt("delete"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Rose,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { confirmForever = n }.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                        if (selected == null) Text(
                            nt("restore"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB45309),
                            modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp)).background(NotesAmber.copy(alpha = 0.16f)).clickable { vm.restore(n) }.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
    confirmForever?.let { n ->
        ConfirmDialog(
            title = nt("delete_forever_title"),
            message = nt("delete_forever_text"),
            confirmText = nt("delete_forever"),
            destructive = true,
            onDismiss = { confirmForever = null },
            onConfirm = { vm.deleteForever(n); confirmForever = null },
        )
    }
    if (confirmEmpty) {
        ConfirmDialog(
            title = nt("empty_trash_title"),
            message = nt("empty_trash_text"),
            confirmText = nt("empty_trash"),
            destructive = true,
            onDismiss = { confirmEmpty = false },
            onConfirm = { vm.emptyTrash(); confirmEmpty = false },
        )
    }
}
