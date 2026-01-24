package com.unamentis.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// =============================================================================
// iOS TYPOGRAPHY (for explicit iOS parity needs)
// =============================================================================

/**
 * iOS-matched typography styles for visual parity.
 *
 * iOS uses SF Pro system font with specific point sizes. We use Android's
 * default system font (Roboto) but match the iOS size hierarchy.
 *
 * Use these when you need exact iOS visual matching. For standard Material 3
 * usage, continue using [Typography].
 *
 * iOS Font Sizes Reference:
 * - largeTitle: 34pt
 * - title: 28pt
 * - title2: 22pt
 * - title3: 20pt
 * - headline: 17pt semibold
 * - body: 17pt
 * - callout: 16pt
 * - subheadline: 15pt
 * - footnote: 13pt
 * - caption: 12pt
 * - caption2: 11pt
 */
object IOSTypography {
    /** iOS largeTitle: 34pt */
    val largeTitle =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            lineHeight = 41.sp,
        )

    /** iOS title: 28pt */
    val title =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
        )

    /** iOS title2: 22pt semibold */
    val title2 =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        )

    /** iOS title3: 20pt */
    val title3 =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 25.sp,
        )

    /** iOS headline: 17pt semibold */
    val headline =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 22.sp,
        )

    /** iOS body: 17pt */
    val body =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 17.sp,
            lineHeight = 22.sp,
        )

    /** iOS callout: 16pt */
    val callout =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 21.sp,
        )

    /** iOS subheadline: 15pt */
    val subheadline =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        )

    /** iOS footnote: 13pt */
    val footnote =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )

    /** iOS caption: 12pt */
    val caption =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )

    /** iOS caption2: 11pt */
    val caption2 =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 14.sp,
        )
}

// =============================================================================
// MATERIAL 3 TYPOGRAPHY (default)
// =============================================================================

// Material 3 Typography
val Typography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 57.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.25).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = 0.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = 0.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
    )

// =============================================================================
// TEXTSTYLE EXTENSIONS (for iOS-style chaining like .subheadline.bold())
// =============================================================================

/**
 * Returns a copy of this TextStyle with bold weight.
 *
 * Matches iOS pattern: `.font(.subheadline.bold())`
 */
fun TextStyle.bold(): TextStyle = copy(fontWeight = FontWeight.Bold)

/**
 * Returns a copy of this TextStyle with the specified font weight.
 *
 * Matches iOS pattern: `.font(.subheadline.weight(.medium))`
 *
 * @param weight The font weight to apply
 */
fun TextStyle.weight(weight: FontWeight): TextStyle = copy(fontWeight = weight)

/**
 * Returns a copy of this TextStyle with italic style.
 *
 * Matches iOS pattern: `.italic()`
 */
fun TextStyle.italic(): TextStyle = copy(fontStyle = FontStyle.Italic)

/**
 * Returns a copy of this TextStyle with monospaced digits (tabular figures).
 *
 * Matches iOS pattern: `.monospacedDigit()`
 * Uses OpenType feature "tnum" for tabular (fixed-width) numbers.
 * This ensures digits align properly in columns and timers.
 */
fun TextStyle.monospacedDigit(): TextStyle =
    copy(
        fontFeatureSettings = "tnum",
    )

/**
 * Creates a serif TextStyle for quotes and special text.
 *
 * Matches iOS pattern: `.font(.system(size: 24, design: .serif))`
 *
 * @param size The font size in sp
 */
fun serifTextStyle(size: TextUnit): TextStyle =
    TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = size,
        fontStyle = FontStyle.Italic,
    )

/**
 * Returns a copy of this TextStyle with a custom font size.
 *
 * Matches iOS pattern: `.font(.system(size: 40))`
 *
 * @param size The font size in sp
 */
fun TextStyle.size(size: TextUnit): TextStyle = copy(fontSize = size)
