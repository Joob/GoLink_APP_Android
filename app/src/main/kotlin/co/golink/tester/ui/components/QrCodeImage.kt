package co.golink.tester.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

@Composable
fun QrCodeImage(url: String) {
    val bitmap = remember(url) {
        if (url.isBlank()) return@remember null
        runCatching {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            )
            val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 512, 512, hints)
            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h) { i ->
                if (matrix[i % w, i / w]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
            android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                .also { it.setPixels(pixels, 0, w, 0, 0, w, h) }
        }.getOrNull()
    } ?: return

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "QR Code",
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    )
}
