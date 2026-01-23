package com.unamentis.ui.curriculum

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unamentis.MainActivity
import com.unamentis.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for CurriculumScreen.
 *
 * Tests curriculum browsing, downloading, searching, and detail views.
 * Uses Hilt for dependency injection to test with real ViewModels.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CurriculumScreenTest {
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
     * Navigate to Curriculum tab using testTag.
     */
    private fun navigateToCurriculum() {
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_curriculum")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_curriculum").performClick()
    }

    @Test
    fun curriculumScreen_navigateToCurriculumTab_displaysScreen() {
        navigateToCurriculum()

        val serverCurriculumText =
            composeTestRule.activity.getString(R.string.curriculum_server)

        // Verify the screen is displayed
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText(serverCurriculumText)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Assert the node is actually visible
        composeTestRule.onAllNodesWithText(serverCurriculumText)
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun curriculumScreen_displaysServerSection() {
        navigateToCurriculum()

        val serverCurriculumText =
            composeTestRule.activity.getString(R.string.curriculum_server)

        // Wait for screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText(serverCurriculumText)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify server section is displayed
        composeTestRule.onNodeWithText(serverCurriculumText).assertIsDisplayed()
    }

    @Test
    fun curriculumScreen_displaysDownloadedSection() {
        navigateToCurriculum()

        val downloadedText =
            composeTestRule.activity.getString(R.string.curriculum_downloaded)

        // Wait for screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText(downloadedText)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify downloaded section is displayed
        composeTestRule.onNodeWithText(downloadedText).assertIsDisplayed()
    }
}
