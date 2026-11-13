package co.golink.tester.ui.screens.notes

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.components.dialogs.CreateSheetScaffold
import co.golink.tester.ui.components.dialogs.FieldLabel
import co.golink.tester.ui.components.dialogs.NameField
import co.golink.tester.ui.components.drawer.AdaptiveNavigationDrawer
import co.golink.tester.ui.components.drawer.AppDrawerContent
import co.golink.tester.ui.components.drawer.DrawerArea
import co.golink.tester.ui.components.drawer.DrawerItem
import co.golink.tester.ui.components.drawer.DrawerSection
import co.golink.tester.ui.components.drawer.isPermanentDrawer
import co.golink.tester.ui.i18n.nt
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.browse.BrowseShellViewModel
import co.golink.tester.ui.screens.calendar.DrivePickerDialog
import co.golink.tester.ui.screens.encryption.E2EGateViewModel
import co.golink.tester.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/** Cores dos cadernos (NoteNotebook::COLORS / notesUi.js). */
private val NOTEBOOK_COLORS = listOf(
    "blue" to 0xFF2563EB, "purple" to 0xFF7C3AED, "green" to 0xFF16A34A,
    "red" to 0xFFDC2626, "orange" to 0xFFEA580C, "pink" to 0xFFDB2777,
    "teal" to 0xFF0D9488, "yellow" to 0xFFCA8A04, "slate" to 0xFF475569,
)

private fun notebookColor(name: String) = Color(NOTEBOOK_COLORS.firstOrNull { it.first == name }?.second ?: 0xFF2563EB)

private fun js(value: String) = JsonPrimitive(value).toString()

private sealed interface NotebookDialog {
    data object Create : NotebookDialog
    data object Manage : NotebookDialog
    data class Edit(val notebook: NotesSidebar.Notebook) : NotebookDialog
    data class Delete(val notebook: NotesSidebar.Notebook) : NotebookDialog
}

/** Menu da área Notes, igual ao SidebarNotesMenu.vue. */
private fun notesSections(s: NotesSidebar, open: (String) -> Unit, onDialog: (NotebookDialog) -> Unit): List<DrawerSection> {
    fun item(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, path: String, count: Int? = null) =
        DrawerItem(label, icon, selected = selected, count = count?.takeIf { it > 0 }, onClick = { open(path) })
    return listOfNotNull(
        DrawerSection(null, listOf(
            item(nt("all"), Icons.Outlined.Description, s.section == "NotesAll", "all", s.all),
            item(nt("pinned"), Icons.Outlined.PushPin, s.section == "NotesPinned", "pinned", s.pinned),
            item(nt("shared"), Icons.Outlined.People, s.section == "NotesShared", "shared", s.shared),
            item(nt("checklists"), Icons.Outlined.CheckBox, s.section == "NotesChecklists", "checklists"),
            item(nt("search"), Icons.Outlined.Search, s.section == "NotesSearch", "search"),
        )),
        DrawerSection(nt("notebooks"), s.notebooks.map { nb ->
            item(nb.name, Icons.Outlined.Book, s.section == "NotesNotebook" && s.notebook == nb.id, "notebook/${nb.id}", nb.count)
        } + listOfNotNull(
            DrawerItem(nt("new_notebook"), Icons.Outlined.Add, onClick = { onDialog(NotebookDialog.Create) }),
            if (s.notebooks.isNotEmpty()) DrawerItem("%s · %s".format(nt("rename"), nt("delete")), Icons.Outlined.Edit, onClick = { onDialog(NotebookDialog.Manage) }) else null,
        )),
        if (s.tags.isNotEmpty()) DrawerSection(nt("tags"), s.tags.map { (tag, count) ->
            item("#$tag", Icons.Outlined.Tag, s.section == "NotesTag" && s.tag == tag, "tag/" + Uri.encode(tag), count)
        }) else null,
        DrawerSection(null, listOf(
            item(nt("history_trash"), Icons.Outlined.History, s.section == "NotesHistory", "history"),
        )),
    )
}

/**
 * Área Notes: menu nativo (rail + secções, cadernos e tags) e a área Notes da
 * web numa WebView (/notes-embed) — lista, editor Yjs em tempo real,
 * Checklists, pesquisa no aparelho, histórico e lixo.
 */
@Composable
fun NotesScreen(
    onOpenDrive: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenArea: (DrawerArea) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenBilling: () -> Unit,
    onOpenHelp: (supportStaff: Boolean) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenEvent: (String) -> Unit,
    onBack: () -> Unit,
    openDrawerRequest: Boolean = false,
    onDrawerRequestHandled: () -> Unit = {},
    vm: NotesViewModel = hiltViewModel(),
    shell: BrowseShellViewModel = hiltViewModel(),
    gateVm: E2EGateViewModel = hiltViewModel(),
    homeVm: NotesHomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val user by shell.user.collectAsStateWithLifecycle()
    val unread by shell.unreadNotifications.collectAsStateWithLifecycle()
    val storage by shell.storage.collectAsStateWithLifecycle()
    val legacyEnabled by shell.legacyEnabled.collectAsStateWithLifecycle()
    val e2eUnlocked by gateVm.unlocked.collectAsStateWithLifecycle()
    val e2eConfigured by gateVm.configuredFlow.collectAsStateWithLifecycle()
    val e2eLocked = e2eConfigured && !e2eUnlocked
    val sidebar by vm.sidebar.collectAsStateWithLifecycle()
    val drivePick by vm.drivePick.collectAsStateWithLifecycle()

    val homeState by homeVm.state.collectAsStateWithLifecycle()
    val homeSection by homeVm.section.collectAsStateWithLifecycle()
    // A lista é nativa; o editor de uma nota ainda é a página (WebView), carregada por
    // trás e só à vista com uma nota aberta. Fecha quando a página sai da nota.
    var editorOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(homeVm.initialNoteId != null) }
    var sawEditing by remember { mutableStateOf(false) }
    // Rota da nota que acabámos de fechar: os eventos dela que cheguem atrasados (ainda
    // editing=true) não podem contar para a nota seguinte — senão o false que vem a
    // seguir fechava o editor novo. Esquece-se ao primeiro evento de outra rota.
    var closedRoute by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sidebar, editorOpen) {
        val stale = closedRoute != null && sidebar.route == closedRoute
        if (closedRoute != null && sidebar.route != closedRoute) closedRoute = null
        if (!editorOpen) {
            sawEditing = false
        } else if (stale) {
            // evento da nota anterior: ignora
        } else if (sidebar.editing) {
            sawEditing = true
        } else if (sawEditing) {
            sawEditing = false
            editorOpen = false
        }
    }
    LaunchedEffect(editorOpen) { if (!editorOpen) homeVm.syncNow() }

    // Sem rememberSaveable: ao sair por navegação a gaveta ainda está "aberta" (o fecho é cancelado)
    // e voltava aberta por cima do conteúdo. Abrir de propósito vai por openDrawerRequest.
    val drawerState = androidx.compose.runtime.remember { androidx.compose.material3.DrawerState(DrawerValue.Closed) }
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<NotebookDialog?>(null) }

    // Página ainda a arrancar (sem window.GoLinkNotes): guarda o último pedido do menu e corre-o quando estiver pronta.
    val pageReady by vm.pageReady.collectAsStateWithLifecycle()
    var pendingRun by remember { mutableStateOf<String?>(null) }
    fun run(script: String) {
        val view = webView
        if (view == null) { pendingRun = script; return }
        view.evaluateJavascript("window.GoLinkNotes ? (window.GoLinkNotes.$script, 1) : 0") { if (it != "1") pendingRun = script }
    }
    fun openNote(id: String) {
        sawEditing = false
        editorOpen = true
        run("navigate(${js("all/$id")})")
    }
    fun newNote(notebookId: String?) {
        sawEditing = false
        editorOpen = true
        run("newNote(${notebookId?.let(::js) ?: "null"})")
    }
    fun closeEditor() {
        if (sidebar.editing) closedRoute = sidebar.route
        editorOpen = false
        run("navigate(${js("all")})")
    }

    // /sync a cada 5 s com o ecrã visível; avisos da lista nativa.
    val syncOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(syncOwner, e2eUnlocked) {
        if (!e2eUnlocked) return@LaunchedEffect
        syncOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { homeVm.syncLoop() }
    }
    LaunchedEffect(Unit) {
        homeVm.toasts.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(pageReady, webView, pendingRun) {
        val script = pendingRun ?: return@LaunchedEffect
        val view = webView ?: return@LaunchedEffect
        if (!pageReady) return@LaunchedEffect
        pendingRun = null
        view.evaluateJavascript("window.GoLinkNotes && window.GoLinkNotes.$script", null)
    }

    // Sair das Notes destrói a WebView: primeiro a página grava o que tem pendente
    // (responde com "flushed"); sem resposta em 3 s, sai na mesma.
    val pendingLeave = remember { mutableMapOf<Int, () -> Unit>() }
    var leaveSeq by remember { mutableStateOf(0) }
    fun leave(action: () -> Unit) {
        val view = webView ?: return action()
        // Já a sair (toques repetidos, Voltar duas vezes): a primeira ação basta.
        if (pendingLeave.isNotEmpty()) return
        val token = ++leaveSeq
        pendingLeave[token] = action
        view.evaluateJavascript("window.GoLinkNotes ? (window.GoLinkNotes.flushThen($token), 1) : 0") { if (it != "1") pendingLeave.remove(token)?.invoke() }
        scope.launch {
            kotlinx.coroutines.delay(3_000)
            pendingLeave.remove(token)?.invoke()
        }
    }

    LaunchedEffect(openDrawerRequest) {
        if (openDrawerRequest) {
            scope.launch { drawerState.open() }
            onDrawerRequestHandled()
        }
    }
    val permanent by rememberUpdatedState(isPermanentDrawer())
    val openFile by rememberUpdatedState(onOpenFile)
    val openEvent by rememberUpdatedState(onOpenEvent)
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                NotesEvent.OpenMenu -> if (!permanent) scope.launch { drawerState.open() }
                is NotesEvent.Toast -> Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                is NotesEvent.OpenFile -> openFile(e.id)
                is NotesEvent.OpenUrl -> runCatching { uriHandler.openUri(e.url) }
                is NotesEvent.OpenEvent -> openEvent(e.id)
                is NotesEvent.Flushed -> pendingLeave.remove(e.token)?.invoke()
            }
        }
    }

    // Voltar: gaveta → o que o editor tiver aberto (popup, a nota) → secção Todas → sair das Notes.
    BackHandler {
        val view = webView
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            editorOpen && view == null -> closeEditor()
            editorOpen && view != null -> {
                // Renderer ocupado/preso: sem resposta em 1,5 s, fecha o editor na mesma.
                var answered = false
                val timeout = scope.launch {
                    kotlinx.coroutines.delay(1_500)
                    if (!answered) { answered = true; closeEditor() }
                }
                view.evaluateJavascript("window.GoLinkNotes ? window.GoLinkNotes.back() : 0") {
                    if (answered) return@evaluateJavascript
                    answered = true
                    timeout.cancel()
                    // "1": a página fechou um popup ou a nota (editing=false fecha o editor).
                    if (it != "1") closeEditor()
                }
            }
            homeSection != NotesSection.All -> homeVm.open(NotesSection.All)
            else -> leave(onBack)
        }
    }

    // Menu (gaveta) a partir do estado nativo.
    val nativeSidebar = remember(homeState, homeSection) {
        val notes = homeState.notes
        NotesSidebar(
            section = when (homeSection) {
                NotesSection.All -> "NotesAll"
                NotesSection.Pinned -> "NotesPinned"
                NotesSection.Shared -> "NotesShared"
                is NotesSection.Notebook -> "NotesNotebook"
                is NotesSection.Tag -> "NotesTag"
                NotesSection.Checklists -> "NotesChecklists"
                NotesSection.Search -> "NotesSearch"
                NotesSection.History -> "NotesHistory"
            },
            notebook = (homeSection as? NotesSection.Notebook)?.id,
            tag = (homeSection as? NotesSection.Tag)?.name,
            all = notes.size,
            pinned = notes.count { it.pinned },
            shared = notes.count { it.shared },
            notebooks = homeState.notebooks.map { nb -> NotesSidebar.Notebook(nb.id, nb.name, nb.color, notes.count { it.notebookId == nb.id }) },
            tags = notes.flatMap { it.meta.tags }.groupingBy { it }.eachCount().toSortedMap(String.CASE_INSENSITIVE_ORDER).map { it.key to it.value },
        )
    }

    val secondaryArea = when {
        user?.role == "admin" -> DrawerArea.Admin
        user?.supportStaff != null -> DrawerArea.Support
        else -> null
    }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }
    val go: (() -> Unit) -> () -> Unit = { action -> { closeDrawer(); leave(action) } }
    val open: (String) -> Unit = { path ->
        closeDrawer()
        if (editorOpen) closeEditor()
        homeVm.open(NotesSection.parse(path).first)
    }

    AdaptiveNavigationDrawer(
        permanent = isPermanentDrawer(),
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                user = user,
                area = DrawerArea.Notes,
                onAreaChange = { area ->
                    when (area) {
                        DrawerArea.Notes -> Unit
                        DrawerArea.Drive -> go(onOpenDrive)()
                        DrawerArea.Calendar -> go(onOpenCalendar)()
                        else -> go { onOpenArea(area) }()
                    }
                },
                areas = listOfNotNull(DrawerArea.Drive, DrawerArea.Calendar, DrawerArea.Notes, DrawerArea.Pass, DrawerArea.Receipts, DrawerArea.Legacy.takeIf { legacyEnabled }, secondaryArea),
                sections = if (e2eUnlocked) notesSections(nativeSidebar, open) { closeDrawer(); dialog = it } else emptyList(),
                unreadNotifications = unread,
                onOpenNotifications = go(onOpenNotifications),
                onHome = { open("all") },
                storage = storage,
                onUpgrade = go(onOpenBilling),
                appVersion = co.golink.tester.BuildConfig.VERSION_NAME,
                onProfile = go(onOpenProfile),
                onAvatarClick = null,
                onHelp = go { onOpenHelp(user?.supportStaff != null) },
                onSettings = go(onOpenSettings),
                onLogout = go { confirmLogout = true },
                lockedContent = if (e2eLocked) {
                    {
                        Column(Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
                            Text(nt("locked_text"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { closeDrawer(); gateVm.reopen() }) { Text("Desbloquear".tr()) }
                        }
                    }
                } else null,
                // Fica nas Notes: não passa pelo caminho de saída (flushThen / pendingLeave).
                onNewNote = if (e2eUnlocked) ({ closeDrawer(); newNote((homeSection as? NotesSection.Notebook)?.id); Unit }) else null,
            )
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
                .imePadding(),
        ) {
            when {
                e2eLocked -> CenterMessage(Icons.Outlined.Lock, nt("locked_title"), nt("locked_text"), onMenu = { scope.launch { drawerState.open() } }) {
                    Button(onClick = gateVm::reopen, shape = RoundedCornerShape(12.dp)) { Text("Desbloquear".tr()) }
                }
                !e2eUnlocked -> CenterMessage(Icons.Outlined.Lock, nt("no_e2e_title"), nt("no_e2e_text"), onMenu = { scope.launch { drawerState.open() } })
                // Este ramo só existe desbloqueado: um lock tira a WebView da composição
                // e o desbloqueio seguinte cria outra (nada do estado anterior sobrevive).
                else -> Box(Modifier.fillMaxSize()) {
                    // Sessão ainda a restaurar: a página precisa do utilizador logo no arranque.
                    if (user != null || webView != null) {
                        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (editorOpen) 1f else 0f }) {
                            NotesWebView(vm, visible = editorOpen, onView = { webView = it }, onClose = { closeEditor() }, onMenu = { scope.launch { drawerState.open() } })
                        }
                    }
                    if (!editorOpen) {
                        // pointerInput: os toques não passam para a WebView escondida por baixo.
                        Box(Modifier.fillMaxSize().pointerInput(Unit) {}) {
                            NotesHome(
                                vm = homeVm,
                                showMenuButton = !isPermanentDrawer(),
                                onMenu = { scope.launch { drawerState.open() } },
                                onOpenNote = {
                                    when {
                                        !it.locked -> openNote(it.id)
                                        !e2eUnlocked -> gateVm.reopen()
                                        else -> homeVm.refresh()
                                    }
                                },
                                onNewNote = { nb -> newNote(nb) },
                                onNotebookDialog = { dialog = NotebookDialog.Create },
                            )
                        }
                    }
                }
            }
        }
    }

    drivePick?.let { pick ->
        DrivePickerDialog(
            exclude = pick.exclude,
            load = vm::listDriveFolder,
            onDismiss = { vm.completeDrivePick(emptyList()) },
            onPick = { vm.completeDrivePick(it) },
            title = nt("ins_file"),
            confirmLabel = { n -> if (n == 0) nt("insert") else "${nt("insert")} ($n)" },
        )
    }

    when (val d = dialog) {
        NotebookDialog.Create -> NotebookSheet(
            title = nt("new_notebook"),
            initialName = "",
            initialColor = "blue",
            onDismiss = { dialog = null },
            onSave = { name, color -> dialog = null; homeVm.createNotebook(name, color) },
        )
        is NotebookDialog.Edit -> NotebookSheet(
            title = nt("rename"),
            initialName = d.notebook.name,
            initialColor = d.notebook.color,
            onDismiss = { dialog = null },
            onSave = { name, color ->
                dialog = null
                val newName = name.takeIf { it != d.notebook.name }
                val newColor = color.takeIf { it != d.notebook.color }
                if (newName != null || newColor != null) homeVm.updateNotebook(d.notebook.id, newName, newColor)
            },
        )
        NotebookDialog.Manage -> ManageNotebooksSheet(
            notebooks = nativeSidebar.notebooks,
            onDismiss = { dialog = null },
            onEdit = { dialog = NotebookDialog.Edit(it) },
            onDelete = { dialog = NotebookDialog.Delete(it) },
        )
        is NotebookDialog.Delete -> ConfirmDialog(
            title = nt("delete_notebook_title"),
            message = nt("delete_notebook_text", "name" to d.notebook.name),
            confirmText = nt("delete"),
            destructive = true,
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; homeVm.deleteNotebook(d.notebook.id) },
        )
        null -> Unit
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "Sair da Conta?".tr(),
            message = "Tens a certeza que queres sair da tua conta?".tr(),
            confirmText = "Sair".tr(),
            destructive = true,
            onDismiss = { confirmLogout = false },
            onConfirm = { confirmLogout = false; shell.logout() },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun NotesWebView(vm: NotesViewModel, visible: Boolean, onView: (WebView?) -> Unit, onClose: () -> Unit, onMenu: () -> Unit) {
    val dark = LocalDarkTheme.current
    val darkNow by rememberUpdatedState(dark)
    var webView by remember { mutableStateOf<WebView?>(null) }
    LaunchedEffect(dark) { webView?.setBackgroundColor(if (dark) 0xFF151515.toInt() else 0xFFFFFFFF.toInt()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var rendererGone by remember { mutableStateOf(false) }
    val pageUri = remember { Uri.parse(vm.notesUrl) }
    val pageReady by vm.pageReady.collectAsStateWithLifecycle()
    // Página que nunca avisa (bundle antigo no servidor): ao fim de 15 s mostra o que houver.
    var readyTimeout by remember { mutableStateOf(false) }
    // Diagnóstico: depois de tirar o indicador, mede a página e a WebView; se não houver
    // nada à vista, mostra no ecrã o estado e a consola (sem adb não há outra forma de ver).
    var diag by remember { mutableStateOf<String?>(null) }
    // Escondida atrás da lista nativa, a página finge estar em segundo plano: o listSync
    // da web pára o /sync de 5 s (a lista nativa já faz o seu) e o WebView pausa o que pode.
    // Ao voltar a ficar visível, o visibilitychange faz logo um sync.
    LaunchedEffect(visible, pageReady, loading, webView) {
        val view = webView ?: return@LaunchedEffect
        if (loading) return@LaunchedEffect
        view.evaluateJavascript(hiddenScript(!visible), null)
        if (visible) view.onResume() else view.onPause()
    }
    LaunchedEffect(loading, pageReady, readyTimeout, failed, visible) {
        diag = null
        // Escondida (lista nativa à frente) não há nada para ver: sem diagnóstico.
        if (!visible || loading || failed || !(pageReady || readyTimeout)) return@LaunchedEffect
        kotlinx.coroutines.delay(2_500)
        val view = webView ?: return@LaunchedEffect
        val native = "webview=${view.width}x${view.height} attached=${view.isAttachedToWindow} shown=${view.isShown} alpha=${view.alpha} " +
            "layer=${view.layerType} ready=$pageReady timeout=$readyTimeout"
        view.evaluateJavascript(NOTES_PROBE) { raw ->
            val probe = runCatching { org.json.JSONObject(org.json.JSONTokener(raw).nextValue() as String) }.getOrNull()
            val text = probe?.optInt("text") ?: 0
            val appH = probe?.optDouble("appH") ?: 0.0
            if (probe == null || text == 0 || appH < 1 || view.height == 0 || !view.isShown) {
                // Em release, nada que possa trazer texto decifrado: sem amostra, sem consola, sem Logcat.
                val debug = co.golink.tester.BuildConfig.DEBUG
                if (!debug) probe?.remove("sample")
                diag = buildString {
                    appendLine(nt("diag_title"))
                    appendLine(native)
                    appendLine(probe?.toString(1) ?: if (debug) "probe=$raw" else "probe=?")
                    if (debug) {
                        appendLine("— consola —")
                        vm.consoleLog.forEach { appendLine(it) }
                    }
                }
                if (debug) android.util.Log.w("NotesWebView", diag!!)
            }
        }
    }
    LaunchedEffect(loading, pageReady) {
        readyTimeout = false
        if (!loading && !pageReady) {
            kotlinx.coroutines.delay(15_000)
            readyTimeout = true
        }
    }

    // Segundo plano: grava já (a app pode ser morta a seguir).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    webView?.evaluateJavascript("window.GoLinkNotes && window.GoLinkNotes.flush()", null)
                    webView?.onPause()
                }
                Lifecycle.Event.ON_START -> webView?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onView(null)
        }
    }
    // onPause() não pára os temporizadores JS: com a app em segundo plano, pára-os (depois do flush).
    co.golink.tester.ui.common.PauseWebViewTimersInBackground({ webView })

    Box(Modifier.fillMaxSize()) {
        if (!rendererGone) AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    // Sem layoutParams o AndroidView dá WRAP_CONTENT e a WebView fica com o viewport de layout
                    // a 0 px de altura: vh/% valem 0 (folha Insert vazia) e há remedições ao deslizar.
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportZoom(false)
                    // Como no editor: sem zonas brancas ao entrar (transição/gaveta) nem ao deslizar.
                    settings.offscreenPreRaster = true
                    setBackgroundColor(if (dark) 0xFF151515.toInt() else 0xFFFFFFFF.toInt())

                    // Ponte só para a origem do servidor (mensagens); sem suporte na WebView, a interface antiga.
                    val bridge = NotesBridge(vm, { darkNow }, this)
                    if (!co.golink.tester.ui.screens.editor.installMessageBridge(this, "GoLinkNotesPort", pageUri, bridge)) {
                        bridge.legacy = co.golink.tester.ui.screens.editor.LegacyPageBridge(bridge, "__glnResolve") { bridge.view }
                            .also { addJavascriptInterface(it, "GoLinkNotesHost") }
                    }
                    tag = bridge

                    // Erros de JS da página no Logcat (tag NotesWebView).
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                            val line = "${m.messageLevel()}: ${m.message()} (${m.sourceId().substringAfterLast('/')}:${m.lineNumber()})"
                            vm.logConsole(line)
                            if (co.golink.tester.BuildConfig.DEBUG && (m.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR || m.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.WARNING)) {
                                android.util.Log.w("NotesWebView", line)
                            }
                            return true
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        // Só a página das Notes corre aqui (é a que tem a ponte); ligações das notas abrem fora.
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url
                            // A página (mesmo depois de redirecionada: www/apex, barra final) carrega aqui.
                            // O redirect só vale dentro do mesmo site (www/apex), nunca para outro host.
                            if (url.path?.trimEnd('/') == pageUri.path?.trimEnd('/') && sameSite(url, pageUri)) return false
                            // Só web e email: content:, file:, intent: e esquemas de outras apps ficam de fora.
                            if (url.scheme?.lowercase() in setOf("http", "https", "mailto")) {
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                            return true
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            // A ponte antiga só responde à página das Notes deste servidor (frame principal).
                            (view.tag as? NotesBridge)?.legacy?.trusted = sameSite(Uri.parse(url), pageUri)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            // Os scripts já correram: sem window.GoLinkNotes a página não arrancou
                            // (bundle em falta no servidor / erro de JS) — mostra o erro, não branco.
                            // Com a ponte por mensagens o arranque espera pela resposta de "info":
                            // volta a ver durante ~3 s antes de dar a página como falhada.
                            fun check(left: Int) {
                                view.evaluateJavascript("typeof window.GoLinkNotes === 'object' ? 1 : 0") { booted ->
                                    when {
                                        booted == "1" -> loading = false
                                        left > 0 -> view.postDelayed({ check(left - 1) }, 300)
                                        else -> {
                                            android.util.Log.w("NotesWebView", "página não arrancou: $url")
                                            failed = true
                                            loading = false
                                        }
                                    }
                                }
                            }
                            check(10)
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) {
                                failed = true
                                loading = false
                            }
                        }

                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                            if (request.isForMainFrame) {
                                failed = true
                                loading = false
                            }
                        }

                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            (view.tag as? NotesBridge)?.view = null
                            webView = null
                            onView(null)
                            rendererGone = true
                            failed = true
                            loading = false
                            return true
                        }
                    }

                    vm.onPageLoading()
                    loadUrl(vm.notesUrl)
                    webView = this
                    onView(this)
                }
            },
            update = { view -> view.visibility = if (visible) android.view.View.VISIBLE else android.view.View.INVISIBLE },
            onRelease = { view ->
                webView = null
                onView(null)
                // Saída sem leave() (lock E2E, ecrã descartado): a página grava o pendente antes
                // de a WebView ser destruída (no máx. 3 s); a ponte continua a responder-lhe até lá.
                val bridge = view.tag as? NotesBridge
                val main = android.os.Handler(android.os.Looper.getMainLooper())
                var destroyed = false
                val destroy = Runnable {
                    if (!destroyed) {
                        destroyed = true
                        bridge?.view = null
                        bridge?.onFlushed = null
                        co.golink.tester.ui.screens.editor.uninstallPageBridge(view, "GoLinkNotesPort", "GoLinkNotesHost")
                        // pauseTimers é global: não deixar as outras WebViews paradas.
                        runCatching { view.resumeTimers() }
                        view.destroy()
                    }
                }
                if (bridge?.view == null) destroy.run()
                else {
                    bridge.onFlushed = { main.post(destroy) }
                    view.evaluateJavascript("window.GoLinkNotes ? (window.GoLinkNotes.flushThen(0), 1) : 0") { if (it != "1") destroy.run() }
                    main.postDelayed(destroy, 3_000)
                }
            },
        )

        if (visible) diag?.let { text ->
            Column(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            ) {
                Text(text, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = Color(0xFF9F1239))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { diag = null }) { Text(nt("close")) }
                    Button(onClick = {
                        diag = null
                        loading = true
                        vm.onPageLoading()
                        webView?.loadUrl(vm.notesUrl)
                    }) { Text(nt("retry")) }
                }
            }
        }

        if (!visible) {
            // Carrega por trás; os avisos só fazem sentido com o editor à vista.
        } else if (failed) {
            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(nt("load_failed"), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onClose) { Text(nt("close")) }
                    Button(onClick = {
                        failed = false
                        loading = true
                        vm.onPageLoading()
                        if (rendererGone) rendererGone = false else webView?.loadUrl(vm.notesUrl)
                    }) { Text(nt("retry")) }
                }
            }
        } else if (loading || (!pageReady && !readyTimeout)) {
            // Tapa a página até ela ter algo à vista, sem spinner nem título (entra como o Calendar/Legacy); o menu nativo fica à mão.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                if (!isPermanentDrawer()) {
                    IconButton(onClick = onMenu, modifier = Modifier.padding(4.dp)) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu".tr())
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
    onMenu: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize()) {
        if (!isPermanentDrawer()) {
            IconButton(onClick = onMenu, modifier = Modifier.padding(4.dp)) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu".tr())
            }
        }
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 420.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(26.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 12.dp))
            Text(text, fontSize = 13.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            if (action != null) {
                Spacer(Modifier.height(16.dp))
                action()
            }
        }
    }
}

/** Criar / mudar o nome e a cor de um caderno (o nome é cifrado na página, como na web). */
@Composable
private fun NotebookSheet(
    title: String,
    initialName: String,
    initialColor: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    CreateSheetScaffold(
        title = title,
        onDismiss = onDismiss,
        confirmText = nt("add").takeIf { initialName.isEmpty() } ?: nt("rename"),
        onConfirm = { if (name.isNotBlank()) onSave(name.trim().take(80), color) },
        icon = { Icon(Icons.Outlined.Book, contentDescription = null, tint = Color.White) },
        iconBackground = notebookColor(color),
        confirmEnabled = name.isNotBlank(),
    ) {
        FieldLabel(nt("notebook_name"))
        NameField(value = name, onValueChange = { name = it.take(80) }, placeholder = nt("notebook_name"), clearable = true)
        FieldLabel(nt("color"))
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NOTEBOOK_COLORS.forEach { (key, argb) ->
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .then(if (key == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .clickable { color = key },
                )
            }
        }
    }
}

@Composable
private fun ManageNotebooksSheet(
    notebooks: List<NotesSidebar.Notebook>,
    onDismiss: () -> Unit,
    onEdit: (NotesSidebar.Notebook) -> Unit,
    onDelete: (NotesSidebar.Notebook) -> Unit,
) {
    CreateSheetScaffold(
        title = nt("notebooks"),
        onDismiss = onDismiss,
        confirmText = null,
        onConfirm = {},
        icon = { Icon(Icons.Outlined.Book, contentDescription = null, tint = Color.White) },
        iconBackground = Color(0xFFD97706),
    ) {
        notebooks.forEach { nb ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(notebookColor(nb.color)))
                Text(nb.name, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = { onEdit(nb) }) { Icon(Icons.Outlined.Edit, contentDescription = nt("rename")) }
                IconButton(onClick = { onDelete(nb) }) { Icon(Icons.Outlined.Delete, contentDescription = nt("delete"), tint = Color(0xFFE11D48)) }
            }
        }
    }
}

/** Ponte com a página das Notes (ver installMessageBridge); o trabalho vai para o ViewModel. */
private class NotesBridge(
    private val vm: NotesViewModel,
    private val dark: () -> Boolean,
    /** null = já não responde (renderer morto / destruída). */
    @Volatile var view: WebView?,
) : co.golink.tester.ui.screens.editor.PageBridgeHost {
    /** WebView a sair: chamado quando a página confirma que gravou. */
    @Volatile var onFlushed: (() -> Unit)? = null

    /** Só quando a WebView não suporta mensagens. */
    var legacy: co.golink.tester.ui.screens.editor.LegacyPageBridge? = null

    override fun info(): String = vm.infoJson(dark())

    override fun request(id: Int, method: String, args: String, onDone: (Int) -> Unit) {
        vm.request(id, method, args) { done ->
            if (view != null) onDone(done)
            if (method == "flushed") onFlushed?.invoke()
        }
    }

    override fun take(id: Int): String = vm.take(id)

    override fun toast(message: String) = vm.toast(message)
}

/** Mesmo esquema e mesmo host (www/apex contam como o mesmo site). */
internal fun sameSite(url: Uri, page: Uri): Boolean =
    url.scheme == page.scheme && url.host != null &&
        url.host?.removePrefix("www.") == page.host?.removePrefix("www.") && url.port == page.port

/** Estado da página para o diagnóstico (devolve uma string JSON). */
private const val NOTES_PROBE = """(function(){try{
var a=document.getElementById('app'),r=a?a.getBoundingClientRect():{width:0,height:0};
var t=((a&&a.innerText)||'').trim();
var c=document.elementFromPoint(innerWidth/2,innerHeight/2);
var css=[].slice.call(document.styleSheets).map(function(s){try{return (s.href||'inline').split('/').pop()+':'+s.cssRules.length}catch(e){return (s.href||'inline').split('/').pop()+':x'}}).join(',');
var cs=a?getComputedStyle(a):null;
return JSON.stringify({text:t.length,sample:t.slice(0,120),appW:r.width,appH:r.height,vw:innerWidth,vh:innerHeight,dpr:devicePixelRatio,
hash:location.hash,booted:!!window.GoLinkNotes,html:document.documentElement.className,bodyBg:getComputedStyle(document.body).backgroundColor,
appColor:cs&&cs.color,appOpacity:cs&&cs.opacity,center:c?(c.tagName+'.'+String(c.className).slice(0,80)):null,
nodes:document.getElementsByTagName('*').length,css:css,ua:navigator.userAgent})
}catch(e){return JSON.stringify({error:String(e)})}})()"""

/** Sobrepõe document.hidden/visibilityState na página e avisa os ouvintes. */
private fun hiddenScript(hidden: Boolean): String = """
(function(h){try{
  Object.defineProperty(document,'hidden',{configurable:true,get:function(){return h}});
  Object.defineProperty(document,'visibilityState',{configurable:true,get:function(){return h?'hidden':'visible'}});
  document.dispatchEvent(new Event('visibilitychange'));
}catch(e){}})($hidden)
""".trimIndent()
