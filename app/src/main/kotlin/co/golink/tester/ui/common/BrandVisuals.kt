package co.golink.tester.ui.common

import co.golink.tester.ui.i18n.tr
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.ui.theme.BrandGreen
import co.golink.tester.ui.theme.BrandGreenDark
import co.golink.tester.ui.theme.BrandGreenLight
import co.golink.tester.ui.theme.Info
import co.golink.tester.ui.theme.TextPrimary

/**
 * Privacy / encryption brand visuals shared across the auth flow.
 * Pure Compose (Canvas + graphicsLayer), no image assets.
 */

/** Shield outline with an inner check mark, drawn on a 64x64 grid. */
@Composable
fun ShieldWithCheck(
    modifier: Modifier = Modifier,
    color: Color = BrandGreen,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun px(x: Float, y: Float) = Offset(x / 64f * w, y / 64f * h)

        val shield = Path().apply {
            moveTo(px(32f, 6f).x, px(32f, 6f).y)
            lineTo(px(12f, 14f).x, px(12f, 14f).y)
            lineTo(px(12f, 30f).x, px(12f, 30f).y)
            cubicTo(px(12f, 45f).x, px(12f, 45f).y, px(22f, 53f).x, px(22f, 53f).y, px(32f, 58f).x, px(32f, 58f).y)
            cubicTo(px(42f, 53f).x, px(42f, 53f).y, px(52f, 45f).x, px(52f, 45f).y, px(52f, 30f).x, px(52f, 30f).y)
            lineTo(px(52f, 14f).x, px(52f, 14f).y)
            close()
        }
        val stroke = w * (3.4f / 64f)
        drawPath(shield, color = color.copy(alpha = 0.12f), style = Fill)
        drawPath(shield, color = color, style = Stroke(width = stroke, join = StrokeJoin.Round))

        val check = Path().apply {
            moveTo(px(24f, 32f).x, px(24f, 32f).y)
            lineTo(px(30f, 38f).x, px(30f, 38f).y)
            lineTo(px(42f, 26f).x, px(42f, 26f).y)
        }
        drawPath(check, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Small vault badge used as the header mark on the auth screens. */
@Composable
fun VaultBadge(
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(10.dp, RoundedCornerShape(size * 0.28f))
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        ShieldWithCheck(Modifier.size(size * 0.54f), BrandGreen)
    }
}

/** Rounded "end-to-end encrypted" reassurance pill. */
@Composable
fun EncryptedPill(
    modifier: Modifier = Modifier,
    text: String = "Encriptado ponta-a-ponta".tr(),
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(BrandGreenLight.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BrandGreen)
    }
}

/** Animated 3D-ish encryption vault scene for the landing/intro screen. */
@Composable
fun EncryptedVaultHero(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "vault")
    val float by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "float",
    )
    val spin by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart), label = "spin",
    )

    val density = LocalDensity.current
    val floatPx = with(density) { 10.dp.toPx() }
    val dashOn = with(density) { 4.dp.toPx() }
    val dashOff = with(density) { 10.dp.toPx() }
    val ringStroke = with(density) { 2.dp.toPx() }

    Box(modifier = modifier.size(236.dp), contentAlignment = Alignment.Center) {

        // Soft glow behind the vault
        Box(
            modifier = Modifier
                .size(196.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(BrandGreen.copy(alpha = 0.18f), Color.Transparent),
                    ),
                ),
        )

        // Rotating dashed orbit ring (flattened to look 3D)
        Canvas(
            modifier = Modifier
                .size(208.dp)
                .graphicsLayer {
                    rotationX = 66f
                    rotationZ = spin
                },
        ) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = BrandGreen.copy(alpha = 0.28f),
                radius = r,
                center = c,
                style = Stroke(width = ringStroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashOn, dashOff))),
            )
            drawCircle(color = BrandGreen, radius = ringStroke * 2.4f, center = Offset(c.x, c.y - r))
            drawCircle(color = Info, radius = ringStroke * 2.4f, center = Offset(c.x + r, c.y))
        }

        // Floating upload chip (top-left)
        FloatingChip(
            up = true,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 6.dp, y = 26.dp)
                .graphicsLayer { translationY = -floatPx * float },
        )

        // Central vault card with a slight 3D tilt + float
        Box(
            modifier = Modifier
                .graphicsLayer {
                    rotationX = 6f
                    rotationY = -10f
                    translationY = -floatPx * float
                }
                .shadow(18.dp, RoundedCornerShape(26.dp))
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White)
                .size(118.dp),
            contentAlignment = Alignment.Center,
        ) {
            ShieldWithCheck(Modifier.size(70.dp), BrandGreen)
        }

        // Floating download chip (bottom-right)
        FloatingChip(
            up = false,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-6).dp, y = (-20).dp)
                .graphicsLayer { translationY = floatPx * float },
        )
    }
}

@Composable
private fun FloatingChip(up: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(13.dp))
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (up) BrandGreen.copy(alpha = 0.15f) else Info.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (up) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = if (up) BrandGreenDark else Info,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(7.dp))
        androidx.compose.foundation.layout.Column {
            Box(
                Modifier
                    .width(30.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TextPrimary.copy(alpha = 0.14f)),
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .width(20.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TextPrimary.copy(alpha = 0.14f)),
            )
        }
        Spacer(Modifier.width(7.dp))
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(13.dp))
    }
}
