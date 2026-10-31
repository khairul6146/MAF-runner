package com.mafrunner.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NordicEmerald,
    secondary = NordicCyan,
    tertiary = NordicAmber,
    background = NordicBlack,
    surface = NordicDarkGray,
    onPrimary = NordicBlack,
    onSecondary = NordicBlack,
    onBackground = NordicWhite,
    onSurface = NordicWhite,
    error = NordicCrimson
)

@Composable
fun MafRunnerTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = NordicBlack.toArgb()
                window.navigationBarColor = NordicBlack.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MAFRunnerTheme(
    content: @Composable () -> Unit
) {
    MafRunnerTheme(content = content)
}
