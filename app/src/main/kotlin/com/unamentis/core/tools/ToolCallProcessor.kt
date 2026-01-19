package com.unamentis.core.tools

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central processor for routing LLM tool calls to their handlers.
 *
 * This class manages the registry of available tools and coordinates
 * their execution when the LLM requests them. It supports:
 * - Dynamic tool registration/unregistration
 * - Parallel execution of multiple tool calls
 * - Context propagation to handlers
 * - Formatting tool definitions for different LLM providers
 *
 * ## Usage
 * ```kotlin
 * // Register handlers (typically done via Hilt DI)
 * processor.registerHandler(todoHandler)
 * processor.registerHandler(contextExpansionHandler)
 *
 * // Get tool definitions for LLM API
 * val tools = processor.getAnthropicToolDefinitions()
 *
 * // Process tool calls from LLM response
 * val results = processor.processToolCalls(response.toolCalls, context)
 * ```
 *
 * @property handlers Set of tool handlers injected via Hilt
 */
@Singleton
class ToolCallProcessor
    @Inject
    constructor(
        handlers: Set<@JvmSuppressWildcards ToolHandler>,
    ) {
        private val handlerMap = mutableMapOf<String, ToolHandler>()
        private val mutex = Mutex()
        private var currentContext = ToolExecutionContext()

        init {
            // Register all injected handlers
            handlers.forEach { handler ->
                handlerMap[handler.toolName] = handler
                Log.d(TAG, "Registered tool handler: ${handler.toolName}")
            }
        }

        /**
         * Register a tool handler dynamically.
         *
         * @param handler Handler to register
         * @throws IllegalStateException if a handler with the same name is already registered
         */
        suspend fun registerHandler(handler: ToolHandler) {
            mutex.withLock {
                if (handlerMap.containsKey(handler.toolName)) {
                    throw IllegalStateException(
                        "Handler for tool '${handler.toolName}' is already registered",
                    )
                }
                handlerMap[handler.toolName] = handler
                Log.d(TAG, "Dynamically registered tool handler: ${handler.toolName}")
            }
        }

        /**
         * Unregister a tool handler.
         *
         * @param toolName Name of the tool to unregister
         * @return True if a handler was removed, false if not found
         */
        suspend fun unregisterHandler(toolName: String): Boolean {
            return mutex.withLock {
                val removed = handlerMap.remove(toolName) != null
                if (removed) {
                    Log.d(TAG, "Unregistered tool handler: $toolName")
                }
                removed
            }
        }

        /**
         * Configure the execution context for tool handlers.
         *
         * This context is passed to all handlers when processing tool calls.
         *
         * @param sessionId Current session ID
         * @param topicId Current topic ID
         * @param topicTitle Current topic title
         * @param userId Current user ID
         */
        suspend fun configureContext(
            sessionId: String? = null,
            topicId: String? = null,
            topicTitle: String? = null,
            userId: String? = null,
        ) {
            mutex.withLock {
                currentContext =
                    ToolExecutionContext(
                        sessionId = sessionId,
                        topicId = topicId,
                        topicTitle = topicTitle,
                        userId = userId,
                    )
            }
        }

        /**
         * Clear the execution context.
         */
        suspend fun clearContext() {
            mutex.withLock {
                currentContext = ToolExecutionContext()
            }
        }

        /**
         * Get all available tool definitions.
         *
         * @return List of tool definitions for all registered handlers
         */
        fun getAvailableTools(): List<LLMToolDefinition> {
            return handlerMap.values.map { it.getDefinition() }
        }

        /**
         * Check if a specific tool is available.
         *
         * @param toolName Name of the tool to check
         * @return True if the tool is registered
         */
        fun hasHandler(toolName: String): Boolean {
            return handlerMap.containsKey(toolName)
        }

        /**
         * Process a single tool call.
         *
         * @param call Tool call to process
         * @return Result from the tool handler
         */
        suspend fun processToolCall(call: LLMToolCall): LLMToolResult {
            val handler =
                handlerMap[call.name]
                    ?: return LLMToolResult.error(
                        call.id,
                        "Unknown tool: ${call.name}. Available tools: ${handlerMap.keys.joinToString()}",
                    )

            return try {
                val context = mutex.withLock { currentContext }
                Log.d(TAG, "Processing tool call: ${call.name} with args: ${call.arguments}")
                val result = handler.handle(call, context)
                Log.d(TAG, "Tool call ${call.name} completed: ${if (result.isError) "ERROR" else "SUCCESS"}")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Tool call ${call.name} threw exception", e)
                LLMToolResult.error(call.id, "Internal error: ${e.message ?: "Unknown error"}")
            }
        }

        /**
         * Process multiple tool calls in parallel.
         *
         * All calls are executed concurrently and results are returned
         * in the same order as the input calls.
         *
         * @param calls List of tool calls to process
         * @return List of results in the same order as input
         */
        suspend fun processToolCalls(calls: List<LLMToolCall>): List<LLMToolResult> {
            if (calls.isEmpty()) return emptyList()

            return coroutineScope {
                calls.map { call ->
                    async { processToolCall(call) }
                }.awaitAll()
            }
        }

        /**
         * Format tool definitions for Anthropic API.
         *
         * Returns a list of tool objects in Anthropic's expected format:
         * ```json
         * [
         *   {
         *     "name": "tool_name",
         *     "description": "What the tool does",
         *     "input_schema": {
         *       "type": "object",
         *       "properties": { ... },
         *       "required": [ ... ]
         *     }
         *   }
         * ]
         * ```
         *
         * @return JSON-compatible list of tool definitions
         */
        fun getAnthropicToolDefinitions(): List<Map<String, Any>> {
            return handlerMap.values.map { handler ->
                val def = handler.getDefinition()
                mapOf(
                    "name" to def.name,
                    "description" to def.description,
                    "input_schema" to
                        mapOf(
                            "type" to def.parameters.type,
                            "properties" to
                                def.parameters.properties.mapValues { (_, prop) ->
                                    buildMap {
                                        put("type", prop.type)
                                        prop.description?.let { put("description", it) }
                                        prop.enum?.let { put("enum", it) }
                                        prop.items?.let { items ->
                                            put("items", mapOf("type" to items.type))
                                        }
                                    }
                                },
                            "required" to def.parameters.required,
                        ),
                )
            }
        }

        /**
         * Format tool definitions for OpenAI API.
         *
         * Returns a list of function objects in OpenAI's expected format:
         * ```json
         * [
         *   {
         *     "type": "function",
         *     "function": {
         *       "name": "tool_name",
         *       "description": "What the tool does",
         *       "parameters": {
         *         "type": "object",
         *         "properties": { ... },
         *         "required": [ ... ]
         *       }
         *     }
         *   }
         * ]
         * ```
         *
         * @return JSON-compatible list of function definitions
         */
        fun getOpenAIToolDefinitions(): List<Map<String, Any>> {
            return handlerMap.values.map { handler ->
                val def = handler.getDefinition()
                mapOf(
                    "type" to "function",
                    "function" to
                        mapOf(
                            "name" to def.name,
                            "description" to def.description,
                            "parameters" to
                                mapOf(
                                    "type" to def.parameters.type,
                                    "properties" to
                                        def.parameters.properties.mapValues { (_, prop) ->
                                            buildMap {
                                                put("type", prop.type)
                                                prop.description?.let { put("description", it) }
                                                prop.enum?.let { put("enum", it) }
                                                prop.items?.let { items ->
                                                    put("items", mapOf("type" to items.type))
                                                }
                                            }
                                        },
                                    "required" to def.parameters.required,
                                ),
                        ),
                )
            }
        }

        /**
         * Format tool results for sending back to Anthropic API.
         *
         * @param results List of tool results to format
         * @return List of tool result content blocks
         */
        fun formatAnthropicToolResults(results: List<LLMToolResult>): List<Map<String, Any>> {
            return results.map { result ->
                mapOf(
                    "type" to "tool_result",
                    "tool_use_id" to result.toolCallId,
                    "content" to result.content,
                    "is_error" to result.isError,
                )
            }
        }

        /**
         * Format tool results for sending back to OpenAI API.
         *
         * @param results List of tool results to format
         * @return List of tool message objects
         */
        fun formatOpenAIToolResults(results: List<LLMToolResult>): List<Map<String, Any>> {
            return results.map { result ->
                mapOf(
                    "role" to "tool",
                    "tool_call_id" to result.toolCallId,
                    "content" to result.content,
                )
            }
        }

        companion object {
            private const val TAG = "ToolCallProcessor"
        }
    }
