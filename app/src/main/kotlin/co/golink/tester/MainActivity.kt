package co.golink.tester

import android.graphics.Color
import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(if (isDark) Color.parseColor("#151515") else Color.WHITE))
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        logger.log("App", "App iniciada")
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
