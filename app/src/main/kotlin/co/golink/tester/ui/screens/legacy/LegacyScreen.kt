package co.golink.tester.ui.screens.legacy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.golink.tester.data.legacy.LegacyState
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.components.drawer.AdaptiveNavigationDrawer
import co.golink.tester.ui.components.drawer.AppDrawerContent
import co.golink.tester.ui.components.drawer.DrawerArea
import co.golink.tester.ui.components.drawer.DrawerItem
import co.golink.tester.ui.components.drawer.DrawerSection
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import co.golink.tester.ui.components.drawer.isPermanentDrawer
import co.golink.tester.ui.i18n.I18n
import co.golink.tester.ui.i18n.lt
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.browse.BrowseShellViewModel
import co.golink.tester.ui.screens.encryption.E2EGateViewModel
import kotlinx.coroutines.launch

/** Panes do Legacy (as rotas filhas de /legacy na web). */
val LEGACY_PANES = listOf("overview", "items", "messages", "guardians", "recipients", "timing", "activity", "guarding")

/** Separador da barra inferior de cada pane. */
private fun tabOf(pane: String): LegacyTab = when (pane) {
    "items", "messages" -> LegacyTab.Vault
    "guardians", "recipients" -> LegacyTab.People
    "timing", "activity" -> LegacyTab.Settings
    "guarding" -> LegacyTab.Guarding
    else -> LegacyTab.Overview
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegacyScreen(
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
    vm: LegacyViewModel = hiltViewModel(),
    activityVm: LegacyActivityViewModel = hiltViewModel(),
    shell: BrowseShellViewModel = hiltViewModel(),
    gateVm: E2EGateViewModel = hiltViewModel(),
) {
    val legacy by vm.legacy.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val user by shell.user.collectAsStateWithLifecycle()
    val unread by shell.unreadNotifications.collectAsStateWithLifecycle()
    val storage by shell.storage.collectAsStateWithLifecycle()
    val legacyEnabled by shell.legacyEnabled.collectAsStateWithLifecycle()
    val e2eUnlocked by gateVm.unlocked.collectAsStateWithLifecycle()
    val e2eConfigured by gateVm.configuredFlow.collectAsStateWithLifecycle()
    val e2eLocked = e2eConfigured && !e2eUnlocked

    var pane by rememberSaveable { mutableStateOf(initialPane.takeIf { it in LEGACY_PANES } ?: "overview") }
    // Último segmento aberto em cada separador.
    var vaultSub by rememberSaveable { mutableStateOf(if (pane == "messages") "messages" else "items") }
    var peopleSub by rememberSaveable { mutableStateOf(if (pane == "recipients") "recipients" else "guardians") }
    var settingsSub by rememberSaveable { mutableStateOf(if (pane == "activity") "activity" else "timing") }
    LaunchedEffect(pane) {
        when (pane) {
            "items", "messages" -> vaultSub = pane
            "guardians", "recipients" -> peopleSub = pane
            "timing", "activity" -> settingsSub = pane
        }
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    var dialog by remember { mutableStateOf<LegacyDialog?>(null) }

    LaunchedEffect(e2eUnlocked) { if (e2eUnlocked) vm.load() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (e2eUnlocked) vm.refreshIfStale() }
    LaunchedEffect(openDrawerRequest) {
        if (openDrawerRequest) {
            scope.launch { drawerState.open() }
            onDrawerRequestHandled()
        }
    }
    LaunchedEffect(ui.toast) {
        val t = ui.toast ?: return@LaunchedEffect
        snackbar.showSnackbar(lt(t.key, *t.params.toList().toTypedArray()))
        vm.consumeToast()
    }
    LaunchedEffect(pane) { scroll.scrollTo(0) }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    val secondaryArea = when {
        user?.role == "admin" -> DrawerArea.Admin
        user?.supportStaff != null -> DrawerArea.Support
        else -> null
    }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }
    val go: (() -> Unit) -> () -> Unit = { action -> { closeDrawer(); action() } }
    val openPane: (String) -> Unit = { pane = it }
    val tab = tabOf(pane)
    val showTabs = !e2eLocked && legacy.loaded && (legacy.vault != null || legacy.guardingCount > 0)

    AdaptiveNavigationDrawer(
        permanent = isPermanentDrawer(),
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                user = user,
                area = DrawerArea.Legacy,
                onAreaChange = { area ->
                    when (area) {
                        DrawerArea.Legacy -> Unit
                        DrawerArea.Drive -> go(onOpenDrive)()
                        DrawerArea.Calendar -> go(onOpenCalendar)()
                        else -> go { onOpenArea(area) }()
                    }
                },
                areas = listOfNotNull(DrawerArea.Drive, DrawerArea.Calendar, DrawerArea.Notes, DrawerArea.Pass, DrawerArea.Legacy.takeIf { legacyEnabled }, secondaryArea),
                sections = if (e2eLocked || !legacy.loaded) emptyList() else legacySections(legacy, pane) { p -> closeDrawer(); pane = p },
                unreadNotifications = unread,
                onOpenNotifications = go(onOpenNotifications),
                onHome = go { pane = "overview" },
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
                            Text(lt("locked_text"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { closeDrawer(); gateVm.reopen() }) { Text("Desbloquear".tr()) }
                        }
                    }
                } else null,
                footerContent = legacy.vault?.let { v -> { LegacyStatusCard(v) } },
                onAddToVault = if (legacy.editable) go { dialog = LegacyDialog.Add } else null,
            )
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (showTabs) {
                    LegacyTabBar(
                        tabs = listOfNotNull(
                            Triple(LegacyTab.Overview, Icons.Outlined.GridView, lt("overview")),
                            Triple(LegacyTab.Vault, Icons.Outlined.Lock, lt("section_vault")),
                            Triple(LegacyTab.People, Icons.Outlined.People, lt("section_people")),
                            Triple(LegacyTab.Settings, Icons.Outlined.Schedule, lt("section_settings")),
                            if (legacy.guardingCount > 0) Triple(LegacyTab.Guarding, Icons.Outlined.FavoriteBorder, lt("guarding")) else null,
                        ),
                        selected = tab,
                        badge = mapOf(LegacyTab.People to if (legacy.editable) legacy.keysToConfirm.size else 0),
                        onSelect = { t ->
                            pane = when (t) {
                                LegacyTab.Overview -> "overview"
                                LegacyTab.Vault -> vaultSub
                                LegacyTab.People -> peopleSub
                                LegacyTab.Settings -> settingsSub
                                LegacyTab.Guarding -> "guarding"
                            }
                        },
                    )
                }
            },
            floatingActionButton = {
                if (showTabs && legacy.editable && legacy.vault != null && tab == LegacyTab.Vault) {
                    val message = pane == "messages"
                    ExtendedFloatingActionButton(
                        onClick = { dialog = if (message) LegacyDialog.Message(null) else LegacyDialog.Add },
                        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        text = { Text(lt(if (message) "new_message" else "add_to_vault"), fontWeight = FontWeight.SemiBold) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (tab) {
                                    LegacyTab.Vault -> lt("section_vault")
                                    LegacyTab.People -> lt("section_people")
                                    LegacyTab.Settings -> lt("section_settings")
                                    LegacyTab.Guarding -> lt("guarding")
                                    LegacyTab.Overview -> "Legacy"
                                },
                                fontWeight = FontWeight.Bold,
                            )
                            if (tab == LegacyTab.Overview) legacy.vault?.let { v ->
                                Spacer(Modifier.width(10.dp))
                                StatusPill(v)
                            }
                        }
                    },
                    navigationIcon = {
                        if (!isPermanentDrawer()) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                // O registo só precisa das chaves E2E: não espera pelo cofre nem cai com ele.
                val standalone = (pane == "activity" || pane == "guarding") && !legacy.loaded
                when {
                    e2eLocked -> CenterCard(Icons.Outlined.Lock, lt("locked_title"), lt("locked_text")) {
                        Button(onClick = gateVm::reopen, shape = RoundedCornerShape(12.dp)) { Text("Desbloquear".tr()) }
                    }
                    !e2eConfigured && !e2eUnlocked -> CenterCard(Icons.Outlined.Lock, lt("no_e2e_title"), lt("no_e2e_text"))
                    ui.failed && !standalone -> CenterCard(Icons.Outlined.ErrorOutline, null, lt("load_failed")) {
                        OutlinedButton(onClick = { vm.load() }, shape = RoundedCornerShape(12.dp)) { Text(lt("retry")) }
                    }
                    !legacy.loaded && !standalone -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                    legacy.vault == null && !standalone && pane != "guarding" -> Welcome(creating = ui.creating, onCreate = vm::create)
                    else -> Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = if (tab == LegacyTab.Vault) 96.dp else 32.dp)),
                    ) {
                        when (tab) {
                            LegacyTab.Vault -> LegacySegmented(
                                options = listOf(
                                    "items" to "${lt("seg_items")} · ${legacy.vaultItems.size}",
                                    "messages" to "${lt("messages")} · ${legacy.messages.size}",
                                ),
                                selected = pane,
                                onSelect = { pane = it },
                            )
                            LegacyTab.People -> LegacySegmented(
                                options = listOf(
                                    "guardians" to "${lt("guardians")} · ${legacy.guardians.size}",
                                    "recipients" to "${lt("recipients")} · ${legacy.recipients.size}",
                                ),
                                selected = pane,
                                onSelect = { pane = it },
                            )
                            LegacyTab.Settings -> LegacySegmented(
                                options = listOf("timing" to lt("timing"), "activity" to lt("activity")),
                                selected = pane,
                                onSelect = { pane = it },
                            )
                            else -> Unit
                        }
                        if (tab in setOf(LegacyTab.Vault, LegacyTab.People, LegacyTab.Settings)) Spacer(Modifier.height(14.dp))
                        when (pane) {
                            "items" -> LegacyItemsPane(legacy) { dialog = it }
                            "messages" -> LegacyMessagesPane(legacy) { dialog = it }
                            "guardians" -> LegacyGuardiansPane(legacy, ui.busy, vm) { dialog = it }
                            "recipients" -> LegacyRecipientsPane(legacy, vm::toast) { dialog = it }
                            "timing" -> LegacyTimingPane(legacy, ui.busy, vm)
                            "activity" -> LegacyActivityPane(activityVm, ui.checkins, vm::toast)
                            "guarding" -> LegacyGuardingPane(vm::toast)
                            else -> LegacyOverviewPane(legacy, ui.busy, vm, openPane)
                        }
                    }
                }
            }
        }
    }

    LegacyDialogs(dialog, legacy, ui, vm, onDialog = { dialog = it }, onOpenPane = openPane)

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

/** Menu da gaveta, igual ao SidebarLegacyMenu da web. */
private fun legacySections(legacy: LegacyState, pane: String, open: (String) -> Unit): List<DrawerSection> {
    fun item(key: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int? = null) =
        DrawerItem(label, icon, selected = pane == key, count = count?.takeIf { it > 0 }, onClick = { open(key) })
    val guarding = if (legacy.guardingCount > 0) DrawerSection(lt("section_guarding"), listOf(
        item("guarding", lt("guarding"), Icons.Outlined.FavoriteBorder, legacy.guardingCount),
    )) else null
    // Sem cofre ainda: só "Para outros" (o resto abre o ecrã de boas-vindas).
    if (legacy.vault == null) return listOfNotNull(guarding)
    return listOfNotNull(
        DrawerSection(lt("section_vault"), listOf(
            item("overview", lt("overview"), Icons.Outlined.GridView),
            item("items", lt("items"), Icons.Outlined.Lock, legacy.vaultItems.size),
            item("messages", lt("messages"), Icons.Outlined.MailOutline, legacy.messages.size),
        )),
        DrawerSection(lt("section_people"), listOf(
            item("guardians", lt("guardians"), Icons.Outlined.Shield, legacy.guardians.size),
            item("recipients", lt("recipients"), Icons.Outlined.People, legacy.recipients.size),
        )),
        DrawerSection(lt("section_settings"), listOf(
            item("timing", lt("timing"), Icons.Outlined.Schedule),
            item("activity", lt("activity"), Icons.AutoMirrored.Outlined.ShowChart),
        )),
        guarding,
    )
}

/** "● Active" ao lado do título no Overview. */
@Composable
private fun StatusPill(vault: co.golink.tester.domain.legacy.LegacyVaultDto) {
    val paused = vault.paused_until != null
    val (label, color) = when {
        paused -> lt("status_paused") to Color(0xFF64748B)
        vault.status == "armed" -> lt("status_armed") to LegacyGreen
        vault.status in setOf("notified", "released") -> lt("status_${vault.status}") to LegacyAmber
        vault.status == "opened" -> lt("status_opened") to MaterialTheme.colorScheme.error
        else -> lt("status_${vault.status}") to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
private fun CenterCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String?,
    text: String,
    action: (@Composable () -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        LegacyCard(modifier = Modifier.widthIn(max = 480.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                if (title != null) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 12.dp))
                }
                Text(text, fontSize = 13.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                if (action != null) {
                    Spacer(Modifier.height(16.dp))
                    action()
                }
            }
        }
    }
}

/** Primeira visita: ainda sem cofre. */
@Composable
private fun Welcome(creating: Boolean, onCreate: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        LegacyCard {
            Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text(lt("welcome_title"), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 12.dp))
            Text(lt("welcome_text"), fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { n ->
                    Row {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(lt("welcome_point_$n"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Button(onClick = onCreate, enabled = !creating, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 20.dp)) {
                Text(lt("welcome_start"))
            }
        }
    }
}

