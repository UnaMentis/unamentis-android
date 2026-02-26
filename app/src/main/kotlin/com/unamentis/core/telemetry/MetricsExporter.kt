package com.unamentis.core.telemetry

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.unamentis.core.session.MetricsUploadService
import com.unamentis.data.model.LatencyType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// =============================================================================
// UNIFIED METRIC PAYLOAD DATA MODELS
// =============================================================================

/**
 * Unified metric payload for the management server.
 *
 * This format provides a standardized JSON structure compatible with
 * the management server's unified metrics endpoint. It matches the
 * iOS MetricsExporter.swift pattern for cross-platform consistency.
 *
 * @property client Platform identifier (always "android")
 * @property clientId Unique device identifier, persisted across launches
 * @property clientName Human-readable application name
 * @property sessionId Unique identifier for the session being reported
 * @property timestamp ISO 8601 timestamp of when the metrics were captured
 * @property metrics Latency and token usage measurements
 * @property providers Active service provider information
 * @property resources Device resource utilization data
 * @property networkProfile Current network conditions (e.g., "wifi", "cellular")
 * @property quality Session quality and error information
 */
@Serializable
data class UnifiedMetricPayload(
    val client: String = "android",
    @SerialName("client_id") val clientId: String,
    @SerialName("client_name") val clientName: String = "UnaMentis Android",
    @SerialName("session_id") val sessionId: String,
    val timestamp: String,
    val metrics: MetricsData,
    val providers: ProviderData,
    val resources: ResourceData,
    @SerialName("network_profile") val networkProfile: String? = null,
    val quality: QualityData? = null,
)

/**
 * Latency and usage metrics for a single turn or aggregated session.
 *
 * All latency values are in milliseconds. Token counts track LLM usage.
 *
 * @property sttLatencyMs Speech-to-text transcription latency
 * @property llmTtfbMs LLM time-to-first-byte (time-to-first-token)
 * @property llmCompletionMs LLM total completion time
 * @property ttsTtfbMs TTS time-to-first-byte
 * @property ttsCompletionMs TTS total completion time
 * @property e2eLatencyMs End-to-end turn latency
 * @property sttConfidence STT transcription confidence score (0.0-1.0)
 * @property llmInputTokens Number of input tokens sent to the LLM
 * @property llmOutputTokens Number of output tokens received from the LLM
 * @property ttsAudioDurationMs Duration of generated TTS audio
 */
@Serializable
data class MetricsData(
    @SerialName("stt_latency_ms") val sttLatencyMs: Double? = null,
    @SerialName("llm_ttfb_ms") val llmTtfbMs: Double? = null,
    @SerialName("llm_completion_ms") val llmCompletionMs: Double? = null,
    @SerialName("tts_ttfb_ms") val ttsTtfbMs: Double? = null,
    @SerialName("tts_completion_ms") val ttsCompletionMs: Double? = null,
    @SerialName("e2e_latency_ms") val e2eLatencyMs: Double? = null,
    @SerialName("stt_confidence") val sttConfidence: Double? = null,
    @SerialName("llm_input_tokens") val llmInputTokens: Int? = null,
    @SerialName("llm_output_tokens") val llmOutputTokens: Int? = null,
    @SerialName("tts_audio_duration_ms") val ttsAudioDurationMs: Double? = null,
)

/**
 * Active service provider information.
 *
 * Identifies which providers were used during the session for
 * STT, LLM, and TTS processing.
 *
 * @property stt Speech-to-text provider name (e.g., "deepgram", "whisper")
 * @property llm LLM provider name (e.g., "openai", "anthropic")
 * @property llmModel Specific LLM model identifier (e.g., "gpt-4", "claude-3")
 * @property tts Text-to-speech provider name (e.g., "elevenlabs")
 * @property ttsVoice TTS voice identifier
 */
@Serializable
data class ProviderData(
    val stt: String? = null,
    val llm: String? = null,
    @SerialName("llm_model") val llmModel: String? = null,
    val tts: String? = null,
    @SerialName("tts_voice") val ttsVoice: String? = null,
)

/**
 * Device resource utilization data.
 *
 * Captures system-level resource metrics at the time of export.
 *
 * @property cpuPercent CPU utilization percentage (0.0-100.0)
 * @property memoryMb Memory usage in megabytes
 * @property thermalState Current device thermal state
 * @property batteryLevel Battery level percentage (0.0-100.0)
 * @property batteryState Battery charging state (e.g., "charging", "discharging")
 */
@Serializable
data class ResourceData(
    @SerialName("cpu_percent") val cpuPercent: Double? = null,
    @SerialName("memory_mb") val memoryMb: Double? = null,
    @SerialName("thermal_state") val thermalState: String? = null,
    @SerialName("battery_level") val batteryLevel: Double? = null,
    @SerialName("battery_state") val batteryState: String? = null,
)

/**
 * Session quality information.
 *
 * Captures whether the session completed successfully and any
 * errors encountered during processing.
 *
 * @property success Whether the session completed without fatal errors
 * @property errors Description of any errors encountered
 * @property scenarioName Name of the scenario or curriculum topic
 * @property repetition Repetition count for the scenario
 */
@Serializable
data class QualityData(
    val success: Boolean,
    val errors: String? = null,
    @SerialName("scenario_name") val scenarioName: String? = null,
    val repetition: Int? = null,
)

// =============================================================================
// METRICS EXPORTER
// =============================================================================

/**
 * Unified metrics exporter for the management server.
 *
 * Formats and exports metrics in a standardized JSON format compatible with
 * the management server's unified metrics endpoint. This matches the iOS
 * MetricsExporter.swift pattern for cross-platform consistency.
 *
 * Responsibilities:
 * - Build unified metric payloads from TelemetryEngine data
 * - Include device resource information (battery, thermal, memory)
 * - Persist a stable client identifier across app launches
 * - Export payloads via MetricsUploadService
 *
 * iOS Parity: Equivalent to MetricsExporter.swift
 *
 * @property context Application context for SharedPreferences and system services
 * @property telemetryEngine Engine for reading collected telemetry data
 * @property metricsUploadService Service for uploading metrics to the server
 */
@Singleton
class MetricsExporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val telemetryEngine: TelemetryEngine,
        private val metricsUploadService: MetricsUploadService,
    ) {
        companion object {
            private const val TAG = "MetricsExporter"

            /** SharedPreferences file name for persisted client ID. */
            private const val PREFS_NAME = "MetricsExporter"

            /** SharedPreferences key for the persisted client ID. */
            private const val KEY_CLIENT_ID = "clientId"
        }

        /** JSON serializer configured for the unified payload format. */
        private val json =
            Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
                prettyPrint = false
            }

        /**
         * Export metrics for a session.
         *
         * Builds a unified metric payload from TelemetryEngine data and
         * sends it via MetricsUploadService. The payload includes latency
         * statistics, cost breakdown, and device resource information.
         *
         * @param sessionId The session to export metrics for
         */
        suspend fun export(sessionId: String) {
            try {
                val payload = createPayload(sessionId)
                val payloadJson = json.encodeToString(payload)
                Log.i(TAG, "Exporting unified metrics for session $sessionId")
                Log.d(TAG, "Payload: $payloadJson")

                metricsUploadService.upload(
                    sessionId = sessionId,
                    sessionDurationSeconds = 0.0,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export metrics for session $sessionId: ${e.message}", e)
            }
        }

        /**
         * Create a unified metric payload for a session.
         *
         * Reads latency statistics, cost breakdowns, and device resource
         * information and assembles them into a [UnifiedMetricPayload].
         *
         * @param sessionId The session to create a payload for
         * @return The assembled unified metric payload
         */
        fun createPayload(sessionId: String): UnifiedMetricPayload {
            val sttStats = telemetryEngine.getLatencyStats(sessionId, LatencyType.STT)
            val llmStats = telemetryEngine.getLatencyStats(sessionId, LatencyType.LLM_TTFT)
            val ttsStats = telemetryEngine.getLatencyStats(sessionId, LatencyType.TTS_TTFB)
            val e2eStats = telemetryEngine.getLatencyStats(sessionId, LatencyType.E2E_TURN)

            val metricsData =
                MetricsData(
                    sttLatencyMs = sttStats.median.toDouble().takeIf { it > 0 },
                    llmTtfbMs = llmStats.median.toDouble().takeIf { it > 0 },
                    llmCompletionMs = null,
                    ttsTtfbMs = ttsStats.median.toDouble().takeIf { it > 0 },
                    ttsCompletionMs = null,
                    e2eLatencyMs = e2eStats.median.toDouble().takeIf { it > 0 },
                    sttConfidence = null,
                    llmInputTokens = null,
                    llmOutputTokens = null,
                    ttsAudioDurationMs = null,
                )

            val providerData =
                ProviderData(
                    stt = null,
                    llm = null,
                    llmModel = null,
                    tts = null,
                    ttsVoice = null,
                )

            val resourceData = getResourceData()

            return UnifiedMetricPayload(
                clientId = getClientId(),
                sessionId = sessionId,
                timestamp = formatIso8601(Date()),
                metrics = metricsData,
                providers = providerData,
                resources = resourceData,
            )
        }

        /**
         * Get the persistent client identifier.
         *
         * Returns a UUID that is persisted in SharedPreferences. If no
         * client ID exists, a new one is generated and stored.
         *
         * @return Stable client UUID string
         */
        private fun getClientId(): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existingId = prefs.getString(KEY_CLIENT_ID, null)
            if (existingId != null) {
                return existingId
            }

            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_CLIENT_ID, newId).apply()
            Log.i(TAG, "Generated new client ID: $newId")
            return newId
        }

        /**
         * Collect current device resource utilization data.
         *
         * Reads battery level, battery state, memory usage, and thermal
         * state from Android system services.
         *
         * @return Device resource data snapshot
         */
        private fun getResourceData(): ResourceData {
            val batteryIntent =
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

            val batteryLevel =
                batteryIntent?.let { intent ->
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        (level.toDouble() / scale.toDouble()) * 100.0
                    } else {
                        null
                    }
                }

            val batteryState =
                batteryIntent?.let { intent ->
                    when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                        BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                        BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                        BatteryManager.BATTERY_STATUS_FULL -> "full"
                        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
                        else -> "unknown"
                    }
                }

            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memoryInfo)
            val usedMemoryMb =
                if (memoryInfo.totalMem > 0) {
                    (memoryInfo.totalMem - memoryInfo.availMem).toDouble() / (1024.0 * 1024.0)
                } else {
                    null
                }

            val thermalState =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    getThermalStateString()
                } else {
                    null
                }

            return ResourceData(
                cpuPercent = null,
                memoryMb = usedMemoryMb,
                thermalState = thermalState,
                batteryLevel = batteryLevel,
                batteryState = batteryState,
            )
        }

        /**
         * Get the current thermal state as a human-readable string.
         *
         * Requires Android Q (API 29) or higher.
         *
         * @return Thermal state string or null if unavailable
         */
        @Suppress("NewApi")
        private fun getThermalStateString(): String? {
            val powerManager =
                context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    ?: return null

            return when (powerManager.currentThermalStatus) {
                android.os.PowerManager.THERMAL_STATUS_NONE -> "none"
                android.os.PowerManager.THERMAL_STATUS_LIGHT -> "light"
                android.os.PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
                android.os.PowerManager.THERMAL_STATUS_SEVERE -> "severe"
                android.os.PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
                android.os.PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
                android.os.PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
                else -> "unknown"
            }
        }

        /**
         * Format a [Date] as ISO 8601 string in UTC.
         *
         * @param date The date to format
         * @return ISO 8601 formatted string (e.g., "2024-01-15T10:30:00Z")
         */
        private fun formatIso8601(date: Date): String {
            val formatter =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            return formatter.format(date)
        }
    }
