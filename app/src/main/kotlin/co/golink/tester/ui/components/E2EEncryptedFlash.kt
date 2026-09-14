package co.golink.tester.ui.components

import co.golink.tester.ui.i18n.tr
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.golink.tester.ui.theme.BrandGreen
import kotlinx.coroutines.delay

private const val E2E_FLASH_DURATION_MS = 2400L

/**
 * Floating "End-to-end encrypted" pill that briefly appears whenever [trigger] changes.
 * Drop inside a Box and align with `Modifier.align(Alignment.TopCenter)` — the pill
 * is small, self-dismisses, and only reassures the user that the menu they just
 * entered is E2E-encrypted.
 */
@Composable
fun E2EEncryptedFlash(
    trigger: Any?,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(trigger) {
        visible = true
        delay(E2E_FLASH_DURATION_MS)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 },
        exit = fadeOut(tween(280)) + slideOutVertically(tween(280)) { it / 2 },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(999.dp), clip = false)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = BrandGreen,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "End-to-end encrypted",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandGreen,
            )
        }
    }
}

/**
 * Versão fixa (não desaparece) do selo "End-to-end encrypted" — para mostrar
 * de forma permanente no fundo de um ecrã (ex.: cada aba dos backups).
 */
@Composable
fun E2EEncryptedBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = BrandGreen,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "End-to-end encrypted",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = BrandGreen,
        )
    }
}
