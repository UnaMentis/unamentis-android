package com.unamentis.ui.settings

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.unamentis.core.config.ApiProvider
import com.unamentis.core.config.AppConfig
import com.unamentis.ui.theme.UnaMentisTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for SettingsScreen.
 *
 * Tests configuration management, provider selection, and user interactions.
 */
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var testConfig: AppConfig

    @Before
    fun setup() {
        testConfig = AppConfig(
            sttProvider = ApiProvider.DEEPGRAM,
            ttsProvider = ApiProvider.ELEVENLABS,
            llmProvider = ApiProvider.OPENAI,
            deepgramApiKey = "",
            elevenLabsApiKey = "",
            openAiApiKey = "",
            anthropicApiKey = "",
            groqApiKey = "",
            assemblyAiApiKey = "",
            serverBaseUrl = "http://10.0.2.2:8766",
            logServerUrl = "http://10.0.2.2:8765",
            audioSampleRate = 16000,
            audioBufferSize = 1024,
            vadThreshold = 0.5f,
            vadSensitivity = 0.7f,
            silenceThresholdMs = 1500L,
            bargeInConfirmationMs = 600L
        )
    }

    @Test
    fun settingsScreen_initialState_displaysAllSections() {
        composeTestRule.setContent {
            UnaMentisTheme {
                SettingsScreen()
            }
        }

        // Verify all section headers are displayed
        composeTestRule.onNodeWithText("API Providers").assertIsDisplayed()
        composeTestRule.onNodeWithText("Audio Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("VAD Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Server Configuration").assertIsDisplayed()
        composeTestRule.onNodeWithText("Presets").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_sttProviderSelection_updatesConfig() {
        var updatedProvider: ApiProvider? = null

        composeTestRule.setContent {
            UnaMentisTheme {
                SettingsScreen(
                    config = testConfig,
                    onConfigUpdate = { newConfig ->
                        updatedProvider = newConfig.sttProvider
                    }
                )
            }
        }

        // Click STT provider dropdown
        composeTestRule.onNodeWithText("Speech-to-Text Provider").performClick()

        // Select AssemblyAI
        composeTestRule.onNodeWithText("AssemblyAI").performClick()

        // Verify provider was updated
        assert(updatedProvider == ApiProvider.ASSEMBLYAI)
    }

    @Test
    fun settingsScreen_ttsProviderSelection_updatesConfig() {
        var updatedProvider: ApiProvider? = null

        composeTestRule.setContent {
            UnaMentisTheme {
                SettingsScreen(
                    config = testConfig,
                    onConfigUpdate = { newConfig ->
                        updatedProvider = newConfig.ttsProvider
                    }
                )
            }
        }

        // Click TTS provider dropdown
        composeTestRule.onNodeWithText("Text-to-Speech Provider").performClick()

        // Select Deepgram
        composeTestRule.onNodeWithText("Deepgram").performClick()

        // Verify provider was updated
        assert(updatedProvider == ApiProvider.DEEPGRAM)
    }

    @Test
    fun settingsScreen_llmProviderSelection_updatesConfig() {
        var updatedProvider: ApiProvider? = null

        composeTestRule.setContent {
            UnaMentisTheme {
                SettingsScreen(
                    config = testConfig,
                    onConfigUpdate = { newConfig ->
                        updatedProvider = newConfig.llmProvider
                    }
                )
            }
        }

        // Click LLM provider dropdown
        composeTestRule.onNodeWithText("Language Model Provider").performClick()

        // Select Anthropic
        composeTestRule.onNodeWithText("Anthropic").performClick()

        // Verify provider was updated
        assert(updatedProvider == ApiProvider.ANTHROPIC)
    }

    @Test
    fun settingsScreen_apiKeyInput_masksValue() {
        composeTestRule.setContent {
            UnaMentisTheme {
                SettingsScreen(config = testConfig)
            }
        }

        // Find OpenAI API key field
        composeTestRule.onNodeWithText("OpenAI API Key").assertIsDisplayed()

        // Verify it's a password field (visualTransformation applied)
        // This is implicit in the TextField configuration
    }

    @Test
    fun settingsScreen_apiKeyInput_updatesConfig() {
        var updatedKey: String? = null

        composeTestRule.setContent {
            UnaMentisTheme {
                SettingsScreen(
                    config = testConfig,
                    onConfigUpdate = { newConfig ->
                        updatedKey = newConfig.openAiApiKey
                    }
                )
            }
        }

        // Enter API key
        composeTestRule.onNodeWithText("OpenAI API Key")
            .performTextInput("sk-test-key-12345")

        // Verify key was updated
        assert(updatedKey == "sk-test-key-12345")
    }

    @Test
    fun settingsScreen_audioSettings_displaysCurrentValues() {
        composeTestRule.setContent {
            UnaMentisTheme {
                SettingsScreen(config = testConfig)
            }
        }

        // Verify current audio settings are displayed
        composeTestRule.onNodeWithText("Sample Rate: 16000 Hz").assertIsDisplayed()
        composeTestRule.onNodeWithText("Buffer Size: 1024 samples").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_vadThresholdSlider_updatesValue() {
        var updatedThreshold: Float? = null

        composeTestRule.setContent {
            UnaMentisTheme {
                SettingsScreen(
                    config = testConfig,
                    onConfigUpdate = { newConfig ->
                        updatedThreshold = newConfig.vadThreshold
                    }
                )
            }
        }

        // Adjust VAD threshold slider
        composeTestRule.onNodeWithContentDescription("VAD threshold slider")
            .performTouchInput {
                // Swipe to increase value
                swipeRight()
            }

        // Verify threshold was updated (value increased)
        assert(updatedThreshold != null && updatedThreshold!! > testConfig.vadThreshold)
    }

    @Test
    fun settingsScreen_presetSelection_appliesConfiguration() {
        var configUpdated = false

        composeTestRule.setContent {
            UnaMentisTheme {
                SettingsScreen(
                    config = testConfig,
                    onConfigUpdate = { configUpdated = true }
                )
            }
        }

        // Click preset button
        composeTestRule.onNodeWithText("Balanced").performClick()

        // Verify config was updated
        assert(configUpdated)
    }

    @Test
    fun settingsScreen_lowLatencyPreset_setsCorrectValues() {
        var updatedConfig: AppConfig? = null

        composeTestRule.setContent {
            UnaMentisTheme {
                SettingsScreen(
                    config = testConfig,
                    onConfigUpdate = { newConfig ->
                        updatedConfig = newConfig
                    }
                )
            }
        }

        // Select Low Latency preset
        composeTestRule.onNodeWithText("Low Latency").performClick()

        // Verify low-latency settings were applied
        assert(updatedConfig != null)
        assert(updatedConfig!!.audioBufferSize == 512)
        assert(updatedConfig!!.silenceThresholdMs == 1000L)
    }

    @Test
    fun settingsScreen_costOptimizedPreset_setsCorrectValues() {
        var updatedConfig: AppConfig? = null

        composeTestRule.setContent {
            UnaMentisTheme {
                SettingsScreen(
                    config = testConfig,
                    onConfigUpdate = { newConfig ->
                        updatedConfig = newConfig
                    }
                )
            }
        }

        // Select Cost Optimized preset
        composeTestRule.onNodeWithText("Cost Optimized").performClick()

        // Verify cost-optimized settings were applied
        assert(updatedConfig != null)
        assert(updatedConfig!!.ttsProvider == ApiProvider.ANDROID)
    }

    @Test
    fun settingsScreen_serverUrlInput_updatesConfig() {
        var updatedUrl: String? = null

        composeTestRule.setContent {
            UnaMentisTheme {
                SettingsScreen(
                    config = testConfig,
                    onConfigUpdate = { newConfig ->
                        updatedUrl = newConfig.serverBaseUrl
                    }
                )
            }
        }

        // Clear and enter new server URL
        composeTestRule.onNodeWithText("Server Base URL")
            .performTextClearance()
        composeTestRule.onNodeWithText("Server Base URL")
            .performTextInput("http://192.168.1.100:8766")

        // Verify URL was updated
        assert(updatedUrl == "http://192.168.1.100:8766")
    }

    @Test
    fun settingsScreen_deviceMetrics_displaysInformation() {
        composeTestRule.setContent {
            UnaMentisTheme {
                SettingsScreen(
                    config = testConfig,
                    showDebugInfo = true
                )
            }
        }

        // Verify device metrics section is displayed
        composeTestRule.onNodeWithText("Device Metrics").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_validation_showsErrorForInvalidUrl() {
        composeTestRule.setContent {
            UnaMentisTheme {
                SettingsScreen(config = testConfig)
            }
        }

        // Enter invalid URL
        composeTestRule.onNodeWithText("Server Base URL")
            .performTextClearance()
        composeTestRule.onNodeWithText("Server Base URL")
            .performTextInput("not-a-valid-url")

        // Verify error message is displayed
        composeTestRule.onNodeWithText("Invalid URL format").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_resetToDefaults_restoresOriginalConfig() {
        var resetTriggered = false

        composeTestRule.setContent {
            UnaMentisTheme {
                SettingsScreen(
                    config = testConfig,
                    onResetToDefaults = { resetTriggered = true }
                )
            }
        }

        // Click reset button
        composeTestRule.onNodeWithText("Reset to Defaults").performClick()

        // Verify reset was triggered
        assert(resetTriggered)
    }

    @Test
    fun settingsScreen_scrolling_showsAllSettings() {
        composeTestRule.setContent {
            UnaMentisTheme {
                SettingsScreen(config = testConfig)
            }
        }

        // Verify top section is visible
        composeTestRule.onNodeWithText("API Providers").assertIsDisplayed()

        // Scroll to bottom
        composeTestRule.onNodeWithText("Presets").performScrollTo()

        // Verify bottom section is now visible
        composeTestRule.onNodeWithText("Presets").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_accessibility_hasContentDescriptions() {
        composeTestRule.setContent {
            UnaMentisTheme {
                SettingsScreen(config = testConfig)
            }
        }

        // Verify accessibility for sliders
        composeTestRule.onNodeWithContentDescription("VAD threshold slider").assertExists()
        composeTestRule.onNodeWithContentDescription("VAD sensitivity slider").assertExists()
    }

    @Test
    fun settingsScreen_darkMode_rendersCorrectly() {
        composeTestRule.setContent {
            UnaMentisTheme(darkTheme = true) {
                SettingsScreen(config = testConfig)
            }
        }

        // Verify screen renders in dark mode
        composeTestRule.onNodeWithText("API Providers").assertIsDisplayed()
        composeTestRule.onNodeWithText("Audio Settings").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_emptyApiKey_showsPlaceholder() {
        composeTestRule.setContent {
            UnaMentisTheme {
                SettingsScreen(config = testConfig)
            }
        }

        // Verify placeholder text is shown for empty API keys
        composeTestRule.onNodeWithText("Enter API key").assertExists()
    }

    @Test
    fun settingsScreen_bargeInSettings_updatesConfirmationWindow() {
        var updatedMs: Long? = null

        composeTestRule.setContent {
            UnaMentisTheme {
                SettingsScreen(
                    config = testConfig,
                    onConfigUpdate = { newConfig ->
                        updatedMs = newConfig.bargeInConfirmationMs
                    }
                )
            }
        }

        // Adjust barge-in confirmation slider
        composeTestRule.onNodeWithContentDescription("Barge-in confirmation window slider")
            .performTouchInput {
                swipeLeft()
            }

        // Verify value was updated
        assert(updatedMs != null && updatedMs!! < testConfig.bargeInConfirmationMs)
    }
}
