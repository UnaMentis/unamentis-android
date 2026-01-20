package com.unamentis.core.fov

import android.util.Log
import com.unamentis.core.fov.buffer.AdaptiveBudgetConfig
import com.unamentis.core.fov.buffer.AddressedMisconception
import com.unamentis.core.fov.buffer.AlternativeExplanation
import com.unamentis.core.fov.buffer.ConversationTurn
import com.unamentis.core.fov.buffer.CurriculumPosition
import com.unamentis.core.fov.buffer.EpisodicBuffer
import com.unamentis.core.fov.buffer.FOVContext
import com.unamentis.core.fov.buffer.FOVTopicSummary
import com.unamentis.core.fov.buffer.GlossaryTerm
import com.unamentis.core.fov.buffer.ImmediateBuffer
import com.unamentis.core.fov.buffer.LearnerSignals
import com.unamentis.core.fov.buffer.MisconceptionTrigger
import com.unamentis.core.fov.buffer.ModelContextWindows
import com.unamentis.core.fov.buffer.RetrievedContent
import com.unamentis.core.fov.buffer.SemanticBuffer
import com.unamentis.core.fov.buffer.TranscriptSegmentContext
import com.unamentis.core.fov.buffer.UserQuestion
import com.unamentis.core.fov.buffer.WorkingBuffer
import com.unamentis.data.model.LLMMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for hierarchical context buffers implementing "Field of View" approach.
 *
 * The FOV approach manages context for LLM calls through four hierarchical buffers:
 * - **Immediate Buffer**: Verbatim recent conversation + current segment
 * - **Working Buffer**: Current topic content + objectives
 * - **Episodic Buffer**: Compressed session history
 * - **Semantic Buffer**: Curriculum outline + position
 *
 * Each buffer has a token budget that adapts to the model's context window size.
 *
 * @property baseSystemPrompt Base tutor system prompt
 */
@Suppress("TooManyFunctions")
@Singleton
class FOVContextManager
    @Inject
    constructor() {
        companion object {
            private const val TAG = "FOVContextManager"
            private const val DEFAULT_CONTEXT_WINDOW = 128_000
            private const val MAX_TOPIC_SUMMARIES = 10
            private const val MAX_USER_QUESTIONS = 10
            private const val COMPRESSION_THRESHOLD = 5
            private const val SUMMARIES_TO_COMPRESS = 3

            /**
             * Default system prompt for voice tutoring.
             */
            val DEFAULT_SYSTEM_PROMPT =
                """
                You are an expert AI tutor conducting a voice-based educational session.

                INTERACTION GUIDELINES:
                - You are in a voice conversation, so be conversational and natural
                - Keep responses concise but comprehensive
                - Use Socratic questioning to guide learning
                - Encourage critical thinking and exploration
                - Adapt explanations to the student's demonstrated understanding
                - Use concrete examples and analogies
                - Check for understanding regularly
                - Be prepared for interruptions and clarification questions

                If the student interrupts or asks a question, respond helpfully based on the context
                provided. You have access to the curriculum content, learning objectives, and session
                history.

                Always maintain a supportive, encouraging tone while being intellectually rigorous.
                """.trimIndent()
        }

        // Configuration
        private var budgetConfig: AdaptiveBudgetConfig =
            AdaptiveBudgetConfig(DEFAULT_CONTEXT_WINDOW)
        private var baseSystemPrompt: String = DEFAULT_SYSTEM_PROMPT
        private var summarizer: ContextSummarizer? = null

        // Buffers
        private var immediateBuffer = ImmediateBuffer()
        private var workingBuffer = WorkingBuffer()
        private var episodicBuffer = EpisodicBuffer()
        private var semanticBuffer = SemanticBuffer()

        // Thread safety
        private val mutex = Mutex()

        // MARK: - Initialization

        /**
         * Initialize with custom configuration.
         *
         * @param modelContextWindow Context window size for the primary model
         * @param systemPrompt Base tutor system prompt
         * @param summarizer Optional summarizer for compressing older content
         */
        suspend fun initialize(
            modelContextWindow: Int = DEFAULT_CONTEXT_WINDOW,
            systemPrompt: String? = null,
            summarizer: ContextSummarizer? = null,
        ) {
            mutex.withLock {
                budgetConfig = AdaptiveBudgetConfig(modelContextWindow)
                baseSystemPrompt = systemPrompt ?: DEFAULT_SYSTEM_PROMPT
                this.summarizer = summarizer

                immediateBuffer = ImmediateBuffer()
                workingBuffer = WorkingBuffer()
                episodicBuffer = EpisodicBuffer()
                semanticBuffer = SemanticBuffer()

                Log.i(
                    TAG,
                    "FOVContextManager initialized: tier=${budgetConfig.tier}, " +
                        "totalBudget=${budgetConfig.totalBudget}",
                )
            }
        }

        /**
         * Create a manager configured for a specific model.
         *
         * @param model Model identifier (e.g., "gpt-4o", "claude-3-5-sonnet")
         * @param systemPrompt Base tutor system prompt
         */
        suspend fun configureForModel(
            model: String,
            systemPrompt: String? = null,
        ) {
            val contextWindow = ModelContextWindows.contextWindow(model)
            initialize(
                modelContextWindow = contextWindow,
                systemPrompt = systemPrompt,
            )
        }

        // MARK: - Context Building

        /**
         * Build complete FOV context for an LLM call.
         *
         * @param conversationHistory Recent conversation messages
         * @param bargeInUtterance User's barge-in utterance (if any)
         * @return Complete FOV context ready for LLM
         */
        suspend fun buildContext(
            conversationHistory: List<LLMMessage> = emptyList(),
            bargeInUtterance: String? = null,
        ): FOVContext =
            mutex.withLock {
                // Update immediate buffer with current conversation
                updateImmediateBufferInternal(
                    conversationHistory = conversationHistory,
                    bargeInUtterance = bargeInUtterance,
                )

                // Render each buffer within its token budget
                val immediateContent = immediateBuffer.render(budgetConfig.immediateTokenBudget)
                val workingContent = workingBuffer.render(budgetConfig.workingTokenBudget)
                val episodicContent = episodicBuffer.render(budgetConfig.episodicTokenBudget)
                val semanticContent = semanticBuffer.render(budgetConfig.semanticTokenBudget)

                val context =
                    FOVContext(
                        systemPrompt = baseSystemPrompt,
                        immediateContext = immediateContent,
                        workingContext = workingContent,
                        episodicContext = episodicContent,
                        semanticContext = semanticContent,
                        immediateBufferTurnCount =
                            minOf(
                                conversationHistory.size,
                                budgetConfig.conversationTurnCount,
                            ),
                        budgetConfig = budgetConfig,
                    )

                Log.d(
                    TAG,
                    "Built FOV context: totalTokens=${context.totalTokenEstimate}, " +
                        "turns=${context.immediateBufferTurnCount}",
                )

                context
            }

        private fun updateImmediateBufferInternal(
            conversationHistory: List<LLMMessage>,
            bargeInUtterance: String?,
        ) {
            immediateBuffer.bargeInUtterance = bargeInUtterance

            // Convert recent messages to conversation turns
            val turnCount = budgetConfig.conversationTurnCount
            val recentMessages = conversationHistory.takeLast(turnCount)

            immediateBuffer.recentTurns =
                recentMessages.map { message ->
                    ConversationTurn.fromLLMMessage(message.role, message.content)
                }
        }

        // MARK: - Buffer Updates

        /**
         * Set the current TTS segment being played.
         *
         * @param segment Current segment context
         */
        suspend fun setCurrentSegment(segment: TranscriptSegmentContext?) =
            mutex.withLock {
                immediateBuffer.currentSegment = segment
                Log.v(TAG, "Set current segment: ${segment?.id ?: "nil"}")
            }

        /**
         * Set adjacent segments for context.
         *
         * @param segments Adjacent segments (typically 1-2 before and after)
         */
        suspend fun setAdjacentSegments(segments: List<TranscriptSegmentContext>) =
            mutex.withLock {
                immediateBuffer.adjacentSegments = segments
            }

        /**
         * Update working buffer with topic content.
         *
         * @param topicTitle Current topic title
         * @param topicContent Topic description/outline
         * @param learningObjectives Learning objectives
         * @param glossaryTerms Relevant glossary terms
         * @param misconceptionTriggers Misconception triggers
         */
        suspend fun updateWorkingBuffer(
            topicTitle: String,
            topicContent: String,
            learningObjectives: List<String> = emptyList(),
            glossaryTerms: List<GlossaryTerm> = emptyList(),
            misconceptionTriggers: List<MisconceptionTrigger> = emptyList(),
        ) = mutex.withLock {
            workingBuffer =
                WorkingBuffer(
                    topicTitle = topicTitle,
                    topicContent = topicContent,
                    learningObjectives = learningObjectives,
                    glossaryTerms = glossaryTerms,
                    misconceptionTriggers = misconceptionTriggers,
                )

            Log.d(TAG, "Updated working buffer for topic: $topicTitle")
        }

        /**
         * Add alternative explanations to working buffer.
         *
         * @param explanations Alternative explanations available
         */
        suspend fun setAlternativeExplanations(explanations: List<AlternativeExplanation>) =
            mutex.withLock {
                workingBuffer.alternativeExplanations = explanations
            }

        /**
         * Expand working buffer with retrieved content.
         *
         * @param content Content retrieved via semantic search
         */
        suspend fun expandWorkingBuffer(content: List<RetrievedContent>) =
            mutex.withLock {
                if (content.isEmpty()) return@withLock

                val expansionText =
                    content.joinToString("\n\n") { item ->
                        "[${item.sourceTitle}]: ${item.content}"
                    }

                if (expansionText.isNotEmpty()) {
                    workingBuffer.topicContent += "\n\n## Additional Context\n$expansionText"
                    Log.d(TAG, "Expanded working buffer with ${content.size} items")
                }
            }

        /**
         * Update semantic buffer with curriculum outline.
         *
         * @param curriculumOutline Compressed curriculum outline
         * @param position Current position in curriculum
         * @param dependencies Topic dependency information
         */
        suspend fun updateSemanticBuffer(
            curriculumOutline: String,
            position: CurriculumPosition,
            dependencies: List<String> = emptyList(),
        ) = mutex.withLock {
            semanticBuffer =
                SemanticBuffer(
                    curriculumOutline = curriculumOutline,
                    currentPosition = position,
                    topicDependencies = dependencies,
                )

            Log.d(TAG, "Updated semantic buffer: ${position.curriculumTitle}")
        }

        // MARK: - Episodic Buffer Management

        /**
         * Record a completed topic in episodic buffer.
         *
         * @param summary Topic summary to record
         */
        suspend fun recordTopicCompletion(summary: FOVTopicSummary) =
            mutex.withLock {
                episodicBuffer.topicSummaries.add(summary)

                // Trim old summaries if needed
                if (episodicBuffer.topicSummaries.size > MAX_TOPIC_SUMMARIES) {
                    val toKeep =
                        episodicBuffer.topicSummaries.takeLast(MAX_TOPIC_SUMMARIES).toMutableList()
                    episodicBuffer.topicSummaries = toKeep
                }

                Log.d(TAG, "Recorded topic completion: ${summary.title}")
            }

        /**
         * Record a user question.
         *
         * @param question Question asked by user
         * @param wasAnswered Whether the question was answered
         */
        suspend fun recordUserQuestion(
            question: String,
            wasAnswered: Boolean = false,
        ) = mutex.withLock {
            episodicBuffer.userQuestions.add(
                UserQuestion(question = question, wasAnswered = wasAnswered),
            )

            // Trim old questions
            if (episodicBuffer.userQuestions.size > MAX_USER_QUESTIONS) {
                val toKeep = episodicBuffer.userQuestions.takeLast(MAX_USER_QUESTIONS).toMutableList()
                episodicBuffer.userQuestions = toKeep
            }
        }

        /**
         * Record an addressed misconception.
         *
         * @param misconception Misconception that was addressed
         */
        suspend fun recordAddressedMisconception(misconception: AddressedMisconception) =
            mutex.withLock {
                episodicBuffer.addressedMisconceptions.add(misconception)
            }

        /**
         * Update learner signals.
         *
         * @param signals Updated learner signals
         */
        suspend fun updateLearnerSignals(signals: LearnerSignals) =
            mutex.withLock {
                episodicBuffer.learnerSignals = signals
            }

        /**
         * Increment clarification request count.
         */
        suspend fun recordClarificationRequest() =
            mutex.withLock {
                episodicBuffer.learnerSignals.clarificationRequests++
            }

        /**
         * Increment repetition request count.
         */
        suspend fun recordRepetitionRequest() =
            mutex.withLock {
                episodicBuffer.learnerSignals.repetitionRequests++
            }

        // MARK: - Buffer Compression

        /**
         * Compress episodic buffer when approaching token limits.
         *
         * Uses summarizer to condense older content.
         */
        suspend fun compressEpisodicBuffer() {
            val currentSummarizer = summarizer
            if (currentSummarizer == null) {
                Log.w(TAG, "Cannot compress: no summarizer configured")
                return
            }

            mutex.withLock {
                // Summarize older topic summaries
                if (episodicBuffer.topicSummaries.size > COMPRESSION_THRESHOLD) {
                    val oldSummaries = episodicBuffer.topicSummaries.take(SUMMARIES_TO_COMPRESS)
                    val summaryTexts = oldSummaries.map { "${it.title}: ${it.summary}" }
                    val combinedText = summaryTexts.joinToString("\n")

                    val condensed = currentSummarizer.summarizeTopicContent(combinedText)

                    // Replace old summaries with single condensed one
                    val avgMastery = oldSummaries.map { it.masteryLevel }.average()
                    val condensedSummary =
                        FOVTopicSummary(
                            topicId = UUID.randomUUID(),
                            title = "Earlier topics",
                            summary = condensed,
                            masteryLevel = avgMastery,
                        )

                    val remaining = episodicBuffer.topicSummaries.drop(SUMMARIES_TO_COMPRESS)
                    episodicBuffer.topicSummaries =
                        (listOf(condensedSummary) + remaining).toMutableList()

                    Log.i(TAG, "Compressed episodic buffer: merged ${oldSummaries.size} summaries")
                }
            }
        }

        // MARK: - Configuration

        /**
         * Update budget configuration for a different model.
         *
         * @param model New model identifier
         */
        suspend fun updateModelConfig(model: String) =
            mutex.withLock {
                val contextWindow = ModelContextWindows.contextWindow(model)
                budgetConfig = AdaptiveBudgetConfig(contextWindow)

                Log.i(TAG, "Updated model config: model=$model, tier=${budgetConfig.tier}")
            }

        /**
         * Update budget configuration with specific context window.
         *
         * @param contextWindow Context window size in tokens
         */
        suspend fun updateContextWindow(contextWindow: Int) =
            mutex.withLock {
                budgetConfig = AdaptiveBudgetConfig(contextWindow)
            }

        /**
         * Get current budget configuration.
         */
        suspend fun getBudgetConfig(): AdaptiveBudgetConfig = mutex.withLock { budgetConfig }

        /**
         * Set the summarizer for buffer compression.
         *
         * @param summarizer Summarizer to use
         */
        suspend fun setSummarizer(summarizer: ContextSummarizer) =
            mutex.withLock {
                this.summarizer = summarizer
            }

        // MARK: - Reset

        /**
         * Reset all buffers (e.g., when starting new session).
         */
        suspend fun reset() =
            mutex.withLock {
                immediateBuffer = ImmediateBuffer()
                workingBuffer = WorkingBuffer()
                episodicBuffer = EpisodicBuffer()
                semanticBuffer = SemanticBuffer()

                Log.i(TAG, "FOVContextManager reset")
            }

        /**
         * Reset only the immediate buffer (e.g., when topic changes).
         */
        suspend fun resetImmediateBuffer() =
            mutex.withLock {
                immediateBuffer = ImmediateBuffer()
            }
    }

/**
 * Interface for context summarization.
 *
 * Used to compress older content in the episodic buffer.
 */
interface ContextSummarizer {
    /**
     * Summarize topic content to a shorter form.
     *
     * @param content Content to summarize
     * @return Summarized content
     */
    suspend fun summarizeTopicContent(content: String): String

    /**
     * Summarize conversation turns to a shorter form.
     *
     * @param turns Conversation turns to summarize
     * @return Summarized conversation
     */
    suspend fun summarizeTurns(turns: List<ConversationTurn>): String

    /**
     * Compress text to fit within a target token budget.
     *
     * @param text Text to compress
     * @param targetTokens Target token count
     * @return Compressed text
     */
    suspend fun compressToFit(
        text: String,
        targetTokens: Int,
    ): String

    /**
     * Extract key concepts from text.
     *
     * @param text Text to analyze
     * @return List of key concepts
     */
    suspend fun extractKeyConcepts(text: String): List<String>
}
