package com.unamentis.core.tools

import com.unamentis.core.tools.handlers.TodoToolHandler
import com.unamentis.data.local.dao.TodoDao
import com.unamentis.data.model.Todo
import com.unamentis.data.model.TodoPriority
import com.unamentis.data.model.TodoStatus
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TodoToolHandlerTest {
    private lateinit var todoDao: TodoDao
    private lateinit var handler: TodoToolHandler

    @Before
    fun setup() {
        todoDao = mockk(relaxed = true)
        handler = TodoToolHandler(todoDao)
    }

    @Test
    fun `toolName is correct`() {
        assertEquals("add_todo", handler.toolName)
    }

    @Test
    fun `getDefinition returns valid definition`() {
        val definition = handler.getDefinition()

        assertEquals("add_todo", definition.name)
        assertTrue(definition.description.isNotEmpty())
        assertTrue(definition.parameters.properties.containsKey("title"))
        assertTrue(definition.parameters.properties.containsKey("type"))
        assertTrue(definition.parameters.properties.containsKey("notes"))
        assertTrue(definition.parameters.properties.containsKey("priority"))
        assertEquals(listOf("title", "type"), definition.parameters.required)
    }

    @Test
    fun `getMarkForReviewDefinition returns valid definition`() {
        val definition = handler.getMarkForReviewDefinition()

        assertEquals("mark_for_review", definition.name)
        assertTrue(definition.description.isNotEmpty())
        assertTrue(definition.parameters.properties.containsKey("reason"))
        assertTrue(definition.parameters.required.isEmpty())
    }

    @Test
    fun `handle add_todo creates todo successfully`() =
        runTest {
            val call =
                LLMToolCall(
                    id = "call_123",
                    name = "add_todo",
                    arguments =
                        buildJsonObject {
                            put("title", "Practice quadratic equations")
                            put("type", "learning_target")
                            put("notes", "Focus on the quadratic formula")
                            put("priority", "high")
                        },
                )

            val context =
                ToolExecutionContext(
                    sessionId = "session_abc",
                    topicId = "topic_xyz",
                    topicTitle = "Algebra",
                )

            val todoSlot = slot<Todo>()
            coEvery { todoDao.insert(capture(todoSlot)) } returns Unit

            val result = handler.handle(call, context)

            assertFalse(result.isError)
            assertTrue(result.content.contains("Created todo"))
            assertTrue(result.content.contains("Practice quadratic equations"))

            val capturedTodo = todoSlot.captured
            assertEquals("Practice quadratic equations", capturedTodo.title)
            assertTrue(capturedTodo.notes?.contains("Focus on the quadratic formula") == true)
            assertTrue(capturedTodo.notes?.contains("Learning target") == true)
            assertEquals(TodoPriority.HIGH, capturedTodo.priority)
            assertEquals(TodoStatus.ACTIVE, capturedTodo.status)
            assertEquals("session_abc", capturedTodo.sessionId)
            assertEquals("topic_xyz", capturedTodo.topicId)
            assertTrue(capturedTodo.isAISuggested)
            assertNotNull(capturedTodo.suggestionReason)
            assertNotNull(capturedTodo.suggestionConfidence)
        }

    @Test
    fun `handle add_todo with reinforcement type`() =
        runTest {
            val call =
                LLMToolCall(
                    id = "call_123",
                    name = "add_todo",
                    arguments =
                        buildJsonObject {
                            put("title", "Review photosynthesis")
                            put("type", "reinforcement")
                        },
                )

            val context = ToolExecutionContext()

            val todoSlot = slot<Todo>()
            coEvery { todoDao.insert(capture(todoSlot)) } returns Unit

            val result = handler.handle(call, context)

            assertFalse(result.isError)
            assertTrue(todoSlot.captured.notes?.contains("Reinforcement") == true)
        }

    @Test
    fun `handle add_todo with default priority`() =
        runTest {
            val call =
                LLMToolCall(
                    id = "call_123",
                    name = "add_todo",
                    arguments =
                        buildJsonObject {
                            put("title", "Test todo")
                            put("type", "learning_target")
                        },
                )

            val todoSlot = slot<Todo>()
            coEvery { todoDao.insert(capture(todoSlot)) } returns Unit

            handler.handle(call, ToolExecutionContext())

            assertEquals(TodoPriority.MEDIUM, todoSlot.captured.priority)
        }

    @Test
    fun `handle add_todo fails when title missing`() =
        runTest {
            val call =
                LLMToolCall(
                    id = "call_123",
                    name = "add_todo",
                    arguments =
                        buildJsonObject {
                            put("type", "learning_target")
                        },
                )

            val result = handler.handle(call, ToolExecutionContext())

            assertTrue(result.isError)
            assertTrue(result.content.contains("Missing required argument: title"))
        }

    @Test
    fun `handle add_todo fails when type missing`() =
        runTest {
            val call =
                LLMToolCall(
                    id = "call_123",
                    name = "add_todo",
                    arguments =
                        buildJsonObject {
                            put("title", "Test todo")
                        },
                )

            val result = handler.handle(call, ToolExecutionContext())

            assertTrue(result.isError)
            assertTrue(result.content.contains("Missing required argument: type"))
        }

    @Test
    fun `handle add_todo fails when type invalid`() =
        runTest {
            val call =
                LLMToolCall(
                    id = "call_123",
                    name = "add_todo",
                    arguments =
                        buildJsonObject {
                            put("title", "Test todo")
                            put("type", "invalid_type")
                        },
                )

            val result = handler.handle(call, ToolExecutionContext())

            assertTrue(result.isError)
            assertTrue(result.content.contains("Invalid type"))
        }

    @Test
    fun `handle add_todo fails when dao throws`() =
        runTest {
            val call =
                LLMToolCall(
                    id = "call_123",
                    name = "add_todo",
                    arguments =
                        buildJsonObject {
                            put("title", "Test todo")
                            put("type", "learning_target")
                        },
                )

            coEvery { todoDao.insert(any()) } throws RuntimeException("Database error")

            val result = handler.handle(call, ToolExecutionContext())

            assertTrue(result.isError)
            assertTrue(result.content.contains("Failed to create todo"))
        }

    @Test
    fun `handle mark_for_review creates review todo`() =
        runTest {
            val call =
                LLMToolCall(
                    id = "call_123",
                    name = "mark_for_review",
                    arguments =
                        buildJsonObject {
                            put("reason", "Student confused about electron configuration")
                        },
                )

            val context =
                ToolExecutionContext(
                    sessionId = "session_abc",
                    topicId = "topic_xyz",
                    topicTitle = "Chemistry: Atomic Structure",
                )

            val todoSlot = slot<Todo>()
            coEvery { todoDao.insert(capture(todoSlot)) } returns Unit

            val result = handler.handle(call, context)

            assertFalse(result.isError)
            assertTrue(result.content.contains("Marked for review"))
            assertTrue(result.content.contains("Chemistry: Atomic Structure"))

            val capturedTodo = todoSlot.captured
            assertEquals("Review: Chemistry: Atomic Structure", capturedTodo.title)
            assertTrue(capturedTodo.notes?.contains("Student confused about electron configuration") == true)
            assertEquals(TodoPriority.MEDIUM, capturedTodo.priority)
            assertEquals("session_abc", capturedTodo.sessionId)
            assertEquals("topic_xyz", capturedTodo.topicId)
            assertTrue(capturedTodo.isAISuggested)
            assertEquals("Student confused about electron configuration", capturedTodo.suggestionReason)
        }

    @Test
    fun `handle mark_for_review without topic title`() =
        runTest {
            val call =
                LLMToolCall(
                    id = "call_123",
                    name = "mark_for_review",
                    arguments = buildJsonObject { },
                )

            val context = ToolExecutionContext()

            val todoSlot = slot<Todo>()
            coEvery { todoDao.insert(capture(todoSlot)) } returns Unit

            val result = handler.handle(call, context)

            assertFalse(result.isError)
            assertEquals("Review session material", todoSlot.captured.title)
        }

    @Test
    fun `handle mark_for_review without reason`() =
        runTest {
            val call =
                LLMToolCall(
                    id = "call_123",
                    name = "mark_for_review",
                    arguments = buildJsonObject { },
                )

            val context = ToolExecutionContext(topicTitle = "Math")

            val todoSlot = slot<Todo>()
            coEvery { todoDao.insert(capture(todoSlot)) } returns Unit

            val result = handler.handle(call, context)

            assertFalse(result.isError)
            assertEquals("Topic marked for review by AI tutor", todoSlot.captured.suggestionReason)
        }

    @Test
    fun `handle returns error for unknown tool`() =
        runTest {
            val call =
                LLMToolCall(
                    id = "call_123",
                    name = "unknown_tool",
                    arguments = buildJsonObject { },
                )

            val result = handler.handle(call, ToolExecutionContext())

            assertTrue(result.isError)
            assertTrue(result.content.contains("Unknown tool"))
        }

    @Test
    fun `priority parsing works for all levels`() =
        runTest {
            val priorities = listOf("low" to TodoPriority.LOW, "medium" to TodoPriority.MEDIUM, "high" to TodoPriority.HIGH)

            for ((input, expected) in priorities) {
                val call =
                    LLMToolCall(
                        id = "call_$input",
                        name = "add_todo",
                        arguments =
                            buildJsonObject {
                                put("title", "Test $input priority")
                                put("type", "learning_target")
                                put("priority", input)
                            },
                    )

                val todoSlot = slot<Todo>()
                coEvery { todoDao.insert(capture(todoSlot)) } returns Unit

                handler.handle(call, ToolExecutionContext())

                assertEquals("Priority $input should map to $expected", expected, todoSlot.captured.priority)
            }
        }

    @Test
    fun `priority parsing is case insensitive`() =
        runTest {
            val call =
                LLMToolCall(
                    id = "call_123",
                    name = "add_todo",
                    arguments =
                        buildJsonObject {
                            put("title", "Test")
                            put("type", "learning_target")
                            put("priority", "HIGH")
                        },
                )

            val todoSlot = slot<Todo>()
            coEvery { todoDao.insert(capture(todoSlot)) } returns Unit

            handler.handle(call, ToolExecutionContext())

            assertEquals(TodoPriority.HIGH, todoSlot.captured.priority)
        }
}
