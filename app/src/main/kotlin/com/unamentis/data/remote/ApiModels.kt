package com.unamentis.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =============================================================================
// COMMON MODELS
// =============================================================================

/**
 * Standard API error response.
 */
@Serializable
data class ApiError(
    val error: String,
    val code: String? = null,
    val details: Map<String, String> = emptyMap(),
)

/**
 * Result wrapper for API operations.
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()

    data class Error(val error: ApiError, val httpCode: Int) : ApiResult<Nothing>()

    data class NetworkError(val message: String, val cause: Throwable? = null) : ApiResult<Nothing>()
}

// =============================================================================
// AUTHENTICATION MODELS (01-AUTHENTICATION.md)
// =============================================================================

/**
 * Request to register a new user.
 */
@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
)

/**
 * Request to login.
 */
@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    @SerialName("device_name") val deviceName: String,
)

/**
 * Request to refresh access token.
 */
@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

/**
 * Authentication tokens returned on login/register/refresh.
 */
@Serializable
data class AuthTokens(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("token_type") val tokenType: String = "Bearer",
)

/**
 * User profile information.
 */
@Serializable
data class User(
    val id: String,
    val email: String,
    val name: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

/**
 * Request to update user profile.
 */
@Serializable
data class UpdateProfileRequest(
    val name: String? = null,
    val email: String? = null,
)

/**
 * Request to change password.
 */
@Serializable
data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
)

/**
 * Registered device information.
 */
@Serializable
data class Device(
    val id: String,
    val name: String,
    @SerialName("last_seen") val lastSeen: String,
    @SerialName("created_at") val createdAt: String,
)

/**
 * Active user session (login session).
 */
@Serializable
data class UserSession(
    val id: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("ip_address") val ipAddress: String? = null,
    @SerialName("created_at") val createdAt: String,
)

// =============================================================================
// CURRICULUM MODELS (02-CURRICULA.md)
// =============================================================================

/**
 * Summary of a curriculum (returned by GET /api/curricula).
 */
@Serializable
data class CurriculumSummary(
    val id: String,
    val title: String,
    val description: String,
    val version: String,
    @SerialName("topic_count") val topicCount: Int,
    @SerialName("total_duration") val totalDuration: String? = null,
    val difficulty: String? = null,
    @SerialName("age_range") val ageRange: String? = null,
    val keywords: List<String> = emptyList(),
    @SerialName("has_visual_assets") val hasVisualAssets: Boolean = false,
    @SerialName("visual_asset_count") val visualAssetCount: Int = 0,
    val source: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/**
 * Archived curriculum info.
 */
@Serializable
data class ArchivedCurriculum(
    val file: String,
    val title: String,
    @SerialName("archived_at") val archivedAt: String,
    @SerialName("original_id") val originalId: String,
)

/**
 * Request to update curriculum metadata.
 */
@Serializable
data class UpdateCurriculumRequest(
    val title: String? = null,
    val description: String? = null,
    val difficulty: String? = null,
    @SerialName("age_range") val ageRange: String? = null,
    val keywords: List<String>? = null,
)

/**
 * Visual asset metadata for upload.
 */
@Serializable
data class VisualAssetUpload(
    val filename: String,
    @SerialName("mime_type") val mimeType: String,
    val caption: String? = null,
    val position: Int? = null,
)

/**
 * Update asset metadata request.
 */
@Serializable
data class UpdateAssetRequest(
    val caption: String? = null,
    val position: Int? = null,
)

// =============================================================================
// SESSION MODELS (03-SESSIONS.md / FOV Context)
// =============================================================================

/**
 * Session status values.
 */
object SessionStatus {
    const val CREATED = "created"
    const val ACTIVE = "active"
    const val PAUSED = "paused"
    const val ENDED = "ended"
}

/**
 * Request to create a new session.
 */
@Serializable
data class CreateSessionRequest(
    @SerialName("curriculum_id") val curriculumId: String,
    @SerialName("topic_id") val topicId: String? = null,
)

/**
 * Server-side session representation.
 */
@Serializable
data class ApiSession(
    val id: String,
    @SerialName("curriculum_id") val curriculumId: String,
    @SerialName("topic_id") val topicId: String? = null,
    val status: String,
    @SerialName("turn_count") val turnCount: Int = 0,
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
)

/**
 * Session list filter parameters.
 */
@Serializable
data class SessionFilter(
    val status: String? = null,
    @SerialName("curriculum_id") val curriculumId: String? = null,
    val limit: Int? = null,
    val offset: Int? = null,
)

/**
 * Conversation turn in a session.
 */
@Serializable
data class ConversationTurn(
    val id: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val role: String,
    val content: String,
    @SerialName("audio_url") val audioUrl: String? = null,
    val timestamp: String? = null,
    @SerialName("latency_ms") val latencyMs: Int? = null,
)

/**
 * Request to add a conversation turn.
 */
@Serializable
data class AddTurnRequest(
    val role: String,
    val content: String,
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("latency_ms") val latencyMs: Int? = null,
)

/**
 * Request to record barge-in event.
 */
@Serializable
data class BargeInRequest(
    val timestamp: String,
    @SerialName("ai_position_ms") val aiPositionMs: Int,
    @SerialName("user_audio_url") val userAudioUrl: String? = null,
)

/**
 * Request to change topic.
 */
@Serializable
data class ChangeTopicRequest(
    @SerialName("topic_id") val topicId: String,
)

/**
 * Request to set playback position.
 */
@Serializable
data class SetPositionRequest(
    val position: Int,
)

/**
 * FOV (Field of View) context for LLM.
 */
@Serializable
data class FOVContext(
    val topic: TopicContext,
    val position: Int,
    @SerialName("history_window") val historyWindow: Int,
    @SerialName("recent_turns") val recentTurns: List<ConversationTurn>,
)

/**
 * Topic context within FOV.
 */
@Serializable
data class TopicContext(
    val id: String,
    val title: String,
    @SerialName("learning_objectives") val learningObjectives: List<String>,
    val transcript: String? = null,
)

/**
 * Request to build LLM context.
 */
@Serializable
data class BuildContextRequest(
    @SerialName("history_window") val historyWindow: Int = 10,
    @SerialName("include_transcript") val includeTranscript: Boolean = true,
)

/**
 * Session signal (events like pause, resume, topic change).
 */
@Serializable
data class SessionSignal(
    val type: String,
    val value: String? = null,
    val timestamp: String? = null,
)

/**
 * Timeline event in a session.
 */
@Serializable
data class TimelineEvent(
    val id: String,
    val type: String,
    val timestamp: String,
    val data: Map<String, String> = emptyMap(),
)

/**
 * Session debug information.
 */
@Serializable
data class SessionDebugInfo(
    @SerialName("session_id") val sessionId: String,
    val status: String,
    @SerialName("turn_count") val turnCount: Int,
    @SerialName("duration_seconds") val durationSeconds: Int,
    val metrics: SessionMetricsInfo,
)

/**
 * Session metrics in debug info.
 */
@Serializable
data class SessionMetricsInfo(
    @SerialName("stt_latency_median") val sttLatencyMedian: Double? = null,
    @SerialName("llm_latency_median") val llmLatencyMedian: Double? = null,
    @SerialName("tts_latency_median") val ttsLatencyMedian: Double? = null,
    @SerialName("e2e_latency_median") val e2eLatencyMedian: Double? = null,
)

/**
 * FOV health check response.
 */
@Serializable
data class FOVHealthResponse(
    val status: String,
    @SerialName("active_sessions") val activeSessions: Int,
    @SerialName("cache_size") val cacheSize: Int? = null,
)

// =============================================================================
// TTS MODELS (04-TTS.md)
// =============================================================================

/**
 * Supported TTS providers.
 */
object TTSProvider {
    const val ELEVENLABS = "elevenlabs"
    const val DEEPGRAM = "deepgram"
    const val APPLE = "apple"
    const val OPENAI = "openai"
}

/**
 * Supported audio formats.
 */
object AudioFormat {
    const val MP3 = "mp3"
    const val WAV = "wav"
    const val OGG = "ogg"
}

/**
 * Request for TTS generation.
 */
@Serializable
data class TTSRequest(
    val text: String,
    val voice: String? = null,
    val provider: String? = null,
    val speed: Float? = null,
    val format: String? = null,
    @SerialName("return_url") val returnUrl: Boolean = false,
)

/**
 * Response from TTS generation.
 */
@Serializable
data class TTSResponse(
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("duration_ms") val durationMs: Int,
    @SerialName("cache_hit") val cacheHit: Boolean = false,
)

/**
 * TTS cache statistics.
 */
@Serializable
data class TTSCacheStats(
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("entry_count") val entryCount: Int,
    @SerialName("hit_rate") val hitRate: Float,
    @SerialName("max_size_bytes") val maxSizeBytes: Long,
)

/**
 * Request to evict cache by size.
 */
@Serializable
data class CacheEvictRequest(
    @SerialName("bytes_to_free") val bytesToFree: Long,
)

/**
 * Request to prefetch topic audio.
 */
@Serializable
data class PrefetchTopicRequest(
    @SerialName("curriculum_id") val curriculumId: String,
    @SerialName("topic_id") val topicId: String,
    val voice: String? = null,
    val provider: String? = null,
)

/**
 * Prefetch job status.
 */
@Serializable
data class PrefetchJobStatus(
    @SerialName("job_id") val jobId: String,
    val status: String,
    val progress: Float,
    @SerialName("segments_completed") val segmentsCompleted: Int,
    @SerialName("segments_total") val segmentsTotal: Int,
    val error: String? = null,
)

/**
 * TTS voice profile.
 */
@Serializable
data class TTSProfile(
    val id: String,
    val name: String,
    val provider: String,
    @SerialName("voice_id") val voiceId: String,
    val settings: TTSProfileSettings,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/**
 * TTS profile settings (provider-specific).
 */
@Serializable
data class TTSProfileSettings(
    val speed: Float? = null,
    val stability: Float? = null,
    @SerialName("similarity_boost") val similarityBoost: Float? = null,
    val style: Float? = null,
    @SerialName("use_speaker_boost") val useSpeakerBoost: Boolean? = null,
)

/**
 * Request to create TTS profile.
 */
@Serializable
data class CreateTTSProfileRequest(
    val name: String,
    val provider: String,
    @SerialName("voice_id") val voiceId: String,
    val settings: TTSProfileSettings? = null,
)

/**
 * Request to update TTS profile.
 */
@Serializable
data class UpdateTTSProfileRequest(
    val name: String? = null,
    @SerialName("voice_id") val voiceId: String? = null,
    val settings: TTSProfileSettings? = null,
)

/**
 * Request to preview TTS profile.
 */
@Serializable
data class PreviewProfileRequest(
    val text: String,
)

// =============================================================================
// IMPORT MODELS (05-IMPORT.md)
// =============================================================================

/**
 * Import source (Brilliant, Khan Academy, etc.).
 */
@Serializable
data class ImportSource(
    val id: String,
    val name: String,
    val description: String,
    @SerialName("requires_auth") val requiresAuth: Boolean,
    @SerialName("supported_types") val supportedTypes: List<String> = emptyList(),
    val icon: String? = null,
)

/**
 * Course from an import source.
 */
@Serializable
data class ImportCourse(
    val id: String,
    val title: String,
    val description: String,
    @SerialName("topic_count") val topicCount: Int? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val difficulty: String? = null,
    val duration: String? = null,
)

/**
 * Course search result.
 */
@Serializable
data class CourseSearchResult(
    val courses: List<ImportCourse>,
    val total: Int,
    val page: Int,
    @SerialName("per_page") val perPage: Int,
)

/**
 * Request to start import job.
 */
@Serializable
data class StartImportJobRequest(
    @SerialName("source_id") val sourceId: String,
    @SerialName("course_id") val courseId: String? = null,
    val file: String? = null,
    val url: String? = null,
    val options: ImportOptions? = null,
)

/**
 * Import options.
 */
@Serializable
data class ImportOptions(
    @SerialName("generate_audio") val generateAudio: Boolean = false,
    @SerialName("include_assets") val includeAssets: Boolean = true,
)

/**
 * Import job status values.
 */
object ImportJobStatus {
    const val PENDING = "pending"
    const val RUNNING = "running"
    const val COMPLETED = "completed"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"
}

/**
 * Import job status.
 */
@Serializable
data class ImportJob(
    val id: String,
    val status: String,
    val progress: Float,
    @SerialName("topics_imported") val topicsImported: Int,
    @SerialName("topics_total") val topicsTotal: Int,
    @SerialName("curriculum_id") val curriculumId: String? = null,
    val error: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("estimated_completion") val estimatedCompletion: String? = null,
)

/**
 * Import system status.
 */
@Serializable
data class ImportSystemStatus(
    @SerialName("active_jobs") val activeJobs: Int,
    @SerialName("pending_jobs") val pendingJobs: Int,
    @SerialName("completed_today") val completedToday: Int,
)

// =============================================================================
// MEDIA MODELS (06-MEDIA.md)
// =============================================================================

/**
 * Diagram types.
 */
object DiagramType {
    const val MERMAID = "mermaid"
    const val GRAPHVIZ = "graphviz"
    const val PLANTUML = "plantuml"
}

/**
 * Map styles.
 */
object MapStyle {
    const val STANDARD = "standard"
    const val SATELLITE = "satellite"
    const val TERRAIN = "terrain"
    const val DARK = "dark"
}

/**
 * Media capabilities response.
 */
@Serializable
data class MediaCapabilities(
    @SerialName("diagram_types") val diagramTypes: List<String>,
    @SerialName("formula_enabled") val formulaEnabled: Boolean,
    @SerialName("map_styles") val mapStyles: List<String>,
    @SerialName("max_diagram_nodes") val maxDiagramNodes: Int = 100,
    @SerialName("max_formula_length") val maxFormulaLength: Int = 1000,
    @SerialName("max_map_markers") val maxMapMarkers: Int = 50,
)

/**
 * Request to validate/render diagram.
 */
@Serializable
data class DiagramRequest(
    val type: String,
    val code: String,
    val theme: String? = null,
    val background: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val scale: Float? = null,
)

/**
 * Diagram validation result.
 */
@Serializable
data class DiagramValidation(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
)

/**
 * Request to validate/render formula.
 */
@Serializable
data class FormulaRequest(
    val latex: String,
    @SerialName("display_mode") val displayMode: Boolean = false,
    @SerialName("font_size") val fontSize: Int? = null,
    val color: String? = null,
    val background: String? = null,
)

/**
 * Formula validation result.
 */
@Serializable
data class FormulaValidation(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
)

/**
 * Map marker.
 */
@Serializable
data class MapMarker(
    val lat: Double,
    val lng: Double,
    val label: String? = null,
    val color: String? = null,
)

/**
 * Request to render map.
 */
@Serializable
data class MapRequest(
    val center: MapCenter,
    val zoom: Int,
    val style: String? = null,
    val size: MapSize? = null,
    val markers: List<MapMarker> = emptyList(),
)

/**
 * Map center coordinates.
 */
@Serializable
data class MapCenter(
    val lat: Double,
    val lng: Double,
)

/**
 * Map size in pixels.
 */
@Serializable
data class MapSize(
    val width: Int,
    val height: Int,
)

/**
 * Rendered media response.
 */
@Serializable
data class RenderedMedia(
    val url: String,
    @SerialName("content_type") val contentType: String,
    val width: Int,
    val height: Int,
)

// =============================================================================
// SYSTEM MODELS (07-SYSTEM.md)
// =============================================================================

/**
 * Health status values.
 */
object HealthStatusValue {
    const val HEALTHY = "healthy"
    const val DEGRADED = "degraded"
    const val UNHEALTHY = "unhealthy"
}

/**
 * Service status values.
 */
object ServiceStatus {
    const val RUNNING = "running"
    const val STOPPED = "stopped"
    const val ERROR = "error"
}

/**
 * Basic health check response.
 */
@Serializable
data class HealthCheck(
    val status: String,
    val version: String? = null,
    val uptime: Long? = null,
)

/**
 * System metrics response.
 */
@Serializable
data class SystemMetrics(
    @SerialName("cpu_percent") val cpuPercent: Float,
    @SerialName("memory_percent") val memoryPercent: Float,
    @SerialName("memory_used_bytes") val memoryUsedBytes: Long,
    @SerialName("disk_percent") val diskPercent: Float,
    @SerialName("active_sessions") val activeSessions: Int,
    @SerialName("requests_per_minute") val requestsPerMinute: Int,
)

/**
 * System snapshot response.
 */
@Serializable
data class SystemSnapshot(
    val health: HealthCheck,
    val metrics: SystemMetrics,
    val services: List<ServiceInfo>,
    val models: List<ModelInfo>,
)

/**
 * Service information.
 */
@Serializable
data class ServiceInfo(
    val id: String,
    val name: String,
    val status: String,
    val port: Int? = null,
    val uptime: Long? = null,
    val error: String? = null,
)

/**
 * AI model information.
 */
@Serializable
data class ModelInfo(
    val id: String,
    val name: String,
    val type: String,
    val status: String,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    @SerialName("loaded_at") val loadedAt: String? = null,
)

/**
 * Model capabilities.
 */
@Serializable
data class ModelCapabilities(
    val models: List<ModelCapability>,
)

/**
 * Individual model capability.
 */
@Serializable
data class ModelCapability(
    val id: String,
    val name: String,
    val type: String,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("supports_streaming") val supportsStreaming: Boolean = false,
)

/**
 * Request to pull/download model.
 */
@Serializable
data class PullModelRequest(
    @SerialName("model_id") val modelId: String,
)

/**
 * Idle status response.
 */
@Serializable
data class IdleStatus(
    @SerialName("is_idle") val isIdle: Boolean,
    @SerialName("idle_since") val idleSince: String? = null,
    @SerialName("timeout_seconds") val timeoutSeconds: Int,
    @SerialName("models_loaded") val modelsLoaded: Int,
)

/**
 * Idle configuration request.
 */
@Serializable
data class IdleConfigRequest(
    @SerialName("timeout_seconds") val timeoutSeconds: Int,
    @SerialName("unload_models_on_idle") val unloadModelsOnIdle: Boolean = true,
)

/**
 * Connected client information.
 */
@Serializable
data class ConnectedClient(
    val id: String,
    val name: String,
    val platform: String,
    val version: String,
    val status: String,
    @SerialName("connected_at") val connectedAt: String,
    @SerialName("last_seen") val lastSeen: String,
)

/**
 * Aggregated statistics.
 */
@Serializable
data class AggregatedStats(
    @SerialName("total_sessions") val totalSessions: Int,
    @SerialName("active_sessions") val activeSessions: Int,
    @SerialName("total_turns") val totalTurns: Long,
    @SerialName("avg_session_duration") val avgSessionDuration: Double,
    @SerialName("total_audio_generated_seconds") val totalAudioGeneratedSeconds: Double,
)

/**
 * Historical metrics (hourly/daily).
 */
@Serializable
data class HistoricalMetrics(
    val period: String,
    val data: List<MetricDataPoint>,
)

/**
 * Single metric data point.
 */
@Serializable
data class MetricDataPoint(
    val timestamp: String,
    @SerialName("cpu_percent") val cpuPercent: Float? = null,
    @SerialName("memory_percent") val memoryPercent: Float? = null,
    @SerialName("requests") val requests: Int? = null,
    @SerialName("errors") val errors: Int? = null,
)

// =============================================================================
// WEBSOCKET MODELS (08-WEBSOCKET.md)
// Note: Full WebSocket message types are defined in WebSocketClient.kt and
// AudioWebSocketClient.kt for type-safe handling.
// =============================================================================

// =============================================================================
// CLIENT/TELEMETRY MODELS (existing + enhanced)
// =============================================================================

/**
 * Client heartbeat request (POST /api/clients/heartbeat).
 */
@Serializable
data class ClientHeartbeat(
    @SerialName("device_model") val deviceModel: String,
    @SerialName("os_version") val osVersion: String,
    @SerialName("app_version") val appVersion: String,
    val status: String,
    @SerialName("current_session_id") val currentSessionId: String? = null,
    val config: ClientConfig,
)

/**
 * Client configuration snapshot.
 */
@Serializable
data class ClientConfig(
    @SerialName("stt_provider") val sttProvider: String,
    @SerialName("tts_provider") val ttsProvider: String,
    @SerialName("llm_provider") val llmProvider: String,
)

/**
 * Response from client heartbeat.
 */
@Serializable
data class HeartbeatResponse(
    val status: String,
    @SerialName("server_time") val serverTime: String,
    @SerialName("config_updates") val configUpdates: ClientConfig? = null,
)

/**
 * Log entry for remote logging (POST /log).
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
    @SerialName("client_id") val clientId: String,
    @SerialName("client_name") val clientName: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("session_duration") val sessionDuration: Double,
    @SerialName("turns_total") val turnsTotal: Int,
    val interruptions: Int,
    @SerialName("stt_latency_median") val sttLatencyMedian: Double,
    @SerialName("stt_latency_p99") val sttLatencyP99: Double,
    @SerialName("llm_ttft_median") val llmTtftMedian: Double,
    @SerialName("llm_ttft_p99") val llmTtftP99: Double,
    @SerialName("tts_ttfb_median") val ttsTtfbMedian: Double,
    @SerialName("tts_ttfb_p99") val ttsTtfbP99: Double,
    @SerialName("e2e_latency_median") val e2eLatencyMedian: Double,
    @SerialName("e2e_latency_p99") val e2eLatencyP99: Double,
    @SerialName("stt_cost") val sttCost: Double,
    @SerialName("tts_cost") val ttsCost: Double,
    @SerialName("llm_cost") val llmCost: Double,
    @SerialName("total_cost") val totalCost: Double,
    @SerialName("thermal_throttle_events") val thermalThrottleEvents: Int,
    @SerialName("network_degradations") val networkDegradations: Int,
)

/**
 * Response from metrics upload.
 */
@Serializable
data class MetricsUploadResponse(
    val status: String,
    val id: String? = null,
)
