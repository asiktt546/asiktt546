package com.meshchat.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MeshDarkColorScheme = darkColorScheme(
    primary = MeshGreen,
    onPrimary = TextOnPrimary,
    primaryContainer = MeshGreenLight,
    onPrimaryContainer = TextOnPrimary,
    secondary = MeshTeal,
    onSecondary = TextOnPrimary,
    secondaryContainer = MeshTeal,
    onSecondaryContainer = TextOnPrimary,
    tertiary = MeshGreenAccent,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DividerColor,
    error = ErrorColor,
    onError = TextOnPrimary,
)

@Composable
fun MeshChatTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = MeshDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkBackground.toArgb()
            window.navigationBarColor = DarkBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MeshTypography,
        content = content
    )
}
