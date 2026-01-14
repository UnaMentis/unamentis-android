package com.unamentis.ui.session

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.unamentis.data.model.SessionState
import com.unamentis.data.model.TranscriptEntry
import com.unamentis.ui.theme.UnaMentisTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for SessionScreen.
 *
 * Tests user interactions, state visualization, and transcript display.
 */
class SessionScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var sessionState: MutableStateFlow<SessionState>
    private lateinit var transcript: MutableStateFlow<List<TranscriptEntry>>
    private lateinit var isPaused: MutableStateFlow<Boolean>
    private lateinit var isMuted: MutableStateFlow<Boolean>
    private lateinit var audioLevel: MutableStateFlow<Float>

    @Before
    fun setup() {
        sessionState = MutableStateFlow(SessionState.IDLE)
        transcript = MutableStateFlow(emptyList())
        isPaused = MutableStateFlow(false)
        isMuted = MutableStateFlow(false)
        audioLevel = MutableStateFlow(0f)
    }

    @Test
    fun sessionScreen_initialState_showsEmptyState() {
        composeTestRule.setContent {
            UnaMentisTheme {
                SessionScreen()
            }
        }

        // Verify empty state is displayed
        composeTestRule.onNodeWithText("No active session").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start a session to begin").assertIsDisplayed()
    }

    @Test
    fun sessionScreen_startButton_startsSession() {
        var startClicked = false

        composeTestRule.setContent {
            UnaMentisTheme {
                SessionScreen(
                    onStartSession = { startClicked = true },
                )
            }
        }

        // Click start button
        composeTestRule.onNodeWithText("Start Session").performClick()

        // Verify callback was triggered
        assert(startClicked)
    }

    @Test
    fun sessionScreen_activeSession_displaysTranscript() {
        val testTranscript =
            listOf(
                TranscriptEntry(
                    id = "1",
                    sessionId = "session1",
                    role = "user",
                    text = "Hello, I want to learn about physics",
                    timestamp = System.currentTimeMillis(),
                ),
                TranscriptEntry(
                    id = "2",
                    sessionId = "session1",
                    role = "assistant",
                    text = "Great! Let's explore the fundamentals of physics together.",
                    timestamp = System.currentTimeMillis(),
                ),
            )

        composeTestRule.setContent {
            UnaMentisTheme {
                SessionScreen(
                    sessionState = SessionState.AI_SPEAKING,
                    transcript = testTranscript,
                )
            }
        }

        // Verify transcript is displayed
        composeTestRule.onNodeWithText("Hello, I want to learn about physics").assertIsDisplayed()
        composeTestRule.onNodeWithText("Great! Let's explore the fundamentals of physics together.").assertIsDisplayed()
    }

    @Test
    fun sessionScreen_pauseButton_pausesSession() {
        var pauseClicked = false

        composeTestRule.setContent {
            UnaMentisTheme {
                SessionScreen(
                    sessionState = SessionState.USER_SPEAKING,
                    isPaused = false,
                    onPauseSession = { pauseClicked = true },
                )
            }
        }

        // Click pause button
        composeTestRule.onNodeWithContentDescription("Pause session").performClick()

        // Verify callback was triggered
        assert(pauseClicked)
    }

    @Test
    fun sessionScreen_resumeButton_resumesSession() {
        var resumeClicked = false

        composeTestRule.setContent {
            UnaMentisTheme {
                SessionScreen(
                    sessionState = SessionState.PAUSED,
                    isPaused = true,
                    onResumeSession = { resumeClicked = true },
                )
            }
        }

        // Click resume button
        composeTestRule.onNodeWithContentDescription("Resume session").performClick()

        // Verify callback was triggered
        assert(resumeClicked)
    }

    @Test
    fun sessionScreen_muteButton_togglesMute() {
        var muteClicked = false

        composeTestRule.setContent {
            UnaMentisTheme {
                SessionScreen(
                    sessionState = SessionState.USER_SPEAKING,
                    isMuted = false,
                    onToggleMute = { muteClicked = true },
                )
            }
        }

        // Click mute button
        composeTestRule.onNodeWithContentDescription("Mute microphone").performClick()

        // Verify callback was triggered
        assert(muteClicked)
    }

    @Test
    fun sessionScreen_slideToStop_stopsSession() {
        var stopClicked = false

        composeTestRule.setContent {
            UnaMentisTheme {
                SessionScreen(
                    sessionState = SessionState.AI_SPEAKING,
                    onStopSession = { stopClicked = true },
                )
            }
        }

        // Perform swipe gesture on slide-to-stop button
        composeTestRule.onNodeWithContentDescription("Slide to stop session")
            .performTouchInput {
                swipeRight()
            }

        // Verify callback was triggered
        assert(stopClicked)
    }

    @Test
    fun sessionScreen_stateIndicator_displaysCorrectState() {
        val states =
            listOf(
                SessionState.IDLE to "Idle",
                SessionState.USER_SPEAKING to "Listening...",
                SessionState.PROCESSING_UTTERANCE to "Processing...",
                SessionState.AI_THINKING to "Thinking...",
                SessionState.AI_SPEAKING to "Speaking...",
                SessionState.INTERRUPTED to "Interrupted",
                SessionState.PAUSED to "Paused",
                SessionState.ERROR to "Error",
            )

        states.forEach { (state, expectedText) ->
            composeTestRule.setContent {
                UnaMentisTheme {
                    SessionScreen(sessionState = state)
                }
            }

            // Verify state indicator shows correct text
            composeTestRule.onNodeWithText(expectedText).assertIsDisplayed()
        }
    }

    @Test
    fun sessionScreen_audioLevel_visualizesAmplitude() {
        composeTestRule.setContent {
            UnaMentisTheme {
                SessionScreen(
                    sessionState = SessionState.USER_SPEAKING,
                    audioLevel = 0.75f,
                )
            }
        }

        // Verify audio level visualization exists
        composeTestRule.onNodeWithContentDescription("Audio level indicator").assertIsDisplayed()
    }

    @Test
    fun sessionScreen_transcript_scrollsToLatestMessage() {
        val longTranscript =
            List(20) { index ->
                TranscriptEntry(
                    id = index.toString(),
                    sessionId = "session1",
                    role = if (index % 2 == 0) "user" else "assistant",
                    text = "Message $index",
                    timestamp = System.currentTimeMillis() + index * 1000,
                )
            }

        composeTestRule.setContent {
            UnaMentisTheme {
                SessionScreen(
                    sessionState = SessionState.AI_SPEAKING,
                    transcript = longTranscript,
                )
            }
        }

        // Latest message should be visible (auto-scroll behavior)
        composeTestRule.onNodeWithText("Message 19").assertIsDisplayed()
    }

    @Test
    fun sessionScreen_errorState_displaysErrorMessage() {
        composeTestRule.setContent {
            UnaMentisTheme {
                SessionScreen(
                    sessionState = SessionState.ERROR,
                    errorMessage = "Connection lost. Please check your network.",
                )
            }
        }

        // Verify error message is displayed
        composeTestRule.onNodeWithText("Connection lost. Please check your network.").assertIsDisplayed()
    }

    @Test
    fun sessionScreen_metrics_displayLatencyAndCost() {
        composeTestRule.setContent {
            UnaMentisTheme {
                SessionScreen(
                    sessionState = SessionState.AI_SPEAKING,
                    e2eLatencyMs = 450L,
                    totalCostCents = 2.5,
                )
            }
        }

        // Verify metrics are displayed
        composeTestRule.onNodeWithText("450ms").assertIsDisplayed()
        composeTestRule.onNodeWithText("$0.03").assertIsDisplayed()
    }

    @Test
    fun sessionScreen_darkMode_rendersCorrectly() {
        composeTestRule.setContent {
            UnaMentisTheme(darkTheme = true) {
                SessionScreen(
                    sessionState = SessionState.USER_SPEAKING,
                    transcript =
                        listOf(
                            TranscriptEntry(
                                id = "1",
                                sessionId = "session1",
                                role = "user",
                                text = "Test message",
                                timestamp = System.currentTimeMillis(),
                            ),
                        ),
                )
            }
        }

        // Verify screen renders in dark mode
        composeTestRule.onNodeWithText("Test message").assertIsDisplayed()
    }

    @Test
    fun sessionScreen_rotation_preservesState() {
        val testTranscript =
            listOf(
                TranscriptEntry(
                    id = "1",
                    sessionId = "session1",
                    role = "user",
                    text = "This should survive rotation",
                    timestamp = System.currentTimeMillis(),
                ),
            )

        composeTestRule.setContent {
            UnaMentisTheme {
                SessionScreen(
                    sessionState = SessionState.AI_SPEAKING,
                    transcript = testTranscript,
                )
            }
        }

        // Verify transcript is displayed before rotation
        composeTestRule.onNodeWithText("This should survive rotation").assertIsDisplayed()

        // Note: Actual rotation testing requires Activity scenario
        // This test verifies composable renders consistently
    }

    @Test
    fun sessionScreen_accessibility_hasContentDescriptions() {
        composeTestRule.setContent {
            UnaMentisTheme {
                SessionScreen(sessionState = SessionState.USER_SPEAKING)
            }
        }

        // Verify all interactive elements have content descriptions
        composeTestRule.onNodeWithContentDescription("Pause session").assertExists()
        composeTestRule.onNodeWithContentDescription("Mute microphone").assertExists()
        composeTestRule.onNodeWithContentDescription("Slide to stop session").assertExists()
    }

    @Test
    fun sessionScreen_visualAsset_displaysWhenAvailable() {
        composeTestRule.setContent {
            UnaMentisTheme {
                SessionScreen(
                    sessionState = SessionState.AI_SPEAKING,
                    currentVisualAsset = "https://example.com/diagram.png",
                )
            }
        }

        // Verify visual asset is displayed
        composeTestRule.onNodeWithContentDescription("Current visual asset").assertIsDisplayed()
    }
}
