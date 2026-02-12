package com.unamentis.data.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
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
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * WebSocket message types for general updates.
 */
sealed class WebSocketMessage {
    /** Real-time log message from server. */
    data class Log(
        val level: String,
        val message: String,
        val source: String,
        val timestamp: String,
    ) : WebSocketMessage()

    /** System metrics update. */
    data class Metric(
        val cpuPercent: Double,
        val memoryPercent: Double,
        val activeSessions: Int,
        val timestamp: String,
    ) : WebSocketMessage()

    /** Session state change. */
    data class SessionUpdate(
        val sessionId: String,
        val status: String,
        val event: String,
        val timestamp: String,
    ) : WebSocketMessage()

    /** Service status change. */
    data class ServiceStatus(
        val serviceId: String,
        val status: String,
        val event: String,
        val timestamp: String,
    ) : WebSocketMessage()

    /** Import job progress. */
    data class ImportProgress(
        val jobId: String,
        val progress: Double,
        val currentTopic: String?,
        val timestamp: String,
    ) : WebSocketMessage()

    /** Pong response to ping. */
    data class Pong(val timestamp: String) : WebSocketMessage()

    /** Connection established. */
    data object Connected : WebSocketMessage()

    /** Connection closed. */
    data class Disconnected(val code: Int, val reason: String) : WebSocketMessage()

    /** Error occurred. */
    data class Error(val message: String, val cause: Throwable?) : WebSocketMessage()
}

/**
 * WebSocket connection state.
 */
enum class WebSocketState {
    /** Not connected. */
    DISCONNECTED,

    /** Attempting to connect. */
    CONNECTING,

    /** Connected and ready. */
    CONNECTED,

    /** Reconnecting after disconnect. */
    RECONNECTING,

    /** Connection failed, not retrying. */
    FAILED,
}

/**
 * Channels available for subscription.
 */
enum class SubscriptionChannel {
    LOGS,
    METRICS,
    SESSIONS,
    SERVICES,
    IMPORTS,
}

// Internal message models for JSON parsing
@Serializable
private data class RawWebSocketMessage(
    val type: String,
    val timestamp: String? = null,
    val data: kotlinx.serialization.json.JsonObject? = null,
    val channels: List<String>? = null,
)

@Serializable
private data class LogData(
    val level: String,
    val message: String,
    val source: String,
)

@Serializable
private data class MetricData(
    @SerialName("cpu_percent") val cpuPercent: Double,
    @SerialName("memory_percent") val memoryPercent: Double,
    @SerialName("active_sessions") val activeSessions: Int,
)

@Serializable
private data class SessionUpdateData(
    @SerialName("session_id") val sessionId: String,
    val status: String,
    val event: String,
)

@Serializable
private data class ServiceStatusData(
    @SerialName("service_id") val serviceId: String,
    val status: String,
    val event: String,
)

@Serializable
private data class ImportProgressData(
    @SerialName("job_id") val jobId: String,
    val progress: Double,
    @SerialName("current_topic") val currentTopic: String? = null,
)

/**
 * WebSocket client for real-time updates from the management console.
 *
 * Provides real-time updates for:
 * - Logs
 * - System metrics
 * - Session state changes
 * - Service status changes
 * - Import job progress
 *
 * Features:
 * - Automatic reconnection with exponential backoff
 * - Heartbeat/ping-pong support
 * - Channel-based subscriptions
 * - Message queuing during disconnection
 *
 * Usage:
 * ```kotlin
 * val client = WebSocketClient(okHttpClient, json) { authRepository.getAccessToken() }
 *
 * // Start receiving messages
 * client.messages.collect { message ->
 *     when (message) {
 *         is WebSocketMessage.Log -> handleLog(message)
 *         is WebSocketMessage.Metric -> updateMetrics(message)
 *         // ...
 *     }
 * }
 *
 * // Connect
 * client.connect()
 *
 * // Subscribe to specific channels
 * client.subscribe(SubscriptionChannel.LOGS, SubscriptionChannel.METRICS)
 *
 * // Disconnect
 * client.disconnect()
 * ```
 */
class WebSocketClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val tokenProvider: suspend () -> String?,
    private val baseUrlProvider: () -> String = { DEFAULT_WS_URL },
) {
    companion object {
        private const val TAG = "WebSocketClient"
        private const val WS_PATH = "/ws"
        private const val PING_INTERVAL_MS = 30_000L
        private const val MAX_RECONNECT_DELAY_MS = 60_000L
        private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val DEFAULT_WS_URL = "ws://10.0.2.2:8766"
    }

    private val baseUrl: String
        get() = baseUrlProvider().takeIf { it.isNotBlank() } ?: DEFAULT_WS_URL

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0

    private val _state = MutableStateFlow(WebSocketState.DISCONNECTED)
    val state: StateFlow<WebSocketState> = _state.asStateFlow()

    private val _messages =
        MutableSharedFlow<WebSocketMessage>(
            replay = 0,
            extraBufferCapacity = 64,
        )
    val messages: SharedFlow<WebSocketMessage> = _messages.asSharedFlow()

    private val pendingMessages = Channel<String>(Channel.BUFFERED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val subscribedChannels = mutableSetOf<SubscriptionChannel>()

    /**
     * Connect to the WebSocket server.
     *
     * If already connected, this is a no-op.
     * Automatically handles authentication using the token provider.
     */
    suspend fun connect() {
        if (_state.value == WebSocketState.CONNECTED || _state.value == WebSocketState.CONNECTING) {
            Log.d(TAG, "Already connected or connecting, skipping")
            return
        }

        _state.value = WebSocketState.CONNECTING
        reconnectAttempts = 0

        performConnect()
    }

    private suspend fun performConnect() {
        val token = tokenProvider()
        val currentBaseUrl = baseUrl

        val url =
            if (token != null) {
                "$currentBaseUrl$WS_PATH?token=$token"
            } else {
                "$currentBaseUrl$WS_PATH"
            }

        Log.d(TAG, "Connecting to WebSocket: $currentBaseUrl$WS_PATH")

        val request =
            Request.Builder()
                .url(url)
                .build()

        // Create a dedicated client with WebSocket timeouts
        val wsClient =
            okHttpClient.newBuilder()
                .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
                .build()

        webSocket = wsClient.newWebSocket(request, createWebSocketListener())
    }

    /**
     * Disconnect from the WebSocket server.
     *
     * Cancels any pending reconnection attempts.
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket")
        reconnectJob?.cancel()
        pingJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _state.value = WebSocketState.DISCONNECTED
        subscribedChannels.clear()
    }

    /**
     * Subscribe to specific update channels.
     *
     * @param channels Channels to subscribe to
     */
    fun subscribe(vararg channels: SubscriptionChannel) {
        subscribedChannels.addAll(channels)

        if (_state.value == WebSocketState.CONNECTED) {
            sendSubscribe(channels.toList())
        }
    }

    /**
     * Unsubscribe from specific update channels.
     *
     * @param channels Channels to unsubscribe from
     */
    fun unsubscribe(vararg channels: SubscriptionChannel) {
        subscribedChannels.removeAll(channels.toSet())

        if (_state.value == WebSocketState.CONNECTED) {
            sendUnsubscribe(channels.toList())
        }
    }

    /**
     * Send a ping to the server.
     */
    fun sendPing() {
        sendMessage("""{"type": "ping"}""")
    }

    private fun sendSubscribe(channels: List<SubscriptionChannel>) {
        val channelNames = channels.map { it.name.lowercase() }
        val message =
            json.encodeToString(
                mapOf(
                    "type" to "subscribe",
                    "channels" to channelNames,
                ),
            )
        sendMessage(message)
    }

    private fun sendUnsubscribe(channels: List<SubscriptionChannel>) {
        val channelNames = channels.map { it.name.lowercase() }
        val message =
            json.encodeToString(
                mapOf(
                    "type" to "unsubscribe",
                    "channels" to channelNames,
                ),
            )
        sendMessage(message)
    }

    private fun sendMessage(message: String) {
        val ws = webSocket
        if (ws != null && _state.value == WebSocketState.CONNECTED) {
            val sent = ws.send(message)
            if (!sent) {
                Log.w(TAG, "Failed to send message, queueing for later")
                scope.launch { pendingMessages.send(message) }
            }
        } else {
            scope.launch { pendingMessages.send(message) }
        }
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(
                webSocket: WebSocket,
                response: Response,
            ) {
                Log.i(TAG, "WebSocket connected")
                _state.value = WebSocketState.CONNECTED
                reconnectAttempts = 0

                scope.launch {
                    _messages.emit(WebSocketMessage.Connected)
                }

                // Re-subscribe to channels
                if (subscribedChannels.isNotEmpty()) {
                    sendSubscribe(subscribedChannels.toList())
                }

                // Drain pending messages before allowing new direct sends.
                // This runs on the same scope to ensure ordering with subsequent sends.
                val pending = mutableListOf<String>()
                while (true) {
                    val message = pendingMessages.tryReceive().getOrNull() ?: break
                    pending.add(message)
                }
                for (message in pending) {
                    webSocket.send(message)
                }

                // Start ping job
                startPingJob()
            }

            override fun onMessage(
                webSocket: WebSocket,
                text: String,
            ) {
                Log.v(TAG, "Received message: $text")
                scope.launch {
                    parseAndEmitMessage(text)
                }
            }

            override fun onClosing(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                Log.d(TAG, "WebSocket closing: $code $reason")
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                Log.i(TAG, "WebSocket closed: $code $reason")
                handleDisconnect(code, reason)
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?,
            ) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
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
            _messages.emit(WebSocketMessage.Disconnected(code, reason))
        }

        // Only reconnect for certain codes
        when (code) {
            1000, 4000 -> {
                // Normal closure or session ended, don't reconnect
                _state.value = WebSocketState.DISCONNECTED
            }
            else -> {
                // Try to reconnect
                scheduleReconnect()
            }
        }
    }

    private fun handleFailure(t: Throwable) {
        pingJob?.cancel()
        webSocket = null

        scope.launch {
            _messages.emit(WebSocketMessage.Error(t.message ?: "Unknown error", t))
        }

        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (_state.value == WebSocketState.FAILED) {
            return
        }

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            _state.value = WebSocketState.FAILED
            scope.launch {
                _messages.emit(
                    WebSocketMessage.Error("Max reconnect attempts reached", null),
                )
            }
            return
        }

        _state.value = WebSocketState.RECONNECTING
        reconnectAttempts++

        val delay =
            (INITIAL_RECONNECT_DELAY_MS * (1 shl minOf(reconnectAttempts - 1, 5)))
                .coerceAtMost(MAX_RECONNECT_DELAY_MS)

        Log.d(TAG, "Scheduling reconnect in ${delay}ms (attempt $reconnectAttempts)")

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
                while (isActive && _state.value == WebSocketState.CONNECTED) {
                    delay(PING_INTERVAL_MS)
                    sendPing()
                }
            }
    }

    private suspend fun parseAndEmitMessage(text: String) {
        try {
            val raw = json.decodeFromString<RawWebSocketMessage>(text)
            val message = parseRawWebSocketMessage(raw)
            message?.let { _messages.emit(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: $text", e)
        }
    }

    private fun parseRawWebSocketMessage(raw: RawWebSocketMessage): WebSocketMessage? {
        val timestamp = raw.timestamp ?: ""
        return when (raw.type) {
            "pong" -> WebSocketMessage.Pong(timestamp)
            "log" -> parseLogMessage(raw.data, timestamp)
            "metric" -> parseMetricMessage(raw.data, timestamp)
            "session_update" -> parseSessionUpdateMessage(raw.data, timestamp)
            "service_status" -> parseServiceStatusMessage(raw.data, timestamp)
            "import_progress" -> parseImportProgressMessage(raw.data, timestamp)
            else -> {
                Log.w(TAG, "Unknown message type: ${raw.type}")
                null
            }
        }
    }

    private fun parseLogMessage(
        data: kotlinx.serialization.json.JsonObject?,
        timestamp: String,
    ): WebSocketMessage.Log? =
        data?.let {
            val logData = json.decodeFromJsonElement<LogData>(it)
            WebSocketMessage.Log(
                level = logData.level,
                message = logData.message,
                source = logData.source,
                timestamp = timestamp,
            )
        }

    private fun parseMetricMessage(
        data: kotlinx.serialization.json.JsonObject?,
        timestamp: String,
    ): WebSocketMessage.Metric? =
        data?.let {
            val metricData = json.decodeFromJsonElement<MetricData>(it)
            WebSocketMessage.Metric(
                cpuPercent = metricData.cpuPercent,
                memoryPercent = metricData.memoryPercent,
                activeSessions = metricData.activeSessions,
                timestamp = timestamp,
            )
        }

    private fun parseSessionUpdateMessage(
        data: kotlinx.serialization.json.JsonObject?,
        timestamp: String,
    ): WebSocketMessage.SessionUpdate? =
        data?.let {
            val sessionData = json.decodeFromJsonElement<SessionUpdateData>(it)
            WebSocketMessage.SessionUpdate(
                sessionId = sessionData.sessionId,
                status = sessionData.status,
                event = sessionData.event,
                timestamp = timestamp,
            )
        }

    private fun parseServiceStatusMessage(
        data: kotlinx.serialization.json.JsonObject?,
        timestamp: String,
    ): WebSocketMessage.ServiceStatus? =
        data?.let {
            val serviceData = json.decodeFromJsonElement<ServiceStatusData>(it)
            WebSocketMessage.ServiceStatus(
                serviceId = serviceData.serviceId,
                status = serviceData.status,
                event = serviceData.event,
                timestamp = timestamp,
            )
        }

    private fun parseImportProgressMessage(
        data: kotlinx.serialization.json.JsonObject?,
        timestamp: String,
    ): WebSocketMessage.ImportProgress? =
        data?.let {
            val importData = json.decodeFromJsonElement<ImportProgressData>(it)
            WebSocketMessage.ImportProgress(
                jobId = importData.jobId,
                progress = importData.progress,
                currentTopic = importData.currentTopic,
                timestamp = timestamp,
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
