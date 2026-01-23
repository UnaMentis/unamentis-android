package com.unamentis.ui.settings

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
 * UI tests for SettingsScreen.
 *
 * Tests configuration management, provider selection, and user interactions.
 * Uses Hilt for dependency injection to test with real ViewModels.
 *
 * Note: Settings is accessed via the "More" menu in the bottom navigation.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    companion object {
        private const val DEFAULT_TIMEOUT = 10_000L
    }

    @Before
    fun setup() {
        hiltRule.inject()
    }

    /**
     * Navigate to Settings via the More menu.
     */
    private fun navigateToSettings() {
        // Wait for bottom nav to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_more")
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Open More menu
        composeTestRule.onNodeWithTag("nav_more").performClick()
        // Wait for menu to appear
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("menu_settings")
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Click Settings
        composeTestRule.onNodeWithTag("menu_settings").performClick()
    }

    @Test
    fun settingsScreen_navigateToSettingsTab_displaysScreen() {
        navigateToSettings()

        // Verify the screen content is displayed
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("API Providers")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun settingsScreen_displaysApiProvidersSection() {
        navigateToSettings()

        // Wait for screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("API Providers")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify API providers section is displayed
        composeTestRule.onNodeWithText("API Providers").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysServerSection() {
        navigateToSettings()

        // Wait for screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Server")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify server section is displayed
        composeTestRule.onNodeWithText("Server").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysVoiceSection() {
        navigateToSettings()

        // Wait for screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Voice")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify voice section is displayed
        composeTestRule.onNodeWithText("Voice").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysOnDeviceAiSection() {
        navigateToSettings()

        // Wait for LazyColumn to be available
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("SettingsLazyColumn")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to On-Device AI section
        composeTestRule.onNodeWithTag("SettingsLazyColumn")
            .performScrollToNode(hasText("On-Device AI"))

        // Verify On-Device AI section is displayed
        composeTestRule.onNodeWithText("On-Device AI").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysOnDeviceAiModelsCard() {
        navigateToSettings()

        // Wait for LazyColumn to be available
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("SettingsLazyColumn")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to On-Device AI Models card
        composeTestRule.onNodeWithTag("SettingsLazyColumn")
            .performScrollToNode(hasText("On-Device AI Models"))

        // Verify On-Device AI Models card is displayed
        composeTestRule.onNodeWithText("On-Device AI Models").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysDeviceRamInfo() {
        navigateToSettings()

        // Wait for LazyColumn to be available
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("SettingsLazyColumn")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to Device RAM info
        composeTestRule.onNodeWithTag("SettingsLazyColumn")
            .performScrollToNode(hasText("Device RAM"))

        // Verify Device RAM info is displayed
        composeTestRule.onNodeWithText("Device RAM").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysAvailableModelsSection() {
        navigateToSettings()

        // Wait for LazyColumn to be available
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("SettingsLazyColumn")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to Available Models section
        composeTestRule.onNodeWithTag("SettingsLazyColumn")
            .performScrollToNode(hasText("Available Models"))

        // Verify Available Models section is displayed
        composeTestRule.onNodeWithText("Available Models").assertIsDisplayed()
    }
}
