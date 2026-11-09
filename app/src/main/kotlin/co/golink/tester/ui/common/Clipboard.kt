package co.golink.tester.ui.common

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/**
 * Copia um link de partilha. Com a chave E2E no fragmento (`#k=`) marca o clip
 * como sensível: o Android 13+ não o mostra na pré-visualização e os teclados
 * não o sincronizam.
 */
fun copyLinkToClipboard(context: Context, label: String, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        if (text.contains("#k=")) {
            clip.description.extras = PersistableBundle().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                else putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        cm.setPrimaryClip(clip)
    }
}

/**
 * Copia um segredo (ex.: token de API): clip marcado como sensível e limpo ao fim de
 * [clearAfterMs] se ainda for o nosso (mesma marca que o Pass usa).
 */
fun copySecretToClipboard(context: Context, label: String, text: String, clearAfterMs: Long = 60_000L) {
    runCatching {
        val app = context.applicationContext
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        val marker = java.util.UUID.randomUUID().toString()
        clip.description.extras = PersistableBundle().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            else putBoolean("android.content.extra.IS_SENSITIVE", true)
            putString(co.golink.tester.data.pass.PassClipboard.CLIP_MARKER, marker)
        }
        cm.setPrimaryClip(clip)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            co.golink.tester.data.pass.PassClipboard.clearIfOurs(app, marker)
        }, clearAfterMs)
    }
}
