package com.unamentis.core.telemetry

import com.unamentis.data.model.CostRecord
import com.unamentis.data.model.LatencyMeasurement
import com.unamentis.data.model.LatencyType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Engine for collecting and aggregating telemetry data.
 *
 * Responsibilities:
 * - Track latency measurements per turn
 * - Aggregate cost data from providers
 * - Compute session-level statistics
 * - Provide data for analytics dashboard
 */
@Singleton
class TelemetryEngine
    @Inject
    constructor() {
        private val latencyMeasurements = mutableMapOf<String, MutableList<LatencyMeasurement>>()
        private val costRecords = mutableMapOf<String, MutableList<CostRecord>>()

        private val _currentSessionId = MutableStateFlow<String?>(null)
        val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

        /**
         * Start tracking a new session.
         */
        fun startSession(sessionId: String) {
            _currentSessionId.value = sessionId
            latencyMeasurements[sessionId] = mutableListOf()
            costRecords[sessionId] = mutableListOf()
        }

        /**
         * End the current session.
         */
        fun endSession() {
            _currentSessionId.value = null
        }

        /**
         * Record a latency measurement.
         */
        fun recordLatency(
            type: LatencyType,
            durationMs: Long,
        ) {
            val sessionId = _currentSessionId.value ?: return
            latencyMeasurements[sessionId]?.add(
                LatencyMeasurement(type = type, durationMs = durationMs),
            )
        }

        /**
         * Record a cost incurred.
         */
        fun recordCost(
            provider: String,
            costUsd: Double,
            metadata: Map<String, String> = emptyMap(),
        ) {
            val sessionId = _currentSessionId.value ?: return
            costRecords[sessionId]?.add(
                CostRecord(provider = provider, costUsd = costUsd, metadata = metadata),
            )
        }

        /**
         * Get metrics for a specific session.
         */
        fun getSessionMetrics(sessionId: String): List<TurnMetrics> {
            val measurements = latencyMeasurements[sessionId] ?: return emptyList()
            val costs = costRecords[sessionId] ?: emptyList()

            // Group measurements into turns based on timestamps
            // For simplicity, return aggregated metrics
            if (measurements.isEmpty()) return emptyList()

            val sttLatencies = measurements.filter { it.type == LatencyType.STT }
            val llmLatencies = measurements.filter { it.type == LatencyType.LLM_TTFT }
            val ttsLatencies = measurements.filter { it.type == LatencyType.TTS_TTFB }
            val e2eLatencies = measurements.filter { it.type == LatencyType.E2E_TURN }

            val totalCost = costs.sumOf { it.costUsd }

            return listOf(
                TurnMetrics(
                    turnNumber = 1,
                    sttLatency = sttLatencies.map { it.durationMs.toInt() }.average().takeIf { !it.isNaN() }?.toInt() ?: 0,
                    llmTTFT = llmLatencies.map { it.durationMs.toInt() }.average().takeIf { !it.isNaN() }?.toInt() ?: 0,
                    ttsTTFB = ttsLatencies.map { it.durationMs.toInt() }.average().takeIf { !it.isNaN() }?.toInt() ?: 0,
                    e2eLatency = e2eLatencies.map { it.durationMs.toInt() }.average().takeIf { !it.isNaN() }?.toInt() ?: 0,
                    estimatedCost = totalCost,
                ),
            )
        }

        /**
         * Get latency statistics for a session.
         */
        fun getLatencyStats(
            sessionId: String,
            type: LatencyType,
        ): LatencyStats {
            val measurements =
                latencyMeasurements[sessionId]
                    ?.filter { it.type == type }
                    ?.map { it.durationMs }
                    ?: return LatencyStats()

            if (measurements.isEmpty()) return LatencyStats()

            val sorted = measurements.sorted()
            return LatencyStats(
                min = sorted.first(),
                max = sorted.last(),
                median = sorted[sorted.size / 2],
                p99 = sorted[(sorted.size * 0.99).toInt().coerceIn(0, sorted.lastIndex)],
                average = measurements.average().toLong(),
            )
        }

        /**
         * Get total cost for a session.
         */
        fun getTotalCost(sessionId: String): Double {
            return costRecords[sessionId]?.sumOf { it.costUsd } ?: 0.0
        }

        /**
         * Get cost breakdown by provider type (STT, TTS, LLM).
         * Provider names are mapped to types based on known providers.
         */
        fun getCostBreakdownByType(sessionId: String): ProviderTypeCostBreakdown {
            val costs = costRecords[sessionId] ?: return ProviderTypeCostBreakdown()

            var sttCost = 0.0
            var ttsCost = 0.0
            var llmCost = 0.0

            costs.forEach { record ->
                when (record.metadata["type"]?.uppercase() ?: categorizeProvider(record.provider)) {
                    "STT" -> sttCost += record.costUsd
                    "TTS" -> ttsCost += record.costUsd
                    "LLM" -> llmCost += record.costUsd
                }
            }

            return ProviderTypeCostBreakdown(
                sttCost = sttCost,
                ttsCost = ttsCost,
                llmCost = llmCost,
                totalCost = sttCost + ttsCost + llmCost,
            )
        }

        /**
         * Get detailed cost breakdown by individual provider.
         */
        fun getCostBreakdownByProvider(sessionId: String): List<ProviderCost> {
            val costs = costRecords[sessionId] ?: return emptyList()

            return costs
                .groupBy { it.provider }
                .map { (provider, records) ->
                    ProviderCost(
                        providerName = provider,
                        providerType =
                            records.firstOrNull()?.metadata?.get("type")
                                ?: categorizeProvider(provider),
                        totalCost = records.sumOf { it.costUsd },
                        requestCount = records.size,
                    )
                }
                .sortedByDescending { it.totalCost }
        }

        /**
         * Get aggregated cost breakdown across all sessions.
         */
        fun getAggregatedCostBreakdown(sessionIds: List<String>): ProviderTypeCostBreakdown {
            var sttCost = 0.0
            var ttsCost = 0.0
            var llmCost = 0.0

            sessionIds.forEach { sessionId ->
                val breakdown = getCostBreakdownByType(sessionId)
                sttCost += breakdown.sttCost
                ttsCost += breakdown.ttsCost
                llmCost += breakdown.llmCost
            }

            return ProviderTypeCostBreakdown(
                sttCost = sttCost,
                ttsCost = ttsCost,
                llmCost = llmCost,
                totalCost = sttCost + ttsCost + llmCost,
            )
        }

        /**
         * Get aggregated detailed provider breakdown across all sessions.
         */
        fun getAggregatedProviderBreakdown(sessionIds: List<String>): List<ProviderCost> {
            val allCosts = sessionIds.flatMap { getCostBreakdownByProvider(it) }

            return allCosts
                .groupBy { it.providerName }
                .map { (provider, costs) ->
                    ProviderCost(
                        providerName = provider,
                        providerType = costs.firstOrNull()?.providerType ?: "UNKNOWN",
                        totalCost = costs.sumOf { it.totalCost },
                        requestCount = costs.sumOf { it.requestCount },
                    )
                }
                .sortedByDescending { it.totalCost }
        }

        /**
         * Categorize a provider name into STT, TTS, or LLM type.
         */
        private fun categorizeProvider(provider: String): String {
            val normalized = provider.lowercase()
            return when {
                // STT providers
                normalized.contains("deepgram") -> "STT"
                normalized.contains("whisper") -> "STT"
                normalized.contains("speechrecognizer") -> "STT"
                normalized == "android" && normalized.contains("stt") -> "STT"

                // TTS providers
                normalized.contains("elevenlabs") -> "TTS"
                normalized.contains("eleven_labs") -> "TTS"
                normalized.contains("texttospeech") -> "TTS"
                normalized == "android" && normalized.contains("tts") -> "TTS"

                // LLM providers
                normalized.contains("openai") -> "LLM"
                normalized.contains("anthropic") -> "LLM"
                normalized.contains("claude") -> "LLM"
                normalized.contains("gpt") -> "LLM"
                normalized.contains("patchpanel") -> "LLM"
                normalized.contains("llama") -> "LLM"

                // Default fallback based on common patterns
                else -> "LLM"
            }
        }

        /**
         * Clear all data for a session.
         */
        fun clearSession(sessionId: String) {
            latencyMeasurements.remove(sessionId)
            costRecords.remove(sessionId)
        }
    }

/**
 * Metrics for a single conversation turn.
 */
data class TurnMetrics(
    val turnNumber: Int,
    val sttLatency: Int,
    val llmTTFT: Int,
    val ttsTTFB: Int,
    val e2eLatency: Int,
    val estimatedCost: Double,
)

/**
 * Statistics for latency measurements.
 */
data class LatencyStats(
    val min: Long = 0,
    val max: Long = 0,
    val median: Long = 0,
    val p99: Long = 0,
    val average: Long = 0,
)

// Re-export LatencyType for convenience
typealias LatencyType = com.unamentis.data.model.LatencyType

/**
 * Cost breakdown by provider type (STT, TTS, LLM).
 */
data class ProviderTypeCostBreakdown(
    val sttCost: Double = 0.0,
    val ttsCost: Double = 0.0,
    val llmCost: Double = 0.0,
    val totalCost: Double = 0.0,
)

/**
 * Cost information for a specific provider.
 */
data class ProviderCost(
    val providerName: String,
    val providerType: String,
    val totalCost: Double,
    val requestCount: Int,
)
