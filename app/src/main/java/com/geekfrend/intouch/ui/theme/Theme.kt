@file:OptIn(ExperimentalTextApi::class)

package com.geekfrend.intouch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
 * DESIGN TOKENS — two themes, chosen in Settings: "Drifter" (default; chosen Oct 16) and "Monument"
 *
 * Colors and the tile layout come from the Drifter direction (deep indigo night, neon signal
 * colors); the type comes from Monument (Josefin Sans for headings, names, labels and numbers;
 * Nunito Sans for body text). This file is the single place for the look. The widget can't read
 * Compose themes, so its colors live in res/values/colors.xml.
 */

/** The two looks the app offers (Settings › Theme). */
enum class AppTheme { DRIFTER, MONUMENT }

/** Raw palettes. Screens use [InTouchTheme.colors] or MaterialTheme, never these directly. */
private object Palette {
    // Drifter: deep indigo night with neon signal colors.
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

/** Monument (canvas row A): warm sand and plum, sage for on track, terracotta for overdue, violet for soon. */
private object MonumentPalette {
    val Sand = Color(0xFFF4E6D8)
    val Card = Color(0xFFFFF8F0)
    val Line = Color(0xFFE8D4C2)
    val LineStrong = Color(0xFFD6BFA9)
    val Plum = Color(0xFF3A2C4C)
    val Muted = Color(0xFF6E5F80)
    val Sage = Color(0xFF3D7A6C)
    val Terracotta = Color(0xFFB84A33)
    val Violet = Color(0xFF6F5A9E)
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
    /** True for a light theme (dark text and status-bar icons). */
    val light: Boolean = false,
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
    val tileHeight: Dp = 156.dp,
    val tileHeightCompact: Dp = 122.dp,
    /** Corner radius of tiles and panels. */
    val corner: Dp = 6.dp,
    /** Corner radius of buttons. */
    val buttonCorner: Dp = 4.dp,
)

private val DrifterColors = AppColors(
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

private val MonumentColors = AppColors(
    night = MonumentPalette.Sand,
    panel = MonumentPalette.Card,
    line = MonumentPalette.Line,
    lineStrong = MonumentPalette.LineStrong,
    text = MonumentPalette.Plum,
    muted = MonumentPalette.Muted,
    signal = MonumentPalette.Sage,
    fading = MonumentPalette.Terracotta,
    soon = MonumentPalette.Violet,
    light = true,
)

private fun scheme(c: AppColors, errorContainer: Color): ColorScheme {
    val base = if (c.light) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = c.signal,
        onPrimary = c.night,
        primaryContainer = c.panel,
        onPrimaryContainer = c.text,
        secondary = c.soon,
        onSecondary = c.night,
        secondaryContainer = c.line,
        onSecondaryContainer = c.text,
        tertiary = c.fading,
        onTertiary = c.night,
        background = c.night,
        onBackground = c.text,
        surface = c.night,
        onSurface = c.text,
        surfaceVariant = c.panel,
        onSurfaceVariant = c.muted,
        surfaceContainerLowest = c.night,
        surfaceContainerLow = c.panel,
        surfaceContainer = c.panel,
        surfaceContainerHigh = c.panel,
        surfaceContainerHighest = c.panel,
        outline = c.lineStrong,
        outlineVariant = c.line,
        error = c.fading,
        onError = c.night,
        errorContainer = errorContainer,
        onErrorContainer = c.text,
    )
}

private val DrifterScheme = scheme(DrifterColors, Color(0xFF3A1530))
private val MonumentScheme = scheme(MonumentColors, Color(0xFFF7D9CF))

/** Drifter is square-cut; Monument is soft and rounded, with pill buttons. */
private val DrifterSizes = Sizes()
private val MonumentSizes = Sizes(corner = 20.dp, buttonCorner = 22.dp)

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

private val DrifterShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

private val MonumentShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val LocalAppColors = staticCompositionLocalOf { DrifterColors }
private val LocalStatusColors = staticCompositionLocalOf { status(DrifterColors) }
private val LocalSpacing = staticCompositionLocalOf { Spacing() }
private val LocalSizes = staticCompositionLocalOf { Sizes() }

private fun status(c: AppColors) = StatusColors(overdue = c.fading, dueSoon = c.soon, onTrack = c.signal)

/** Wraps the app in the chosen look. Type is the same in both: Josefin Sans and Nunito Sans. */
@Composable
fun InTouchTheme(theme: AppTheme = AppTheme.DRIFTER, content: @Composable () -> Unit) {
    val colors = if (theme == AppTheme.MONUMENT) MonumentColors else DrifterColors
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalStatusColors provides status(colors),
        LocalSpacing provides Spacing(),
        LocalSizes provides if (theme == AppTheme.MONUMENT) MonumentSizes else DrifterSizes,
    ) {
        MaterialTheme(
            colorScheme = if (theme == AppTheme.MONUMENT) MonumentScheme else DrifterScheme,
            typography = AppTypography,
            shapes = if (theme == AppTheme.MONUMENT) MonumentShapes else DrifterShapes,
            content = content,
        )
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
