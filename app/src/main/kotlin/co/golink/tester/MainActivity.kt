package co.golink.tester

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
import co.golink.tester.data.AppLogger
import co.golink.tester.data.settings.AppLockManager
import co.golink.tester.ui.navigation.AppNavHost
import co.golink.tester.ui.theme.VueFileManagerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var lockManager: AppLockManager
    @Inject lateinit var logger: AppLogger

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
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    lockManager.onForeground()
                    logger.log("App", "App em primeiro plano")
                }
                Lifecycle.Event.ON_STOP -> {
                    lockManager.onBackground()
                    logger.log("App", "App em segundo plano")
                }
                else -> {}
            }
        })
        setContent {
            VueFileManagerTheme {
                AppNavHost()
            }
        }
    }
}
