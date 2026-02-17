package com.unamentis.core.todo

import com.unamentis.data.local.dao.TodoDao
import com.unamentis.data.model.Todo
import com.unamentis.data.model.TodoItemSource
import com.unamentis.data.model.TodoItemType
import com.unamentis.data.model.TodoStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TodoManagerTest {
    private lateinit var todoDao: TodoDao
    private lateinit var curriculumSuggestionService: CurriculumSuggestionService
    private lateinit var todoManager: TodoManager

    @Before
    fun setup() {
        todoDao = mockk(relaxed = true)
        curriculumSuggestionService = mockk(relaxed = true)
        todoManager = TodoManager(todoDao, curriculumSuggestionService)
    }

    // region Create Operations

    @Test
    fun `createItem creates todo with correct fields`() =
        runTest {
            coEvery { todoDao.getMaxSortOrder() } returns 5

            val result =
                todoManager.createItem(
                    title = "Study algebra",
                    type = TodoItemType.LEARNING_TARGET,
                    source = TodoItemSource.MANUAL,
                    notes = "Focus on quadratics",
                )

            assertEquals("Study algebra", result.title)
            assertEquals(TodoItemType.LEARNING_TARGET, result.itemType)
            assertEquals(TodoItemSource.MANUAL, result.source)
            assertEquals("Focus on quadratics", result.notes)
            assertEquals(6, result.sortOrder)
            assertEquals(TodoStatus.ACTIVE, result.status)

            val insertedSlot = slot<Todo>()
            coVerify { todoDao.insert(capture(insertedSlot)) }
            assertEquals("Study algebra", insertedSlot.captured.title)
        }

    @Test
    fun `createItem with learning target triggers curriculum suggestions`() =
        runTest {
            coEvery { todoDao.getMaxSortOrder() } returns null

            todoManager.createItem(
                title = "Learn calculus",
                type = TodoItemType.LEARNING_TARGET,
            )

            coVerify { curriculumSuggestionService.updateTodoWithSuggestions(any()) }
        }

    @Test
    fun `createItem with non-learning-target does not trigger suggestions`() =
        runTest {
            coEvery { todoDao.getMaxSortOrder() } returns 0

            todoManager.createItem(
                title = "Review topic",
                type = TodoItemType.REINFORCEMENT,
            )

            coVerify(exactly = 0) { curriculumSuggestionService.updateTodoWithSuggestions(any()) }
        }

    @Test
    fun `createItem assigns sortOrder of 0 when no existing items`() =
        runTest {
            coEvery { todoDao.getMaxSortOrder() } returns null

            val result = todoManager.createItem(title = "First item")

            assertEquals(0, result.sortOrder)
        }

    @Test
    fun `createCurriculumItem creates with correct granularity`() =
        runTest {
            coEvery { todoDao.getMaxSortOrder() } returns 2

            val result =
                todoManager.createCurriculumItem(
                    title = "Study Biology",
                    curriculumId = "curriculum-123",
                    topicId = "topic-456",
                    granularity = "topic",
                )

            assertEquals("Study Biology", result.title)
            assertEquals(TodoItemType.TOPIC, result.itemType)
            assertEquals("curriculum-123", result.curriculumId)
            assertEquals("topic-456", result.topicId)
            assertEquals("topic", result.granularity)
            assertEquals(3, result.sortOrder)
        }

    @Test
    fun `createCurriculumItem maps granularity to correct item type`() =
        runTest {
            coEvery { todoDao.getMaxSortOrder() } returns 0

            val curriculumItem =
                todoManager.createCurriculumItem(
                    title = "Test",
                    curriculumId = "c1",
                    granularity = "curriculum",
                )
            assertEquals(TodoItemType.CURRICULUM, curriculumItem.itemType)

            val moduleItem =
                todoManager.createCurriculumItem(
                    title = "Test",
                    curriculumId = "c1",
                    granularity = "module",
                )
            assertEquals(TodoItemType.MODULE, moduleItem.itemType)

            val topicItem =
                todoManager.createCurriculumItem(
                    title = "Test",
                    curriculumId = "c1",
                    granularity = "topic",
                )
            assertEquals(TodoItemType.TOPIC, topicItem.itemType)

            val defaultItem =
                todoManager.createCurriculumItem(
                    title = "Test",
                    curriculumId = "c1",
                    granularity = "unknown",
                )
            assertEquals(TodoItemType.CURRICULUM, defaultItem.itemType)
        }

    @Test
    fun `createAutoResumeItem creates new item at position 0`() =
        runTest {
            coEvery { todoDao.getAutoResumeForTopic("topic-1") } returns null

            val result =
                todoManager.createAutoResumeItem(
                    title = "Continue: Chemistry",
                    topicId = "topic-1",
                    segmentIndex = 5,
                    conversationContext = """[{"role":"user","content":"hello"}]""",
                )

            assertEquals("Continue: Chemistry", result.title)
            assertEquals(TodoItemType.AUTO_RESUME, result.itemType)
            assertEquals(TodoItemSource.AUTO_RESUME, result.source)
            assertEquals("topic-1", result.resumeTopicId)
            assertEquals(5, result.resumeSegmentIndex)
            assertEquals(0, result.sortOrder)

            coVerify { todoDao.shiftSortOrdersDown(0) }
            coVerify { todoDao.insert(any()) }
        }

    @Test
    fun `createAutoResumeItem updates existing item instead of creating new`() =
        runTest {
            val existing =
                Todo(
                    id = "existing-1",
                    title = "Continue: Chemistry",
                    itemType = TodoItemType.AUTO_RESUME,
                    source = TodoItemSource.AUTO_RESUME,
                    resumeTopicId = "topic-1",
                    resumeSegmentIndex = 3,
                )
            coEvery { todoDao.getAutoResumeForTopic("topic-1") } returns existing

            val result =
                todoManager.createAutoResumeItem(
                    title = "Continue: Chemistry",
                    topicId = "topic-1",
                    segmentIndex = 7,
                    conversationContext = "new context",
                )

            assertEquals("existing-1", result.id)
            assertEquals(7, result.resumeSegmentIndex)
            assertEquals("new context", result.resumeConversationContext)

            coVerify(exactly = 0) { todoDao.shiftSortOrdersDown(any()) }
            coVerify(exactly = 0) { todoDao.insert(any()) }
            coVerify { todoDao.update(any()) }
        }

    @Test
    fun `createReinforcementItem creates with correct fields`() =
        runTest {
            coEvery { todoDao.getMaxSortOrder() } returns 10

            val result =
                todoManager.createReinforcementItem(
                    title = "Review: Electron configuration",
                    notes = "Student was confused",
                    sessionId = "session-1",
                )

            assertEquals("Review: Electron configuration", result.title)
            assertEquals(TodoItemType.REINFORCEMENT, result.itemType)
            assertEquals(TodoItemSource.REINFORCEMENT, result.source)
            assertEquals("Student was confused", result.notes)
            assertEquals("session-1", result.sessionId)
            assertEquals(11, result.sortOrder)
        }

    // endregion

    // region Update Operations

    @Test
    fun `updateStatus updates status and timestamps`() =
        runTest {
            val todo =
                Todo(
                    id = "test-1",
                    title = "Test",
                    status = TodoStatus.ACTIVE,
                )
            coEvery { todoDao.getById("test-1") } returns todo

            todoManager.updateStatus("test-1", TodoStatus.COMPLETED)

            val updatedSlot = slot<Todo>()
            coVerify { todoDao.update(capture(updatedSlot)) }

            assertEquals(TodoStatus.COMPLETED, updatedSlot.captured.status)
            assertNotNull(updatedSlot.captured.completedAt)
        }

    @Test
    fun `updateStatus sets archivedAt when archiving`() =
        runTest {
            val todo =
                Todo(
                    id = "test-1",
                    title = "Test",
                    status = TodoStatus.ACTIVE,
                )
            coEvery { todoDao.getById("test-1") } returns todo

            todoManager.updateStatus("test-1", TodoStatus.ARCHIVED)

            val updatedSlot = slot<Todo>()
            coVerify { todoDao.update(capture(updatedSlot)) }

            assertEquals(TodoStatus.ARCHIVED, updatedSlot.captured.status)
            assertNotNull(updatedSlot.captured.archivedAt)
        }

    @Test
    fun `updateStatus does nothing for non-existent item`() =
        runTest {
            coEvery { todoDao.getById("non-existent") } returns null

            todoManager.updateStatus("non-existent", TodoStatus.COMPLETED)

            coVerify(exactly = 0) { todoDao.update(any()) }
        }

    @Test
    fun `updateItem updates title and notes`() =
        runTest {
            val todo =
                Todo(
                    id = "test-1",
                    title = "Old title",
                    notes = "Old notes",
                )
            coEvery { todoDao.getById("test-1") } returns todo

            todoManager.updateItem("test-1", title = "New title", notes = "New notes")

            val updatedSlot = slot<Todo>()
            coVerify { todoDao.update(capture(updatedSlot)) }

            assertEquals("New title", updatedSlot.captured.title)
            assertEquals("New notes", updatedSlot.captured.notes)
        }

    @Test
    fun `updateItem keeps existing title when null is passed`() =
        runTest {
            val todo =
                Todo(
                    id = "test-1",
                    title = "Original title",
                )
            coEvery { todoDao.getById("test-1") } returns todo

            todoManager.updateItem("test-1", title = null, notes = "Updated notes")

            val updatedSlot = slot<Todo>()
            coVerify { todoDao.update(capture(updatedSlot)) }

            assertEquals("Original title", updatedSlot.captured.title)
        }

    @Test
    fun `completeItem delegates to updateStatus with COMPLETED`() =
        runTest {
            val todo =
                Todo(
                    id = "test-1",
                    title = "Test",
                )
            coEvery { todoDao.getById("test-1") } returns todo

            todoManager.completeItem("test-1")

            val updatedSlot = slot<Todo>()
            coVerify { todoDao.update(capture(updatedSlot)) }
            assertEquals(TodoStatus.COMPLETED, updatedSlot.captured.status)
        }

    @Test
    fun `archiveItem delegates to updateStatus with ARCHIVED`() =
        runTest {
            val todo =
                Todo(
                    id = "test-1",
                    title = "Test",
                )
            coEvery { todoDao.getById("test-1") } returns todo

            todoManager.archiveItem("test-1")

            val updatedSlot = slot<Todo>()
            coVerify { todoDao.update(capture(updatedSlot)) }
            assertEquals(TodoStatus.ARCHIVED, updatedSlot.captured.status)
        }

    @Test
    fun `restoreItem sets status to ACTIVE and clears archivedAt`() =
        runTest {
            val todo =
                Todo(
                    id = "test-1",
                    title = "Test",
                    status = TodoStatus.ARCHIVED,
                    archivedAt = System.currentTimeMillis(),
                )
            coEvery { todoDao.getById("test-1") } returns todo

            todoManager.restoreItem("test-1")

            val updatedSlot = slot<Todo>()
            coVerify { todoDao.update(capture(updatedSlot)) }
            assertEquals(TodoStatus.ACTIVE, updatedSlot.captured.status)
            assertNull(updatedSlot.captured.archivedAt)
        }

    // endregion

    // region Reordering Operations

    @Test
    fun `moveItem moves item down correctly`() =
        runTest {
            val items =
                listOf(
                    Todo(id = "a", title = "A", sortOrder = 0),
                    Todo(id = "b", title = "B", sortOrder = 1),
                    Todo(id = "c", title = "C", sortOrder = 2),
                )
            coEvery { todoDao.getActiveTodosList() } returns items

            todoManager.moveItem("a", 2)

            // Items B and C should have been shifted up, A moved to position 2
            coVerify { todoDao.update(match { it.id == "b" && it.sortOrder == 0 }) }
            coVerify { todoDao.update(match { it.id == "c" && it.sortOrder == 1 }) }
            coVerify { todoDao.update(match { it.id == "a" && it.sortOrder == 2 }) }
        }

    @Test
    fun `moveItem moves item up correctly`() =
        runTest {
            val items =
                listOf(
                    Todo(id = "a", title = "A", sortOrder = 0),
                    Todo(id = "b", title = "B", sortOrder = 1),
                    Todo(id = "c", title = "C", sortOrder = 2),
                )
            coEvery { todoDao.getActiveTodosList() } returns items

            todoManager.moveItem("c", 0)

            // Items A and B should have been shifted down, C moved to position 0
            coVerify { todoDao.update(match { it.id == "a" && it.sortOrder == 1 }) }
            coVerify { todoDao.update(match { it.id == "b" && it.sortOrder == 2 }) }
            coVerify { todoDao.update(match { it.id == "c" && it.sortOrder == 0 }) }
        }

    @Test
    fun `moveItem with invalid index does nothing`() =
        runTest {
            val items =
                listOf(
                    Todo(id = "a", title = "A", sortOrder = 0),
                )
            coEvery { todoDao.getActiveTodosList() } returns items

            todoManager.moveItem("a", 5)

            coVerify(exactly = 0) { todoDao.update(any()) }
        }

    @Test
    fun `reorderItems sets sortOrder based on position in list`() =
        runTest {
            val todos =
                listOf(
                    Todo(id = "c", title = "C", sortOrder = 2),
                    Todo(id = "a", title = "A", sortOrder = 0),
                    Todo(id = "b", title = "B", sortOrder = 1),
                )
            coEvery { todoDao.getById("c") } returns todos[0]
            coEvery { todoDao.getById("a") } returns todos[1]
            coEvery { todoDao.getById("b") } returns todos[2]

            todoManager.reorderItems(listOf("c", "a", "b"))

            coVerify { todoDao.update(match { it.id == "c" && it.sortOrder == 0 }) }
            coVerify { todoDao.update(match { it.id == "a" && it.sortOrder == 1 }) }
            coVerify { todoDao.update(match { it.id == "b" && it.sortOrder == 2 }) }
        }

    // endregion

    // region Delete Operations

    @Test
    fun `deleteItem deletes existing item`() =
        runTest {
            val todo = Todo(id = "test-1", title = "Delete me")
            coEvery { todoDao.getById("test-1") } returns todo

            todoManager.deleteItem("test-1")

            coVerify { todoDao.delete(todo) }
        }

    @Test
    fun `deleteItem does nothing for non-existent item`() =
        runTest {
            coEvery { todoDao.getById("non-existent") } returns null

            todoManager.deleteItem("non-existent")

            coVerify(exactly = 0) { todoDao.delete(any()) }
        }

    @Test
    fun `deleteAllCompleted calls dao method`() =
        runTest {
            todoManager.deleteAllCompleted()

            coVerify { todoDao.deleteAllCompleted() }
        }

    // endregion

    // region Auto-Resume Operations

    @Test
    fun `clearAutoResume deletes auto-resume item for topic`() =
        runTest {
            val autoResume =
                Todo(
                    id = "ar-1",
                    title = "Continue: Chemistry",
                    itemType = TodoItemType.AUTO_RESUME,
                    resumeTopicId = "topic-1",
                )
            coEvery { todoDao.getAutoResumeForTopic("topic-1") } returns autoResume

            todoManager.clearAutoResume("topic-1")

            coVerify { todoDao.delete(autoResume) }
        }

    @Test
    fun `clearAutoResume does nothing when no auto-resume exists`() =
        runTest {
            coEvery { todoDao.getAutoResumeForTopic("topic-1") } returns null

            todoManager.clearAutoResume("topic-1")

            coVerify(exactly = 0) { todoDao.delete(any()) }
        }

    @Test
    fun `getResumeContext returns resume data when item exists`() =
        runTest {
            val autoResume =
                Todo(
                    id = "ar-1",
                    title = "Continue: Chemistry",
                    itemType = TodoItemType.AUTO_RESUME,
                    resumeTopicId = "topic-1",
                    resumeSegmentIndex = 5,
                    resumeConversationContext = """[{"role":"user","content":"test"}]""",
                )
            coEvery { todoDao.getAutoResumeForTopic("topic-1") } returns autoResume

            val result = todoManager.getResumeContext("topic-1")

            assertNotNull(result)
            assertEquals(5, result!!.segmentIndex)
            assertNotNull(result.conversationContext)
        }

    @Test
    fun `getResumeContext returns null when no item exists`() =
        runTest {
            coEvery { todoDao.getAutoResumeForTopic("topic-1") } returns null

            val result = todoManager.getResumeContext("topic-1")

            assertNull(result)
        }

    // endregion

    // region Learning Target Specific

    @Test
    fun `updateSuggestedCurricula updates learning target item`() =
        runTest {
            val todo =
                Todo(
                    id = "lt-1",
                    title = "Learn calculus",
                    itemType = TodoItemType.LEARNING_TARGET,
                )
            coEvery { todoDao.getById("lt-1") } returns todo

            todoManager.updateSuggestedCurricula("lt-1", """["c1","c2"]""")

            val updatedSlot = slot<Todo>()
            coVerify { todoDao.update(capture(updatedSlot)) }
            assertEquals("""["c1","c2"]""", updatedSlot.captured.suggestedCurriculumIds)
        }

    @Test
    fun `updateSuggestedCurricula does nothing for non-learning-target`() =
        runTest {
            val todo =
                Todo(
                    id = "r-1",
                    title = "Review item",
                    itemType = TodoItemType.REINFORCEMENT,
                )
            coEvery { todoDao.getById("r-1") } returns todo

            todoManager.updateSuggestedCurricula("r-1", """["c1"]""")

            coVerify(exactly = 0) { todoDao.update(any()) }
        }

    // endregion

    // region Fetch Operations

    @Test
    fun `fetchItem delegates to dao`() =
        runTest {
            val expected = Todo(id = "test-1", title = "Test")
            coEvery { todoDao.getById("test-1") } returns expected

            val result = todoManager.fetchItem("test-1")

            assertEquals(expected, result)
        }

    @Test
    fun `fetchActiveItems delegates to dao`() =
        runTest {
            val expected =
                listOf(
                    Todo(id = "1", title = "A"),
                    Todo(id = "2", title = "B"),
                )
            coEvery { todoDao.getActiveTodosList() } returns expected

            val result = todoManager.fetchActiveItems()

            assertEquals(expected, result)
        }

    // endregion
}
