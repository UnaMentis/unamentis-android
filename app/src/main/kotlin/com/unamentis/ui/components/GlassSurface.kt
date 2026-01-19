package com.unamentis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.unamentis.ui.theme.Dimensions

/**
 * Glass morphism surface mimicking iOS ultraThinMaterial.
 *
 * Provides a frosted glass effect for status badges, control bars,
 * and floating UI elements - matching iOS visual language.
 *
 * iOS uses `.ultraThinMaterial` which is a semi-transparent blur effect.
 * On Android, we approximate this with a semi-transparent background
 * since real blur requires expensive RenderEffect (API 31+).
 *
 * @param modifier Modifier to apply to the surface
 * @param cornerRadius Corner radius for the surface shape
 * @param content Content to display inside the glass surface
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = Dimensions.CardCornerRadius,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()

    // iOS ultraThinMaterial approximation
    // Light mode: white with high opacity for subtle glass effect
    // Dark mode: dark gray with lower opacity for depth
    val backgroundColor =
        if (isDark) {
            Color.White.copy(alpha = 0.08f)
        } else {
            Color.White.copy(alpha = 0.75f)
        }

    // Subtle border for glass edge definition
    val borderColor =
        if (isDark) {
            Color.White.copy(alpha = 0.12f)
        } else {
            Color.Black.copy(alpha = 0.05f)
        }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(backgroundColor)
                .border(
                    width = 0.5.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(cornerRadius),
                ),
        content = content,
    )
}

/**
 * Capsule-shaped glass surface for status badges.
 *
 * Matches iOS pattern: Capsule().fill(.ultraThinMaterial)
 * Used for session status indicators, metrics badges, and tags.
 *
 * @param modifier Modifier to apply to the capsule
 * @param content Content to display inside the capsule
 */
@Composable
fun GlassCapsule(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        cornerRadius = Dimensions.CapsuleCornerRadius,
        content = content,
    )
}

/**
 * Thin material surface for control bars and panels.
 *
 * Provides a subtle backdrop for floating controls like the session
 * control bar at the bottom of the session screen.
 *
 * @param modifier Modifier to apply to the surface
 * @param content Content to display inside the surface
 */
@Composable
fun ThinMaterialSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()

    // Slightly more opaque than GlassSurface for better contrast
    val backgroundColor =
        if (isDark) {
            Color(0xFF1C1C1E).copy(alpha = 0.85f)
        } else {
            Color.White.copy(alpha = 0.9f)
        }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(Dimensions.ControlBarCornerRadius))
                .background(backgroundColor),
        content = content,
    )
}
