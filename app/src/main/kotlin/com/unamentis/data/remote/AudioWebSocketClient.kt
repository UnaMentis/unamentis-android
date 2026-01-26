package com.unamentis.data.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

/**
 * Audio WebSocket message types for voice sessions.
 */
sealed class AudioWebSocketMessage {
    /** Voice activity detected. */
    data class VadStart(val timestamp: String) : AudioWebSocketMessage()

    /** End of speech detected. */
    data class VadEnd(
        val timestamp: String,
        val durationMs: Long,
    ) : AudioWebSocketMessage()

    /** Speech-to-text result. */
    data class Transcript(
        val text: String,
        val confidence: Double,
        val latencyMs: Long,
    ) : AudioWebSocketMessage()

    /** LLM processing started. */
    data class LlmStart(val timestamp: String) : AudioWebSocketMessage()

    /** Streaming LLM token. */
    data class LlmToken(val token: String) : AudioWebSocketMessage()

    /** LLM response complete. */
    data class LlmComplete(
        val text: String,
        val latencyMs: Long,
    ) : AudioWebSocketMessage()

    /** TTS generation started. */
    data class TtsStart(val textLength: Int) : AudioWebSocketMessage()

    /** TTS audio chunk (binary). */
    data class Audio(val data: ByteArray) : AudioWebSocketMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Audio
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    /** TTS generation complete. */
    data class TtsComplete(
        val durationMs: Long,
        val latencyMs: Long,
    ) : AudioWebSocketMessage()

    /** Full turn complete. */
    data class TurnComplete(
        val turnId: String,
        val sttMs: Long,
        val llmMs: Long,
        val ttsMs: Long,
        val totalMs: Long,
    ) : AudioWebSocketMessage()

    /** Visual asset to display. */
    data class VisualAsset(
        val id: String,
        val type: String,
        val url: String,
        val caption: String?,
    ) : AudioWebSocketMessage()

    /** Error occurred. */
    data class Error(
        val code: String,
        val message: String,
        val recoverable: Boolean,
    ) : AudioWebSocketMessage()

    /** Connection established. */
    data object Connected : AudioWebSocketMessage()

    /** Connection closed. */
    data class Disconnected(val code: Int, val reason: String) : AudioWebSocketMessage()

    /** Connection error. */
    data class ConnectionError(val message: String, val cause: Throwable?) : AudioWebSocketMessage()
}

/**
 * Audio WebSocket connection state.
 */
enum class AudioWebSocketState {
    /** Not connected. */
    DISCONNECTED,

    /** Attempting to connect. */
    CONNECTING,

    /** Connected and ready. */
    CONNECTED,

    /** Recording audio. */
    RECORDING,

    /** Processing (STT/LLM/TTS). */
    PROCESSING,

    /** Playing response audio. */
    PLAYING,

    /** Reconnecting after disconnect. */
    RECONNECTING,

    /** Connection failed, not retrying. */
    FAILED,
}

/**
 * Audio configuration for the WebSocket session.
 */
data class AudioConfig(
    val sampleRate: Int = 16000,
    val channels: Int = 1,
    val format: String = "pcm16",
    val vadEnabled: Boolean = true,
    val vadThreshold: Float = 0.5f,
)

// Internal JSON models
@Serializable
private data class RawAudioMessage(
    val type: String,
    val timestamp: String? = null,
    val text: String? = null,
    val token: String? = null,
    val confidence: Double? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("latency_ms") val latencyMs: Long? = null,
    @SerialName("text_length") val textLength: Int? = null,
    @SerialName("turn_id") val turnId: String? = null,
    val metrics: TurnMetrics? = null,
    val asset: AssetData? = null,
    val code: String? = null,
    val message: String? = null,
    val recoverable: Boolean? = null,
)

@Serializable
private data class TurnMetrics(
    @SerialName("stt_ms") val sttMs: Long,
    @SerialName("llm_ms") val llmMs: Long,
    @SerialName("tts_ms") val ttsMs: Long,
    @SerialName("total_ms") val totalMs: Long,
)

@Serializable
private data class AssetData(
    val id: String,
    val type: String,
    val url: String,
    val caption: String? = null,
)

@Serializable
private data class ConfigMessage(
    val type: String = "config",
    @SerialName("sample_rate") val sampleRate: Int,
    val channels: Int,
    val format: String,
    @SerialName("vad_enabled") val vadEnabled: Boolean,
    @SerialName("vad_threshold") val vadThreshold: Float,
)

@Serializable
private data class ControlMessage(
    val type: String,
    @SerialName("topic_id") val topicId: String? = null,
)

/**
 * WebSocket client for audio streaming in voice sessions.
 *
 * Handles bidirectional audio streaming for real-time voice tutoring:
 * - Sends raw PCM audio data
 * - Receives STT transcripts, LLM tokens, and TTS audio
 * - Manages VAD (Voice Activity Detection) events
 *
 * Protocol:
 * - JSON frames for control messages
 * - Binary frames for audio data
 *
 * Features:
 * - Automatic reconnection with session resumption
 * - Configurable audio settings
 * - Real-time metrics and latency tracking
 *
 * Usage:
 * ```kotlin
 * val client = AudioWebSocketClient(okHttpClient, json) { authRepository.getAccessToken() }
 *
 * // Collect messages
 * client.messages.collect { message ->
 *     when (message) {
 *         is AudioWebSocketMessage.Transcript -> showTranscript(message.text)
 *         is AudioWebSocketMessage.Audio -> playAudio(message.data)
 *         // ...
 *     }
 * }
 *
 * // Connect to session
 * client.connect(sessionId = "sess-001")
 *
 * // Configure audio
 * client.configure(AudioConfig(sampleRate = 16000))
 *
 * // Start recording and send audio
 * client.startRecording()
 * client.sendAudio(audioChunk)
 *
 * // Stop recording to trigger processing
 * client.stopRecording()
 *
 * // Disconnect
 * client.disconnect()
 * ```
 */
class AudioWebSocketClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val tokenProvider: suspend () -> String?,
    private val baseUrlProvider: () -> String = { DEFAULT_WS_URL },
) {
    companion object {
        private const val TAG = "AudioWebSocketClient"
        private const val WS_PATH = "/ws/audio"
        private const val PING_INTERVAL_MS = 30_000L
        private const val MAX_RECONNECT_DELAY_MS = 10_000L
        private const val INITIAL_RECONNECT_DELAY_MS = 500L
        private const val DEFAULT_WS_URL = "ws://10.0.2.2:8766"
    }

    private val baseUrl: String
        get() = baseUrlProvider().takeIf { it.isNotBlank() } ?: DEFAULT_WS_URL

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var currentSessionId: String? = null
    private var currentConfig: AudioConfig? = null

    private val _state = MutableStateFlow(AudioWebSocketState.DISCONNECTED)
    val state: StateFlow<AudioWebSocketState> = _state.asStateFlow()

    private val _messages =
        MutableSharedFlow<AudioWebSocketMessage>(
            replay = 0,
            extraBufferCapacity = 256,
        )
    val messages: SharedFlow<AudioWebSocketMessage> = _messages.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Connect to the audio WebSocket for a specific session.
     *
     * @param sessionId The session ID to connect to
     */
    suspend fun connect(sessionId: String) {
        if (_state.value == AudioWebSocketState.CONNECTED && currentSessionId == sessionId) {
            Log.d(TAG, "Already connected to session $sessionId")
            return
        }

        // Disconnect from any existing session
        if (webSocket != null) {
            disconnect()
        }

        currentSessionId = sessionId
        _state.value = AudioWebSocketState.CONNECTING
        reconnectAttempts = 0

        performConnect()
    }

    private suspend fun performConnect() {
        val sessionId =
            currentSessionId ?: run {
                Log.e(TAG, "No session ID set")
                _state.value = AudioWebSocketState.FAILED
                return
            }

        val token = tokenProvider()
        val currentBaseUrl = baseUrl

        val urlBuilder = StringBuilder("$currentBaseUrl$WS_PATH?session=$sessionId")
        if (token != null) {
            urlBuilder.append("&token=$token")
        }

        Log.d(TAG, "Connecting to audio WebSocket for session: $sessionId (URL: $currentBaseUrl)")

        val request =
            Request.Builder()
                .url(urlBuilder.toString())
                .build()

        // Create a dedicated client with WebSocket timeouts
        val wsClient =
            okHttpClient.newBuilder()
                .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
                .build()

        webSocket = wsClient.newWebSocket(request, createWebSocketListener())
    }

    /**
     * Disconnect from the audio WebSocket.
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting audio WebSocket")
        reconnectJob?.cancel()
        pingJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _state.value = AudioWebSocketState.DISCONNECTED
        currentSessionId = null
        currentConfig = null
    }

    /**
     * Configure audio settings.
     *
     * Should be called after connecting and before sending audio.
     *
     * @param config Audio configuration
     */
    fun configure(config: AudioConfig) {
        currentConfig = config

        val message =
            json.encodeToString(
                ConfigMessage(
                    sampleRate = config.sampleRate,
                    channels = config.channels,
                    format = config.format,
                    vadEnabled = config.vadEnabled,
                    vadThreshold = config.vadThreshold,
                ),
            )
        sendText(message)
    }

    /**
     * Start recording audio.
     */
    fun startRecording() {
        if (_state.value != AudioWebSocketState.CONNECTED) {
            Log.w(TAG, "Cannot start recording, not connected")
            return
        }

        _state.value = AudioWebSocketState.RECORDING
        sendText("""{"type": "start_recording"}""")
    }

    /**
     * Stop recording and trigger processing.
     */
    fun stopRecording() {
        if (_state.value != AudioWebSocketState.RECORDING) {
            Log.w(TAG, "Not currently recording")
            return
        }

        _state.value = AudioWebSocketState.PROCESSING
        sendText("""{"type": "stop_recording"}""")
    }

    /**
     * Cancel current processing.
     */
    fun cancel() {
        sendText("""{"type": "cancel"}""")

        if (_state.value == AudioWebSocketState.PROCESSING ||
            _state.value == AudioWebSocketState.PLAYING
        ) {
            _state.value = AudioWebSocketState.CONNECTED
        }
    }

    /**
     * Change current topic.
     *
     * @param topicId The new topic ID
     */
    fun setTopic(topicId: String) {
        val message =
            json.encodeToString(
                ControlMessage(type = "set_topic", topicId = topicId),
            )
        sendText(message)
    }

    /**
     * Send raw audio data.
     *
     * @param data PCM audio data (16-bit, mono, 16kHz)
     */
    fun sendAudio(data: ByteArray) {
        val ws = webSocket
        if (ws != null && _state.value == AudioWebSocketState.RECORDING) {
            val sent = ws.send(data.toByteString())
            if (!sent) {
                Log.w(TAG, "Failed to send audio data")
            }
        }
    }

    /**
     * Send a ping to keep the connection alive.
     */
    fun sendPing() {
        sendText("""{"type": "ping"}""")
    }

    private fun sendText(message: String) {
        val ws = webSocket
        if (ws != null) {
            val sent = ws.send(message)
            if (!sent) {
                Log.w(TAG, "Failed to send message: $message")
            }
        } else {
            Log.w(TAG, "WebSocket not connected, cannot send: $message")
        }
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(
                webSocket: WebSocket,
                response: Response,
            ) {
                Log.i(TAG, "Audio WebSocket connected")
                _state.value = AudioWebSocketState.CONNECTED
                reconnectAttempts = 0

                scope.launch {
                    _messages.emit(AudioWebSocketMessage.Connected)
                }

                // Re-apply configuration if we have one
                currentConfig?.let { configure(it) }

                // Start ping job
                startPingJob()
            }

            override fun onMessage(
                webSocket: WebSocket,
                text: String,
            ) {
                Log.v(TAG, "Received text: $text")
                scope.launch {
                    parseAndEmitTextMessage(text)
                }
            }

            override fun onMessage(
                webSocket: WebSocket,
                bytes: ByteString,
            ) {
                Log.v(TAG, "Received binary: ${bytes.size} bytes")
                scope.launch {
                    _messages.emit(AudioWebSocketMessage.Audio(bytes.toByteArray()))
                }

                // If we receive audio, we're in playing state
                if (_state.value == AudioWebSocketState.PROCESSING) {
                    _state.value = AudioWebSocketState.PLAYING
                }
            }

            override fun onClosing(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                Log.d(TAG, "Audio WebSocket closing: $code $reason")
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                Log.i(TAG, "Audio WebSocket closed: $code $reason")
                handleDisconnect(code, reason)
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?,
            ) {
                Log.e(TAG, "Audio WebSocket failure: ${t.message}", t)
                handleFailure(t)
            }
        }
    }

    private fun handleDisconnect(
        code: Int,
        reason: String,
    ) {
        pingJob?.cancel()
        webSocket = null

        scope.launch {
            _messages.emit(AudioWebSocketMessage.Disconnected(code, reason))
        }

        // Handle close codes
        when (code) {
            1000, 4000 -> {
                // Normal closure or session ended
                _state.value = AudioWebSocketState.DISCONNECTED
                currentSessionId = null
            }
            4001 -> {
                // Session not found
                _state.value = AudioWebSocketState.FAILED
                currentSessionId = null
            }
            4002 -> {
                // Rate limited, wait longer before reconnect
                reconnectAttempts = 5 // Force longer delay
                scheduleReconnect()
            }
            else -> {
                scheduleReconnect()
            }
        }
    }

    private fun handleFailure(t: Throwable) {
        pingJob?.cancel()
        webSocket = null

        scope.launch {
            _messages.emit(AudioWebSocketMessage.ConnectionError(t.message ?: "Unknown error", t))
        }

        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (_state.value == AudioWebSocketState.FAILED || currentSessionId == null) {
            return
        }

        _state.value = AudioWebSocketState.RECONNECTING
        reconnectAttempts++

        val delay =
            (INITIAL_RECONNECT_DELAY_MS * (1 shl minOf(reconnectAttempts - 1, 4)))
                .coerceAtMost(MAX_RECONNECT_DELAY_MS)

        Log.d(TAG, "Scheduling audio reconnect in ${delay}ms (attempt $reconnectAttempts)")

        reconnectJob =
            scope.launch {
                delay(delay)
                performConnect()
            }
    }

    private fun startPingJob() {
        pingJob?.cancel()
        pingJob =
            scope.launch {
                while (isActive && _state.value != AudioWebSocketState.DISCONNECTED) {
                    delay(PING_INTERVAL_MS)
                    sendPing()
                }
            }
    }

    private suspend fun parseAndEmitTextMessage(text: String) {
        try {
            val raw = json.decodeFromString<RawAudioMessage>(text)
            val message = parseRawAudioMessage(raw)
            message?.let { _messages.emit(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse audio message: $text", e)
        }
    }

    private fun parseRawAudioMessage(raw: RawAudioMessage): AudioWebSocketMessage? =
        when (raw.type) {
            "pong" -> null
            "vad_start" -> AudioWebSocketMessage.VadStart(raw.timestamp ?: "")
            "vad_end" -> parseVadEnd(raw)
            "transcript" -> parseTranscript(raw)
            "llm_start" -> AudioWebSocketMessage.LlmStart(raw.timestamp ?: "")
            "llm_token" -> AudioWebSocketMessage.LlmToken(raw.token ?: "")
            "llm_complete" -> parseLlmComplete(raw)
            "tts_start" -> AudioWebSocketMessage.TtsStart(raw.textLength ?: 0)
            "tts_complete" -> parseTtsComplete(raw)
            "turn_complete" -> parseTurnComplete(raw)
            "visual_asset" -> parseVisualAsset(raw)
            "error" -> parseError(raw)
            else -> {
                Log.w(TAG, "Unknown audio message type: ${raw.type}")
                null
            }
        }

    private fun parseVadEnd(raw: RawAudioMessage) =
        AudioWebSocketMessage.VadEnd(
            timestamp = raw.timestamp ?: "",
            durationMs = raw.durationMs ?: 0,
        )

    private fun parseTranscript(raw: RawAudioMessage) =
        AudioWebSocketMessage.Transcript(
            text = raw.text ?: "",
            confidence = raw.confidence ?: 0.0,
            latencyMs = raw.latencyMs ?: 0,
        )

    private fun parseLlmComplete(raw: RawAudioMessage) =
        AudioWebSocketMessage.LlmComplete(
            text = raw.text ?: "",
            latencyMs = raw.latencyMs ?: 0,
        )

    private fun parseTtsComplete(raw: RawAudioMessage) =
        AudioWebSocketMessage.TtsComplete(
            durationMs = raw.durationMs ?: 0,
            latencyMs = raw.latencyMs ?: 0,
        )

    private fun parseTurnComplete(raw: RawAudioMessage): AudioWebSocketMessage {
        val metrics = raw.metrics
        _state.value = AudioWebSocketState.CONNECTED
        return AudioWebSocketMessage.TurnComplete(
            turnId = raw.turnId ?: "",
            sttMs = metrics?.sttMs ?: 0,
            llmMs = metrics?.llmMs ?: 0,
            ttsMs = metrics?.ttsMs ?: 0,
            totalMs = metrics?.totalMs ?: 0,
        )
    }

    private fun parseVisualAsset(raw: RawAudioMessage): AudioWebSocketMessage? =
        raw.asset?.let { asset ->
            AudioWebSocketMessage.VisualAsset(
                id = asset.id,
                type = asset.type,
                url = asset.url,
                caption = asset.caption,
            )
        }

    private fun parseError(raw: RawAudioMessage): AudioWebSocketMessage {
        if (raw.recoverable != true && _state.value != AudioWebSocketState.DISCONNECTED) {
            _state.value = AudioWebSocketState.CONNECTED
        }
        return AudioWebSocketMessage.Error(
            code = raw.code ?: "UNKNOWN",
            message = raw.message ?: "Unknown error",
            recoverable = raw.recoverable ?: false,
        )
    }

    /**
     * Clean up resources.
     */
    fun destroy() {
        disconnect()
        scope.cancel()
    }
}
