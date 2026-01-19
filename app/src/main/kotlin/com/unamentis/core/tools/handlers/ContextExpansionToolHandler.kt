package com.unamentis.core.tools.handlers

import android.util.Log
import com.unamentis.core.curriculum.CurriculumEngine
import com.unamentis.core.fov.FOVContextManager
import com.unamentis.core.fov.buffer.ExpansionScope
import com.unamentis.core.fov.buffer.RetrievedContent
import com.unamentis.core.tools.LLMToolCall
import com.unamentis.core.tools.LLMToolDefinition
import com.unamentis.core.tools.LLMToolResult
import com.unamentis.core.tools.ToolExecutionContext
import com.unamentis.core.tools.ToolHandler
import com.unamentis.core.tools.ToolParameters
import com.unamentis.core.tools.ToolProperty
import com.unamentis.data.model.Topic
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tool handler for explicit context expansion requests from the LLM.
 *
 * This handler enables the AI tutor to request additional curriculum content
 * when it determines it needs more information to answer a question accurately.
 * The retrieved content is both returned to the LLM and injected into the
 * FOV working buffer for future turns.
 *
 * ## Tool: expand_context
 *
 * Request additional curriculum context. Use when:
 * - Uncertain about specific details in a topic
 * - Need to reference related concepts
 * - Want to provide more comprehensive information
 *
 * ```
 * Arguments:
 *   query: string (required) - What information is needed
 *   scope: string (optional) - Where to search
 *     - "current_topic" (default): Current topic only
 *     - "current_unit": Adjacent topics
 *     - "full_curriculum": Search all topics
 *     - "related_topics": Related/prerequisite topics
 *   reason: string (optional) - Why the information is needed
 * ```
 *
 * @property curriculumEngine Engine for curriculum access and search
 * @property contextManager FOV context manager to update with expanded content
 */
@Singleton
class ContextExpansionToolHandler
    @Inject
    constructor(
        private val curriculumEngine: CurriculumEngine,
        private val contextManager: FOVContextManager,
    ) : ToolHandler {
        override val toolName: String = TOOL_EXPAND_CONTEXT

        // Configuration
        private var maxRetrievalTokens: Int = DEFAULT_MAX_RETRIEVAL_TOKENS

        override fun getDefinition(): LLMToolDefinition {
            return LLMToolDefinition(
                name = TOOL_EXPAND_CONTEXT,
                description =
                    """
                    Request additional curriculum context when you need more information to answer
                    the user's question accurately. Use this when you're uncertain about specific
                    details, need to reference related topics, or want to provide more comprehensive
                    information.
                    """.trimIndent(),
                parameters =
                    ToolParameters(
                        properties =
                            mapOf(
                                "query" to
                                    ToolProperty(
                                        type = "string",
                                        description = "What information do you need? Be specific about the topic or concept.",
                                    ),
                                "scope" to
                                    ToolProperty(
                                        type = "string",
                                        description = "Where to search for the information",
                                        enum = listOf("current_topic", "current_unit", "full_curriculum", "related_topics"),
                                    ),
                                "reason" to
                                    ToolProperty(
                                        type = "string",
                                        description = "Why do you need this information? Helps prioritize retrieval.",
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
            // Parse required query argument
            val query = call.getStringArgument("query")
            if (query.isNullOrBlank()) {
                return LLMToolResult.error(call.id, "Missing required argument: query")
            }

            // Parse optional scope argument
            val scopeStr = call.getStringArgument("scope") ?: "current_topic"
            val scope =
                when (scopeStr.lowercase()) {
                    "current_topic" -> ExpansionScope.CURRENT_TOPIC
                    "current_unit" -> ExpansionScope.CURRENT_UNIT
                    "full_curriculum" -> ExpansionScope.FULL_CURRICULUM
                    "related_topics" -> ExpansionScope.RELATED_TOPICS
                    else -> ExpansionScope.CURRENT_TOPIC
                }

            // Parse optional reason (for logging)
            val reason = call.getStringArgument("reason")

            Log.i(
                TAG,
                "Processing expansion request - query: '$query', scope: $scope" +
                    if (reason != null) ", reason: $reason" else "",
            )

            val startTime = System.currentTimeMillis()

            // Perform search based on scope
            val retrievedContent = performSearch(query, scope)

            // Update FOV context manager with retrieved content
            if (retrievedContent.isNotEmpty()) {
                contextManager.expandWorkingBuffer(retrievedContent)
            }

            // Format response for LLM
            val formattedContent = formatRetrievedContent(retrievedContent)

            val durationMs = System.currentTimeMillis() - startTime
            val totalTokens = retrievedContent.sumOf { it.estimatedTokens }

            Log.i(
                TAG,
                "Expansion complete - items: ${retrievedContent.size}, tokens: $totalTokens, duration: ${durationMs}ms",
            )

            return if (retrievedContent.isNotEmpty()) {
                LLMToolResult.success(call.id, formattedContent)
            } else {
                LLMToolResult.success(
                    call.id,
                    "No additional context found for your query: '$query'. " +
                        "Try broadening the search scope or rephrasing the query.",
                )
            }
        }

        // MARK: - Search Implementation

        /**
         * Perform search based on scope.
         */
        private fun performSearch(
            query: String,
            scope: ExpansionScope,
        ): List<RetrievedContent> {
            return when (scope) {
                ExpansionScope.CURRENT_TOPIC -> searchCurrentTopic(query)
                ExpansionScope.CURRENT_UNIT -> searchCurrentUnit(query)
                ExpansionScope.FULL_CURRICULUM -> searchFullCurriculum(query)
                ExpansionScope.RELATED_TOPICS -> searchRelatedTopics(query)
            }
        }

        /**
         * Search within current topic only.
         */
        private fun searchCurrentTopic(query: String): List<RetrievedContent> {
            val topic = curriculumEngine.currentTopic.value ?: return emptyList()

            val context = generateContextForQuery(query, topic, maxRetrievalTokens)
            if (context.isEmpty()) return emptyList()

            return listOf(
                RetrievedContent(
                    sourceTitle = topic.title,
                    content = context,
                    relevanceScore = 1.0f,
                ),
            )
        }

        /**
         * Search within current unit (adjacent topics).
         */
        private fun searchCurrentUnit(query: String): List<RetrievedContent> {
            val results = mutableListOf<RetrievedContent>()

            // Get current topic first
            results.addAll(searchCurrentTopic(query))

            val curriculum = curriculumEngine.currentCurriculum.value ?: return results
            val currentTopic = curriculumEngine.currentTopic.value ?: return results

            val topics = curriculum.topics
            val currentIndex = topics.indexOfFirst { it.id == currentTopic.id }
            if (currentIndex == -1) return results

            // Search previous topic
            if (currentIndex > 0) {
                val prevTopic = topics[currentIndex - 1]
                val prevContext = generateContextForQuery(query, prevTopic, maxRetrievalTokens / 3)
                if (prevContext.isNotEmpty()) {
                    results.add(
                        RetrievedContent(
                            sourceTitle = prevTopic.title,
                            content = prevContext,
                            relevanceScore = 0.8f,
                        ),
                    )
                }
            }

            // Search next topic
            if (currentIndex < topics.size - 1) {
                val nextTopic = topics[currentIndex + 1]
                val nextContext = generateContextForQuery(query, nextTopic, maxRetrievalTokens / 3)
                if (nextContext.isNotEmpty()) {
                    results.add(
                        RetrievedContent(
                            sourceTitle = nextTopic.title,
                            content = nextContext,
                            relevanceScore = 0.7f,
                        ),
                    )
                }
            }

            return results
        }

        /**
         * Search the full curriculum.
         */
        private fun searchFullCurriculum(query: String): List<RetrievedContent> {
            val curriculum = curriculumEngine.currentCurriculum.value ?: return emptyList()
            val results = mutableListOf<RetrievedContent>()

            // Search each topic (limited to avoid excessive results)
            val tokensPerTopic = maxRetrievalTokens / 5
            for (topic in curriculum.topics.take(MAX_TOPICS_TO_SEARCH)) {
                val context = generateContextForQuery(query, topic, tokensPerTopic)
                if (context.isNotEmpty()) {
                    results.add(
                        RetrievedContent(
                            sourceTitle = topic.title,
                            content = context,
                            relevanceScore = 0.6f,
                        ),
                    )
                }
            }

            // Sort by relevance and take top results
            return results.sortedByDescending { it.relevanceScore }.take(MAX_RESULTS)
        }

        /**
         * Search related/prerequisite topics.
         *
         * For now, this is similar to currentUnit. In the future, this could
         * use a topic dependency graph for smarter retrieval.
         */
        private fun searchRelatedTopics(query: String): List<RetrievedContent> {
            // For now, delegate to currentUnit search
            // Future: Use topic dependency/relationship graph
            return searchCurrentUnit(query)
        }

        // MARK: - Context Generation

        /**
         * Generate context content for a query within a topic.
         *
         * This performs keyword matching against transcript segments to find
         * relevant content. For more sophisticated search, consider integrating
         * with an embedding-based semantic search.
         */
        private fun generateContextForQuery(
            query: String,
            topic: Topic,
            maxTokens: Int,
        ): String {
            val queryTerms = query.lowercase().split(Regex("\\s+")).filter { it.length > 2 }
            if (queryTerms.isEmpty()) return ""

            // Score each segment based on keyword matches
            val scoredSegments =
                topic.transcript.mapNotNull { segment ->
                    val segmentContent = segment.content.lowercase()
                    val score = queryTerms.count { term -> segmentContent.contains(term) }
                    if (score > 0) segment to score else null
                }.sortedByDescending { it.second }

            if (scoredSegments.isEmpty()) return ""

            // Build context from top-scoring segments within token budget
            val contextBuilder = StringBuilder()
            var estimatedTokens = 0
            val tokensPerChar = CHARS_PER_TOKEN.toFloat()

            for ((segment, _) in scoredSegments) {
                val segmentTokens = (segment.content.length / tokensPerChar).toInt()
                if (estimatedTokens + segmentTokens > maxTokens) break

                if (contextBuilder.isNotEmpty()) {
                    contextBuilder.append("\n\n")
                }
                contextBuilder.append(segment.content)
                estimatedTokens += segmentTokens
            }

            return contextBuilder.toString()
        }

        // MARK: - Formatting

        /**
         * Format retrieved content for LLM consumption.
         */
        private fun formatRetrievedContent(content: List<RetrievedContent>): String {
            if (content.isEmpty()) {
                return "No additional context found for your query."
            }

            val formatted = StringBuilder("Here is additional context from the curriculum:\n\n")

            for ((index, item) in content.withIndex()) {
                formatted.append("**[${item.sourceTitle}]**\n")
                formatted.append(item.content)
                if (index < content.size - 1) {
                    formatted.append("\n\n---\n\n")
                }
            }

            return formatted.toString()
        }

        // MARK: - Configuration

        /**
         * Update the maximum retrieval tokens.
         *
         * @param tokens New maximum token count
         */
        fun setMaxRetrievalTokens(tokens: Int) {
            maxRetrievalTokens = tokens.coerceIn(MIN_RETRIEVAL_TOKENS, MAX_RETRIEVAL_TOKENS)
            Log.d(TAG, "Max retrieval tokens set to: $maxRetrievalTokens")
        }

        companion object {
            private const val TAG = "ContextExpansionTool"

            /** Tool name for context expansion */
            const val TOOL_EXPAND_CONTEXT = "expand_context"

            /** Default maximum tokens to retrieve per expansion */
            private const val DEFAULT_MAX_RETRIEVAL_TOKENS = 2000

            /** Minimum retrieval tokens */
            private const val MIN_RETRIEVAL_TOKENS = 500

            /** Maximum retrieval tokens */
            private const val MAX_RETRIEVAL_TOKENS = 5000

            /** Maximum topics to search in full curriculum mode */
            private const val MAX_TOPICS_TO_SEARCH = 10

            /** Maximum results to return */
            private const val MAX_RESULTS = 5

            /** Approximate characters per token */
            private const val CHARS_PER_TOKEN = 4
        }
    }
