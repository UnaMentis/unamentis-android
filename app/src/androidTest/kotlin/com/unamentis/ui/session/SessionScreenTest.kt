package com.unamentis.ui.session

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
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun sessionScreen_initialState_displaysScreen() {
        // Session tab should be the default tab
        // Verify the screen title is displayed
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Session").assertIsDisplayed()
    }

    @Test
    fun sessionScreen_displaysStartButton() {
        // Wait for screen to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Start Session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify start session button is displayed
        composeTestRule.onNodeWithText("Start Session").assertIsDisplayed()
    }

    @Test
    fun sessionScreen_displaysEmptyState() {
        // Wait for screen to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Start a session to begin")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify empty state message is displayed
        composeTestRule.onNodeWithText("Start a session to begin").assertIsDisplayed()
    }

    @Test
    fun sessionScreen_displaysStateIndicator() {
        // Wait for screen to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("IDLE")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify state indicator is displayed
        composeTestRule.onNodeWithText("IDLE").assertIsDisplayed()
    }

    @Test
    fun sessionScreen_navigateToOtherTabAndBack_preservesState() {
        // Navigate to Settings tab
        composeTestRule.onNodeWithText("Settings").performClick()

        // Wait for Settings screen to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Settings")
                .fetchSemanticsNodes().size > 1
        }

        // Navigate back to Session tab
        composeTestRule.onNodeWithText("Session").performClick()

        // Verify Session screen is displayed
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Start Session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Start Session").assertIsDisplayed()
    }
}
