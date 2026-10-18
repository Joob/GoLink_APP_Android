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

    LaunchedEffect(top, splashElapsed) {
        if (!splashElapsed) return@LaunchedEffect
        val target = when (top) {
            TopState.NeedsAuth -> Routes.LANDING
            TopState.NeedsOtp -> Routes.OTP
            TopState.Authenticated -> Routes.HOME
            TopState.BootstrapFailed -> Routes.BOOTSTRAP_ERROR
            TopState.Loading -> null
        } ?: return@LaunchedEffect
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
                current == Routes.NOTIFICATIONS ||
                current.startsWith("viewer/")
        )
        val shouldNavigate = when {
            current == null -> true
            current == target -> false
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

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) { SplashScreen() }
        composable(Routes.LANDING) {
            LandingScreen(
                onSignIn = { navController.navigate(Routes.SIGN_IN) },
                onRegister = { navController.navigate(Routes.REGISTER) },
            )
        }
        composable(Routes.SIGN_IN) {
            SignInScreen(
                onLoginSucceeded = { /* gate handles transition */ },
                onRegister = { navController.navigate(Routes.REGISTER) },
                onForgotPassword = { navController.navigate(Routes.FORGOT) },
                onSocialite = { provider -> navController.navigate(Routes.socialite(provider)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegistered = { _ -> navController.popBackStack() },
                onBack = { navController.popBackStack() },
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
            CreateNewPasswordScreen(
                token = token,
                onDone = {
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() },
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
            RegisterScreen(
                onRegistered = { _ ->
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() },
                invitationToken = invitation,
            )
        }
        composable(Routes.OTP) {
            OtpScreen(
                onValidated = { /* gate transitions to Home */ },
                onCancel = { /* logout button on Home is the primary exit */ },
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
                    onOpenAdmin = { pane -> navController.navigate(Routes.settings(pane)) },
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
        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                onOpenSecurityActivity = { navController.navigate(Routes.SECURITY_ACTIVITY) },
                onOpenSupportTicket = { id, admin -> navController.navigate(Routes.supportTicket(id, admin)) },
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
        composable(Routes.BOOTSTRAP_ERROR) {
            BootstrapErrorScreen()
        }
    }

    AnimatedVisibility(
        visible = locked && top == TopState.Authenticated,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(320)) + scaleOut(animationSpec = tween(320), targetScale = 1.08f),
    ) {
        LockScreen()
    }
}
