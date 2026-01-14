package com.unamentis.data.remote

import kotlinx.serialization.Serializable

/**
 * Summary of a curriculum (returned by GET /api/curricula).
 *
 * @property id Curriculum identifier
 * @property title Curriculum title
 * @property description Brief description
 * @property version Curriculum version
 * @property topicCount Number of topics
 * @property totalDuration Total duration (ISO 8601 format)
 * @property difficulty Difficulty level
 * @property ageRange Recommended age range
 * @property keywords Searchable keywords
 * @property hasVisualAssets Whether curriculum includes visual assets
 * @property visualAssetCount Number of visual assets
 */
@Serializable
data class CurriculumSummary(
    val id: String,
    val title: String,
    val description: String,
    val version: String,
    val topicCount: Int,
    val totalDuration: String? = null,
    val difficulty: String? = null,
    val ageRange: String? = null,
    val keywords: List<String> = emptyList(),
    val hasVisualAssets: Boolean = false,
    val visualAssetCount: Int = 0,
)

/**
 * Client heartbeat request (POST /api/clients/heartbeat).
 *
 * @property deviceModel Device model string
 * @property osVersion Android OS version
 * @property appVersion App version string
 * @property status Client status ("active", "idle", "in_session")
 * @property currentSessionId Active session ID (if any)
 * @property config Current client configuration
 */
@Serializable
data class ClientHeartbeat(
    val deviceModel: String,
    val osVersion: String,
    val appVersion: String,
    val status: String,
    val currentSessionId: String? = null,
    val config: ClientConfig,
)

/**
 * Client configuration snapshot.
 *
 * @property sttProvider Active STT provider
 * @property ttsProvider Active TTS provider
 * @property llmProvider Active LLM provider
 */
@Serializable
data class ClientConfig(
    val sttProvider: String,
    val ttsProvider: String,
    val llmProvider: String,
)

/**
 * Response from client heartbeat.
 *
 * @property status Response status ("ok", "error")
 * @property serverTime Current server time (ISO 8601)
 * @property configUpdates Optional configuration updates from server
 */
@Serializable
data class HeartbeatResponse(
    val status: String,
    val serverTime: String,
    val configUpdates: ClientConfig? = null,
)

/**
 * Log entry for remote logging (POST /log).
 *
 * @property level Log level ("TRACE", "DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL")
 * @property message Log message
 * @property label Component label (e.g., "SessionManager")
 * @property timestamp Unix timestamp (milliseconds)
 * @property file Source file name
 * @property function Function name
 * @property line Line number
 * @property metadata Optional metadata
 */
@Serializable
data class LogEntry(
    val level: String,
    val message: String,
    val label: String,
    val timestamp: Long,
    val file: String = "",
    val function: String = "",
    val line: Int = 0,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Metrics upload request (POST /api/metrics).
 */
@Serializable
data class MetricsUploadRequest(
    val clientId: String,
    val clientName: String,
    val sessionId: String,
    val sessionDuration: Double,
    val turnsTotal: Int,
    val interruptions: Int,
    val sttLatencyMedian: Double,
    val sttLatencyP99: Double,
    val llmTtftMedian: Double,
    val llmTtftP99: Double,
    val ttsTtfbMedian: Double,
    val ttsTtfbP99: Double,
    val e2eLatencyMedian: Double,
    val e2eLatencyP99: Double,
    val sttCost: Double,
    val ttsCost: Double,
    val llmCost: Double,
    val totalCost: Double,
    val thermalThrottleEvents: Int,
    val networkDegradations: Int,
)

/**
 * Response from metrics upload.
 *
 * @property status Response status ("received", "error")
 * @property id Metrics record ID
 */
@Serializable
data class MetricsUploadResponse(
    val status: String,
    val id: String? = null,
)
