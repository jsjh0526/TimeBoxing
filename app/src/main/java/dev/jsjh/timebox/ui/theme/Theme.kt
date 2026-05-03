package dev.jsjh.timebox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AppPrimary,
    secondary = AppAccent,
    tertiary = AppPrimary,
    background = AppBackground,
    surface = AppSurface,
    surfaceVariant = AppSurfaceAlt,
    outline = AppBorder,
    onPrimary = AppTextPrimary,
    onSecondary = AppBackground,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary,
    onSurfaceVariant = AppTextSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = AppPrimary,
    secondary = AppAccent,
    tertiary = AppPrimary,
    background = AppBackground,
    surface = AppSurface,
    surfaceVariant = AppSurfaceAlt,
    outline = AppBorder,
    onPrimary = AppTextPrimary,
    onSecondary = AppBackground,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary,
    onSurfaceVariant = AppTextSecondary
)

@Composable
fun TimeBoxingTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
