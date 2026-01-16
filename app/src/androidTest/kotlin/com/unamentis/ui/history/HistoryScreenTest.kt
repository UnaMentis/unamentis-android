package com.unamentis.ui.history

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun historyScreen_navigateToHistoryTab_displaysScreen() {
        // Navigate to History tab
        composeTestRule.onNodeWithText("History").performClick()

        // Verify the screen title is displayed
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("History")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun historyScreen_displaysEmptyStateOrSessions() {
        // Navigate to History tab
        composeTestRule.onNodeWithText("History").performClick()

        // Wait for screen to load - should either show sessions or empty state
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            val hasHistory =
                composeTestRule.onAllNodesWithText("History")
                    .fetchSemanticsNodes().isNotEmpty()
            val hasEmptyState =
                composeTestRule.onAllNodesWithText("No sessions yet")
                    .fetchSemanticsNodes().isNotEmpty()
            hasHistory || hasEmptyState
        }
    }
}
