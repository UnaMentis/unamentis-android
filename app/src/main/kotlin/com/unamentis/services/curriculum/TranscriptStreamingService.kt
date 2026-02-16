package com.unamentis.services.curriculum

import android.util.Log
import com.unamentis.core.config.ServerConfigManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TTS server options with their respective ports and sample rates.
 *
 * @property port Network port for the TTS server
 * @property sampleRate Audio sample rate in Hz
 */
enum class TTSServer(
    val port: Int,
    val sampleRate: Int,
) {
    /** VibeVoice TTS at port 8880, 24 kHz. */
    VIBE_VOICE(port = 8880, sampleRate = 24000),

    /** Piper TTS at port 11402, 22050 Hz. */
    PIPER(port = 11402, sampleRate = 22050),
}

/**
 * A segment of transcript with its audio data, delivered during streaming.
 *
 * @property index Zero-based position in the transcript
 * @property type Segment type (narration, explanation, checkpoint, etc.)
 * @property text The text content of the segment
 * @property audioData WAV audio bytes for playback, null if TTS failed
 */
data class StreamedTranscriptSegment(
    val index: Int,
    val type: String,
    val text: String,
    val audioData: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StreamedTranscriptSegment) return false
        return index == other.index &&
            type == other.type &&
            text == other.text &&
            audioData.contentEquals(other.audioData)
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + (audioData?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * State of the transcript streaming service.
 */
sealed class StreamingState {
    /** No streaming active. */
    data object Idle : StreamingState()

    /** Fetching transcript from server. */
    data object FetchingTranscript : StreamingState()

    /** Streaming audio segments. */
    data class Streaming(val segmentIndex: Int, val totalSegments: Int) : StreamingState()

    /** Streaming completed successfully. */
    data object Completed : StreamingState()

    /** Streaming failed with an error. */
    data class Error(val message: String) : StreamingState()
}

/**
 * Service for streaming pre-written transcript audio directly from the TTS server.
 *
 * Ported from iOS TranscriptStreamingService. This bypasses the LLM entirely,
 * enabling near-instant audio playback for curriculum content.
 *
 * Features:
 * - Fetches transcript segments from the management console
 * - Generates audio via TTS server with automatic fallback
 * - Delivers segments in real-time via [SharedFlow]
 * - Supports cancellation and server preference
 * - Sticks with the first working TTS server to prevent voice switching
 *
 * @property okHttpClient HTTP client for network requests
 * @property serverConfigManager Server configuration for host/port
 * @property json JSON parser for transcript responses
 */
@Singleton
class TranscriptStreamingService
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val serverConfigManager: ServerConfigManager,
        private val json: Json,
    ) {
        companion object {
            private const val TAG = "TranscriptStreaming"
            private const val MIN_AUDIO_SIZE_BYTES = 44 // WAV header
            private const val TTS_TIMEOUT_SECONDS = 30L
            private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        }

        private val _state = MutableStateFlow<StreamingState>(StreamingState.Idle)

        /** Observable streaming state. */
        val state: StateFlow<StreamingState> = _state.asStateFlow()

        private val _segments = MutableSharedFlow<StreamedTranscriptSegment>(replay = 0)

        /** Flow of transcript segments with audio as they become available. */
        val segments: SharedFlow<StreamedTranscriptSegment> = _segments.asSharedFlow()

        private var currentJob: Job? = null

        /** Preferred TTS server order (first is tried first). */
        private var ttsServerOrder: List<TTSServer> = listOf(TTSServer.PIPER, TTSServer.VIBE_VOICE)

        /** TTS server confirmed to work for the current session. */
        private var confirmedTTSServer: TTSServer? = null

        /**
         * Set the preferred TTS server. The alternate server is used as fallback.
         *
         * @param server Preferred TTS server to try first
         */
        fun setPreferredTTS(server: TTSServer) {
            ttsServerOrder =
                if (server == TTSServer.PIPER) {
                    listOf(TTSServer.PIPER, TTSServer.VIBE_VOICE)
                } else {
                    listOf(TTSServer.VIBE_VOICE, TTSServer.PIPER)
                }
            Log.i(TAG, "TTS server preference: ${ttsServerOrder.map { it.name }}")
        }

        /**
         * Start streaming transcript audio for a topic.
         *
         * Fetches the transcript from the management console, then iterates through
         * each segment, generating TTS audio and emitting [StreamedTranscriptSegment]
         * events via the [segments] flow.
         *
         * @param curriculumId The curriculum containing the topic
         * @param topicId The topic whose transcript to stream
         * @param voice TTS voice name (default: "nova")
         */
        suspend fun streamTopicAudio(
            curriculumId: String,
            topicId: String,
            voice: String = "nova",
        ) {
            // Cancel any existing stream
            stopStreaming()
            confirmedTTSServer = null

            coroutineScope {
                currentJob =
                    launch {
                        try {
                            performStreaming(curriculumId, topicId, voice)
                        } catch (e: CancellationException) {
                            _state.value = StreamingState.Idle
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "Streaming failed: ${e.message}", e)
                            _state.value = StreamingState.Error(e.message ?: "Unknown error")
                        }
                    }
            }
        }

        /**
         * Stop any active streaming and reset state.
         */
        fun stopStreaming() {
            currentJob?.cancel()
            currentJob = null
            confirmedTTSServer = null
            _state.value = StreamingState.Idle
            Log.i(TAG, "Streaming stopped")
        }

        /**
         * Perform the full streaming pipeline: fetch transcript, generate TTS, emit segments.
         */
        private suspend fun performStreaming(
            curriculumId: String,
            topicId: String,
            voice: String,
        ) {
            val managementUrl = serverConfigManager.getManagementServerUrl()

            // Phase 1: Fetch transcript segments
            _state.value = StreamingState.FetchingTranscript
            val transcriptSegments =
                fetchTranscript(managementUrl, curriculumId, topicId)

            if (transcriptSegments.isEmpty()) {
                throw TranscriptStreamingException("Topic has no transcript segments")
            }

            Log.i(TAG, "Fetched ${transcriptSegments.size} transcript segments")

            // Phase 2: Stream audio for each segment
            var successfulSegments = 0
            var failedSegments = 0

            for ((index, segment) in transcriptSegments.withIndex()) {
                // Check for cancellation
                kotlinx.coroutines.ensureActive()

                val text = segment.content
                if (text.isBlank()) {
                    Log.d(TAG, "Skipping empty segment $index")
                    continue
                }

                _state.value =
                    StreamingState.Streaming(index, transcriptSegments.size)

                // Emit text immediately for display
                _segments.emit(
                    StreamedTranscriptSegment(
                        index = index,
                        type = segment.type,
                        text = text,
                        audioData = null,
                    ),
                )

                // Generate TTS audio
                val hostForTts = serverConfigManager.getServerHost()
                try {
                    val audioData =
                        requestTTSWithFallback(
                            host = hostForTts,
                            text = text,
                            voice = voice,
                            segmentIndex = index,
                            totalSegments = transcriptSegments.size,
                        )

                    _segments.emit(
                        StreamedTranscriptSegment(
                            index = index,
                            type = segment.type,
                            text = text,
                            audioData = audioData,
                        ),
                    )

                    successfulSegments++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failedSegments++
                    Log.e(TAG, "TTS failed for segment $index: ${e.message}")
                    // Continue to next segment
                }
            }

            Log.i(
                TAG,
                "Streaming complete: $successfulSegments successful, $failedSegments failed",
            )
            _state.value = StreamingState.Completed
        }

        /**
         * Fetch transcript segments from the management console.
         */
        private suspend fun fetchTranscript(
            baseUrl: String,
            curriculumId: String,
            topicId: String,
        ): List<TranscriptSegmentDto> =
            withContext(Dispatchers.IO) {
                val url = "$baseUrl/api/curricula/$curriculumId/topics/$topicId/transcript"
                Log.i(TAG, "Fetching transcript from: $url")

                val request = Request.Builder().url(url).get().build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.code == 404) {
                        throw TranscriptStreamingException("Topic not found on server")
                    }

                    if (!response.isSuccessful) {
                        throw TranscriptStreamingException(
                            "Transcript fetch failed: HTTP ${response.code}",
                        )
                    }

                    val body =
                        response.body?.string()
                            ?: throw TranscriptStreamingException("Empty transcript response")

                    try {
                        val transcriptResponse =
                            json.decodeFromString<TranscriptResponseDto>(body)
                        transcriptResponse.segments
                    } catch (e: Exception) {
                        throw TranscriptStreamingException(
                            "Failed to parse transcript: ${e.message}",
                            e,
                        )
                    }
                }
            }

        /**
         * Request TTS with fallback to alternate servers.
         *
         * Once a server succeeds, it is used exclusively for the rest of the session
         * to avoid voice switching mid-stream.
         */
        private suspend fun requestTTSWithFallback(
            host: String,
            text: String,
            voice: String,
            segmentIndex: Int,
            totalSegments: Int,
        ): ByteArray {
            // If we already found a working server, use it exclusively
            confirmedTTSServer?.let { server ->
                return requestTTS(host, server, text, voice, segmentIndex, totalSegments)
            }

            // First segment: try servers in order and remember which one works
            var lastException: Exception? = null

            for (server in ttsServerOrder) {
                try {
                    val audioData =
                        requestTTS(host, server, text, voice, segmentIndex, totalSegments)
                    confirmedTTSServer = server
                    Log.i(TAG, "Confirmed TTS server for session: ${server.name}")
                    return audioData
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastException = e
                    Log.w(
                        TAG,
                        "TTS server ${server.name} failed, trying next: ${e.message}",
                    )
                }
            }

            throw lastException
                ?: TranscriptStreamingException("All TTS servers failed to generate audio")
        }

        /**
         * Request TTS from a specific server.
         */
        private suspend fun requestTTS(
            host: String,
            server: TTSServer,
            text: String,
            voice: String,
            segmentIndex: Int,
            totalSegments: Int,
        ): ByteArray =
            withContext(Dispatchers.IO) {
                val url = "http://$host:${server.port}/v1/audio/speech"
                val ttsBody =
                    json.encodeToString(
                        TTSRequestDto.serializer(),
                        TTSRequestDto(
                            model = "tts-1",
                            input = text,
                            voice = voice,
                            responseFormat = "wav",
                        ),
                    )

                val request =
                    Request.Builder()
                        .url(url)
                        .post(ttsBody.toRequestBody(JSON_MEDIA_TYPE))
                        .build()

                Log.i(
                    TAG,
                    "[${server.name}] Requesting TTS segment " +
                        "${segmentIndex + 1}/$totalSegments: ${text.take(50)}...",
                )

                val startTime = System.currentTimeMillis()
                okHttpClient.newCall(request).execute().use { response ->
                    val latencyMs = System.currentTimeMillis() - startTime

                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "unknown"
                        throw TranscriptStreamingException(
                            "TTS error (HTTP ${response.code}): $errorBody",
                        )
                    }

                    val audioData =
                        response.body?.bytes()
                            ?: throw TranscriptStreamingException(
                                "Empty TTS response from ${server.name}",
                            )

                    if (audioData.size < MIN_AUDIO_SIZE_BYTES) {
                        throw TranscriptStreamingException(
                            "Audio data too small: ${audioData.size} bytes",
                        )
                    }

                    Log.i(
                        TAG,
                        "[${server.name}] Got ${audioData.size} bytes of audio " +
                            "in ${latencyMs}ms",
                    )
                    audioData
                }
            }
    }

// =============================================================================
// INTERNAL DTO MODELS
// =============================================================================

/**
 * DTO for transcript API response.
 */
@Serializable
internal data class TranscriptResponseDto(
    val segments: List<TranscriptSegmentDto> = emptyList(),
)

/**
 * DTO for a single transcript segment from the server.
 */
@Serializable
internal data class TranscriptSegmentDto(
    val id: String = "",
    val type: String = "narration",
    val content: String = "",
)

/**
 * DTO for TTS request body.
 */
@Serializable
internal data class TTSRequestDto(
    val model: String,
    val input: String,
    val voice: String,
    @kotlinx.serialization.SerialName("response_format")
    val responseFormat: String,
)

/**
 * Exception thrown when transcript streaming operations fail.
 *
 * @property message Description of the failure
 * @property cause Optional underlying exception
 */
class TranscriptStreamingException(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)
