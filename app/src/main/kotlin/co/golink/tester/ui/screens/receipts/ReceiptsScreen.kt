package co.golink.tester.ui.screens.receipts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import co.golink.tester.data.receipts.Aggregate
import co.golink.tester.data.receipts.IrsRules
import co.golink.tester.data.receipts.RcCategories
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.components.drawer.AdaptiveNavigationDrawer
import co.golink.tester.ui.components.drawer.AppDrawerContent
import co.golink.tester.ui.components.drawer.DrawerArea
import co.golink.tester.ui.components.drawer.DrawerItem
import co.golink.tester.ui.components.drawer.DrawerSection
import co.golink.tester.ui.components.drawer.isPermanentDrawer
import co.golink.tester.ui.i18n.rt
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.browse.BrowseShellViewModel
import co.golink.tester.ui.screens.encryption.E2EGateViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ROOT_TABS = setOf("spending", "all", "irs", "budgets")
private val YMD = Regex("\\d{4}-\\d{2}-\\d{2}")

/** Painel pedido por link/notificação → estado inicial do ecrã. */
private data class RcLink(
    val tab: String,
    val irsTab: String = "overview",
    val stack: String = "",
    val kind: String? = null,
    val anchor: String? = null,
)

private fun parseLink(pane: String): RcLink {
    val s = pane.split('/')
    return when (s[0]) {
        "spending" -> if (s.size == 3 && s[1] in setOf("day", "week", "month", "year") && YMD.matches(s[2])) RcLink("spending", kind = s[1], anchor = s[2]) else RcLink("spending")
        "all" -> RcLink("all", stack = s.getOrNull(1)?.let { "receipt:$it" }.orEmpty())
        "budgets" -> RcLink("budgets")
        "budget" -> RcLink("budgets", stack = s.getOrNull(1)?.takeIf { it in RcCategories.SPEND_KEYS }?.let { "budget:$it" }.orEmpty())
        "irs" -> RcLink("irs")
        "efatura" -> RcLink("irs", irsTab = "efatura")
        "scan" -> RcLink("spending", stack = "scan")
        "trash" -> RcLink("all", stack = "trash")
        "recat" -> RcLink("all", stack = s.getOrNull(1)?.let { "recat:$it" }.orEmpty())
        else -> RcLink("spending")
    }
}

@Composable
fun ReceiptsScreen(
    initialPane: String,
    onOpenDrive: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenArea: (DrawerArea) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenBilling: () -> Unit,
    onOpenHelp: (supportStaff: Boolean) -> Unit,
    openDrawerRequest: Boolean = false,
    onDrawerRequestHandled: () -> Unit = {},
    vm: ReceiptsViewModel = hiltViewModel(),
    shell: BrowseShellViewModel = hiltViewModel(),
    gateVm: E2EGateViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val user by shell.user.collectAsStateWithLifecycle()
    val unread by shell.unreadNotifications.collectAsStateWithLifecycle()
    val storage by shell.storage.collectAsStateWithLifecycle()
    val legacyEnabled by shell.legacyEnabled.collectAsStateWithLifecycle()
    val e2eUnlocked by gateVm.unlocked.collectAsStateWithLifecycle()
    val e2eConfigured by gateVm.configuredFlow.collectAsStateWithLifecycle()

    val first = remember { parseLink(initialPane) }
    var tab by rememberSaveable { mutableStateOf(first.tab) }
    var irsTab by rememberSaveable { mutableStateOf(first.irsTab) }
    var stackText by rememberSaveable { mutableStateOf(first.stack) }
    var kind by rememberSaveable { mutableStateOf(first.kind ?: "month") }
    var anchorText by rememberSaveable { mutableStateOf(first.anchor ?: LocalDate.now().toString()) }
    var year by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    var listFilter by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }
    var appliedPane by rememberSaveable { mutableStateOf(initialPane) }

    val stack = stackText.split('|').filter { it.isNotEmpty() }
    fun push(page: String) { stackText = (stack + page).joinToString("|") }
    fun pop() { stackText = stack.dropLast(1).joinToString("|") }
    fun replaceTop(page: String) { stackText = (stack.dropLast(1) + page).joinToString("|") }
    val top = stack.lastOrNull()

    // Link novo com o ecrã já aberto (notificação, App Link): aplica uma vez.
    LaunchedEffect(initialPane) {
        if (initialPane == appliedPane) return@LaunchedEffect
        appliedPane = initialPane
        val l = parseLink(initialPane)
        tab = l.tab
        irsTab = l.irsTab
        stackText = l.stack
        l.kind?.let { kind = it }
        l.anchor?.let { anchorText = it }
    }

    val drawerState = remember { DrawerState(DrawerValue.Closed) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }
    val palette = rcPalette()

    LaunchedEffect(e2eUnlocked) { vm.load() }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state.loaded) {
        if (!state.loaded) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                runCatching { vm.sync() }
                delay(5_000)
            }
        }
    }
    LaunchedEffect(openDrawerRequest) {
        if (openDrawerRequest) {
            scope.launch { drawerState.open() }
            onDrawerRequestHandled()
        }
    }
    LaunchedEffect(toast) {
        val t = toast ?: return@LaunchedEffect
        vm.consumeToast()
        snackbar.showSnackbar(t)
    }
    // Recibo aberto apagado de vez (noutro aparelho): fecha a página.
    LaunchedEffect(top, state.items) {
        val id = top?.substringAfter(':', "")?.takeIf { top.startsWith("receipt:") || top.startsWith("edit:") || top.startsWith("recat:") } ?: return@LaunchedEffect
        if (state.loaded && state.item(id) == null) pop()
    }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = !drawerState.isOpen && stack.isNotEmpty()) { pop() }
    BackHandler(enabled = !drawerState.isOpen && stack.isEmpty() && tab != "spending") { tab = "spending" }

    val years = remember(state.items) { Aggregate.yearsWithReceipts(state.items) }
    val drawerYears = remember(years) {
        val now = LocalDate.now().year
        (years.take(2) + now + (now - 1)).toSortedSet().toList().takeLast(2)
    }
    // "Por validar" do ano fiscal (selo do IRS e do e-Fatura).
    var todo by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.items, state.meta.household, year) {
        todo = withContext(Dispatchers.Default) {
            val now = LocalDateTime.now()
            state.active.count { !it.locked && it.date.startsWith(year.toString()) && IrsRules.efStatus(it, state.meta.household, now) == "validate" }
        }
    }

    val secondaryArea = when {
        user?.role == "admin" -> DrawerArea.Admin
        user?.supportStaff != null -> DrawerArea.Support
        else -> null
    }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }
    val go: (() -> Unit) -> () -> Unit = { action -> { closeDrawer(); action() } }
    val openTab: (String) -> Unit = { t -> tab = t; stackText = "" }
    val ready = state.loaded && !state.needsKeys
    val root = stack.isEmpty()
    fun item(label: String, icon: ImageVector, selected: Boolean, count: Int? = null, badge: Int? = null, onClick: () -> Unit) =
        DrawerItem(label, icon, selected = selected, count = count, badge = badge, onClick = go(onClick))

    val sections = if (!ready) emptyList() else listOf(
        DrawerSection(rt("nav.money"), listOf(
            item(rt("nav.spending"), Icons.Outlined.BarChart, root && tab == "spending") { openTab("spending") },
            item(rt("nav.all"), Icons.AutoMirrored.Outlined.ReceiptLong, root && tab == "all" && listFilter == "all", count = state.active.size) { listFilter = "all"; openTab("all") },
            item(rt("nav.budgets"), Icons.Outlined.Savings, root && tab == "budgets") { openTab("budgets") },
        )),
        DrawerSection(rt("nav.taxes", "year" to year), listOf(
            item(rt("nav.irs"), Icons.Outlined.AccountBalance, root && tab == "irs" && irsTab == "overview") { irsTab = "overview"; openTab("irs") },
            item(rt("nav.efatura"), Icons.Outlined.FactCheck, root && tab == "irs" && irsTab == "efatura", badge = todo.takeIf { it > 0 }) { irsTab = "efatura"; openTab("irs") },
        ) + drawerYears.filter { it != year && drawerYears.size > 1 }.map { y ->
            item("IRS $y", Icons.Outlined.AccountBalance, false) { year = y; irsTab = "overview"; openTab("irs") }
        }),
        DrawerSection(rt("nav.categories"), RcCategories.SPEND_KEYS.map { c ->
            item(rt("cat.$c"), Icons.Outlined.Label, root && tab == "all" && listFilter == "cat-$c") { listFilter = "cat-$c"; openTab("all") }
        }),
        DrawerSection(null, listOf(
            item(rt("app.household"), Icons.Outlined.Groups, top == "household") { stackText = "household" },
            item(rt("app.export"), Icons.Outlined.FileDownload, top == "export") { stackText = "export" },
            item(rt("app.settings"), Icons.Outlined.NotificationsActive, top == "alerts") { stackText = "alerts" },
            item(rt("nav.trash"), Icons.Outlined.Delete, top == "trash", count = state.trash.size.takeIf { it > 0 }) { stackText = "trash" },
        )),
    )

    CompositionLocalProvider(LocalRc provides palette) {
        AdaptiveNavigationDrawer(
            permanent = isPermanentDrawer(),
            drawerState = drawerState,
            drawerContent = {
                AppDrawerContent(
                    user = user,
                    area = DrawerArea.Receipts,
                    onAreaChange = { area ->
                        when (area) {
                            DrawerArea.Receipts -> Unit
                            DrawerArea.Drive -> go(onOpenDrive)()
                            DrawerArea.Calendar -> go(onOpenCalendar)()
                            else -> go { onOpenArea(area) }()
                        }
                    },
                    areas = listOfNotNull(DrawerArea.Drive, DrawerArea.Calendar, DrawerArea.Notes, DrawerArea.Pass, DrawerArea.Receipts, DrawerArea.Legacy.takeIf { legacyEnabled }, secondaryArea),
                    sections = sections,
                    unreadNotifications = unread,
                    onOpenNotifications = go(onOpenNotifications),
                    onHome = go { openTab("spending") },
                    storage = storage,
                    onUpgrade = go(onOpenBilling),
                    appVersion = co.golink.tester.BuildConfig.VERSION_NAME,
                    onProfile = go(onOpenProfile),
                    onAvatarClick = null,
                    onHelp = go { onOpenHelp(user?.supportStaff != null) },
                    onSettings = go(onOpenSettings),
                    onLogout = go { confirmLogout = true },
                    onScanReceipt = if (ready) go { stackText = "scan" } else null,
                )
            },
        ) {
            val showTabs = ready && root
            Scaffold(
                contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                containerColor = palette.bg,
                snackbarHost = { SnackbarHost(snackbar) },
                floatingActionButton = {
                    if (showTabs) {
                        ExtendedFloatingActionButton(
                            onClick = { push("scan") },
                            containerColor = palette.accent,
                            contentColor = if (palette.dark) Color(0xFF042F2E) else Color.White,
                            shape = RoundedCornerShape(18.dp),
                            icon = { Icon(Icons.Outlined.PhotoCamera, null) },
                            text = { Text(rt("nav.scan"), fontWeight = FontWeight.Bold) },
                        )
                    }
                },
                bottomBar = {
                    if (showTabs) {
                        RcTabBar(
                            tabs = listOf(
                                Triple("spending", Icons.Outlined.BarChart, rt("tab.spending")),
                                Triple("all", Icons.AutoMirrored.Outlined.ReceiptLong, rt("tab.receipts")),
                                Triple("irs", Icons.Outlined.AccountBalance, rt("tab.irs")),
                                Triple("budgets", Icons.Outlined.Savings, rt("nav.budgets")),
                            ),
                            selected = tab,
                            badges = mapOf("irs" to todo),
                            onSelect = { tab = it },
                        )
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().background(palette.bg).padding(padding)) {
                    val openMenu: (() -> Unit)? = if (isPermanentDrawer()) null else ({ scope.launch { drawerState.open() } })
                    when {
                        !e2eConfigured && !e2eUnlocked && !vm.hasKey() -> RcCenterCard(Icons.Outlined.Lock, rt("no_e2e_title"), rt("no_e2e_text"), openMenu)
                        state.needsKeys -> RcCenterCard(Icons.Outlined.Lock, rt("e2e_locked_title"), rt("e2e_locked_text"), openMenu) {
                            Button(onClick = gateVm::reopen, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = palette.accent)) { Text("Desbloquear".tr()) }
                        }
                        state.failed && !state.loaded -> RcCenterCard(Icons.Outlined.ErrorOutline, null, rt("load_failed"), openMenu) {
                            OutlinedButton(onClick = { vm.retry() }, shape = RoundedCornerShape(12.dp)) { Text(rt("retry")) }
                        }
                        !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp, color = palette.accent) }
                        top != null && (top.startsWith("receipt:") || top.startsWith("recat:")) -> ReceiptDetailPage(
                            vm = vm,
                            state = state,
                            id = top.substringAfter(':'),
                            recat = top.startsWith("recat:"),
                            onBack = ::pop,
                            onEdit = { push("edit:${top.substringAfter(':')}") },
                        )
                        top == "scan" || (top != null && top.startsWith("edit:")) -> ReceiptScanPage(
                            vm = vm,
                            state = state,
                            editId = top.removePrefix("edit:").takeIf { top.startsWith("edit:") },
                            onClose = ::pop,
                            onSaved = { id -> if (top == "scan") replaceTop("receipt:$id") else pop() },
                        )
                        top != null && top.startsWith("budget:") -> BudgetDetailPage(
                            vm = vm,
                            state = state,
                            cat = top.removePrefix("budget:"),
                            onBack = ::pop,
                            onOpenReceipt = { push("receipt:$it") },
                        )
                        top == "trash" -> ReceiptsTrashPage(vm, state, onBack = ::pop, onOpen = { push("receipt:$it") })
                        top == "household" -> HouseholdPage(vm, state, onBack = ::pop)
                        top == "export" -> ReceiptsExportPage(vm, state, year = year, years = drawerYears.ifEmpty { listOf(year) }, onBack = ::pop)
                        top == "alerts" -> ReceiptsAlertsPage(vm, state, onBack = ::pop)
                        else -> when (tab) {
                            "all" -> ReceiptsListPane(
                                vm = vm,
                                state = state,
                                year = year,
                                years = years,
                                onYear = { year = it },
                                filter = listFilter,
                                onFilter = { listFilter = it },
                                query = query,
                                onQuery = { query = it },
                                onOpen = { push("receipt:$it") },
                                openMenu = openMenu,
                            )
                            "irs" -> IrsPane(
                                vm = vm,
                                state = state,
                                year = year,
                                years = years,
                                onYear = { year = it },
                                tab = irsTab,
                                onTab = { irsTab = it },
                                todo = todo,
                                onOpenReceipt = { push("receipt:$it") },
                                onOpenHousehold = { push("household") },
                                onFilter = { f -> listFilter = f; tab = "all" },
                                openMenu = openMenu,
                            )
                            "budgets" -> BudgetsPane(
                                vm = vm,
                                state = state,
                                onOpenBudget = { push("budget:$it") },
                                openMenu = openMenu,
                            )
                            else -> SpendingPane(
                                vm = vm,
                                state = state,
                                kind = kind,
                                anchor = runCatching { LocalDate.parse(anchorText) }.getOrDefault(LocalDate.now()),
                                onKind = { kind = it },
                                onAnchor = { anchorText = it.toString() },
                                years = years,
                                onOpenReceipt = { push("receipt:$it") },
                                onOpenIrs = { y -> year = y; irsTab = "overview"; tab = "irs" },
                                onOpenBudget = { push("budget:$it") },
                                onOpenCategory = { c -> listFilter = "cat-$c"; tab = "all" },
                                openMenu = openMenu,
                            )
                        }
                    }
                }
            }
        }
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

/** Cartão centrado (sem E2E, bloqueado, falhou). */
@Composable
fun RcCenterCard(icon: ImageVector, title: String?, text: String, openMenu: (() -> Unit)?, action: @Composable () -> Unit = {}) {
    val p = LocalRc.current
    Column(Modifier.fillMaxSize()) {
        RcTopBar(rt("title"), openMenu)
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().background(p.card, RoundedCornerShape(20.dp)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(52.dp).background(p.accentSoft, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = p.accent)
                }
                if (title != null) Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = p.text, textAlign = TextAlign.Center)
                Text(text, fontSize = 14.sp, color = p.muted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                action()
            }
        }
    }
}
