package co.golink.tester.ui.navigation

import co.golink.tester.ui.i18n.tr
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.blur
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import co.golink.tester.data.AppLogger
import co.golink.tester.data.auth.AuthState
import co.golink.tester.data.auth.SessionManager
import co.golink.tester.data.settings.AppLockManager
import co.golink.tester.ui.screens.browse.BrowseScreen
import co.golink.tester.ui.screens.backup.AutoBackupScreen
import co.golink.tester.ui.screens.lock.LockScreen
import androidx.navigation.compose.currentBackStackEntryAsState
import co.golink.tester.ui.screens.error.BootstrapErrorScreen
import co.golink.tester.ui.screens.viewer.FileViewerScreen
import co.golink.tester.ui.screens.forgot.ForgotPasswordScreen
import co.golink.tester.ui.screens.notifications.NotificationsScreen
import co.golink.tester.ui.screens.security.SecurityActivityScreen
import co.golink.tester.ui.screens.otp.OtpScreen
import co.golink.tester.ui.screens.register.RegisterScreen
import co.golink.tester.ui.screens.resetpassword.CreateNewPasswordScreen
import co.golink.tester.ui.screens.settings.SettingsScreen
import co.golink.tester.ui.screens.signin.SignInScreen
import co.golink.tester.ui.screens.socialite.SocialiteWebViewScreen
import co.golink.tester.ui.screens.landing.LandingScreen
import co.golink.tester.ui.screens.splash.SplashScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

object Routes {
    const val SPLASH = "splash"
    const val LANDING = "landing"
    const val SIGN_IN = "sign_in"
    const val REGISTER = "register"
    const val FORGOT = "forgot"
    const val CREATE_NEW_PASSWORD = "create_new_password?token={token}"
    fun createNewPassword(token: String? = null) =
        if (token == null) "create_new_password" else "create_new_password?token=$token"
    const val REGISTER_INVITED = "register_invited/{invitation}"
    fun registerInvited(invitation: String) = "register_invited/$invitation"
    const val OTP = "otp"
    const val HOME = "home"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings?initial={initial}"
    fun settings(initial: String? = null) = if (initial == null) "settings" else "settings?initial=$initial"
    const val SECURITY_ACTIVITY = "security_activity"
    const val AUTO_BACKUP = "auto_backup"
    const val MOBILE_BACKUPS = "mobile_backups"
    const val BOOTSTRAP_ERROR = "bootstrap_error"
    const val SOCIALITE = "socialite/{provider}"
    fun socialite(provider: String) = "socialite/$provider"
    const val VIEWER = "viewer/{fileId}"
    fun viewer(id: String) = "viewer/$id"
    const val SUPPORT = "support?admin={admin}&status={status}&assigned={assigned}&priority={priority}"
    fun support(admin: Boolean = false, status: String? = null, assigned: String? = null, priority: String? = null) =
        "support?admin=$admin&status=${status.orEmpty()}&assigned=${assigned.orEmpty()}&priority=${priority.orEmpty()}"
    const val SUPPORT_DASHBOARD = "support_dashboard"
    const val SUPPORT_TICKET = "support_ticket/{id}?admin={admin}"
    fun supportTicket(id: String, admin: Boolean = false) = "support_ticket/$id?admin=$admin"
    const val SUPPORT_NEW = "support_new"
    const val ADMIN = "admin/{pane}"
    fun admin(pane: String) = "admin/$pane"
    const val ADMIN_USER = "admin_user/{id}"
    fun adminUser(id: String) = "admin_user/$id"
    const val CALENDAR = "calendar?event={event}&date={date}"
    fun calendar(event: String? = null, date: String? = null) = "calendar?event=${event.orEmpty()}&date=${date.orEmpty()}"
    const val LEGACY = "legacy?pane={pane}"
    fun legacy(pane: String = "overview") = "legacy?pane=$pane"
    // Páginas públicas do Legacy (App Links, sem sessão). "legacy_" ≠ "legacy?".
    const val LEGACY_OPEN = "legacy_open/{token}"
    const val LEGACY_INVITE = "legacy_invite/{id}"
    const val LEGACY_CHECKIN = "legacy_checkin/{token}"
    // Links dos emails (AppLinkRouter): abrem com ou sem sessão.
    const val VERIFY_EMAIL = "verify_email?url={url}"
    fun verifyEmail(url: String) = "verify_email?url=${android.net.Uri.encode(url)}"
    const val TEAM_INVITATION = "team_invitation/{id}"
    fun teamInvitation(id: String) = "team_invitation/$id"
}

enum class TopState { Loading, NeedsAuth, NeedsOtp, Authenticated, BootstrapFailed }

@HiltViewModel
class RootGateViewModel @Inject constructor(
    sessionManager: SessionManager,
    lockManager: AppLockManager,
    val logger: AppLogger,
) : ViewModel() {
    val state: StateFlow<TopState> = sessionManager.state
        .map { auth ->
            when (auth) {
                is AuthState.Loading -> TopState.Loading
                is AuthState.Unauthenticated -> TopState.NeedsAuth
                is AuthState.OtpRequired -> TopState.NeedsOtp
                is AuthState.Authenticated -> TopState.Authenticated
                is AuthState.BootstrapFailed -> TopState.BootstrapFailed
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TopState.Loading)

    val locked: StateFlow<Boolean> = lockManager.locked
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    gate: RootGateViewModel = hiltViewModel(),
) {
    val top by gate.state.collectAsState()
    val locked by gate.locked.collectAsState()
    val currentEntry by navController.currentBackStackEntryAsState()
    var splashElapsed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!splashElapsed) {
            delay(1_500)
            splashElapsed = true
        }
    }

    LaunchedEffect(currentEntry?.destination?.route) {
        currentEntry?.destination?.route?.let { route ->
            gate.logger.log("Nav", "Screen[$route]")
        }
    }

    fun gateTarget(): String? = when (top) {
        TopState.NeedsAuth -> Routes.LANDING
        TopState.NeedsOtp -> Routes.OTP
        TopState.Authenticated -> Routes.HOME
        TopState.BootstrapFailed -> Routes.BOOTSTRAP_ERROR
        TopState.Loading -> null
    }

    // Voltar sem nada útil atrás (raiz da pilha ou splash de um deep link):
    // recomeça no destino do gate em vez de esvaziar o NavHost ou ficar no splash.
    fun backOrRestart() {
        val previous = navController.previousBackStackEntry?.destination?.route
        if (previous != null && previous != Routes.SPLASH) {
            navController.popBackStack()
            return
        }
        navController.navigate(gateTarget() ?: Routes.LANDING) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    // Login "limpo" (após repor a password ou registo por convite): Landing → Login.
    fun freshSignIn(registered: Boolean? = null) {
        navController.navigate(Routes.LANDING) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
        navController.navigate(Routes.SIGN_IN)
        if (registered != null) {
            navController.currentBackStackEntry?.savedStateHandle?.set("registered", registered)
        }
    }

    LaunchedEffect(top, splashElapsed) {
        // Com sessão não há nada a esperar: o splash mínimo é só para quem vai ao Landing.
        if (!splashElapsed && top != TopState.Authenticated) return@LaunchedEffect
        val target = gateTarget() ?: return@LaunchedEffect
        val current = navController.currentDestination?.route
        val inAuthStack = current != null && (
            current == Routes.LANDING ||
                current == Routes.SIGN_IN ||
                current == Routes.REGISTER ||
                current == Routes.FORGOT ||
                current.startsWith("create_new_password") ||
                current.startsWith("register_invited") ||
                current.startsWith("socialite/")
        )
        val inAuthenticated = current != null && (
            current == Routes.HOME ||
                current.startsWith("settings") ||
                current.startsWith("admin") ||
                current.startsWith("calendar") ||
                current.startsWith("legacy") ||
                current == Routes.NOTIFICATIONS ||
                current.startsWith("viewer/")
        )
        val shouldNavigate = when {
            current == null -> true
            current == target -> false
            // Páginas públicas do Legacy: abrem com ou sem sessão, o gate não as tira.
            current.startsWith("legacy_") -> false
            // Links dos emails (confirmar email, convite de equipa): idem.
            current.startsWith("verify_email") || current.startsWith("team_invitation") -> false
            target == Routes.HOME && inAuthenticated -> false
            target == Routes.LANDING && inAuthStack -> false
            else -> true
        }
        if (shouldNavigate) {
            navController.navigate(target) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Lembrete do calendário tocado: abre o evento quando já estamos dentro da app.
    val calendarLink by CalendarDeepLink.pending.collectAsState()
    val currentRoute = currentEntry?.destination?.route
    LaunchedEffect(calendarLink, top, currentRoute) {
        val link = calendarLink ?: return@LaunchedEffect
        if (top != TopState.Authenticated) return@LaunchedEffect
        val route = currentRoute ?: return@LaunchedEffect
        if (route != Routes.HOME && !route.startsWith("calendar") && route != Routes.NOTIFICATIONS && !route.startsWith("settings")) return@LaunchedEffect
        CalendarDeepLink.consume()
        // Nova entrada (os argumentos mudam); a anterior do calendário sai.
        navController.navigate(Routes.calendar(link.event, link.date)) { popUpTo(Routes.HOME) }
    }

    // Notificação do contador tocada: abre o Inbox nas mesmas condições.
    val inboxLink by InboxDeepLink.pending.collectAsState()
    LaunchedEffect(inboxLink, top, currentRoute) {
        if (!inboxLink || top != TopState.Authenticated) return@LaunchedEffect
        val route = currentRoute ?: return@LaunchedEffect
        if (route != Routes.HOME && !route.startsWith("calendar") && route != Routes.NOTIFICATIONS && !route.startsWith("settings")) return@LaunchedEffect
        InboxDeepLink.consume()
        if (route != Routes.NOTIFICATIONS) navController.navigate(Routes.NOTIFICATIONS)
    }

    // Pasta vinda de um link (email / convite aceite): volta à Home e pede-lhe a pasta.
    fun openFolderFromLink(kind: String, id: String?) {
        if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
            navController.navigate(Routes.HOME) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
        runCatching { navController.getBackStackEntry(Routes.HOME).savedStateHandle["open_folder"] = "$kind:${id.orEmpty()}" }
    }

    // Link de um email (App Link): abre o destino. Confirmar email e convites não
    // precisam de sessão; o resto espera pelo login (e fica por cima da Home).
    val appLink by AppLinkRouter.pending.collectAsState()
    LaunchedEffect(appLink, top, currentRoute) {
        val link = appLink ?: return@LaunchedEffect
        val route = currentRoute ?: return@LaunchedEffect
        // Ainda a decidir a sessão: o gate ia limpar a pilha a seguir.
        if (top == TopState.Loading || route == Routes.SPLASH) return@LaunchedEffect
        val authed = top == TopState.Authenticated
        val needsAuth = when (link) {
            is AppLinkRouter.Target.Nav, is AppLinkRouter.Target.VerifyEmail -> false
            is AppLinkRouter.Target.TeamInvite -> link.requireAuth
            else -> true
        }
        val inApp = route == Routes.HOME || route == Routes.NOTIFICATIONS || route.startsWith("settings") ||
            route.startsWith("calendar") || route.startsWith("support") || route.startsWith("legacy") ||
            route.startsWith("viewer/") || route.startsWith("team_invitation") || route.startsWith("verify_email")
        if (needsAuth && (!authed || !inApp)) return@LaunchedEffect
        val target = AppLinkRouter.consume() ?: return@LaunchedEffect
        fun onTopOfHome(dest: String) = navController.navigate(dest) { popUpTo(Routes.HOME) }
        when (target) {
            is AppLinkRouter.Target.Nav -> runCatching { navController.navigate(target.uri) }
            is AppLinkRouter.Target.VerifyEmail -> navController.navigate(Routes.verifyEmail(target.url))
            is AppLinkRouter.Target.TeamInvite -> navController.navigate(Routes.teamInvitation(target.id))
            is AppLinkRouter.Target.Folder -> openFolderFromLink(target.kind, target.id)
            is AppLinkRouter.Target.Calendar -> onTopOfHome(Routes.calendar(target.event))
            is AppLinkRouter.Target.Ticket -> onTopOfHome(Routes.supportTicket(target.id, target.admin))
            AppLinkRouter.Target.NewTicket -> onTopOfHome(Routes.SUPPORT_NEW)
            AppLinkRouter.Target.Billing -> onTopOfHome(Routes.settings("billing"))
            is AppLinkRouter.Target.Legacy -> onTopOfHome(Routes.legacy(target.pane))
        }
    }

    // Convite de guardião aberto antes do login: Guarding assim que houver sessão.
    val legacyGuardingLink by LegacyDeepLink.guarding.collectAsState()
    LaunchedEffect(legacyGuardingLink, top, currentRoute) {
        if (!legacyGuardingLink || top != TopState.Authenticated || currentRoute != Routes.HOME) return@LaunchedEffect
        if (LegacyDeepLink.consume()) navController.navigate(Routes.legacy("guarding"))
    }

    fun openGuarding() {
        navController.navigate(Routes.HOME) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
        navController.navigate(Routes.legacy("guarding"))
    }

    fun legacyLinks(path: String) = listOf(
        navDeepLink { uriPattern = "https://golink.co/$path" },
        navDeepLink { uriPattern = "https://tester.golink.co/$path" },
    )

    // Bloqueada: o conteúdo fica desfocado por trás da folha do PIN (Android 12+;
    // antes disso o LockScreen tapa-o com um fundo opaco).
    val lockedNow = locked && top == TopState.Authenticated
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier
            .fillMaxSize()
            .then(if (lockedNow) androidx.compose.ui.Modifier.blur(28.dp) else androidx.compose.ui.Modifier),
    ) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) { SplashScreen() }
        composable(Routes.LANDING) {
            LandingScreen(
                onSignIn = { navController.navigate(Routes.SIGN_IN) },
                onRegister = { navController.navigate(Routes.REGISTER) },
            )
        }
        composable(Routes.SIGN_IN) { entry ->
            // Vindo do registo: true = falta confirmar o email, false = já pode entrar.
            val registered by entry.savedStateHandle.getStateFlow<Boolean?>("registered", null).collectAsState()
            SignInScreen(
                notice = when (registered) {
                    true -> "Conta criada. Confirma o teu email através do link que te enviámos e depois entra.".tr()
                    false -> "Conta criada. Já podes entrar.".tr()
                    null -> null
                },
                onLoginSucceeded = { /* gate handles transition */ },
                onRegister = { navController.navigate(Routes.REGISTER) },
                onForgotPassword = { navController.navigate(Routes.FORGOT) },
                onSocialite = { provider -> navController.navigate(Routes.socialite(provider)) },
                onBack = { backOrRestart() },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegistered = { requiresVerification ->
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(Routes.LANDING)
                        launchSingleTop = true
                    }
                    navController.currentBackStackEntry?.savedStateHandle?.set("registered", requiresVerification)
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.LEGACY_OPEN,
            arguments = listOf(navArgument("token") { type = NavType.StringType }),
            deepLinks = legacyLinks("legacy-open/{token}"),
        ) { entry ->
            androidx.activity.compose.BackHandler { backOrRestart() }
            co.golink.tester.ui.screens.legacy.LegacyOpenScreen(
                token = entry.arguments?.getString("token").orEmpty(),
                onBack = { backOrRestart() },
            )
        }
        composable(
            route = Routes.LEGACY_INVITE,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
            deepLinks = legacyLinks("legacy-invite/{id}"),
        ) { entry ->
            androidx.activity.compose.BackHandler { backOrRestart() }
            co.golink.tester.ui.screens.legacy.LegacyInviteScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                authenticated = top == TopState.Authenticated,
                onOpenGuarding = { openGuarding() },
                onSignIn = {
                    LegacyDeepLink.requestGuarding()
                    freshSignIn()
                },
                onSignUp = {
                    LegacyDeepLink.requestGuarding()
                    navController.navigate(Routes.REGISTER)
                },
                onBack = { backOrRestart() },
            )
        }
        composable(
            route = Routes.LEGACY_CHECKIN,
            arguments = listOf(navArgument("token") { type = NavType.StringType }),
            deepLinks = legacyLinks("legacy-checkin/{token}"),
        ) { entry ->
            androidx.activity.compose.BackHandler { backOrRestart() }
            co.golink.tester.ui.screens.legacy.LegacyCheckinScreen(
                token = entry.arguments?.getString("token").orEmpty(),
                onBack = { backOrRestart() },
            )
        }
        composable(Routes.FORGOT) {
            ForgotPasswordScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.CREATE_NEW_PASSWORD,
            arguments = listOf(navArgument("token") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://golink.co/create-new-password?token={token}" },
                navDeepLink { uriPattern = "https://tester.golink.co/create-new-password?token={token}" },
            ),
        ) { backStackEntry ->
            val token = backStackEntry.arguments?.getString("token").orEmpty()
            // Gesto de voltar num deep link: a entrada anterior é o splash.
            androidx.activity.compose.BackHandler { backOrRestart() }
            CreateNewPasswordScreen(
                token = token,
                onDone = { freshSignIn() },
                onBack = { backOrRestart() },
            )
        }
        composable(
            route = Routes.REGISTER_INVITED,
            arguments = listOf(navArgument("invitation") { type = NavType.StringType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://golink.co/register/{invitation}" },
                navDeepLink { uriPattern = "https://tester.golink.co/register/{invitation}" },
            ),
        ) { backStackEntry ->
            val invitation = backStackEntry.arguments?.getString("invitation").orEmpty()
            // Gesto de voltar num deep link: a entrada anterior é o splash.
            androidx.activity.compose.BackHandler { backOrRestart() }
            RegisterScreen(
                onRegistered = { requiresVerification -> freshSignIn(requiresVerification) },
                onBack = { backOrRestart() },
                invitationToken = invitation,
            )
        }
        composable(Routes.OTP) {
            OtpScreen(
                onValidated = { /* gate transitions to Home */ },
                onCancel = { /* OtpViewModel.cancel() faz logout; o gate volta à Landing */ },
            )
        }
        composable(
            route = Routes.SOCIALITE,
            arguments = listOf(navArgument("provider") { type = NavType.StringType }),
        ) { backStackEntry ->
            val provider = backStackEntry.arguments?.getString("provider") ?: "google"
            SocialiteWebViewScreen(
                provider = provider,
                onCompleted = { /* gate transitions */ },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Routes.HOME) { entry ->
            // Pedido vindo das Definições ("Libertar espaço"): abrir o Lixo.
            val openTrash by entry.savedStateHandle.getStateFlow("open_trash", false).collectAsState()
            // Vindo do Calendário (toggle Admin/Suporte): abre a gaveta nessa área.
            val areaRequest by entry.savedStateHandle.getStateFlow<String?>("drawer_area", null).collectAsState()
            val drawerRequest by entry.savedStateHandle.getStateFlow("open_drawer", false).collectAsState()
            // Link de um email: "kind:id" (files | team | shared).
            val folderLink by entry.savedStateHandle.getStateFlow<String?>("open_folder", null).collectAsState()
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                BrowseScreen(
                    openTrashRequest = openTrash,
                    onTrashRequestHandled = { entry.savedStateHandle["open_trash"] = false },
                    onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onOpenSettings = { navController.navigate(Routes.settings()) },
                    onOpenBilling = { navController.navigate(Routes.settings("billing")) },
                    onOpenAutoBackup = { navController.navigate(Routes.MOBILE_BACKUPS) },
                    onOpenFile = { fileId -> navController.navigate(Routes.viewer(fileId)) },
                    onOpenProfile = { navController.navigate(Routes.settings("profile")) },
                    onOpenSupport = { admin -> navController.navigate(Routes.support(admin)) },
                    onOpenSupportDashboard = { navController.navigate(Routes.SUPPORT_DASHBOARD) },
                    onOpenAdmin = { pane -> navController.navigate(Routes.admin(pane)) },
                    onOpenCalendar = { navController.navigate(Routes.calendar()) { launchSingleTop = true } },
                    onOpenLegacy = { navController.navigate(Routes.legacy()) { launchSingleTop = true } },
                    areaRequest = areaRequest?.let { name -> runCatching { co.golink.tester.ui.components.drawer.DrawerArea.valueOf(name) }.getOrNull() },
                    onAreaRequestHandled = { entry.savedStateHandle["drawer_area"] = null },
                    openDrawerRequest = drawerRequest,
                    onDrawerRequestHandled = { entry.savedStateHandle["open_drawer"] = false },
                    folderLinkRequest = folderLink,
                    onFolderLinkHandled = { entry.savedStateHandle["open_folder"] = null },
                )
                // Gate E2E: pede unlock/setup quando autenticado mas sem privada em memória.
                co.golink.tester.ui.screens.encryption.E2EGate()
            }
        }
        composable(
            route = Routes.VIEWER,
            arguments = listOf(navArgument("fileId") { type = NavType.StringType }),
        ) {
            FileViewerScreen(onClose = { navController.popBackStack() })
        }
        composable(
            route = Routes.ADMIN,
            arguments = listOf(navArgument("pane") { type = NavType.StringType }),
        ) { entry ->
            co.golink.tester.ui.screens.admin.AdminScreen(
                pane = entry.arguments?.getString("pane").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenUser = { id -> navController.navigate(Routes.adminUser(id)) },
            )
        }
        composable(
            route = Routes.ADMIN_USER,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) {
            co.golink.tester.ui.screens.admin.AdminUserScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.CALENDAR,
            arguments = listOf(
                navArgument("event") { type = NavType.StringType; defaultValue = "" },
                navArgument("date") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val backToHome: (String?) -> Unit = { area ->
                if (area != null) runCatching { navController.getBackStackEntry(Routes.HOME).savedStateHandle["drawer_area"] = area }
                if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                    navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                }
            }
            val drawerRequest by entry.savedStateHandle.getStateFlow("open_drawer", false).collectAsState()
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                co.golink.tester.ui.screens.calendar.CalendarTheme { co.golink.tester.ui.screens.calendar.CalendarScreen(
                    openEventId = entry.arguments?.getString("event")?.takeIf { it.isNotBlank() },
                    openDate = entry.arguments?.getString("date")?.takeIf { it.isNotBlank() },
                    onOpenDrive = { backToHome(null) },
                    onOpenArea = { area ->
                        if (area == co.golink.tester.ui.components.drawer.DrawerArea.Legacy) {
                            navController.navigate(Routes.legacy()) { popUpTo(Routes.HOME) }
                        } else {
                            backToHome(area.name)
                        }
                    },
                    onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onOpenSettings = { navController.navigate(Routes.settings()) },
                    onOpenProfile = { navController.navigate(Routes.settings("profile")) },
                    onOpenBilling = { navController.navigate(Routes.settings("billing")) },
                    onOpenHelp = { staff -> navController.navigate(if (staff) Routes.SUPPORT_DASHBOARD else Routes.support(false)) },
                    onOpenFile = { id -> navController.navigate(Routes.viewer(id)) },
                    openDrawerRequest = drawerRequest,
                    onDrawerRequestHandled = { entry.savedStateHandle["open_drawer"] = false },
                ) }
                co.golink.tester.ui.screens.encryption.E2EGate()
            }
        }
        composable(
            route = Routes.LEGACY,
            arguments = listOf(navArgument("pane") { type = NavType.StringType; defaultValue = "overview" }),
        ) { entry ->
            val backToHome: (String?) -> Unit = { area ->
                if (area != null) runCatching { navController.getBackStackEntry(Routes.HOME).savedStateHandle["drawer_area"] = area }
                if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                    navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                }
            }
            val drawerRequest by entry.savedStateHandle.getStateFlow("open_drawer", false).collectAsState()
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                co.golink.tester.ui.screens.legacy.LegacyScreen(
                    initialPane = entry.arguments?.getString("pane").orEmpty(),
                    onOpenDrive = { backToHome(null) },
                    onOpenCalendar = { navController.navigate(Routes.calendar()) { popUpTo(Routes.HOME) } },
                    onOpenArea = { area -> backToHome(area.name) },
                    onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onOpenSettings = { navController.navigate(Routes.settings()) },
                    onOpenProfile = { navController.navigate(Routes.settings("profile")) },
                    onOpenBilling = { navController.navigate(Routes.settings("billing")) },
                    onOpenHelp = { staff -> navController.navigate(if (staff) Routes.SUPPORT_DASHBOARD else Routes.support(false)) },
                    openDrawerRequest = drawerRequest,
                    onDrawerRequestHandled = { entry.savedStateHandle["open_drawer"] = false },
                )
                co.golink.tester.ui.screens.encryption.E2EGate()
            }
        }
        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                // A gaveta (com as secções da área) vive no Drive e no Calendário: volta
                // ao que estava por baixo; vindo das Definições, abre a do Drive.
                onOpenMenu = {
                    val previous = navController.previousBackStackEntry
                    val hasDrawer = previous?.destination?.route.let { it == Routes.HOME || it == Routes.CALENDAR || it == Routes.LEGACY }
                    val target = if (hasDrawer) previous else runCatching { navController.getBackStackEntry(Routes.HOME) }.getOrNull()
                    if (target != null) {
                        target.savedStateHandle["open_drawer"] = true
                        navController.popBackStack(target.destination.id, inclusive = false)
                    } else {
                        navController.popBackStack()
                    }
                },
                onOpenSecurityActivity = { navController.navigate(Routes.SECURITY_ACTIVITY) },
                onOpenSupportTicket = { id, admin -> navController.navigate(Routes.supportTicket(id, admin)) },
                onOpenCalendar = { event, date -> navController.navigate(Routes.calendar(event, date)) },
                onOpenLegacy = { pane -> navController.navigate(Routes.legacy(pane)) { popUpTo(Routes.HOME) } },
                onOpenSettings = { navController.navigate(Routes.settings()) },
            )
        }
        composable(Routes.SECURITY_ACTIVITY) {
            SecurityActivityScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.SETTINGS,
            arguments = listOf(navArgument("initial") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) { backStackEntry ->
            val initial = backStackEntry.arguments?.getString("initial")
            SettingsScreen(
                onBack = { navController.popBackStack() },
                initialRoute = initial,
                onOpenAutoBackup = { navController.navigate(Routes.MOBILE_BACKUPS) },
                onOpenSecurityActivity = { navController.navigate(Routes.SECURITY_ACTIVITY) },
                onOpenFile = { id -> navController.navigate(Routes.viewer(id)) },
                onOpenTrash = {
                    runCatching { navController.getBackStackEntry(Routes.HOME).savedStateHandle["open_trash"] = true }
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                // Staff abre o Support Desk no Dashboard (como na web); utilizador vai aos seus tickets.
                onOpenSupport = { admin ->
                    navController.navigate(if (admin) Routes.SUPPORT_DASHBOARD else Routes.support(false))
                },
            )
        }
        composable(
            route = Routes.SUPPORT,
            arguments = listOf(
                navArgument("admin") { type = NavType.BoolType; defaultValue = false },
                navArgument("status") { type = NavType.StringType; defaultValue = "" },
                navArgument("assigned") { type = NavType.StringType; defaultValue = "" },
                navArgument("priority") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val admin = entry.arguments?.getBoolean("admin") ?: false
            co.golink.tester.ui.screens.support.SupportListScreen(
                onBack = { navController.popBackStack() },
                onOpenTicket = { id -> navController.navigate(Routes.supportTicket(id, admin)) },
                onNewTicket = { navController.navigate(Routes.SUPPORT_NEW) },
                onOpenDashboard = {
                    // Veio do Dashboard: voltar; senão abrir.
                    if (!navController.popBackStack(Routes.SUPPORT_DASHBOARD, inclusive = false)) {
                        navController.navigate(Routes.SUPPORT_DASHBOARD)
                    }
                },
            )
        }
        composable(Routes.SUPPORT_DASHBOARD) {
            co.golink.tester.ui.screens.support.SupportDashboardScreen(
                onBack = { navController.popBackStack() },
                onOpenTicket = { id -> navController.navigate(Routes.supportTicket(id, true)) },
                onOpenList = { status, assigned, priority ->
                    navController.navigate(Routes.support(true, status, assigned, priority))
                },
            )
        }
        composable(
            route = Routes.SUPPORT_TICKET,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("admin") { type = NavType.BoolType; defaultValue = false },
            ),
        ) {
            co.golink.tester.ui.screens.support.SupportTicketScreen(
                onBack = { navController.popBackStack() },
                onNewTicket = { navController.navigate(Routes.SUPPORT_NEW) },
            )
        }
        composable(Routes.SUPPORT_NEW) {
            co.golink.tester.ui.screens.support.NewSupportTicketScreen(
                onBack = { navController.popBackStack() },
                onCreated = { id ->
                    navController.popBackStack()
                    navController.navigate(Routes.supportTicket(id))
                },
            )
        }
        composable(Routes.AUTO_BACKUP) {
            AutoBackupScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.MOBILE_BACKUPS) {
            co.golink.tester.ui.screens.backup.MobileBackupsScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.AUTO_BACKUP) },
                onOpenFile = { fileId -> navController.navigate(Routes.viewer(fileId)) },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
            )
        }
        composable(
            Routes.VERIFY_EMAIL,
            arguments = listOf(navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            co.golink.tester.ui.screens.applinks.VerifyEmailScreen(
                url = entry.arguments?.getString("url").orEmpty(),
                authenticated = top == TopState.Authenticated,
                onContinue = { navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true }; launchSingleTop = true } },
                onSignIn = { freshSignIn() },
                onBack = { backOrRestart() },
            )
        }
        composable(
            Routes.TEAM_INVITATION,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            co.golink.tester.ui.screens.applinks.TeamInvitationScreen(
                id = id,
                authenticated = top == TopState.Authenticated,
                onOpenTeamFolder = { folderId -> openFolderFromLink("shared", folderId) },
                onSignIn = {
                    // Depois do login volta a este convite.
                    AppLinkRouter.set(AppLinkRouter.Target.TeamInvite(id, requireAuth = true))
                    freshSignIn()
                },
                onRegister = {
                    navController.navigate(Routes.LANDING) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
                    navController.navigate(Routes.REGISTER)
                },
                onDone = { backOrRestart() },
            )
        }
        composable(Routes.BOOTSTRAP_ERROR) {
            BootstrapErrorScreen()
        }
    }
    // Painel de transferências (em qualquer ecrã, com a sessão aberta e a app desbloqueada).
    if (top == TopState.Authenticated && !locked) {
        co.golink.tester.ui.components.DownloadPanel(Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
    }
    }

    // O bloqueio vai numa janela própria (Dialog): folhas e popups abertos são
    // janelas por cima da principal e ficavam à vista e clicáveis sobre o PIN.
    val lockVisible = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    lockVisible.targetState = locked && top == TopState.Authenticated
    if (lockVisible.currentState || lockVisible.targetState) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            LockWindowSetup()
            AnimatedVisibility(
                visibleState = lockVisible,
                // Sem fade de entrada: ao voltar à app o conteúdo não pode aparecer nem um instante.
                enter = androidx.compose.animation.EnterTransition.None,
                exit = fadeOut(animationSpec = tween(320)) + scaleOut(animationSpec = tween(320), targetScale = 1.08f),
            ) {
                LockScreen()
            }
        }
    }
}

/**
 * Janela do bloqueio: ecrã inteiro, sem escurecer nem animar a entrada e, no
 * Android 12+, desfoca tudo o que está por trás (incluindo folhas/popups — o blur
 * da janela principal não lhes chega). Herda o FLAG_SECURE da atividade.
 */
@Composable
private fun LockWindowSetup() {
    val view = androidx.compose.ui.platform.LocalView.current
    val activity = androidx.compose.ui.platform.LocalContext.current.findActivity()
    androidx.compose.runtime.SideEffect {
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window ?: return@SideEffect
        window.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT)
        window.setWindowAnimations(0)
        window.setDimAmount(0f)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = 80 }
        }
        val secure = ((activity?.window?.attributes?.flags ?: 0) and android.view.WindowManager.LayoutParams.FLAG_SECURE) != 0
        if (secure) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
