package org.capnav.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.capnav.app.data.settings.ThemeMode

/** Brand tokens (prompt §6). Screens never use raw colours; they go through these or the scheme. */
object Brand {
    val blue = Color(0xFF33CCFF)
    val blueDeep = Color(0xFF0A6CC4)
    val navy = Color(0xFF0B1F33)
    val purple = Color(0xFF8F5BFF)
    val mist = Color(0xFFE6F4FB)
    val white = Color(0xFFFFFFFF)

    // Neutrals tinted toward the brand blue instead of pure greys.
    val navy2 = Color(0xFF13304B)
    val navy3 = Color(0xFF1C3F60)
    val slate = Color(0xFF5A6E82)
    val slateLight = Color(0xFFB5C6D6)
    val ink = Color(0xFF0B1F33)
    val surfaceLight = Color(0xFFFFFFFF)
    val surfaceLight2 = Color(0xFFF3F9FC)
    val alternativeRoute = Color(0xFF8FA3B8)
}

/**
 * Semantic palette: traffic, danger and confirmation. Deliberately disjoint from the brand blue so
 * a route can never be confused with a traffic state. Each traffic level also has a pattern width.
 */
@Immutable
data class Semantic(
    val trafficFree: Color,
    val trafficLight: Color,
    val trafficSlow: Color,
    val trafficHeavy: Color,
    val trafficBlocked: Color,
    val danger: Color,
    val onDanger: Color,
    val warning: Color,
    val onWarning: Color,
    val success: Color,
    val onSuccess: Color,
)

val StandardSemantic = Semantic(
    trafficFree = Color(0xFF2E9E5B),
    trafficLight = Color(0xFFF2C200),
    trafficSlow = Color(0xFFF08A00),
    trafficHeavy = Color(0xFFE02D2D),
    trafficBlocked = Color(0xFF8E1010),
    danger = Color(0xFFC62828),
    onDanger = Color(0xFFFFFFFF),
    warning = Color(0xFFF2C200),
    onWarning = Color(0xFF0B1F33),
    success = Color(0xFF1E7E45),
    onSuccess = Color(0xFFFFFFFF),
)

/** Colour-blind friendly (blue/orange based) alternative for the traffic scale. */
val AccessibleSemantic = StandardSemantic.copy(
    trafficFree = Color(0xFF3D8FD1),
    trafficLight = Color(0xFFF7D774),
    trafficSlow = Color(0xFFE69F00),
    trafficHeavy = Color(0xFFD55E00),
    trafficBlocked = Color(0xFF5A1E00),
)

val LocalSemantic = staticCompositionLocalOf { StandardSemantic }

private val LightScheme = lightColorScheme(
    primary = Brand.blueDeep,
    onPrimary = Brand.white,
    primaryContainer = Brand.blue,
    onPrimaryContainer = Brand.navy,
    secondary = Brand.purple,
    onSecondary = Brand.white,
    secondaryContainer = Color(0xFFEDE4FF),
    onSecondaryContainer = Color(0xFF2D1366),
    background = Brand.mist,
    onBackground = Brand.ink,
    surface = Brand.surfaceLight,
    onSurface = Brand.ink,
    surfaceVariant = Brand.mist,
    onSurfaceVariant = Color(0xFF3E5468),
    surfaceContainer = Brand.surfaceLight2,
    surfaceContainerHigh = Brand.mist,
    surfaceContainerLow = Brand.surfaceLight,
    surfaceContainerLowest = Brand.surfaceLight,
    surfaceContainerHighest = Color(0xFFD7EAF4),
    outline = Color(0xFF8DA2B5),
    outlineVariant = Color(0xFFCFE0EB),
    error = StandardSemantic.danger,
    onError = Brand.white,
)

private val DarkScheme = darkColorScheme(
    primary = Brand.blue,
    onPrimary = Brand.navy,
    primaryContainer = Brand.blueDeep,
    onPrimaryContainer = Brand.white,
    secondary = Color(0xFFB79BFF),
    onSecondary = Color(0xFF240C5C),
    secondaryContainer = Color(0xFF4A2D99),
    onSecondaryContainer = Color(0xFFEDE4FF),
    background = Brand.navy,
    onBackground = Color(0xFFE3EEF6),
    surface = Brand.navy2,
    onSurface = Color(0xFFE3EEF6),
    surfaceVariant = Brand.navy3,
    onSurfaceVariant = Color(0xFFB5C6D6),
    surfaceContainer = Brand.navy2,
    surfaceContainerHigh = Brand.navy3,
    surfaceContainerLow = Brand.navy2,
    surfaceContainerLowest = Brand.navy,
    surfaceContainerHighest = Color(0xFF26507A),
    outline = Color(0xFF6F879E),
    outlineVariant = Color(0xFF2C4D6E),
    error = Color(0xFFFF8A80),
    onError = Brand.navy,
)

private val HighContrastLight = LightScheme.copy(
    onSurface = Color.Black, onBackground = Color.Black, onSurfaceVariant = Color(0xFF14202B), outline = Color(0xFF14202B),
)
private val HighContrastDark = DarkScheme.copy(
    onSurface = Color.White, onBackground = Color.White, onSurfaceVariant = Color.White, outline = Color.White,
)

/** Two radii only (prompt §6): small for controls, large for sheets/panels. */
object Radii {
    val small = 6.dp
    val large = 16.dp
}

private val CapShapes = Shapes(
    extraSmall = RoundedCornerShape(Radii.small),
    small = RoundedCornerShape(Radii.small),
    medium = RoundedCornerShape(Radii.small),
    large = RoundedCornerShape(Radii.large),
    extraLarge = RoundedCornerShape(Radii.large),
)

private val Sans = FontFamily.SansSerif

private val CapTypography = Typography(
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.AUTO -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun CapTheme(dark: Boolean, highContrast: Boolean, accessibleTraffic: Boolean, content: @Composable () -> Unit) {
    val scheme: ColorScheme = when {
        dark && highContrast -> HighContrastDark
        dark -> DarkScheme
        highContrast -> HighContrastLight
        else -> LightScheme
    }
    CompositionLocalProvider(LocalSemantic provides if (accessibleTraffic) AccessibleSemantic else StandardSemantic) {
        MaterialTheme(colorScheme = scheme, typography = CapTypography, shapes = CapShapes, content = content)
    }
}

/** Token pairs actually used as text/background; verified against WCAG AA by ContrastTest. */
object ContrastPairs {
    val pairs: List<Triple<String, Color, Color>> = listOf(
        Triple("navy on blue (primary button, light)", Brand.navy, Brand.blue),
        Triple("white on blueDeep (button)", Brand.white, Brand.blueDeep),
        Triple("ink on surface", Brand.ink, Brand.surfaceLight),
        Triple("ink on mist", Brand.ink, Brand.mist),
        Triple("white on purple-container (pause banner)", Brand.white, Color(0xFF4A2D99)),
        Triple("on-surface on navy2 (dark)", Color(0xFFE3EEF6), Brand.navy2),
        Triple("variant on navy2 (dark)", Color(0xFFB5C6D6), Brand.navy2),
        Triple("variant on surface (light)", Color(0xFF3E5468), Brand.surfaceLight),
        Triple("white on danger", StandardSemantic.onDanger, StandardSemantic.danger),
        Triple("navy on warning", StandardSemantic.onWarning, StandardSemantic.warning),
        Triple("white on success", StandardSemantic.onSuccess, StandardSemantic.success),
        Triple("blue on navy (dark primary text)", Brand.blue, Brand.navy),
        Triple("white on navy (maneuver banner)", Brand.white, Brand.navy),
    )
}
