package com.example.tvim.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.tvim.R

val LocalNothingDesign = staticCompositionLocalOf { false }
val NothingDisplayFont = FontFamily(Font(R.font.nothing_doto))

private val NothingTypography = Typography.copy(
    displayLarge = Typography.displayLarge.copy(fontFamily = NothingDisplayFont, letterSpacing = 1.5.sp),
    displayMedium = Typography.displayMedium.copy(fontFamily = NothingDisplayFont, letterSpacing = 1.5.sp),
    displaySmall = Typography.displaySmall.copy(fontFamily = NothingDisplayFont, letterSpacing = 1.2.sp),
    headlineLarge = Typography.headlineLarge.copy(fontFamily = NothingDisplayFont, letterSpacing = 1.sp),
    headlineMedium = Typography.headlineMedium.copy(fontFamily = NothingDisplayFont, letterSpacing = 0.8.sp),
    headlineSmall = Typography.headlineSmall.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.5.sp),
    titleLarge = Typography.titleLarge.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.45.sp),
    titleMedium = Typography.titleMedium.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.4.sp),
    titleSmall = Typography.titleSmall.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.35.sp),
    bodyLarge = Typography.bodyLarge.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.35.sp),
    bodyMedium = Typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.3.sp),
    bodySmall = Typography.bodySmall.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.25.sp),
    labelLarge = Typography.labelLarge.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.45.sp),
    labelMedium = Typography.labelMedium.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.4.sp),
    labelSmall = Typography.labelSmall.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.35.sp)
)

@Composable
fun TvimTheme(
    primaryColor: Color = RedAccent,
    nothingDesign: Boolean = false,
    content: @Composable () -> Unit
) {
    val activeColor = if (nothingDesign) Color(0xFFFF2433) else primaryColor
    val colorScheme = darkColorScheme(
        primary = activeColor,
        onPrimary = Color.Black,
        background = PureBlack,
        onBackground = Color.White,
        surface = DarkGray,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF242424),
        surfaceContainerHighest = DarkGray,
        secondary = DarkGray,
        onSecondary = activeColor
    )

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

    CompositionLocalProvider(LocalNothingDesign provides nothingDesign) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = if (nothingDesign) NothingTypography else Typography,
            content = content
        )
    }
}
