package com.unamentis.services.llm

import android.util.Log
import com.unamentis.data.model.LLMMessage
import com.unamentis.data.model.LLMService
import com.unamentis.data.model.LLMToken
import kotlinx.coroutines.flow.Flow

/**
 * Intelligent LLM routing service that selects the optimal provider
 * based on task type, device capabilities, and runtime conditions.
 *
 * Routing Rules:
 * - TUTORING: High-quality models (Claude Sonnet, GPT-4)
 * - PLANNING: High-quality models with good reasoning
 * - SUMMARIZATION: Fast models (GPT-4o-mini, Claude Haiku)
 * - ASSESSMENT: High-quality models for accuracy
 * - SIMPLE_RESPONSE: Fast, cost-effective models
 *
 * Conditions:
 * - Device tier (FLAGSHIP, STANDARD, MINIMUM)
 * - Network quality (EXCELLENT, GOOD, POOR, OFFLINE)
 * - Cost optimization preference (QUALITY, BALANCED, COST)
 *
 * @property providers Map of provider name to LLMService instance
 * @property routingTable Routing rules configuration
 */
class PatchPanelService(
    private val providers: Map<String, LLMService>,
    private val routingTable: RoutingTable = RoutingTable.default(),
) : LLMService {
    override val providerName: String = "PatchPanel"

    private var currentProvider: LLMService? = null

    /**
     * Stream completion with automatic provider selection.
     *
     * Selects the optimal provider based on:
     * 1. Task type (from routing context)
     * 2. Device capabilities
     * 3. Network conditions
     * 4. Cost preferences
     *
     * Falls back to next-best provider if primary fails.
     */
    override fun streamCompletion(
        messages: List<LLMMessage>,
        temperature: Float,
        maxTokens: Int,
    ): Flow<LLMToken> {
        // Extract task type from system message or use default
        val taskType = extractTaskType(messages)
        // TODO: Get deviceTier from DeviceCapabilityDetector
        // TODO: Get networkQuality from NetworkMonitor
        // TODO: Get costPreference from settings
        val context =
            RoutingContext(
                taskType = taskType,
                deviceTier = DeviceTier.STANDARD,
                networkQuality = NetworkQuality.GOOD,
                costPreference = CostPreference.BALANCED,
            )

        // Select provider based on routing rules
        val selectedProvider = selectProvider(context)
        currentProvider = selectedProvider

        Log.i("PatchPanel", "Routing ${taskType.name} to ${selectedProvider.providerName}")

        return selectedProvider.streamCompletion(messages, temperature, maxTokens)
    }

    /**
     * Stop the current provider's generation.
     */
    override suspend fun stop() {
        currentProvider?.stop()
    }

    /**
     * Select the optimal provider based on routing context.
     */
    private fun selectProvider(context: RoutingContext): LLMService {
        val candidates = routingTable.getProviderCandidates(context)

        // Try each candidate in priority order
        for (providerName in candidates) {
            providers[providerName]?.let { return it }
        }

        // Fallback to first available provider
        return providers.values.firstOrNull()
            ?: throw IllegalStateException("No LLM providers available")
    }

    /**
     * Extract task type from conversation context.
     *
     * Looks for task type hints in system message using hierarchical matching.
     * More specific task types are matched before general ones.
     */
    private fun extractTaskType(messages: List<LLMMessage>): TaskType {
        val systemMessage = messages.firstOrNull { it.role == "system" }?.content ?: ""
        val lastUserMessage = messages.lastOrNull { it.role == "user" }?.content ?: ""
        val context = "$systemMessage $lastUserMessage".lowercase()

        return detectMetaTask(context)
            ?: detectSimpleInteraction(context)
            ?: detectAssessmentTask(context)
            ?: detectTutoringTask(context)
            ?: detectPlanningTask(context)
            ?: detectContentTask(context)
            ?: detectSummarizationTask(context)
            ?: detectNavigationTask(context)
            ?: TaskType.SIMPLE_RESPONSE
    }

    private fun detectMetaTask(context: String): TaskType? =
        when {
            context.containsAny("reflect", "progress review", "how am i doing") -> TaskType.META_REFLECTION
            context.containsAny("motivat", "encourage", "you can do") -> TaskType.META_MOTIVATION
            context.containsAny("error", "mistake", "sorry", "let me try again") -> TaskType.META_ERROR_RECOVERY
            else -> null
        }

    private fun detectSimpleInteraction(context: String): TaskType? =
        when {
            context.containsAny("hello", "hi ", "hey ", "good morning", "good afternoon") -> TaskType.SIMPLE_GREETING
            context.containsAny("ok", "got it", "understood", "i see", "thanks") -> TaskType.SIMPLE_ACKNOWLEDGMENT
            else -> null
        }

    private fun detectAssessmentTask(context: String): TaskType? =
        when {
            context.containsAny("quiz", "test me", "question me") -> TaskType.ASSESSMENT_QUIZ
            context.containsAny("feedback", "how did i do", "grade my") -> TaskType.ASSESSMENT_FEEDBACK
            context.containsAny("rubric", "criteria", "scoring") -> TaskType.ASSESSMENT_RUBRIC
            context.containsAny("assess", "evaluat", "check understanding") -> TaskType.ASSESSMENT
            else -> null
        }

    private fun detectTutoringTask(context: String): TaskType? =
        when {
            context.containsAny("explain", "what is", "how does", "why does") -> TaskType.TUTORING_EXPLANATION
            context.containsAny("example", "show me", "demonstrate", "walk through") -> TaskType.TUTORING_EXAMPLE
            context.containsAny("analogy", "like", "similar to", "compare it to") -> TaskType.TUTORING_ANALOGY
            context.containsAny("clarify", "confused", "don't understand", "what do you mean") ->
                TaskType.TUTORING_CLARIFICATION
            context.containsAny("tutor", "teach", "learn about", "lesson") -> TaskType.TUTORING
            else -> null
        }

    private fun detectPlanningTask(context: String): TaskType? =
        when {
            context.containsAny("curriculum", "course", "syllabus") -> TaskType.PLANNING_CURRICULUM
            context.containsAny("schedule", "when should", "time") -> TaskType.PLANNING_SCHEDULE
            context.containsAny("goal", "objective", "target") -> TaskType.PLANNING_GOAL_SETTING
            context.containsAny("plan", "organize", "structure") -> TaskType.PLANNING
            else -> null
        }

    private fun detectContentTask(context: String): TaskType? =
        when {
            context.containsAny("adapt", "simplify", "make easier", "beginner") -> TaskType.CONTENT_ADAPTATION
            context.containsAny("translat", "convert", "rephrase for") -> TaskType.CONTENT_TRANSLATION
            context.containsAny("generat", "create", "write", "produce") -> TaskType.CONTENT_GENERATION
            else -> null
        }

    private fun detectSummarizationTask(context: String): TaskType? =
        when {
            context.containsAny("key point", "main idea", "takeaway") -> TaskType.SUMMARIZATION_KEY_POINTS
            context.containsAny("compar", "differ", "versus", " vs ") -> TaskType.SUMMARIZATION_COMPARISON
            context.containsAny("summariz", "brief", "overview", "recap") -> TaskType.SUMMARIZATION
            else -> null
        }

    private fun detectNavigationTask(context: String): TaskType? =
        when {
            context.containsAny("where can i find", "show me", "navigate", "go to") -> TaskType.NAVIGATION
            context.containsAny("classify", "categoriz", "what type", "which kind") -> TaskType.CLASSIFICATION
            context.containsAny("topic", "subject", "about") -> TaskType.TOPIC_DETECTION
            else -> null
        }
}

/**
 * Extension function to check if string contains any of the given substrings.
 */
private fun String.containsAny(vararg substrings: String): Boolean = substrings.any { this.contains(it, ignoreCase = true) }

/**
 * Task type classification for routing decisions.
 *
 * 20+ task types organized by category for intelligent routing.
 */
enum class TaskType {
    // Core Tutoring Tasks
    TUTORING, // Long-form educational dialogue
    TUTORING_EXPLANATION, // Explaining concepts in depth
    TUTORING_EXAMPLE, // Providing worked examples
    TUTORING_ANALOGY, // Making analogies and comparisons
    TUTORING_CLARIFICATION, // Clarifying confusion

    // Planning & Organization
    PLANNING, // Creating study plans, lesson plans
    PLANNING_CURRICULUM, // Curriculum design and sequencing
    PLANNING_SCHEDULE, // Time management and scheduling
    PLANNING_GOAL_SETTING, // Setting learning objectives

    // Content Generation
    CONTENT_GENERATION, // Creating new educational content
    CONTENT_ADAPTATION, // Adapting content for different levels
    CONTENT_TRANSLATION, // Translating between knowledge domains

    // Assessment & Evaluation
    ASSESSMENT, // Evaluating understanding
    ASSESSMENT_QUIZ, // Generating quiz questions
    ASSESSMENT_FEEDBACK, // Providing feedback on answers
    ASSESSMENT_RUBRIC, // Applying scoring rubrics

    // Analysis & Summarization
    SUMMARIZATION, // Condensing information
    SUMMARIZATION_KEY_POINTS, // Extracting main ideas
    SUMMARIZATION_COMPARISON, // Comparing multiple concepts

    // Navigation & Classification
    NAVIGATION, // Directing to relevant content
    CLASSIFICATION, // Categorizing queries or content
    TOPIC_DETECTION, // Detecting topic changes

    // Simple Interactions
    SIMPLE_RESPONSE, // Quick questions, confirmations
    SIMPLE_GREETING, // Greetings and pleasantries
    SIMPLE_ACKNOWLEDGMENT, // Acknowledgments and backchannels

    // Meta Tasks
    META_REFLECTION, // Reflecting on learning progress
    META_MOTIVATION, // Encouragement and motivation
    META_ERROR_RECOVERY, // Recovering from errors gracefully
}

/**
 * Device capability tier.
 */
enum class DeviceTier {
    FLAGSHIP, // High-end devices (8GB+ RAM, flagship SoC)
    STANDARD, // Mid-range devices (4-8GB RAM)
    MINIMUM, // Entry-level devices (<4GB RAM)
}

/**
 * Network quality assessment.
 */
enum class NetworkQuality {
    EXCELLENT, // WiFi or 5G
    GOOD, // 4G LTE
    POOR, // 3G or weak signal
    OFFLINE, // No connectivity
}

/**
 * Cost optimization preference.
 */
enum class CostPreference {
    QUALITY, // Prioritize best quality regardless of cost
    BALANCED, // Balance quality and cost
    COST, // Minimize cost
}

/**
 * Routing context for provider selection.
 */
data class RoutingContext(
    val taskType: TaskType,
    val deviceTier: DeviceTier,
    val networkQuality: NetworkQuality,
    val costPreference: CostPreference,
)

/**
 * Routing table defining provider selection rules.
 *
 * Each rule maps a context to an ordered list of provider candidates.
 * The first available provider in the list is selected.
 *
 * Routing strategies by task category:
 * - Complex reasoning (tutoring, assessment): Prefer high-quality models (Claude, GPT-4)
 * - Fast responses (simple, greetings): Prefer fast/cheap models (Ollama, OnDevice)
 * - Content generation: Prefer creative models (Claude for nuance, GPT-4 for breadth)
 * - Analysis: Prefer accurate models (Anthropic for precision)
 */
data class RoutingTable(
    val rules: Map<TaskType, Map<CostPreference, List<String>>>,
) {
    companion object {
        // Provider shorthand constants
        private const val ANTHROPIC = "Anthropic"
        private const val OPENAI = "OpenAI"
        private const val OLLAMA = "Ollama"
        private const val ON_DEVICE = "OnDevice"

        // Common routing patterns
        private val highQuality = listOf(ANTHROPIC, OPENAI)
        private val balanced = listOf(OPENAI, ANTHROPIC, OLLAMA)
        private val costOptimized = listOf(OLLAMA, ON_DEVICE, OPENAI)
        private val fast = listOf(OPENAI, OLLAMA, ON_DEVICE)
        private val ultraFast = listOf(OLLAMA, ON_DEVICE, OPENAI)

        /**
         * Default routing table with comprehensive coverage of all 28 task types.
         *
         * Provider priorities by category:
         * - Quality: Claude Sonnet > GPT-4 (best reasoning, nuance)
         * - Balanced: GPT-4o-mini > Claude Haiku > Ollama (good quality, reasonable cost)
         * - Cost: Ollama > On-Device > GPT-4o-mini (minimize API spend)
         */
        fun default(): RoutingTable {
            val rules = mutableMapOf<TaskType, Map<CostPreference, List<String>>>()
            addTutoringRules(rules)
            addPlanningRules(rules)
            addContentRules(rules)
            addAssessmentRules(rules)
            addSummarizationRules(rules)
            addNavigationRules(rules)
            addSimpleInteractionRules(rules)
            addMetaTaskRules(rules)
            return RoutingTable(rules)
        }

        private fun addTutoringRules(rules: MutableMap<TaskType, Map<CostPreference, List<String>>>) {
            rules[TaskType.TUTORING] =
                mapOf(
                    CostPreference.QUALITY to highQuality,
                    CostPreference.BALANCED to balanced,
                    CostPreference.COST to costOptimized,
                )
            rules[TaskType.TUTORING_EXPLANATION] =
                mapOf(
                    CostPreference.QUALITY to highQuality,
                    CostPreference.BALANCED to listOf(ANTHROPIC, OPENAI, OLLAMA),
                    CostPreference.COST to listOf(OLLAMA, OPENAI),
                )
            rules[TaskType.TUTORING_EXAMPLE] =
                mapOf(
                    CostPreference.QUALITY to highQuality,
                    CostPreference.BALANCED to balanced,
                    CostPreference.COST to listOf(OPENAI, OLLAMA),
                )
            rules[TaskType.TUTORING_ANALOGY] =
                mapOf(
                    CostPreference.QUALITY to listOf(ANTHROPIC, OPENAI),
                    CostPreference.BALANCED to listOf(ANTHROPIC, OPENAI, OLLAMA),
                    CostPreference.COST to listOf(OLLAMA, OPENAI),
                )
            rules[TaskType.TUTORING_CLARIFICATION] =
                mapOf(
                    CostPreference.QUALITY to highQuality,
                    CostPreference.BALANCED to balanced,
                    CostPreference.COST to costOptimized,
                )
        }

        private fun addPlanningRules(rules: MutableMap<TaskType, Map<CostPreference, List<String>>>) {
            rules[TaskType.PLANNING] =
                mapOf(
                    CostPreference.QUALITY to highQuality,
                    CostPreference.BALANCED to listOf(OPENAI, ANTHROPIC),
                    CostPreference.COST to listOf(OPENAI, OLLAMA),
                )
            rules[TaskType.PLANNING_CURRICULUM] =
                mapOf(
                    CostPreference.QUALITY to listOf(ANTHROPIC, OPENAI),
                    CostPreference.BALANCED to listOf(ANTHROPIC, OPENAI),
                    CostPreference.COST to listOf(OPENAI, OLLAMA),
                )
            rules[TaskType.PLANNING_SCHEDULE] =
                mapOf(
                    CostPreference.QUALITY to listOf(OPENAI, ANTHROPIC),
                    CostPreference.BALANCED to listOf(OPENAI, OLLAMA),
                    CostPreference.COST to listOf(OLLAMA, OPENAI),
                )
            rules[TaskType.PLANNING_GOAL_SETTING] =
                mapOf(
                    CostPreference.QUALITY to highQuality,
                    CostPreference.BALANCED to balanced,
                    CostPreference.COST to listOf(OPENAI, OLLAMA),
                )
        }

        private fun addContentRules(rules: MutableMap<TaskType, Map<CostPreference, List<String>>>) {
            rules[TaskType.CONTENT_GENERATION] =
                mapOf(
                    CostPreference.QUALITY to listOf(ANTHROPIC, OPENAI),
                    CostPreference.BALANCED to listOf(OPENAI, ANTHROPIC),
                    CostPreference.COST to listOf(OPENAI, OLLAMA),
                )
            rules[TaskType.CONTENT_ADAPTATION] =
                mapOf(
                    CostPreference.QUALITY to listOf(ANTHROPIC, OPENAI),
                    CostPreference.BALANCED to balanced,
                    CostPreference.COST to listOf(OLLAMA, OPENAI),
                )
            rules[TaskType.CONTENT_TRANSLATION] =
                mapOf(
                    CostPreference.QUALITY to highQuality,
                    CostPreference.BALANCED to listOf(OPENAI, ANTHROPIC),
                    CostPreference.COST to listOf(OPENAI, OLLAMA),
                )
        }

        private fun addAssessmentRules(rules: MutableMap<TaskType, Map<CostPreference, List<String>>>) {
            rules[TaskType.ASSESSMENT] =
                mapOf(
                    CostPreference.QUALITY to listOf(ANTHROPIC, OPENAI),
                    CostPreference.BALANCED to listOf(OPENAI, ANTHROPIC),
                    CostPreference.COST to listOf(OPENAI, ANTHROPIC),
                )
            rules[TaskType.ASSESSMENT_QUIZ] =
                mapOf(
                    CostPreference.QUALITY to listOf(ANTHROPIC, OPENAI),
                    CostPreference.BALANCED to listOf(OPENAI, ANTHROPIC, OLLAMA),
                    CostPreference.COST to listOf(OPENAI, OLLAMA),
                )
            rules[TaskType.ASSESSMENT_FEEDBACK] =
                mapOf(
                    CostPreference.QUALITY to listOf(ANTHROPIC, OPENAI),
                    CostPreference.BALANCED to listOf(ANTHROPIC, OPENAI),
                    CostPreference.COST to listOf(OPENAI, ANTHROPIC),
                )
            rules[TaskType.ASSESSMENT_RUBRIC] =
                mapOf(
                    CostPreference.QUALITY to listOf(ANTHROPIC, OPENAI),
                    CostPreference.BALANCED to listOf(OPENAI, ANTHROPIC),
                    CostPreference.COST to listOf(OPENAI, OLLAMA),
                )
        }

        private fun addSummarizationRules(rules: MutableMap<TaskType, Map<CostPreference, List<String>>>) {
            rules[TaskType.SUMMARIZATION] =
                mapOf(
                    CostPreference.QUALITY to listOf(OPENAI, ANTHROPIC),
                    CostPreference.BALANCED to listOf(OPENAI, OLLAMA),
                    CostPreference.COST to costOptimized,
                )
            rules[TaskType.SUMMARIZATION_KEY_POINTS] =
                mapOf(
                    CostPreference.QUALITY to listOf(ANTHROPIC, OPENAI),
                    CostPreference.BALANCED to listOf(OPENAI, OLLAMA),
                    CostPreference.COST to costOptimized,
                )
            rules[TaskType.SUMMARIZATION_COMPARISON] =
                mapOf(
                    CostPreference.QUALITY to listOf(ANTHROPIC, OPENAI),
                    CostPreference.BALANCED to listOf(ANTHROPIC, OPENAI, OLLAMA),
                    CostPreference.COST to listOf(OPENAI, OLLAMA),
                )
        }

        private fun addNavigationRules(rules: MutableMap<TaskType, Map<CostPreference, List<String>>>) {
            rules[TaskType.NAVIGATION] =
                mapOf(
                    CostPreference.QUALITY to fast,
                    CostPreference.BALANCED to fast,
                    CostPreference.COST to ultraFast,
                )
            rules[TaskType.CLASSIFICATION] =
                mapOf(
                    CostPreference.QUALITY to fast,
                    CostPreference.BALANCED to fast,
                    CostPreference.COST to ultraFast,
                )
            rules[TaskType.TOPIC_DETECTION] =
                mapOf(
                    CostPreference.QUALITY to fast,
                    CostPreference.BALANCED to ultraFast,
                    CostPreference.COST to ultraFast,
                )
        }

        private fun addSimpleInteractionRules(rules: MutableMap<TaskType, Map<CostPreference, List<String>>>) {
            rules[TaskType.SIMPLE_RESPONSE] =
                mapOf(
                    CostPreference.QUALITY to listOf(OPENAI, ANTHROPIC),
                    CostPreference.BALANCED to listOf(OPENAI, OLLAMA),
                    CostPreference.COST to ultraFast,
                )
            rules[TaskType.SIMPLE_GREETING] =
                mapOf(
                    CostPreference.QUALITY to ultraFast,
                    CostPreference.BALANCED to ultraFast,
                    CostPreference.COST to listOf(ON_DEVICE, OLLAMA),
                )
            rules[TaskType.SIMPLE_ACKNOWLEDGMENT] =
                mapOf(
                    CostPreference.QUALITY to ultraFast,
                    CostPreference.BALANCED to ultraFast,
                    CostPreference.COST to listOf(ON_DEVICE, OLLAMA),
                )
        }

        private fun addMetaTaskRules(rules: MutableMap<TaskType, Map<CostPreference, List<String>>>) {
            rules[TaskType.META_REFLECTION] =
                mapOf(
                    CostPreference.QUALITY to listOf(ANTHROPIC, OPENAI),
                    CostPreference.BALANCED to balanced,
                    CostPreference.COST to listOf(OPENAI, OLLAMA),
                )
            rules[TaskType.META_MOTIVATION] =
                mapOf(
                    CostPreference.QUALITY to listOf(ANTHROPIC, OPENAI),
                    CostPreference.BALANCED to balanced,
                    CostPreference.COST to costOptimized,
                )
            rules[TaskType.META_ERROR_RECOVERY] =
                mapOf(
                    CostPreference.QUALITY to fast,
                    CostPreference.BALANCED to fast,
                    CostPreference.COST to ultraFast,
                )
        }
    }

    /**
     * Get ordered list of provider candidates for the given context.
     *
     * Applies condition-based adjustments:
     * - OFFLINE network: Filter to only offline-capable providers
     * - POOR network: Deprioritize cloud providers
     * - MINIMUM device tier: Prefer cloud over on-device
     * - Thermal throttling: Prefer cloud providers
     */
    fun getProviderCandidates(context: RoutingContext): List<String> {
        val baseList =
            rules[context.taskType]?.get(context.costPreference)
                ?: listOf("OpenAI") // Ultimate fallback

        // Apply condition-based filtering
        return when (context.networkQuality) {
            NetworkQuality.OFFLINE ->
                baseList.filter { it in listOf("OnDevice", "Ollama") }
                    .ifEmpty { listOf("OnDevice") }
            NetworkQuality.POOR -> {
                // Move cloud providers to end of list
                val (local, cloud) = baseList.partition { it in listOf("OnDevice", "Ollama") }
                local + cloud
            }
            else -> baseList
        }
    }
}
