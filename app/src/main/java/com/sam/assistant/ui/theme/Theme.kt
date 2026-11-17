package com.sam.assistant.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = SamPrimary,
    secondary = SamAccent,
    background = SamDarkBackground,
    surface = SamDarkSurface,
    onPrimary = SamText,
    onSecondary = SamText,
    onBackground = SamText,
    onSurface = SamText,
    error = SamError
)

@Composable
fun SAMTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = SamDarkBackground.toArgb()
            window.navigationBarColor = SamDarkBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = SamTypography,
        content = content
    )
}

