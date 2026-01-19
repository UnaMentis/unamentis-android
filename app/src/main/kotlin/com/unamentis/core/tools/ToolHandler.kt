package com.unamentis.core.tools

/**
 * Interface for handling LLM tool calls.
 *
 * Implementations provide specific functionality that the LLM can invoke
 * during conversations, such as creating todos, expanding context, or
 * controlling app features.
 *
 * Tool handlers are registered with the [ToolCallProcessor] and automatically
 * invoked when the LLM requests the corresponding tool.
 *
 * ## Example Implementation
 * ```kotlin
 * class MyToolHandler @Inject constructor(
 *     private val myService: MyService
 * ) : ToolHandler {
 *     override val toolName = "my_tool"
 *
 *     override fun getDefinition(): LLMToolDefinition {
 *         return LLMToolDefinition(
 *             name = toolName,
 *             description = "Does something useful",
 *             parameters = ToolParameters(
 *                 properties = mapOf(
 *                     "input" to ToolProperty(type = "string", description = "Input value")
 *                 ),
 *                 required = listOf("input")
 *             )
 *         )
 *     }
 *
 *     override suspend fun handle(
 *         call: LLMToolCall,
 *         context: ToolExecutionContext
 *     ): LLMToolResult {
 *         val input = call.getStringArgument("input") ?: return LLMToolResult.error(
 *             call.id, "Missing required argument: input"
 *         )
 *         val result = myService.doSomething(input)
 *         return LLMToolResult.success(call.id, "Success: $result")
 *     }
 * }
 * ```
 */
interface ToolHandler {
    /**
     * Unique name of this tool.
     *
     * Must match the name in the tool definition and be unique
     * across all registered handlers.
     */
    val toolName: String

    /**
     * Get the tool definition for API registration.
     *
     * This definition is sent to the LLM provider so it knows
     * what tools are available and how to call them.
     *
     * @return Tool definition with name, description, and parameter schema
     */
    fun getDefinition(): LLMToolDefinition

    /**
     * Handle a tool call from the LLM.
     *
     * This method is invoked when the LLM requests this tool.
     * It should:
     * 1. Parse and validate the arguments
     * 2. Perform the requested action
     * 3. Return a result for the LLM to incorporate
     *
     * Implementations should handle errors gracefully and return
     * [LLMToolResult.error] rather than throwing exceptions.
     *
     * @param call The tool call containing arguments
     * @param context Execution context with session/topic info
     * @return Result to send back to the LLM
     */
    suspend fun handle(
        call: LLMToolCall,
        context: ToolExecutionContext,
    ): LLMToolResult
}

/**
 * Exception thrown when tool handling fails.
 *
 * @property toolName Name of the tool that failed
 * @property reason Reason for the failure
 */
class ToolHandlerException(
    val toolName: String,
    val reason: String,
    cause: Throwable? = null,
) : Exception("Tool '$toolName' failed: $reason", cause)
