package com.unamentis.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences for tracking onboarding state.
 *
 * Stores whether the user has completed (or skipped) the onboarding flow.
 * This determines whether to show the onboarding screens on app launch.
 *
 * Usage:
 * ```kotlin
 * // Check if onboarding should be shown
 * if (!onboardingPreferences.hasCompletedOnboarding()) {
 *     // Show onboarding
 * }
 *
 * // Mark onboarding as complete
 * onboardingPreferences.setOnboardingCompleted()
 * ```
 */
@Singleton
class OnboardingPreferences
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val PREFS_NAME = "unamentis_onboarding"
            private const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
        }

        private val prefs by lazy {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        /**
         * Check if the user has completed or skipped onboarding.
         *
         * @return true if onboarding has been completed, false if it should be shown
         */
        fun hasCompletedOnboarding(): Boolean = prefs.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false)

        /**
         * Mark onboarding as completed.
         *
         * Call this when the user finishes all onboarding pages or taps Skip.
         */
        fun setOnboardingCompleted() {
            prefs.edit().putBoolean(KEY_HAS_COMPLETED_ONBOARDING, true).apply()
        }

        /**
         * Reset onboarding state (for testing or re-showing onboarding).
         *
         * After calling this, [hasCompletedOnboarding] will return false.
         */
        fun resetOnboarding() {
            prefs.edit().putBoolean(KEY_HAS_COMPLETED_ONBOARDING, false).apply()
        }
    }
