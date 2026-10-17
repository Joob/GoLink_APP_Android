package co.golink.tester.ui.screens.browse

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import co.golink.tester.ui.components.SpeedDialFab
import co.golink.tester.ui.components.drawer.AdaptiveNavigationDrawer
import co.golink.tester.ui.components.drawer.AppDrawerContent
import co.golink.tester.ui.components.drawer.DrawerArea
import co.golink.tester.ui.components.drawer.DrawerItem
import co.golink.tester.ui.components.drawer.DrawerSection
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.material.icons.outlined.Description
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
import androidx.compose.ui.draw.alpha
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
import co.golink.tester.domain.files.unsupportedExtension
import co.golink.tester.domain.files.visibleTextExtensionsLabel
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

private enum class ActionDialog { None, CreateFolder, CreateTextFile, Rename, Move, Delete, Share, PermanentDelete, EmptyTrash, RemoteUpload, Details, CreateTeamFolder, ConvertToTeamFolder, FileRequest, Logout, DissolveTeam, LeaveTeam }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onOpenNotifications: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenBilling: () -> Unit = {},
    onOpenAutoBackup: () -> Unit = {},
    onOpenFile: (String) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenSupport: (admin: Boolean) -> Unit = {},
    onOpenSupportDashboard: () -> Unit = {},
    onOpenAdmin: (pane: String) -> Unit = {},
    viewModel: BrowseViewModel = hiltViewModel(),
    shell: BrowseShellViewModel = hiltViewModel(),
) {
    val openActivityFile: (BrowseItem.File) -> Unit = { file ->
        val viewable = viewModel.activity.value.sections.values
            .flatMap { it.entries }
            .filter { !it.itemTrashed }
            .mapNotNull { it.item as? BrowseItem.File }
            .filter { it.isViewable() }
            .distinctBy { it.id }
        viewModel.prepareViewer(viewable.ifEmpty { listOf(file) }, file.id)
        onOpenFile(file.id)
    }
    val openFile: (BrowseItem.File) -> Unit = { file ->
        val viewable = viewModel.state.value.items.filterIsInstance<BrowseItem.File>().filter { it.isViewable() }
        viewModel.prepareViewer(viewable, file.id)
        onOpenFile(file.id)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val feedState by viewModel.feed.collectAsStateWithLifecycle()
    val activityState by viewModel.activity.collectAsStateWithLifecycle()
    // Só o dono copia o link (a chave #k= só se monta com as chaves dele) — igual
    // à web ($checkPermission('master')). No lixo e em "Partilhado comigo" não.
    val canCopyLinks = state.mode != BrowseMode.Trash && state.mode !is BrowseMode.SharedWithMe
    // Atividade: itens de outros (Team Folders / Partilhado comigo) também abrem o menu.
    val foreignActivityIds = remember(activityState.sections) {
        val me = viewModel.currentUserId()
        activityState.sections.values.flatMap { it.entries }
            .filter { it.itemOwnerId != null && it.itemOwnerId != me }
            .mapNotNull { it.item?.id }
            .toSet()
    }
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

    // Lote recusado antes de enviar (limite de envio / sem espaço): nada foi enviado (igual à web).
    state.uploadBlocked?.let { block ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::dismissUploadBlocked,
            title = { Text(block.title) },
            text = { Text(block.message) },
            confirmButton = { TextButton(onClick = viewModel::dismissUploadBlocked) { Text("OK") } },
        )
    }

    // Ficheiro de texto acabado de criar: abre-o no viewer (que arranca já em
    // modo de edição) assim que aparece na lista recarregada.
    LaunchedEffect(state.openFileRequest) {
        val id = state.openFileRequest ?: return@LaunchedEffect
        viewModel.consumeOpenFileRequest()
        onOpenFile(id)
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
    // "Pesquisar ficheiros" na gaveta: incrementa → a SearchRow pede o foco.
    var searchFocusRequest by remember { mutableStateOf(0) }
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

    val permanentDrawer = co.golink.tester.ui.components.drawer.isPermanentDrawer()
    val isAdmin = user?.role == "admin"
    val secondaryArea = when {
        isAdmin -> DrawerArea.Admin
        user?.supportStaff != null -> DrawerArea.Support
        else -> null
    }
    var drawerArea by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(DrawerArea.Drive) }
    if (secondaryArea == null && drawerArea != DrawerArea.Drive) drawerArea = DrawerArea.Drive
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }
    val go: (() -> Unit) -> () -> Unit = { action -> { closeDrawer(); action() } }
    val supportSection = DrawerSection(
        "Suporte".tr(),
        listOf(
            DrawerItem("Painel do suporte".tr(), Icons.Outlined.SupportAgent, onClick = go(onOpenSupportDashboard)),
            DrawerItem("Tickets".tr(), Icons.Outlined.Inbox, onClick = go { onOpenSupport(true) }),
        ),
    )
    val drawerSections = when (drawerArea) {
        DrawerArea.Drive -> listOfNotNull(
            DrawerSection("O meu drive".tr(), listOf(
                DrawerItem("Todos os ficheiros".tr(), Icons.Outlined.Folder, selected = state.mode is BrowseMode.Folder, onClick = go(viewModel::openRoot)),
                DrawerItem("Recentes".tr(), Icons.Outlined.History, selected = state.mode == BrowseMode.Latest, onClick = go(viewModel::openLatest)),
                DrawerItem(
                    "Favoritos".tr(), Icons.Outlined.Star,
                    selected = state.mode == BrowseMode.Favourites,
                    count = favourites.size,
                    // As pastas favoritas logo por baixo (até 6), como na web.
                    children = favourites.take(6).map { folder ->
                        DrawerItem(
                            folder.name, Icons.Outlined.Folder,
                            selected = (state.mode as? BrowseMode.Folder)?.id == folder.id,
                            onClick = go { viewModel.openFolder(folder) },
                        )
                    },
                    onClick = go(viewModel::openFavourites),
                ),
            )),
            DrawerSection("Partilha".tr(), listOf(
                DrawerItem("Pastas de equipa".tr(), Icons.Outlined.Groups, selected = state.mode is BrowseMode.TeamFolder, onClick = go(viewModel::openTeamFolders)),
                DrawerItem("Partilhado comigo".tr(), Icons.Outlined.PeopleAlt, selected = state.mode is BrowseMode.SharedWithMe, onClick = go(viewModel::openSharedWithMe)),
                DrawerItem("Links públicos".tr(), Icons.Outlined.Link, selected = state.mode == BrowseMode.Shared, onClick = go(viewModel::openShared)),
            )),
            DrawerSection("Backups".tr(), listOf(
                DrawerItem("Telemóvel".tr(), Icons.Outlined.PhoneAndroid, statusDot = autoBackupEnabled, onClick = go(onOpenAutoBackup)),
            )),
            // Administradores tratam o suporte na área Suporte: sem "Os meus pedidos".
            if (!isAdmin) DrawerSection("Suporte".tr(), listOf(
                DrawerItem("Os meus pedidos".tr(), Icons.Outlined.SupportAgent, onClick = go { onOpenSupport(false) }),
            )) else null,
            DrawerSection(null, listOf(
                DrawerItem("Lixo".tr(), Icons.Outlined.Delete, selected = state.mode == BrowseMode.Trash, onClick = go(viewModel::openTrash)),
            )),
        )
        DrawerArea.Admin -> listOf(
            DrawerSection("Visão geral".tr(), listOf(
                DrawerItem("Painel".tr(), Icons.Outlined.Dashboard, onClick = go { onOpenAdmin("dashboard") }),
                DrawerItem("Análises".tr(), Icons.Outlined.Insights, onClick = go { onOpenAdmin("analytics") }),
            )),
            DrawerSection("Utilizadores".tr(), listOf(
                DrawerItem("Todos os utilizadores".tr(), Icons.Outlined.People, onClick = go { onOpenAdmin("users") }),
                DrawerItem("Convites".tr(), Icons.Outlined.MailOutline, onClick = go { onOpenAdmin("invites") }),
            )),
            DrawerSection("Conteúdo".tr(), listOf(
                DrawerItem("Notícias".tr(), Icons.Outlined.Campaign, onClick = go { onOpenAdmin("news") }),
            )),
            supportSection,
        )
        DrawerArea.Support -> listOf(supportSection)
    }

    AdaptiveNavigationDrawer(
        permanent = permanentDrawer,
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                user = user,
                area = drawerArea,
                onAreaChange = { drawerArea = it },
                secondaryArea = secondaryArea,
                sections = drawerSections,
                unreadNotifications = unread,
                onOpenNotifications = go(onOpenNotifications),
                storage = storage,
                onUpgrade = go(onOpenBilling),
                appVersion = co.golink.tester.BuildConfig.VERSION_NAME,
                onProfile = go(onOpenProfile),
                onAvatarClick = {
                    avatarPicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
                onHelp = go { if (user?.supportStaff != null) onOpenSupportDashboard() else onOpenSupport(false) },
                onSettings = go(onOpenSettings),
                onLogout = go { activeDialog = ActionDialog.Logout },
                onNewUpload = go {
                    if (state.mode !is BrowseMode.Folder) viewModel.openRoot()
                    viewModel.exitSelectMode()
                    fabMenuOpen = true
                },
                onSearch = go { searchFocusRequest++ },
                lockedContent = if (e2eLocked) {
                    {
                        DrawerEncryptedIndicator(onUnlock = {
                            closeDrawer()
                            gateVm.reopen()
                        })
                    }
                } else null,
            )
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

        Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        // E2E trancado: sem breadcrumb (esconde o nome da pasta).
                        // Com o cabeçalho de pasta, o caminho está nele: aqui só a secção.
                        if (!e2eLocked) {
                            if (hasFolderHeader(state)) Text(sectionTitle(state.mode), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            else TopBarTitle(state = state, onCrumb = viewModel::goToCrumb)
                        }
                    },
                    navigationIcon = {
                        // Gaveta fixa (ecrã largo): sem botão de menu.
                        if (!permanentDrawer) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu".tr())
                            }
                        }
                    },
                    actions = {
                        if (!permanentDrawer) IconButton(onClick = onOpenNotifications) {
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
                        if (!state.selectMode && state.mode != BrowseMode.Latest) {
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
                            readOnly = state.readOnly,
                            summary = selectionSummary(state.items.filter { it.id in state.selectedIds }),
                            onShare = if (state.mode is BrowseMode.Folder || state.mode is BrowseMode.TeamFolder || state.mode == BrowseMode.Shared) openShareForSelected else null,
                            onRestore = { viewModel.restoreSelected() },
                        )
                    }
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
                co.golink.tester.ui.components.DeletionScheduledBanner()
                co.golink.tester.ui.screens.encryption.E2EMigrationBanner()
                co.golink.tester.ui.screens.encryption.E2ENameMigrationBanner()
                // Cabeçalho da pasta (igual à web): caminho, ícone, ★, menu, estado e chips.
                if (hasFolderHeader(state)) {
                    val inside = isInsideFolder(state.mode)
                    val root = state.root.takeIf { inside }
                    co.golink.tester.ui.components.FolderHeader(
                        title = if (inside) root?.name ?: state.crumbs.lastOrNull()?.name.orEmpty() else sectionTitle(state.mode),
                        crumbs = if (inside) state.crumbs.map { it.name } else emptyList(),
                        section = headerSection(state.mode),
                        folder = root,
                        badge = folderBadge(state, root),
                        stats = state.stats,
                        typeFilter = state.typeFilter,
                        isFavourite = root != null && favourites.any { it.id == root.id },
                        canFavourite = root != null && state.mode is BrowseMode.Folder,
                        canFolderMenu = root != null && !state.readOnly,
                        onBack = if (inside && state.crumbs.size > 1) {
                            { viewModel.goToCrumb(state.crumbs.size - 2) }
                        } else null,
                        onCrumb = viewModel::goToCrumb,
                        onToggleFavourite = { root?.let(viewModel::toggleFavourite) },
                        onFolderMenu = { sheetItem = root },
                        onTypeFilter = viewModel::setTypeFilter,
                    )
                }
                SearchRow(
                    query = state.searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    onClear = viewModel::clearSearch,
                    viewMode = state.viewMode,
                    sortMode = state.sortMode,
                    onSetViewMode = viewModel::setViewMode,
                    onSetSortMode = viewModel::setSortMode,
                    showViewControls = state.mode != BrowseMode.Latest,
                    focusRequest = searchFocusRequest,
                )
                // Filtros reconhecidos na frase (parser local, sem IA): "PDF · março 2026".
                if (state.mode is BrowseMode.SearchResults && state.searchChips.isNotEmpty()) {
                    Text(
                        "Filtros".tr() + ": " + state.searchChips.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
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
                // Lixo: aviso de retenção entre a barra e a lista (como na web) —
                // o servidor apaga de vez o que lá está há mais de N dias.
                val trashRetentionDays by viewModel.trashRetentionDays.collectAsStateWithLifecycle()
                if (state.mode == BrowseMode.Trash && state.items.isNotEmpty() && trashRetentionDays > 0 && !state.selectMode) {
                    TrashRetentionNotice(
                        days = trashRetentionDays,
                        onEmptyNow = { activeDialog = ActionDialog.EmptyTrash },
                    )
                }
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(if (state.isEmptyingTrash) Modifier.blur(14.dp) else Modifier),
                ) {
                    when {
                        state.mode == BrowseMode.Latest -> when {
                            !feedState.loaded && feedState.isLoading -> FileListSkeleton(modifier = Modifier.fillMaxSize())
                            !feedState.loaded && feedState.error != null ->
                                EmptyState(title = "Não foi possível carregar".tr(), subtitle = feedState.error ?: "")
                            else -> co.golink.tester.ui.components.ActivityFeedContent(
                                entries = feedState.entries,
                                tab = feedState.tab,
                                hasMore = feedState.hasMore,
                                loadingMore = feedState.loadingMore,
                                summary = feedState.summary,
                                onTab = viewModel::setActivityTab,
                                onOpen = { entry ->
                                    when (val item = entry.item) {
                                        null -> Unit
                                        // No lixo: ficheiro visualizável abre "só ver" (como na web); o resto → Lixo.
                                        else -> if (entry.itemTrashed) {
                                            if (item is BrowseItem.File && item.isViewable()) {
                                                viewModel.prepareViewer(listOf(item), item.id, fromTrash = true)
                                                onOpenFile(item.id)
                                            } else viewModel.openTrash()
                                        } else when (item) {
                                            is BrowseItem.Folder -> viewModel.openActivityFolder(
                                                item,
                                                ownedByMe = entry.itemOwnerId != null && entry.itemOwnerId == viewModel.currentUserId(),
                                            )
                                            is BrowseItem.File -> if (item.isViewable()) openActivityFile(item) else sheetItem = item
                                        }
                                    }
                                },
                                onOpenFolder = { folder, ownerId -> viewModel.openActivityFolder(folder, ownedByMe = ownerId == null || ownerId == viewModel.currentUserId()) },
                                onRestore = { item -> viewModel.restore(item) },
                                onMore = { entry -> entry.item?.let { sheetItem = it } },
                                onLoadMore = viewModel::loadMoreFeed,
                            )
                        }
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
                                    // Ficheiro visualizável: abre "só ver"; pastas e o resto → ações.
                                    if (item is BrowseItem.File && item.isViewable()) openFile(item) else sheetItem = item
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
                            // Secções recolhíveis (Pastas / meses / Ficheiros), como na web.
                            var collapsed by remember(state.mode) { mutableStateOf(setOf<String>()) }
                            val toggleSection: (String) -> Unit = { k -> collapsed = if (k in collapsed) collapsed - k else collapsed + k }
                            val byDate = state.sortMode == SortMode.DATE_DESC || state.sortMode == SortMode.DATE_ASC
                            val sections = remember(displayed, byDate) { browseSections(displayed, byDate) }
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
                                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    // "Pastas" (cartões compactos) + secções por mês (data) ou
                                    // "Ficheiros" (nome), recolhíveis — como na web.
                                    sections.forEach { section ->
                                    val isCollapsed = section.key in collapsed
                                    if (section.title != null) {
                                        item(
                                            key = "sec-${section.key}",
                                            span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                                            contentType = "month",
                                        ) {
                                            MonthHeader(
                                                section.title, section.count,
                                                Modifier.padding(top = 10.dp, bottom = 2.dp),
                                                collapsed = isCollapsed,
                                                onToggle = { toggleSection(section.key) },
                                            )
                                        }
                                    }
                                    if (!isCollapsed) items(section.items, key = { it.id }, contentType = { if (section.folders) "folder" else "file" }) { item ->
                                        BrowseItemGridCard(
                                            item = item,
                                            compactFolder = section.folders,
                                            selected = state.selectedIds.contains(item.id),
                                            selectionMode = state.selectMode,
                                            onClick = { onItemClick(item) },
                                            onLongClick = { onItemLongClick(item) },
                                            onMoreClick = { sheetItem = item },
                                            onMembersClick = if (item is BrowseItem.Folder && item.members.isNotEmpty()) {
                                                { membersPopupFolder = item }
                                            } else null,
                                            onCopyLinkClick = if (canCopyLinks && !item.share?.link.isNullOrBlank()) {
                                                { viewModel.copyShareLink(item) }
                                            } else null,
                                        )
                                    }
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
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    // As mesmas secções recolhíveis da grelha.
                                    sections.forEach { section ->
                                    val isCollapsed = section.key in collapsed
                                    if (section.title != null) {
                                        item(key = "sec-${section.key}", contentType = "month") {
                                            MonthHeader(
                                                section.title, section.count,
                                                Modifier.padding(top = 8.dp, bottom = 2.dp),
                                                collapsed = isCollapsed,
                                                onToggle = { toggleSection(section.key) },
                                            )
                                        }
                                    }
                                    if (!isCollapsed) items(section.items, key = { it.id }) { item ->
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
                                            onCopyLinkClick = if (canCopyLinks && !item.share?.link.isNullOrBlank()) {
                                                { viewModel.copyShareLink(item) }
                                            } else null,
                                        )
                                    }
                                    }
                                    // Rodapé "N itens" (como a tabela da web), só com a lista completa.
                                    if (!state.isLoadingMore && state.currentPage >= state.lastPage) {
                                        item(key = "list-footer") {
                                            Text(
                                                "%d itens".tr().format(displayed.size),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                                            )
                                        }
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

        // Botão "+" por cima do separador "Ficheiros", com as ações em bolhas.
        // Só no separador Ficheiros (como o antigo "+" da barra de cima).
        if (state.mode is BrowseMode.Folder && !e2eLocked && !state.selectMode) {
            val primary = MaterialTheme.colorScheme.primary
            val secondary = MaterialTheme.colorScheme.secondary
            val tertiary = MaterialTheme.colorScheme.tertiary
            SpeedDialFab(
                open = fabMenuOpen,
                onOpenChange = { fabMenuOpen = it },
                // Sem barra de baixo desde o redesign 2026-10.
                bottomOffset = 0.dp,
                actions = listOf(
                    co.golink.tester.ui.components.SpeedDialAction(Icons.Outlined.NoteAdd, "Criar pedido de ficheiros".tr(), tertiary) {
                        actionTarget = null; activeDialog = ActionDialog.FileRequest
                    },
                    co.golink.tester.ui.components.SpeedDialAction(Icons.Outlined.GroupAdd, "Criar pasta de equipa".tr(), tertiary) {
                        activeDialog = ActionDialog.CreateTeamFolder
                    },
                    co.golink.tester.ui.components.SpeedDialAction(Icons.Outlined.Description, "Criar ficheiro de texto".tr(), tertiary) {
                        activeDialog = ActionDialog.CreateTextFile
                    },
                    co.golink.tester.ui.components.SpeedDialAction(Icons.Outlined.CreateNewFolder, "Criar pasta".tr(), tertiary) {
                        activeDialog = ActionDialog.CreateFolder
                    },
                    co.golink.tester.ui.components.SpeedDialAction(Icons.Outlined.Link, "Carregamento remoto".tr(), secondary) {
                        activeDialog = ActionDialog.RemoteUpload
                    },
                    co.golink.tester.ui.components.SpeedDialAction(Icons.Outlined.DriveFolderUpload, "Carregar pasta".tr(), primary) {
                        folderPicker.launch(null)
                    },
                    co.golink.tester.ui.components.SpeedDialAction(Icons.Outlined.FileUpload, "Carregar ficheiros".tr(), primary) {
                        filePicker.launch(arrayOf("*/*"))
                    },
                ),
            )
        } else if (fabMenuOpen) {
            LaunchedEffect(Unit) { fabMenuOpen = false }
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
            onCopyLink = if (canCopyLinks && target.id !in foreignActivityIds) { { viewModel.copyShareLink(target) } } else null,
            canEdit = !state.readOnly,
            onComingSoon = { viewModel.notifyComingSoon() },
            onConvertToTeamFolder = { actionTarget = target; activeDialog = ActionDialog.ConvertToTeamFolder },
            onFileRequest = { actionTarget = target; activeDialog = ActionDialog.FileRequest },
            onDissolveTeam = (target as? BrowseItem.Folder)?.takeIf { state.mode is BrowseMode.TeamFolder && it.isTeamFolder }?.let { f ->
                { actionTarget = f; activeDialog = ActionDialog.DissolveTeam }
            },
            onLeaveTeam = (target as? BrowseItem.Folder)?.takeIf { state.mode is BrowseMode.SharedWithMe && (state.mode as BrowseMode.SharedWithMe).id == null || it.id == state.crumbs.getOrNull(1)?.id && state.mode is BrowseMode.SharedWithMe }?.let { f ->
                { actionTarget = f; activeDialog = ActionDialog.LeaveTeam }
            },
        )
    }

    membersPopupFolder?.let { folder ->
        TeamMembersPopup(
            folder = folder,
            onDismiss = { membersPopupFolder = null },
        )
    }

    when (activeDialog) {
        ActionDialog.CreateFolder -> co.golink.tester.ui.components.dialogs.CreateFolderDialog(
            location = state.crumbs.lastOrNull()?.name ?: "O meu Drive".tr(),
            onDismiss = { activeDialog = ActionDialog.None },
            onCreate = { name, color -> viewModel.createFolder(name, color) },
        )
        ActionDialog.CreateTextFile -> co.golink.tester.ui.components.dialogs.CreateTextFileDialog(
            location = state.crumbs.lastOrNull()?.name ?: "O meu Drive".tr(),
            onDismiss = { activeDialog = ActionDialog.None },
            onCreate = { filename, openEditor -> viewModel.createTextFile(filename, openEditor) },
        )
        ActionDialog.Rename -> actionTarget?.let { target ->
            co.golink.tester.ui.components.dialogs.EditItemDialog(
                item = target,
                onDismiss = { activeDialog = ActionDialog.None; actionTarget = null },
                onSave = { name, appearance -> viewModel.rename(target, name, appearance) },
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
                    onCreate = { pwd, perm, minutes, limit, views -> viewModel.createShare(pwd, perm, minutes, limit, views) },
                    onUpdate = { pwd, perm, minutes, limit, views, prot -> viewModel.updateCurrentShare(pwd, perm, minutes, limit, views, prot) },
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
        ActionDialog.RemoteUpload -> co.golink.tester.ui.components.dialogs.RemoteUploadDialog(
            location = state.crumbs.lastOrNull()?.name ?: "O meu Drive".tr(),
            onDismiss = { activeDialog = ActionDialog.None },
            onImport = { urls -> viewModel.remoteUpload(urls.joinToString("\n")) },
        )
        ActionDialog.EmptyTrash -> ConfirmDialog(
            title = "Esvaziar lixo?".tr(),
            message = "Todos os itens no lixo serão eliminados para sempre.".tr(),
            confirmText = "Esvaziar lixo".tr(),
            destructive = true,
            onDismiss = { activeDialog = ActionDialog.None },
            onConfirm = viewModel::emptyTrash,
        )
        ActionDialog.DissolveTeam -> (actionTarget as? BrowseItem.Folder)?.let { folder ->
            ConfirmDialog(
                title = "Dissolver a equipa?".tr(),
                message = "Os membros deixam de ter acesso e a pasta volta para os teus ficheiros.".tr(),
                confirmText = "Dissolver".tr(),
                destructive = true,
                onDismiss = { activeDialog = ActionDialog.None; actionTarget = null },
                onConfirm = { viewModel.dissolveTeam(folder) },
            )
        }
        ActionDialog.LeaveTeam -> (actionTarget as? BrowseItem.Folder)?.let { folder ->
            ConfirmDialog(
                title = "Sair da pasta de equipa?".tr(),
                message = "Deixas de ver esta pasta. Só o dono te pode voltar a convidar.".tr(),
                confirmText = "Sair".tr(),
                destructive = true,
                onDismiss = { activeDialog = ActionDialog.None; actionTarget = null },
                onConfirm = { viewModel.leaveTeam(folder) },
            )
        }
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
                fetchShare = viewModel::fetchShare,
                fetchActivity = { page -> viewModel.itemActivity(target, page) },
                location = if (state.mode is BrowseMode.Folder || state.mode is BrowseMode.TeamFolder || state.mode is BrowseMode.SharedWithMe) {
                    state.crumbs.lastOrNull()?.name
                } else null,
                onDownload = if (state.mode == BrowseMode.Trash) null else {
                    { viewModel.downloadItem(target) }
                },
                onMove = if (state.readOnly || state.mode == BrowseMode.Trash) null else {
                    {
                        viewModel.loadNavigationTree()
                        actionTarget = target
                        activeDialog = ActionDialog.Move
                    }
                },
                onDelete = if (state.readOnly) null else {
                    {
                        actionTarget = target
                        activeDialog = if (state.mode == BrowseMode.Trash) ActionDialog.PermanentDelete else ActionDialog.Delete
                    }
                },
                // Fora do lixo, como a ação "Partilhar" do menu.
                onShare = if (state.mode == BrowseMode.Trash) null else {
                    {
                        actionTarget = target
                        viewModel.openShareDialog(target)
                        activeDialog = ActionDialog.Share
                    }
                },
            )
        }
        ActionDialog.CreateTeamFolder -> CreateTeamFolderDialog(
            isConvert = false,
            onDismiss = { activeDialog = ActionDialog.None },
            onCreate = { name, invites -> viewModel.createTeamFolder(name, invites) },
            onConvert = {},
            ownerName = user?.name.orEmpty(),
        )
        ActionDialog.ConvertToTeamFolder -> actionTarget?.let { target ->
            CreateTeamFolderDialog(
                isConvert = true,
                onDismiss = { activeDialog = ActionDialog.None; actionTarget = null },
                onCreate = { _, _ -> },
                onConvert = { invites -> viewModel.convertToTeamFolder(target.id, invites) },
                folderName = target.name,
                ownerName = user?.name.orEmpty(),
            )
        }
        ActionDialog.FileRequest -> CreateFileRequestDialog(
            onDismiss = { activeDialog = ActionDialog.None },
            folderName = actionTarget?.name ?: state.crumbs.lastOrNull()?.name ?: "O meu Drive".tr(),
            // actionTarget definido = pedido para essa pasta (menu "…"); null = pasta actual (botão +).
            onCreate = { name, email, notes, days -> viewModel.createFileRequest(name, email, notes, actionTarget?.id, days) },
        )
        ActionDialog.None -> Unit
    }
}

// "1 vídeo selecionado · 19.30 MB", "3 itens selecionados · 4.10 MB".
private fun selectionSummary(selected: List<BrowseItem>): String? {
    if (selected.isEmpty()) return null
    val n = selected.size
    val type = selected.map { (it as? BrowseItem.File)?.type ?: "folder" }.distinct().singleOrNull()
    val label = when (type) {
        "video" -> if (n == 1) "1 vídeo selecionado".tr() else "%d vídeos selecionados".tr().format(n)
        "image" -> if (n == 1) "1 imagem selecionada".tr() else "%d imagens selecionadas".tr().format(n)
        "folder" -> if (n == 1) "1 pasta selecionada".tr() else "%d pastas selecionadas".tr().format(n)
        else -> if (n == 1) "1 item selecionado".tr() else "%d itens selecionados".tr().format(n)
    }
    val bytes = selected.sumOf { if (it is BrowseItem.File) it.filesizeBytes else (it as BrowseItem.Folder).filesizeBytes }
    return if (bytes > 0) "$label · ${co.golink.tester.ui.common.formatBytes(bytes)}" else label
}

private data class MonthSection(
    val key: String,
    val title: String?,
    val count: String?,
    val items: List<BrowseItem>,
    val folders: Boolean = false,
)

// Secções (grelha e lista, como na web): "Pastas" à cabeça; ficheiros por mês
// quando ordenado por data, senão numa secção "Ficheiros" (só se houver pastas).
private fun browseSections(items: List<BrowseItem>, byDate: Boolean): List<MonthSection> {
    val folders = items.filterIsInstance<BrowseItem.Folder>()
    val files = items.filterIsInstance<BrowseItem.File>()
    val out = mutableListOf<MonthSection>()
    if (folders.isNotEmpty()) {
        val n = folders.size
        out += MonthSection("folders", "Pastas".tr(), if (n == 1) "1 pasta".tr() else "%d pastas".tr().format(n), folders, folders = true)
    }
    if (files.isEmpty()) return out
    if (!byDate) {
        out += if (folders.isEmpty()) MonthSection("files", null, null, files)
        else MonthSection("files", "Ficheiros".tr(), typeBreakdown(files), files)
        return out
    }
    files.groupBy { (it.createdAtIso ?: it.updatedAtIso)?.take(7) ?: "" }.forEach { (ym, group) ->
        val first = group.first()
        val month = co.golink.tester.ui.common.formatMonth(first.createdAtIso ?: first.updatedAtIso).ifEmpty { "—" }
        out += MonthSection("m-$ym", month, typeBreakdown(group), group)
    }
    return out
}

// "6 vídeos, 1 documento" (contagem por tipo, como na web).
private fun typeBreakdown(files: List<BrowseItem.File>): String {
    val order = listOf("video", "image", "audio", "doc")
    val counts = files.groupingBy { if (it.type in order) it.type else "doc" }.eachCount()
    return order.mapNotNull { t ->
        val n = counts[t] ?: return@mapNotNull null
        when (t) {
            "video" -> if (n == 1) "1 vídeo".tr() else "%d vídeos".tr().format(n)
            "image" -> if (n == 1) "1 imagem".tr() else "%d imagens".tr().format(n)
            "audio" -> if (n == 1) "1 áudio".tr() else "%d áudios".tr().format(n)
            else -> if (n == 1) "1 documento".tr() else "%d documentos".tr().format(n)
        }
    }.joinToString(", ")
}

// Título de secção: chevron (recolher), título a negrito, contagem e uma linha.
@Composable
private fun MonthHeader(
    title: String,
    count: String?,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    onToggle: (() -> Unit)? = null,
) {
    val rotation by androidx.compose.animation.core.animateFloatAsState(if (collapsed) -90f else 0f, label = "sec-chevron")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        if (onToggle != null) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = if (collapsed) "Mostrar secção".tr() else "Ocultar secção".tr(),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation },
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                count,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.width(10.dp))
        androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// Vistas com o cabeçalho de pasta (como na web).
private fun hasFolderHeader(state: BrowseUiState): Boolean = when (state.mode) {
    is BrowseMode.Folder, is BrowseMode.TeamFolder, is BrowseMode.SharedWithMe, BrowseMode.Shared, BrowseMode.Trash -> true
    else -> false
}

private fun isInsideFolder(mode: BrowseMode): Boolean = when (mode) {
    is BrowseMode.Folder -> mode.id != null
    is BrowseMode.TeamFolder -> mode.id != null
    is BrowseMode.SharedWithMe -> mode.id != null
    else -> false
}

private fun sectionTitle(mode: BrowseMode): String = when (mode) {
    is BrowseMode.Folder -> "O meu Drive".tr()
    is BrowseMode.TeamFolder -> "Pastas de equipa".tr()
    is BrowseMode.SharedWithMe -> "Partilhado comigo".tr()
    BrowseMode.Shared -> "Links públicos".tr()
    BrowseMode.Trash -> "Lixo".tr()
    BrowseMode.Latest -> "Atividade".tr()
    BrowseMode.Favourites -> "Favoritos".tr()
    is BrowseMode.SearchResults -> "Resultados".tr()
}

private fun headerSection(mode: BrowseMode) = when (mode) {
    is BrowseMode.TeamFolder -> co.golink.tester.ui.components.HeaderSection.Team
    is BrowseMode.SharedWithMe -> co.golink.tester.ui.components.HeaderSection.SharedWithMe
    BrowseMode.Shared -> co.golink.tester.ui.components.HeaderSection.Links
    BrowseMode.Trash -> co.golink.tester.ui.components.HeaderSection.Trash
    else -> co.golink.tester.ui.components.HeaderSection.Drive
}

// Privada / Partilhada (link) / Equipa · permissão.
private fun folderBadge(state: BrowseUiState, root: BrowseItem.Folder?): co.golink.tester.ui.components.FolderBadge? {
    return when (state.mode) {
        is BrowseMode.Folder -> when {
            root?.share != null -> co.golink.tester.ui.components.FolderBadge(Icons.Outlined.Link, "Partilhada".tr(), co.golink.tester.ui.components.FolderBadge.Tone.Green)
            else -> co.golink.tester.ui.components.FolderBadge(Icons.Outlined.Lock, "Privada".tr(), co.golink.tester.ui.components.FolderBadge.Tone.Gray)
        }
        is BrowseMode.TeamFolder, is BrowseMode.SharedWithMe -> if (root != null) {
            val label = if (state.readOnly) "Equipa · só ver".tr() else "Equipa".tr()
            co.golink.tester.ui.components.FolderBadge(Icons.Outlined.Groups, label, co.golink.tester.ui.components.FolderBadge.Tone.Blue)
        } else null
        else -> null
    }
}

private fun topBarTitle(state: BrowseUiState): String = when (val mode = state.mode) {
    is BrowseMode.Folder -> mode.name
    BrowseMode.Latest -> "Atividade".tr()
    BrowseMode.Shared -> "Partilhado".tr()
    BrowseMode.Favourites -> "Favoritos".tr()
    BrowseMode.Trash -> "Lixo".tr()
    is BrowseMode.SearchResults -> "Resultados: ${mode.query}"
    is BrowseMode.TeamFolder -> mode.name
    is BrowseMode.SharedWithMe -> mode.name
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
    showViewControls: Boolean = true,
    focusRequest: Int = 0,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) runCatching { focusRequester.requestFocus() }
    }
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
                .height(50.dp)
                .focusRequester(focusRequester),
        )
        if (!showViewControls) return@Row
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
    val dark = co.golink.tester.ui.theme.LocalDarkTheme.current
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

@Composable
internal fun BrowseItemDetailsSheet(
    item: BrowseItem,
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null,
    fetchShare: (suspend (String) -> Result<co.golink.tester.domain.browse.ShareInfo?>)? = null,
    fetchActivity: (suspend (Int) -> Result<co.golink.tester.domain.activity.ActivityPage>)? = null,
    onDownload: (() -> Unit)? = null,
    onMove: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    location: String? = null,
) {
    co.golink.tester.ui.components.ItemDetailsSheet(
        item = item,
        onDismiss = onDismiss,
        onShare = onShare,
        fetchShare = fetchShare,
        fetchActivity = fetchActivity,
        onDownload = onDownload,
        onMove = onMove,
        onDelete = onDelete,
        location = location,
    )
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
            // Convites ainda por aceitar (como no Info panel da web).
            if (folder.pendingInvites.isNotEmpty()) {
                Text(
                    "Convites pendentes".tr(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                folder.pendingInvites.forEach { invite ->
                    Box(modifier = Modifier.alpha(0.65f)) { MemberRow(member = invite) }
                    Spacer(Modifier.height(4.dp))
                }
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

/** Aviso do Lixo: itens com mais de [days] dias são apagados automaticamente. */
@Composable
private fun TrashRetentionNotice(days: Int, onEmptyNow: () -> Unit) {
    // Cartão de aviso (como na web): ícone âmbar, título, explicação e Esvaziar.
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .background(co.golink.tester.ui.theme.Warning.copy(alpha = 0.16f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = Color(0xFFB45309),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Eliminados ao fim de %d dias".tr().format(days),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Os itens que estão no Lixo há mais de %d dias são eliminados automaticamente.".tr().format(days),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(6.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = onEmptyNow,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, co.golink.tester.ui.theme.Danger.copy(alpha = 0.4f)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Text("Esvaziar".tr(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = co.golink.tester.ui.theme.Danger)
            }
        }
    }
}
