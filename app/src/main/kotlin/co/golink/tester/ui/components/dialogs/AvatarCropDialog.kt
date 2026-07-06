package co.golink.tester.ui.components.dialogs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import co.golink.tester.ui.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Popup para recortar/redimensionar a imagem antes de a enviar como avatar.
 * O utilizador arrasta e faz pinça para enquadrar; o recorte é sempre quadrado
 * (o círculo é só o guia visual, já que o avatar é mostrado redondo). Confirmar
 * devolve o JPEG (512×512) pronto para upload.
 */
@Composable
fun AvatarCropDialog(
    imageUri: Uri,
    onCancel: () -> Unit,
    onConfirm: (ByteArray) -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Tamanho (px) do viewport quadrado, capturado no layout para a matemática do recorte.
    var viewportPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(imageUri) {
        bitmap = withContext(Dispatchers.IO) { runCatching { decodeSampled(context, imageUri) }.getOrNull() }
    }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    "Ajustar imagem".tr(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Arrasta e faz pinça para enquadrar".tr(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        // Sem isto, ao ampliar (zoom) a imagem transbordava o viewport
                        // e tapava o resto do popup. O clip mantém-na dentro do quadrado/círculo.
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val d = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
                    viewportPx = d
                    val bmp = bitmap
                    if (bmp == null) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        val baseScale = d / minOf(bmp.width, bmp.height).toFloat()
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth })
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y,
                                )
                                .pointerInput(bmp) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (scale * zoom).coerceIn(1f, 6f)
                                        val effW = bmp.width * baseScale * newScale
                                        val effH = bmp.height * baseScale * newScale
                                        val maxX = max(0f, (effW - d) / 2f)
                                        val maxY = max(0f, (effH - d) / 2f)
                                        scale = newScale
                                        offset = Offset(
                                            (offset.x + pan.x).coerceIn(-maxX, maxX),
                                            (offset.y + pan.y).coerceIn(-maxY, maxY),
                                        )
                                    }
                                },
                        )
                        // Máscara: escurece fora do círculo + anel guia.
                        androidx.compose.foundation.Canvas(modifier = Modifier.size(with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth })) {
                            val r = size.minDimension / 2f
                            val circle = Path().apply {
                                addOval(androidx.compose.ui.geometry.Rect(center = center, radius = r))
                            }
                            clipPath(circle, clipOp = ClipOp.Difference) {
                                drawRect(Color.Black.copy(alpha = 0.45f))
                            }
                            drawCircle(Color.White.copy(alpha = 0.9f), radius = r, style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancel) { Text("Cancelar".tr()) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = bitmap != null,
                        onClick = {
                            val bmp = bitmap ?: return@Button
                            val jpeg = cropToJpeg(bmp, viewportPx, scale, offset)
                            if (jpeg != null) onConfirm(jpeg) else onCancel()
                        },
                    ) { Text("Confirmar".tr()) }
                }
            }
        }
    }
}

// Recorte quadrado a partir do transform (scale/offset) aplicado com ContentScale.Crop.
private fun cropToJpeg(bmp: Bitmap, viewport: Float, scale: Float, offset: Offset): ByteArray? {
    if (viewport <= 0f) return null
    val w = bmp.width.toFloat()
    val h = bmp.height.toFloat()
    val baseScale = viewport / minOf(w, h)
    val eff = baseScale * scale
    if (eff <= 0f) return null
    // Canto superior-esquerdo do viewport mapeado para píxeis do bitmap.
    val srcLeft = w / 2f - (viewport / 2f + offset.x) / eff
    val srcTop = h / 2f - (viewport / 2f + offset.y) / eff
    val srcSize = viewport / eff
    val l = srcLeft.roundToInt().coerceIn(0, (bmp.width - 1).coerceAtLeast(0))
    val t = srcTop.roundToInt().coerceIn(0, (bmp.height - 1).coerceAtLeast(0))
    val s = srcSize.roundToInt().coerceAtLeast(1)
    val sw = s.coerceAtMost(bmp.width - l)
    val sh = s.coerceAtMost(bmp.height - t)
    if (sw <= 0 || sh <= 0) return null
    return runCatching {
        val cropped = Bitmap.createBitmap(bmp, l, t, sw, sh)
        val out = Bitmap.createScaledBitmap(cropped, OUTPUT, OUTPUT, true)
        ByteArrayOutputStream().use { bos ->
            out.compress(Bitmap.CompressFormat.JPEG, 90, bos)
            bos.toByteArray()
        }
    }.getOrNull()
}

// Decodifica com amostragem (limita a MAX_DECODE) e corrige a orientação EXIF.
private fun decodeSampled(context: Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    while (longest / sample > MAX_DECODE) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    } ?: return null
    val orientation = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        else -> return raw
    }
    return runCatching { Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true) }.getOrDefault(raw)
}

private const val OUTPUT = 512
private const val MAX_DECODE = 1600
