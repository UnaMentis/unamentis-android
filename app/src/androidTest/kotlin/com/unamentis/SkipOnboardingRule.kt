package com.unamentis

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Test rule that marks onboarding as completed before the Activity launches.
 *
 * Without this rule, tests using [MainActivity] will show the onboarding screen
 * instead of the main navigation, causing all UI element lookups to timeout
 * since the bottom navigation bar is hidden during onboarding.
 *
 * Usage: Add this rule between [HiltAndroidRule] (order 0) and the compose rule (order 2)
 * so it runs after Hilt setup but before the Activity is created.
 *
 * ```kotlin
 * @get:Rule(order = 0)
 * val hiltRule = HiltAndroidRule(this)
 *
 * @get:Rule(order = 1)
 * val skipOnboardingRule = SkipOnboardingRule()
 *
 * @get:Rule(order = 2)
 * val composeTestRule = createAndroidComposeRule<MainActivity>()
 * ```
 */
class SkipOnboardingRule : TestWatcher() {
    override fun starting(description: Description?) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAS_COMPLETED_ONBOARDING, true)
            .commit()
    }

    companion object {
        private const val PREFS_NAME = "unamentis_onboarding"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
    }
}
