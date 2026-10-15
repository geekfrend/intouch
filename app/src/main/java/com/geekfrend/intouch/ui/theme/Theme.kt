package com.geekfrend.intouch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * DESIGN TOKENS
 *
 * This file is the single place for the app's look: colors, status colors, type, shapes and spacing.
 * The visual design pass should only need to change values here (plus res/values/colors.xml and
 * res/values-night/colors.xml for the widget, which can't read Compose themes).
 * Screens never hard-code a color, font size or spacing value.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6B5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2EFDC),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4B635B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE8DD),
    onSecondaryContainer = Color(0xFF07201A),
    background = Color(0xFFF7FAF8),
    onBackground = Color(0xFF181C1B),
    surface = Color(0xFFF7FAF8),
    onSurface = Color(0xFF181C1B),
    surfaceVariant = Color(0xFFDBE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    outline = Color(0xFF6F7975),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF96D3C0),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF0F5244),
    onPrimaryContainer = Color(0xFFB2EFDC),
    secondary = Color(0xFFB1CCC1),
    onSecondary = Color(0xFF1D352E),
    secondaryContainer = Color(0xFF344C44),
    onSecondaryContainer = Color(0xFFCDE8DD),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0F1513),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBEC9C4),
    outline = Color(0xFF89938F),
    error = Color(0xFFFFB4AB),
)

/** Colors for Overdue / Due soon / On track. */
@Immutable
data class StatusColors(val overdue: Color, val dueSoon: Color, val onTrack: Color)

private val LightStatus = StatusColors(overdue = Color(0xFFBA1A1A), dueSoon = Color(0xFF8A5300), onTrack = Color(0xFF2E6B5C))
private val DarkStatus = StatusColors(overdue = Color(0xFFFFB4AB), dueSoon = Color(0xFFFFB95C), onTrack = Color(0xFF96D3C0))

@Immutable
data class Spacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
)

/** Avatar and touch-target sizes. */
@Immutable
data class Sizes(
    val avatarSmall: Dp = 44.dp,
    val avatarMedium: Dp = 56.dp,
    val avatarLarge: Dp = 72.dp,
    val listBottomPadding: Dp = 88.dp,
)

private val AppTypography = Typography()
private val AppShapes = Shapes()

private val LocalStatusColors = staticCompositionLocalOf { LightStatus }
private val LocalSpacing = staticCompositionLocalOf { Spacing() }
private val LocalSizes = staticCompositionLocalOf { Sizes() }

@Composable
fun InTouchTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalStatusColors provides if (dark) DarkStatus else LightStatus,
        LocalSpacing provides Spacing(),
        LocalSizes provides Sizes(),
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Access to the app's own tokens, next to MaterialTheme's colors, type and shapes. */
object InTouchTheme {
    val status: StatusColors
        @Composable @ReadOnlyComposable get() = LocalStatusColors.current
    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current
    val sizes: Sizes
        @Composable @ReadOnlyComposable get() = LocalSizes.current
}
