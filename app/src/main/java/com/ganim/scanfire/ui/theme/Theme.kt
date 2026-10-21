package com.ganim.scanfire.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ganim.scanfire.R

/*
 * "Ink and paper": a warm paper ground, deep ink text, one ember accent. Small text and buttons use
 * the deeper Ember700 (passes 4.5:1 on white); the bright Ember is for large actions (Scan).
 */
val Ink = Color(0xFF161A22)
val Paper = Color(0xFFF6F4EF)
val Ember = Color(0xFFE4572E)
val Ember700 = Color(0xFFC2410C)
val Muted = Color(0xFF5B6170)
val Line = Color(0xFFE4E1DA)
val Positive = Color(0xFF1E6B4A)

/** Soft tints for the quick-action icon tiles. */
object Tints {
    val ember = Color(0xFFFCE9E2) to Color(0xFFC2410C)
    val blue = Color(0xFFE8EEF7) to Color(0xFF1F4E8C)
    val green = Color(0xFFE9F3EE) to Color(0xFF1E6B4A)
    val violet = Color(0xFFF1ECF8) to Color(0xFF5B3E96)
    val emberDark = Color(0xFF3A231B) to Color(0xFFFFB59F)
    val blueDark = Color(0xFF1C2A3D) to Color(0xFFA9C7F0)
    val greenDark = Color(0xFF17302A) to Color(0xFF8FD3B3)
    val violetDark = Color(0xFF2A2340) to Color(0xFFC9B6F0)
}

/** Whether the active scheme is dark, for the few places that pick tints by hand. */
val LocalIsDark = staticCompositionLocalOf { false }

private val LightColors = lightColorScheme(
    primary = Ember700,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFCE9E2),
    onPrimaryContainer = Color(0xFF5A1A04),
    secondary = Ink,
    onSecondary = Paper,
    secondaryContainer = Ink,
    onSecondaryContainer = Paper,
    tertiary = Positive,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFEBE3),
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEFECE6),
    surfaceContainerHighest = Color(0xFFE8E4DC),
    outline = Color(0xFFCFC8BC),
    outlineVariant = Line,
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF9A78),
    onPrimary = Color(0xFF4A1503),
    primaryContainer = Color(0xFF5A2210),
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = Paper,
    onSecondary = Ink,
    secondaryContainer = Paper,
    onSecondaryContainer = Ink,
    tertiary = Color(0xFF8FD3B3),
    background = Color(0xFF12151C),
    onBackground = Color(0xFFF1EEE8),
    surface = Color(0xFF12151C),
    onSurface = Color(0xFFF1EEE8),
    surfaceVariant = Color(0xFF2B303B),
    onSurfaceVariant = Color(0xFFB9BDC6),
    surfaceContainerLowest = Color(0xFF0E1117),
    surfaceContainerLow = Color(0xFF1C2029),
    surfaceContainer = Color(0xFF1F2430),
    surfaceContainerHigh = Color(0xFF262B36),
    surfaceContainerHighest = Color(0xFF2E3440),
    outline = Color(0xFF4A505C),
    outlineVariant = Color(0xFF343A46),
)

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Bundled (OFL) so the look is identical offline and on first launch. Variable weights on API 26+. */
val Display = FontFamily(
    variable(R.font.plus_jakarta_sans, 500), variable(R.font.plus_jakarta_sans, 600),
    variable(R.font.plus_jakarta_sans, 700), variable(R.font.plus_jakarta_sans, 800),
)
val Body = FontFamily(
    variable(R.font.inter, 400), variable(R.font.inter, 500), variable(R.font.inter, 600), variable(R.font.inter, 700),
)

private val AppTypography = Typography().let { t ->
    fun TextStyle.display(weight: Int) = copy(fontFamily = Display, fontWeight = FontWeight(weight))
    fun TextStyle.body(weight: Int = 400) = copy(fontFamily = Body, fontWeight = FontWeight(weight))
    Typography(
        displayLarge = t.displayLarge.display(800),
        displayMedium = t.displayMedium.display(800),
        displaySmall = t.displaySmall.display(700),
        headlineLarge = t.headlineLarge.display(700).copy(letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.display(700).copy(fontSize = 28.sp, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.display(700),
        titleLarge = t.titleLarge.display(700).copy(fontSize = 20.sp),
        titleMedium = t.titleMedium.display(700).copy(fontSize = 17.sp),
        titleSmall = t.titleSmall.body(600).copy(fontSize = 15.sp),
        bodyLarge = t.bodyLarge.body(),
        bodyMedium = t.bodyMedium.body().copy(fontSize = 14.sp),
        bodySmall = t.bodySmall.body().copy(fontSize = 13.sp),
        labelLarge = t.labelLarge.body(600).copy(fontSize = 15.sp),
        labelMedium = t.labelMedium.body(600).copy(fontSize = 12.5.sp),
        labelSmall = t.labelSmall.body(600),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * [themeMode]: 0 follows the system, 1 is light, 2 is dark. Dynamic (wallpaper) colour is opt-in
 * so the brand look stays recognisable by default.
 */
@Composable
fun ScanFireTheme(
    themeMode: Int = 0,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
