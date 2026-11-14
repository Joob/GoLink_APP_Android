package co.golink.tester.ui.screens.backup

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.zIndex
import co.golink.tester.ui.components.pinchToZoomLevels
import co.golink.tester.ui.i18n.tr
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.ui.components.BrowseItemRow
import co.golink.tester.ui.components.ItemActionsSheet
import co.golink.tester.ui.components.SelectionActionBar
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.components.dialogs.CreateFileRequestDialog
import co.golink.tester.ui.components.dialogs.CreateTeamFolderDialog
import co.golink.tester.ui.components.dialogs.MoveDestinationDialog
import co.golink.tester.ui.components.dialogs.ShareDialog
import co.golink.tester.ui.components.dialogs.ShareDialogState
import co.golink.tester.ui.components.dialogs.TextInputDialog
import co.golink.tester.ui.screens.browse.BrowseItemDetailsSheet
import co.golink.tester.ui.screens.viewer.isViewable
import co.golink.tester.ui.components.BrowseItemGridCard
import co.golink.tester.ui.screens.browse.SortMode
import co.golink.tester.ui.screens.browse.ViewMode
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip

private val AccentGreen = Color(0xFF16A34A)

private enum class BackupItemDialog { None, Rename, Delete, Move, Details, ConvertToTeamFolder, FileRequest }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileBackupsScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenNotifications: () -> Unit = {},
    viewModel: MobileBackupsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backupEnabled by viewModel.backupEnabled.collectAsStateWithLifecycle()
    val shareState by viewModel.shareState.collectAsStateWithLifecycle()
    val unread by viewModel.unreadNotifications.collectAsStateWithLifecycle()
    val moveJobs by viewModel.moveJobs.collectAsStateWithLifecycle()

    // Em modo de selecção o back cancela a selecção; dentro de uma subpasta
    // sobe um nível; na raiz sai do ecrã.
    BackHandler {
        when {
            state.selectMode -> viewModel.exitSelectMode()
            !viewModel.navigateUp() -> onBack()
        }
    }

    // BrowseItem (não só File): as pastas de backup também têm o menu "…".
    var sheetItem by remember { mutableStateOf<BrowseItem?>(null) }
    var dialogTarget by remember { mutableStateOf<BrowseItem?>(null) }
    var activeDialog by remember { mutableStateOf(BackupItemDialog.None) }
    var viewSortOpen by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    // Cliques partilhados por lista e grelha (mesmo comportamento).
    val onItemClick: (BrowseItem) -> Unit = { item ->
        when (item) {
            is BrowseItem.Folder ->
                if (state.selectMode) viewModel.toggleSelect(item.id) else viewModel.openFolder(item)
            is BrowseItem.File -> when {
                state.selectMode -> viewModel.toggleSelect(item.id)
                item.isViewable() -> { viewModel.prepareViewer(item); onOpenFile(item.id) }
                else -> sheetItem = item
            }
        }
    }
    val onItemLongClick: (BrowseItem) -> Unit = { item ->
        when (item) {
            is BrowseItem.Folder -> {
                if (!state.selectMode) viewModel.enterSelectMode()
                viewModel.toggleSelect(item.id)
            }
            is BrowseItem.File ->
                if (state.selectMode) viewModel.toggleSelect(item.id) else { sheetItem = item }
        }
    }

    // Refresh the list whenever the user returns to this screen — newly
    // backed-up items uploaded since they last visited will then show up.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Alterações feitas noutro lado (web, outro aparelho): só com o ecrã à frente.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.syncLoop() }
    }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.selectMode) {
                            // "3 selecionados · 40.40 GB" (mockup 2026-11, igual ao browser).
                            val selected = state.items.filter { it.id in state.selectedIds }
                            val n = selected.size
                            val label = if (n == 1) "1 selecionado".tr() else "%d selecionados".tr().format(n)
                            val bytes = selected.sumOf { if (it is BrowseItem.File) it.filesizeBytes else (it as BrowseItem.Folder).filesizeBytes }
                            if (bytes > 0) "$label · ${co.golink.tester.ui.common.formatBytes(bytes)}" else label
                        } else state.folderStack.lastOrNull()?.name ?: "Backups Automáticos".tr(),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        fontWeight = if (state.selectMode) androidx.compose.ui.text.font.FontWeight.Bold else null,
                    )
                },
                navigationIcon = {
                    if (state.selectMode) {
                        IconButton(onClick = { viewModel.exitSelectMode() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Limpar selecção".tr())
                        }
                    } else {
                        IconButton(onClick = { if (!viewModel.navigateUp()) onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar".tr())
                        }
                    }
                },
                actions = {
                    if (state.selectMode) {
                        val allSelected = state.items.isNotEmpty() && state.selectedIds.size >= state.items.size
                        val green = MaterialTheme.colorScheme.primary
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(50))
                                .background(green.copy(alpha = 0.14f))
                                .clickable { if (allSelected) viewModel.clearSelection() else viewModel.selectAll() }
                                .padding(horizontal = 14.dp),
                        ) {
                            Text(
                                if (allSelected) "Desmarcar todos".tr() else "Selecionar tudo".tr(),
                                color = green,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    } else {
                        IconButton(onClick = onOpenNotifications) {
                            BadgedBox(badge = {
                                if (unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) }
                            }) {
                                Icon(
                                    if (unread > 0) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                                    contentDescription = "Notificações".tr(),
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { viewSortOpen = true }) {
                                Icon(
                                    if (state.viewMode == ViewMode.GRID) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                                    contentDescription = "Vista e ordenação".tr(),
                                )
                            }
                            if (viewSortOpen) co.golink.tester.ui.components.ViewSortSheet(
                                viewMode = state.viewMode,
                                sortMode = state.sortMode,
                                foldersFirst = state.foldersFirst,
                                groupByMonth = state.groupByMonth,
                                onDismiss = { viewSortOpen = false },
                                onApply = { view, sort, ff, gbm ->
                                    viewSortOpen = false
                                    if (view != state.viewMode) viewModel.setViewMode(view)
                                    if (ff != state.foldersFirst) viewModel.setFoldersFirst(ff)
                                    if (gbm != state.groupByMonth) viewModel.setGroupByMonth(gbm)
                                    if (sort != state.sortMode) viewModel.setSortMode(sort)
                                },
                            )
                        }
                        IconButton(onClick = { viewModel.enterSelectMode() }) {
                            Icon(Icons.Outlined.CheckBox, contentDescription = "Selecionar".tr())
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Definições".tr())
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            Column {
                co.golink.tester.ui.components.MoveProgressBanner(
                    jobs = moveJobs,
                    onRetry = viewModel::retryMove,
                    onDismiss = viewModel::dismissMove,
                    // O destino pode ser fora do Backup Mobile: aqui só fecha.
                    onOpenFolder = { job -> viewModel.dismissMove(job.id) },
                )
                if (state.selectMode) {
                    SelectionActionBar(
                        onDownload = { viewModel.downloadSelected() },
                        onMove = {
                            viewModel.loadNavigationTree()
                            dialogTarget = null
                            activeDialog = BackupItemDialog.Move
                        },
                        onDelete = {
                            dialogTarget = null
                            activeDialog = BackupItemDialog.Delete
                        },
                        selectedCount = state.selectedIds.size,
                        showSummary = false,
                    )
                }
                // When the backup feature is off we keep the file list visible —
                // those uploads still exist server-side — and surface a small
                // banner so the user knows new files won't be added until they
                // turn it back on. Matches the reference design.
                if (!backupEnabled) BackupDisabledBanner(onTurnOn = onOpenSettings)
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Cartões dos telemóveis (só na raiz), como na web.
            if (state.folderStack.isEmpty()) {
                state.summary?.let { summary ->
                    BackupDeviceCards(
                        summary = summary,
                        accountEnabled = summary.enabled,
                        busy = state.pausingDevice,
                        selectedId = state.selectedDeviceId,
                        onSelect = { viewModel.selectDevice(it.id) },
                        onSetPaused = { device, paused -> viewModel.setDevicePaused(device.id, paused) },
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
            }

            // Tabs só na raiz — dentro de uma subpasta a navegação é da pasta.
            if (state.folderStack.isEmpty()) {
                DivisionTabs(
                    selected = state.tab,
                    counts = state.counts,
                    onSelect = viewModel::selectTab,
                )
            }

            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::pullRefresh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Pinça (como nos Ficheiros): lista grande ↔ grelha de 5.
                    .pinchToZoomLevels(state.zoomLevel, onChange = viewModel::setZoomLevel),
            ) {
            co.golink.tester.ui.components.ZoomLevelHint(
                state.zoomLevel,
                Modifier.align(Alignment.TopCenter).padding(top = 12.dp).zIndex(1f),
            )
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.error != null -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                }

                state.items.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                ) { EmptyDivisionState(state.tab) }

                else -> {
                    // Mesmo formato dos Ficheiros: "Pastas" (cartões compactos /
                    // mosaicos / chips com nome) + secções por dia/mês/ano, recolhíveis.
                    val zoom = state.zoomLevel
                    val byDate = state.sortMode.isDate && state.groupByMonth
                    val granularity = when (zoom) {
                        0 -> co.golink.tester.ui.screens.browse.DateGroup.DAY
                        4 -> co.golink.tester.ui.screens.browse.DateGroup.YEAR
                        else -> co.golink.tester.ui.screens.browse.DateGroup.MONTH
                    }
                    // Pastas de backup com milhares de fotos: ordenar e agrupar fora do
                    // main thread (antes corria na composição e engasgava a UI). Até à
                    // primeira conta fica a lista anterior.
                    val sectionItems = state.items
                    val sectionSort = state.sortMode
                    val sectionFoldersFirst = state.foldersFirst
                    val sections by androidx.compose.runtime.produceState(
                        initialValue = emptyList<co.golink.tester.ui.screens.browse.MonthSection>(),
                        sectionItems, sectionSort, granularity, byDate, sectionFoldersFirst,
                    ) {
                        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            co.golink.tester.ui.screens.browse.browseSections(sortBackupItems(sectionItems, sectionSort), byDate, granularity, sectionFoldersFirst)
                        }
                    }
                    // Barra de tamanho: escala pelo maior item da lista (pastas incluídas, como na web).
                    val listMaxBytes = remember(state.items) {
                        state.items.maxOfOrNull { if (it is BrowseItem.Folder) it.filesizeBytes else (it as BrowseItem.File).filesizeBytes } ?: 0L
                    }
                    var collapsed by remember(state.tab, state.folderStack.size) { mutableStateOf(setOf<String>()) }
                    val toggle: (String) -> Unit = { k -> collapsed = if (k in collapsed) collapsed - k else collapsed + k }
                    // Scroll infinito: perto do fim pede a página seguinte (100 ficheiros).
                    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    val hasMore = state.hasMore
                    // items.size: depois de cada página volta a medir (uma falha não repete sozinha).
                    LaunchedEffect(gridState, listState, state.viewMode, hasMore, state.items.size) {
                        if (!hasMore) return@LaunchedEffect
                        androidx.compose.runtime.snapshotFlow {
                            if (state.viewMode == ViewMode.GRID) {
                                val info = gridState.layoutInfo
                                info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 12
                            } else {
                                val info = listState.layoutInfo
                                info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 8
                            }
                        }
                            .collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
                    }
                    if (state.viewMode == ViewMode.GRID) {
                        val gap = if (zoom >= 3) 4.dp else 8.dp
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(co.golink.tester.ui.screens.browse.gridColumnsFor(zoom)),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = if (zoom >= 3) 8.dp else 16.dp, end = if (zoom >= 3) 8.dp else 16.dp, top = 8.dp, bottom = 56.dp),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            sections.forEach { section ->
                                val isCollapsed = section.key in collapsed
                                if (section.title != null) {
                                    item(key = "sec-${section.key}", span = { GridItemSpan(maxLineSpan) }, contentType = "month") {
                                        co.golink.tester.ui.screens.browse.MonthHeader(
                                            section.title, section.count, Modifier.padding(top = 10.dp, bottom = 2.dp),
                                            collapsed = isCollapsed, onToggle = { toggle(section.key) },
                                            selectState = if (state.selectMode) co.golink.tester.ui.screens.browse.sectionSelectState(section.items, state.selectedIds) else null,
                                            onToggleSelect = { viewModel.toggleSectionSelection(section.items.map { it.id }) },
                                        )
                                    }
                                }
                                if (!isCollapsed && section.folders && zoom >= 4) {
                                    item(key = co.golink.tester.ui.screens.browse.FOLDERS_STRIP_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = "folders-strip") {
                                        co.golink.tester.ui.components.FolderChipsRow(
                                            folders = section.items.filterIsInstance<BrowseItem.Folder>(),
                                            selectedIds = state.selectedIds,
                                            selectionMode = state.selectMode,
                                            onClick = onItemClick,
                                            onLongClick = onItemLongClick,
                                        )
                                    }
                                } else if (!isCollapsed) {
                                    gridItems(section.items, key = { it.id }, contentType = { if (section.folders) "folder" else "file" }) { item ->
                                        BrowseItemGridCard(
                                            item = item,
                                            compactFolder = section.folders,
                                            density = co.golink.tester.ui.components.cardDensityFor(zoom),
                                            onClick = { onItemClick(item) },
                                            onLongClick = { onItemLongClick(item) },
                                            onMoreClick = { sheetItem = item },
                                            selected = item.id in state.selectedIds,
                                            selectionMode = state.selectMode,
                                        )
                                    }
                                }
                            }
                            if (state.hasMore) {
                                item(key = "load-more", span = { GridItemSpan(maxLineSpan) }, contentType = "load-more") {
                                    BackupLoadMoreFooter(state.loadingMore, viewModel::loadMore)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 56.dp),
                            verticalArrangement = Arrangement.spacedBy(if (zoom == 0) 8.dp else 6.dp),
                        ) {
                            sections.forEach { section ->
                                val isCollapsed = section.key in collapsed
                                if (section.title != null) {
                                    item(key = "sec-${section.key}", contentType = "month") {
                                        co.golink.tester.ui.screens.browse.MonthHeader(
                                            section.title, section.count, Modifier.padding(top = 8.dp, bottom = 2.dp),
                                            collapsed = isCollapsed, onToggle = { toggle(section.key) },
                                            size = section.size,
                                            selectState = if (state.selectMode) co.golink.tester.ui.screens.browse.sectionSelectState(section.items, state.selectedIds) else null,
                                            onToggleSelect = { viewModel.toggleSectionSelection(section.items.map { it.id }) },
                                        )
                                    }
                                }
                                if (!isCollapsed) items(section.items, key = { it.id }) { item ->
                                    BrowseItemRow(
                                        item = item,
                                        maxBytes = listMaxBytes,
                                        large = zoom == 0,
                                        onClick = { onItemClick(item) },
                                        onLongClick = { onItemLongClick(item) },
                                        onMoreClick = { sheetItem = item },
                                        selected = item.id in state.selectedIds,
                                        selectionMode = state.selectMode,
                                    )
                                }
                            }
                            if (state.hasMore) {
                                item(key = "load-more", contentType = "load-more") { BackupLoadMoreFooter(state.loadingMore, viewModel::loadMore) }
                            }
                        }
                    }
                }
            }
            }
        }
        // Selo fixo "End-to-end encrypted" no fundo, visível em todas as abas.
        co.golink.tester.ui.components.E2EEncryptedBadge(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        )
        // Overlay de progresso ao eliminar/mover.
        if (state.processing != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(
                            state.processing ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        }
    }

    sheetItem?.let { target ->
        ItemActionsSheet(
            item = target,
            isFavourite = false,
            isShared = target.share != null,
            inTrash = false,
            onDismiss = { sheetItem = null },
            onDownload = { viewModel.downloadItem(target) },
            onShare = { viewModel.openShareDialog(target) },
            onToggleFavourite = { /* file favourites not supported */ },
            onRename = { dialogTarget = target; activeDialog = BackupItemDialog.Rename },
            onMove = {
                dialogTarget = target
                viewModel.loadNavigationTree()
                activeDialog = BackupItemDialog.Move
            },
            onDelete = { dialogTarget = target; activeDialog = BackupItemDialog.Delete },
            onDetails = { dialogTarget = target; activeDialog = BackupItemDialog.Details },
            onConvertToTeamFolder = { dialogTarget = target; activeDialog = BackupItemDialog.ConvertToTeamFolder },
            onFileRequest = { dialogTarget = target; activeDialog = BackupItemDialog.FileRequest },
            showFolderCollaboration = false,
        )
    }

    shareState?.let { ss ->
        ShareDialog(
            state = ShareDialogState(
                item = ss.item,
                share = ss.share,
                qrSvg = ss.qrSvg,
                sendingEmail = ss.sendingEmail,
                loadingQr = ss.loadingQr,
                isWorking = ss.isWorking,
                emailDialogVisible = ss.emailDialogVisible,
            ),
            onDismiss = { viewModel.closeShareDialog() },
            onCreate = { pwd, perm, minutes, limit, views -> viewModel.createShare(pwd, perm, minutes, limit, views) },
            onUpdate = { pwd, perm, minutes, limit, views, prot -> viewModel.updateCurrentShare(pwd, perm, minutes, limit, views, prot) },
            onCopy = { /* clipboard handled inside dialog */ },
            onShowQr = viewModel::fetchQrCode,
            onSendEmail = viewModel::sendShareEmail,
            onShowEmailDialog = viewModel::setEmailDialogVisible,
            onRevoke = viewModel::revokeCurrentShare,
        )
    }

    when (activeDialog) {
        BackupItemDialog.Rename -> dialogTarget?.let { target ->
            TextInputDialog(
                title = "Renomear".tr(),
                label = "Novo nome".tr(),
                initialValue = target.name,
                confirmText = "Guardar".tr(),
                onDismiss = { activeDialog = BackupItemDialog.None; dialogTarget = null },
                onConfirm = { newName ->
                    viewModel.rename(target, newName)
                    activeDialog = BackupItemDialog.None
                    dialogTarget = null
                },
            )
        }
        BackupItemDialog.Delete -> {
            val target = dialogTarget
            val count = if (target == null) state.selectedIds.size else 1
            if (count > 0) {
                ConfirmDialog(
                    title = "Eliminar ficheiro".tr(),
                    message = if (target == null) "Mover $count itens para o lixo?"
                              else "Mover \"${target.name}\" para o lixo?",
                    confirmText = "Eliminar".tr(),
                    destructive = true,
                    onDismiss = { activeDialog = BackupItemDialog.None; dialogTarget = null },
                    onConfirm = {
                        if (target == null) viewModel.deleteSelected() else viewModel.delete(target)
                        activeDialog = BackupItemDialog.None
                        dialogTarget = null
                    },
                )
            }
        }
        BackupItemDialog.Move -> {
            val target = dialogTarget
            val count = if (target == null) state.selectedIds.size else 1
            if (count > 0) {
                MoveDestinationDialog(
                    sections = state.navigationTree,
                    excludeId = target?.id,
                    onDismiss = { activeDialog = BackupItemDialog.None; dialogTarget = null },
                    onConfirm = { destinationId ->
                        if (target == null) viewModel.moveSelected(destinationId)
                        else viewModel.move(target, destinationId)
                        activeDialog = BackupItemDialog.None
                        dialogTarget = null
                    },
                )
            }
        }
        BackupItemDialog.Details -> dialogTarget?.let { target ->
            BrowseItemDetailsSheet(
                item = target,
                onDismiss = { activeDialog = BackupItemDialog.None; dialogTarget = null },
                onShare = { viewModel.openShareDialog(target) },
                fetchShare = viewModel::fetchShare,
            )
        }
        BackupItemDialog.ConvertToTeamFolder -> dialogTarget?.let { target ->
            CreateTeamFolderDialog(
                isConvert = true,
                folderName = target.name,
                onDismiss = { activeDialog = BackupItemDialog.None; dialogTarget = null },
                onCreate = { _, _, _ -> },
                onConvert = { invites, onDone ->
                    viewModel.convertToTeamFolder(target.id, invites) { error ->
                        if (error == null) { activeDialog = BackupItemDialog.None; dialogTarget = null }
                        onDone(error)
                    }
                },
            )
        }
        BackupItemDialog.FileRequest -> dialogTarget?.let { target ->
            CreateFileRequestDialog(
                onDismiss = { activeDialog = BackupItemDialog.None; dialogTarget = null },
                folderName = target.name,
                onCreate = { name, email, notes, days ->
                    viewModel.createFileRequest(name, email, notes, target.id, days)
                    activeDialog = BackupItemDialog.None
                    dialogTarget = null
                },
            )
        }
        BackupItemDialog.None -> Unit
    }
}

@Composable
private fun BackupDisabledBanner(onTurnOn: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 0.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Backup desactivado.".tr(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onTurnOn) {
                Text("Activar".tr(), color = AccentGreen, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DivisionTabs(
    selected: MobileBackupTab,
    counts: Map<MobileBackupTab, Int>,
    onSelect: (MobileBackupTab) -> Unit,
) {
    // Scroll horizontal: com 4 separadores já não cabem à largura do ecrã, e
    // assim o utilizador desliza para o lado para escolher.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DivisionChip("Imagens".tr(), Icons.Outlined.Image, MobileBackupTab.Images, selected, counts[MobileBackupTab.Images], onSelect)
        DivisionChip("Vídeos".tr(), Icons.Outlined.VideoLibrary, MobileBackupTab.Videos, selected, counts[MobileBackupTab.Videos], onSelect)
        DivisionChip("Áudios".tr(), Icons.Outlined.MusicNote, MobileBackupTab.Audios, selected, counts[MobileBackupTab.Audios], onSelect)
        DivisionChip("Ficheiros".tr(), Icons.Outlined.Description, MobileBackupTab.Files, selected, counts[MobileBackupTab.Files], onSelect)
    }
}

@Composable
private fun DivisionChip(
    label: String,
    icon: ImageVector,
    tab: MobileBackupTab,
    selected: MobileBackupTab,
    count: Int?,
    onSelect: (MobileBackupTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = tab == selected
    Surface(
        modifier = modifier.clickable { onSelect(tab) },
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) AccentGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.35f)) else null,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isSelected) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (count != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "$count ${if (count == 1) "item" else "itens"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) AccentGreen.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun EmptyDivisionState(tab: MobileBackupTab) {
    val msg = when (tab) {
        MobileBackupTab.Images -> "Sem imagens enviadas ainda.".tr()
        MobileBackupTab.Videos -> "Sem vídeos enviados ainda.".tr()
        MobileBackupTab.Audios -> "Sem áudios enviados ainda.".tr()
        MobileBackupTab.Files -> "Sem outros ficheiros enviados ainda.".tr()
    }
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(AccentGreen.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Rodapé do scroll infinito: roda enquanto pede a página seguinte. */
@Composable
private fun BackupLoadMoreFooter(loading: Boolean, onLoadMore: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        if (loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        else androidx.compose.material3.TextButton(onClick = onLoadMore) { Text("Carregar mais".tr()) }
    }
}

// Ordena a lista plana consoante o modo escolhido (usado na grelha, na lista
// alfabética e no viewer). DATE_DESC = mais recente primeiro.
internal fun sortBackupItems(items: List<BrowseItem>, sort: SortMode): List<BrowseItem> = when (sort) {
    SortMode.ALPHA_ASC -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    SortMode.ALPHA_DESC -> items.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
    SortMode.DATE_DESC -> items.sortedByDescending { sortStamp(it) }
    SortMode.DATE_ASC -> items.sortedBy { sortStamp(it) }
    // Tamanho/tipo (folha "Ordenar e vista"), desempate pelo nome.
    SortMode.SIZE_DESC -> items.sortedWith(compareByDescending<BrowseItem> { sizeStamp(it) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    SortMode.SIZE_ASC -> items.sortedWith(compareBy<BrowseItem> { sizeStamp(it) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    SortMode.TYPE_ASC -> items.sortedWith(compareBy<BrowseItem> { typeStamp(it) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    SortMode.TYPE_DESC -> items.sortedWith(compareByDescending<BrowseItem> { typeStamp(it) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

private fun sizeStamp(item: BrowseItem): Long = when (item) {
    is BrowseItem.Folder -> item.filesizeBytes
    is BrowseItem.File -> item.filesizeBytes
}

private fun typeStamp(item: BrowseItem): String = (item as? BrowseItem.File)?.type ?: "folder"

// Chave de ordenação por data: usa o ISO-8601 (UTC), ordenável por texto. Os
// campos createdAt/updatedAt são strings localizadas ("25. Jul. 2026, 03:00") e
// não ordenam de forma fiável. Sem ISO (itens antigos/pré-migração) devolve ""
// para não rebentar — esses caem no fim de forma estável.
private fun sortStamp(item: BrowseItem): String =
    item.createdAtIso ?: item.updatedAtIso ?: ""

@Composable
private fun MenuSectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun MenuIconBox(active: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) { content() }
}

