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
    val SpacingXXSmall: Dp = 2.dp
    val SpacingXSmall: Dp = 4.dp
    val SpacingSmall: Dp = 8.dp
    val SpacingMedium: Dp = 12.dp
    val SpacingLarge: Dp = 16.dp
    val SpacingXLarge: Dp = 24.dp
    val SpacingXXLarge: Dp = 32.dp
    val SpacingXXXLarge: Dp = 40.dp

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
    // TRANSCRIPT / BUBBLE STYLING (iOS: 14pt corner radius for bubbles)
    // ==========================================================================
    val BubbleCornerRadius: Dp = 14.dp // Changed from 16dp to match iOS
    val BubblePadding: Dp = 12.dp
    val BubblePaddingHorizontal: Dp = 14.dp
    val BubblePaddingVertical: Dp = 10.dp
    val BubbleMaxWidth: Dp = 300.dp

    // ==========================================================================
    // SESSION BUTTON SIZES (iOS: SessionControlButton)
    // ==========================================================================

    /** Session start button when idle - large */
    val SessionButtonSizeIdle: Dp = 80.dp

    /** Session button icon size when idle */
    val SessionButtonIconSizeIdle: Dp = 32.dp

    /** Session stop button when active - smaller */
    val SessionButtonSizeActive: Dp = 50.dp

    /** Session button icon size when active */
    val SessionButtonIconSizeActive: Dp = 20.dp

    // ==========================================================================
    // VU METER / AUDIO LEVEL (iOS: AudioLevelView)
    // ==========================================================================
    val VuMeterHeight: Dp = 40.dp
    val VuMeterBarWidth: Dp = 8.dp
    val VuMeterBarSpacing: Dp = 4.dp

    // ==========================================================================
    // VISUAL ASSETS (iOS: VisualAssetView, MapAssetView)
    // ==========================================================================
    val VisualAssetHeightCompact: Dp = 120.dp
    val VisualAssetHeightStandard: Dp = 150.dp
    val VisualAssetHeightMedium: Dp = 200.dp
    val VisualAssetHeightLarge: Dp = 250.dp

    /** Side panel width for iPad/tablet layout */
    val VisualAssetSidePanelWidth: Dp = 340.dp

    // ==========================================================================
    // EMPTY STATE
    // ==========================================================================
    val EmptyStateIconSize: Dp = 64.dp

    // ==========================================================================
    // TOOLBAR / TOP APP BAR
    // ==========================================================================
    val ToolbarIconSpacing: Dp = 12.dp

    // ==========================================================================
    // SWIPE ACTION
    // ==========================================================================
    val SwipeActionIconSize: Dp = 24.dp
    val SwipeActionThreshold: Dp = 56.dp

    // ==========================================================================
    // ONBOARDING
    // ==========================================================================
    val OnboardingIconSize: Dp = 80.dp

    // ==========================================================================
    // SLIDE TO STOP BUTTON (iOS: SlideToStopButton)
    // ==========================================================================
    val SlideThumbSize: Dp = 44.dp
    val SlideTrackPadding: Dp = 4.dp
    val SlideTrackMaxWidth: Dp = 280.dp

    // ==========================================================================
    // SESSION CONTROL BUTTONS (iOS: SessionControlComponents)
    // ==========================================================================
    val SessionMuteButtonSize: Dp = 44.dp
    val SessionPauseButtonSize: Dp = 50.dp
    val CurriculumNavButtonSize: Dp = 44.dp

    // ==========================================================================
    // TOPIC PROGRESS BAR
    // ==========================================================================
    val TopicProgressHeight: Dp = 4.dp
    val TopicProgressCornerRadius: Dp = 2.dp

    // ==========================================================================
    // AUDIO LEVEL VIEW (VU METER)
    // ==========================================================================
    const val AudioLevelBarCount: Int = 20
    val AudioLevelBarCornerRadius: Dp = 2.dp

    // ==========================================================================
    // CURRICULUM COMPONENTS (iOS: CurriculumView, TopicRow)
    // ==========================================================================

    /** Curriculum icon size in list rows - iOS 44x44 points */
    val CurriculumIconSize: Dp = 44.dp

    /** Status icon size - iOS 32x32 points */
    val StatusIconSize: Dp = 32.dp

    /** Status icon size large (for detail views) - iOS 48x48 scaled */
    val StatusIconSizeLarge: Dp = 48.dp

    /** Download progress bar height - iOS 8pt */
    val DownloadProgressHeight: Dp = 8.dp

    /** Download progress bar corner radius - iOS 4pt */
    val DownloadProgressCornerRadius: Dp = 4.dp
}
