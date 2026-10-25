package cn.nascp.clash.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// One layout shared by the system light and dark palettes.
internal val LocalPlain = androidx.compose.runtime.staticCompositionLocalOf { true }
internal val LocalPearlGlass = androidx.compose.runtime.staticCompositionLocalOf { false }
internal val LocalSageGlass = androidx.compose.runtime.staticCompositionLocalOf { false }
private val Light = lightColorScheme(
    primary = Color(0xFF1766D6), onPrimary = Color.White,
    secondary = Color(0xFF7B55F5), tertiary = Color(0xFF16853C),
    background = Color(0xFFF4F4F4), onBackground = Color(0xFF171717),
    surface = Color.White, onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFEEEEEF), onSurfaceVariant = Color(0xFF727680),
    surfaceContainer = Color(0xFFF4F4F4), surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFEEEEEF),
    primaryContainer = Color(0xFFEAF3FF), onPrimaryContainer = Color(0xFF1766D6),
    secondaryContainer = Color(0xFFEAF3FF), onSecondaryContainer = Color(0xFF1766D6),
    outline = Color(0xFF898D94), outlineVariant = Color(0xFFEEEFF1))
private val Dark = darkColorScheme(
    primary = Color(0xFF8AB8FF), onPrimary = Color(0xFF002E68),
    secondary = Color(0xFFC3ADFF), tertiary = Color(0xFF80DB94),
    background = Color.Black, onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF232323), onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF171717), onSurfaceVariant = Color(0xFFA5A5AA),
    surfaceContainer = Color(0xFF171717), surfaceContainerHigh = Color(0xFF232323),
    surfaceContainerHighest = Color(0xFF353535),
    primaryContainer = Color(0xFF263B56), onPrimaryContainer = Color(0xFFBFD8FF),
    secondaryContainer = Color(0xFF353535), onSecondaryContainer = Color(0xFFF2F2F2),
    outline = Color(0xFF85858A), outlineVariant = Color(0xFF353535),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6))

@Composable fun ClashTheme(theme: String, language:String="zh", fontScale:Float=1f, content: @Composable () -> Unit) {
    val dark = when (theme) { "dark" -> true; "light", "plain" -> false; else -> isSystemInDarkTheme() }
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val density=LocalDensity.current
    CompositionLocalProvider(LocalPlain provides true,LocalLanguage provides language,LocalDensity provides Density(density.density,density.fontScale*fontScale)) {
    MaterialTheme(colorScheme = animatedColors(if (dark) Dark else Light),
        shapes = Shapes(extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)), typography = Typography(
        headlineMedium = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
        titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 23.sp, fontWeight = FontWeight.Bold),
        titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
     ).let { type -> type.copy(
        headlineMedium = type.headlineMedium.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = type.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
        titleMedium = type.titleMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        bodyLarge = type.bodyLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodyMedium = type.bodyMedium.copy(fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = type.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    ) }, content = content)
    }
}

@Composable private fun animatedColors(target: ColorScheme): ColorScheme {
    @Composable fun color(value: Color) = animateColorAsState(value, tween(220), label = "themeColor").value
    return target.copy(
        primary = color(target.primary),
        onPrimary = color(target.onPrimary),
        primaryContainer = color(target.primaryContainer),
        onPrimaryContainer = color(target.onPrimaryContainer),
        secondary = color(target.secondary),
        secondaryContainer = color(target.secondaryContainer),
        onSecondaryContainer = color(target.onSecondaryContainer),
        tertiary = color(target.tertiary),
        background = color(target.background),
        onBackground = color(target.onBackground),
        surface = color(target.surface),
        onSurface = color(target.onSurface),
        surfaceVariant = color(target.surfaceVariant),
        onSurfaceVariant = color(target.onSurfaceVariant),
        outline = color(target.outline),
        outlineVariant = color(target.outlineVariant),
        surfaceContainer = color(target.surfaceContainer),
        surfaceContainerHigh = color(target.surfaceContainerHigh),
        surfaceContainerHighest = color(target.surfaceContainerHighest)
    )
}
