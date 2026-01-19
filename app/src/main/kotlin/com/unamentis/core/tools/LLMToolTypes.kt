package com.unamentis.core.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Definition of an LLM tool that can be invoked during conversation.
 *
 * This follows the standard function calling schema used by both
 * Anthropic and OpenAI APIs.
 *
 * @property name Unique identifier for the tool
 * @property description Human-readable description of what the tool does
 * @property parameters JSON Schema describing the tool's input parameters
 */
@Serializable
data class LLMToolDefinition(
    val name: String,
    val description: String,
    val parameters: ToolParameters,
)

/**
 * JSON Schema for tool parameters.
 *
 * @property type Always "object" for tool parameters
 * @property properties Map of property names to their schemas
 * @property required List of required property names
 */
@Serializable
data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, ToolProperty>,
    val required: List<String> = emptyList(),
)

/**
 * JSON Schema for a single tool property.
 *
 * @property type JSON type ("string", "number", "boolean", "array", "object")
 * @property description Human-readable description
 * @property enum Optional list of allowed values
 * @property items Schema for array items (if type is "array")
 */
@Serializable
data class ToolProperty(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null,
    val items: ToolProperty? = null,
)

/**
 * A tool call request from the LLM.
 *
 * When the LLM decides to use a tool, it returns one or more of these
 * objects specifying which tool to call and with what arguments.
 *
 * @property id Unique identifier for this tool call (used to correlate results)
 * @property name Name of the tool to invoke
 * @property arguments Raw JSON arguments from the LLM
 */
@Serializable
data class LLMToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject,
) {
    /**
     * Parse arguments into a typed data class.
     *
     * @param T The type to deserialize arguments into
     * @return Parsed arguments
     * @throws kotlinx.serialization.SerializationException if parsing fails
     */
    inline fun <reified T> parseArguments(): T {
        return toolJson.decodeFromJsonElement(arguments)
    }

    /**
     * Get a string argument by name.
     *
     * @param name Argument name
     * @return String value or null if not present
     */
    fun getStringArgument(name: String): String? {
        return arguments[name]?.let { element ->
            try {
                toolJson.decodeFromJsonElement<String>(element)
            } catch (e: Exception) {
                element.toString().removeSurrounding("\"")
            }
        }
    }

    /**
     * Get an integer argument by name.
     *
     * @param name Argument name
     * @return Int value or null if not present
     */
    fun getIntArgument(name: String): Int? {
        return arguments[name]?.let { element ->
            try {
                toolJson.decodeFromJsonElement<Int>(element)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Get a boolean argument by name.
     *
     * @param name Argument name
     * @return Boolean value or null if not present
     */
    fun getBooleanArgument(name: String): Boolean? {
        return arguments[name]?.let { element ->
            try {
                toolJson.decodeFromJsonElement<Boolean>(element)
            } catch (e: Exception) {
                null
            }
        }
    }

    companion object {
        @PublishedApi
        internal val toolJson =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
    }
}

/**
 * Result of executing a tool.
 *
 * This is returned to the LLM so it can incorporate the tool's
 * output into its response.
 *
 * @property toolCallId ID of the tool call this result corresponds to
 * @property content Result content (typically stringified JSON or plain text)
 * @property isError Whether the tool execution failed
 */
@Serializable
data class LLMToolResult(
    @SerialName("tool_call_id")
    val toolCallId: String,
    val content: String,
    @SerialName("is_error")
    val isError: Boolean = false,
) {
    companion object {
        /**
         * Create a successful tool result.
         *
         * @param toolCallId ID of the tool call
         * @param content Success message or data
         * @return Successful result
         */
        fun success(
            toolCallId: String,
            content: String,
        ): LLMToolResult {
            return LLMToolResult(
                toolCallId = toolCallId,
                content = content,
                isError = false,
            )
        }

        /**
         * Create an error tool result.
         *
         * @param toolCallId ID of the tool call
         * @param error Error message
         * @return Error result
         */
        fun error(
            toolCallId: String,
            error: String,
        ): LLMToolResult {
            return LLMToolResult(
                toolCallId = toolCallId,
                content = "Error: $error",
                isError = true,
            )
        }
    }
}

/**
 * Extended LLM response that may contain tool calls.
 *
 * @property content Text content of the response (may be empty if only tool calls)
 * @property toolCalls List of tool calls requested by the LLM
 * @property stopReason Why generation stopped ("end_turn", "tool_use", "max_tokens")
 */
@Serializable
data class LLMResponseWithTools(
    val content: String = "",
    @SerialName("tool_calls")
    val toolCalls: List<LLMToolCall> = emptyList(),
    @SerialName("stop_reason")
    val stopReason: String = "end_turn",
) {
    /**
     * Whether this response contains tool calls that need to be processed.
     */
    val hasToolCalls: Boolean
        get() = toolCalls.isNotEmpty()
}

/**
 * Context passed to tool handlers for execution.
 *
 * Provides information about the current session and topic
 * that tools may need to perform their actions.
 *
 * @property sessionId Current session ID (if any)
 * @property topicId Current curriculum topic ID (if any)
 * @property topicTitle Current topic title (if any)
 * @property userId User identifier (if authenticated)
 */
data class ToolExecutionContext(
    val sessionId: String? = null,
    val topicId: String? = null,
    val topicTitle: String? = null,
    val userId: String? = null,
)
