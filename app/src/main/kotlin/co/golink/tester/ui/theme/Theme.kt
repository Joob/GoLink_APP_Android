package co.golink.tester.ui.theme

import co.golink.tester.ui.i18n.tr
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = BrandGreenLight,
    onPrimaryContainer = BrandGreenDark,
    secondary = BrandGreenDark,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = LightBackground,
    onSurfaceVariant = TextMuted,
    outline = LightBorder,
    error = Danger,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = BrandGreenDark,
    onPrimaryContainer = Color.White,
    secondary = BrandGreenLight,
    onSecondary = TextPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkForeground,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkBackground,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    error = Danger,
    onError = Color.White,
)

@Composable
fun VueFileManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
    }
}

/**
 * Tema efetivo da app (escolha Lua/Sol ou sistema). Usar em vez de
 * isSystemInDarkTheme() e de recursos -night, que só seguem o sistema.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }
