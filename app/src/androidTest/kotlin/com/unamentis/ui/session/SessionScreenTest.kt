package com.unamentis.ui.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unamentis.CiTestConfig
import com.unamentis.MainActivity
import com.unamentis.SkipOnboardingRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for SessionScreen.
 *
 * Tests user interactions, state visualization, and transcript display.
 * Uses Hilt for dependency injection to test with real ViewModels.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SessionScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val skipOnboardingRule = SkipOnboardingRule()

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    /**
     * Navigate to Settings via the Settings tab.
     */
    private fun navigateToSettings() {
        composeTestRule.waitUntil(CiTestConfig.DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_settings")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_settings").performClick()
        composeTestRule.waitForIdle()
    }

    /**
     * Navigate to Session tab.
     */
    private fun navigateToSession() {
        composeTestRule.waitUntil(CiTestConfig.DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_session")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_session").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun sessionScreen_initialState_displaysScreen() {
        // Session tab should be the default tab
        // Verify the session tab is displayed using content description
        composeTestRule.waitUntil(CiTestConfig.DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription("Session tab")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithContentDescription("Session tab").assertIsDisplayed()
    }

    @Test
    fun sessionScreen_displaysStartButton() {
        // Wait for screen to load - button has content description, not visible text
        composeTestRule.waitUntil(CiTestConfig.DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription("Start session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify start session button is displayed
        composeTestRule.onNodeWithContentDescription("Start session").assertIsDisplayed()
    }

    @Test
    fun sessionScreen_displaysEmptyState() {
        // Wait for screen to load
        composeTestRule.waitUntil(CiTestConfig.DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Start a session to begin")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify empty state message is displayed
        composeTestRule.onNodeWithText("Start a session to begin").assertIsDisplayed()
    }

    @Test
    fun sessionScreen_displaysStateIndicator() {
        // Wait for screen to load - production shows "Ready" for IDLE state
        composeTestRule.waitUntil(CiTestConfig.DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Ready")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify state indicator is displayed
        composeTestRule.onNodeWithText("Ready").assertIsDisplayed()
    }

    @Test
    fun sessionScreen_navigateToOtherTabAndBack_preservesState() {
        // Navigate to Settings tab
        navigateToSettings()

        // Wait for Settings screen to load using testTag (more reliable)
        composeTestRule.waitUntil(CiTestConfig.LONG_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("settings_providers_header")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate back to Session tab
        navigateToSession()

        // Verify Session screen is displayed - use content description for button
        composeTestRule.waitUntil(CiTestConfig.DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription("Start session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithContentDescription("Start session").assertIsDisplayed()
    }
}
