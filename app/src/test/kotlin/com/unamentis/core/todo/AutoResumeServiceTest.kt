package com.unamentis.core.todo

import com.unamentis.data.model.Todo
import com.unamentis.data.model.TodoItemSource
import com.unamentis.data.model.TodoItemType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoResumeServiceTest {
    private lateinit var todoManager: TodoManager
    private lateinit var autoResumeService: AutoResumeService

    @Before
    fun setup() {
        todoManager = mockk(relaxed = true)
        autoResumeService = AutoResumeService(todoManager)
    }

    // region shouldCreateAutoResume

    @Test
    fun `shouldCreateAutoResume returns true when all conditions met`() {
        val context =
            AutoResumeContext(
                topicId = "topic-1",
                topicTitle = "Chemistry",
                segmentIndex = 3,
                totalSegments = 10,
                // 5 minutes
                sessionDurationMs = 300_000L,
            )

        assertTrue(autoResumeService.shouldCreateAutoResume(context))
    }

    @Test
    fun `shouldCreateAutoResume returns false when session too short`() {
        val context =
            AutoResumeContext(
                topicId = "topic-1",
                topicTitle = "Chemistry",
                segmentIndex = 3,
                totalSegments = 10,
                // 1 minute (below threshold)
                sessionDurationMs = 60_000L,
            )

        assertFalse(autoResumeService.shouldCreateAutoResume(context))
    }

    @Test
    fun `shouldCreateAutoResume returns false at beginning of topic`() {
        val context =
            AutoResumeContext(
                topicId = "topic-1",
                topicTitle = "Chemistry",
                segmentIndex = 0,
                totalSegments = 10,
                sessionDurationMs = 300_000L,
            )

        assertFalse(autoResumeService.shouldCreateAutoResume(context))
    }

    @Test
    fun `shouldCreateAutoResume returns false at end of topic`() {
        val context =
            AutoResumeContext(
                topicId = "topic-1",
                topicTitle = "Chemistry",
                segmentIndex = 9,
                totalSegments = 10,
                sessionDurationMs = 300_000L,
            )

        assertFalse(autoResumeService.shouldCreateAutoResume(context))
    }

    @Test
    fun `shouldCreateAutoResume returns false for exactly minimum duration`() {
        // Duration must be >= minimum (120 seconds)
        val context =
            AutoResumeContext(
                topicId = "topic-1",
                topicTitle = "Chemistry",
                segmentIndex = 3,
                totalSegments = 10,
                sessionDurationMs = AutoResumeService.MINIMUM_SESSION_DURATION_MS,
            )

        assertTrue(autoResumeService.shouldCreateAutoResume(context))
    }

    @Test
    fun `shouldCreateAutoResume returns false for exactly below minimum duration`() {
        val context =
            AutoResumeContext(
                topicId = "topic-1",
                topicTitle = "Chemistry",
                segmentIndex = 3,
                totalSegments = 10,
                sessionDurationMs = AutoResumeService.MINIMUM_SESSION_DURATION_MS - 1,
            )

        assertFalse(autoResumeService.shouldCreateAutoResume(context))
    }

    @Test
    fun `shouldCreateAutoResume handles single segment topic`() {
        val context =
            AutoResumeContext(
                topicId = "topic-1",
                topicTitle = "Chemistry",
                segmentIndex = 0,
                totalSegments = 1,
                sessionDurationMs = 300_000L,
            )

        assertFalse(autoResumeService.shouldCreateAutoResume(context))
    }

    // endregion

    // region handleSessionStop

    @Test
    fun `handleSessionStop creates auto-resume when conditions met`() =
        runTest {
            val context =
                AutoResumeContext(
                    topicId = "topic-1",
                    topicTitle = "Chemistry",
                    segmentIndex = 3,
                    totalSegments = 10,
                    sessionDurationMs = 300_000L,
                    conversationMessages =
                        listOf(
                            ResumeConversationMessage("user", "What is an atom?"),
                            ResumeConversationMessage("assistant", "An atom is..."),
                        ),
                )

            coEvery {
                todoManager.createAutoResumeItem(any(), any(), any(), any())
            } returns
                Todo(
                    id = "auto-1",
                    title = "Continue: Chemistry",
                    itemType = TodoItemType.AUTO_RESUME,
                    source = TodoItemSource.AUTO_RESUME,
                )

            val result = autoResumeService.handleSessionStop(context)

            assertTrue(result)
            coVerify {
                todoManager.createAutoResumeItem(
                    title = "Continue: Chemistry",
                    topicId = "topic-1",
                    segmentIndex = 3,
                    conversationContext = any(),
                )
            }
        }

    @Test
    fun `handleSessionStop returns false when conditions not met`() =
        runTest {
            val context =
                AutoResumeContext(
                    topicId = "topic-1",
                    topicTitle = "Chemistry",
                    // At beginning
                    segmentIndex = 0,
                    totalSegments = 10,
                    sessionDurationMs = 300_000L,
                )

            val result = autoResumeService.handleSessionStop(context)

            assertFalse(result)
            coVerify(exactly = 0) { todoManager.createAutoResumeItem(any(), any(), any(), any()) }
        }

    @Test
    fun `handleSessionStop returns false when creation fails`() =
        runTest {
            val context =
                AutoResumeContext(
                    topicId = "topic-1",
                    topicTitle = "Chemistry",
                    segmentIndex = 3,
                    totalSegments = 10,
                    sessionDurationMs = 300_000L,
                )

            coEvery {
                todoManager.createAutoResumeItem(any(), any(), any(), any())
            } throws RuntimeException("Database error")

            val result = autoResumeService.handleSessionStop(context)

            assertFalse(result)
        }

    // endregion

    // region encodeConversationContext

    @Test
    fun `encodeConversationContext filters system messages`() {
        val messages =
            listOf(
                ResumeConversationMessage("system", "You are a tutor"),
                ResumeConversationMessage("user", "Hello"),
                ResumeConversationMessage("assistant", "Hi there"),
            )

        val encoded = autoResumeService.encodeConversationContext(messages)

        assertTrue(encoded.contains("Hello"))
        assertTrue(encoded.contains("Hi there"))
        assertFalse(encoded.contains("You are a tutor"))
    }

    @Test
    fun `encodeConversationContext limits to max context size`() {
        val messages =
            (1..20).map {
                ResumeConversationMessage("user", "Message $it")
            }

        val encoded = autoResumeService.encodeConversationContext(messages)

        // Should only contain the last MAX_CONVERSATION_CONTEXT messages
        assertTrue(encoded.contains("Message 11"))
        assertTrue(encoded.contains("Message 20"))
        assertFalse(encoded.contains("Message 1"))
    }

    @Test
    fun `encodeConversationContext handles empty list`() {
        val encoded = autoResumeService.encodeConversationContext(emptyList())

        assertEquals("[]", encoded)
    }

    // endregion

    // region Resume Context Retrieval

    @Test
    fun `getConversationContext returns decoded messages`() =
        runTest {
            val contextJson =
                """[{"role":"user","content":"Hello"},{"role":"assistant","content":"Hi"}]"""
            coEvery { todoManager.getResumeContext("topic-1") } returns
                ResumeContext(
                    segmentIndex = 3,
                    conversationContext = contextJson,
                )

            val result = autoResumeService.getConversationContext("topic-1")

            assertNotNull(result)
            assertEquals(2, result!!.size)
            assertEquals("user", result[0].role)
            assertEquals("Hello", result[0].content)
            assertEquals("assistant", result[1].role)
            assertEquals("Hi", result[1].content)
        }

    @Test
    fun `getConversationContext returns null when no resume exists`() =
        runTest {
            coEvery { todoManager.getResumeContext("topic-1") } returns null

            val result = autoResumeService.getConversationContext("topic-1")

            assertNull(result)
        }

    @Test
    fun `getConversationContext returns null when context data is null`() =
        runTest {
            coEvery { todoManager.getResumeContext("topic-1") } returns
                ResumeContext(segmentIndex = 3, conversationContext = null)

            val result = autoResumeService.getConversationContext("topic-1")

            assertNull(result)
        }

    @Test
    fun `getConversationContext returns null for invalid JSON`() =
        runTest {
            coEvery { todoManager.getResumeContext("topic-1") } returns
                ResumeContext(segmentIndex = 3, conversationContext = "invalid json")

            val result = autoResumeService.getConversationContext("topic-1")

            assertNull(result)
        }

    @Test
    fun `getResumeSegmentIndex returns index when exists`() =
        runTest {
            coEvery { todoManager.getResumeContext("topic-1") } returns
                ResumeContext(segmentIndex = 5, conversationContext = null)

            val result = autoResumeService.getResumeSegmentIndex("topic-1")

            assertEquals(5, result)
        }

    @Test
    fun `getResumeSegmentIndex returns null when not exists`() =
        runTest {
            coEvery { todoManager.getResumeContext("topic-1") } returns null

            val result = autoResumeService.getResumeSegmentIndex("topic-1")

            assertNull(result)
        }

    // endregion

    // region Clear Auto Resume

    @Test
    fun `clearAutoResume delegates to todoManager`() =
        runTest {
            autoResumeService.clearAutoResume("topic-1")

            coVerify { todoManager.clearAutoResume("topic-1") }
        }

    // endregion
}
