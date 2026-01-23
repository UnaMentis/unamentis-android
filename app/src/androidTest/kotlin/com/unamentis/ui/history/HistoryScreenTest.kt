package com.unamentis.ui.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unamentis.MainActivity
import com.unamentis.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for HistoryScreen.
 *
 * Tests session history display, filtering, detail view, and export.
 * Uses Hilt for dependency injection to test with real ViewModels.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    companion object {
        private const val DEFAULT_TIMEOUT = 10_000L
        private const val LONG_TIMEOUT = 15_000L
    }

    @Before
    fun setup() {
        hiltRule.inject()
    }

    /**
     * Navigate to History tab using testTag.
     */
    private fun navigateToHistory() {
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_history")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_history").performClick()
    }

    @Test
    fun historyScreen_navigateToHistoryTab_displaysScreen() {
        navigateToHistory()

        val historyTitle = composeTestRule.activity.getString(R.string.tab_history)
        val noSessionsText = composeTestRule.activity.getString(R.string.history_no_sessions)

        // Wait for the History screen to load - check for either the title or empty state
        // These are History screen-specific elements, not just the navigation tab
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            val hasHistoryTitle =
                composeTestRule.onAllNodesWithText(historyTitle)
                    .fetchSemanticsNodes().isNotEmpty()
            val hasEmptyState =
                composeTestRule.onAllNodesWithText(noSessionsText)
                    .fetchSemanticsNodes().isNotEmpty()
            hasHistoryTitle || hasEmptyState
        }

        // Assert one of the History screen elements is visible
        try {
            composeTestRule.onAllNodesWithText(noSessionsText)
                .onFirst()
                .assertIsDisplayed()
        } catch (_: AssertionError) {
            // If empty state not found, check for the History title instead
            composeTestRule.onAllNodesWithText(historyTitle)
                .onFirst()
                .assertIsDisplayed()
        }
    }

    @Test
    fun historyScreen_displaysEmptyStateOrSessions() {
        navigateToHistory()

        val noSessionsText = composeTestRule.activity.getString(R.string.history_no_sessions)

        // Wait for screen to load - should either show sessions or empty state
        composeTestRule.waitUntil(LONG_TIMEOUT) {
            val hasEmptyState =
                composeTestRule.onAllNodesWithText(noSessionsText)
                    .fetchSemanticsNodes().isNotEmpty()
            // Check for session cards (they would have content like "Free Session" or turns count)
            val hasSessionContent =
                composeTestRule.onAllNodesWithText("turns")
                    .fetchSemanticsNodes().isNotEmpty()
            hasEmptyState || hasSessionContent
        }
    }
}
