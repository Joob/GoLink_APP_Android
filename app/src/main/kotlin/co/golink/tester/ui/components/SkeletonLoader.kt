package co.golink.tester.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val titleWidths = listOf(0.45f, 0.70f, 0.35f, 0.55f, 0.60f, 0.40f, 0.50f, 0.65f)
private val subWidths = listOf(0.25f, 0.35f, 0.20f, 0.30f, 0.28f, 0.22f)

@Composable
fun FileListSkeleton(count: Int = 6, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val strong = Color(0xFFE5E7EB)
    val soft = Color(0xFFF3F4F6)
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        repeat(count) { i ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .alpha(pulse),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(strong),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Bar(color = strong, widthFraction = titleWidths[i % titleWidths.size], height = 12.dp)
                    Spacer(Modifier.height(8.dp))
                    Bar(color = soft, widthFraction = subWidths[i % subWidths.size], height = 8.dp)
                }
            }
        }
    }
}

@Composable
private fun Bar(color: Color, widthFraction: Float, height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(color),
    )
}

