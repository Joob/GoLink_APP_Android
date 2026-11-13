package co.golink.tester.ui.screens.legacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import co.golink.tester.data.pass.PassClipboard
import co.golink.tester.data.settings.SecureScreens

/** Segundos até o código de um destinatário sair do clipboard. */
private const val LEGACY_CLIP_CLEAR_SECONDS = 60

/**
 * Copia um código de destinatário como o Pass copia passwords: clip sensível (fora da
 * pré-visualização do Android 13+) e limpo ao fim de [LEGACY_CLIP_CLEAR_SECONDS].
 */
@Composable
fun rememberLegacyCopy(): (String) -> Unit {
    val context = LocalContext.current.applicationContext
    val clipboard = remember(context) { PassClipboard(context) }
    return remember(clipboard) { { text -> clipboard.copy("GoLink Legacy", text, sensitive = true, clearAfterSeconds = LEGACY_CLIP_CLEAR_SECONDS) } }
}

/** FLAG_SECURE (decidido pela MainActivity) enquanto [active]: códigos ou conteúdo decifrado no ecrã. */
@Composable
fun LegacySecureWhile(active: Boolean) {
    DisposableEffect(active) {
        if (active) SecureScreens.acquire()
        onDispose { if (active) SecureScreens.release() }
    }
}
