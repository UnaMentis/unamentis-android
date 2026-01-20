package com.unamentis.core.tools

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ToolCallProcessorTest {
    private lateinit var mockHandler1: ToolHandler
    private lateinit var mockHandler2: ToolHandler
    private lateinit var processor: ToolCallProcessor

    @Before
    fun setup() {
        mockHandler1 = mockk(relaxed = true)
        coEvery { mockHandler1.toolName } returns "test_tool_1"
        coEvery { mockHandler1.getDefinition() } returns
            LLMToolDefinition(
                name = "test_tool_1",
                description = "Test tool 1",
                parameters =
                    ToolParameters(
                        properties =
                            mapOf(
                                "input" to ToolProperty(type = "string", description = "Input value"),
                            ),
                        required = listOf("input"),
                    ),
            )

        mockHandler2 = mockk(relaxed = true)
        coEvery { mockHandler2.toolName } returns "test_tool_2"
        coEvery { mockHandler2.getDefinition() } returns
            LLMToolDefinition(
                name = "test_tool_2",
                description = "Test tool 2",
                parameters =
                    ToolParameters(
                        properties =
                            mapOf(
                                "count" to ToolProperty(type = "number", description = "Count value"),
                            ),
                        required = listOf("count"),
                    ),
            )

        processor = ToolCallProcessor(setOf(mockHandler1, mockHandler2))
    }

    @Test
    fun `getAvailableTools returns all registered tools`() {
        val tools = processor.getAvailableTools()

        assertEquals(2, tools.size)
        assertTrue(tools.any { it.name == "test_tool_1" })
        assertTrue(tools.any { it.name == "test_tool_2" })
    }

    @Test
    fun `hasHandler returns true for registered tool`() {
        assertTrue(processor.hasHandler("test_tool_1"))
        assertTrue(processor.hasHandler("test_tool_2"))
    }

    @Test
    fun `hasHandler returns false for unregistered tool`() {
        assertFalse(processor.hasHandler("unknown_tool"))
    }

    @Test
    fun `processToolCall routes to correct handler`() =
        runTest {
            val call =
                LLMToolCall(
                    id = "call_1",
                    name = "test_tool_1",
                    arguments = buildJsonObject { put("input", "test value") },
                )

            val expectedResult = LLMToolResult.success("call_1", "Success")
            coEvery { mockHandler1.handle(any(), any()) } returns expectedResult

            val result = processor.processToolCall(call)

            assertEquals(expectedResult, result)
            coVerify { mockHandler1.handle(call, any()) }
        }

    @Test
    fun `processToolCall returns error for unknown tool`() =
        runTest {
            val call =
                LLMToolCall(
                    id = "call_1",
                    name = "unknown_tool",
                    arguments = buildJsonObject { },
                )

            val result = processor.processToolCall(call)

            assertTrue(result.isError)
            assertTrue(result.content.contains("Unknown tool"))
        }

    @Test
    fun `processToolCall returns error when handler throws`() =
        runTest {
            val call =
                LLMToolCall(
                    id = "call_1",
                    name = "test_tool_1",
                    arguments = buildJsonObject { put("input", "test") },
                )

            coEvery { mockHandler1.handle(any(), any()) } throws RuntimeException("Handler failed")

            val result = processor.processToolCall(call)

            assertTrue(result.isError)
            assertTrue(result.content.contains("Internal error"))
        }

    @Test
    fun `processToolCalls processes multiple calls in parallel`() =
        runTest {
            val call1 =
                LLMToolCall(
                    id = "call_1",
                    name = "test_tool_1",
                    arguments = buildJsonObject { put("input", "value1") },
                )
            val call2 =
                LLMToolCall(
                    id = "call_2",
                    name = "test_tool_2",
                    arguments = buildJsonObject { put("count", 42) },
                )

            coEvery { mockHandler1.handle(any(), any()) } returns LLMToolResult.success("call_1", "Result 1")
            coEvery { mockHandler2.handle(any(), any()) } returns LLMToolResult.success("call_2", "Result 2")

            val results = processor.processToolCalls(listOf(call1, call2))

            assertEquals(2, results.size)
            assertEquals("call_1", results[0].toolCallId)
            assertEquals("call_2", results[1].toolCallId)
        }

    @Test
    fun `configureContext sets context for handlers`() =
        runTest {
            processor.configureContext(
                sessionId = "session_123",
                topicId = "topic_456",
                topicTitle = "Test Topic",
                userId = "user_789",
            )

            val call =
                LLMToolCall(
                    id = "call_1",
                    name = "test_tool_1",
                    arguments = buildJsonObject { put("input", "test") },
                )

            coEvery { mockHandler1.handle(any(), any()) } answers {
                val context = secondArg<ToolExecutionContext>()
                assertEquals("session_123", context.sessionId)
                assertEquals("topic_456", context.topicId)
                assertEquals("Test Topic", context.topicTitle)
                assertEquals("user_789", context.userId)
                LLMToolResult.success(call.id, "OK")
            }

            processor.processToolCall(call)

            coVerify { mockHandler1.handle(call, any()) }
        }

    @Test
    fun `clearContext resets context`() =
        runTest {
            processor.configureContext(sessionId = "session_123")
            processor.clearContext()

            val call =
                LLMToolCall(
                    id = "call_1",
                    name = "test_tool_1",
                    arguments = buildJsonObject { put("input", "test") },
                )

            coEvery { mockHandler1.handle(any(), any()) } answers {
                val context = secondArg<ToolExecutionContext>()
                assertEquals(null, context.sessionId)
                LLMToolResult.success(call.id, "OK")
            }

            processor.processToolCall(call)
        }

    @Test
    fun `registerHandler adds new handler`() =
        runTest {
            val newHandler = mockk<ToolHandler>(relaxed = true)
            coEvery { newHandler.toolName } returns "new_tool"
            coEvery { newHandler.getDefinition() } returns
                LLMToolDefinition(
                    name = "new_tool",
                    description = "New tool",
                    parameters = ToolParameters(properties = emptyMap()),
                )

            processor.registerHandler(newHandler)

            assertTrue(processor.hasHandler("new_tool"))
            assertEquals(3, processor.getAvailableTools().size)
        }

    @Test
    fun `unregisterHandler removes handler`() =
        runTest {
            val removed = processor.unregisterHandler("test_tool_1")

            assertTrue(removed)
            assertFalse(processor.hasHandler("test_tool_1"))
            assertEquals(1, processor.getAvailableTools().size)
        }

    @Test
    fun `unregisterHandler returns false for unknown tool`() =
        runTest {
            val removed = processor.unregisterHandler("unknown_tool")

            assertFalse(removed)
        }

    @Test
    fun `getAnthropicToolDefinitions formats correctly`() {
        val definitions = processor.getAnthropicToolDefinitions()

        assertEquals(2, definitions.size)

        val tool1 = definitions.find { it["name"] == "test_tool_1" }!!
        assertEquals("Test tool 1", tool1["description"])

        @Suppress("UNCHECKED_CAST")
        val inputSchema = tool1["input_schema"] as Map<String, Any>
        assertEquals("object", inputSchema["type"])

        @Suppress("UNCHECKED_CAST")
        val properties = inputSchema["properties"] as Map<String, Any>
        assertTrue(properties.containsKey("input"))

        @Suppress("UNCHECKED_CAST")
        val required = inputSchema["required"] as List<String>
        assertTrue(required.contains("input"))
    }

    @Test
    fun `getOpenAIToolDefinitions formats correctly`() {
        val definitions = processor.getOpenAIToolDefinitions()

        assertEquals(2, definitions.size)

        val tool1 =
            definitions.find {
                @Suppress("UNCHECKED_CAST")
                (it["function"] as? Map<String, Any>)?.get("name") == "test_tool_1"
            }!!

        assertEquals("function", tool1["type"])

        @Suppress("UNCHECKED_CAST")
        val function = tool1["function"] as Map<String, Any>
        assertEquals("test_tool_1", function["name"])
        assertEquals("Test tool 1", function["description"])

        @Suppress("UNCHECKED_CAST")
        val parameters = function["parameters"] as Map<String, Any>
        assertEquals("object", parameters["type"])
    }

    @Test
    fun `formatAnthropicToolResults formats correctly`() {
        val results =
            listOf(
                LLMToolResult.success("call_1", "Result 1"),
                LLMToolResult.error("call_2", "Failed"),
            )

        val formatted = processor.formatAnthropicToolResults(results)

        assertEquals(2, formatted.size)

        assertEquals("tool_result", formatted[0]["type"])
        assertEquals("call_1", formatted[0]["tool_use_id"])
        assertEquals("Result 1", formatted[0]["content"])
        assertEquals(false, formatted[0]["is_error"])

        assertEquals("tool_result", formatted[1]["type"])
        assertEquals("call_2", formatted[1]["tool_use_id"])
        assertTrue((formatted[1]["content"] as String).contains("Error"))
        assertEquals(true, formatted[1]["is_error"])
    }

    @Test
    fun `formatOpenAIToolResults formats correctly`() {
        val results =
            listOf(
                LLMToolResult.success("call_1", "Result 1"),
                LLMToolResult.success("call_2", "Result 2"),
            )

        val formatted = processor.formatOpenAIToolResults(results)

        assertEquals(2, formatted.size)

        assertEquals("tool", formatted[0]["role"])
        assertEquals("call_1", formatted[0]["tool_call_id"])
        assertEquals("Result 1", formatted[0]["content"])

        assertEquals("tool", formatted[1]["role"])
        assertEquals("call_2", formatted[1]["tool_call_id"])
        assertEquals("Result 2", formatted[1]["content"])
    }
}
