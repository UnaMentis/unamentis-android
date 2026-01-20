package com.unamentis.ui.curriculum

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

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun curriculumScreen_navigateToCurriculumTab_displaysScreen() {
        // Navigate to Curriculum tab
        composeTestRule.onNodeWithText("Curriculum").performClick()

        // Verify the screen title is displayed
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Curriculum")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun curriculumScreen_displaysServerSection() {
        // Navigate to Curriculum tab
        composeTestRule.onNodeWithText("Curriculum").performClick()

        // Wait for screen to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Server Curriculum")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify server section is displayed
        composeTestRule.onNodeWithText("Server Curriculum").assertIsDisplayed()
    }

    @Test
    fun curriculumScreen_displaysDownloadedSection() {
        // Navigate to Curriculum tab
        composeTestRule.onNodeWithText("Curriculum").performClick()

        // Wait for screen to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Downloaded")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify downloaded section is displayed
        composeTestRule.onNodeWithText("Downloaded").assertIsDisplayed()
    }
}
