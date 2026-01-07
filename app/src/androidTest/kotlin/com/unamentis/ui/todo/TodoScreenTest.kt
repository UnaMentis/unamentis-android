package com.unamentis.ui.todo

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.unamentis.data.model.Todo
import com.unamentis.ui.theme.UnaMentisTheme
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for TodoScreen.
 *
 * Tests todo CRUD operations, filtering, and context resume functionality.
 */
class TodoScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testTodo = Todo(
        id = "todo-1",
        title = "Review Newton's Laws",
        description = "Go over the three laws of motion",
        isCompleted = false,
        isArchived = false,
        context = "Discussed in session session-42",
        sessionId = "session-42",
        createdAt = System.currentTimeMillis(),
        completedAt = null
    )

    @Test
    fun todoScreen_initialState_displaysFilterTabs() {
        composeTestRule.setContent {
            UnaMentisTheme {
                TodoScreen()
            }
        }

        // Verify filter tabs are displayed
        composeTestRule.onNodeWithText("Active").assertIsDisplayed()
        composeTestRule.onNodeWithText("Completed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Archived").assertIsDisplayed()
    }

    @Test
    fun todoScreen_activeTab_displaysActiveTodos() {
        val todos = listOf(testTodo)

        composeTestRule.setContent {
            UnaMentisTheme {
                TodoScreen(todos = todos)
            }
        }

        // Verify active todo is displayed
        composeTestRule.onNodeWithText("Review Newton's Laws").assertIsDisplayed()
        composeTestRule.onNodeWithText("Go over the three laws of motion").assertIsDisplayed()
    }

    @Test
    fun todoScreen_todoCard_showsContext() {
        val todos = listOf(testTodo)

        composeTestRule.setContent {
            UnaMentisTheme {
                TodoScreen(todos = todos)
            }
        }

        // Verify context is displayed
        composeTestRule.onNodeWithText("Discussed in session session-42").assertIsDisplayed()
    }

    @Test
    fun todoScreen_completedTab_showsOnlyCompleted() {
        val active = testTodo.copy(isCompleted = false)
        val completed = testTodo.copy(
            id = "todo-2",
            title = "Practice kinematics",
            isCompleted = true,
            completedAt = System.currentTimeMillis()
        )
        val todos = listOf(active, completed)

        composeTestRule.setContent {
            UnaMentisTheme {
                TodoScreen(todos = todos)
            }
        }

        // Switch to Completed tab
        composeTestRule.onNodeWithText("Completed").performClick()

        // Verify only completed todo is shown
        composeTestRule.onNodeWithText("Practice kinematics").assertIsDisplayed()
        composeTestRule.onNodeWithText("Review Newton's Laws").assertDoesNotExist()
    }

    @Test
    fun todoScreen_archivedTab_showsOnlyArchived() {
        val active = testTodo.copy(isArchived = false)
        val archived = testTodo.copy(
            id = "todo-2",
            title = "Old assignment",
            isArchived = true
        )
        val todos = listOf(active, archived)

        composeTestRule.setContent {
            UnaMentisTheme {
                TodoScreen(todos = todos)
            }
        }

        // Switch to Archived tab
        composeTestRule.onNodeWithText("Archived").performClick()

        // Verify only archived todo is shown
        composeTestRule.onNodeWithText("Old assignment").assertIsDisplayed()
        composeTestRule.onNodeWithText("Review Newton's Laws").assertDoesNotExist()
    }

    @Test
    fun todoScreen_addButton_opensAddSheet() {
        var addSheetOpened = false

        composeTestRule.setContent {
            UnaMentisTheme {
                TodoScreen(onAdd = { addSheetOpened = true })
            }
        }

        // Click add button
        composeTestRule.onNodeWithContentDescription("Add todo").performClick()

        // Verify add sheet was opened
        assert(addSheetOpened)
    }

    @Test
    fun todoScreen_addSheet_createsNewTodo() {
        var todoCreated = false

        composeTestRule.setContent {
            UnaMentisTheme {
                AddTodoSheet(
                    onSave = { todoCreated = true },
                    onDismiss = {}
                )
            }
        }

        // Enter title
        composeTestRule.onNodeWithText("Title")
            .performTextInput("Study thermodynamics")

        // Enter description
        composeTestRule.onNodeWithText("Description (optional)")
            .performTextInput("Focus on entropy and heat transfer")

        // Click save
        composeTestRule.onNodeWithText("Save").performClick()

        // Verify todo was created
        assert(todoCreated)
    }

    @Test
    fun todoScreen_editTodo_updatesContent() {
        var todoUpdated = false
        val todos = listOf(testTodo)

        composeTestRule.setContent {
            UnaMentisTheme {
                TodoScreen(
                    todos = todos,
                    onEdit = { todoUpdated = true }
                )
            }
        }

        // Click on todo to edit
        composeTestRule.onNodeWithText("Review Newton's Laws").performClick()

        // Update title
        composeTestRule.onNodeWithText("Review Newton's Laws")
            .performTextClearance()
        composeTestRule.onNodeWithText("Review Newton's Laws")
            .performTextInput("Master Newton's Laws")

        // Save changes
        composeTestRule.onNodeWithText("Save").performClick()

        // Verify todo was updated
        assert(todoUpdated)
    }

    @Test
    fun todoScreen_checkboxToggle_marksTodoComplete() {
        var completionToggled = false
        val todos = listOf(testTodo)

        composeTestRule.setContent {
            UnaMentisTheme {
                TodoScreen(
                    todos = todos,
                    onToggleComplete = { completionToggled = true }
                )
            }
        }

        // Click checkbox to complete todo
        composeTestRule.onNodeWithContentDescription("Mark as complete").performClick()

        // Verify completion was toggled
        assert(completionToggled)
    }

    @Test
    fun todoScreen_deleteTodo_showsConfirmation() {
        var deleteConfirmed = false
        val todos = listOf(testTodo)

        composeTestRule.setContent {
            UnaMentisTheme {
                TodoScreen(
                    todos = todos,
                    onDelete = { deleteConfirmed = true }
                )
            }
        }

        // Swipe to delete
        composeTestRule.onNodeWithText("Review Newton's Laws")
            .performTouchInput { swipeLeft() }

        // Confirm deletion
        composeTestRule.onNodeWithText("Delete").performClick()

        // Verify deletion was confirmed
        assert(deleteConfirmed)
    }

    @Test
    fun todoScreen_archiveTodo_movesToArchived() {
        var archiveTriggered = false
        val todos = listOf(testTodo)

        composeTestRule.setContent {
            UnaMentisTheme {
                TodoScreen(
                    todos = todos,
                    onArchive = { archiveTriggered = true }
                )
            }
        }

        // Long press to show options
        composeTestRule.onNodeWithText("Review Newton's Laws")
            .performTouchInput { longClick() }

        // Click archive option
        composeTestRule.onNodeWithText("Archive").performClick()

        // Verify archive was triggered
        assert(archiveTriggered)
    }

    @Test
    fun todoScreen_resumeFromContext_opensSession() {
        var resumeTriggered = false
        val todos = listOf(testTodo)

        composeTestRule.setContent {
            UnaMentisTheme {
                TodoScreen(
                    todos = todos,
                    onResumeContext = { resumeTriggered = true }
                )
            }
        }

        // Click on context link
        composeTestRule.onNodeWithText("Discussed in session session-42").performClick()

        // Verify resume was triggered
        assert(resumeTriggered)
    }

    @Test
    fun todoScreen_emptyState_displaysMessage() {
        composeTestRule.setContent {
            UnaMentisTheme {
                TodoScreen(todos = emptyList())
            }
        }

        // Verify empty state is displayed
        composeTestRule.onNodeWithText("No active todos").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add a todo to get started").assertIsDisplayed()
    }

    @Test
    fun todoScreen_search_filtersTodosByTitle() {
        val todos = listOf(
            testTodo,
            testTodo.copy(
                id = "todo-2",
                title = "Study thermodynamics"
            )
        )

        composeTestRule.setContent {
            UnaMentisTheme {
                TodoScreen(todos = todos)
            }
        }

        // Enter search query
        composeTestRule.onNodeWithContentDescription("Search todos")
            .performTextInput("Newton")

        // Verify only matching todo is shown
        composeTestRule.onNodeWithText("Review Newton's Laws").assertIsDisplayed()
        composeTestRule.onNodeWithText("Study thermodynamics").assertDoesNotExist()
    }

    @Test
    fun todoScreen_sortByDate_changesOrder() {
        var sortChanged = false
        val todos = listOf(testTodo)

        composeTestRule.setContent {
            UnaMentisTheme {
                TodoScreen(
                    todos = todos,
                    onSortChange = { sortChanged = true }
                )
            }
        }

        // Open sort menu
        composeTestRule.onNodeWithContentDescription("Sort todos").performClick()

        // Select sort by date
        composeTestRule.onNodeWithText("Date created").performClick()

        // Verify sort was changed
        assert(sortChanged)
    }

    @Test
    fun todoScreen_validationError_preventsEmptyTodo() {
        var todoCreated = false

        composeTestRule.setContent {
            UnaMentisTheme {
                AddTodoSheet(
                    onSave = { todoCreated = true },
                    onDismiss = {}
                )
            }
        }

        // Try to save without title
        composeTestRule.onNodeWithText("Save").performClick()

        // Verify error is shown and todo was not created
        composeTestRule.onNodeWithText("Title is required").assertIsDisplayed()
        assert(!todoCreated)
    }

    @Test
    fun todoScreen_completedCount_showsProgress() {
        val todos = listOf(
            testTodo.copy(id = "1", isCompleted = true),
            testTodo.copy(id = "2", isCompleted = true),
            testTodo.copy(id = "3", isCompleted = false)
        )

        composeTestRule.setContent {
            UnaMentisTheme {
                TodoScreen(todos = todos)
            }
        }

        // Verify completion count is displayed
        composeTestRule.onNodeWithText("2 of 3 completed").assertIsDisplayed()
    }

    @Test
    fun todoScreen_bulkActions_selectMultiple() {
        var bulkActionTriggered = false
        val todos = List(5) { index ->
            testTodo.copy(id = "todo-$index", title = "Todo $index")
        }

        composeTestRule.setContent {
            UnaMentisTheme {
                TodoScreen(
                    todos = todos,
                    onBulkAction = { bulkActionTriggered = true }
                )
            }
        }

        // Enter selection mode
        composeTestRule.onNodeWithContentDescription("Select todos").performClick()

        // Select multiple todos
        composeTestRule.onNodeWithText("Todo 0").performClick()
        composeTestRule.onNodeWithText("Todo 1").performClick()

        // Apply bulk action
        composeTestRule.onNodeWithText("Complete selected").performClick()

        // Verify bulk action was triggered
        assert(bulkActionTriggered)
    }

    @Test
    fun todoScreen_accessibility_hasContentDescriptions() {
        val todos = listOf(testTodo)

        composeTestRule.setContent {
            UnaMentisTheme {
                TodoScreen(todos = todos)
            }
        }

        // Verify accessibility for interactive elements
        composeTestRule.onNodeWithContentDescription("Add todo").assertExists()
        composeTestRule.onNodeWithContentDescription("Search todos").assertExists()
        composeTestRule.onNodeWithContentDescription("Sort todos").assertExists()
        composeTestRule.onNodeWithContentDescription("Mark as complete").assertExists()
    }

    @Test
    fun todoScreen_darkMode_rendersCorrectly() {
        val todos = listOf(testTodo)

        composeTestRule.setContent {
            UnaMentisTheme(darkTheme = true) {
                TodoScreen(todos = todos)
            }
        }

        // Verify screen renders in dark mode
        composeTestRule.onNodeWithText("Review Newton's Laws").assertIsDisplayed()
    }
}
