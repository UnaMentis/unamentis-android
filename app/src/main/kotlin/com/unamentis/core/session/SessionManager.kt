package com.unamentis.core.session

import com.unamentis.core.audio.AudioEngine
import com.unamentis.core.curriculum.CurriculumEngine
import com.unamentis.data.model.*
import com.unamentis.services.vad.VADService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import android.util.Log
import java.util.*

/**
 * Core session orchestrator managing voice conversation state machine.
 *
 * State Machine:
 * - IDLE: No active session
 * - USER_SPEAKING: User is speaking (VAD detected speech)
 * - PROCESSING_UTTERANCE: STT finalizing transcription
 * - AI_THINKING: LLM generating response
 * - AI_SPEAKING: TTS playing audio
 * - INTERRUPTED: User barged in during AI speech
 * - PAUSED: Session paused by user
 * - ERROR: Unrecoverable error occurred
 *
 * Conversation Flow:
 * 1. VAD detects speech start → USER_SPEAKING
 * 2. VAD detects 1.5s silence → PROCESSING_UTTERANCE
 * 3. STT finalizes → AI_THINKING
 * 4. LLM starts streaming → AI_SPEAKING (concurrent with TTS)
 * 5. TTS completes → Back to listening (IDLE or USER_SPEAKING)
 *
 * Barge-in:
 * - User can interrupt AI speech after 600ms confirmation window
 * - AI audio stops, LLM generation cancelled
 * - New user utterance processed
 *
 * @property audioEngine Audio I/O engine
 * @property vadService Voice activity detection
 * @property sttService Speech-to-text provider
 * @property ttsService Text-to-speech provider
 * @property llmService Language model provider
 * @property curriculumEngine Curriculum progress tracking
 * @property scope Coroutine scope for session lifecycle
 */
class SessionManager(
    private val audioEngine: AudioEngine,
    private val vadService: VADService,
    private val sttService: STTService,
    private val ttsService: TTSService,
    private val llmService: LLMService,
    private val curriculumEngine: CurriculumEngine,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.IDLE)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    private val _transcript = MutableStateFlow<List<TranscriptEntry>>(emptyList())
    val transcript: StateFlow<List<TranscriptEntry>> = _transcript.asStateFlow()

    private val _metrics = MutableStateFlow(SessionMetrics())
    val metrics: StateFlow<SessionMetrics> = _metrics.asStateFlow()

    // Conversation history for LLM context
    private val conversationHistory = mutableListOf<LLMMessage>()

    // Turn timing
    private var currentTurnStartTime = 0L
    private var userSpeechStartTime = 0L
    private var lastSpeechDetectedTime = 0L

    // Barge-in handling
    private var aiSpeechStartTime = 0L
    private val bargeInConfirmationWindowMs = 600L

    // Active jobs for cancellation
    private var sttJob: Job? = null
    private var llmJob: Job? = null
    private var ttsJob: Job? = null
    private var vadJob: Job? = null

    // Configuration
    private val silenceThresholdMs = 1500L // 1.5 seconds of silence to finalize utterance
    private val vadFrameSizeMs = 32 // 32ms frames at 16kHz = 512 samples

    /**
     * Start a new session.
     *
     * @param curriculumId Optional curriculum to load
     * @param topicId Optional specific topic to start with
     */
    suspend fun startSession(curriculumId: String? = null, topicId: String? = null) {
        if (_sessionState.value != SessionState.IDLE) {
            Log.w("SessionManager", "Cannot start session: already active")
            return
        }

        Log.i("SessionManager", "Starting new session")

        // Create new session
        val session = Session(
            id = UUID.randomUUID().toString(),
            curriculumId = curriculumId,
            currentTopicId = topicId,
            startTime = System.currentTimeMillis(),
            endTime = null,
            totalTurns = 0
        )
        _currentSession.value = session

        // Load curriculum if specified
        curriculumId?.let {
            curriculumEngine.loadCurriculum(it, topicId)
        }

        // Initialize system prompt
        val systemPrompt = buildSystemPrompt()
        conversationHistory.clear()
        conversationHistory.add(LLMMessage(role = "system", content = systemPrompt))

        // Start audio capture
        val captureStarted = audioEngine.startCapture { audioSamples ->
            scope.launch {
                processAudioFrame(audioSamples)
            }
        }

        if (!captureStarted) {
            _sessionState.value = SessionState.ERROR
            Log.e("SessionManager", "Failed to start audio capture")
            return
        }

        _sessionState.value = SessionState.IDLE
        currentTurnStartTime = System.currentTimeMillis()

        Log.i("SessionManager", "Session started: ${session.id}")
    }

    /**
     * Pause the current session.
     */
    suspend fun pauseSession() {
        if (_sessionState.value == SessionState.PAUSED) return

        Log.i("SessionManager", "Pausing session")

        // Cancel all active operations
        cancelActiveOperations()

        // Stop audio
        audioEngine.stopCapture()
        audioEngine.stopPlayback()

        _sessionState.value = SessionState.PAUSED
    }

    /**
     * Resume a paused session.
     */
    suspend fun resumeSession() {
        if (_sessionState.value != SessionState.PAUSED) return

        Log.i("SessionManager", "Resuming session")

        // Restart audio capture
        audioEngine.startCapture { audioSamples ->
            scope.launch {
                processAudioFrame(audioSamples)
            }
        }

        _sessionState.value = SessionState.IDLE
    }

    /**
     * Stop the current session.
     */
    suspend fun stopSession() {
        Log.i("SessionManager", "Stopping session")

        // Cancel all active operations
        cancelActiveOperations()

        // Stop audio
        audioEngine.stopCapture()
        audioEngine.stopPlayback()

        // Finalize session
        _currentSession.value?.let { session ->
            val finalSession = session.copy(
                endTime = System.currentTimeMillis()
            )
            _currentSession.value = finalSession

            // TODO: Save to database via SessionRepository
        }

        _sessionState.value = SessionState.IDLE
        _currentSession.value = null

        Log.i("SessionManager", "Session stopped")
    }

    /**
     * Send a text message (for testing or text-based interaction).
     */
    suspend fun sendTextMessage(text: String) {
        if (_sessionState.value != SessionState.IDLE) {
            Log.w("SessionManager", "Cannot send text: session busy")
            return
        }

        Log.i("SessionManager", "Processing text message: $text")

        // Add to transcript
        val userEntry = TranscriptEntry(
            id = UUID.randomUUID().toString(),
            sessionId = _currentSession.value?.id ?: "",
            role = "user",
            content = text,
            timestamp = System.currentTimeMillis()
        )
        addToTranscript(userEntry)

        // Add to conversation history
        conversationHistory.add(LLMMessage(role = "user", content = text))

        // Get AI response
        processLLMResponse()
    }

    /**
     * Process audio frame from AudioEngine.
     */
    private suspend fun processAudioFrame(audioSamples: FloatArray) {
        // Run VAD
        val vadResult = vadService.processAudio(audioSamples)

        when (_sessionState.value) {
            SessionState.IDLE, SessionState.USER_SPEAKING -> {
                if (vadResult.isSpeech) {
                    handleSpeechDetected()
                } else {
                    handleSilenceDetected()
                }
            }
            SessionState.AI_SPEAKING -> {
                // Check for barge-in
                if (vadResult.isSpeech && canBargeIn()) {
                    handleBargeIn()
                }
            }
            else -> {
                // Other states: ignore audio
            }
        }
    }

    /**
     * Handle speech detected by VAD.
     */
    private suspend fun handleSpeechDetected() {
        lastSpeechDetectedTime = System.currentTimeMillis()

        if (_sessionState.value == SessionState.IDLE) {
            // Start of user utterance
            userSpeechStartTime = System.currentTimeMillis()
            _sessionState.value = SessionState.USER_SPEAKING

            // Start STT streaming
            startSTTStreaming()

            Log.i("SessionManager", "User started speaking")
        }
    }

    /**
     * Handle silence detected by VAD.
     */
    private suspend fun handleSilenceDetected() {
        if (_sessionState.value == SessionState.USER_SPEAKING) {
            val silenceDuration = System.currentTimeMillis() - lastSpeechDetectedTime

            if (silenceDuration >= silenceThresholdMs) {
                // User finished speaking
                _sessionState.value = SessionState.PROCESSING_UTTERANCE

                // Stop STT and finalize
                finalizeSTT()

                Log.i("SessionManager", "User stopped speaking (${silenceDuration}ms silence)")
            }
        }
    }

    /**
     * Check if barge-in is allowed.
     */
    private fun canBargeIn(): Boolean {
        val aiSpeechDuration = System.currentTimeMillis() - aiSpeechStartTime
        return aiSpeechDuration >= bargeInConfirmationWindowMs
    }

    /**
     * Handle user barge-in during AI speech.
     */
    private suspend fun handleBargeIn() {
        Log.i("SessionManager", "Barge-in detected")

        // Cancel AI operations
        llmJob?.cancel()
        ttsJob?.cancel()
        audioEngine.stopPlayback()

        _sessionState.value = SessionState.INTERRUPTED

        // Immediately transition to listening
        userSpeechStartTime = System.currentTimeMillis()
        _sessionState.value = SessionState.USER_SPEAKING

        // Start new STT stream
        startSTTStreaming()
    }

    /**
     * Start STT streaming.
     */
    private fun startSTTStreaming() {
        sttJob?.cancel()
        sttJob = scope.launch {
            try {
                sttService.startStreaming().collect { result ->
                    Log.d("SessionManager", "STT: ${result.text} (final=${result.isFinal})")

                    if (result.isFinal) {
                        // Final transcription received
                        handleFinalTranscription(result.text)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SessionManager", "STT error", e)
                _sessionState.value = SessionState.ERROR
            }
        }
    }

    /**
     * Finalize STT (called when silence threshold reached).
     */
    private suspend fun finalizeSTT() {
        // STT service will emit final result
        // Wait briefly for final transcription
        delay(200)
    }

    /**
     * Handle final transcription from STT.
     */
    private suspend fun handleFinalTranscription(text: String) {
        if (text.isBlank()) {
            // No speech detected, return to idle
            _sessionState.value = SessionState.IDLE
            return
        }

        Log.i("SessionManager", "Final transcription: $text")

        // Stop STT
        sttJob?.cancel()
        sttService.stopStreaming()

        // Add to transcript
        val userEntry = TranscriptEntry(
            id = UUID.randomUUID().toString(),
            sessionId = _currentSession.value?.id ?: "",
            role = "user",
            content = text,
            timestamp = System.currentTimeMillis()
        )
        addToTranscript(userEntry)

        // Add to conversation history
        conversationHistory.add(LLMMessage(role = "user", content = text))

        // Process with LLM
        processLLMResponse()
    }

    /**
     * Process LLM response.
     */
    private fun processLLMResponse() {
        _sessionState.value = SessionState.AI_THINKING

        val llmStartTime = System.currentTimeMillis()
        var firstTokenTime = 0L
        val responseBuffer = StringBuilder()

        llmJob?.cancel()
        llmJob = scope.launch {
            try {
                llmService.streamCompletion(
                    messages = conversationHistory,
                    temperature = 0.7f,
                    maxTokens = 500
                ).collect { token ->
                    if (firstTokenTime == 0L && token.content.isNotEmpty()) {
                        firstTokenTime = System.currentTimeMillis()
                        val ttft = firstTokenTime - llmStartTime
                        Log.i("SessionManager", "LLM TTFT: ${ttft}ms")

                        // Track TTFT metric
                        _metrics.value = _metrics.value.copy(
                            llmTTFT = (_metrics.value.llmTTFT + ttft) / 2 // Running average
                        )

                        // Transition to AI_SPEAKING
                        _sessionState.value = SessionState.AI_SPEAKING
                        aiSpeechStartTime = System.currentTimeMillis()
                    }

                    if (!token.isDone) {
                        responseBuffer.append(token.content)

                        // Stream to TTS in chunks (every ~20 tokens or sentence boundary)
                        if (shouldSendToTTS(responseBuffer.toString())) {
                            val chunk = responseBuffer.toString()
                            synthesizeAndPlay(chunk)
                            responseBuffer.clear()
                        }
                    } else {
                        // LLM complete
                        if (responseBuffer.isNotEmpty()) {
                            val finalChunk = responseBuffer.toString()
                            synthesizeAndPlay(finalChunk)
                            responseBuffer.clear()
                        }

                        // Finalize response
                        finalizeLLMResponse()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SessionManager", "LLM error", e)
                _sessionState.value = SessionState.ERROR
            }
        }
    }

    /**
     * Determine if accumulated text should be sent to TTS.
     */
    private fun shouldSendToTTS(text: String): Boolean {
        // Send on sentence boundaries or every 100 characters
        return text.endsWith(".") || text.endsWith("?") || text.endsWith("!") || text.length >= 100
    }

    /**
     * Synthesize text and play audio.
     */
    private fun synthesizeAndPlay(text: String) {
        ttsJob = scope.launch {
            try {
                val audioChunks = mutableListOf<ByteArray>()
                var firstChunkTime = 0L
                val ttsStartTime = System.currentTimeMillis()

                ttsService.synthesize(text).collect { chunk ->
                    if (firstChunkTime == 0L && chunk.audioData.isNotEmpty()) {
                        firstChunkTime = System.currentTimeMillis()
                        val ttfb = firstChunkTime - ttsStartTime
                        Log.i("SessionManager", "TTS TTFB: ${ttfb}ms")

                        // Track TTFB metric
                        _metrics.value = _metrics.value.copy(
                            ttsTTFB = (_metrics.value.ttsTTFB + ttfb) / 2
                        )
                    }

                    audioChunks.add(chunk.audioData)

                    // Queue audio for playback
                    if (chunk.audioData.isNotEmpty()) {
                        // Convert to float samples and queue
                        val floatSamples = convertBytesToFloat(chunk.audioData)
                        audioEngine.queuePlayback(floatSamples)
                    }
                }

                Log.d("SessionManager", "TTS synthesis complete: $text")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SessionManager", "TTS error", e)
            }
        }
    }

    /**
     * Finalize LLM response and add to transcript.
     */
    private suspend fun finalizeLLMResponse() {
        // Wait for TTS to complete
        ttsJob?.join()

        // Get full response from conversation history
        val fullResponse = conversationHistory.lastOrNull { it.role == "assistant" }?.content ?: ""

        // Add to transcript
        val aiEntry = TranscriptEntry(
            id = UUID.randomUUID().toString(),
            sessionId = _currentSession.value?.id ?: "",
            role = "assistant",
            content = fullResponse,
            timestamp = System.currentTimeMillis()
        )
        addToTranscript(aiEntry)

        // Update session metrics
        _currentSession.value?.let { session ->
            _currentSession.value = session.copy(
                totalTurns = session.totalTurns + 1
            )
        }

        // Calculate E2E latency
        val e2eLatency = System.currentTimeMillis() - currentTurnStartTime
        Log.i("SessionManager", "E2E turn latency: ${e2eLatency}ms")

        _metrics.value = _metrics.value.copy(
            e2eLatency = (_metrics.value.e2eLatency + e2eLatency) / 2
        )

        // Return to listening
        _sessionState.value = SessionState.IDLE
        currentTurnStartTime = System.currentTimeMillis()
    }

    /**
     * Build system prompt for LLM.
     */
    private fun buildSystemPrompt(): String {
        val curriculumContext = curriculumEngine.getCurrentContext()

        return buildString {
            appendLine("You are an AI tutor helping a student learn through voice conversation.")
            appendLine("Keep responses concise and conversational (2-3 sentences).")
            appendLine("Encourage questions and check for understanding.")

            if (curriculumContext != null) {
                appendLine()
                appendLine("Current Topic: ${curriculumContext.topicTitle}")
                appendLine("Learning Objectives: ${curriculumContext.learningObjectives.joinToString(", ")}")
            }
        }
    }

    /**
     * Add entry to transcript.
     */
    private fun addToTranscript(entry: TranscriptEntry) {
        _transcript.value = _transcript.value + entry
    }

    /**
     * Cancel all active operations.
     */
    private fun cancelActiveOperations() {
        sttJob?.cancel()
        llmJob?.cancel()
        ttsJob?.cancel()
        vadJob?.cancel()

        sttService.stopStreaming()
        llmService.stop()
        ttsService.stop()
    }

    /**
     * Convert byte array to float samples.
     */
    private fun convertBytesToFloat(bytes: ByteArray): FloatArray {
        // Assuming 16-bit PCM
        val floats = FloatArray(bytes.size / 2)
        for (i in floats.indices) {
            val sample = (bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 0xFF)
            floats[i] = sample / 32768.0f
        }
        return floats
    }

    /**
     * Release resources.
     */
    fun release() {
        scope.cancel()
        audioEngine.release()
    }
}

/**
 * Session metrics tracked in real-time.
 */
data class SessionMetrics(
    val llmTTFT: Long = 0, // Time to first token (ms)
    val ttsTTFB: Long = 0, // Time to first byte (ms)
    val e2eLatency: Long = 0 // End-to-end turn latency (ms)
)
