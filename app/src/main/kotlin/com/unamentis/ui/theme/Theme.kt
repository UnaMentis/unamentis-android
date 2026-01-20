package com.unamentis.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
    lightColorScheme(
        primary = md_theme_light_primary,
        onPrimary = md_theme_light_onPrimary,
        primaryContainer = md_theme_light_primaryContainer,
        onPrimaryContainer = md_theme_light_onPrimaryContainer,
        secondary = md_theme_light_secondary,
        onSecondary = md_theme_light_onSecondary,
        secondaryContainer = md_theme_light_secondaryContainer,
        onSecondaryContainer = md_theme_light_onSecondaryContainer,
        tertiary = md_theme_light_tertiary,
        onTertiary = md_theme_light_onTertiary,
        tertiaryContainer = md_theme_light_tertiaryContainer,
        onTertiaryContainer = md_theme_light_onTertiaryContainer,
        error = md_theme_light_error,
        errorContainer = md_theme_light_errorContainer,
        onError = md_theme_light_onError,
        onErrorContainer = md_theme_light_onErrorContainer,
        background = md_theme_light_background,
        onBackground = md_theme_light_onBackground,
        surface = md_theme_light_surface,
        onSurface = md_theme_light_onSurface,
        surfaceVariant = md_theme_light_surfaceVariant,
        onSurfaceVariant = md_theme_light_onSurfaceVariant,
        outline = md_theme_light_outline,
        inverseOnSurface = md_theme_light_inverseOnSurface,
        inverseSurface = md_theme_light_inverseSurface,
        inversePrimary = md_theme_light_inversePrimary,
        surfaceTint = md_theme_light_surfaceTint,
        outlineVariant = md_theme_light_outlineVariant,
        scrim = md_theme_light_scrim,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = md_theme_dark_primary,
        onPrimary = md_theme_dark_onPrimary,
        primaryContainer = md_theme_dark_primaryContainer,
        onPrimaryContainer = md_theme_dark_onPrimaryContainer,
        secondary = md_theme_dark_secondary,
        onSecondary = md_theme_dark_onSecondary,
        secondaryContainer = md_theme_dark_secondaryContainer,
        onSecondaryContainer = md_theme_dark_onSecondaryContainer,
        tertiary = md_theme_dark_tertiary,
        onTertiary = md_theme_dark_onTertiary,
        tertiaryContainer = md_theme_dark_tertiaryContainer,
        onTertiaryContainer = md_theme_dark_onTertiaryContainer,
        error = md_theme_dark_error,
        errorContainer = md_theme_dark_errorContainer,
        onError = md_theme_dark_onError,
        onErrorContainer = md_theme_dark_onErrorContainer,
        background = md_theme_dark_background,
        onBackground = md_theme_dark_onBackground,
        surface = md_theme_dark_surface,
        onSurface = md_theme_dark_onSurface,
        surfaceVariant = md_theme_dark_surfaceVariant,
        onSurfaceVariant = md_theme_dark_onSurfaceVariant,
        outline = md_theme_dark_outline,
        inverseOnSurface = md_theme_dark_inverseOnSurface,
        inverseSurface = md_theme_dark_inverseSurface,
        inversePrimary = md_theme_dark_inversePrimary,
        surfaceTint = md_theme_dark_surfaceTint,
        outlineVariant = md_theme_dark_outlineVariant,
        scrim = md_theme_dark_scrim,
    )

/**
 * Extended colors for UnaMentis app, matching iOS semantic colors.
 *
 * These colors provide semantic meaning beyond Material 3's color scheme,
 * ensuring visual parity with the iOS app.
 */
@Immutable
data class ExtendedColors(
    // Brand colors
    val brandNavy: Color = BrandNavy,
    val brandNavyDark: Color = BrandNavyDark,
    val brandTeal: Color = BrandTeal,
    val brandTealLight: Color = BrandTealLight,
    val brandCyanLight: Color = BrandCyanLight,
    // Semantic status colors (matching iOS)
    val success: Color = SuccessGreen,
    val warning: Color = WarningOrange,
    val info: Color = InfoBlue,
    val destructive: Color = DestructiveRed,
    val accent: Color = AccentPurple,
    // Session control colors (matching iOS SessionControlComponents.swift)
    val sessionStop: Color = SessionStopRed,
    val sessionStopLight: Color = SessionStopRedLight,
    val sessionStopMedium: Color = SessionStopRedMedium,
    val sessionPause: Color = SessionPauseBlue,
    val sessionPauseLight: Color = SessionPauseBlueLight,
    val mutedLight: Color = MutedRedLight,
    // Status indicator colors (matching iOS)
    val statusCompleted: Color = StatusCompleted,
    val statusPending: Color = StatusPending,
    val statusInProgress: Color = StatusInProgress,
    val statusFailed: Color = StatusFailed,
    // Speech detection colors (matching iOS)
    val speechDetected: Color = SpeechDetectedColor,
    val noSpeech: Color = NoSpeechColor,
    // Transcript bubble colors
    val userBubble: Color = UserBubbleLight,
    val assistantBubble: Color = AssistantBubbleLight,
    // Audio visualization colors
    val audioLevelLow: Color = AudioLevelLow,
    val audioLevelMedium: Color = AudioLevelMedium,
    val audioLevelHigh: Color = AudioLevelHigh,
    // Onboarding colors (matching iOS OnboardingView.swift)
    val onboardingWelcome: Color = OnboardingWelcome,
    val onboardingCurriculum: Color = OnboardingCurriculum,
    val onboardingOffline: Color = OnboardingOffline,
    val onboardingHandsFree: Color = OnboardingHandsFree,
)

private val LightExtendedColors =
    ExtendedColors(
        userBubble = UserBubbleLight,
        assistantBubble = AssistantBubbleLight,
    )

private val DarkExtendedColors =
    ExtendedColors(
        userBubble = UserBubbleDark,
        assistantBubble = AssistantBubbleDark,
    )

val LocalExtendedColors = staticCompositionLocalOf { ExtendedColors() }

/**
 * UnaMentis theme with support for light/dark modes and dynamic colors (Android 12+).
 *
 * This theme provides:
 * - Material 3 color scheme with brand colors
 * - Extended semantic colors matching iOS for visual parity
 * - Optional dynamic colors from system wallpaper (Android 12+)
 *
 * @param darkTheme Whether to use dark theme colors
 * @param dynamicColor Whether to use dynamic colors from the system (Android 12+ only).
 *                     Set to false to always use UnaMentis brand colors.
 * @param content The composable content to apply the theme to
 */
@Composable
fun UnaMentisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Default to brand colors for consistency with iOS
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

/**
 * Access extended colors from within a composable.
 *
 * Usage:
 * ```kotlin
 * val extendedColors = MaterialTheme.extendedColors
 * Box(modifier = Modifier.background(extendedColors.success))
 * ```
 */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current
