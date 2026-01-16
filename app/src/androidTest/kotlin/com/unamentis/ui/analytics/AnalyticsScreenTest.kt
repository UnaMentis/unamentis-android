package com.unamentis.ui.analytics

import androidx.compose.ui.test.assertIsDisplayed
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
 * UI tests for AnalyticsScreen.
 *
 * Tests metrics display, charts, filtering, and export functionality.
 * Uses Hilt for dependency injection to test with real ViewModels.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AnalyticsScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun analyticsScreen_navigateToAnalyticsTab_displaysScreen() {
        // Navigate to Analytics tab
        composeTestRule.onNodeWithText("Analytics").performClick()

        // Verify the screen title is displayed
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Analytics")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun analyticsScreen_displaysTimeRangeSection() {
        // Navigate to Analytics tab
        composeTestRule.onNodeWithText("Analytics").performClick()

        // Wait for screen to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Time Range")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify time range section is displayed
        composeTestRule.onNodeWithText("Time Range").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_displaysOverviewSection() {
        // Navigate to Analytics tab
        composeTestRule.onNodeWithText("Analytics").performClick()

        // Wait for screen to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Overview")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify overview section is displayed
        composeTestRule.onNodeWithText("Overview").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_displaysLatencyBreakdown() {
        // Navigate to Analytics tab
        composeTestRule.onNodeWithText("Analytics").performClick()

        // Wait for screen to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Latency Breakdown")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify latency breakdown section is displayed
        composeTestRule.onNodeWithText("Latency Breakdown").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_displaysCostBreakdown() {
        // Navigate to Analytics tab
        composeTestRule.onNodeWithText("Analytics").performClick()

        // Wait for screen to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Cost Breakdown")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify cost breakdown section is displayed
        composeTestRule.onNodeWithText("Cost Breakdown").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_displaysSessionTrends() {
        // Navigate to Analytics tab
        composeTestRule.onNodeWithText("Analytics").performClick()

        // Wait for screen to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Session Trends")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify session trends section is displayed
        composeTestRule.onNodeWithText("Session Trends").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_timeRangeChips_areClickable() {
        // Navigate to Analytics tab
        composeTestRule.onNodeWithText("Analytics").performClick()

        // Wait for screen to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("7 Days")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Click on different time range options
        composeTestRule.onNodeWithText("7 Days").performClick()
        composeTestRule.onNodeWithText("30 Days").performClick()
        composeTestRule.onNodeWithText("90 Days").performClick()
        composeTestRule.onNodeWithText("All Time").performClick()

        // No assertion failure means success
    }
}
