package com.unamentis.core.fov

import android.util.Log
import com.unamentis.core.curriculum.CurriculumEngine
import com.unamentis.core.fov.buffer.CurriculumPosition
import com.unamentis.core.fov.buffer.ExpansionScope
import com.unamentis.core.fov.buffer.FOVTopicSummary
import com.unamentis.core.fov.buffer.ModelContextWindows
import com.unamentis.core.fov.buffer.RetrievedContent
import com.unamentis.core.fov.buffer.TranscriptSegmentContext
import com.unamentis.core.tools.handlers.ContextExpansionToolHandler
import com.unamentis.data.model.LLMMessage
import com.unamentis.data.model.LLMService
import com.unamentis.data.model.Topic
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinator for integrating FOV context management into voice sessions.
 *
 * This coordinator provides:
 * - Foveated context building for LLM calls
 * - Confidence monitoring for automatic expansion
 * - Integration with CurriculumEngine for topic-aware context
 * - Barge-in context handling
 *
 * Use this class via dependency injection rather than modifying the core SessionManager.
 *
 * ## Usage
 * ```kotlin
 * // Build foveated messages for LLM call
 * val messages = coordinator.buildFoveatedMessages(conversationHistory, bargeInUtterance)
 *
 * // After getting response, analyze confidence
 * val recommendation = coordinator.analyzeResponseConfidence(response)
 * if (recommendation.shouldExpand) {
 *     val additionalContext = coordinator.expandContext(recommendation)
 * }
 * ```
 *
 * @property contextManager FOV context manager
 * @property confidenceMonitor Confidence monitor for automatic expansion
 */
@Singleton
class FOVSessionContextCoordinator
    @Inject
    constructor(
        val contextManager: FOVContextManager,
        val confidenceMonitor: ConfidenceMonitor,
    ) {
        companion object {
            private const val TAG = "FOVSessionCoordinator"
            private const val DEFAULT_CONTEXT_WINDOW = 128_000
        }

        // Dependencies set via initialization
        private var curriculumEngine: CurriculumEngine? = null
        private var summarizer: ContextSummarizer? = null
        private var expansionHandler: ContextExpansionToolHandler? = null

        // State
        private var isEnabled: Boolean = true
        private var currentTopic: Topic? = null
        private var currentSegment: TranscriptSegmentContext? = null

        // MARK: - Initialization

        /**
         * Initialize the coordinator with dependencies.
         *
         * @param curriculumEngine Curriculum engine for content access
         * @param llmService LLM service for summarization
         * @param model Model identifier for context window calculation
         */
        suspend fun initialize(
            curriculumEngine: CurriculumEngine?,
            llmService: LLMService? = null,
            model: String = "gpt-4o",
        ) {
            this.curriculumEngine = curriculumEngine

            // Configure context manager for model
            val contextWindow = ModelContextWindows.contextWindow(model)
            contextManager.initialize(
                modelContextWindow = contextWindow,
            )

            // Initialize summarizer if LLM available
            llmService?.let { service ->
                val newSummarizer = ContextSummarizerImpl(service)
                this.summarizer = newSummarizer
                contextManager.setSummarizer(newSummarizer)
            }

            // Initialize expansion handler if curriculum available
            curriculumEngine?.let { engine ->
                this.expansionHandler = ContextExpansionToolHandler(engine, contextManager)
            }

            Log.i(TAG, "FOVSessionContextCoordinator initialized with model: $model")
        }

        // MARK: - Context Building

        /**
         * Build foveated context for an LLM call.
         *
         * @param conversationHistory Full conversation history
         * @param bargeInUtterance User's barge-in utterance (if any)
         * @return Array of LLM messages with foveated context as system message
         */
        suspend fun buildFoveatedMessages(
            conversationHistory: List<LLMMessage>,
            bargeInUtterance: String? = null,
        ): List<LLMMessage> {
            if (!isEnabled) {
                // Return original messages if disabled
                return conversationHistory
            }

            // Build FOV context
            val fovContext =
                contextManager.buildContext(
                    conversationHistory = conversationHistory,
                    bargeInUtterance = bargeInUtterance,
                )

            // Create messages with foveated system prompt
            val messages = mutableListOf<LLMMessage>()

            // System message with full foveated context
            messages.add(
                LLMMessage(
                    role = "system",
                    content = fovContext.toSystemMessage(),
                ),
            )

            // Add recent conversation turns (limited by immediate buffer config)
            val turnCount = fovContext.immediateBufferTurnCount
            val recentHistory = conversationHistory.takeLast(turnCount * 2) // user + assistant pairs

            for (message in recentHistory) {
                if (message.role != "system") {
                    messages.add(message)
                }
            }

            Log.d(
                TAG,
                "Built foveated messages - total: ${messages.size}, turns: $turnCount, " +
                    "tokens: ~${fovContext.totalTokenEstimate}",
            )

            return messages
        }

        /**
         * Analyze response and determine if expansion is needed.
         *
         * @param response LLM response to analyze
         * @return Expansion recommendation
         */
        suspend fun analyzeResponseConfidence(response: String): ExpansionRecommendation {
            val analysis = confidenceMonitor.analyzeResponse(response)
            return confidenceMonitor.getExpansionRecommendation(analysis)
        }

        /**
         * Expand context based on recommendation.
         *
         * @param query What information to search for
         * @param scope Where to search
         * @param reason Why the information is needed
         * @return Expanded content (if any)
         */
        suspend fun expandContext(
            query: String,
            scope: ExpansionScope = ExpansionScope.CURRENT_TOPIC,
            @Suppress("UNUSED_PARAMETER") reason: String? = null,
        ): String? {
            if (curriculumEngine == null) {
                Log.w(TAG, "No curriculum engine configured for expansion")
                return null
            }

            // Perform local search and update context
            val retrievedContent = performLocalExpansion(query, scope)
            if (retrievedContent.isNotEmpty()) {
                contextManager.expandWorkingBuffer(retrievedContent)
                return formatRetrievedContent(retrievedContent)
            }

            return null
        }

        /**
         * Perform local expansion without using the tool handler.
         */
        private fun performLocalExpansion(
            query: String,
            scope: ExpansionScope,
        ): List<RetrievedContent> {
            val curriculum = curriculumEngine ?: return emptyList()

            return when (scope) {
                ExpansionScope.CURRENT_TOPIC -> searchCurrentTopic(query, curriculum)
                ExpansionScope.CURRENT_UNIT -> searchCurrentUnit(query, curriculum)
                ExpansionScope.FULL_CURRICULUM -> searchFullCurriculum(query, curriculum)
                ExpansionScope.RELATED_TOPICS -> searchCurrentUnit(query, curriculum)
            }
        }

        private fun searchCurrentTopic(
            query: String,
            curriculum: CurriculumEngine,
        ): List<RetrievedContent> {
            val topic = curriculum.currentTopic.value ?: return emptyList()
            val context = searchTopicContent(query, topic)
            if (context.isEmpty()) return emptyList()

            return listOf(
                RetrievedContent(
                    sourceTitle = topic.title,
                    content = context,
                    relevanceScore = 1.0f,
                ),
            )
        }

        private fun searchCurrentUnit(
            query: String,
            curriculum: CurriculumEngine,
        ): List<RetrievedContent> {
            val results = mutableListOf<RetrievedContent>()
            results.addAll(searchCurrentTopic(query, curriculum))

            val currentCurriculum = curriculum.currentCurriculum.value ?: return results
            val currentTopic = curriculum.currentTopic.value ?: return results

            val topics = currentCurriculum.topics
            val currentIndex = topics.indexOfFirst { it.id == currentTopic.id }
            if (currentIndex == -1) return results

            // Search previous topic
            if (currentIndex > 0) {
                val prevTopic = topics[currentIndex - 1]
                val prevContext = searchTopicContent(query, prevTopic)
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
                val nextContext = searchTopicContent(query, nextTopic)
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

        private fun searchFullCurriculum(
            query: String,
            curriculum: CurriculumEngine,
        ): List<RetrievedContent> {
            val currentCurriculum = curriculum.currentCurriculum.value ?: return emptyList()
            val results = mutableListOf<RetrievedContent>()

            for (topic in currentCurriculum.topics.take(10)) {
                val context = searchTopicContent(query, topic)
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

            return results.sortedByDescending { it.relevanceScore }.take(5)
        }

        private fun searchTopicContent(
            query: String,
            topic: Topic,
        ): String {
            val queryTerms = query.lowercase().split(Regex("\\s+")).filter { it.length > 2 }
            if (queryTerms.isEmpty()) return ""

            // Score segments based on keyword matches
            val matchingSegments =
                topic.transcript.mapNotNull { segment ->
                    val content = segment.content.lowercase()
                    val score = queryTerms.count { term -> content.contains(term) }
                    if (score > 0) segment.content to score else null
                }.sortedByDescending { it.second }
                    .take(3)
                    .map { it.first }

            return matchingSegments.joinToString("\n\n")
        }

        private fun formatRetrievedContent(content: List<RetrievedContent>): String {
            if (content.isEmpty()) return ""

            return buildString {
                append("Here is additional context from the curriculum:\n\n")
                for ((index, item) in content.withIndex()) {
                    append("**[${item.sourceTitle}]**\n")
                    append(item.content)
                    if (index < content.size - 1) {
                        append("\n\n---\n\n")
                    }
                }
            }
        }

        // MARK: - Topic Management

        /**
         * Set the current topic for context building.
         *
         * @param topic Current topic
         */
        suspend fun setCurrentTopic(topic: Topic?) {
            currentTopic = topic

            if (topic == null) return

            // Update working buffer with topic content
            contextManager.updateWorkingBuffer(
                topicTitle = topic.title,
                topicContent = topic.transcript.joinToString("\n\n") { it.content },
                learningObjectives = topic.learningObjectives,
                // TODO: Extract from curriculum
                glossaryTerms = emptyList(),
                // TODO: Extract from curriculum
                misconceptionTriggers = emptyList(),
            )

            // Update semantic buffer with curriculum position
            val curriculum = curriculumEngine?.currentCurriculum?.value
            if (curriculum != null) {
                val topics = curriculum.topics
                val currentIndex = topics.indexOfFirst { it.id == topic.id }
                val totalTopics = topics.size

                val outline =
                    topics.joinToString("\n") { t ->
                        val prefix = if (t.id == topic.id) "→ " else "  "
                        "$prefix${t.title}"
                    }

                contextManager.updateSemanticBuffer(
                    curriculumOutline = outline,
                    position =
                        CurriculumPosition(
                            curriculumTitle = curriculum.title,
                            currentTopicIndex = currentIndex,
                            totalTopics = totalTopics,
                            currentUnitTitle = topic.title,
                        ),
                )
            }

            Log.i(TAG, "Set current topic: ${topic.title}")
        }

        /**
         * Set the current TTS segment being played.
         *
         * @param segment Current segment context
         */
        suspend fun setCurrentSegment(segment: TranscriptSegmentContext?) {
            currentSegment = segment
            contextManager.setCurrentSegment(segment)
        }

        // MARK: - Session Events

        /**
         * Record a user question for the episodic buffer.
         *
         * @param question User's question
         * @param wasAnswered Whether the question was answered
         */
        suspend fun recordUserQuestion(
            question: String,
            wasAnswered: Boolean = false,
        ) {
            contextManager.recordUserQuestion(question, wasAnswered)
        }

        /**
         * Record topic completion.
         *
         * @param topicId Completed topic ID
         * @param title Topic title
         * @param summary Summary of the topic content
         * @param masteryLevel Achieved mastery level (0.0-1.0)
         */
        suspend fun recordTopicCompletion(
            topicId: String,
            title: String,
            summary: String,
            masteryLevel: Double,
        ) {
            val topicSummary =
                FOVTopicSummary(
                    topicId = UUID.fromString(topicId),
                    title = title,
                    summary = summary,
                    masteryLevel = masteryLevel,
                )
            contextManager.recordTopicCompletion(topicSummary)
        }

        /**
         * Record a clarification request (updates learner signals).
         */
        suspend fun recordClarificationRequest() {
            contextManager.recordClarificationRequest()
        }

        /**
         * Record a repetition request (updates learner signals).
         */
        suspend fun recordRepetitionRequest() {
            contextManager.recordRepetitionRequest()
        }

        // MARK: - Configuration

        /**
         * Enable or disable FOV context.
         *
         * @param enabled Whether FOV context is enabled
         */
        fun setEnabled(enabled: Boolean) {
            isEnabled = enabled
            Log.i(TAG, "FOV context ${if (enabled) "enabled" else "disabled"}")
        }

        /**
         * Check if FOV context is enabled.
         */
        fun isEnabled(): Boolean = isEnabled

        /**
         * Update model configuration.
         *
         * @param model Model identifier
         */
        suspend fun updateModelConfig(model: String) {
            contextManager.updateModelConfig(model)
        }

        /**
         * Reset the coordinator for a new session.
         */
        suspend fun reset() {
            contextManager.reset()
            confidenceMonitor.reset()
            currentTopic = null
            currentSegment = null
            Log.i(TAG, "FOVSessionContextCoordinator reset")
        }
    }

/**
 * Helper to integrate FOV context into existing SessionManager workflow.
 */
object FOVContextIntegration {
    /**
     * Process a response with confidence monitoring.
     * Returns additional context if expansion was triggered.
     *
     * @param response LLM response to analyze
     * @param coordinator FOV coordinator
     * @param conversationHistory Current conversation history
     * @return Pair of (shouldExpand, additionalContext)
     */
    suspend fun processResponseWithConfidence(
        response: String,
        coordinator: FOVSessionContextCoordinator,
        conversationHistory: List<LLMMessage>,
    ): Pair<Boolean, String?> {
        val recommendation = coordinator.analyzeResponseConfidence(response)

        if (recommendation.shouldExpand) {
            // Extract query from recent conversation
            val query = extractQueryFromHistory(conversationHistory)

            val additionalContext =
                coordinator.expandContext(
                    query = query,
                    scope = recommendation.suggestedScope,
                    reason = recommendation.reason,
                )
            return true to additionalContext
        }

        return false to null
    }

    /**
     * Extract the most likely query from conversation history.
     */
    private fun extractQueryFromHistory(history: List<LLMMessage>): String {
        // Get the last user message
        val lastUserMessage = history.lastOrNull { it.role == "user" }
        return lastUserMessage?.content ?: "more information about the current topic"
    }
}

/**
 * Specialized builder for barge-in scenarios.
 */
object BargeInContextBuilder {
    /**
     * Build context specifically for a barge-in interruption.
     *
     * @param utterance User's barge-in utterance
     * @param interruptedSegment The TTS segment that was interrupted
     * @param coordinator FOV coordinator
     * @param conversationHistory Current conversation history
     * @return Messages ready for LLM call
     */
    suspend fun buildBargeInContext(
        utterance: String,
        interruptedSegment: TranscriptSegmentContext?,
        coordinator: FOVSessionContextCoordinator,
        conversationHistory: List<LLMMessage>,
    ): List<LLMMessage> {
        // Update the immediate buffer with barge-in specific context
        interruptedSegment?.let {
            coordinator.setCurrentSegment(it)
        }

        // Build foveated messages with the barge-in utterance
        return coordinator.buildFoveatedMessages(
            conversationHistory = conversationHistory,
            bargeInUtterance = utterance,
        )
    }
}
