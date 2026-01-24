package com.unamentis.ui.analytics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
 *
 * Note: Analytics is accessed directly via the Analytics tab in the bottom navigation.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AnalyticsScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    companion object {
        private const val DEFAULT_TIMEOUT = 15_000L
    }

    @Before
    fun setup() {
        hiltRule.inject()
    }

    /**
     * Navigate to Analytics via the Analytics tab.
     */
    private fun navigateToAnalytics() {
        // Wait for Analytics tab to be visible
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_analytics")
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Click Analytics tab directly
        composeTestRule.onNodeWithTag("nav_analytics").performClick()

        // Wait for navigation to complete
        composeTestRule.waitForIdle()
    }

    @Test
    fun analyticsScreen_navigateToAnalyticsTab_displaysScreen() {
        navigateToAnalytics()

        // Verify the screen title is displayed
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Analytics")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun analyticsScreen_displaysTimeRangeSection() {
        navigateToAnalytics()

        // Wait for screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Time Range")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify time range section is displayed
        composeTestRule.onNodeWithText("Time Range").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_displaysOverviewSection() {
        navigateToAnalytics()

        // Wait for screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Overview")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify overview section is displayed
        composeTestRule.onNodeWithText("Overview").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_displaysLatencyBreakdown() {
        navigateToAnalytics()

        // Wait for screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Latency Breakdown")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify latency breakdown section is displayed
        composeTestRule.onNodeWithText("Latency Breakdown").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_displaysCostBreakdown() {
        navigateToAnalytics()

        // Wait for LazyColumn to be available
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("AnalyticsLazyColumn")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to Cost Breakdown section
        composeTestRule.onNodeWithTag("AnalyticsLazyColumn")
            .performScrollToNode(hasText("Cost Breakdown"))

        // Verify cost breakdown section is displayed
        composeTestRule.onNodeWithText("Cost Breakdown").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_displaysSessionTrends() {
        navigateToAnalytics()

        // Wait for LazyColumn to be available
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("AnalyticsLazyColumn")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to Session Trends section (at the bottom)
        composeTestRule.onNodeWithTag("AnalyticsLazyColumn")
            .performScrollToNode(hasText("Session Trends"))

        // Verify session trends section is displayed
        composeTestRule.onNodeWithText("Session Trends").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_timeRangeChips_areClickable() {
        navigateToAnalytics()

        // Wait for screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
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
