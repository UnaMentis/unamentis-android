package com.unamentis.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Material 3 color palette for UnaMentis.
 *
 * Design System (matching iOS app):
 * - Primary: Deep Navy Blue (education, trust) - from iOS logo
 * - Secondary: Teal/Cyan (voice AI, engagement) - from iOS logo gradient
 * - Tertiary: Green (progress, success)
 * - Error: iOS system red
 * - Surface variants for depth
 *
 * Brand Colors (extracted from iOS logo):
 * - Deep Navy Blue: #1E3A5F to #003D7A
 * - Teal/Cyan: #00A884 to #4DB6AC
 * - Light Cyan: #B2EBF2 (highlights)
 *
 * Brand Colors (from iOS logo):
 */
val BrandNavyDark = Color(0xFF1E3A5F)
val BrandNavy = Color(0xFF003D7A)
val BrandTeal = Color(0xFF00A884)
val BrandTealLight = Color(0xFF4DB6AC)
val BrandCyanLight = Color(0xFFB2EBF2)

// =============================================================================
// iOS SYSTEM COLORS (for feature parity)
// =============================================================================
val iOSBlue = Color(0xFF007AFF)
val iOSGreen = Color(0xFF34C759)
val iOSRed = Color(0xFFFF3B30)
val iOSOrange = Color(0xFFFF9500)
val iOSYellow = Color(0xFFFFCC00)
val iOSPurple = Color(0xFFA855F7)
val iOSCyan = Color(0xFF00BCD4)
val iOSGray = Color(0xFF8E8E93)
val iOSGray2 = Color(0xFFAEAEB2)
val iOSGray3 = Color(0xFFC7C7CC)
val iOSGray4 = Color(0xFFD1D1D6)
val iOSGray5 = Color(0xFFE5E5EA)
val iOSGray6 = Color(0xFFF2F2F7)

// Dark mode iOS grays
val iOSGrayDark = Color(0xFF8E8E93)
val iOSGray2Dark = Color(0xFF636366)
val iOSGray3Dark = Color(0xFF48484A)
val iOSGray4Dark = Color(0xFF3A3A3C)
val iOSGray5Dark = Color(0xFF2C2C2E)
val iOSGray6Dark = Color(0xFF1C1C1E)

// =============================================================================
// LIGHT THEME COLORS
// =============================================================================
val md_theme_light_primary = BrandNavy
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFD1E4FF)
val md_theme_light_onPrimaryContainer = Color(0xFF001D36)

val md_theme_light_secondary = BrandTeal
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFB2EBF2)
val md_theme_light_onSecondaryContainer = Color(0xFF00363D)

val md_theme_light_tertiary = iOSGreen
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFD4F8D4)
val md_theme_light_onTertiaryContainer = Color(0xFF002204)

val md_theme_light_error = iOSRed
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF410002)

val md_theme_light_background = Color(0xFFFFFFFF)
val md_theme_light_onBackground = Color(0xFF1A1C1E)

val md_theme_light_surface = Color(0xFFFFFFFF)
val md_theme_light_onSurface = Color(0xFF1A1C1E)
val md_theme_light_surfaceVariant = iOSGray6
val md_theme_light_onSurfaceVariant = Color(0xFF43474E)

val md_theme_light_outline = iOSGray
val md_theme_light_inverseOnSurface = Color(0xFFF1F0F4)
val md_theme_light_inverseSurface = Color(0xFF2F3033)
val md_theme_light_inversePrimary = Color(0xFF9ECAFF)

val md_theme_light_surfaceTint = BrandNavy
val md_theme_light_outlineVariant = iOSGray4
val md_theme_light_scrim = Color(0xFF000000)

// =============================================================================
// DARK THEME COLORS
// =============================================================================
val md_theme_dark_primary = Color(0xFF9ECAFF)
val md_theme_dark_onPrimary = BrandNavyDark
val md_theme_dark_primaryContainer = Color(0xFF00497D)
val md_theme_dark_onPrimaryContainer = Color(0xFFD1E4FF)

val md_theme_dark_secondary = BrandTealLight
val md_theme_dark_onSecondary = Color(0xFF003D43)
val md_theme_dark_secondaryContainer = Color(0xFF004F58)
val md_theme_dark_onSecondaryContainer = BrandCyanLight

val md_theme_dark_tertiary = Color(0xFF7ADC77)
val md_theme_dark_onTertiary = Color(0xFF00390A)
val md_theme_dark_tertiaryContainer = Color(0xFF005313)
val md_theme_dark_onTertiaryContainer = Color(0xFF96F990)

val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)

val md_theme_dark_background = iOSGray6Dark
val md_theme_dark_onBackground = Color(0xFFE2E2E6)

val md_theme_dark_surface = iOSGray6Dark
val md_theme_dark_onSurface = Color(0xFFE2E2E6)
val md_theme_dark_surfaceVariant = iOSGray4Dark
val md_theme_dark_onSurfaceVariant = Color(0xFFC3C7CF)

val md_theme_dark_outline = iOSGrayDark
val md_theme_dark_inverseOnSurface = iOSGray6Dark
val md_theme_dark_inverseSurface = Color(0xFFE2E2E6)
val md_theme_dark_inversePrimary = BrandNavy

val md_theme_dark_surfaceTint = Color(0xFF9ECAFF)
val md_theme_dark_outlineVariant = iOSGray3Dark
val md_theme_dark_scrim = Color(0xFF000000)

// =============================================================================
// SEMANTIC COLORS (matching iOS usage)
// =============================================================================
val SuccessGreen = iOSGreen
val WarningOrange = iOSOrange
val InfoBlue = iOSBlue
val DestructiveRed = iOSRed
val AccentPurple = iOSPurple

// Session control colors (matching iOS SessionControlComponents.swift)
val SessionStopRed = iOSRed
val SessionStopRedLight = iOSRed.copy(alpha = 0.15f)
val SessionStopRedMedium = iOSRed.copy(alpha = 0.3f)
val SessionPauseBlue = iOSBlue
val SessionPauseBlueLight = iOSBlue.copy(alpha = 0.4f)
val MutedRedLight = iOSRed.copy(alpha = 0.15f)

// Transcript bubble colors
val UserBubbleLight = Color(0xFFE3F2FD)
val UserBubbleDark = BrandNavyDark
val AssistantBubbleLight = iOSGray6
val AssistantBubbleDark = iOSGray5Dark

// Audio level visualization
val AudioLevelLow = iOSGreen
val AudioLevelMedium = Color(0xFFFFEB3B)
val AudioLevelHigh = iOSRed

// Speech detection indicators (matching iOS)
val SpeechDetectedColor = iOSGreen
val NoSpeechColor = iOSGray

// Status indicator colors (matching iOS)
val StatusCompleted = iOSGreen
val StatusPending = iOSOrange
val StatusInProgress = iOSBlue
val StatusFailed = iOSRed

// Session state colors (matching iOS SessionView.swift statusColor)
val SessionStateIdle = iOSGray
val SessionStateUserSpeaking = iOSGreen
val SessionStateAIThinking = iOSOrange
val SessionStateAISpeaking = iOSBlue
val SessionStateInterrupted = Color(0xFFFFCC00) // iOS yellow
val SessionStatePaused = Color(0xFF00BCD4) // iOS cyan
val SessionStateProcessing = iOSPurple
val SessionStateError = iOSRed

// Onboarding page accent colors (matching iOS OnboardingView.swift)
val OnboardingWelcome = iOSBlue
val OnboardingCurriculum = iOSOrange
val OnboardingOffline = iOSGreen
val OnboardingHandsFree = iOSPurple

// =============================================================================
// IOS SYSTEM BACKGROUND COLORS (for semantic mapping)
// =============================================================================
// These map to iOS Color(.systemBackground), Color(.systemGray6), etc.
// Use these for consistent background treatment across the app

/** iOS systemBackground equivalent - pure white in light, near-black in dark */
val iOSSystemBackground = Color(0xFFFFFFFF)
val iOSSystemBackgroundDark = Color(0xFF000000)

/** iOS systemGroupedBackground equivalent - slightly off-white in light */
val iOSSystemGroupedBackground = iOSGray6
val iOSSystemGroupedBackgroundDark = Color(0xFF000000)
