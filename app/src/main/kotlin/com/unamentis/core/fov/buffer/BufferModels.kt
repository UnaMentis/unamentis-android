package com.unamentis.core.fov.buffer

import java.util.UUID

/**
 * Complete hierarchical context for LLM calls.
 *
 * Contains all buffer layers merged into a single context payload.
 */
data class FOVContext(
    /** Base tutor system prompt */
    val systemPrompt: String,
    /** Immediate buffer: verbatim recent conversation + current segment */
    val immediateContext: String,
    /** Working buffer: current topic content + objectives + glossary */
    val workingContext: String,
    /** Episodic buffer: compressed session history + learner signals */
    val episodicContext: String,
    /** Semantic buffer: curriculum outline + position */
    val semanticContext: String,
    /** Number of conversation turns included in immediate buffer */
    val immediateBufferTurnCount: Int,
    /** Budget configuration used to build this context */
    val budgetConfig: AdaptiveBudgetConfig,
    /** Timestamp when this context was generated */
    val generatedAtMillis: Long = System.currentTimeMillis(),
) {
    /**
     * Flatten all buffers into a single system message for LLM.
     */
    fun toSystemMessage(): String {
        val sections = mutableListOf<String>()

        sections.add(systemPrompt)

        if (semanticContext.isNotEmpty()) {
            sections.add(
                """
                ## CURRICULUM OVERVIEW
                $semanticContext
                """.trimIndent(),
            )
        }

        if (episodicContext.isNotEmpty()) {
            sections.add(
                """
                ## SESSION HISTORY
                $episodicContext
                """.trimIndent(),
            )
        }

        if (workingContext.isNotEmpty()) {
            sections.add(
                """
                ## CURRENT TOPIC CONTEXT
                $workingContext
                """.trimIndent(),
            )
        }

        if (immediateContext.isNotEmpty()) {
            sections.add(
                """
                ## IMMEDIATE CONTEXT
                $immediateContext
                """.trimIndent(),
            )
        }

        return sections.joinToString("\n\n")
    }

    /**
     * Estimated total token count for this context.
     * Rough estimate: ~4 characters per token for English.
     */
    val totalTokenEstimate: Int
        get() {
            val totalChars =
                systemPrompt.length +
                    immediateContext.length +
                    workingContext.length +
                    episodicContext.length +
                    semanticContext.length
            return totalChars / CHARS_PER_TOKEN
        }

    companion object {
        private const val CHARS_PER_TOKEN = 4
    }
}

/**
 * Token budget configuration that adapts to model context window size.
 */
data class AdaptiveBudgetConfig(
    /** Model's maximum context window in tokens */
    val modelContextWindow: Int,
) {
    /** Model tier classification */
    val tier: ModelTier = ModelTier.from(modelContextWindow)

    /** Token budget for immediate buffer (recent conversation) */
    val immediateTokenBudget: Int get() = tier.budgets.immediate

    /** Token budget for working buffer (current topic) */
    val workingTokenBudget: Int get() = tier.budgets.working

    /** Token budget for episodic buffer (session history) */
    val episodicTokenBudget: Int get() = tier.budgets.episodic

    /** Token budget for semantic buffer (curriculum outline) */
    val semanticTokenBudget: Int get() = tier.budgets.semantic

    /** Total budget across all buffers */
    val totalBudget: Int get() = tier.budgets.total

    /** Number of conversation turns to keep verbatim */
    val conversationTurnCount: Int get() = tier.conversationTurns

    companion object {
        /**
         * Create config for a specific model identifier.
         */
        fun forModel(model: String): AdaptiveBudgetConfig {
            val contextWindow = ModelContextWindows.contextWindow(model)
            return AdaptiveBudgetConfig(contextWindow)
        }
    }
}

/**
 * Classification of model capability based on context window.
 */
enum class ModelTier(val budgets: BufferBudgets, val conversationTurns: Int) {
    /** 128K+ tokens (GPT-4o, Claude 3.5) */
    CLOUD(
        budgets =
            BufferBudgets(
                total = 12_000,
                immediate = 3_000,
                working = 5_000,
                episodic = 2_500,
                semantic = 1_500,
            ),
        conversationTurns = 10,
    ),

    /** 32K-128K tokens */
    MID_RANGE(
        budgets =
            BufferBudgets(
                total = 8_000,
                immediate = 2_000,
                working = 3_500,
                episodic = 1_500,
                semantic = 1_000,
            ),
        conversationTurns = 7,
    ),

    /** 8K-32K tokens */
    ON_DEVICE(
        budgets =
            BufferBudgets(
                total = 4_000,
                immediate = 1_200,
                working = 1_500,
                episodic = 800,
                semantic = 500,
            ),
        conversationTurns = 5,
    ),

    /** <8K tokens */
    TINY(
        budgets =
            BufferBudgets(
                total = 2_000,
                immediate = 800,
                working = 700,
                episodic = 300,
                semantic = 200,
            ),
        conversationTurns = 3,
    ),
    ;

    companion object {
        /**
         * Classify model tier from context window size.
         */
        fun from(contextWindow: Int): ModelTier =
            when {
                contextWindow >= 128_000 -> CLOUD
                contextWindow >= 32_000 -> MID_RANGE
                contextWindow >= 8_000 -> ON_DEVICE
                else -> TINY
            }
    }
}

/**
 * Token budgets for buffer tiers.
 */
data class BufferBudgets(
    val total: Int,
    val immediate: Int,
    val working: Int,
    val episodic: Int,
    val semantic: Int,
)

/**
 * Lookup table for known model context windows.
 */
object ModelContextWindows {
    private const val DEFAULT_CONTEXT_WINDOW = 8_192

    /**
     * Get context window size for a model identifier.
     */
    fun contextWindow(model: String): Int {
        val normalizedModel = model.lowercase()

        // OpenAI models
        if ("gpt-4o" in normalizedModel) return 128_000
        if ("gpt-4-turbo" in normalizedModel) return 128_000
        if ("gpt-4" in normalizedModel) return 8_192
        if ("gpt-3.5" in normalizedModel) return 16_385

        // Anthropic models
        if ("claude-3" in normalizedModel) return 200_000
        if ("claude-2" in normalizedModel) return 100_000

        // Self-hosted models (common configurations)
        if ("qwen2.5" in normalizedModel) return 32_768
        if ("llama3.2" in normalizedModel) return 128_000
        if ("llama3.1" in normalizedModel) return 128_000
        if ("mistral" in normalizedModel) return 32_768

        // On-device models
        if ("ministral" in normalizedModel) return 8_192
        if ("phi" in normalizedModel) return 4_096

        return DEFAULT_CONTEXT_WINDOW
    }
}

/**
 * A single turn in conversation history.
 */
data class ConversationTurn(
    val role: Role,
    val content: String,
    val timestampMillis: Long = System.currentTimeMillis(),
) {
    enum class Role {
        USER,
        ASSISTANT,
        SYSTEM,
    }

    companion object {
        /**
         * Create from LLMMessage.
         */
        fun fromLLMMessage(
            role: String,
            content: String,
            timestampMillis: Long = System.currentTimeMillis(),
        ): ConversationTurn {
            val parsedRole =
                when (role.lowercase()) {
                    "user" -> Role.USER
                    "assistant" -> Role.ASSISTANT
                    "system" -> Role.SYSTEM
                    else -> Role.USER
                }
            return ConversationTurn(parsedRole, content, timestampMillis)
        }
    }
}

/**
 * Context for a transcript segment.
 */
data class TranscriptSegmentContext(
    val id: String,
    val content: String,
    val segmentIndex: Int,
    val glossaryRefs: List<String> = emptyList(),
)

/**
 * Glossary term with definition.
 */
data class GlossaryTerm(
    val term: String,
    val definition: String,
    val spokenDefinition: String? = null,
)

/**
 * Alternative explanation for a concept.
 */
data class AlternativeExplanation(
    val style: Style,
    val content: String,
) {
    enum class Style {
        SIMPLER,
        TECHNICAL,
        ANALOGY,
    }
}

/**
 * Misconception trigger with remediation.
 */
data class MisconceptionTrigger(
    val triggerPhrase: String,
    val misconception: String,
    val remediation: String,
)

/**
 * Summary of a completed topic for FOV context episodic buffer.
 */
data class FOVTopicSummary(
    val topicId: UUID = UUID.randomUUID(),
    val title: String,
    val summary: String,
    val masteryLevel: Double,
    val completedAtMillis: Long = System.currentTimeMillis(),
)

/**
 * User question from earlier in the session.
 */
data class UserQuestion(
    val question: String,
    val wasAnswered: Boolean,
    val timestampMillis: Long = System.currentTimeMillis(),
)

/**
 * Misconception that was addressed.
 */
data class AddressedMisconception(
    val misconception: String,
    val remediation: String,
    val seemsResolved: Boolean,
    val addressedAtMillis: Long = System.currentTimeMillis(),
)

/**
 * Pace preference enum.
 */
enum class PacePreference {
    SLOW,
    MODERATE,
    FAST,
}

/**
 * Learner profile signals detected during session.
 */
data class LearnerSignals(
    /** Detected pace preference */
    var pacePreference: PacePreference? = null,
    /** Detected explanation style preference */
    var explanationStylePreference: AlternativeExplanation.Style? = null,
    /** Number of clarification requests */
    var clarificationRequests: Int = 0,
    /** Number of repetition requests */
    var repetitionRequests: Int = 0,
    /** Average think time before responding (milliseconds) */
    var averageThinkTimeMs: Long? = null,
) {
    /**
     * Render to concise string.
     */
    fun render(): String {
        val signals = mutableListOf<String>()

        pacePreference?.let {
            signals.add("Preferred pace: ${it.name.lowercase()}")
        }
        explanationStylePreference?.let {
            signals.add("Prefers ${it.name.lowercase()} explanations")
        }
        if (clarificationRequests > 2) {
            signals.add("Has asked for clarification $clarificationRequests times")
        }
        if (repetitionRequests > 1) {
            signals.add("Has requested repetition $repetitionRequests times")
        }

        return if (signals.isEmpty()) "" else "Learner profile: ${signals.joinToString("; ")}"
    }
}

/**
 * Current position in curriculum.
 */
data class CurriculumPosition(
    val curriculumTitle: String = "",
    val currentTopicIndex: Int = 0,
    val totalTopics: Int = 0,
    val currentUnitTitle: String? = null,
) {
    /**
     * Render position to string.
     */
    fun render(): String {
        val parts = mutableListOf<String>()

        if (curriculumTitle.isNotEmpty()) {
            parts.add("Course: $curriculumTitle")
        }

        currentUnitTitle?.let {
            parts.add("Unit: $it")
        }

        if (totalTopics > 0) {
            val progress = ((currentTopicIndex + 1).toDouble() / totalTopics * 100).toInt()
            parts.add("Progress: Topic ${currentTopicIndex + 1} of $totalTopics ($progress%)")
        }

        return parts.joinToString(" | ")
    }
}

/**
 * Scope for context expansion requests.
 */
enum class ExpansionScope {
    /** Search within current topic only */
    CURRENT_TOPIC,

    /** Search within current unit */
    CURRENT_UNIT,

    /** Search entire curriculum */
    FULL_CURRICULUM,

    /** Search related/prerequisite topics */
    RELATED_TOPICS,
}

/**
 * Result of context expansion.
 */
data class ExpansionResult(
    val query: String,
    val scope: ExpansionScope,
    val retrievedContent: List<RetrievedContent>,
) {
    val totalTokens: Int
        get() = retrievedContent.sumOf { it.estimatedTokens }
}

/**
 * Content retrieved during expansion.
 */
data class RetrievedContent(
    val sourceTitle: String,
    val content: String,
    val relevanceScore: Float,
) {
    val estimatedTokens: Int
        get() = content.length / CHARS_PER_TOKEN

    companion object {
        private const val CHARS_PER_TOKEN = 4
    }
}
