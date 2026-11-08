package co.golink.tester.ui.screens.lock

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Fundo do bloqueio quando não há desfoque entre janelas (Android < 12, poupança
 * de bateria, aparelhos sem suporte): captura da janela principal, reduzida e
 * desfocada em software. null = falhou (fica o fundo opaco).
 */
object LockBackdrop {
    private const val SCALE = 10
    private const val RADIUS = 6

    suspend fun capture(activity: Activity): ImageBitmap? {
        val window = activity.window ?: return null
        val decor = window.decorView
        val w = decor.width
        val h = decor.height
        if (w <= 0 || h <= 0) return null
        val full = runCatching { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val ok = suspendCancellableCoroutine { cont ->
            runCatching {
                PixelCopy.request(window, full, { result -> if (cont.isActive) cont.resume(result == PixelCopy.SUCCESS) }, Handler(Looper.getMainLooper()))
            }.onFailure { if (cont.isActive) cont.resume(false) }
        }
        if (!ok) { full.recycle(); return null }
        return withContext(Dispatchers.Default) {
            val small = Bitmap.createScaledBitmap(full, (w / SCALE).coerceAtLeast(1), (h / SCALE).coerceAtLeast(1), true)
            full.recycle()
            boxBlur(small, RADIUS)
            boxBlur(small, RADIUS)
            small.asImageBitmap()
        }
    }

    /** Box blur separável (2 passagens ≈ gaussiano), no próprio bitmap. */
    private fun boxBlur(bmp: Bitmap, r: Int) {
        val w = bmp.width
        val h = bmp.height
        val src = IntArray(w * h)
        bmp.getPixels(src, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        pass(src, tmp, w, h, r, horizontal = true)
        pass(tmp, src, w, h, r, horizontal = false)
        bmp.setPixels(src, 0, w, 0, 0, w, h)
    }

    private fun pass(input: IntArray, out: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = r * 2 + 1
        for (line in 0 until lines) {
            fun idx(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + c else c * w + line
            }
            var a = 0; var rr = 0; var g = 0; var b = 0
            for (i in -r..r) {
                val p = input[idx(i)]
                a += p ushr 24; rr += (p shr 16) and 0xff; g += (p shr 8) and 0xff; b += p and 0xff
            }
            for (i in 0 until len) {
                out[idx(i)] = ((a / div) shl 24) or ((rr / div) shl 16) or ((g / div) shl 8) or (b / div)
                val pOut = input[idx(i - r)]
                val pIn = input[idx(i + r + 1)]
                a += (pIn ushr 24) - (pOut ushr 24)
                rr += ((pIn shr 16) and 0xff) - ((pOut shr 16) and 0xff)
                g += ((pIn shr 8) and 0xff) - ((pOut shr 8) and 0xff)
                b += (pIn and 0xff) - (pOut and 0xff)
            }
        }
    }
}
