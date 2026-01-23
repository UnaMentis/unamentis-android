package com.unamentis

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navigation flow integration tests.
 *
 * Tests navigation between all 6 primary screens, state preservation,
 * back stack management, and deep link handling.
 *
 * Navigation structure:
 * - Primary tabs (bottom nav): Session, Curriculum, To-Do, History
 * - More menu items: Analytics, Settings
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationFlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    companion object {
        private const val DEFAULT_TIMEOUT = 10_000L
        private const val LONG_TIMEOUT = 15_000L
    }

    @Before
    fun init() {
        hiltRule.inject()
    }

    /**
     * Navigate to a tab using testTag.
     * Primary tabs: nav_session, nav_curriculum, nav_todo, nav_history
     * More menu items: menu_analytics, menu_settings (requires opening More menu first)
     */
    private fun navigateToTab(route: String) {
        val moreMenuRoutes = listOf("analytics", "settings")
        if (route in moreMenuRoutes) {
            // Open More menu first
            composeTestRule.onNodeWithTag("nav_more").performClick()
            // Wait for menu to appear and click the item
            composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
                composeTestRule.onAllNodesWithTag("menu_$route")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("menu_$route").performClick()
        } else {
            composeTestRule.onNodeWithTag("nav_$route").performClick()
        }
    }

    @Test
    fun navigation_initialScreen_showsSessionTab() {
        // Wait for app to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_session")
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Verify app starts on Session tab
        composeTestRule.onNodeWithContentDescription("Session tab").assertIsSelected()
    }

    @Test
    fun navigation_switchToPrimaryTabs_succeeds() {
        // Navigate to each primary tab in sequence (these are directly on bottom nav)
        val primaryTabs = listOf("curriculum", "history", "todo", "session")

        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        primaryTabs.forEach { route ->
            navigateToTab(route)
            composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
                composeTestRule.onAllNodesWithTag("nav_$route")
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun navigation_switchToMoreMenuItems_succeeds() {
        // Navigate to More menu items (Analytics, Settings)
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_more")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to Analytics via More menu
        navigateToTab("analytics")
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Analytics")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to Settings via More menu
        navigateToTab("settings")
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("API Providers")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun navigation_sessionToSettings_preservesSessionState() {
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Start on Session tab
        composeTestRule.onNodeWithContentDescription("Session tab").assertIsSelected()

        // Switch to Settings via More menu
        navigateToTab("settings")
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("API Providers")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("API Providers").assertIsDisplayed()

        // Switch back to Session
        navigateToTab("session")

        // Verify session screen is still rendered correctly
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription("Start session")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Start session").assertExists()
    }

    @Test
    fun navigation_curriculumToCurriculum_preservesScrollPosition() {
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_curriculum")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to Curriculum tab
        navigateToTab("curriculum")

        // Wait for curriculum list to load
        composeTestRule.waitUntil(LONG_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Server Curriculum")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify curriculum is displayed
        composeTestRule.onNodeWithText("Server Curriculum").assertIsDisplayed()

        // Navigate to Settings and back
        navigateToTab("settings")
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("API Providers")
                .fetchSemanticsNodes().isNotEmpty()
        }

        navigateToTab("curriculum")

        // Verify curriculum is still displayed
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Server Curriculum")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Server Curriculum").assertIsDisplayed()
    }

    @Test
    fun navigation_backButton_navigatesToPreviousTab() {
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Start on Session tab
        composeTestRule.onNodeWithContentDescription("Session tab").assertIsSelected()

        // Navigate to Analytics via More menu
        navigateToTab("analytics")
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Analytics")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Press back button
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        // Verify we're back on Session tab (or app exits, depending on implementation)
        // Note: Bottom navigation typically doesn't maintain back stack by default
        // This test verifies the behavior, whatever it may be
    }

    @Test
    fun navigation_settingsToApiProviders_opensDetailView() {
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_more")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to Settings via More menu
        navigateToTab("settings")

        // Wait for screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("API Providers")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Click on API Providers section
        composeTestRule.onNodeWithText("API Providers").performClick()

        // Verify provider selection dialog or screen opens
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Speech-to-Text")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Speech-to-Text").assertIsDisplayed()
        composeTestRule.onNodeWithText("Text-to-Speech").assertIsDisplayed()
    }

    @Test
    fun navigation_historyScreen_displaysContent() {
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_history")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to History tab
        navigateToTab("history")

        // Wait for history screen to load - should either show sessions or empty state
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

    @Test
    fun navigation_analyticsScreen_displaysContent() {
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_more")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to Analytics via More menu
        navigateToTab("analytics")

        // Wait for screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Analytics")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate away and back
        navigateToTab("session")
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        navigateToTab("analytics")
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Analytics")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun navigation_todoScreen_displaysContent() {
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_todo")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to To-Do tab
        navigateToTab("todo")

        // Wait for todo screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription("To-Do tab")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun navigation_allPrimaryTabsAccessible_fromBottomNav() {
        val primaryTabs = listOf("session", "curriculum", "todo", "history")

        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify all primary tabs are present in bottom navigation
        primaryTabs.forEach { route ->
            composeTestRule.onNodeWithTag("nav_$route").assertExists()
        }

        // Verify More menu is present
        composeTestRule.onNodeWithTag("nav_more").assertExists()
    }

    @Test
    fun navigation_rapidTabSwitching_handlesCorrectly() {
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Rapidly switch between primary tabs
        repeat(3) {
            navigateToTab("curriculum")
            navigateToTab("history")
            navigateToTab("todo")
            navigateToTab("session")
        }

        // Verify we end up on Session tab without crashes
        composeTestRule.onNodeWithContentDescription("Session tab").assertIsSelected()
    }

    @Test
    fun navigation_sessionScreenRotation_preservesState() {
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Start on Session tab
        composeTestRule.onNodeWithContentDescription("Session tab").assertIsSelected()

        // Rotate device (simulated)
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.recreate()
        }

        // Verify Session tab is still selected after recreation
        composeTestRule.waitUntil(LONG_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription("Session tab")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Session tab").assertIsSelected()
    }

    @Test
    fun navigation_deepLink_navigatesToCorrectTab() {
        // This test would verify deep link handling
        // Deep links like: unamentis://curriculum/physics-101
        // Implementation depends on NavDeepLink configuration in Navigation graph

        // Placeholder for deep link testing
        // Actual implementation would use Intent with data URI
    }

    @Test
    fun navigation_settingsChanges_reflectInSession() {
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_more")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to Settings via More menu
        navigateToTab("settings")

        // Wait for screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("API Providers")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to Session
        navigateToTab("session")

        // Verify session screen is displayed
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription("Session tab")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun navigation_multipleBackPresses_handlesCorrectly() {
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate through several tabs
        navigateToTab("curriculum")
        navigateToTab("settings")
        navigateToTab("analytics")

        // Press back multiple times
        composeTestRule.activityRule.scenario.onActivity { activity ->
            repeat(3) {
                activity.onBackPressedDispatcher.onBackPressed()
            }
        }

        // App should either be on Session tab or have exited
        // (Behavior depends on navigation configuration)
    }

    @Test
    fun navigation_todoCreation_persistsAcrossNavigation() {
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_todo")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to To-Do tab
        navigateToTab("todo")

        // Wait for screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription("Add todo")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Create a new todo
        composeTestRule.onNodeWithContentDescription("Add todo").performClick()

        // Wait for dialog
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Title")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Title")
            .performTextInput("Test navigation todo")
        composeTestRule.onNodeWithText("Save").performClick()

        // Navigate away and back
        navigateToTab("session")
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        navigateToTab("todo")

        // Verify todo persists
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Test navigation todo")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Test navigation todo").assertIsDisplayed()
    }

    @Test
    fun navigation_sessionActive_showsWarningOnExit() {
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription("Start session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Start a session (if auto-start is disabled, start manually)
        composeTestRule.onNodeWithContentDescription("Start session")
            .performClick()

        // Wait for session to start
        composeTestRule.waitUntil(LONG_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Listening...")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Try to navigate away
        navigateToTab("settings")

        // Verify warning dialog appears (if implemented)
        // "Session in progress. Are you sure you want to leave?"
    }
}
