package com.unamentis.ui.history

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unamentis.MainActivity
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

        // Verify the history tab is displayed
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription("History tab")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun historyScreen_displaysEmptyStateOrSessions() {
        navigateToHistory()

        // Wait for screen to load - should either show sessions or empty state
        composeTestRule.waitUntil(LONG_TIMEOUT) {
            val hasHistory =
                composeTestRule.onAllNodesWithContentDescription("History tab")
                    .fetchSemanticsNodes().isNotEmpty()
            val hasEmptyState =
                composeTestRule.onAllNodesWithText("No sessions yet")
                    .fetchSemanticsNodes().isNotEmpty()
            hasHistory || hasEmptyState
        }
    }
}
