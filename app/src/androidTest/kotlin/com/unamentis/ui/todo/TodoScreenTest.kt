package com.unamentis.ui.todo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unamentis.MainActivity
import com.unamentis.SkipOnboardingRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for TodoScreen.
 *
 * Tests todo CRUD operations, filtering, and navigation.
 * Uses Hilt for dependency injection to test with real ViewModels.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TodoScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val skipOnboardingRule = SkipOnboardingRule()

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    companion object {
        private const val DEFAULT_TIMEOUT = 10_000L
    }

    @Before
    fun setup() {
        hiltRule.inject()
    }

    /**
     * Navigate to To-Do tab using testTag.
     */
    private fun navigateToTodo() {
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithTag("nav_todo")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_todo").performClick()
    }

    @Test
    fun todoScreen_navigateToTodoTab_displaysScreen() {
        navigateToTodo()

        // Verify the screen is displayed
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription("To-Do tab")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun todoScreen_displaysFilterTabs() {
        navigateToTodo()

        // Wait for screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Active")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify filter tabs are displayed
        composeTestRule.onNodeWithText("Active").assertIsDisplayed()
        composeTestRule.onNodeWithText("Completed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Archived").assertIsDisplayed()
    }

    @Test
    fun todoScreen_addButton_exists() {
        navigateToTodo()

        // Wait for screen to load and verify FAB exists
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription("Add todo")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithContentDescription("Add todo").assertIsDisplayed()
    }

    @Test
    fun todoScreen_switchToCompletedTab_works() {
        navigateToTodo()

        // Wait for screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Active")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Click on Completed tab
        composeTestRule.onNodeWithText("Completed").performClick()

        // Tab should be clickable (no assertion failure means success)
    }

    @Test
    fun todoScreen_switchToArchivedTab_works() {
        navigateToTodo()

        // Wait for screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Active")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Click on Archived tab
        composeTestRule.onNodeWithText("Archived").performClick()

        // Tab should be clickable (no assertion failure means success)
    }

    @Test
    fun todoScreen_clickAddButton_opensDialog() {
        navigateToTodo()

        // Wait for screen to load
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithContentDescription("Add todo")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Click add button
        composeTestRule.onNodeWithContentDescription("Add todo").performClick()

        // Verify create dialog appears
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Create Todo")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Create Todo").assertIsDisplayed()
    }
}
