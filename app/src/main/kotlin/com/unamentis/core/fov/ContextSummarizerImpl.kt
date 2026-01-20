package com.unamentis.core.fov

import android.util.Log
import com.unamentis.core.fov.buffer.ConversationTurn
import com.unamentis.core.fov.buffer.FOVTopicSummary
import com.unamentis.core.fov.buffer.LearnerSignals
import com.unamentis.core.fov.buffer.UserQuestion
import com.unamentis.data.model.LLMMessage
import com.unamentis.data.model.LLMService
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ContextSummarizer using LLM for summarization.
 *
 * Uses a cost-optimized LLM to compress older conversation content
 * while preserving essential information.
 */
@Suppress("TooManyFunctions")
@Singleton
class ContextSummarizerImpl
    @Inject
    constructor(
        private val llmService: LLMService,
    ) : ContextSummarizer {
        companion object {
            private const val TAG = "ContextSummarizer"
            private const val MAX_CACHE_SIZE = 50
            private const val CHARS_PER_TOKEN = 4
        }

        // Configuration
        private var config: SummarizerConfig = SummarizerConfig.DEFAULT

        // Cache for recent summaries
        private val summaryCache = mutableMapOf<String, CachedSummary>()
        private val mutex = Mutex()

        // MARK: - ContextSummarizer Implementation

        override suspend fun summarizeTopicContent(content: String): String {
            if (content.isEmpty()) return ""

            // Check cache
            val cacheKey = "topic:${content.hashCode()}"
            mutex.withLock {
                summaryCache[cacheKey]?.takeIf { !it.isExpired }?.let {
                    Log.d(TAG, "Using cached topic summary")
                    return it.summary
                }
            }

            val prompt =
                """
                Summarize the following educational topic content into a brief overview.
                Focus on the main concepts and key takeaways.
                Keep it under 2 sentences.

                Content:
                ${content.take(config.maxInputLength)}

                Summary:
                """.trimIndent()

            val summary = generateSummary(prompt)
            cacheSummary(cacheKey, summary)

            return summary
        }

        override suspend fun summarizeTurns(turns: List<ConversationTurn>): String {
            if (turns.isEmpty()) return ""

            // Create cache key
            val cacheKey =
                turns.joinToString("|") {
                    "${it.role}:${it.content.take(50)}"
                }

            mutex.withLock {
                summaryCache[cacheKey]?.takeIf { !it.isExpired }?.let {
                    Log.d(TAG, "Using cached turns summary")
                    return it.summary
                }
            }

            // Format turns for summarization
            val turnText =
                turns.joinToString("\n\n") { turn ->
                    "[${turn.role.name.lowercase().replaceFirstChar { it.uppercase() }}]: ${turn.content}"
                }

            val prompt =
                """
                Summarize the following conversation between a student and AI tutor.
                Focus on:
                - Key topics discussed
                - Questions asked by the student
                - Main points explained
                - Any areas of confusion or difficulty

                Keep the summary concise (2-3 sentences max).

                Conversation:
                $turnText

                Summary:
                """.trimIndent()

            val summary = generateSummary(prompt)
            cacheSummary(cacheKey, summary)

            return summary
        }

        override suspend fun compressToFit(
            text: String,
            targetTokens: Int,
        ): String {
            val estimatedTokens = text.length / CHARS_PER_TOKEN

            // If already fits, return as-is
            if (estimatedTokens <= targetTokens) {
                return text
            }

            // Calculate compression ratio needed
            val ratio = targetTokens.toFloat() / estimatedTokens

            val prompt =
                if (ratio > 0.5f) {
                    // Light compression
                    """
                    Condense this text by removing redundancy while keeping all key information:

                    ${text.take(config.maxInputLength)}

                    Condensed version (about ${(ratio * 100).toInt()}% of original length):
                    """.trimIndent()
                } else {
                    // Heavy compression
                    """
                    Summarize the essential points from this text in just 1-2 sentences:

                    ${text.take(config.maxInputLength)}

                    Essential summary:
                    """.trimIndent()
                }

            return generateSummary(prompt)
        }

        override suspend fun extractKeyConcepts(text: String): List<String> {
            val prompt =
                """
                Extract 3-5 key concepts from this educational content as a comma-separated list:

                ${text.take(config.maxInputLength)}

                Key concepts:
                """.trimIndent()

            val response = generateSummary(prompt)
            return response.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }

        // MARK: - Additional Methods

        /**
         * Summarize a list of user questions.
         *
         * @param questions Questions to summarize
         * @return Compressed summary of question themes
         */
        suspend fun summarizeQuestions(questions: List<UserQuestion>): String {
            if (questions.isEmpty()) return ""

            val questionText = questions.joinToString("\n- ") { it.question }

            val prompt =
                """
                Identify the main themes from these student questions:
                - $questionText

                List 1-3 key areas of interest or confusion (one line each):
                """.trimIndent()

            return generateSummary(prompt)
        }

        /**
         * Generate a learning progress summary.
         *
         * @param topicSummaries Summaries of completed topics
         * @param signals Learner signals detected
         * @return Progress summary
         */
        suspend fun generateProgressSummary(
            topicSummaries: List<FOVTopicSummary>,
            signals: LearnerSignals,
        ): String {
            if (topicSummaries.isEmpty()) return ""

            val topicsText =
                topicSummaries.joinToString(", ") { summary ->
                    "${summary.title} (mastery: ${(summary.masteryLevel * 100).toInt()}%)"
                }

            val signalsText =
                buildString {
                    signals.pacePreference?.let {
                        append("Pace preference: ${it.name.lowercase()}. ")
                    }
                    if (signals.clarificationRequests > 0) {
                        append("Asked for clarification ${signals.clarificationRequests} times. ")
                    }
                }

            val prompt =
                """
                Create a brief learning progress note:
                Topics covered: $topicsText
                $signalsText

                Write one sentence summarizing the student's progress and any notable patterns:
                """.trimIndent()

            return generateSummary(prompt)
        }

        // MARK: - Internal Helpers

        /**
         * Generate a summary using the LLM.
         */
        private suspend fun generateSummary(prompt: String): String =
            try {
                val messages =
                    listOf(
                        LLMMessage(role = "system", content = config.systemPrompt),
                        LLMMessage(role = "user", content = prompt),
                    )

                // Collect all tokens from the stream
                val tokens =
                    llmService.streamCompletion(
                        messages = messages,
                        temperature = config.temperature,
                        maxTokens = config.maxOutputTokens,
                    ).toList()

                val response = tokens.joinToString("") { it.content }
                response.trim()
            } catch (e: Exception) {
                Log.e(TAG, "Summarization failed: ${e.message}")
                // Fall back to simple truncation
                prompt.take(200) + "..."
            }

        /**
         * Cache a summary.
         */
        private suspend fun cacheSummary(
            key: String,
            summary: String,
        ) = mutex.withLock {
            // Evict old entries if cache is full
            if (summaryCache.size >= MAX_CACHE_SIZE) {
                val oldestKey =
                    summaryCache.minByOrNull { it.value.timestampMillis }?.key
                oldestKey?.let { summaryCache.remove(it) }
            }

            val expiresAtMillis = System.currentTimeMillis() + config.cacheExpirationMs
            summaryCache[key] =
                CachedSummary(
                    summary = summary,
                    timestampMillis = System.currentTimeMillis(),
                    expiresAtMillis = expiresAtMillis,
                )
        }

        // MARK: - Configuration

        /**
         * Update summarizer configuration.
         */
        suspend fun updateConfig(config: SummarizerConfig) =
            mutex.withLock {
                this.config = config
                Log.i(TAG, "Updated summarizer config")
            }

        /**
         * Clear the summary cache.
         */
        suspend fun clearCache() =
            mutex.withLock {
                summaryCache.clear()
                Log.i(TAG, "Summary cache cleared")
            }
    }

// MARK: - Supporting Types

/**
 * Configuration for the summarizer.
 */
data class SummarizerConfig(
    /** System prompt for summarization tasks */
    val systemPrompt: String,
    /** Maximum input length to process */
    val maxInputLength: Int,
    /** Maximum output tokens */
    val maxOutputTokens: Int,
    /** Temperature for LLM generation */
    val temperature: Float,
    /** Cache expiration time in milliseconds */
    val cacheExpirationMs: Long,
) {
    companion object {
        /** Default configuration */
        val DEFAULT =
            SummarizerConfig(
                systemPrompt =
                    """
                    You are a concise summarization assistant for an educational tutoring system.
                    Create brief, accurate summaries that preserve essential information.
                    Be direct and avoid filler words.
                    """.trimIndent(),
                maxInputLength = 4000,
                maxOutputTokens = 200,
                temperature = 0.3f,
                // 1 hour
                cacheExpirationMs = 3600_000L,
            )

        /** Configuration optimized for minimal token usage */
        val MINIMAL =
            SummarizerConfig(
                systemPrompt = "Summarize concisely.",
                maxInputLength = 2000,
                maxOutputTokens = 150,
                temperature = 0.3f,
                // 30 minutes
                cacheExpirationMs = 1800_000L,
            )
    }
}

/**
 * Cached summary entry.
 */
private data class CachedSummary(
    val summary: String,
    val timestampMillis: Long,
    val expiresAtMillis: Long,
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAtMillis
}
