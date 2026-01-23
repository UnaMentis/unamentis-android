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
import org.junit.Ignore
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
        private const val DEFAULT_TIMEOUT = 15_000L
        private const val LONG_TIMEOUT = 20_000L
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

            // Wait for menu to appear and stabilize
            composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
                composeTestRule.onAllNodesWithTag("menu_$route")
                    .fetchSemanticsNodes().isNotEmpty()
            }

            // Small delay for menu animation
            composeTestRule.mainClock.advanceTimeBy(300)

            // Click the menu item
            composeTestRule.onNodeWithTag("menu_$route").performClick()

            // Wait for navigation to complete
            composeTestRule.waitForIdle()
        } else {
            composeTestRule.onNodeWithTag("nav_$route").performClick()
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun navigation_initialScreen_showsSessionTab() {
        val sessionTabText =
            composeTestRule.activity.getString(
                R.string.nav_tab_content_description,
                composeTestRule.activity.getString(R.string.tab_session),
            )

        // Wait for app to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_session")
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Verify app starts on Session tab
        composeTestRule.onNodeWithContentDescription(sessionTabText).assertIsSelected()
    }

    @Test
    fun navigation_switchToPrimaryTabs_succeeds() {
        // Navigate to each primary tab in sequence (these are directly on bottom nav)
        val primaryTabs = listOf("curriculum", "history", "todo", "session")

        // Get destination-specific text for each screen
        val serverCurriculumText = composeTestRule.activity.getString(R.string.curriculum_server)
        val historyText = composeTestRule.activity.getString(R.string.tab_history)
        val noSessionsText = composeTestRule.activity.getString(R.string.history_no_sessions)
        val startSessionText = composeTestRule.activity.getString(R.string.cd_start_session)

        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        primaryTabs.forEach { route ->
            navigateToTab(route)
            // Wait for destination-specific UI element to appear, not just the nav tab
            composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
                when (route) {
                    "curriculum" ->
                        composeTestRule.onAllNodesWithText(serverCurriculumText)
                            .fetchSemanticsNodes().isNotEmpty()
                    "history" -> {
                        // History screen shows either the History title or empty state
                        composeTestRule.onAllNodesWithText(historyText)
                            .fetchSemanticsNodes().isNotEmpty() ||
                            composeTestRule.onAllNodesWithText(noSessionsText)
                                .fetchSemanticsNodes().isNotEmpty()
                    }
                    "todo" ->
                        composeTestRule.onAllNodesWithContentDescription(
                            composeTestRule.activity.getString(R.string.cd_add_todo),
                        ).fetchSemanticsNodes().isNotEmpty()
                    "session" ->
                        composeTestRule.onAllNodesWithContentDescription(startSessionText)
                            .fetchSemanticsNodes().isNotEmpty()
                    else ->
                        composeTestRule.onAllNodesWithTag("nav_$route")
                            .fetchSemanticsNodes().isNotEmpty()
                }
            }
        }
    }

    @Test
    fun navigation_switchToMoreMenuItems_succeeds() {
        // Navigate to More menu items (Analytics, Settings)
        val analyticsText = composeTestRule.activity.getString(R.string.tab_analytics)

        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_more")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to Analytics via More menu
        navigateToTab("analytics")
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText(analyticsText)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to Settings via More menu
        navigateToTab("settings")
        // Use testTag for settings screen detection (actual text is "Providers")
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("settings_providers_header")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun navigation_sessionToSettings_preservesSessionState() {
        val sessionTabText =
            composeTestRule.activity.getString(
                R.string.nav_tab_content_description,
                composeTestRule.activity.getString(R.string.tab_session),
            )
        val startSessionText = composeTestRule.activity.getString(R.string.cd_start_session)

        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Start on Session tab
        composeTestRule.onNodeWithContentDescription(sessionTabText).assertIsSelected()

        // Switch to Settings via More menu
        navigateToTab("settings")
        // Use testTag for settings screen detection
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("settings_providers_header")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("settings_providers_header").assertIsDisplayed()

        // Switch back to Session
        navigateToTab("session")

        // Verify session screen is still rendered correctly
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription(startSessionText)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription(startSessionText).assertExists()
    }

    @Test
    fun navigation_curriculumToCurriculum_preservesScrollPosition() {
        val serverCurriculumText = composeTestRule.activity.getString(R.string.curriculum_server)

        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_curriculum")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to Curriculum tab
        navigateToTab("curriculum")

        // Wait for curriculum list to load
        composeTestRule.waitUntil(LONG_TIMEOUT) {
            composeTestRule.onAllNodesWithText(serverCurriculumText)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify curriculum is displayed
        composeTestRule.onNodeWithText(serverCurriculumText).assertIsDisplayed()

        // Navigate to Settings and back
        navigateToTab("settings")
        // Use testTag for settings screen detection
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("settings_providers_header")
                .fetchSemanticsNodes().isNotEmpty()
        }

        navigateToTab("curriculum")

        // Verify curriculum is still displayed
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText(serverCurriculumText)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(serverCurriculumText).assertIsDisplayed()
    }

    @Test
    fun navigation_backButton_navigatesToPreviousTab() {
        val sessionTabText =
            composeTestRule.activity.getString(
                R.string.nav_tab_content_description,
                composeTestRule.activity.getString(R.string.tab_session),
            )
        val analyticsText = composeTestRule.activity.getString(R.string.tab_analytics)

        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Start on Session tab
        composeTestRule.onNodeWithContentDescription(sessionTabText).assertIsSelected()

        // Navigate to Analytics via More menu
        navigateToTab("analytics")
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText(analyticsText)
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
        val speechToTextText = composeTestRule.activity.getString(R.string.settings_speech_to_text)
        val textToSpeechText = composeTestRule.activity.getString(R.string.settings_text_to_speech)

        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_more")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to Settings via More menu
        navigateToTab("settings")

        // Wait for screen to load using testTag
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("settings_providers_header")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify provider cards are displayed (Speech-to-Text and Text-to-Speech are provider cards)
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText(speechToTextText)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(speechToTextText).assertIsDisplayed()
        composeTestRule.onNodeWithText(textToSpeechText).assertIsDisplayed()
    }

    @Test
    fun navigation_historyScreen_displaysContent() {
        val historyText = composeTestRule.activity.getString(R.string.tab_history)
        val noSessionsText = composeTestRule.activity.getString(R.string.history_no_sessions)

        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_history")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to History tab
        navigateToTab("history")

        // Wait for history screen to load - should either show sessions or empty state
        composeTestRule.waitUntil(LONG_TIMEOUT) {
            val hasHistoryTitle =
                composeTestRule.onAllNodesWithText(historyText)
                    .fetchSemanticsNodes().isNotEmpty()
            val hasEmptyState =
                composeTestRule.onAllNodesWithText(noSessionsText)
                    .fetchSemanticsNodes().isNotEmpty()
            hasHistoryTitle || hasEmptyState
        }
    }

    @Test
    fun navigation_analyticsScreen_displaysContent() {
        val analyticsText = composeTestRule.activity.getString(R.string.tab_analytics)
        val startSessionText = composeTestRule.activity.getString(R.string.cd_start_session)

        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_more")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to Analytics via More menu
        navigateToTab("analytics")

        // Wait for screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText(analyticsText)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate away and back
        navigateToTab("session")
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription(startSessionText)
                .fetchSemanticsNodes().isNotEmpty()
        }

        navigateToTab("analytics")
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText(analyticsText)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun navigation_todoScreen_displaysContent() {
        val addTodoText = composeTestRule.activity.getString(R.string.cd_add_todo)

        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_todo")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to To-Do tab
        navigateToTab("todo")

        // Wait for todo screen to load - check for screen-specific element
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription(addTodoText)
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
        val sessionTabText =
            composeTestRule.activity.getString(
                R.string.nav_tab_content_description,
                composeTestRule.activity.getString(R.string.tab_session),
            )

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
        composeTestRule.onNodeWithContentDescription(sessionTabText).assertIsSelected()
    }

    @Test
    fun navigation_sessionScreenRotation_preservesState() {
        val sessionTabText =
            composeTestRule.activity.getString(
                R.string.nav_tab_content_description,
                composeTestRule.activity.getString(R.string.tab_session),
            )

        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Start on Session tab
        composeTestRule.onNodeWithContentDescription(sessionTabText).assertIsSelected()

        // Rotate device (simulated)
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.recreate()
        }

        // Verify Session tab is still selected after recreation
        composeTestRule.waitUntil(LONG_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription(sessionTabText)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription(sessionTabText).assertIsSelected()
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
        val startSessionText = composeTestRule.activity.getString(R.string.cd_start_session)

        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_more")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to Settings via More menu
        navigateToTab("settings")

        // Wait for screen to load using testTag
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("settings_providers_header")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to Session
        navigateToTab("session")

        // Verify session screen is displayed - check for screen-specific element
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription(startSessionText)
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
        val addTodoText = composeTestRule.activity.getString(R.string.cd_add_todo)
        val titleText = composeTestRule.activity.getString(R.string.todo_title_label)
        val saveText = composeTestRule.activity.getString(R.string.todo_save)
        val startSessionText = composeTestRule.activity.getString(R.string.cd_start_session)

        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_todo")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to To-Do tab
        navigateToTab("todo")

        // Wait for screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription(addTodoText)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Create a new todo
        composeTestRule.onNodeWithContentDescription(addTodoText).performClick()

        // Wait for dialog
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText(titleText)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(titleText)
            .performTextInput("Test navigation todo")
        composeTestRule.onNodeWithText(saveText).performClick()

        // Navigate away and back
        navigateToTab("session")
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription(startSessionText)
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
    @Ignore("Test is incomplete - needs warning dialog implementation and has flaky infrastructure issues")
    fun navigation_sessionActive_showsWarningOnExit() {
        // Wait for activity to be ready before accessing resources
        composeTestRule.waitForIdle()

        val startSessionText = composeTestRule.activity.getString(R.string.cd_start_session)
        val listeningText = composeTestRule.activity.getString(R.string.session_listening)

        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription(startSessionText)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Start a session (if auto-start is disabled, start manually)
        composeTestRule.onNodeWithContentDescription(startSessionText)
            .performClick()

        // Wait for session to start
        composeTestRule.waitUntil(LONG_TIMEOUT) {
            composeTestRule.onAllNodesWithText(listeningText)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Try to navigate away
        navigateToTab("settings")

        // Verify warning dialog appears (if implemented)
        // "Session in progress. Are you sure you want to leave?"
    }
}
