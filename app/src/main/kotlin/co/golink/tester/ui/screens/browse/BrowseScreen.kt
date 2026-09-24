package co.golink.tester.ui.screens.browse

import co.golink.tester.ui.i18n.tr
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.platform.LocalContext
import co.golink.tester.data.encryption.E2EShareService
import co.golink.tester.ui.components.dialogs.GrantE2EAccessDialog
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.DisabledByDefault
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DriveFolderUpload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import co.golink.tester.data.auth.AuthRepository
import co.golink.tester.data.notifications.NotificationsRepository
import co.golink.tester.data.settings.SettingsRepository
import co.golink.tester.data.user.UserRepository
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.TeamMember
import androidx.compose.foundation.isSystemInDarkTheme
import co.golink.tester.domain.settings.StorageLevel
import co.golink.tester.domain.settings.StorageUsage
import co.golink.tester.domain.user.User
import co.golink.tester.ui.components.BrowseItemGridCard
import co.golink.tester.ui.components.BrowseItemRow
import co.golink.tester.ui.components.MemberAvatar
import co.golink.tester.ui.components.NewsBanner
import co.golink.tester.ui.components.SelectionAction
import co.golink.tester.ui.components.SelectionActionBar
import co.golink.tester.ui.components.FileListSkeleton
import co.golink.tester.ui.components.ItemActionsSheet
import co.golink.tester.ui.components.UploadProgressBanner
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.components.dialogs.CreateFileRequestDialog
import co.golink.tester.ui.components.dialogs.CreateTeamFolderDialog
import co.golink.tester.ui.components.dialogs.MoveDestinationDialog
import co.golink.tester.ui.components.dialogs.ShareDialog
import co.golink.tester.ui.components.dialogs.TextInputDialog
import co.golink.tester.ui.screens.viewer.isViewable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BrowseShellViewModel @Inject constructor(
    private val userRepository: UserRepository,
    notificationsRepository: NotificationsRepository,
    autoBackupPreferences: co.golink.tester.data.backup.AutoBackupPreferences,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val user: StateFlow<User?> = userRepository.me
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Avatar recortado no popup → upload → refresca o utilizador.
    fun updateAvatar(jpeg: ByteArray) = viewModelScope.launch {
        settingsRepository.updateAvatar(jpeg).onSuccess { userRepository.fetchMe() }
    }
    val unreadNotifications: StateFlow<Int> = notificationsRepository.unreadCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val autoBackupEnabled: StateFlow<Boolean> = autoBackupPreferences.state
        .map { it.enabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _storage = kotlinx.coroutines.flow.MutableStateFlow<StorageUsage?>(null)
    val storage: StateFlow<StorageUsage?> = _storage
    fun logout() = viewModelScope.launch { authRepository.logout() }
    fun refreshStorage() = viewModelScope.launch {
        settingsRepository.storage().onSuccess { _storage.value = it }
    }
    init { refreshStorage() }
}

private enum class ActionDialog { None, CreateFolder, Rename, Move, Delete, Share, PermanentDelete, EmptyTrash, RemoteUpload, Details, CreateTeamFolder, ConvertToTeamFolder, FileRequest, Logout }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onOpenNotifications: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenBilling: () -> Unit = {},
    onOpenAutoBackup: () -> Unit = {},
    onOpenFile: (String) -> Unit = {},
    viewModel: BrowseViewModel = hiltViewModel(),
    shell: BrowseShellViewModel = hiltViewModel(),
) {
    val openFile: (BrowseItem.File) -> Unit = { file ->
        val viewable = viewModel.state.value.items.filterIsInstance<BrowseItem.File>().filter { it.isViewable() }
        viewModel.prepareViewer(viewable, file.id)
        onOpenFile(file.id)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uploads by viewModel.uploads.collectAsStateWithLifecycle()
    val user by shell.user.collectAsStateWithLifecycle()
    val unread by shell.unreadNotifications.collectAsStateWithLifecycle()
    val storage by shell.storage.collectAsStateWithLifecycle()
    val autoBackupEnabled by shell.autoBackupEnabled.collectAsStateWithLifecycle()
    val favourites by viewModel.favouriteFolders.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Gate E2E (mesma instância do E2EGate() sobreposto no AppNavHost). Enquanto
    // estiver configurado e trancado, escondemos a tabela e a navegação.
    val gateVm: co.golink.tester.ui.screens.encryption.E2EGateViewModel = hiltViewModel()
    val e2eUnlocked by gateVm.unlocked.collectAsStateWithLifecycle()
    val e2eConfigured by gateVm.configuredFlow.collectAsStateWithLifecycle()
    val e2eLocked = e2eConfigured && !e2eUnlocked

    // Avatar: clicar → escolher imagem → recortar no popup → upload.
    var avatarPick by remember { mutableStateOf<android.net.Uri?>(null) }
    val avatarPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) avatarPick = uri }
    avatarPick?.let { uri ->
        co.golink.tester.ui.components.dialogs.AvatarCropDialog(
            imageUri = uri,
            onCancel = { avatarPick = null },
            onConfirm = { jpeg -> avatarPick = null; shell.updateAvatar(jpeg) },
        )
    }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.toast) {
        val msg = state.toast ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeToast()
    }

    // Storage em tempo real: recalcula o uso sempre que um upload termina.
    val completedUploads = uploads.count { it.state.name == "Completed" }
    LaunchedEffect(completedUploads) {
        if (completedUploads > 0) shell.refreshStorage()
    }

    // Sincroniza a pasta em tempo real (polling) só enquanto o ecrã está visível.
    LifecycleStartEffect(Unit) {
        viewModel.startRealtimeSync()
        onStopOrDispose { viewModel.stopRealtimeSync() }
    }

    var actionTarget by remember { mutableStateOf<BrowseItem?>(null) }
    var sheetItem by remember { mutableStateOf<BrowseItem?>(null) }
    var activeDialog by remember { mutableStateOf(ActionDialog.None) }
    var createInMoveFor by remember { mutableStateOf<Pair<Boolean, String?>?>(null) }
    var fabMenuOpen by remember { mutableStateOf(false) }
    var membersPopupFolder by remember { mutableStateOf<BrowseItem.Folder?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.uploadMany(uris)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree?.let { viewModel.uploadFolder(it) }
    }

    val mode = state.mode
    val canConsumeBack = state.selectMode ||
        drawerState.isOpen ||
        mode !is BrowseMode.Folder ||
        (mode as? BrowseMode.Folder)?.id != null
    BackHandler(enabled = canConsumeBack) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            state.selectMode -> viewModel.exitSelectMode()
            state.crumbs.size > 1 -> viewModel.goToCrumb(state.crumbs.size - 2)
            else -> viewModel.openRoot()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
              Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .clickable {
                                avatarPicker.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val avatarUrl = user?.avatar
                        if (!avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                        } else {
                            Text(
                                user?.name?.firstOrNull()?.uppercase() ?: "U",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        // Selo de câmara.
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.PhotoCamera,
                                contentDescription = "Alterar imagem".tr(),
                                tint = Color.White,
                                modifier = Modifier.size(10.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user?.name ?: "VueFileManager",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        user?.email?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    // Seletor de idioma ao lado do nome/email no menu principal.
                    co.golink.tester.ui.i18n.LanguageMenu()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                if (e2eLocked) {
                    DrawerEncryptedIndicator(onUnlock = {
                        scope.launch { drawerState.close() }
                        gateVm.reopen()
                    })
                } else {
                DrawerSectionHeader("BASE")
                DrawerEntry(Icons.Outlined.Folder, "Ficheiros".tr(), state.mode is BrowseMode.Folder) {
                    viewModel.openRoot(); scope.launch { drawerState.close() }
                }
                DrawerEntry(Icons.Outlined.History, "Carregamentos recentes".tr(), state.mode == BrowseMode.Latest) {
                    viewModel.openLatest(); scope.launch { drawerState.close() }
                }
                DrawerEntry(Icons.Outlined.Link, "Partilhado publicamente".tr(), state.mode == BrowseMode.Shared) {
                    viewModel.openShared(); scope.launch { drawerState.close() }
                }
                DrawerEntry(Icons.Outlined.Delete, "Lixeira".tr(), state.mode == BrowseMode.Trash) {
                    viewModel.openTrash(); scope.launch { drawerState.close() }
                }
                Spacer(Modifier.height(6.dp))
                DrawerSectionHeader("PARTILHAS")
                DrawerEntry(Icons.Outlined.Groups, "Pastas de equipa".tr(), state.mode is BrowseMode.TeamFolder) {
                    viewModel.openTeamFolders(); scope.launch { drawerState.close() }
                }
                DrawerEntry(Icons.Outlined.PeopleAlt, "Partilhado comigo".tr(), state.mode is BrowseMode.SharedWithMe) {
                    viewModel.openSharedWithMe(); scope.launch { drawerState.close() }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(6.dp))
                var myFilesExpanded by remember { mutableStateOf(false) }
                DrawerExpandableHeader(
                    title = "Os meus ficheiros".tr(),
                    expanded = myFilesExpanded,
                    onClick = {
                        myFilesExpanded = !myFilesExpanded
                        if (myFilesExpanded && state.navigationTree.isEmpty()) viewModel.loadNavigationTree()
                    },
                )
                if (myFilesExpanded) {
                    val tree = state.navigationTree.flatMap { it.folders }
                    if (tree.isEmpty()) {
                        DrawerHint("Sem pastas".tr())
                    } else {
                        tree.forEach { folder ->
                            NavFolderTreeItem(
                                folder = folder,
                                depth = 0,
                                onOpen = { id, name ->
                                    viewModel.openFolderById(id, name)
                                    scope.launch { drawerState.close() }
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                var favExpanded by remember { mutableStateOf(false) }
                DrawerExpandableHeader(
                    title = "Favoritos".tr(),
                    expanded = favExpanded,
                    onClick = { favExpanded = !favExpanded },
                )
                if (favExpanded) {
                    val favs = favourites
                    if (favs.isEmpty()) {
                        DrawerHint("Arrasta aqui as tuas pastas favoritas".tr())
                    } else {
                        favs.forEach { folder ->
                            DrawerFolderItem(label = folder.name) {
                                viewModel.openFolder(folder); scope.launch { drawerState.close() }
                            }
                        }
                    }
                }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                // Backups Automáticos revela estado/conteúdo da conta: só disponível com o cofre aberto.
                if (!e2eLocked) {
                    DrawerEntry(
                        Icons.Outlined.CloudUpload,
                        "Backups Automáticos".tr(),
                        selected = false,
                        badge = { OnOffBadge(on = autoBackupEnabled) },
                    ) {
                        scope.launch { drawerState.close() }
                        onOpenAutoBackup()
                    }
                }
                DrawerEntry(Icons.Outlined.Settings, "Definições".tr(), false) {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(10.dp))
                DrawerEntry(Icons.AutoMirrored.Filled.Logout, "Sair da Conta".tr(), false, destructive = true) {
                    scope.launch { drawerState.close() }
                    activeDialog = ActionDialog.Logout
                }
                Spacer(Modifier.height(12.dp))
              }
              StorageFooter(
                  storage = storage,
                  onUpgrade = {
                      scope.launch { drawerState.close() }
                      onOpenBilling()
                  },
              )
            }
        },
    ) {
        val openShareForSelected: () -> Unit = {
            viewModel.selectedItems().firstOrNull()?.let { item ->
                viewModel.openShareDialog(item)
                actionTarget = item
                activeDialog = ActionDialog.Share
            }
        }
        val renameSelected: () -> Unit = {
            viewModel.selectedItems().firstOrNull()?.let { item ->
                actionTarget = item
                activeDialog = ActionDialog.Rename
            }
        }
        val moveSelected: () -> Unit = {
            val sel = viewModel.selectedItems()
            viewModel.loadNavigationTree()
            actionTarget = if (sel.size == 1) sel.first() else null
            if (sel.isNotEmpty()) activeDialog = ActionDialog.Move
        }
        val downloadSelected: () -> Unit = { viewModel.downloadSelected() }
        val deleteSelectedConfirm: () -> Unit = {
            val sel = viewModel.selectedItems()
            actionTarget = if (sel.size == 1) sel.first() else null
            if (sel.isNotEmpty()) activeDialog = ActionDialog.Delete
        }
        val permanentDeleteSelectedConfirm: () -> Unit = {
            val sel = viewModel.selectedItems()
            actionTarget = if (sel.size == 1) sel.first() else null
            if (sel.isNotEmpty()) activeDialog = ActionDialog.PermanentDelete
        }
        val openSelected: () -> Unit = {
            viewModel.selectedItems().firstOrNull()?.let { item ->
                when (item) {
                    is BrowseItem.Folder -> { viewModel.openFolder(item); viewModel.clearSelection() }
                    is BrowseItem.File -> {
                        viewModel.clearSelection()
                        if (item.isViewable()) openFile(item) else sheetItem = item
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        // E2E trancado: sem breadcrumb (esconde o nome da pasta).
                        if (!e2eLocked) TopBarTitle(state = state, onCrumb = viewModel::goToCrumb)
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu".tr())
                        }
                    },
                    actions = {
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
                        // E2E trancado: esconde as ações de ficheiros (selecionar,
                        // esvaziar lixo, adicionar). Mantém notificações e o menu.
                        if (!e2eLocked) {
                        if (!state.selectMode) {
                            IconButton(onClick = { viewModel.enterSelectMode() }) {
                                Icon(Icons.Outlined.CheckBox, contentDescription = "Selecionar")
                            }
                        }
                        if (state.mode == BrowseMode.Trash) {
                            IconButton(
                                onClick = { activeDialog = ActionDialog.EmptyTrash },
                                enabled = !state.isEmptyingTrash && state.items.isNotEmpty(),
                            ) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = "Esvaziar lixo".tr())
                            }
                        }
                        if (state.mode is BrowseMode.Folder) {
                            Box {
                                IconButton(onClick = { fabMenuOpen = true }) {
                                    Icon(Icons.Filled.Add, contentDescription = "Adicionar".tr())
                                }
                                DropdownMenu(
                                    expanded = fabMenuOpen,
                                    onDismissRequest = { fabMenuOpen = false },
                                    shape = RoundedCornerShape(20.dp),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 0.dp,
                                    shadowElevation = 16.dp,
                                    modifier = Modifier.widthIn(min = 260.dp).padding(vertical = 4.dp),
                                ) {
                                    FabSectionHeader("Mais usados".tr())
                                    DropdownMenuItem(
                                        text = { Text("Carregar ficheiros".tr(), style = MaterialTheme.typography.bodyMedium) },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                                                contentAlignment = Alignment.Center,
                                            ) { Icon(Icons.Outlined.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                                        },
                                        onClick = { fabMenuOpen = false; filePicker.launch(arrayOf("*/*")) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Carregar pasta".tr(), style = MaterialTheme.typography.bodyMedium) },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                                                contentAlignment = Alignment.Center,
                                            ) { Icon(Icons.Outlined.DriveFolderUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                                        },
                                        onClick = { fabMenuOpen = false; folderPicker.launch(null) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Carregamento remoto".tr(), style = MaterialTheme.typography.bodyMedium) },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)),
                                                contentAlignment = Alignment.Center,
                                            ) { Icon(Icons.Outlined.Link, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp)) }
                                        },
                                        onClick = { fabMenuOpen = false; activeDialog = ActionDialog.RemoteUpload },
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    FabSectionHeader("Outros")
                                    DropdownMenuItem(
                                        text = { Text("Criar pasta".tr(), style = MaterialTheme.typography.bodyMedium) },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)),
                                                contentAlignment = Alignment.Center,
                                            ) { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp)) }
                                        },
                                        onClick = { fabMenuOpen = false; activeDialog = ActionDialog.CreateFolder },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Criar pasta de equipa".tr(), style = MaterialTheme.typography.bodyMedium) },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)),
                                                contentAlignment = Alignment.Center,
                                            ) { Icon(Icons.Outlined.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp)) }
                                        },
                                        onClick = { fabMenuOpen = false; activeDialog = ActionDialog.CreateTeamFolder },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Criar pedido de ficheiros".tr(), style = MaterialTheme.typography.bodyMedium) },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)),
                                                contentAlignment = Alignment.Center,
                                            ) { Icon(Icons.Outlined.NoteAdd, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp)) }
                                        },
                                        onClick = { fabMenuOpen = false; actionTarget = null; activeDialog = ActionDialog.FileRequest },
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Column {
                    if (state.selectMode && state.selectedIds.isNotEmpty()) {
                        SelectionActionBar(
                            onDownload = downloadSelected,
                            onMove = moveSelected,
                            onDelete = if (state.mode == BrowseMode.Trash) permanentDeleteSelectedConfirm else deleteSelectedConfirm,
                            trashMode = state.mode == BrowseMode.Trash,
                            selectedCount = state.selectedIds.size,
                        )
                    }
                    BrowseBottomBar(
                        mode = state.mode,
                        onHome = viewModel::openLatest,
                        onFavourites = viewModel::openFavourites,
                        onShared = viewModel::openShared,
                        onFiles = viewModel::openRoot,
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (e2eLocked) {
                E2ELockedContent(modifier = Modifier.fillMaxSize()) { gateVm.reopen() }
            } else {
            Column(modifier = Modifier.fillMaxSize()) {
                if (storage?.isLow == true) {
                    LowStorageBanner(
                        used = storage?.used,
                        capacity = storage?.capacity,
                        onUpgrade = onOpenBilling,
                        level = storage!!.level,
                    )
                }
                NewsBanner()
                co.golink.tester.ui.screens.encryption.E2EMigrationBanner()
                co.golink.tester.ui.screens.encryption.E2ENameMigrationBanner()
                SearchRow(
                    query = state.searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    onClear = viewModel::clearSearch,
                    viewMode = state.viewMode,
                    sortMode = state.sortMode,
                    onSetViewMode = viewModel::setViewMode,
                    onSetSortMode = viewModel::setSortMode,
                )
                if (state.selectMode) QuickActionsChips(
                    filesOnly = state.filesOnly,
                    inSelectionMode = state.selectMode,
                    viewMode = state.viewMode,
                    onSearchFocus = { /* search field already visible */ },
                    onToggleFilesOnly = viewModel::toggleFilesOnly,
                    onUpload = { filePicker.launch(arrayOf("*/*")) },
                    onSelectMode = { viewModel.enterSelectMode() },
                    onSelectAll = viewModel::selectAll,
                    onDeselectAll = viewModel::clearSelection,
                    onDone = viewModel::exitSelectMode,
                    onView = viewModel::toggleViewMode,
                )
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(if (state.isEmptyingTrash) Modifier.blur(14.dp) else Modifier),
                ) {
                    when {
                        state.isLoading && state.items.isEmpty() -> {
                            FileListSkeleton(modifier = Modifier.fillMaxSize())
                        }
                        state.error != null && state.items.isEmpty() -> {
                            EmptyState(title = "Não foi possível carregar".tr(), subtitle = state.error ?: "")
                        }
                        state.items.isEmpty() -> {
                            EmptyState(
                                title = "Sem conteúdo".tr(),
                                subtitle = when (state.mode) {
                                    is BrowseMode.SearchResults -> "Sem resultados para \"${(state.mode as BrowseMode.SearchResults).query}\""
                                    else -> "Esta pasta está vazia".tr()
                                },
                            )
                        }
                        else -> {
                            val onItemClick: (BrowseItem) -> Unit = { item ->
                                if (state.selectMode) {
                                    viewModel.toggleSelection(item.id)
                                } else if (state.mode == BrowseMode.Trash) {
                                    sheetItem = item
                                } else when (item) {
                                    is BrowseItem.Folder -> viewModel.openFolder(item)
                                    is BrowseItem.File -> if (item.isViewable()) openFile(item) else sheetItem = item
                                }
                            }
                            val onItemLongClick: (BrowseItem) -> Unit = {
                                viewModel.enterSelectMode()
                                viewModel.toggleSelection(it.id)
                            }
                            val displayed = remember(state.items, state.filesOnly) {
                                if (state.filesOnly) state.items.filterIsInstance<BrowseItem.File>() else state.items
                            }
                            if (state.viewMode == ViewMode.GRID) {
                                val gridState = rememberLazyGridState()
                                val shouldLoadMore by remember {
                                    androidx.compose.runtime.derivedStateOf {
                                        val total = gridState.layoutInfo.totalItemsCount
                                        val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                        total > 0 && last >= total - 6
                                    }
                                }
                                LaunchedEffect(shouldLoadMore, state.currentPage, state.lastPage) {
                                    if (shouldLoadMore && state.currentPage < state.lastPage) viewModel.loadMore()
                                }
                                LazyVerticalGrid(
                                    state = gridState,
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(displayed, key = { it.id }) { item ->
                                        BrowseItemGridCard(
                                            item = item,
                                            selected = state.selectedIds.contains(item.id),
                                            selectionMode = state.selectMode,
                                            onClick = { onItemClick(item) },
                                            onLongClick = { onItemLongClick(item) },
                                            onMoreClick = { sheetItem = item },
                                            onMembersClick = if (item is BrowseItem.Folder && item.members.isNotEmpty()) {
                                                { membersPopupFolder = item }
                                            } else null,
                                        )
                                    }
                                }
                            } else {
                                val listState = rememberLazyListState()
                                val shouldLoadMore by remember {
                                    androidx.compose.runtime.derivedStateOf {
                                        val total = listState.layoutInfo.totalItemsCount
                                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                        total > 0 && last >= total - 4
                                    }
                                }
                                LaunchedEffect(shouldLoadMore, state.currentPage, state.lastPage) {
                                    if (shouldLoadMore && state.currentPage < state.lastPage) viewModel.loadMore()
                                }
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(displayed, key = { it.id }) { item ->
                                        BrowseItemRow(
                                            item = item,
                                            selected = state.selectedIds.contains(item.id),
                                            selectionMode = state.selectMode,
                                            onClick = { onItemClick(item) },
                                            onLongClick = { onItemLongClick(item) },
                                            onMoreClick = { sheetItem = item },
                                            onMembersClick = if (item is BrowseItem.Folder && item.members.isNotEmpty()) {
                                                { membersPopupFolder = item }
                                            } else null,
                                        )
                                    }
                                    if (state.isLoadingMore) {
                                        item {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                CircularProgressIndicator(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Auto-backup uploads are surfaced inside the Backups
                    // Automáticos screen, not in this global banner, so the
                    // browser only sees user-initiated uploads here.
                    val browserUploads = uploads.filterNot { it.mobileBackup }
                    UploadProgressBanner(
                        tasks = browserUploads,
                        onDismiss = viewModel::clearAllUploads,
                        onCancelTask = viewModel::cancelUpload,
                        onRetryTask = viewModel::retryUpload,
                        onRetryFailed = viewModel::retryFailedUploads,
                        onOverwrite = viewModel::overwriteConflict,
                        onSkip = viewModel::skipConflict,
                        onViewFiles = { viewModel.refresh(); viewModel.dismissCompletedUploads() },
                    )
                }
            }
            }
            co.golink.tester.ui.components.E2EEncryptedFlash(
                trigger = state.mode,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            }
        }
    }

    if (state.processing != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(
                    state.processing!!,
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    // Flush do lixo: cartão de progresso 0–100% por cima da lista desfocada,
    // enquanto os ficheiros vão desaparecendo por trás a cada lote apagado.
    if (state.isEmptyingTrash) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 30.dp, vertical = 24.dp),
                ) {
                    CircularProgressIndicator(
                        progress = { state.emptyingProgress / 100f },
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "A eliminar permanentemente…".tr(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${state.emptyingProgress}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    sheetItem?.let { target ->
        ItemActionsSheet(
            item = target,
            isFavourite = (target as? BrowseItem.Folder)?.let { f -> favourites.any { it.id == f.id } } ?: false,
            isShared = target.share != null,
            inTrash = state.mode == BrowseMode.Trash,
            onDismiss = { sheetItem = null },
            onDownload = { viewModel.downloadItem(target) },
            onShare = {
                actionTarget = target
                viewModel.openShareDialog(target)
                activeDialog = ActionDialog.Share
            },
            onToggleFavourite = { (target as? BrowseItem.Folder)?.let { viewModel.toggleFavourite(it) } },
            onRename = { actionTarget = target; activeDialog = ActionDialog.Rename },
            onMove = {
                actionTarget = target
                viewModel.loadNavigationTree()
                activeDialog = ActionDialog.Move
            },
            onDelete = { actionTarget = target; activeDialog = ActionDialog.Delete },
            onRestore = { viewModel.restore(target) },
            onPermanentDelete = { actionTarget = target; activeDialog = ActionDialog.PermanentDelete },
            onDetails = { actionTarget = target; activeDialog = ActionDialog.Details },
            onComingSoon = { viewModel.notifyComingSoon() },
            onConvertToTeamFolder = { actionTarget = target; activeDialog = ActionDialog.ConvertToTeamFolder },
            onFileRequest = { actionTarget = target; activeDialog = ActionDialog.FileRequest },
        )
    }

    membersPopupFolder?.let { folder ->
        TeamMembersPopup(
            folder = folder,
            onDismiss = { membersPopupFolder = null },
        )
    }

    when (activeDialog) {
        ActionDialog.CreateFolder -> TextInputDialog(
            title = "Nova pasta".tr(),
            label = "Nome",
            confirmText = "Criar".tr(),
            onDismiss = { activeDialog = ActionDialog.None },
            onConfirm = { name -> viewModel.createFolder(name) },
        )
        ActionDialog.Rename -> actionTarget?.let { target ->
            TextInputDialog(
                title = "Renomear".tr(),
                label = "Novo nome".tr(),
                initialValue = target.name,
                confirmText = "Guardar".tr(),
                onDismiss = { activeDialog = ActionDialog.None; actionTarget = null },
                onConfirm = { name -> viewModel.rename(target, name) },
            )
        }
        ActionDialog.Delete -> {
            val multi = actionTarget == null
            val count = if (multi) viewModel.selectedItems().size else 1
            val targetName = actionTarget?.name
            if (count > 0) {
                ConfirmDialog(
                    title = "Mover para o lixo?".tr(),
                    message = if (multi) "$count itens ficarão no lixo e podes restaurar depois."
                              else "$targetName ficará no lixo e podes restaurar depois.",
                    confirmText = "Mover para o lixo".tr(),
                    destructive = true,
                    onDismiss = { activeDialog = ActionDialog.None; actionTarget = null },
                    onConfirm = {
                        if (multi) viewModel.deleteSelected()
                        else actionTarget?.let { viewModel.delete(it) }
                    },
                )
            }
        }
        ActionDialog.Move -> {
            val multi = actionTarget == null
            val excludeId = actionTarget?.id
            val count = if (multi) viewModel.selectedItems().size else 1
            if (count > 0) {
                MoveDestinationDialog(
                    sections = state.navigationTree,
                    excludeId = excludeId,
                    onDismiss = { activeDialog = ActionDialog.None; actionTarget = null },
                    onConfirm = { destinationId ->
                        if (multi) viewModel.moveSelected(destinationId)
                        else actionTarget?.let { viewModel.move(it, destinationId) }
                    },
                    onCreateFolder = { parentId -> createInMoveFor = true to parentId },
                )
                createInMoveFor?.let { (_, parentId) ->
                    TextInputDialog(
                        title = "Nova pasta".tr(),
                        label = "Nome",
                        confirmText = "Criar".tr(),
                        onDismiss = { createInMoveFor = null },
                        onConfirm = { name ->
                            viewModel.createFolderIn(name, parentId)
                            createInMoveFor = null
                        },
                    )
                }
            }
        }
        ActionDialog.Share -> {
            val shareState by viewModel.shareState.collectAsStateWithLifecycle()
            shareState?.let { ss ->
                ShareDialog(
                    state = co.golink.tester.ui.components.dialogs.ShareDialogState(
                        item = ss.item,
                        share = ss.share,
                        qrSvg = ss.qrSvg,
                        sendingEmail = ss.sendingEmail,
                        loadingQr = ss.loadingQr,
                        isWorking = ss.isWorking,
                        emailDialogVisible = ss.emailDialogVisible,
                        keyPending = ss.keyPending,
                    ),
                    onDismiss = {
                        activeDialog = ActionDialog.None
                        actionTarget = null
                        viewModel.closeShareDialog()
                    },
                    onCreate = { pwd, perm, days, limit -> viewModel.createShare(pwd, perm, days, limit) },
                    onUpdate = { pwd, perm, days, limit -> viewModel.updateCurrentShare(pwd, perm, days, limit) },
                    onCopy = { /* clipboard handled inside dialog */ },
                    onShowQr = viewModel::fetchQrCode,
                    onSendEmail = viewModel::sendShareEmail,
                    onShowEmailDialog = viewModel::setEmailDialogVisible,
                    onRevoke = viewModel::revokeCurrentShare,
                )
            }
        }
        ActionDialog.PermanentDelete -> {
            val multi = actionTarget == null
            val count = if (multi) viewModel.selectedItems().size else 1
            if (count > 0) {
                ConfirmDialog(
                    title = "Eliminar permanentemente?".tr(),
                    message = if (multi) "$count itens serão removidos para sempre. Esta acção não pode ser desfeita."
                              else "${actionTarget?.name} será removido para sempre. Esta acção não pode ser desfeita.",
                    confirmText = "Eliminar permanentemente".tr(),
                    destructive = true,
                    onDismiss = { activeDialog = ActionDialog.None; actionTarget = null },
                    onConfirm = {
                        if (multi) viewModel.deleteSelected(permanent = true)
                        else actionTarget?.let { viewModel.delete(it, permanent = true) }
                        shell.refreshStorage()
                    },
                )
            }
        }
        ActionDialog.RemoteUpload -> TextInputDialog(
            title = "Carregamento remoto".tr(),
            label = "URL(s)",
            confirmText = "Iniciar".tr(),
            subtitle = "Insere os URLs separados por vírgula ou nova linha".tr(),
            icon = Icons.Outlined.CloudUpload,
            iconColor = MaterialTheme.colorScheme.primary,
            multiLine = true,
            onDismiss = { activeDialog = ActionDialog.None },
            onConfirm = { input -> viewModel.remoteUpload(input) },
        )
        ActionDialog.EmptyTrash -> ConfirmDialog(
            title = "Esvaziar lixo?".tr(),
            message = "Todos os itens no lixo serão eliminados para sempre.".tr(),
            confirmText = "Esvaziar lixo".tr(),
            destructive = true,
            onDismiss = { activeDialog = ActionDialog.None },
            onConfirm = viewModel::emptyTrash,
        )
        ActionDialog.Logout -> ConfirmDialog(
            title = "Sair da Conta?".tr(),
            message = "Tens a certeza que queres sair da tua conta?".tr(),
            confirmText = "Sair".tr(),
            destructive = true,
            onDismiss = { activeDialog = ActionDialog.None },
            onConfirm = { shell.logout() },
        )
        ActionDialog.Details -> actionTarget?.let { target ->
            BrowseItemDetailsSheet(
                item = target,
                onDismiss = { activeDialog = ActionDialog.None; actionTarget = null },
            )
        }
        ActionDialog.CreateTeamFolder -> CreateTeamFolderDialog(
            isConvert = false,
            onDismiss = { activeDialog = ActionDialog.None },
            onCreate = { name, invites -> viewModel.createTeamFolder(name, invites) },
            onConvert = {},
        )
        ActionDialog.ConvertToTeamFolder -> actionTarget?.let { target ->
            CreateTeamFolderDialog(
                isConvert = true,
                onDismiss = { activeDialog = ActionDialog.None; actionTarget = null },
                onCreate = { _, _ -> },
                onConvert = { invites -> viewModel.convertToTeamFolder(target.id, invites) },
            )
        }
        ActionDialog.FileRequest -> CreateFileRequestDialog(
            onDismiss = { activeDialog = ActionDialog.None },
            // actionTarget definido = pedido para essa pasta (menu "…"); null = pasta actual (botão +).
            onCreate = { name, email, notes -> viewModel.createFileRequest(name, email, notes, actionTarget?.id) },
        )
        ActionDialog.None -> Unit
    }
}

private fun topBarTitle(state: BrowseUiState): String = when (val mode = state.mode) {
    is BrowseMode.Folder -> mode.name
    BrowseMode.Latest -> "Recentes"
    BrowseMode.Shared -> "Partilhado comigo".tr()
    BrowseMode.Favourites -> "Favoritos".tr()
    BrowseMode.Trash -> "Lixo".tr()
    is BrowseMode.SearchResults -> "Resultados: ${mode.query}"
    is BrowseMode.TeamFolder -> mode.name
    is BrowseMode.SharedWithMe -> mode.name
}

@Composable
private fun DrawerSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun DrawerExpandableHeader(title: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 28.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun DrawerFolderItem(
    label: String,
    depth: Int = 0,
    hasChildren: Boolean = false,
    expanded: Boolean = false,
    onToggle: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(
                start = (16 + depth * 14).dp,
                end = 16.dp,
                top = 6.dp,
                bottom = 6.dp,
            ),
    ) {
        if (hasChildren && onToggle != null) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
        } else {
            Spacer(Modifier.width(28.dp))
        }
        Icon(
            Icons.Outlined.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NavFolderTreeItem(
    folder: co.golink.tester.domain.browse.NavFolder,
    depth: Int,
    onOpen: (String, String) -> Unit,
) {
    var expanded by remember(folder.id) { mutableStateOf(false) }
    val hasChildren = folder.folders.isNotEmpty()
    DrawerFolderItem(
        label = folder.name,
        depth = depth,
        hasChildren = hasChildren,
        expanded = expanded,
        onToggle = if (hasChildren) ({ expanded = !expanded }) else null,
        onClick = { onOpen(folder.id, folder.name) },
    )
    if (expanded) {
        folder.folders.forEach { child ->
            NavFolderTreeItem(folder = child, depth = depth + 1, onOpen = onOpen)
        }
    }
}

@Composable
private fun DrawerHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 44.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun DrawerEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    destructive: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    // Custom compact row instead of NavigationDrawerItem (whose fixed 56dp
    // height made the drawer feel oversized). ~42dp tall keeps it tighter.
    val unselectedContent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val content = if (selected) MaterialTheme.colorScheme.primary else unselectedContent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.12f else 0f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        badge?.let {
            Spacer(Modifier.width(8.dp))
            it()
        }
    }
}

/** Pill "On"/"Off" para indicar o estado de uma funcionalidade no menu. */
@Composable
private fun OnOffBadge(on: Boolean) {
    val color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            if (on) "On" else "Off",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            title = { Text("$count selecionado") },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Limpar selecção".tr())
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SelectionAction(Icons.Outlined.OpenInNew, "Abrir".tr(), onOpen)
            SelectionAction(Icons.Outlined.Share, "Partilhar".tr(), onShare)
            SelectionAction(Icons.Outlined.DriveFileMove, "Mover".tr(), onMove)
            SelectionAction(Icons.Outlined.DriveFileRenameOutline, "Renomear".tr(), onRename)
            SelectionAction(Icons.Outlined.Download, "Descarregar".tr(), onDownload)
            SelectionAction(Icons.Outlined.Delete, "Eliminar".tr(), onDelete)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
}

@Composable
private fun QuickActionsChips(
    filesOnly: Boolean,
    inSelectionMode: Boolean,
    viewMode: ViewMode,
    onSearchFocus: () -> Unit,
    onToggleFilesOnly: () -> Unit,
    onUpload: () -> Unit,
    onSelectMode: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDone: () -> Unit,
    onView: () -> Unit,
) {
    val viewIcon = if (viewMode == ViewMode.GRID) Icons.Outlined.ViewList else Icons.Outlined.GridView
    val viewLabel = if (viewMode == ViewMode.GRID) "Lista".tr() else "Grelha".tr()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (inSelectionMode) {
            ActionChip(icon = Icons.Outlined.CheckBox, label = "Selecionar tudo".tr(), onClick = onSelectAll, large = true)
            ActionChip(icon = Icons.Outlined.DisabledByDefault, label = "Desmarcar todos".tr(), onClick = onDeselectAll, large = true)
            ActionChip(icon = Icons.Outlined.Check, label = "Feito".tr(), onClick = onDone, large = true)
        } else {
            ActionChip(icon = Icons.Outlined.CheckBox, label = "Selecionar", onClick = onSelectMode, large = true)
            ActionChip(icon = viewIcon, label = viewLabel, onClick = onView, large = true)
        }
    }
}

@Composable
private fun FabSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean = false,
    large: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val content = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val hPad = if (large) 16.dp else 12.dp
    val vPad = if (large) 12.dp else 8.dp
    val iconSize = if (large) 22.dp else 18.dp
    val spacing = if (large) 8.dp else 6.dp
    val textStyle = if (large) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = hPad, vertical = vPad),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(spacing))
        Text(label, style = textStyle, color = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    viewMode: ViewMode,
    sortMode: SortMode,
    onSetViewMode: (ViewMode) -> Unit,
    onSetSortMode: (SortMode) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    "Pesquisar ficheiros e pastas".tr(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            singleLine = true,
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = null) } }
            } else null,
            shape = RoundedCornerShape(14.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            ),
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
        )
        var menuOpen by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    RoundedCornerShape(14.dp),
                )
                .clickable { menuOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (viewMode == ViewMode.GRID) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                contentDescription = "Vista e ordenação".tr(),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
            ViewSortMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                viewMode = viewMode,
                sortMode = sortMode,
                onSetViewMode = { onSetViewMode(it); menuOpen = false },
                onSetSortMode = { onSetSortMode(it); menuOpen = false },
            )
        }
    }
}

@Composable
private fun ViewSortMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewMode: ViewMode,
    sortMode: SortMode,
    onSetViewMode: (ViewMode) -> Unit,
    onSetSortMode: (SortMode) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
        modifier = Modifier.widthIn(min = 250.dp).padding(vertical = 4.dp),
    ) {
        Text(
            "Visualizar".tr().uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
        )
        val targetView = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
        DropdownMenuItem(
            text = { Text(if (targetView == ViewMode.GRID) "Vista em grelha".tr() else "Vista em lista".tr(), style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = {
                Box(
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (targetView == ViewMode.GRID) Icons.Outlined.GridView else Icons.Outlined.ViewList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
            onClick = { onSetViewMode(targetView) },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Text(
            "Ordenação".tr().uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp),
        )
        val dateActive = sortMode == SortMode.DATE_DESC || sortMode == SortMode.DATE_ASC
        val alphaActive = sortMode == SortMode.ALPHA_ASC || sortMode == SortMode.ALPHA_DESC
        DropdownMenuItem(
            text = { Text("Ordenar por data".tr(), style = MaterialTheme.typography.bodyMedium, color = if (dateActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
            leadingIcon = {
                Box(
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(if (dateActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = if (dateActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
            },
            trailingIcon = if (dateActive) {
                { Icon(if (sortMode == SortMode.DATE_ASC) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
            } else null,
            // Voltar a escolher o mesmo critério inverte o sentido da ordenação
            onClick = { onSetSortMode(if (sortMode == SortMode.DATE_DESC) SortMode.DATE_ASC else SortMode.DATE_DESC) },
        )
        DropdownMenuItem(
            text = { Text("Ordenar alfabeticamente".tr(), style = MaterialTheme.typography.bodyMedium, color = if (alphaActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
            leadingIcon = {
                Box(
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(if (alphaActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.SortByAlpha, contentDescription = null, tint = if (alphaActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
            },
            trailingIcon = if (alphaActive) {
                { Icon(if (sortMode == SortMode.ALPHA_ASC) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
            } else null,
            onClick = { onSetSortMode(if (sortMode == SortMode.ALPHA_ASC) SortMode.ALPHA_DESC else SortMode.ALPHA_ASC) },
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun TopBarTitle(state: BrowseUiState, onCrumb: (Int) -> Unit) {
    val mode = state.mode
    if ((mode is BrowseMode.Folder || mode is BrowseMode.TeamFolder || mode is BrowseMode.SharedWithMe) && state.crumbs.isNotEmpty()) {
        val all = state.crumbs
        data class Entry(val label: String, val originalIndex: Int?, val clickable: Boolean)
        val entries: List<Entry> = if (all.size <= 2) {
            all.mapIndexed { i, c -> Entry(c.name, i, i != all.lastIndex) }
        } else {
            listOf(
                Entry("…", all.size - 2, true),
                Entry(all.last().name, all.lastIndex, false),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            entries.forEachIndexed { i, e ->
                if (i > 0) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                val isLast = i == entries.lastIndex
                Text(
                    text = e.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 140.dp)
                        .padding(end = 6.dp)
                        .then(
                            if (e.clickable && e.originalIndex != null)
                                Modifier.clickable { onCrumb(e.originalIndex) }
                            else Modifier
                        ),
                )
            }
        }
    } else {
        Text(text = topBarTitle(state), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BreadcrumbsRow(crumbs: List<Crumb>, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { index, crumb ->
            if (index > 0) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            val isLast = index == crumbs.lastIndex
            Text(
                text = crumb.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .then(
                        if (!isLast) Modifier.clickable { onSelect(index) }
                        else Modifier,
                    ),
            )
        }
    }
}

/**
 * Severity accent for storage UI. Green (OK) → amber (WARNING) → red (DANGER).
 * Amber/red are theme-aware light/dark variants; green reuses the theme primary.
 */
@Composable
private fun storageAccent(level: StorageLevel): Color {
    val dark = isSystemInDarkTheme()
    return when (level) {
        StorageLevel.OK -> MaterialTheme.colorScheme.primary
        StorageLevel.WARNING -> if (dark) Color(0xFFFBBF24) else Color(0xFFD97706)
        StorageLevel.DANGER -> if (dark) Color(0xFFF87171) else Color(0xFFDC2626)
    }
}

@Composable
private fun LowStorageBanner(
    used: String?,
    capacity: String?,
    onUpgrade: () -> Unit,
    level: StorageLevel = StorageLevel.DANGER,
) {
    val color = storageAccent(level)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(onClick = onUpgrade)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Espaço quase esgotado".tr(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
            if (used != null && capacity != null) {
                Text(
                    "%1\$s de %2\$s utilizados".tr().format(used, capacity),
                    style = MaterialTheme.typography.bodySmall,
                    color = color.copy(alpha = 0.85f),
                )
            }
        }
        Text(
            "Upgrade".tr() + " →",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StorageFooter(
    storage: StorageUsage?,
    onUpgrade: () -> Unit,
) {
    val level = storage?.level ?: StorageLevel.OK
    val isLow = storage?.isLow == true
    val accent = storageAccent(level)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(accent.copy(alpha = 0.08f))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isLow) Icons.Outlined.Warning else Icons.Outlined.CloudQueue,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Armazenamento".tr(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isLow) accent else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(12.dp))
            val pct = (storage?.percentage ?: 0f).coerceIn(0f, 100f) / 100f
            LinearProgressIndicator(
                progress = { pct },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = accent,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = storage?.let { "${it.used} ${"de".tr()} ${it.capacity} ${"usados".tr()}" } ?: "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isLow) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Pouco espaço disponível. Considere fazer upgrade.".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onUpgrade,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Obter mais espaço".tr(), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BrowseBottomBar(
    mode: BrowseMode,
    onHome: () -> Unit,
    onFavourites: () -> Unit,
    onShared: () -> Unit,
    onFiles: () -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        val itemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NavigationBarItem(
            selected = mode == BrowseMode.Latest,
            onClick = onHome,
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("Recentes") },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = mode == BrowseMode.Favourites,
            onClick = onFavourites,
            icon = { Icon(Icons.Filled.Star, contentDescription = null) },
            label = { Text("Favoritos".tr()) },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = mode == BrowseMode.Shared,
            onClick = onShared,
            icon = { Icon(Icons.Filled.PeopleAlt, contentDescription = null) },
            label = { Text("Partilhado".tr()) },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = mode is BrowseMode.Folder,
            onClick = onFiles,
            icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
            label = { Text("Ficheiros".tr()) },
            colors = itemColors,
        )
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Placeholder no lugar da tabela enquanto o E2E está trancado. */
@Composable
private fun E2ELockedContent(modifier: Modifier = Modifier, onUnlock: () -> Unit) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text("Os teus ficheiros estão protegidos.".tr(), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Desbloqueia com a tua password de encriptação para veres as pastas e ficheiros.".tr(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onUnlock,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = co.golink.tester.ui.theme.BrandGreen),
        ) {
            Text(
                "Desencriptar ficheiros".tr(),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Indicador "Encrypted" no drawer, no lugar da navegação de ficheiros. */
@Composable
private fun DrawerEncryptedIndicator(onUnlock: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Conteúdo encriptado".tr(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Introduz a tua passphrase para veres os ficheiros e os backups.".tr(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onUnlock,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Desencriptar ficheiros".tr(), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowseItemDetailsSheet(item: BrowseItem, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("Detalhes".tr(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            DetailRow("Nome", item.name)
            when (item) {
                is BrowseItem.File -> {
                    DetailRow("Tipo", item.type.ifBlank { "—" })
                    DetailRow("Mimetype", item.mimetype ?: "—")
                    DetailRow("Tamanho", item.filesize ?: "—")
                }
                is BrowseItem.Folder -> {
                    DetailRow("Itens".tr(), item.itemCount?.toString() ?: "—")
                    DetailRow("Tamanho", item.filesize ?: "—")
                    if (item.isTeamFolder) DetailRow("Tipo", "Pasta de equipa".tr())
                }
            }
            DetailRow("Criado em".tr(), item.createdAt?.take(10) ?: "—")
            DetailRow("Atualizado em".tr(), item.updatedAt?.take(10) ?: "—")
            if (item.share != null) {
                DetailRow("Partilhado".tr(), "Sim")
                item.share!!.permission?.let { DetailRow("Permissão".tr(), if (it == "can-edit") "Pode editar".tr() else "Apenas ver".tr()) }
                if (item.share!!.protected) DetailRow("Password", "Sim")
                item.share!!.expireIn?.let { if (it > 0) DetailRow("Expira em".tr(), "${it} dias") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.width(130.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamMembersPopup(
    folder: BrowseItem.Folder,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val e2eShare = remember { E2EShareService.get(context) }
    var grantMember by remember { mutableStateOf<TeamMember?>(null) }

    grantMember?.let { m ->
        GrantE2EAccessDialog(member = m, folderId = folder.id, onDismiss = { grantMember = null })
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        folder.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${folder.members.size} membro${if (folder.members.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(bottom = 12.dp),
            )
            folder.members.forEach { member ->
                MemberRow(
                    member = member,
                    onGrant = if (e2eShare.isUnlocked && member.permission != "owner") {
                        { grantMember = member }
                    } else null,
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun MemberRow(member: TeamMember, onGrant: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(member = member, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                member.name ?: member.email,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (member.name != null) {
                Text(
                    member.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // E2E: conceder acesso de encriptação (com confirmação de fingerprint).
        onGrant?.let {
            IconButton(onClick = it) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = "Conceder acesso de encriptação".tr(),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        member.permission?.let { perm ->
            val label = when (perm) {
                "owner" -> "Dono".tr()
                "editor", "can-edit" -> "Editor".tr()
                else -> "Visualizador"
            }
            androidx.compose.material3.Badge(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
