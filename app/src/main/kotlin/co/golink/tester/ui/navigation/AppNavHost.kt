package co.golink.tester.ui.navigation

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
import co.golink.tester.data.AppLogger
import co.golink.tester.data.auth.AuthState
import co.golink.tester.data.auth.SessionManager
import co.golink.tester.data.settings.AppLockManager
import co.golink.tester.ui.screens.browse.BrowseScreen
import co.golink.tester.ui.screens.lock.LockScreen
import androidx.navigation.compose.currentBackStackEntryAsState
import co.golink.tester.ui.screens.error.BootstrapErrorScreen
import co.golink.tester.ui.screens.viewer.FileViewerScreen
import co.golink.tester.ui.screens.forgot.ForgotPasswordScreen
import co.golink.tester.ui.screens.notifications.NotificationsScreen
import co.golink.tester.ui.screens.otp.OtpScreen
import co.golink.tester.ui.screens.register.RegisterScreen
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
    const val OTP = "otp"
    const val HOME = "home"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings?initial={initial}"
    fun settings(initial: String? = null) = if (initial == null) "settings" else "settings?initial=$initial"
    const val BOOTSTRAP_ERROR = "bootstrap_error"
    const val SOCIALITE = "socialite/{provider}"
    fun socialite(provider: String) = "socialite/$provider"
    const val VIEWER = "viewer/{fileId}"
    fun viewer(id: String) = "viewer/$id"
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
        composable(Routes.HOME) {
            BrowseScreen(
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenSettings = { navController.navigate(Routes.settings()) },
                onOpenBilling = { navController.navigate(Routes.settings("billing")) },
                onOpenFile = { fileId -> navController.navigate(Routes.viewer(fileId)) },
            )
        }
        composable(
            route = Routes.VIEWER,
            arguments = listOf(navArgument("fileId") { type = NavType.StringType }),
        ) {
            FileViewerScreen(onClose = { navController.popBackStack() })
        }
        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(onBack = { navController.popBackStack() })
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
