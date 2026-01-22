package com.unamentis.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

    @Test
    fun settingsScreen_displaysOnDeviceAiSection() {
        // Navigate to Settings tab
        composeTestRule.onNodeWithText("Settings").performClick()

        // Wait for LazyColumn to be available
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Settings")
                .fetchSemanticsNodes().size > 1
        }

        // Scroll to On-Device AI section
        composeTestRule.onNodeWithTag("SettingsLazyColumn")
            .performScrollToNode(hasText("On-Device AI"))

        // Verify On-Device AI section is displayed
        composeTestRule.onNodeWithText("On-Device AI").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysOnDeviceAiModelsCard() {
        // Navigate to Settings tab
        composeTestRule.onNodeWithText("Settings").performClick()

        // Wait for LazyColumn to be available
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Settings")
                .fetchSemanticsNodes().size > 1
        }

        // Scroll to On-Device AI Models card
        composeTestRule.onNodeWithTag("SettingsLazyColumn")
            .performScrollToNode(hasText("On-Device AI Models"))

        // Verify On-Device AI Models card is displayed
        composeTestRule.onNodeWithText("On-Device AI Models").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysDeviceRamInfo() {
        // Navigate to Settings tab
        composeTestRule.onNodeWithText("Settings").performClick()

        // Wait for LazyColumn to be available
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Settings")
                .fetchSemanticsNodes().size > 1
        }

        // Scroll to Device RAM info
        composeTestRule.onNodeWithTag("SettingsLazyColumn")
            .performScrollToNode(hasText("Device RAM"))

        // Verify Device RAM info is displayed
        composeTestRule.onNodeWithText("Device RAM").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysAvailableModelsSection() {
        // Navigate to Settings tab
        composeTestRule.onNodeWithText("Settings").performClick()

        // Wait for LazyColumn to be available
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Settings")
                .fetchSemanticsNodes().size > 1
        }

        // Scroll to Available Models section
        composeTestRule.onNodeWithTag("SettingsLazyColumn")
            .performScrollToNode(hasText("Available Models"))

        // Verify Available Models section is displayed
        composeTestRule.onNodeWithText("Available Models").assertIsDisplayed()
    }
}
