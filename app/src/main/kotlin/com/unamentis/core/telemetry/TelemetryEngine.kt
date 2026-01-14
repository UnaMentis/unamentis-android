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
