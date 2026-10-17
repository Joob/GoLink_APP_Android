package co.golink.tester.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.ui.i18n.tr
import kotlinx.coroutines.delay

/**
 * Pinça com 2 dedos para mudar de nível (estilo Google Fotos): afastar → nível
 * anterior (itens maiores), juntar → nível seguinte (mais pequenos). Um passo
 * por gesto. Com 1 dedo nada é consumido: scroll, toques e long-press normais.
 */
fun Modifier.pinchToZoomLevels(
    level: Int,
    min: Int = 0,
    max: Int = 4,
    onChange: (Int) -> Unit,
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val currentLevel by rememberUpdatedState(level)
    val currentOnChange by rememberUpdatedState(onChange)
    pointerInput(min, max) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var startSpan = 0f
            var fired = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val a = pressed[0].position
                    val b = pressed[1].position
                    val span = (a - b).getDistance()
                    if (startSpan == 0f) startSpan = span
                    // Dois dedos: a lista não faz scroll enquanto se faz pinça.
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                    if (!fired && startSpan > 0f) {
                        val scale = span / startSpan
                        val next = when {
                            scale > 1.25f -> currentLevel - 1
                            scale < 0.8f -> currentLevel + 1
                            else -> currentLevel
                        }.coerceIn(min, max)
                        if (next != currentLevel) {
                            fired = true
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnChange(next)
                        }
                    }
                } else {
                    startSpan = 0f
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

/** Nome do nível, para a pílula temporária. */
fun zoomLevelLabel(level: Int): String = when (level) {
    0 -> "Lista grande".tr()
    1 -> "Lista".tr()
    2 -> "Grelha · %d colunas".tr().format(2)
    3 -> "Grelha · %d colunas".tr().format(3)
    else -> "Grelha compacta".tr()
}

/** Pílula escura que aparece ~1 s quando o nível muda por pinça. */
@Composable
fun ZoomLevelHint(level: Int, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(level) {
        if (first) { first = false; return@LaunchedEffect }
        shown = true
        delay(1000)
        shown = false
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = modifier,
    ) {
        Text(
            zoomLevelLabel(level),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
