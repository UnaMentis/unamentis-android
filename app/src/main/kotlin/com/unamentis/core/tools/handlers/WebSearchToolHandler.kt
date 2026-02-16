package com.unamentis.core.tools.handlers

import android.util.Log
import com.unamentis.core.tools.LLMToolCall
import com.unamentis.core.tools.LLMToolDefinition
import com.unamentis.core.tools.LLMToolResult
import com.unamentis.core.tools.ToolExecutionContext
import com.unamentis.core.tools.ToolHandler
import com.unamentis.core.tools.ToolParameters
import com.unamentis.core.tools.ToolProperty
import com.unamentis.services.websearch.WebSearchException
import com.unamentis.services.websearch.WebSearchProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tool handler for web search via LLM function calling.
 *
 * This handler enables the AI tutor to search the web for current
 * information during conversations, such as:
 * - Recent events or facts the LLM is unsure about
 * - Up-to-date information beyond the model's training cutoff
 * - Research on topics the student is exploring
 *
 * ## Supported Tool
 *
 * ### web_search
 * Searches the web and returns relevant results.
 * ```
 * Arguments:
 *   query: string (required) - The search query
 *   num_results: integer (optional) - Number of results (1-10, default 5)
 * ```
 *
 * @property searchProvider Web search provider implementation (e.g., BraveSearchService)
 */
@Singleton
class WebSearchToolHandler
    @Inject
    constructor(
        private val searchProvider: WebSearchProvider,
    ) : ToolHandler {
        override val toolName: String = TOOL_NAME

        override fun getDefinition(): LLMToolDefinition {
            return LLMToolDefinition(
                name = TOOL_NAME,
                description =
                    """
                    Search the web for current information. Use this when the user asks about
                    recent events, facts you're unsure about, or anything that requires up-to-date
                    information. Returns titles, URLs, and descriptions of relevant web pages.
                    """.trimIndent(),
                parameters =
                    ToolParameters(
                        properties =
                            mapOf(
                                "query" to
                                    ToolProperty(
                                        type = "string",
                                        description = "The search query to look up on the web",
                                    ),
                                "num_results" to
                                    ToolProperty(
                                        type = "integer",
                                        description = "Number of results to return (1-10, default 5)",
                                    ),
                            ),
                        required = listOf("query"),
                    ),
            )
        }

        override suspend fun handle(
            call: LLMToolCall,
            context: ToolExecutionContext,
        ): LLMToolResult {
            // Parse required argument
            val query = call.getStringArgument("query")
            if (query.isNullOrBlank()) {
                return LLMToolResult.error(call.id, "Missing required argument: query")
            }

            // Parse optional argument with validation
            val numResults =
                (call.getIntArgument("num_results") ?: DEFAULT_RESULT_COUNT)
                    .coerceIn(MIN_RESULTS, MAX_RESULTS)

            Log.d(TAG, "Web search: '$query' (max $numResults results)")

            return try {
                val response = searchProvider.search(query, numResults)
                LLMToolResult.success(call.id, response.formattedForLLM)
            } catch (e: WebSearchException.ApiKeyMissing) {
                Log.w(TAG, "Web search API key not configured")
                LLMToolResult.error(
                    call.id,
                    "Web search not configured. Add a Brave Search API key in Settings.",
                )
            } catch (e: WebSearchException.RateLimited) {
                Log.w(TAG, "Web search rate limited")
                LLMToolResult.error(call.id, "Search rate limit exceeded. Try again later.")
            } catch (e: WebSearchException.QuotaExceeded) {
                Log.w(TAG, "Web search quota exceeded")
                LLMToolResult.error(call.id, "Search quota exceeded for this month.")
            } catch (e: WebSearchException) {
                Log.e(TAG, "Web search failed", e)
                LLMToolResult.error(call.id, "Search failed: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected web search error", e)
                LLMToolResult.error(call.id, "Search failed: ${e.message ?: "Unknown error"}")
            }
        }

        companion object {
            private const val TAG = "WebSearchToolHandler"

            /** Tool name registered with LLM providers. */
            const val TOOL_NAME = "web_search"

            /** Default number of search results. */
            private const val DEFAULT_RESULT_COUNT = 5

            /** Minimum number of search results. */
            private const val MIN_RESULTS = 1

            /** Maximum number of search results. */
            private const val MAX_RESULTS = 10
        }
    }
