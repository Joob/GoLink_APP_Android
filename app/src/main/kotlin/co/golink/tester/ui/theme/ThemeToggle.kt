package co.golink.tester.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import co.golink.tester.ui.i18n.tr

/**
 * Botão Lua/Sol (menu lateral, ao lado da língua): Lua quando a app está clara,
 * Sol quando está escura. Tocar alterna e guarda a escolha (AppThemeMode).
 * Mesmo estilo em pílula do LanguageMenu.
 */
@Composable
fun ThemeToggle(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = LocalDarkTheme.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape)
            .clickable {
                AppThemeMode.set(context, if (dark) AppThemeMode.Mode.LIGHT else AppThemeMode.Mode.DARK)
            },
    ) {
        AnimatedContent(
            targetState = dark,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.6f)) togetherWith
                    (fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.6f))
            },
            label = "theme-toggle",
        ) { isDark ->
            if (isDark) {
                Icon(
                    Icons.Outlined.LightMode,
                    contentDescription = "Tema claro".tr(),
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    Icons.Outlined.DarkMode,
                    contentDescription = "Tema escuro".tr(),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
