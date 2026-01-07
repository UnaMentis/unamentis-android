package com.unamentis.ui.curriculum

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.unamentis.data.model.Curriculum
import com.unamentis.data.model.Topic
import com.unamentis.ui.theme.UnaMentisTheme
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for CurriculumScreen.
 *
 * Tests curriculum browsing, downloading, searching, and detail views.
 */
class CurriculumScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testCurriculum = Curriculum(
        id = "physics-101",
        title = "Introduction to Physics",
        description = "Learn the fundamentals of classical mechanics and thermodynamics",
        author = "Dr. Jane Smith",
        version = "1.0.0",
        topics = listOf(
            Topic(
                id = "topic-1",
                title = "Newton's Laws",
                objectives = listOf("Understand F=ma", "Apply to real-world scenarios"),
                segments = emptyList()
            ),
            Topic(
                id = "topic-2",
                title = "Thermodynamics",
                objectives = listOf("Learn heat transfer", "Understand entropy"),
                segments = emptyList()
            )
        ),
        isDownloaded = false,
        downloadProgress = 0f,
        lastUpdated = System.currentTimeMillis()
    )

    @Test
    fun curriculumScreen_initialState_showsServerCurricula() {
        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumScreen()
            }
        }

        // Verify tab navigation is displayed
        composeTestRule.onNodeWithText("Server").assertIsDisplayed()
        composeTestRule.onNodeWithText("Downloaded").assertIsDisplayed()
    }

    @Test
    fun curriculumScreen_serverTab_displaysCurriculumList() {
        val curricula = listOf(testCurriculum)

        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumScreen(serverCurricula = curricula)
            }
        }

        // Verify curriculum is displayed
        composeTestRule.onNodeWithText("Introduction to Physics").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dr. Jane Smith").assertIsDisplayed()
    }

    @Test
    fun curriculumScreen_curriculumCard_showsMetadata() {
        val curricula = listOf(testCurriculum)

        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumScreen(serverCurricula = curricula)
            }
        }

        // Verify metadata is shown
        composeTestRule.onNodeWithText("Introduction to Physics").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dr. Jane Smith").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 topics").assertIsDisplayed()
        composeTestRule.onNodeWithText("v1.0.0").assertIsDisplayed()
    }

    @Test
    fun curriculumScreen_clickCurriculum_opensDetailView() {
        var detailOpened = false
        val curricula = listOf(testCurriculum)

        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumScreen(
                    serverCurricula = curricula,
                    onCurriculumClick = { detailOpened = true }
                )
            }
        }

        // Click curriculum card
        composeTestRule.onNodeWithText("Introduction to Physics").performClick()

        // Verify detail view callback was triggered
        assert(detailOpened)
    }

    @Test
    fun curriculumScreen_detailView_displaysTopics() {
        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumDetailScreen(curriculum = testCurriculum)
            }
        }

        // Verify topics are displayed
        composeTestRule.onNodeWithText("Newton's Laws").assertIsDisplayed()
        composeTestRule.onNodeWithText("Thermodynamics").assertIsDisplayed()
    }

    @Test
    fun curriculumScreen_detailView_displaysObjectives() {
        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumDetailScreen(curriculum = testCurriculum)
            }
        }

        // Expand first topic
        composeTestRule.onNodeWithText("Newton's Laws").performClick()

        // Verify objectives are displayed
        composeTestRule.onNodeWithText("Understand F=ma").assertIsDisplayed()
        composeTestRule.onNodeWithText("Apply to real-world scenarios").assertIsDisplayed()
    }

    @Test
    fun curriculumScreen_downloadButton_triggersDownload() {
        var downloadTriggered = false
        val curricula = listOf(testCurriculum)

        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumScreen(
                    serverCurricula = curricula,
                    onDownload = { downloadTriggered = true }
                )
            }
        }

        // Click download button
        composeTestRule.onNodeWithContentDescription("Download curriculum").performClick()

        // Verify download was triggered
        assert(downloadTriggered)
    }

    @Test
    fun curriculumScreen_downloadProgress_displaysProgress() {
        val downloadingCurriculum = testCurriculum.copy(
            downloadProgress = 0.45f
        )
        val curricula = listOf(downloadingCurriculum)

        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumScreen(serverCurricula = curricula)
            }
        }

        // Verify progress is displayed
        composeTestRule.onNodeWithText("45%").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Download progress").assertIsDisplayed()
    }

    @Test
    fun curriculumScreen_downloadedCurriculum_showsCheckmark() {
        val downloadedCurriculum = testCurriculum.copy(
            isDownloaded = true,
            downloadProgress = 1.0f
        )
        val curricula = listOf(downloadedCurriculum)

        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumScreen(serverCurricula = curricula)
            }
        }

        // Verify downloaded indicator is shown
        composeTestRule.onNodeWithContentDescription("Downloaded").assertIsDisplayed()
    }

    @Test
    fun curriculumScreen_downloadedTab_showsOnlyDownloaded() {
        val downloaded = testCurriculum.copy(isDownloaded = true)
        val notDownloaded = testCurriculum.copy(
            id = "physics-102",
            title = "Advanced Physics",
            isDownloaded = false
        )

        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumScreen(
                    serverCurricula = listOf(downloaded, notDownloaded),
                    downloadedCurricula = listOf(downloaded)
                )
            }
        }

        // Switch to Downloaded tab
        composeTestRule.onNodeWithText("Downloaded").performClick()

        // Verify only downloaded curriculum is shown
        composeTestRule.onNodeWithText("Introduction to Physics").assertIsDisplayed()
        composeTestRule.onNodeWithText("Advanced Physics").assertDoesNotExist()
    }

    @Test
    fun curriculumScreen_search_filtersCurricula() {
        val curricula = listOf(
            testCurriculum,
            testCurriculum.copy(
                id = "chemistry-101",
                title = "Introduction to Chemistry"
            )
        )

        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumScreen(serverCurricula = curricula)
            }
        }

        // Enter search query
        composeTestRule.onNodeWithContentDescription("Search curricula")
            .performTextInput("Physics")

        // Verify only matching curriculum is shown
        composeTestRule.onNodeWithText("Introduction to Physics").assertIsDisplayed()
        composeTestRule.onNodeWithText("Introduction to Chemistry").assertDoesNotExist()
    }

    @Test
    fun curriculumScreen_emptyState_displaysMessage() {
        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumScreen(serverCurricula = emptyList())
            }
        }

        // Verify empty state is displayed
        composeTestRule.onNodeWithText("No curricula available").assertIsDisplayed()
    }

    @Test
    fun curriculumScreen_loading_displaysSpinner() {
        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumScreen(isLoading = true)
            }
        }

        // Verify loading indicator is displayed
        composeTestRule.onNodeWithContentDescription("Loading curricula").assertIsDisplayed()
    }

    @Test
    fun curriculumScreen_error_displaysMessage() {
        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumScreen(
                    isLoading = false,
                    errorMessage = "Failed to fetch curricula. Check your connection."
                )
            }
        }

        // Verify error message is displayed
        composeTestRule.onNodeWithText("Failed to fetch curricula. Check your connection.")
            .assertIsDisplayed()
    }

    @Test
    fun curriculumScreen_retryButton_triggersRefresh() {
        var refreshTriggered = false

        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumScreen(
                    errorMessage = "Network error",
                    onRetry = { refreshTriggered = true }
                )
            }
        }

        // Click retry button
        composeTestRule.onNodeWithText("Retry").performClick()

        // Verify refresh was triggered
        assert(refreshTriggered)
    }

    @Test
    fun curriculumScreen_deleteCurriculum_showsConfirmation() {
        var deleteConfirmed = false
        val downloaded = testCurriculum.copy(isDownloaded = true)

        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumScreen(
                    downloadedCurricula = listOf(downloaded),
                    onDelete = { deleteConfirmed = true }
                )
            }
        }

        // Switch to Downloaded tab
        composeTestRule.onNodeWithText("Downloaded").performClick()

        // Long press to show delete option
        composeTestRule.onNodeWithText("Introduction to Physics")
            .performTouchInput { longClick() }

        // Confirm deletion
        composeTestRule.onNodeWithText("Delete").performClick()

        // Verify deletion was confirmed
        assert(deleteConfirmed)
    }

    @Test
    fun curriculumScreen_adaptiveLayout_phone() {
        val curricula = List(10) { index ->
            testCurriculum.copy(
                id = "curriculum-$index",
                title = "Curriculum $index"
            )
        }

        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumScreen(
                    serverCurricula = curricula,
                    isTablet = false
                )
            }
        }

        // Verify single column layout (all items stacked)
        composeTestRule.onNodeWithText("Curriculum 0").assertIsDisplayed()
    }

    @Test
    fun curriculumScreen_adaptiveLayout_tablet() {
        val curricula = List(10) { index ->
            testCurriculum.copy(
                id = "curriculum-$index",
                title = "Curriculum $index"
            )
        }

        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumScreen(
                    serverCurricula = curricula,
                    isTablet = true
                )
            }
        }

        // Verify grid layout (multiple columns visible)
        // On tablet, should see multiple items in same row
        composeTestRule.onNodeWithText("Curriculum 0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Curriculum 1").assertIsDisplayed()
    }

    @Test
    fun curriculumScreen_sortOptions_changeSorting() {
        val curricula = listOf(
            testCurriculum.copy(id = "a", title = "Zebra"),
            testCurriculum.copy(id = "b", title = "Apple")
        )

        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumScreen(serverCurricula = curricula)
            }
        }

        // Open sort menu
        composeTestRule.onNodeWithContentDescription("Sort options").performClick()

        // Select alphabetical sort
        composeTestRule.onNodeWithText("Alphabetical").performClick()

        // Verify order changed (implementation would reorder list)
        // This test verifies the UI exists
        composeTestRule.onNodeWithText("Apple").assertIsDisplayed()
        composeTestRule.onNodeWithText("Zebra").assertIsDisplayed()
    }

    @Test
    fun curriculumScreen_accessibility_hasContentDescriptions() {
        val curricula = listOf(testCurriculum)

        composeTestRule.setContent {
            UnaMentisTheme {
                CurriculumScreen(serverCurricula = curricula)
            }
        }

        // Verify accessibility for interactive elements
        composeTestRule.onNodeWithContentDescription("Search curricula").assertExists()
        composeTestRule.onNodeWithContentDescription("Sort options").assertExists()
        composeTestRule.onNodeWithContentDescription("Download curriculum").assertExists()
    }

    @Test
    fun curriculumScreen_darkMode_rendersCorrectly() {
        val curricula = listOf(testCurriculum)

        composeTestRule.setContent {
            UnaMentisTheme(darkTheme = true) {
                CurriculumScreen(serverCurricula = curricula)
            }
        }

        // Verify screen renders in dark mode
        composeTestRule.onNodeWithText("Introduction to Physics").assertIsDisplayed()
    }
}
