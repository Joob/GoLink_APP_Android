package co.golink.tester

import androidx.compose.foundation.layout.fillMaxSize
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import co.golink.tester.data.AppLogger
import co.golink.tester.data.settings.AppLockManager
import co.golink.tester.data.settings.SecureScreens
import co.golink.tester.ui.navigation.AppNavHost
import co.golink.tester.ui.theme.VueFileManagerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : androidx.fragment.app.FragmentActivity() {

    @Inject lateinit var lockManager: AppLockManager
    @Inject lateinit var securityPrefs: co.golink.tester.data.settings.AppSecurityPreferences
    @Inject lateinit var logger: AppLogger
    @Inject lateinit var legacyCheckin: co.golink.tester.data.legacy.LegacyCheckin
    @Inject lateinit var sessionManager: co.golink.tester.data.auth.SessionManager

    // Sem esta permissão (Android 13+) o NotificationManager.notify() é ignorado
    // em silêncio → downloads (single, zip e foreground service) não mostram
    // nenhuma notificação. Pedimos uma vez no arranque.
    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* resultado irrelevante */ }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        logger.log("App", "App iniciada")
        ensureNotificationPermission()
        // Só no arranque "a sério": ao recriar ou ao voltar pelos recentes o
        // sistema reentrega o Intent original do lembrete.
        val fromHistory = (intent.flags and android.content.Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        externalStart = savedInstanceState == null && !fromHistory && !isLauncherIntent(intent)
        if (savedInstanceState == null && !fromHistory) {
            co.golink.tester.ui.navigation.CalendarDeepLink.from(intent)
            co.golink.tester.data.receipts.ReceiptsDeepLink.from(intent)
            co.golink.tester.ui.navigation.InboxDeepLink.from(intent)
            // Link de um email (App Link): abre o ecrã certo na app.
            co.golink.tester.ui.navigation.AppLinkRouter.from(intent, fromNewIntent = false)
        }
        // Keystore/Tink do PIN fora da main thread (1.º PIN no bloqueio sem engasgo).
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { securityPrefs.warmUp() }
        // Carrega o motor da WebView quando a UI fica parada: a 1.ª entrada nas Notes
        // (e no editor) deixa de pagar essa inicialização (centenas de ms).
        android.os.Looper.myQueue().addIdleHandler {
            runCatching { android.webkit.WebSettings.getDefaultUserAgent(applicationContext) }
            false
        }
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    lockManager.onForeground()
                    // Check-in do Legacy só quando o utilizador abre a app (não por share,
                    // App Link ou notificação) e só depois de passar o bloqueio da app.
                    checkinWhenUnlocked = !externalStart
                    externalStart = false
                    maybeLegacyCheckin()
                    sessionManager.resyncIfNeeded()
                    logger.log("App", "App em primeiro plano")
                }
                // Modo privado: sem miniatura nos recentes enquanto o bloqueio estiver ligado.
                Lifecycle.Event.ON_RESUME -> {
                    lockManager.onResume()
                    applyPrivacyMode()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    lockManager.onPause()
                    applyPrivacyMode()
                }
                Lifecycle.Event.ON_STOP -> {
                    checkinWhenUnlocked = false
                    // Rodar o ecrã / mudar de tema recria a atividade: não é sair da app.
                    if (!isChangingConfigurations) lockManager.onBackground()
                    logger.log("App", "App em segundo plano")
                }
                else -> {}
            }
        })
        applyPrivacyMode()
        // Ecrãs seguros (Pass) a entrar/sair: recalcula o FLAG_SECURE.
        lifecycleScope.launch { SecureScreens.active.collect { applyPrivacyMode() } }
        // PIN/biometria aceites: o check-in pendente deste arranque conta agora.
        lifecycleScope.launch { lockManager.locked.collect { maybeLegacyCheckin() } }
        setContent {
            VueFileManagerTheme(darkTheme = co.golink.tester.ui.theme.AppThemeMode.isDark()) {
                androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    AppNavHost()
                    // Aviso de ligação por cima de tudo (offline / a tentar de novo / restabelecida).
                    co.golink.tester.ui.components.ConnectivityBanner(
                        modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.TopCenter),
                    )
                }
            }
        }
    }

    // Android 13+: só a miniatura dos recentes fica em branco (capturas continuam).
    // Antes do 13 não há alternativa ao FLAG_SECURE (bloqueia também capturas).
    // O FLAG_SECURE é decidido só aqui: modo privado OU um ecrã seguro ativo (Pass).
    private fun applyPrivacyMode() {
        val hide = lockManager.hideInRecents || lockManager.locksOnLeave
        val legacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        if (!legacy) {
            // Folhas/popups são janelas próprias (a camada não as tapa): sem captura,
            // ao voltar o sistema mostra o splash em vez do conteúdo.
            setRecentsScreenshotEnabled(!hide)
        }
        if (SecureScreens.active.value > 0 || (legacy && hide)) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Câmara, seletores de ficheiros/pastas, etc. (launchers de resultado passam
    // por aqui): voltar deles não volta a pedir o PIN.
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: android.content.Intent, requestCode: Int, options: Bundle?) {
        lockManager.expectExternalReturn()
        super.startActivityForResult(intent, requestCode, options)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun startIntentSenderForResult(
        intent: android.content.IntentSender,
        requestCode: Int,
        fillInIntent: android.content.Intent?,
        flagsMask: Int,
        flagsValues: Int,
        extraFlags: Int,
        options: Bundle?,
    ) {
        lockManager.expectExternalReturn()
        super.startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, options)
    }

    // Lembrete do calendário tocado com a app já aberta.
    // Arranque por share, App Link ou notificação: esse ON_START não é um check-in do Legacy.
    private var externalStart = false
    private var checkinWhenUnlocked = false

    private fun isLauncherIntent(intent: android.content.Intent?): Boolean =
        intent?.action == android.content.Intent.ACTION_MAIN && intent.hasCategory(android.content.Intent.CATEGORY_LAUNCHER)

    private fun maybeLegacyCheckin() {
        if (!checkinWhenUnlocked || lockManager.locked.value) return
        checkinWhenUnlocked = false
        legacyCheckin.onForeground()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Chega antes do ON_START quando a app volta do fundo.
        externalStart = !isLauncherIntent(intent)
        co.golink.tester.ui.navigation.CalendarDeepLink.from(intent)
        co.golink.tester.data.receipts.ReceiptsDeepLink.from(intent)
        co.golink.tester.ui.navigation.InboxDeepLink.from(intent)
        co.golink.tester.ui.navigation.AppLinkRouter.from(intent, fromNewIntent = true)
    }
}
