@file:OptIn(ExperimentalTextApi::class)

package com.geekfrend.intouch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.geekfrend.intouch.R

/*
 * DESIGN TOKENS — "Drifter × Monument" (chosen Oct 16)
 *
 * Colors and the tile layout come from the Drifter direction (deep indigo night, neon signal
 * colors); the type comes from Monument (Josefin Sans for headings, names, labels and numbers;
 * Nunito Sans for body text). This file is the single place for the look. The widget can't read
 * Compose themes, so its colors live in res/values/colors.xml.
 */

/** Raw palette. Screens use [InTouchTheme.colors] or MaterialTheme, never these directly. */
private object Palette {
    val Night = Color(0xFF110E24)
    val Panel = Color(0xFF1B1736)
    val Line = Color(0xFF2C2650)
    val LineStrong = Color(0xFF3A3368)
    val Text = Color(0xFFEEE8FF)
    val Muted = Color(0xFFA49CC8)
    val Signal = Color(0xFF43E5D6) // strong signal, primary actions
    val Fading = Color(0xFFFF5C9E) // overdue
    val Soon = Color(0xFFFFC65C) // due soon
}

@Immutable
data class AppColors(
    val night: Color,
    val panel: Color,
    val line: Color,
    val lineStrong: Color,
    val text: Color,
    val muted: Color,
    val signal: Color,
    val fading: Color,
    val soon: Color,
)

/** Colors for Overdue / Due soon / On track. */
@Immutable
data class StatusColors(val overdue: Color, val dueSoon: Color, val onTrack: Color)

@Immutable
data class Spacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
)

@Immutable
data class Sizes(
    val avatarSmall: Dp = 44.dp,
    val avatarMedium: Dp = 56.dp,
    val avatarLarge: Dp = 72.dp,
    val listBottomPadding: Dp = 88.dp,
    val tileHeight: Dp = 150.dp,
    val tileHeightCompact: Dp = 118.dp,
    /** Corner radius of tiles and panels. */
    val corner: Dp = 6.dp,
    /** Corner radius of buttons. */
    val buttonCorner: Dp = 4.dp,
)

private val AppColorsValue = AppColors(
    night = Palette.Night,
    panel = Palette.Panel,
    line = Palette.Line,
    lineStrong = Palette.LineStrong,
    text = Palette.Text,
    muted = Palette.Muted,
    signal = Palette.Signal,
    fading = Palette.Fading,
    soon = Palette.Soon,
)

private val Status = StatusColors(overdue = Palette.Fading, dueSoon = Palette.Soon, onTrack = Palette.Signal)

private val Scheme = darkColorScheme(
    primary = Palette.Signal,
    onPrimary = Palette.Night,
    primaryContainer = Palette.Panel,
    onPrimaryContainer = Palette.Text,
    secondary = Palette.Soon,
    onSecondary = Palette.Night,
    secondaryContainer = Palette.Line,
    onSecondaryContainer = Palette.Text,
    tertiary = Palette.Fading,
    onTertiary = Palette.Night,
    background = Palette.Night,
    onBackground = Palette.Text,
    surface = Palette.Night,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Panel,
    onSurfaceVariant = Palette.Muted,
    surfaceContainerLowest = Palette.Night,
    surfaceContainerLow = Palette.Panel,
    surfaceContainer = Palette.Panel,
    surfaceContainerHigh = Palette.Panel,
    surfaceContainerHighest = Palette.Panel,
    outline = Palette.LineStrong,
    outlineVariant = Palette.Line,
    error = Palette.Fading,
    onError = Palette.Night,
    errorContainer = Color(0xFF3A1530),
    onErrorContainer = Palette.Text,
)

/** Josefin Sans (variable font): headings, names, labels, numbers. */
private val Josefin = FontFamily(
    Font(R.font.josefin_sans, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.josefin_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.josefin_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.josefin_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** Nunito Sans (variable font): body text. */
private val Nunito = FontFamily(
    Font(R.font.nunito_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.nunito_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.nunito_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private fun heading(size: Int, weight: FontWeight, lineHeight: Int = (size * 1.15).toInt()) =
    TextStyle(fontFamily = Josefin, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp)

private fun body(size: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Nunito, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.4).toInt().sp)

/** Spaced capitals for small labels (the role pixel type played in the Drifter mockup). */
private fun label(size: Int) = TextStyle(
    fontFamily = Josefin,
    fontWeight = FontWeight.SemiBold,
    fontSize = size.sp,
    lineHeight = (size * 1.3).toInt().sp,
    letterSpacing = 0.18.em,
)

private val AppTypography = Typography(
    displayLarge = heading(48, FontWeight.Light),
    displayMedium = heading(40, FontWeight.Light),
    displaySmall = heading(34, FontWeight.Light),
    headlineLarge = heading(38, FontWeight.Light),
    headlineMedium = heading(30, FontWeight.Light),
    headlineSmall = heading(24, FontWeight.Normal),
    titleLarge = heading(22, FontWeight.SemiBold),
    titleMedium = heading(18, FontWeight.SemiBold),
    titleSmall = heading(15, FontWeight.SemiBold),
    bodyLarge = body(16),
    bodyMedium = body(14),
    bodySmall = body(13),
    labelLarge = heading(15, FontWeight.SemiBold),
    labelMedium = label(12),
    labelSmall = label(11),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

private val LocalAppColors = staticCompositionLocalOf { AppColorsValue }
private val LocalStatusColors = staticCompositionLocalOf { Status }
private val LocalSpacing = staticCompositionLocalOf { Spacing() }
private val LocalSizes = staticCompositionLocalOf { Sizes() }

/** The app is always dark: the night palette is the design. */
@Composable
fun InTouchTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppColors provides AppColorsValue,
        LocalStatusColors provides Status,
        LocalSpacing provides Spacing(),
        LocalSizes provides Sizes(),
    ) {
        MaterialTheme(colorScheme = Scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/** Access to the app's own tokens, next to MaterialTheme's colors, type and shapes. */
object InTouchTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
    val status: StatusColors
        @Composable @ReadOnlyComposable get() = LocalStatusColors.current
    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current
    val sizes: Sizes
        @Composable @ReadOnlyComposable get() = LocalSizes.current
}
