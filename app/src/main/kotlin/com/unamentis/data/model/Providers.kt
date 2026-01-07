package com.unamentis.data.model

import kotlinx.coroutines.flow.Flow

/**
 * Result from a Speech-to-Text (STT) operation.
 *
 * @property text Transcribed text
 * @property isFinal Whether this is a final transcript (vs interim/partial)
 * @property confidence Confidence score (0.0 - 1.0)
 * @property latencyMs Time from audio capture to result (milliseconds)
 */
data class STTResult(
    val text: String,
    val isFinal: Boolean,
    val confidence: Float = 1.0f,
    val latencyMs: Long = 0
)

/**
 * Interface for Speech-to-Text service providers.
 *
 * Implementations should provide streaming transcription with both
 * partial and final results.
 */
interface STTService {
    /**
     * Start streaming transcription.
     *
     * @return Flow of STT results (both partial and final)
     */
    fun startStreaming(): Flow<STTResult>

    /**
     * Stop streaming and release resources.
     */
    suspend fun stopStreaming()

    /**
     * Provider name for logging and metrics.
     */
    val providerName: String
}

/**
 * Audio chunk from Text-to-Speech synthesis.
 *
 * @property audioData PCM audio data (16-bit, 16kHz, mono)
 * @property isFirst True if this is the first chunk (for TTFB metric)
 * @property isLast True if this is the final chunk
 */
data class TTSAudioChunk(
    val audioData: ByteArray = byteArrayOf(),
    val isFirst: Boolean = false,
    val isLast: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TTSAudioChunk
        if (!audioData.contentEquals(other.audioData)) return false
        if (isFirst != other.isFirst) return false
        if (isLast != other.isLast) return false
        return true
    }

    override fun hashCode(): Int {
        var result = audioData.contentHashCode()
        result = 31 * result + isFirst.hashCode()
        result = 31 * result + isLast.hashCode()
        return result
    }
}

/**
 * Interface for Text-to-Speech service providers.
 *
 * Implementations should provide streaming audio synthesis with
 * low time-to-first-byte for responsive conversations.
 */
interface TTSService {
    /**
     * Synthesize text to audio stream.
     *
     * @param text Text to synthesize
     * @return Flow of audio chunks
     */
    fun synthesize(text: String): Flow<TTSAudioChunk>

    /**
     * Stop synthesis and release resources.
     */
    suspend fun stop()

    /**
     * Provider name for logging and metrics.
     */
    val providerName: String
}

/**
 * Message for LLM conversation.
 *
 * @property role Role of the message sender ("user", "assistant", "system")
 * @property content Message content
 */
data class LLMMessage(
    val role: String,
    val content: String
)

/**
 * Token from LLM streaming response.
 *
 * @property content Token text content
 * @property isDone Whether this is the final token
 */
data class LLMToken(
    val content: String,
    val isDone: Boolean = false
)

/**
 * Interface for Large Language Model service providers.
 *
 * Implementations should provide streaming completion with
 * low time-to-first-token for responsive interactions.
 */
interface LLMService {
    /**
     * Stream completion for a conversation.
     *
     * @param messages Conversation history
     * @param temperature Sampling temperature (0.0 - 2.0)
     * @param maxTokens Maximum tokens to generate
     * @return Flow of generated tokens
     */
    fun streamCompletion(
        messages: List<LLMMessage>,
        temperature: Float = 0.7f,
        maxTokens: Int = 500
    ): Flow<LLMToken>

    /**
     * Stop generation and release resources.
     */
    suspend fun stop()

    /**
     * Provider name for logging and metrics.
     */
    val providerName: String
}

/**
 * Voice Activity Detection result.
 *
 * @property isSpeech Whether speech was detected in the audio frame
 * @property confidence Confidence score (0.0 - 1.0)
 */
data class VADResult(
    val isSpeech: Boolean,
    val confidence: Float
)

/**
 * Interface for Voice Activity Detection service.
 *
 * VAD determines when a user starts and stops speaking to control
 * turn-taking in the conversation.
 */
interface VADService {
    /**
     * Process audio samples and detect speech.
     *
     * @param samples Audio samples (16-bit PCM, 16kHz)
     * @return VAD result
     */
    fun processAudio(samples: FloatArray): VADResult

    /**
     * Release VAD resources.
     */
    fun release()
}
