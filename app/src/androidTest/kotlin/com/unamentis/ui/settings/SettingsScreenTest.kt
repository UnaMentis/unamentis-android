package com.unamentis.ui.settings

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
 * UI tests for SettingsScreen.
 *
 * Tests configuration management, provider selection, and user interactions.
 * Uses Hilt for dependency injection to test with real ViewModels.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun settingsScreen_navigateToSettingsTab_displaysScreen() {
        // Navigate to Settings tab
        composeTestRule.onNodeWithText("Settings").performClick()

        // Verify the screen title is displayed
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Settings")
                .fetchSemanticsNodes().size > 1
        }
    }

    @Test
    fun settingsScreen_displaysApiProvidersSection() {
        // Navigate to Settings tab
        composeTestRule.onNodeWithText("Settings").performClick()

        // Wait for screen to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("API Providers")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify API providers section is displayed
        composeTestRule.onNodeWithText("API Providers").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysServerSection() {
        // Navigate to Settings tab
        composeTestRule.onNodeWithText("Settings").performClick()

        // Wait for screen to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Server")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify server section is displayed
        composeTestRule.onNodeWithText("Server").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysVoiceSection() {
        // Navigate to Settings tab
        composeTestRule.onNodeWithText("Settings").performClick()

        // Wait for screen to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Voice")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify voice section is displayed
        composeTestRule.onNodeWithText("Voice").assertIsDisplayed()
    }
}
