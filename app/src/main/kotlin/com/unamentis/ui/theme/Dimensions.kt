package com.unamentis.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * iOS-parity spacing and dimension system.
 *
 * Maps iOS point values to Android dp for visual consistency across platforms.
 * Use these constants throughout the app to maintain consistent spacing that
 * matches the iOS UnaMentis app.
 */
object Dimensions {
    // ==========================================================================
    // SCREEN PADDING (iOS: 20pt horizontal standard)
    // ==========================================================================
    val ScreenHorizontalPadding: Dp = 20.dp
    val ScreenVerticalPadding: Dp = 16.dp

    // ==========================================================================
    // STANDARD SPACING (iOS patterns)
    // ==========================================================================
    val SpacingXSmall: Dp = 4.dp
    val SpacingSmall: Dp = 8.dp
    val SpacingMedium: Dp = 12.dp
    val SpacingLarge: Dp = 16.dp
    val SpacingXLarge: Dp = 24.dp
    val SpacingXXLarge: Dp = 32.dp

    // ==========================================================================
    // CARD STYLING (iOS: RoundedRectangle(cornerRadius: 12))
    // ==========================================================================
    val CardCornerRadius: Dp = 12.dp
    val CardCornerRadiusLarge: Dp = 16.dp
    val CardCornerRadiusSmall: Dp = 8.dp
    val CardPadding: Dp = 16.dp
    val CardElevation: Dp = 0.dp // iOS cards don't have elevation shadows

    // ==========================================================================
    // STATUS BADGE / CAPSULE (iOS: Capsule().fill(.ultraThinMaterial))
    // ==========================================================================
    val CapsuleCornerRadius: Dp = 20.dp
    val BadgePaddingHorizontal: Dp = 12.dp
    val BadgePaddingVertical: Dp = 8.dp

    // ==========================================================================
    // PROGRESS BAR (iOS: 4pt height, 2pt corner radius)
    // ==========================================================================
    val ProgressBarHeight: Dp = 4.dp
    val ProgressBarCornerRadius: Dp = 2.dp

    // ==========================================================================
    // TOUCH TARGETS (Keep Android 48dp for accessibility - higher than iOS 44pt)
    // ==========================================================================
    val MinTouchTarget: Dp = 48.dp
    val IconButtonSize: Dp = 44.dp

    // ==========================================================================
    // BUTTON STYLING (iOS: 12pt corner radius)
    // ==========================================================================
    val ButtonCornerRadius: Dp = 12.dp
    val ButtonPaddingHorizontal: Dp = 16.dp
    val ButtonPaddingVertical: Dp = 12.dp
    val ButtonHeight: Dp = 44.dp

    // ==========================================================================
    // CONTROL BAR (iOS session controls at bottom)
    // ==========================================================================
    val ControlBarCornerRadius: Dp = 20.dp
    val ControlBarPadding: Dp = 16.dp
    val ControlBarBottomPadding: Dp = 34.dp // Safe area for home indicator

    // ==========================================================================
    // LIST / SECTION STYLING
    // ==========================================================================
    val ListItemPadding: Dp = 16.dp
    val SectionSpacing: Dp = 24.dp

    // ==========================================================================
    // ICON SIZES
    // ==========================================================================
    val IconSizeSmall: Dp = 16.dp
    val IconSizeMedium: Dp = 24.dp
    val IconSizeLarge: Dp = 32.dp
    val StatusDotSize: Dp = 12.dp

    // ==========================================================================
    // TRANSCRIPT / BUBBLE STYLING
    // ==========================================================================
    val BubbleCornerRadius: Dp = 16.dp
    val BubblePadding: Dp = 12.dp
    val BubbleMaxWidth: Dp = 300.dp
}
