package co.golink.tester.ui.common

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * App em segundo plano: depois de [graceMs] (a página grava o pendente com o flush do
 * ON_STOP), pára os temporizadores JS (o poll do collab continuava de 6 em 6 s).
 * pauseTimers é global a todas as WebViews do processo, por isso segue o ciclo de vida
 * do processo e não o do ecrã (outra WebView à frente continuaria parada).
 */
@Composable
fun PauseWebViewTimersInBackground(webView: () -> WebView?, graceMs: Long = 5_000) {
    val current = rememberUpdatedState(webView)
    DisposableEffect(Unit) {
        val main = Handler(Looper.getMainLooper())
        var paused = false
        val pause = Runnable {
            current.value()?.let {
                runCatching { it.pauseTimers() }
                paused = true
            }
        }
        fun resume() {
            main.removeCallbacks(pause)
            if (paused) {
                paused = false
                runCatching { current.value()?.resumeTimers() }
            }
        }
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> main.postDelayed(pause, graceMs)
                Lifecycle.Event.ON_START -> resume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            resume()
        }
    }
}
