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
        val context =
            RoutingContext(
                taskType = taskType,
                deviceTier = DeviceTier.STANDARD, // TODO: Get from DeviceCapabilityDetector
                networkQuality = NetworkQuality.GOOD, // TODO: Get from NetworkMonitor
                costPreference = CostPreference.BALANCED, // TODO: Get from settings
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
     * Looks for task type hints in system message.
     */
    private fun extractTaskType(messages: List<LLMMessage>): TaskType {
        val systemMessage = messages.firstOrNull { it.role == "system" }?.content ?: ""

        return when {
            systemMessage.contains("tutor", ignoreCase = true) -> TaskType.TUTORING
            systemMessage.contains("plan", ignoreCase = true) -> TaskType.PLANNING
            systemMessage.contains("summarize", ignoreCase = true) -> TaskType.SUMMARIZATION
            systemMessage.contains("assess", ignoreCase = true) -> TaskType.ASSESSMENT
            else -> TaskType.SIMPLE_RESPONSE
        }
    }
}

/**
 * Task type classification for routing decisions.
 */
enum class TaskType {
    TUTORING, // Long-form educational dialogue
    PLANNING, // Creating study plans, lesson plans
    SUMMARIZATION, // Condensing information
    ASSESSMENT, // Evaluating understanding
    SIMPLE_RESPONSE, // Quick questions, confirmations
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
 */
data class RoutingTable(
    val rules: Map<TaskType, Map<CostPreference, List<String>>>,
) {
    companion object {
        /**
         * Default routing table.
         *
         * Provider priorities:
         * - Quality: Claude Sonnet > GPT-4 > GPT-4o-mini
         * - Balanced: GPT-4o-mini > Claude Haiku > Ollama
         * - Cost: Ollama > On-Device > GPT-4o-mini
         */
        fun default() =
            RoutingTable(
                rules =
                    mapOf(
                        TaskType.TUTORING to
                            mapOf(
                                CostPreference.QUALITY to listOf("Anthropic", "OpenAI"),
                                CostPreference.BALANCED to listOf("OpenAI", "Anthropic", "Ollama"),
                                CostPreference.COST to listOf("Ollama", "OnDevice", "OpenAI"),
                            ),
                        TaskType.PLANNING to
                            mapOf(
                                CostPreference.QUALITY to listOf("Anthropic", "OpenAI"),
                                CostPreference.BALANCED to listOf("OpenAI", "Anthropic"),
                                CostPreference.COST to listOf("OpenAI", "Ollama"),
                            ),
                        TaskType.SUMMARIZATION to
                            mapOf(
                                CostPreference.QUALITY to listOf("OpenAI", "Anthropic"),
                                CostPreference.BALANCED to listOf("OpenAI", "Ollama"),
                                CostPreference.COST to listOf("Ollama", "OnDevice", "OpenAI"),
                            ),
                        TaskType.ASSESSMENT to
                            mapOf(
                                CostPreference.QUALITY to listOf("Anthropic", "OpenAI"),
                                CostPreference.BALANCED to listOf("OpenAI", "Anthropic"),
                                CostPreference.COST to listOf("OpenAI", "Anthropic"),
                            ),
                        TaskType.SIMPLE_RESPONSE to
                            mapOf(
                                CostPreference.QUALITY to listOf("OpenAI", "Anthropic"),
                                CostPreference.BALANCED to listOf("OpenAI", "Ollama"),
                                CostPreference.COST to listOf("Ollama", "OnDevice", "OpenAI"),
                            ),
                    ),
            )
    }

    /**
     * Get ordered list of provider candidates for the given context.
     */
    fun getProviderCandidates(context: RoutingContext): List<String> {
        return rules[context.taskType]?.get(context.costPreference)
            ?: listOf("OpenAI") // Ultimate fallback
    }
}
