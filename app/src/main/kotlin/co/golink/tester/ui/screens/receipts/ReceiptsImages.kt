package co.golink.tester.ui.screens.receipts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Imagens dos recibos (= services/receipts/imageTools.js): PDF → 1.ª página, leitura do
 * QR da AT com ZXing e redução para ≤1600 px em JPEG 0,82 (o que vai cifrado).
 * Nada em claro fica em disco: os PDFs são lidos de um ficheiro temporário apagado logo.
 */
object ReceiptsImages {
    const val MAX_FILE = 25L * 1024 * 1024
    private const val MAX_SIDE = 1600
    private const val QUALITY = 82
    private const val PDF_MAX_SIDE = 4000
    /** Lado máximo ao ler o QR de uma imagem escolhida (talões com QR pequeno). */
    private const val READ_MAX_SIDE = 3000

    class TooLargeException : Exception("too large")
    class BadFileException : Exception("bad file")

    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8",
    )

    /** Leitor para a câmara (reaproveitado entre frames; uma thread só). */
    fun newReader(): MultiFormatReader = MultiFormatReader().apply { setHints(hints) }

    private fun decode(reader: MultiFormatReader, source: LuminanceSource): String? {
        for (s in listOf(source, source.invert())) {
            runCatching { return reader.decodeWithState(BinaryBitmap(HybridBinarizer(s))).text }
            reader.reset()
            runCatching { return reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(s))).text }
            reader.reset()
        }
        return null
    }

    /** Frame Y da câmara (só a luminância), recortado ao centro onde está a moldura. */
    fun decodeFrame(reader: MultiFormatReader, y: ByteArray, rowStride: Int, width: Int, height: Int): String? {
        val side = (min(width, height) * 0.8f).roundToInt()
        val left = (width - side) / 2
        val top = (height - side) / 2
        val src = runCatching { PlanarYUVLuminanceSource(y, rowStride, height, left, top, side, side, false) }.getOrNull() ?: return null
        return decode(reader, src) ?: runCatching { decode(reader, PlanarYUVLuminanceSource(y, rowStride, height, 0, 0, width, height, false)) }.getOrNull()
    }

    fun decodeBitmap(bmp: Bitmap): String? {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val reader = newReader()
        val full = RGBLuminanceSource(bmp.width, bmp.height, px)
        decode(reader, full)?.let { return it }
        // QR pequeno num talão comprido: tenta também a metade de baixo (onde costuma estar).
        if (bmp.height > bmp.width * 1.4f) {
            val half = bmp.height / 2
            return decode(reader, full.crop(0, half, bmp.width, bmp.height - half))
        }
        return null
    }

    private fun mimeOf(context: Context, uri: Uri): String = context.contentResolver.getType(uri).orEmpty()

    private fun sizeOf(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L
    }.getOrDefault(-1L)

    fun isPdf(context: Context, uri: Uri): Boolean =
        mimeOf(context, uri) == "application/pdf" || uri.lastPathSegment.orEmpty().endsWith(".pdf", ignoreCase = true)

    /** Imagem ou PDF escolhido → bitmap para ler o QR (rodado pelo EXIF). */
    fun load(context: Context, uri: Uri): Bitmap {
        val size = sizeOf(context, uri)
        if (size > MAX_FILE) throw TooLargeException()
        val mime = mimeOf(context, uri)
        if (isPdf(context, uri)) return pdfFirstPage(context, uri)
        if (mime.isNotEmpty() && !mime.startsWith("image/")) throw BadFileException()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw BadFileException()
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= READ_MAX_SIDE) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw BadFileException()
        val rotation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        return rotate(bmp, rotation)
    }

    private val ExifInterface.rotationDegrees: Int
        get() = when (getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    fun rotate(bmp: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bmp
        val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
        if (out !== bmp) bmp.recycle()
        return out
    }

    /** 1.ª página do PDF em branco (como o pdf.js da web: escala ≤2, lado ≤4000). */
    private fun pdfFirstPage(context: Context, uri: Uri): Bitmap {
        // PdfRenderer precisa de um descritor com seek: copia para a cache e apaga logo.
        val tmp = File.createTempFile("rc", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_FILE) throw TooLargeException()
                        out.write(buf, 0, n)
                    }
                }
            } ?: throw BadFileException()
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { pdf ->
                    if (pdf.pageCount < 1) throw BadFileException()
                    pdf.openPage(0).use { page ->
                        val scale = min(2f, PDF_MAX_SIDE.toFloat() / max(page.width, page.height).coerceAtLeast(1))
                        val w = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val h = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return bmp
                    }
                }
            }
        } finally {
            tmp.delete()
        }
    }

    /** ≤1600 px no lado maior, JPEG 0,82 (o que vai cifrado para o servidor). */
    fun toJpeg(bmp: Bitmap): ByteArray {
        val scale = min(1f, MAX_SIDE.toFloat() / max(bmp.width, bmp.height))
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).roundToInt().coerceAtLeast(1), (bmp.height * scale).roundToInt().coerceAtLeast(1), true) else bmp
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            if (scaled !== bmp) scaled.recycle()
            out.toByteArray()
        }
    }

    /** JPEG decifrado → bitmap reduzido para o ecrã (miniatura/zoom). */
    fun decodeSampled(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
