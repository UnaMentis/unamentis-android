package com.unamentis.core.accessibility

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accessibility checker and helper.
 *
 * Monitors accessibility settings and provides utilities for
 * ensuring app complies with accessibility best practices.
 *
 * Features:
 * - TalkBack detection
 * - Font scale monitoring
 * - Touch exploration state
 * - Accessibility event helpers
 *
 * Accessibility Requirements:
 * - All interactive elements must have content descriptions
 * - Minimum touch target size: 48dp x 48dp
 * - Color contrast ratio: 4.5:1 for normal text, 3:1 for large text (WCAG AA)
 * - Support for large font sizes (up to 2x scale)
 * - Logical focus order
 * - Screen reader announcements for state changes
 */
@Singleton
class AccessibilityChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val accessibilityManager: AccessibilityManager? = context.getSystemService()

    /**
     * TalkBack enabled state.
     */
    private val _isTalkBackEnabled = MutableStateFlow(false)
    val isTalkBackEnabled: StateFlow<Boolean> = _isTalkBackEnabled.asStateFlow()

    /**
     * Touch exploration enabled state.
     */
    private val _isTouchExplorationEnabled = MutableStateFlow(false)
    val isTouchExplorationEnabled: StateFlow<Boolean> = _isTouchExplorationEnabled.asStateFlow()

    /**
     * Current font scale.
     */
    private val _fontScale = MutableStateFlow(1.0f)
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    init {
        updateAccessibilityState()
    }

    /**
     * Update accessibility state from system.
     */
    fun updateAccessibilityState() {
        _isTalkBackEnabled.value = isTalkBackActive()
        _isTouchExplorationEnabled.value = accessibilityManager?.isTouchExplorationEnabled ?: false
        _fontScale.value = context.resources.configuration.fontScale
    }

    /**
     * Check if TalkBack is active.
     */
    private fun isTalkBackActive(): Boolean {
        val isEnabled = accessibilityManager?.isEnabled ?: false
        val isTouchExplorationEnabled = accessibilityManager?.isTouchExplorationEnabled ?: false
        return isEnabled && isTouchExplorationEnabled
    }

    /**
     * Check if any accessibility service is enabled.
     */
    fun isAccessibilityEnabled(): Boolean {
        return accessibilityManager?.isEnabled ?: false
    }

    /**
     * Get recommended timeout for accessibility users.
     *
     * Accessibility users may need more time to interact with UI elements.
     */
    fun getRecommendedTimeoutMs(): Long {
        return if (_isTalkBackEnabled.value) {
            30000L // 30 seconds for TalkBack users
        } else {
            15000L // 15 seconds for regular users
        }
    }

    /**
     * Check if font scale is large.
     */
    fun isLargeFontScale(): Boolean {
        return _fontScale.value >= 1.5f
    }

    /**
     * Check if font scale is extra large.
     */
    fun isExtraLargeFontScale(): Boolean {
        return _fontScale.value >= 2.0f
    }

    /**
     * Get accessibility summary for debugging.
     */
    fun getAccessibilitySummary(): String {
        return buildString {
            appendLine("Accessibility Status:")
            appendLine("- TalkBack: ${if (_isTalkBackEnabled.value) "Enabled" else "Disabled"}")
            appendLine("- Touch Exploration: ${if (_isTouchExplorationEnabled.value) "Enabled" else "Disabled"}")
            appendLine("- Font Scale: ${_fontScale.value}x")
            appendLine("- Recommended Timeout: ${getRecommendedTimeoutMs()}ms")
        }
    }

    /**
     * Accessibility best practices checklist.
     */
    data class AccessibilityChecklist(
        val hasContentDescriptions: Boolean,
        val meetsMinimumTouchTargetSize: Boolean,
        val meetsColorContrastRatio: Boolean,
        val supportsLargeFonts: Boolean,
        val hasLogicalFocusOrder: Boolean,
        val hasStateAnnouncements: Boolean
    ) {
        fun isFullyAccessible(): Boolean {
            return hasContentDescriptions &&
                    meetsMinimumTouchTargetSize &&
                    meetsColorContrastRatio &&
                    supportsLargeFonts &&
                    hasLogicalFocusOrder &&
                    hasStateAnnouncements
        }

        fun getMissingRequirements(): List<String> {
            val missing = mutableListOf<String>()
            if (!hasContentDescriptions) missing.add("Content descriptions for interactive elements")
            if (!meetsMinimumTouchTargetSize) missing.add("48dp minimum touch target size")
            if (!meetsColorContrastRatio) missing.add("WCAG AA color contrast ratios")
            if (!supportsLargeFonts) missing.add("Support for large font sizes (2x)")
            if (!hasLogicalFocusOrder) missing.add("Logical focus order")
            if (!hasStateAnnouncements) missing.add("Screen reader announcements")
            return missing
        }
    }

    companion object {
        /**
         * Minimum touch target size in dp (WCAG 2.1 Level AAA).
         */
        const val MIN_TOUCH_TARGET_DP = 48

        /**
         * Minimum color contrast ratio for normal text (WCAG AA).
         */
        const val MIN_CONTRAST_RATIO_NORMAL = 4.5

        /**
         * Minimum color contrast ratio for large text (WCAG AA).
         */
        const val MIN_CONTRAST_RATIO_LARGE = 3.0

        /**
         * Calculate color contrast ratio.
         *
         * @param color1 First color (ARGB)
         * @param color2 Second color (ARGB)
         * @return Contrast ratio (1.0 to 21.0)
         */
        fun calculateContrastRatio(color1: Int, color2: Int): Double {
            val luminance1 = calculateLuminance(color1)
            val luminance2 = calculateLuminance(color2)

            val lighter = maxOf(luminance1, luminance2)
            val darker = minOf(luminance1, luminance2)

            return (lighter + 0.05) / (darker + 0.05)
        }

        /**
         * Calculate relative luminance of a color.
         */
        private fun calculateLuminance(color: Int): Double {
            val r = android.graphics.Color.red(color) / 255.0
            val g = android.graphics.Color.green(color) / 255.0
            val b = android.graphics.Color.blue(color) / 255.0

            val rLum = if (r <= 0.03928) r / 12.92 else Math.pow((r + 0.055) / 1.055, 2.4)
            val gLum = if (g <= 0.03928) g / 12.92 else Math.pow((g + 0.055) / 1.055, 2.4)
            val bLum = if (b <= 0.03928) b / 12.92 else Math.pow((b + 0.055) / 1.055, 2.4)

            return 0.2126 * rLum + 0.7152 * gLum + 0.0722 * bLum
        }

        /**
         * Check if contrast ratio meets WCAG AA standards.
         */
        fun meetsWCAGAA(foreground: Int, background: Int, isLargeText: Boolean = false): Boolean {
            val ratio = calculateContrastRatio(foreground, background)
            val minimumRatio = if (isLargeText) MIN_CONTRAST_RATIO_LARGE else MIN_CONTRAST_RATIO_NORMAL
            return ratio >= minimumRatio
        }
    }
}
