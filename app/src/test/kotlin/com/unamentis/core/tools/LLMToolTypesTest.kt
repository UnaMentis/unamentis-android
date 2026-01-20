package com.unamentis.core.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LLMToolTypesTest {
    @Test
    fun `LLMToolCall getStringArgument returns correct value`() {
        val call =
            LLMToolCall(
                id = "test_id",
                name = "test_tool",
                arguments =
                    buildJsonObject {
                        put("name", "John")
                        put("number", 42)
                    },
            )

        assertEquals("John", call.getStringArgument("name"))
    }

    @Test
    fun `LLMToolCall getStringArgument returns null for missing key`() {
        val call =
            LLMToolCall(
                id = "test_id",
                name = "test_tool",
                arguments = buildJsonObject { },
            )

        assertNull(call.getStringArgument("missing"))
    }

    @Test
    fun `LLMToolCall getIntArgument returns correct value`() {
        val call =
            LLMToolCall(
                id = "test_id",
                name = "test_tool",
                arguments =
                    buildJsonObject {
                        put("count", 42)
                    },
            )

        assertEquals(42, call.getIntArgument("count"))
    }

    @Test
    fun `LLMToolCall getIntArgument returns null for non-integer`() {
        val call =
            LLMToolCall(
                id = "test_id",
                name = "test_tool",
                arguments =
                    buildJsonObject {
                        put("name", "not a number")
                    },
            )

        assertNull(call.getIntArgument("name"))
    }

    @Test
    fun `LLMToolCall getBooleanArgument returns correct value`() {
        val call =
            LLMToolCall(
                id = "test_id",
                name = "test_tool",
                arguments =
                    buildJsonObject {
                        put("enabled", true)
                        put("disabled", false)
                    },
            )

        assertEquals(true, call.getBooleanArgument("enabled"))
        assertEquals(false, call.getBooleanArgument("disabled"))
    }

    @Test
    fun `LLMToolCall getBooleanArgument returns null for non-boolean`() {
        val call =
            LLMToolCall(
                id = "test_id",
                name = "test_tool",
                arguments =
                    buildJsonObject {
                        put("name", "not a boolean")
                    },
            )

        assertNull(call.getBooleanArgument("name"))
    }

    @Test
    fun `LLMToolResult success creates non-error result`() {
        val result = LLMToolResult.success("call_123", "Success message")

        assertEquals("call_123", result.toolCallId)
        assertEquals("Success message", result.content)
        assertFalse(result.isError)
    }

    @Test
    fun `LLMToolResult error creates error result`() {
        val result = LLMToolResult.error("call_123", "Something went wrong")

        assertEquals("call_123", result.toolCallId)
        assertTrue(result.content.contains("Error"))
        assertTrue(result.content.contains("Something went wrong"))
        assertTrue(result.isError)
    }

    @Test
    fun `LLMResponseWithTools hasToolCalls returns true when calls present`() {
        val response =
            LLMResponseWithTools(
                content = "",
                toolCalls =
                    listOf(
                        LLMToolCall(
                            id = "call_1",
                            name = "test_tool",
                            arguments = buildJsonObject { },
                        ),
                    ),
                stopReason = "tool_use",
            )

        assertTrue(response.hasToolCalls)
    }

    @Test
    fun `LLMResponseWithTools hasToolCalls returns false when no calls`() {
        val response =
            LLMResponseWithTools(
                content = "Hello",
                toolCalls = emptyList(),
                stopReason = "end_turn",
            )

        assertFalse(response.hasToolCalls)
    }

    @Test
    fun `ToolParameters creates valid schema`() {
        val params =
            ToolParameters(
                properties =
                    mapOf(
                        "name" to
                            ToolProperty(
                                type = "string",
                                description = "User name",
                            ),
                        "age" to
                            ToolProperty(
                                type = "number",
                                description = "User age",
                            ),
                        "role" to
                            ToolProperty(
                                type = "string",
                                description = "User role",
                                enum = listOf("admin", "user", "guest"),
                            ),
                    ),
                required = listOf("name"),
            )

        assertEquals("object", params.type)
        assertEquals(3, params.properties.size)
        assertEquals("string", params.properties["name"]?.type)
        assertEquals("number", params.properties["age"]?.type)
        assertEquals(listOf("admin", "user", "guest"), params.properties["role"]?.enum)
        assertEquals(listOf("name"), params.required)
    }

    @Test
    fun `ToolExecutionContext holds all values`() {
        val context =
            ToolExecutionContext(
                sessionId = "session_123",
                topicId = "topic_456",
                topicTitle = "Introduction to Algebra",
                userId = "user_789",
            )

        assertEquals("session_123", context.sessionId)
        assertEquals("topic_456", context.topicId)
        assertEquals("Introduction to Algebra", context.topicTitle)
        assertEquals("user_789", context.userId)
    }

    @Test
    fun `ToolExecutionContext has null defaults`() {
        val context = ToolExecutionContext()

        assertNull(context.sessionId)
        assertNull(context.topicId)
        assertNull(context.topicTitle)
        assertNull(context.userId)
    }
}
