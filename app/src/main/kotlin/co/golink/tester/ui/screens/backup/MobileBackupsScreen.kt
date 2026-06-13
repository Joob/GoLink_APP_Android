package co.golink.tester.ui.screens.backup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.ui.components.BrowseItemRow
import co.golink.tester.ui.components.ItemActionsSheet
import co.golink.tester.ui.components.SelectionActionBar
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.components.dialogs.MoveDestinationDialog
import co.golink.tester.ui.components.dialogs.ShareDialog
import co.golink.tester.ui.components.dialogs.ShareDialogState
import co.golink.tester.ui.components.dialogs.TextInputDialog
import co.golink.tester.ui.screens.browse.BrowseItemDetailsSheet
import co.golink.tester.ui.screens.viewer.isViewable

private val AccentGreen = Color(0xFF16A34A)

private enum class BackupItemDialog { None, Rename, Delete, Move, Details }

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

    // Em modo de selecção o back cancela a selecção; dentro de uma subpasta
    // sobe um nível; na raiz sai do ecrã.
    BackHandler {
        when {
            state.selectMode -> viewModel.exitSelectMode()
            !viewModel.navigateUp() -> onBack()
        }
    }

    var sheetItem by remember { mutableStateOf<BrowseItem.File?>(null) }
    var dialogTarget by remember { mutableStateOf<BrowseItem.File?>(null) }
    var activeDialog by remember { mutableStateOf(BackupItemDialog.None) }
    val snackbarHost = remember { SnackbarHostState() }

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
                        if (state.selectMode) "${state.selectedIds.size} selecionado"
                        else state.folderStack.lastOrNull()?.name ?: "Backups Automáticos",
                    )
                },
                navigationIcon = {
                    if (state.selectMode) {
                        IconButton(onClick = { viewModel.exitSelectMode() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Limpar selecção")
                        }
                    } else {
                        IconButton(onClick = { if (!viewModel.navigateUp()) onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                actions = {
                    if (state.selectMode) {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = "Selecionar tudo")
                        }
                    } else {
                        IconButton(onClick = onOpenNotifications) {
                            BadgedBox(badge = {
                                if (unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) }
                            }) {
                                Icon(
                                    if (unread > 0) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                                    contentDescription = "Notificações",
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.enterSelectMode() }) {
                            Icon(Icons.Outlined.CheckBox, contentDescription = "Selecionar")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Definições")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            Column {
                if (state.selectMode && state.selectedIds.isNotEmpty()) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Tabs só na raiz — dentro de uma subpasta a navegação é da pasta.
            if (state.folderStack.isEmpty()) {
                DivisionTabs(
                    selected = state.tab,
                    counts = state.counts,
                    onSelect = viewModel::selectTab,
                )
            }

            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.error != null -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                }

                state.items.isEmpty() -> EmptyDivisionState(state.tab)

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        when (item) {
                            is BrowseItem.Folder -> BrowseItemRow(
                                item = item,
                                onClick = {
                                    if (state.selectMode) viewModel.toggleSelect(item.id)
                                    else viewModel.openFolder(item)
                                },
                                onLongClick = {
                                    if (!state.selectMode) viewModel.enterSelectMode()
                                    viewModel.toggleSelect(item.id)
                                },
                                onMoreClick = { viewModel.openFolder(item) },
                                selected = item.id in state.selectedIds,
                                selectionMode = state.selectMode,
                            )
                            is BrowseItem.File -> BrowseItemRow(
                                item = item,
                                onClick = {
                                    if (state.selectMode) {
                                        viewModel.toggleSelect(item.id)
                                    } else if (item.isViewable()) {
                                        // Sem isto o viewer abria com a sessão
                                        // antiga do browser (ou vazia).
                                        viewModel.prepareViewer(item)
                                        onOpenFile(item.id)
                                    } else {
                                        sheetItem = item
                                    }
                                },
                                onLongClick = {
                                    if (state.selectMode) {
                                        viewModel.toggleSelect(item.id)
                                    } else {
                                        sheetItem = item
                                    }
                                },
                                onMoreClick = { sheetItem = item },
                                selected = item.id in state.selectedIds,
                                selectionMode = state.selectMode,
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
            onDownload = { viewModel.download(target) },
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
            onComingSoon = { /* no-op */ },
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
            onCreate = { pwd, perm, days -> viewModel.createShare(pwd, perm, days) },
            onUpdate = { pwd, perm, days -> viewModel.updateCurrentShare(pwd, perm, days) },
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
                title = "Renomear",
                label = "Novo nome",
                initialValue = target.name,
                confirmText = "Guardar",
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
                    title = "Eliminar ficheiro",
                    message = if (target == null) "Mover $count itens para o lixo?"
                              else "Mover \"${target.name}\" para o lixo?",
                    confirmText = "Eliminar",
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
                "Backup desactivado.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onTurnOn) {
                Text("Activar", color = AccentGreen, fontWeight = FontWeight.SemiBold)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DivisionChip("Fotos", Icons.Outlined.Image, MobileBackupTab.Photos, selected, counts[MobileBackupTab.Photos], onSelect, Modifier.weight(1f))
        DivisionChip("Vídeos", Icons.Outlined.VideoLibrary, MobileBackupTab.Videos, selected, counts[MobileBackupTab.Videos], onSelect, Modifier.weight(1f))
        DivisionChip("Ficheiros", Icons.Outlined.Description, MobileBackupTab.Files, selected, counts[MobileBackupTab.Files], onSelect, Modifier.weight(1f))
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
        MobileBackupTab.Photos -> "Sem fotos enviadas ainda."
        MobileBackupTab.Videos -> "Sem vídeos enviados ainda."
        MobileBackupTab.Files -> "Sem outros ficheiros enviados ainda."
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
