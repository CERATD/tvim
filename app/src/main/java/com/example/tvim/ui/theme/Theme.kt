package com.example.tvim.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MinimalColorScheme = darkColorScheme(
    primary = RedAccent,
    onPrimary = Color.White,
    background = PureBlack,
    onBackground = Color.White,
    surface = DarkGray,
    onSurface = Color.White,
    secondary = DarkGray,
    onSecondary = RedAccent
)

@Composable
fun TvimTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = MinimalColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = PureBlack.toArgb()
            window.navigationBarColor = PureBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
