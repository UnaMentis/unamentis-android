package com.unamentis.data.model

import kotlinx.serialization.Serializable

/**
 * Types of latency measurements tracked by the telemetry engine.
 */
enum class LatencyType {
    /** Speech-to-Text transcription latency */
    STT,

    /** LLM Time-to-First-Token latency */
    LLM_TTFT,

    /** Text-to-Speech Time-to-First-Byte latency */
    TTS_TTFB,

    /** End-to-end turn latency (user stops speaking → AI starts speaking) */
    E2E_TURN
}

/**
 * Comprehensive session metrics for telemetry.
 *
 * These metrics are collected during a session and uploaded to the
 * management console for analysis and monitoring.
 *
 * @property sessionId Unique session identifier
 * @property sessionDurationSeconds Total session duration
 * @property turnsTotal Number of conversation turns
 * @property interruptions Number of successful barge-ins
 * @property sttLatencyMedian Median STT latency (milliseconds)
 * @property sttLatencyP99 99th percentile STT latency
 * @property llmTtftMedian Median LLM time-to-first-token
 * @property llmTtftP99 99th percentile LLM TTFT
 * @property ttsttfbMedian Median TTS time-to-first-byte
 * @property ttsTtfbP99 99th percentile TTS TTFB
 * @property e2eLatencyMedian Median end-to-end turn latency
 * @property e2eLatencyP99 99th percentile E2E latency
 * @property sttCost Total STT cost (USD)
 * @property ttsCost Total TTS cost (USD)
 * @property llmCost Total LLM cost (USD)
 * @property totalCost Combined API cost (USD)
 * @property thermalThrottleEvents Number of thermal throttling events
 * @property networkDegradations Number of network quality drops
 */
@Serializable
data class SessionMetrics(
    val sessionId: String,
    val sessionDurationSeconds: Double,
    val turnsTotal: Int,
    val interruptions: Int,
    val sttLatencyMedian: Double,
    val sttLatencyP99: Double,
    val llmTtftMedian: Double,
    val llmTtftP99: Double,
    val ttsttfbMedian: Double,
    val ttsTtfbP99: Double,
    val e2eLatencyMedian: Double,
    val e2eLatencyP99: Double,
    val sttCost: Double,
    val ttsCost: Double,
    val llmCost: Double,
    val totalCost: Double,
    val thermalThrottleEvents: Int,
    val networkDegradations: Int
)

/**
 * Latency measurement record.
 *
 * @property type Type of latency being measured
 * @property durationMs Duration in milliseconds
 * @property timestamp When the measurement was taken
 */
data class LatencyMeasurement(
    val type: LatencyType,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Cost tracking record.
 *
 * @property provider Provider name (e.g., "openai", "deepgram")
 * @property costUsd Cost in US dollars
 * @property timestamp When the cost was incurred
 * @property metadata Optional metadata (e.g., model, tokens)
 */
data class CostRecord(
    val provider: String,
    val costUsd: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Device capability tier based on hardware specifications.
 */
enum class DeviceTier {
    /** Flagship devices (12GB+ RAM, 8+ cores) - full on-device capabilities */
    FLAGSHIP,

    /** Standard devices (8GB+ RAM, 6+ cores) - reduced on-device capabilities */
    STANDARD,

    /** Minimum supported (6GB+ RAM) - cloud-primary */
    MINIMUM,

    /** Below minimum requirements */
    UNSUPPORTED
}

/**
 * Thermal status of the device.
 */
enum class ThermalStatus {
    /** Normal operating temperature */
    NOMINAL,

    /** Slightly elevated, no throttling */
    FAIR,

    /** Moderate throttling likely */
    SERIOUS,

    /** Severe throttling, reduce workload */
    CRITICAL
}
