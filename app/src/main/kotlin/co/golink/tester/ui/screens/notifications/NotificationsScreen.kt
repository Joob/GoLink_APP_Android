package co.golink.tester.ui.screens.notifications

import co.golink.tester.ui.components.drawer.isPermanentDrawer
import co.golink.tester.ui.i18n.I18n
import co.golink.tester.ui.i18n.tr
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.state.ToggleableState
import co.golink.tester.ui.components.SelectCircle
import co.golink.tester.ui.components.SelectionAction
import co.golink.tester.ui.components.SelectionDock
import co.golink.tester.ui.components.SelectionTopBar
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.DpOffset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.domain.notifications.Notification
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Instante local da notificação (null sem ISO). */
private fun Notification.localTime(zone: ZoneId): ZonedDateTime? =
    createdAtInstant?.atZone(zone)

private val HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Formatos com o mês no idioma da app; criar um formatter por linha pesa. */
private class DayFormats(locale: Locale) {
    val sameYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", locale)
    val otherYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
}

/** Hoje/ontem: HH:mm; antes: "27 out." (com o ano se não for o atual) — como na web. */
private fun timeLabel(n: Notification, t: ZonedDateTime?, today: LocalDate, days: DayFormats): String {
    if (t == null) return n.createdAt.orEmpty()
    return when {
        !t.toLocalDate().isBefore(today.minusDays(1)) -> t.format(HOUR_FORMAT)
        t.year == today.year -> co.golink.tester.ui.common.UserDisplayPrefs.format(t.toLocalDate(), withYear = false) ?: t.format(days.sameYear)
        else -> co.golink.tester.ui.common.UserDisplayPrefs.format(t.toLocalDate()) ?: t.format(days.otherYear)
    }
}

private data class FilterChip(val key: String, val label: String, val count: Int)

/** Notificação já com a data lida uma vez (o parse não se repete a cada recomposição). */
private class Entry(val n: Notification, val time: ZonedDateTime?, val sortTime: ZonedDateTime?)

private data class LocalClock(val today: LocalDate, val zone: ZoneId)

/**
 * Dia de hoje e fuso, sempre atuais (Hoje/Ontem e as horas não ficam velhos).
 * O delay até à meia-noite corre em uptime e pára com o telemóvel a dormir, por
 * isso também se relê ao voltar ao ecrã e quando o sistema muda data/hora/fuso.
 */
@Composable
private fun rememberLocalClock(): LocalClock {
    fun now() = ZoneId.systemDefault().let { zone -> LocalClock(LocalDate.now(zone), zone) }
    var clock by remember { mutableStateOf(now()) }
    val refresh = { now().let { if (it != clock) clock = it } }

    // Em ciclo (e não por mudança de `clock`): se o relógio recuar, o refresh dá
    // o mesmo dia e um efeito com chave não voltava a agendar.
    LaunchedEffect(Unit) {
        while (true) {
            val c = now()
            val next = c.today.plusDays(1).atStartOfDay(c.zone)
            delay(Duration.between(ZonedDateTime.now(c.zone), next).toMillis().coerceAtLeast(1_000))
            refresh()
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) = refresh()
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_DATE_CHANGED)
            addAction(android.content.Intent.ACTION_TIME_CHANGED)
            addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return clock
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenSecurityActivity: () -> Unit = {},
    onOpenSupportTicket: (id: String, admin: Boolean) -> Unit = { _, _ -> },
    onOpenCalendar: (event: String?, date: String?) -> Unit = { _, _ -> },
    onOpenLegacy: (pane: String) -> Unit = {},
    onOpenNotes: (path: String) -> Unit = {},
    onOpenReceipts: (pane: String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val hasMoreOlder by viewModel.hasMoreOlder.collectAsStateWithLifecycle()
    val undoable by viewModel.undoable.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) {
        state.toast?.let { snackbarHost.showSnackbar(it); viewModel.consumeToast() }
    }
    // Eliminar com "Desfazer": o pedido só sai quando o aviso fecha sem desfazer.
    LaunchedEffect(undoable) {
        val id = undoable ?: return@LaunchedEffect
        val result = snackbarHost.showSnackbar(
            "Notificação eliminada".tr(),
            actionLabel = "Desfazer".tr(),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(id) else viewModel.commitDelete(id)
    }

    val clock = rememberLocalClock()
    val today = clock.today
    val dayFormats = remember(I18n.lang) { DayFormats(Locale.forLanguageTag(I18n.lang)) }
    // Ordem pelo sort_at (uma acordada do snooze volta ao topo); a hora mostrada é a de criação.
    val sorted = remember(items, clock.zone) {
        items.map { Entry(it, it.localTime(clock.zone), it.sortAtInstant?.atZone(clock.zone)) }
            .sortedByDescending { it.sortTime?.toInstant() }
    }
    val unreadCount = items.count { !it.isRead }
    val hasRead = items.any { it.isRead }

    // Chips: Todas, Não lidas e um por grupo com notificações (contagem = não lidas).
    val groupCounts = remember(items) {
        val byGroup = items.groupBy { groupOf(it.category) }
        NOTIFICATION_GROUPS.mapNotNull { (group, _) ->
            byGroup[group]?.let { list -> group to list.count { !it.isRead } }
        }
    }
    val chips = buildList {
        add(FilterChip("all", "Todas".tr(), 0))
        add(FilterChip("unread", "Não lidas".tr(), unreadCount))
        groupCounts.forEach { (group, unread) -> add(FilterChip(group, groupLabel(group), unread)) }
    }
    var chosen by rememberSaveable { mutableStateOf("all") }
    // O chip ativo deixou de existir (ex. apagou a última): volta a Todas — e
    // fica lá (sem repor `chosen`, voltaria sozinho ao chegar outra do grupo).
    val filter = chosen.takeIf { key -> chips.any { it.key == key } } ?: "all"
    // Só com a lista carregada: ao restaurar o processo os itens chegam depois e
    // o chip guardado não pode ser trocado por Todas no primeiro frame.
    val loaded = items.isNotEmpty() && !state.isLoading
    LaunchedEffect(filter, loaded) { if (loaded && chosen != filter) chosen = filter }
    // As do filtro que já cá estão (efeito local das ações "todas as do filtro").
    val matches: (Notification) -> Boolean = remember(filter) {
        when (filter) {
            "all" -> { _: Notification -> true }
            "unread" -> { n: Notification -> !n.isRead }
            else -> { n: Notification -> groupOf(n.category) == filter }
        }
    }

    // Destinos das ações calculados uma vez por lista (não em cada linha a cada
    // recomposição), com os callbacks de navegação sempre atuais.
    val openSecurity by rememberUpdatedState(onOpenSecurityActivity)
    val openTicket by rememberUpdatedState(onOpenSupportTicket)
    val openCalendar by rememberUpdatedState(onOpenCalendar)
    val openLegacy by rememberUpdatedState(onOpenLegacy)
    val openNotes by rememberUpdatedState(onOpenNotes)
    val openReceipts by rememberUpdatedState(onOpenReceipts)
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val actionOf = remember(uriHandler) {
        { n: Notification ->
            actionFor(
                n,
                { openSecurity() },
                { id, admin -> openTicket(id, admin) },
                { e, d -> openCalendar(e, d) },
                { pane -> openLegacy(pane) },
                { path -> openNotes(path) },
                { pane -> openReceipts(pane) },
                // Só https no nosso domínio (os App Links voltam à app); o resto é ignorado.
                { url ->
                    if (co.golink.tester.ui.navigation.AppLinkRouter.isOwnHttpsLink(android.net.Uri.parse(url))) {
                        runCatching { uriHandler.openUri(url) }
                    }
                },
            )
        }
    }

    // As do chip ativo, pela ordem do ecrã (a seleção usa a mesma lista).
    val visible = remember(sorted, filter) { sorted.filter { matches(it.n) } }
    val sections = remember(visible, today) {
        visible
            .groupBy { e ->
                val d = e.sortTime?.toLocalDate()
                when {
                    d == null -> 2
                    !d.isBefore(today) -> 0
                    !d.isBefore(today.minusDays(1)) -> 1
                    else -> 2
                }
            }
            .toSortedMap()
    }
    val visibleIds = remember(visible) { visible.map { it.n.id } }
    val filterUnread = if (filter == "all" || filter == "unread") unreadCount > 0 else visible.any { !it.n.isRead }
    val filterReadIds = remember(visible) { visible.filter { it.n.isRead }.map { it.n.id } }
    // "Limpar lidas": em Todas/Não lidas apaga todas as lidas; num grupo, as dele.
    val canClearRead = if (filter == "all" || filter == "unread") hasRead else filterReadIds.isNotEmpty()

    // Seleção: tira as que desapareceram; Voltar sai da seleção.
    val sel = selection
    LaunchedEffect(items) { viewModel.pruneSelection(items.mapTo(HashSet()) { it.id }) }
    BackHandler(enabled = sel != null) { viewModel.clearSelection() }
    val allLoadedSelected = sel != null && visibleIds.isNotEmpty() && sel.ids.containsAll(visibleIds)
    // Total do filtro no servidor (o GET só traz 50 lidas): "Selecionar todas as N".
    val serverTotal = stats?.let { st ->
        when (filter) {
            "all" -> st.total
            "unread" -> st.unread
            else -> st.groups[filter]?.total ?: 0
        }
    }
    val selectedCount = if (sel?.allInFilter == true) (serverTotal ?: sel.ids.size) else sel?.ids?.size ?: 0
    val selectedItems = remember(items, sel) { sel?.let { s -> items.filter { it.id in s.ids } }.orEmpty() }
    val canMarkRead = sel?.allInFilter == true || selectedItems.any { !it.isRead }
    val canMarkUnread = sel?.allInFilter == true || selectedItems.any { it.isRead }

    var confirm by remember { mutableStateOf<String?>(null) }
    when (confirm) {
        "bulk-delete" -> ConfirmDialog(
            title = if (selectedCount == 1) "Eliminar esta notificação?".tr() else "Eliminar %d notificações?".tr().format(selectedCount),
            message = "Esta ação não elimina o pedido nem a atividade a que se refere.".tr(),
            confirmText = "Eliminar".tr(),
            destructive = true,
            onDismiss = { confirm = null },
            onConfirm = { confirm = null; viewModel.bulkSelected("delete", filter, matches) },
        )
        "clear-read" -> ConfirmDialog(
            title = "Limpar as notificações lidas?".tr(),
            message = if (filter == "all" || filter == "unread") "Todas as notificações já lidas são eliminadas.".tr()
            else "As notificações lidas de %s são eliminadas.".tr().format(groupLabel(filter)),
            confirmText = "Limpar lidas".tr(),
            destructive = true,
            onDismiss = { confirm = null },
            onConfirm = { confirm = null; viewModel.clearRead(filter, filterReadIds) },
        )
    }

    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val answering by viewModel.answering.collectAsStateWithLifecycle()

    // Popup de detalhe: abrir marca como lida; no popup e no ⋮ fica "Marcar como não lida".
    items.firstOrNull { it.id == openId }?.let { n ->
        val target = actionOf(n)
        NotificationDetailDialog(
            notification = n,
            groupLabel = groupLabel(groupOf(n.category)),
            onDismiss = { openId = null },
            onToggleRead = { viewModel.toggleRead(n); openId = null },
            onDelete = { viewModel.delete(n.id); openId = null },
            answering = n.id in answering,
            onAnswer = if (n.pendingAnswer) { yes -> viewModel.answer(n, yes) } else null,
            onAction = target?.let { go -> { if (!n.isRead) viewModel.markRead(n.id); openId = null; go() } },
        )
    }

    val haptic = LocalHapticFeedback.current
    val accent = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                if (sel != null) {
                    SelectionTopBar(
                        title = if (selectedCount == 1) "1 selecionada".tr() else "%d selecionadas".tr().format(selectedCount),
                        allSelected = allLoadedSelected,
                        onClose = viewModel::clearSelection,
                        onToggleAll = { if (allLoadedSelected) viewModel.clearSelection() else viewModel.selectAll(visibleIds) },
                        selectAllLabel = "Selecionar tudo".tr(),
                        deselectAllLabel = "Desmarcar".tr(),
                        closeLabel = "Cancelar seleção".tr(),
                        accent = accent,
                        accentBg = accent.copy(alpha = 0.12f),
                    )
                } else Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp),
                ) {
                    // Telemóvel: hambúrguer para a gaveta, como no Drive/Calendário; o X fecha.
                    if (isPermanentDrawer()) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar".tr())
                        }
                    } else {
                        IconButton(onClick = onOpenMenu) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu".tr())
                        }
                    }
                    Text(
                        "Notificações".tr(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    // Respeita o chip ativo (num grupo, só as desse grupo).
                    IconButton(onClick = { viewModel.markAllRead(filter, matches) }, enabled = filterUnread) {
                        Icon(Icons.Filled.DoneAll, contentDescription = "Marcar todas lidas".tr(), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Definições".tr(), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar".tr(), modifier = Modifier.size(20.dp))
                    }
                }
                if (items.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                    ) {
                        items(chips, key = { it.key }) { chip ->
                            // Trocar de chip larga a seleção (era de outro filtro).
                            ChipButton(chip, selected = filter == chip.key) {
                                if (chip.key != filter) viewModel.clearSelection()
                                chosen = chip.key
                            }
                        }
                    }
                }
                // Gmail: todas as carregadas escolhidas e o servidor tem mais no filtro.
                if (sel != null && serverTotal != null && serverTotal > visibleIds.size && (sel.allInFilter || allLoadedSelected)) {
                    SelectAllBanner(
                        text = if (sel.allInFilter) "Todas as %d selecionadas.".tr().format(serverTotal)
                        else "Selecionadas %d.".tr().format(sel.ids.size),
                        action = if (sel.allInFilter) "Limpar seleção".tr() else "Selecionar todas as %d".tr().format(serverTotal),
                        onAction = { if (sel.allInFilter) viewModel.clearSelection() else viewModel.selectAllInFilter() },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        },
        bottomBar = {
            if (sel != null) {
                SelectionDock(Modifier.navigationBarsPadding()) {
                    SelectionAction(Icons.Outlined.Done, "Marcar como lidas".tr(), { viewModel.bulkSelected("read", filter, matches) }, enabled = canMarkRead)
                    SelectionAction(Icons.Outlined.MarkEmailUnread, "Marcar como não lidas".tr(), { viewModel.bulkSelected("unread", filter, matches) }, enabled = canMarkUnread)
                    SelectionAction(Icons.Outlined.DeleteOutline, "Eliminar".tr(), { confirm = "bulk-delete" }, danger = true, enabled = selectedCount > 0)
                }
            } else if (canClearRead) {
                Column(Modifier.navigationBarsPadding()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        TextButton(onClick = { confirm = "clear-read" }) {
                            Text(
                                "Limpar lidas".tr(),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading && items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            } else PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::pullRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (sections.isEmpty()) {
                        // Dentro da lista: assim o puxar para atualizar também funciona aqui.
                        item(key = "empty") {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 16.dp),
                            ) {
                                val failed = state.loadFailed && items.isEmpty()
                                Icon(
                                    if (failed) Icons.Outlined.CloudOff else Icons.Outlined.NotificationsOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    when {
                                        failed -> "Não foi possível carregar as notificações.".tr()
                                        items.isEmpty() -> "Sem notificações".tr()
                                        else -> "Ainda não há nada aqui.".tr()
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                if (failed) {
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(onClick = viewModel::refresh) {
                                        Text("Tentar de novo".tr(), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    sections.forEach { (bucket, list) ->
                        item(key = "h$bucket") {
                            val ids = list.map { it.n.id }
                            SectionHeader(
                                title = when (bucket) {
                                    0 -> "Hoje".tr()
                                    1 -> "Ontem".tr()
                                    else -> "Anteriores".tr()
                                },
                                selectState = sel?.let { s ->
                                    val n = ids.count { it in s.ids }
                                    when {
                                        s.allInFilter || n == ids.size -> ToggleableState.On
                                        n == 0 -> ToggleableState.Off
                                        else -> ToggleableState.Indeterminate
                                    }
                                },
                                onToggleSelect = {
                                    val s = selection
                                    viewModel.setSelected(ids, select = s == null || s.allInFilter.not() && !s.ids.containsAll(ids))
                                },
                            )
                        }
                        items(list, key = { it.n.id }) { entry ->
                            val n = entry.n
                            // Por linha e não por lista: só as visíveis pagam isto.
                            val time = remember(n, today, dayFormats) { timeLabel(n, entry.time, today, dayFormats) }
                            val target = remember(n) { actionOf(n) }
                            val selecting = sel != null
                            SwipeableRow(
                                notification = n,
                                enabled = !selecting,
                                onToggleRead = { viewModel.toggleRead(n) },
                                onDelete = { viewModel.deleteWithUndo(n.id) },
                            ) {
                                NotificationRow(
                                    notification = n,
                                    time = time,
                                    onTap = {
                                        if (selecting) viewModel.toggleSelected(n.id)
                                        else {
                                            openId = n.id
                                            if (!n.isRead) viewModel.markRead(n.id)
                                        }
                                    },
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (selecting) viewModel.toggleSelected(n.id) else viewModel.startSelection(n.id)
                                    },
                                    onAction = target?.let { go -> { if (!n.isRead) viewModel.markRead(n.id); go() } },
                                    onToggleRead = { viewModel.toggleRead(n) },
                                    onDelete = { viewModel.deleteWithUndo(n.id) },
                                    answering = n.id in answering,
                                    onAnswer = if (n.pendingAnswer) { yes -> viewModel.answer(n, yes) } else null,
                                    selected = n.id == openId,
                                    selectionMode = selecting,
                                    checked = sel?.let { it.allInFilter || n.id in it.ids } == true,
                                )
                            }
                        }
                    }
                    // As lidas antigas não vêm no GET (só as 50 mais recentes): "Carregar mais".
                    if (hasMoreOlder && filter != "unread" && sections.isNotEmpty()) {
                        item(key = "older") {
                            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                if (state.loadingOlder) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    TextButton(onClick = viewModel::loadOlder) {
                                        Text("Carregar mais".tr(), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            co.golink.tester.ui.components.E2EEncryptedFlash(
                trigger = Unit,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Título de secção (Hoje/Ontem/Anteriores); em seleção, a caixa marca/desmarca a secção. */
@Composable
private fun SectionHeader(title: String, selectState: ToggleableState?, onToggleSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = if (selectState != null) 4.dp else 12.dp, top = if (selectState != null) 8.dp else 16.dp, bottom = if (selectState != null) 0.dp else 8.dp),
    ) {
        if (selectState != null) {
            TriStateCheckbox(state = selectState, onClick = onToggleSelect)
        }
        Text(
            title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

/** Faixa do "Selecionar todas as N" (como no Gmail). */
@Composable
private fun SelectAllBanner(text: String, action: String, onAction: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, fontSize = 13.sp, color = scheme.onSurface, modifier = Modifier.weight(1f, fill = false))
        TextButton(onClick = onAction) {
            Text(action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.primary)
        }
    }
}

/**
 * Deslizar: para a esquerda elimina (com "Desfazer"); para a direita alterna
 * lida/não lida e a linha volta ao sítio. Desligado em modo seleção.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRow(
    notification: Notification,
    enabled: Boolean,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val swipe = rememberSwipeToDismissBoxState()
    val toggle by rememberUpdatedState(onToggleRead)
    val delete by rememberUpdatedState(onDelete)
    LaunchedEffect(swipe.currentValue) {
        when (swipe.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> { toggle(); swipe.reset() }
            SwipeToDismissBoxValue.EndToStart -> delete()
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }
    val scheme = MaterialTheme.colorScheme
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
        modifier = Modifier.padding(bottom = 4.dp),
        backgroundContent = {
            val direction = swipe.dismissDirection
            val toEnd = direction == SwipeToDismissBoxValue.StartToEnd
            // Parada (Settled) sem cor: a linha lida é transparente por cima.
            val bg = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> scheme.error
                SwipeToDismissBoxValue.StartToEnd -> scheme.primary
                else -> Color.Transparent
            }
            Box(
                contentAlignment = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 20.dp),
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        when {
                            !toEnd -> Icons.Outlined.DeleteOutline
                            notification.isRead -> Icons.Outlined.MarkEmailUnread
                            else -> Icons.Outlined.Done
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
    ) { content() }
}

/** Chip de filtro: ativo preenchido escuro, inativo com borda (como na web). */
@Composable
private fun ChipButton(chip: FilterChip, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fg = if (selected) scheme.surface else scheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(scheme.onSurface)
                else Modifier.border(1.dp, scheme.outline, CircleShape),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(chip.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg)
        if (chip.count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                if (chip.count > 99) "99+" else chip.count.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = fg.copy(alpha = 0.65f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationRow(
    notification: Notification,
    time: String,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onAction: (() -> Unit)?,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit,
    answering: Boolean = false,
    /** Convite por responder: true = Aceitar, false = Recusar. */
    onAnswer: ((Boolean) -> Unit)? = null,
    /** Linha aberta no popup: contorno verde, como no mockup. */
    selected: Boolean = false,
    /** Modo seleção: círculo no lugar do ícone e sem ⋮. */
    selectionMode: Boolean = false,
    checked: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val unread = !notification.isRead
    val style = notificationIcon(notification.category)
    var menuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            // Base opaca: ao deslizar, a cor do fundo não aparece através da linha.
            .background(scheme.background)
            .background(
                when {
                    checked -> scheme.primary.copy(alpha = 0.16f)
                    unread -> scheme.primary.copy(alpha = 0.08f)
                    else -> Color.Transparent
                },
            )
            .then(if (selected) Modifier.border(1.5.dp, scheme.primary, RoundedCornerShape(12.dp)) else Modifier)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
    ) {
        if (unread) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 15.dp)
                    .offset(x = (-12).dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
            )
        }
        Row(verticalAlignment = Alignment.Top) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selectionMode) Color.Transparent else style.bg),
            ) {
                if (selectionMode) SelectCircle(checked, scheme.primary)
                else Icon(style.icon, contentDescription = null, tint = style.fg, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        notification.title.ifBlank { groupLabel(groupOf(notification.category)) },
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        time,
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                if (notification.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        notification.description,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onAnswer != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlineAction("Recusar".tr(), enabled = !answering) { onAnswer(false) }
                        OutlineAction("Aceitar".tr(), primary = true, enabled = !answering) { onAnswer(true) }
                        if (answering) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                } else {
                    rsvpLabel(notification.rsvpStatus)?.let { label ->
                        Spacer(Modifier.height(8.dp))
                        RsvpDone(label)
                    }
                    if (onAction != null) {
                        Spacer(Modifier.height(10.dp))
                        OutlineAction(notification.actionButton?.takeIf { it.isNotBlank() } ?: "Abrir".tr(), onClick = onAction)
                    }
                }
            }
            if (!selectionMode) Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Mais opções".tr(),
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                // Menu no estilo da app: fundo da superfície (sem o lilás do M3), borda fina,
                // itens compactos e "Eliminar" a vermelho.
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    offset = DpOffset(x = 0.dp, y = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    containerColor = scheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, scheme.outline),
                    modifier = Modifier.widthIn(min = 200.dp),
                ) {
                    NotificationMenuItem(
                        label = if (unread) "Marcar como lida".tr() else "Marcar como não lida".tr(),
                        icon = if (unread) Icons.Outlined.Done else Icons.Outlined.MarkEmailUnread,
                        color = scheme.onSurface,
                    ) { menuOpen = false; onToggleRead() }
                    NotificationMenuItem(
                        label = "Eliminar".tr(),
                        icon = Icons.Outlined.DeleteOutline,
                        color = scheme.error,
                    ) { menuOpen = false; onDelete() }
                }
            }
        }
    }
}

@Composable
private fun NotificationMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = color, leadingIconColor = color),
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = Modifier.height(44.dp),
    )
}

/** Botão da web: secundário (fundo claro com borda) ou primário (cheio, como "Aceitar"). */
@Composable
private fun OutlineAction(label: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (primary) scheme.surface else scheme.onSurface,
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.6f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (primary) scheme.onSurface else scheme.surface)
            .border(1.dp, if (primary) scheme.onSurface else scheme.outline, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Resposta já dada a um convite de evento. */
internal fun rsvpLabel(status: String?): String? = when (status) {
    "accepted" -> "Aceitaste".tr()
    "declined" -> "Recusaste".tr()
    "tentative" -> "Respondeste talvez".tr()
    else -> null
}

@Composable
internal fun RsvpDone(label: String) {
    val green = if (co.golink.tester.ui.theme.LocalDarkTheme.current) Color(0xFF34D399) else Color(0xFF059669)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Outlined.Done, contentDescription = null, tint = green, modifier = Modifier.size(14.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = green)
    }
}

/**
 * Destino do botão da ação. Segurança → atividade de segurança;
 * suporte → o action web traz a rota (fila do staff ou do user) e o id do ticket.
 */
private fun actionFor(
    n: Notification,
    openSecurity: () -> Unit,
    openTicket: (String, Boolean) -> Unit,
    openCalendar: (String?, String?) -> Unit,
    openLegacy: (String) -> Unit,
    openNotes: (String) -> Unit,
    openReceipts: (String) -> Unit,
    openUrl: (String) -> Unit,
): (() -> Unit)? {
    if (n.category == "security-alert") return openSecurity
    // Legacy: /legacy e /legacy/<pane> abrem na app; o convite de guardião vai ao
    // Guarding (já há sessão). /legacy-open segue o link (App Link → ecrã público).
    if (n.category == "legacy") {
        val url = n.actionUrl ?: return null
        val segments = android.net.Uri.parse(url).pathSegments
        if (segments.firstOrNull() == "legacy") {
            val pane = segments.getOrNull(1)?.takeIf { it in co.golink.tester.ui.screens.legacy.LEGACY_PANES } ?: "overview"
            return { openLegacy(pane) }
        }
        if (segments.firstOrNull() == "legacy-invite") return { openLegacy("guarding") }
        return { openUrl(url) }
    }
    // Notes: /notes/<id> ou /notes/checklists (o texto da nota nunca vem na notificação).
    if (n.category == "notes") {
        val url = n.actionUrl ?: return { openNotes("") }
        val segments = android.net.Uri.parse(url).pathSegments
        return { openNotes(if (segments.firstOrNull() == "notes") segments.drop(1).joinToString("/") else "") }
    }
    // Recibos: /receipts/budgets ou /receipts/spending?period=week&date=… (sem valores).
    if (n.category == "receipts") {
        val url = n.actionUrl ?: return { openReceipts("spending") }
        return { openReceipts(co.golink.tester.ui.navigation.AppLinkRouter.receiptsPane(android.net.Uri.parse(url))) }
    }
    // Calendário: o action traz /calendar?event=…&date=… (o título é genérico, o
    // verdadeiro está cifrado e só se vê ao abrir o evento). Cancelamentos não têm action.
    if (n.category == "calendar") {
        val url = n.actionUrl ?: return null
        val query = android.net.Uri.parse(url)
        return { openCalendar(query.getQueryParameter("event"), query.getQueryParameter("date")) }
    }
    val id = n.actionId ?: return null
    return when (n.actionRoute) {
        "AdminSupportTicketDetail" -> { { openTicket(id, true) } }
        "SupportTicketDetail" -> { { openTicket(id, false) } }
        else -> null
    }
}
