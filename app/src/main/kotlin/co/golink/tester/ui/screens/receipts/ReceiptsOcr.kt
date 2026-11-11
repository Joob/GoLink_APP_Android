package co.golink.tester.ui.screens.receipts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * OCR dos talões no aparelho (Tesseract, modelo português dentro da APK). A imagem
 * e o texto nunca saem do telemóvel e o texto não é guardado: só os campos que o
 * utilizador confirma no formulário.
 */
object ReceiptsOcr {
    /** Muda quando o modelo em assets/tessdata muda (obriga a copiar outra vez). */
    private const val MODEL = "por-best-711de9db"
    private const val MAX_SIDE = 2400
    private const val TIMEOUT_MS = 20_000L

    /** Um OCR de cada vez (cada instância do Tesseract ocupa dezenas de MB). Liberta-se no fim da thread. */
    private val gate = Semaphore(1)

    /** Copia o modelo para filesDir uma vez (o Tesseract precisa de um caminho real). */
    @Synchronized
    private fun dataDir(context: Context): File {
        val root = File(context.filesDir, "tesseract")
        val dir = File(root, "tessdata")
        val model = File(dir, "por.traineddata")
        val marker = File(dir, "por.version")
        if (model.exists() && marker.exists() && marker.readText() == MODEL) return root
        dir.mkdirs()
        val tmp = File(dir, "por.traineddata.tmp")
        context.assets.open("tessdata/por.traineddata").use { input -> tmp.outputStream().use { input.copyTo(it, 64 * 1024) } }
        model.delete()
        if (!tmp.renameTo(model)) throw IllegalStateException("tessdata")
        marker.writeText(MODEL)
        return root
    }

    /** Cinzento, lado maior ≤ 2400 px e contraste esticado (percentis 2–98). */
    private fun prepare(src: Bitmap): Bitmap {
        val scale = minOf(1f, MAX_SIDE.toFloat() / max(src.width, src.height))
        val w = (src.width * scale).roundToInt().coerceAtLeast(1)
        val h = (src.height * scale).roundToInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            src,
            android.graphics.Rect(0, 0, src.width, src.height),
            android.graphics.Rect(0, 0, w, h),
            Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) },
        )
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        val hist = IntArray(256)
        for (c in px) hist[c and 0xFF]++
        fun percentile(p: Double): Int {
            val target = (px.size * p).toLong()
            var acc = 0L
            for (i in 0..255) {
                acc += hist[i]
                if (acc >= target) return i
            }
            return 255
        }
        val lo = percentile(0.02)
        val hi = percentile(0.98)
        if (hi - lo in 20..230) {
            val lut = IntArray(256) { v -> ((v - lo) * 255 / (hi - lo)).coerceIn(0, 255) }
            for (i in px.indices) {
                val g = lut[px[i] and 0xFF]
                px[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
            out.setPixels(px, 0, w, 0, 0, w, h)
        }
        return out
    }

    /**
     * Texto do talão. Cancelável (pára o Tesseract) e com limite de 20 s.
     * [onProgress] recebe 0–100 numa thread de fundo.
     */
    suspend fun read(context: Context, bitmap: Bitmap, onProgress: (Int) -> Unit = {}): String {
        gate.acquire()
        var handedOff = false
        try {
            val (root, img) = withContext(Dispatchers.Default) { dataDir(context) to prepare(bitmap) }
            val api = TessBaseAPI { values -> onProgress(values.percent) }
            if (!api.init(root.absolutePath, "por")) {
                api.recycle()
                img.recycle()
                throw IllegalStateException("tesseract init")
            }
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            api.setImage(img)
            // O Tesseract é bloqueante e nativo: corre numa thread própria, que é dona
            // da API e da imagem (recycle + gate no fim, mesmo depois de cancelado).
            val result = CompletableDeferred<String>()
            val lock = Any()
            var finished = false
            thread(name = "rc-ocr", isDaemon = true) {
                try {
                    api.getHOCRText(0) // reconhece, com progresso
                    result.complete(api.utF8Text.orEmpty())
                } catch (e: Throwable) {
                    result.completeExceptionally(e)
                } finally {
                    synchronized(lock) {
                        finished = true
                        runCatching { api.recycle() }
                    }
                    img.recycle()
                    gate.release()
                }
            }
            handedOff = true
            return try {
                withTimeout(TIMEOUT_MS) { result.await() }
            } catch (e: CancellationException) {
                // Cancelado ou 20 s: o Tesseract pára e a thread acaba (nunca depois do recycle).
                synchronized(lock) { if (!finished) runCatching { api.stop() } }
                throw e
            }
        } finally {
            if (!handedOff) gate.release()
        }
    }
}
