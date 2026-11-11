package co.golink.tester.ui.screens.pass

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import co.golink.tester.data.pass.PassBiometricStore
import co.golink.tester.ui.components.dialogs.ConfirmDialog
import co.golink.tester.ui.components.drawer.AdaptiveNavigationDrawer
import co.golink.tester.ui.components.drawer.AppDrawerContent
import co.golink.tester.ui.components.drawer.DrawerArea
import co.golink.tester.ui.components.drawer.DrawerItem
import co.golink.tester.ui.components.drawer.DrawerSection
import co.golink.tester.ui.components.drawer.isPermanentDrawer
import co.golink.tester.ui.i18n.pt
import co.golink.tester.ui.i18n.tr
import co.golink.tester.ui.screens.browse.BrowseShellViewModel
import co.golink.tester.ui.screens.encryption.E2EGateViewModel
import javax.crypto.Cipher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pré-preenchimento de um item novo (gerador, QR de 2FA…). */
data class PassPrefill(
    val type: String = "login",
    val name: String = "",
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val totp: String = "",
)

/** Pedir de novo (biometria/passphrase) antes de mostrar/copiar um segredo, se `reprompt`. */
val LocalPassGuard = staticCompositionLocalOf<(() -> Unit) -> Unit> { { it() } }

private val ROOT_PANES = setOf("vault", "totp", "generator", "health")

/** Páginas que se podem abrir diretamente por `pass?pane=` (App Link /pass/settings, /pass/trash). */
private val LINK_PAGES = setOf("settings", "trash")

fun FragmentActivity.passBiometricPrompt(cipher: Cipher, title: String, onOk: (Cipher) -> Unit, onFail: () -> Unit = {}) {
    BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                result.cryptoObject?.cipher?.let(onOk) ?: onFail()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail()
        },
    ).authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(pt("cancel"))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build(),
        BiometricPrompt.CryptoObject(cipher),
    )
}

/**
 * Confirmação biométrica (reprompt) presa à chave do Keystore do Pass (CryptoObject): só passa
 * se a K abrir, por isso uma impressão inscrita depois não serve. Sem biometria ligada ou com a
 * chave invalidada → [onFail] (aí `bio.consumeInvalidated()` diz se mudou) e fica a passphrase.
 */
fun FragmentActivity.passBiometricConfirm(bio: PassBiometricStore, title: String, onOk: () -> Unit, onFail: () -> Unit) {
    val cipher = bio.decryptCipher() ?: return onFail()
    passBiometricPrompt(cipher, title, onOk = { c ->
        val key = bio.open(c)
        if (key != null) { key.fill(0); onOk() } else onFail()
    }, onFail = onFail)
}

@Composable
fun PassScreen(
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
    vm: PassViewModel = hiltViewModel(),
    shell: BrowseShellViewModel = hiltViewModel(),
    gateVm: E2EGateViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val blockScreens by vm.blockScreens.collectAsStateWithLifecycle()
    val bioEnabled by vm.bioEnabled.collectAsStateWithLifecycle()
    val user by shell.user.collectAsStateWithLifecycle()
    val unread by shell.unreadNotifications.collectAsStateWithLifecycle()
    val storage by shell.storage.collectAsStateWithLifecycle()
    val legacyEnabled by shell.legacyEnabled.collectAsStateWithLifecycle()
    val e2eUnlocked by gateVm.unlocked.collectAsStateWithLifecycle()
    val e2eConfigured by gateVm.configuredFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var pane by rememberSaveable { mutableStateOf(initialPane.takeIf { it in ROOT_PANES } ?: "vault") }
    var filter by rememberSaveable { mutableStateOf("all") }
    // Páginas por cima do separador: "item:<id>", "edit:<id>", "new", "settings", "trash".
    var stackText by rememberSaveable { mutableStateOf(initialPane.takeIf { it in LINK_PAGES }.orEmpty()) }
    val stack = stackText.split('|').filter { it.isNotEmpty() }
    fun push(page: String) { stackText = (stack + page).joinToString("|") }
    fun pop() { stackText = stack.dropLast(1).joinToString("|") }
    fun replaceTop(page: String) { stackText = (stack.dropLast(1) + page).joinToString("|") }
    var prefill by remember { mutableStateOf<PassPrefill?>(null) }
    val top = stack.lastOrNull()

    // Sem rememberSaveable: ao sair por navegação a gaveta ainda está "aberta" (o fecho é cancelado)
    // e voltava aberta por cima do conteúdo. Abrir de propósito vai por openDrawerRequest.
    val drawerState = androidx.compose.runtime.remember { androidx.compose.material3.DrawerState(DrawerValue.Closed) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }
    var reprompt by remember { mutableStateOf<(() -> Unit)?>(null) }

    val palette = passPalette()

    // Ecrã seguro enquanto o Pass está visível (pref local "Block screenshots").
    // A MainActivity decide o FLAG_SECURE (modo privado + ecrãs seguros): não mexer na janela aqui.
    DisposableEffect(blockScreens) {
        if (blockScreens) co.golink.tester.data.settings.SecureScreens.acquire()
        onDispose {
            if (blockScreens) co.golink.tester.data.settings.SecureScreens.release()
        }
    }

    val bioAvailable = remember(context) {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val lockedView = state.locked || (state.needsKeys && e2eConfigured)

    // Sem chaves o load marca needsKeys (biometria ou E2EGate); ao abrir as chaves volta a tentar.
    LaunchedEffect(e2eUnlocked, state.locked) { if (!state.locked) vm.load() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.checkIdle() }
    // Sync de 5 s enquanto o Pass está visível (como o listSync da web).
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state.loaded, state.locked) {
        if (!state.loaded || state.locked) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                vm.sync()
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
    // Item aberto foi apagado de vez (noutro aparelho): fecha a página.
    LaunchedEffect(top, state.items) {
        val id = top?.substringAfter(':', "")?.takeIf { top.startsWith("item:") || top.startsWith("edit:") } ?: return@LaunchedEffect
        if (state.loaded && state.item(id) == null) pop()
    }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = !drawerState.isOpen && stack.isNotEmpty()) { pop() }
    BackHandler(enabled = !drawerState.isOpen && stack.isEmpty() && pane != "vault") { pane = "vault" }

    val guard: (() -> Unit) -> Unit = { action ->
        if (vm.needsReprompt()) reprompt = action else action()
    }

    val secondaryArea = when {
        user?.role == "admin" -> DrawerArea.Admin
        user?.supportStaff != null -> DrawerArea.Support
        else -> null
    }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }
    val go: (() -> Unit) -> () -> Unit = { action -> { closeDrawer(); action() } }
    val counts = remember(state.items) {
        val a = state.active
        mapOf(
            "all" to a.size, "login" to a.count { it.type == "login" }, "card" to a.count { it.type == "card" },
            "note" to a.count { it.type == "note" }, "favourite" to a.count { it.favourite }, "trash" to state.trash.size,
        )
    }
    val openFilter: (String) -> Unit = { f -> filter = f; pane = "vault"; stackText = "" }
    val sections = if (lockedView || !state.loaded) emptyList() else listOf(
        DrawerSection(pt("section_vault"), listOf(
            DrawerItem(pt("all"), Icons.Outlined.Key, selected = stack.isEmpty() && pane == "vault" && filter == "all", count = counts["all"], onClick = go { openFilter("all") }),
            DrawerItem(pt("logins"), Icons.Outlined.Lock, selected = stack.isEmpty() && pane == "vault" && filter == "login", count = counts["login"], onClick = go { openFilter("login") }),
            DrawerItem(pt("cards"), Icons.Outlined.FolderOpen, selected = stack.isEmpty() && pane == "vault" && filter == "card", count = counts["card"], onClick = go { openFilter("card") }),
            DrawerItem(pt("notes"), Icons.Outlined.FolderOpen, selected = stack.isEmpty() && pane == "vault" && filter == "note", count = counts["note"], onClick = go { openFilter("note") }),
            DrawerItem(pt("favourites"), Icons.Outlined.Star, selected = stack.isEmpty() && pane == "vault" && filter == "favourite", count = counts["favourite"], onClick = go { openFilter("favourite") }),
            DrawerItem(pt("trash"), Icons.Outlined.Delete, selected = top == "trash", count = counts["trash"]?.takeIf { it > 0 }, onClick = go { stackText = "trash" }),
        )),
        DrawerSection(pt("section_tools"), listOf(
            DrawerItem(pt("totp"), Icons.Outlined.Timer, selected = stack.isEmpty() && pane == "totp", onClick = go { pane = "totp"; stackText = "" }),
            DrawerItem(pt("health"), Icons.Outlined.VerifiedUser, selected = stack.isEmpty() && pane == "health", badge = state.health?.flagged?.takeIf { it > 0 }, onClick = go { pane = "health"; stackText = "" }),
            DrawerItem(pt("generator"), Icons.Outlined.Autorenew, selected = stack.isEmpty() && pane == "generator", onClick = go { pane = "generator"; stackText = "" }),
            DrawerItem(pt("import_settings"), Icons.Outlined.Settings, selected = top == "settings", onClick = go { stackText = "settings" }),
        )),
    )

    CompositionLocalProvider(LocalPassPalette provides palette, LocalPassGuard provides guard) {
        AdaptiveNavigationDrawer(
            permanent = isPermanentDrawer(),
            drawerState = drawerState,
            drawerContent = {
                AppDrawerContent(
                    user = user,
                    area = DrawerArea.Pass,
                    onAreaChange = { area ->
                        when (area) {
                            DrawerArea.Pass -> Unit
                            DrawerArea.Drive -> go(onOpenDrive)()
                            DrawerArea.Calendar -> go(onOpenCalendar)()
                            else -> go { onOpenArea(area) }()
                        }
                    },
                    areas = listOfNotNull(DrawerArea.Drive, DrawerArea.Calendar, DrawerArea.Notes, DrawerArea.Pass, DrawerArea.Legacy.takeIf { legacyEnabled }, secondaryArea),
                    sections = sections,
                    unreadNotifications = unread,
                    onOpenNotifications = go(onOpenNotifications),
                    onHome = go { openFilter("all") },
                    storage = storage,
                    onUpgrade = go(onOpenBilling),
                    appVersion = co.golink.tester.BuildConfig.VERSION_NAME,
                    onProfile = go(onOpenProfile),
                    onAvatarClick = null,
                    onHelp = go { onOpenHelp(user?.supportStaff != null) },
                    onSettings = go(onOpenSettings),
                    onLogout = go { confirmLogout = true },
                    onNewPassItem = if (state.loaded && !lockedView) go { prefill = null; stackText = "new" } else null,
                )
            },
        ) {
            val showTabs = state.loaded && !lockedView && stack.isEmpty()
            Scaffold(
                // As barras tratam os insets (status/navegação/teclado) elas mesmas.
                contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                containerColor = palette.bg,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (showTabs) {
                        PassTabBar(
                            tabs = listOf(
                                Triple("vault", Icons.Outlined.Lock, pt("section_vault")),
                                Triple("totp", Icons.Outlined.Timer, pt("tab_2fa")),
                                Triple("generator", Icons.Outlined.Autorenew, pt("tab_generator")),
                                Triple("health", Icons.Outlined.GppMaybe, pt("tab_health")),
                            ),
                            selected = pane,
                            badges = mapOf("health" to (state.health?.flagged ?: 0)),
                            onSelect = { pane = it },
                        )
                    }
                },
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(palette.bg)
                        .padding(padding)
                        // Qualquer toque conta como atividade (auto-lock).
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                    vm.touch()
                                }
                            }
                        },
                ) {
                    val openMenu: (() -> Unit)? = if (isPermanentDrawer()) null else ({ scope.launch { drawerState.open() } })
                    when {
                        !e2eConfigured && !e2eUnlocked && !vm.hasKey() && !bioEnabled -> PassCenterCard(Icons.Outlined.Lock, pt("no_e2e_title"), pt("no_e2e_text"), openMenu)
                        lockedView -> PassLockView(
                            vm = vm,
                            bioEnabled = bioEnabled && bioAvailable,
                            e2eUnlocked = e2eUnlocked,
                            openMenu = openMenu,
                            onOpenGate = gateVm::reopen,
                        )
                        state.needsKeys -> PassCenterCard(Icons.Outlined.Lock, pt("e2e_locked_title"), pt("e2e_locked_text"), openMenu) {
                            Button(onClick = gateVm::reopen, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = PassBlue)) { Text(pt("unlock")) }
                        }
                        state.failed && !state.loaded -> PassCenterCard(Icons.Outlined.ErrorOutline, null, pt("load_failed"), openMenu) {
                            OutlinedButton(onClick = { vm.retry() }, shape = RoundedCornerShape(12.dp)) { Text(pt("retry")) }
                        }
                        !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp, color = PassBlue) }
                        top != null && top.startsWith("item:") -> PassItemDetail(
                            vm = vm,
                            state = state,
                            id = top.removePrefix("item:"),
                            onBack = ::pop,
                            onEdit = { push("edit:${top.removePrefix("item:")}") },
                        )
                        top != null && (top.startsWith("edit:") || top == "new") -> PassItemForm(
                            vm = vm,
                            state = state,
                            id = top.removePrefix("edit:").takeIf { top.startsWith("edit:") },
                            prefill = prefill,
                            onClose = ::pop,
                            onSaved = { saved -> if (top == "new") { prefill = null; replaceTop("item:${saved.id}") } else pop() },
                        )
                        top == "settings" -> PassSettingsPane(vm = vm, state = state, bioAvailable = bioAvailable, onBack = ::pop)
                        top == "trash" -> PassTrashPane(vm = vm, state = state, onBack = ::pop, onOpen = { push("item:$it") })
                        else -> Column(Modifier.fillMaxSize()) {
                            PassTopBar(
                                title = when (pane) {
                                    "totp" -> pt("totp")
                                    "generator" -> pt("generator")
                                    "health" -> pt("tab_health")
                                    else -> pt("title")
                                },
                                onMenu = openMenu,
                                onLock = vm::lock,
                                onSettings = { push("settings") },
                            )
                            when (pane) {
                                "totp" -> PassTotpPane(vm, state, onNewWithKey = { p -> prefill = p; push("new") }, onOpenItem = { push("item:$it") })
                                "generator" -> PassGeneratorPane(vm, onSaveAsLogin = { pw -> prefill = PassPrefill(password = pw); push("new") })
                                "health" -> PassHealthPane(vm, state, onEdit = { push("edit:$it") }, onOpen = { push("item:$it") })
                                else -> PassVaultPane(
                                    vm = vm,
                                    state = state,
                                    filter = filter,
                                    onFilter = { filter = it },
                                    onOpen = { push("item:$it") },
                                    onNew = { prefill = null; push("new") },
                                    onOpenHealth = { pane = "health" },
                                )
                            }
                        }
                    }
                }
            }
        }

        reprompt?.let { action ->
            PassRepromptDialog(
                vm = vm,
                // O reprompt biométrico usa a chave do desbloqueio biométrico (CryptoObject).
                bioAvailable = bioAvailable && vm.bio.enabled,
                onDismiss = { reprompt = null },
                onOk = { vm.markReprompted(); reprompt = null; action() },
            )
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

/** Barra de topo dos separadores: ☰ Título · 🔒 · ⚙ (como no mockup). */
@Composable
fun PassTopBar(title: String, onMenu: (() -> Unit)?, onLock: (() -> Unit)? = null, onSettings: (() -> Unit)? = null) {
    val p = LocalPassPalette.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onMenu != null) IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, pt("menu"), tint = p.text) } else Spacer(Modifier.size(12.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = p.text, modifier = Modifier.weight(1f).padding(start = 4.dp))
        if (onLock != null) IconButton(onClick = onLock) { Icon(Icons.Outlined.Lock, pt("lock_now"), tint = p.text) }
        if (onSettings != null) IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, pt("settings_title"), tint = p.text) }
    }
}

/** Barra de topo das páginas internas: ← Título [ações]. */
@Composable
fun PassBackBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    val p = LocalPassPalette.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, pt("back"), tint = p.text) }
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = p.text, modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1)
        actions()
    }
}

@Composable
fun PassCenterCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String?,
    text: String,
    onMenu: (() -> Unit)?,
    action: (@Composable () -> Unit)? = null,
) {
    val p = LocalPassPalette.current
    Column(Modifier.fillMaxSize()) {
        PassTopBar(pt("title"), onMenu)
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            PassCard(Modifier.widthIn(max = 480.dp), padding = 24.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, null, tint = PassBlue, modifier = Modifier.size(28.dp))
                    if (title != null) Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = p.text, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                    Text(text, fontSize = 13.sp, color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    if (action != null) { Spacer(Modifier.height(16.dp)); action() }
                }
            }
        }
    }
}

/** Cofre trancado: biometria (se ligada) ou passphrase E2E. */
@Composable
private fun PassLockView(vm: PassViewModel, bioEnabled: Boolean, e2eUnlocked: Boolean, openMenu: (() -> Unit)?, onOpenGate: () -> Unit) {
    val p = LocalPassPalette.current
    val activity = LocalContext.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    var secret by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val tryBiometric: () -> Unit = {
        val cipher = vm.bio.decryptCipher()
        if (cipher == null) {
            if (vm.bio.consumeInvalidated()) error = pt("biometric_changed")
        } else {
            activity?.passBiometricPrompt(cipher, pt("biometric_prompt"), onOk = { c ->
                busy = true
                vm.unlockWithBiometric(c) { busy = false; error = pt("unlock_wrong") }
            })
        }
    }
    LaunchedEffect(bioEnabled) { if (bioEnabled) tryBiometric() }

    val submit: () -> Unit = {
        if (secret.isNotBlank() && !busy) {
            busy = true
            error = null
            scope.launch {
                val ok = vm.unlockWithSecret(secret)
                busy = false
                if (ok) secret = "" else error = pt("unlock_wrong")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PassTopBar(pt("title"), openMenu)
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            PassCard(Modifier.widthIn(max = 460.dp), padding = 24.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp).background(p.accentSoft, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Lock, null, tint = PassBlue, modifier = Modifier.size(28.dp))
                    }
                    Text(pt("locked_title"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = p.text, modifier = Modifier.padding(top = 14.dp))
                    Text(pt("locked_text"), fontSize = 13.sp, color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(16.dp))
                    if (bioEnabled) {
                        Button(
                            onClick = tryBiometric,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PassBlue),
                        ) {
                            Icon(Icons.Outlined.Fingerprint, null)
                            Spacer(Modifier.size(8.dp))
                            Text(pt("use_biometric"), fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it; error = null },
                        placeholder = { Text(pt("unlock_placeholder")) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        isError = error != null,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (error != null) Text(error!!, color = PassRose, fontSize = 12.5.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = submit,
                        enabled = secret.isNotBlank() && !busy,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (bioEnabled) p.text else PassBlue),
                    ) { Text(if (busy) pt("unlocking") else pt("unlock"), fontWeight = FontWeight.SemiBold) }
                    if (!e2eUnlocked && !bioEnabled) {
                        TextButton(onClick = onOpenGate) { Text(pt("e2e_locked_title"), color = PassBlue) }
                    }
                }
            }
        }
    }
}

/** "Confirm it's you" antes de mostrar/copiar (reprompt). */
@Composable
private fun PassRepromptDialog(vm: PassViewModel, bioAvailable: Boolean, onDismiss: () -> Unit, onOk: () -> Unit) {
    val activity = LocalContext.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    var secret by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (bioAvailable && activity != null) activity.passBiometricConfirm(vm.bio, pt("reprompt_title"), onOk = onOk, onFail = {})
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pt("reprompt_title")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(pt("reprompt_text"), fontSize = 14.sp)
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it; error = false },
                    singleLine = true,
                    isError = error,
                    placeholder = { Text(pt("unlock_placeholder")) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error) Text(pt("unlock_wrong"), color = PassRose, fontSize = 12.sp)
                if (bioAvailable && activity != null) {
                    TextButton(onClick = { activity.passBiometricConfirm(vm.bio, pt("reprompt_title"), onOk = onOk, onFail = {}) }) {
                        Icon(Icons.Outlined.Fingerprint, null, tint = PassBlue)
                        Spacer(Modifier.size(6.dp))
                        Text(pt("use_biometric"), color = PassBlue)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = secret.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    val ok = vm.verifySecret(secret)
                    busy = false
                    if (ok) onOk() else error = true
                }
            }) { Text(pt("confirm")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(pt("cancel")) } },
    )
}
