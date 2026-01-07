package com.unamentis.ui.history

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.unamentis.data.local.entity.SessionEntity
import com.unamentis.data.model.TranscriptEntry
import com.unamentis.ui.theme.UnaMentisTheme
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for HistoryScreen.
 *
 * Tests session history display, filtering, detail view, and export.
 */
class HistoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testSession = SessionEntity(
        id = "session-1",
        curriculumId = "physics-101",
        curriculumTitle = "Introduction to Physics",
        startTime = System.currentTimeMillis() - 3600000, // 1 hour ago
        endTime = System.currentTimeMillis(),
        durationMs = 3600000L,
        turnCount = 42,
        totalCostCents = 5.5,
        avgE2ELatencyMs = 450L,
        completed = true
    )

    private val testTranscript = listOf(
        TranscriptEntry(
            id = "1",
            sessionId = "session-1",
            role = "user",
            text = "Explain Newton's First Law",
            timestamp = System.currentTimeMillis() - 3600000
        ),
        TranscriptEntry(
            id = "2",
            sessionId = "session-1",
            role = "assistant",
            text = "Newton's First Law states that an object at rest stays at rest...",
            timestamp = System.currentTimeMillis() - 3500000
        )
    )

    @Test
    fun historyScreen_initialState_displaysSessionList() {
        composeTestRule.setContent {
            UnaMentisTheme {
                HistoryScreen()
            }
        }

        // Verify session list section is displayed
        composeTestRule.onNodeWithText("Session History").assertIsDisplayed()
    }

    @Test
    fun historyScreen_sessionList_displaysSession() {
        val sessions = listOf(testSession)

        composeTestRule.setContent {
            UnaMentisTheme {
                HistoryScreen(sessions = sessions)
            }
        }

        // Verify session is displayed
        composeTestRule.onNodeWithText("Introduction to Physics").assertIsDisplayed()
        composeTestRule.onNodeWithText("42 turns").assertIsDisplayed()
    }

    @Test
    fun historyScreen_sessionCard_showsMetrics() {
        val sessions = listOf(testSession)

        composeTestRule.setContent {
            UnaMentisTheme {
                HistoryScreen(sessions = sessions)
            }
        }

        // Verify session metrics are displayed
        composeTestRule.onNodeWithText("1h 0m").assertIsDisplayed() // Duration
        composeTestRule.onNodeWithText("450ms").assertIsDisplayed() // Latency
        composeTestRule.onNodeWithText("$0.06").assertIsDisplayed() // Cost
    }

    @Test
    fun historyScreen_clickSession_opensDetailView() {
        var detailOpened = false
        val sessions = listOf(testSession)

        composeTestRule.setContent {
            UnaMentisTheme {
                HistoryScreen(
                    sessions = sessions,
                    onSessionClick = { detailOpened = true }
                )
            }
        }

        // Click session card
        composeTestRule.onNodeWithText("Introduction to Physics").performClick()

        // Verify detail view callback was triggered
        assert(detailOpened)
    }

    @Test
    fun historyScreen_detailView_displaysTranscript() {
        composeTestRule.setContent {
            UnaMentisTheme {
                SessionDetailScreen(
                    session = testSession,
                    transcript = testTranscript
                )
            }
        }

        // Verify transcript is displayed
        composeTestRule.onNodeWithText("Explain Newton's First Law").assertIsDisplayed()
        composeTestRule.onNodeWithText("Newton's First Law states that an object at rest stays at rest...")
            .assertIsDisplayed()
    }

    @Test
    fun historyScreen_detailView_showsSessionSummary() {
        composeTestRule.setContent {
            UnaMentisTheme {
                SessionDetailScreen(
                    session = testSession,
                    transcript = testTranscript
                )
            }
        }

        // Verify session summary is displayed
        composeTestRule.onNodeWithText("Session Summary").assertIsDisplayed()
        composeTestRule.onNodeWithText("Duration: 1h 0m").assertIsDisplayed()
        composeTestRule.onNodeWithText("Turns: 42").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cost: $0.06").assertIsDisplayed()
    }

    @Test
    fun historyScreen_exportButton_triggersExport() {
        var exportTriggered = false
        val sessions = listOf(testSession)

        composeTestRule.setContent {
            UnaMentisTheme {
                SessionDetailScreen(
                    session = testSession,
                    transcript = testTranscript,
                    onExport = { exportTriggered = true }
                )
            }
        }

        // Click export button
        composeTestRule.onNodeWithText("Export").performClick()

        // Verify export was triggered
        assert(exportTriggered)
    }

    @Test
    fun historyScreen_exportFormat_showsOptions() {
        composeTestRule.setContent {
            UnaMentisTheme {
                SessionDetailScreen(
                    session = testSession,
                    transcript = testTranscript
                )
            }
        }

        // Click export button
        composeTestRule.onNodeWithText("Export").performClick()

        // Verify format options are displayed
        composeTestRule.onNodeWithText("JSON").assertIsDisplayed()
        composeTestRule.onNodeWithText("Text").assertIsDisplayed()
    }

    @Test
    fun historyScreen_deleteSession_showsConfirmation() {
        var deleteConfirmed = false
        val sessions = listOf(testSession)

        composeTestRule.setContent {
            UnaMentisTheme {
                HistoryScreen(
                    sessions = sessions,
                    onDelete = { deleteConfirmed = true }
                )
            }
        }

        // Long press to show delete option
        composeTestRule.onNodeWithText("Introduction to Physics")
            .performTouchInput { longClick() }

        // Confirm deletion
        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.onNodeWithText("Confirm").performClick()

        // Verify deletion was confirmed
        assert(deleteConfirmed)
    }

    @Test
    fun historyScreen_filter_showsOnlyCompleted() {
        val completed = testSession.copy(completed = true)
        val incomplete = testSession.copy(
            id = "session-2",
            curriculumTitle = "Advanced Physics",
            completed = false
        )
        val sessions = listOf(completed, incomplete)

        composeTestRule.setContent {
            UnaMentisTheme {
                HistoryScreen(sessions = sessions)
            }
        }

        // Apply filter
        composeTestRule.onNodeWithContentDescription("Filter sessions").performClick()
        composeTestRule.onNodeWithText("Completed only").performClick()

        // Verify only completed session is shown
        composeTestRule.onNodeWithText("Introduction to Physics").assertIsDisplayed()
        composeTestRule.onNodeWithText("Advanced Physics").assertDoesNotExist()
    }

    @Test
    fun historyScreen_sortBy_changesOrder() {
        var sortChanged = false
        val sessions = listOf(testSession)

        composeTestRule.setContent {
            UnaMentisTheme {
                HistoryScreen(
                    sessions = sessions,
                    onSortChange = { sortChanged = true }
                )
            }
        }

        // Open sort menu
        composeTestRule.onNodeWithContentDescription("Sort sessions").performClick()

        // Select sort by duration
        composeTestRule.onNodeWithText("Duration").performClick()

        // Verify sort was changed
        assert(sortChanged)
    }

    @Test
    fun historyScreen_search_filtersSessionsByTitle() {
        val sessions = listOf(
            testSession,
            testSession.copy(
                id = "session-2",
                curriculumTitle = "Introduction to Chemistry"
            )
        )

        composeTestRule.setContent {
            UnaMentisTheme {
                HistoryScreen(sessions = sessions)
            }
        }

        // Enter search query
        composeTestRule.onNodeWithContentDescription("Search sessions")
            .performTextInput("Physics")

        // Verify only matching session is shown
        composeTestRule.onNodeWithText("Introduction to Physics").assertIsDisplayed()
        composeTestRule.onNodeWithText("Introduction to Chemistry").assertDoesNotExist()
    }

    @Test
    fun historyScreen_emptyState_displaysMessage() {
        composeTestRule.setContent {
            UnaMentisTheme {
                HistoryScreen(sessions = emptyList())
            }
        }

        // Verify empty state is displayed
        composeTestRule.onNodeWithText("No sessions yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Complete a session to see it here").assertIsDisplayed()
    }

    @Test
    fun historyScreen_groupByDate_organizesessions() {
        val today = testSession.copy(
            id = "session-today",
            startTime = System.currentTimeMillis()
        )
        val yesterday = testSession.copy(
            id = "session-yesterday",
            startTime = System.currentTimeMillis() - 86400000L
        )
        val sessions = listOf(today, yesterday)

        composeTestRule.setContent {
            UnaMentisTheme {
                HistoryScreen(sessions = sessions, groupByDate = true)
            }
        }

        // Verify date headers are displayed
        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
        composeTestRule.onNodeWithText("Yesterday").assertIsDisplayed()
    }

    @Test
    fun historyScreen_sessionStats_showsAggregate() {
        val multipleSessions = List(10) { index ->
            testSession.copy(
                id = "session-$index",
                durationMs = 1800000L + (index * 60000L)
            )
        }

        composeTestRule.setContent {
            UnaMentisTheme {
                HistoryScreen(sessions = multipleSessions)
            }
        }

        // Verify aggregate stats are displayed
        composeTestRule.onNodeWithText("Total Sessions: 10").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total Time").assertIsDisplayed()
    }

    @Test
    fun historyScreen_shareSession_opensShareSheet() {
        var shareTriggered = false

        composeTestRule.setContent {
            UnaMentisTheme {
                SessionDetailScreen(
                    session = testSession,
                    transcript = testTranscript,
                    onShare = { shareTriggered = true }
                )
            }
        }

        // Click share button
        composeTestRule.onNodeWithContentDescription("Share session").performClick()

        // Verify share was triggered
        assert(shareTriggered)
    }

    @Test
    fun historyScreen_scrolling_loadsMoreSessions() {
        val manySessions = List(50) { index ->
            testSession.copy(
                id = "session-$index",
                curriculumTitle = "Session $index"
            )
        }

        composeTestRule.setContent {
            UnaMentisTheme {
                HistoryScreen(sessions = manySessions)
            }
        }

        // Verify first session is visible
        composeTestRule.onNodeWithText("Session 0").assertIsDisplayed()

        // Scroll down
        composeTestRule.onNodeWithText("Session 0").performScrollTo()

        // Later sessions should load (pagination)
        composeTestRule.onNodeWithText("Session 10").assertExists()
    }

    @Test
    fun historyScreen_accessibility_hasContentDescriptions() {
        val sessions = listOf(testSession)

        composeTestRule.setContent {
            UnaMentisTheme {
                HistoryScreen(sessions = sessions)
            }
        }

        // Verify accessibility for interactive elements
        composeTestRule.onNodeWithContentDescription("Search sessions").assertExists()
        composeTestRule.onNodeWithContentDescription("Sort sessions").assertExists()
        composeTestRule.onNodeWithContentDescription("Filter sessions").assertExists()
    }

    @Test
    fun historyScreen_darkMode_rendersCorrectly() {
        val sessions = listOf(testSession)

        composeTestRule.setContent {
            UnaMentisTheme(darkTheme = true) {
                HistoryScreen(sessions = sessions)
            }
        }

        // Verify screen renders in dark mode
        composeTestRule.onNodeWithText("Introduction to Physics").assertIsDisplayed()
    }
}
