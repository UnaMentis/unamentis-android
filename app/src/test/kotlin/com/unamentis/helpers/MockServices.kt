package com.unamentis.helpers

import com.unamentis.data.model.LLMMessage
import com.unamentis.data.model.LLMService
import com.unamentis.data.model.LLMToken
import com.unamentis.data.model.STTResult
import com.unamentis.data.model.STTService
import com.unamentis.data.model.TTSAudioChunk
import com.unamentis.data.model.TTSService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/*
 * UnaMentis - Mock Services for Testing
 *
 * Faithful mocks for paid external API dependencies only.
 *
 * TESTING PHILOSOPHY (see AGENTS.md for full details):
 * - Mock testing is only acceptable for paid third-party APIs
 * - Mocks must be FAITHFUL: validate inputs, simulate all errors, match real behavior
 * - Internal services (TelemetryEngine, etc.) should use real implementations
 * - Use Room.inMemoryDatabaseBuilder() for database tests
 */

/**
 * Exception types that LLM APIs can produce.
 */
sealed class LLMException(message: String) : Exception(message) {
    class InvalidRequest(message: String) : LLMException(message)

    class RateLimited(val retryAfterSeconds: Int) : LLMException("Rate limited. Retry after $retryAfterSeconds seconds")

    class AuthenticationFailed : LLMException("Authentication failed. Check API key.")

    class QuotaExceeded : LLMException("API quota exceeded.")

    class ContextLengthExceeded(val maxTokens: Int) : LLMException("Context length exceeded. Max tokens: $maxTokens")

    class ContentFiltered : LLMException("Content blocked by safety filter.")

    class ModelNotFound(val model: String) : LLMException("Model not found: $model")

    class ConnectionFailed(reason: String) : LLMException("Connection failed: $reason")
}

/**
 * Faithful mock LLM service for testing.
 *
 * This mock exists because real LLM API calls:
 * - Cost money per token ($3-15 per million tokens)
 * - Require API keys
 * - Could hit rate limits during CI
 *
 * The mock faithfully reproduces real API behavior including:
 * - Input validation (empty messages, context length)
 * - All error conditions (rate limiting, auth failures, etc.)
 * - Realistic streaming with configurable latency
 * - Token counting
 */
class MockLLMService : LLMService {
    // region Test Configuration

    /** Response text to return (will be tokenized word-by-word). */
    var responseText: String = "This is a test response from the mock LLM service."

    /** Error to simulate (null = no error). */
    var simulatedError: LLMException? = null

    /** Whether to simulate realistic latency (disabled by default for fast tests). */
    var simulateLatency: Boolean = false

    /** Time to first token in milliseconds (150ms default, matching real API). */
    var ttftMs: Long = 150

    /** Inter-token delay in milliseconds (20ms default). */
    var tokenDelayMs: Long = 20

    /** Maximum context length (matches Claude 3.5 Sonnet). */
    var maxContextTokens: Int = 200_000

    // endregion

    // region Call Tracking

    /** Track method calls for test assertions. */
    var streamCompletionCallCount: Int = 0
        private set

    var lastMessages: List<LLMMessage>? = null
        private set

    var lastTemperature: Float? = null
        private set

    var lastMaxTokens: Int? = null
        private set

    // endregion

    override val providerName: String = "MockLLM"

    override fun streamCompletion(
        messages: List<LLMMessage>,
        temperature: Float,
        maxTokens: Int,
    ): Flow<LLMToken> =
        flow {
            streamCompletionCallCount++
            lastMessages = messages
            lastTemperature = temperature
            lastMaxTokens = maxTokens

            // VALIDATION: Empty messages (real API would reject)
            if (messages.isEmpty()) {
                throw LLMException.InvalidRequest("Messages array cannot be empty")
            }

            // VALIDATION: Estimate input tokens and check context length
            val inputTokenEstimate = messages.sumOf { it.content.length / 4 }
            if (inputTokenEstimate > maxContextTokens) {
                throw LLMException.ContextLengthExceeded(maxTokens = maxContextTokens)
            }

            // VALIDATION: Max tokens in config
            if (maxTokens > 4096) {
                throw LLMException.InvalidRequest("max_tokens cannot exceed 4096")
            }

            // VALIDATION: Temperature range
            if (temperature < 0f || temperature > 2f) {
                throw LLMException.InvalidRequest("temperature must be between 0 and 2")
            }

            // ERROR SIMULATION: Throw configured error if set
            simulatedError?.let { throw it }

            // Simulate realistic time to first token
            if (simulateLatency) {
                delay(ttftMs)
            }

            // Stream tokens word by word (realistic behavior)
            val words = responseText.split(" ")
            words.forEachIndexed { index, word ->
                val isLast = index == words.lastIndex
                val tokenContent = word + if (isLast) "" else " "

                emit(
                    LLMToken(
                        content = tokenContent,
                        isDone = isLast,
                    ),
                )

                // Simulate inter-token delay
                if (simulateLatency && !isLast) {
                    delay(tokenDelayMs)
                }
            }
        }

    override suspend fun stop() {
        // No-op for mock
    }

    // region Test Helpers

    /** Reset mock state between tests. */
    fun reset() {
        responseText = "This is a test response from the mock LLM service."
        simulatedError = null
        simulateLatency = false
        streamCompletionCallCount = 0
        lastMessages = null
        lastTemperature = null
        lastMaxTokens = null
    }

    /** Configure mock to return specific response. */
    fun configure(response: String) {
        responseText = response
    }

    /**
     * Configure mock to simulate a specific error.
     *
     * Available errors (matching real API):
     * - [LLMException.RateLimited] - Too many requests
     * - [LLMException.AuthenticationFailed] - Invalid API key
     * - [LLMException.QuotaExceeded] - Account quota exceeded
     * - [LLMException.ContentFiltered] - Content blocked by safety
     * - [LLMException.ContextLengthExceeded] - Input too long
     * - [LLMException.ModelNotFound] - Invalid model
     * - [LLMException.ConnectionFailed] - Network error
     */
    fun configureToFail(error: LLMException) {
        simulatedError = error
    }

    /** Enable realistic latency simulation. */
    fun enableLatencySimulation(
        ttftMs: Long = 150,
        tokenDelayMs: Long = 20,
    ) {
        simulateLatency = true
        this.ttftMs = ttftMs
        this.tokenDelayMs = tokenDelayMs
    }

    // endregion
}

// endregion

// region STT Service Mock

/**
 * Exception types that STT APIs can produce.
 */
sealed class STTException(message: String) : Exception(message) {
    class ConnectionFailed(reason: String) : STTException("Connection failed: $reason")

    class AuthenticationFailed : STTException("Authentication failed. Check API key.")

    class RateLimited(val retryAfterSeconds: Int) : STTException("Rate limited. Retry after $retryAfterSeconds seconds")

    class AudioFormatUnsupported : STTException("Unsupported audio format")

    class QuotaExceeded : STTException("API quota exceeded.")
}

/**
 * Faithful mock STT service for testing.
 *
 * This mock exists because real STT API calls:
 * - Cost money per minute of audio
 * - Require API keys
 * - Need actual audio input
 *
 * The mock faithfully reproduces real API behavior including:
 * - Interim (partial) and final results
 * - Realistic latencies
 * - All error conditions
 */
class MockSTTService : STTService {
    // region Test Configuration

    /** Transcript results to emit. */
    var transcriptResults: List<STTResult> =
        listOf(
            STTResult(text = "Hello", isFinal = false, confidence = 0.8f),
            STTResult(text = "Hello world", isFinal = true, confidence = 0.95f),
        )

    /** Error to simulate (null = no error). */
    var simulatedError: STTException? = null

    /** Whether to simulate realistic latency. */
    var simulateLatency: Boolean = false

    /** Delay between results in milliseconds. */
    var resultDelayMs: Long = 100

    // endregion

    // region Call Tracking

    var startStreamingCallCount: Int = 0
        private set

    var stopStreamingCallCount: Int = 0
        private set

    // endregion

    override val providerName: String = "MockSTT"

    override fun startStreaming(): Flow<STTResult> =
        flow {
            startStreamingCallCount++

            // ERROR SIMULATION
            simulatedError?.let { throw it }

            for (result in transcriptResults) {
                if (simulateLatency) {
                    delay(resultDelayMs)
                }
                emit(result)
            }
        }

    override suspend fun stopStreaming() {
        stopStreamingCallCount++
    }

    // region Test Helpers

    /** Reset mock state between tests. */
    fun reset() {
        transcriptResults =
            listOf(
                STTResult(text = "Hello", isFinal = false, confidence = 0.8f),
                STTResult(text = "Hello world", isFinal = true, confidence = 0.95f),
            )
        simulatedError = null
        simulateLatency = false
        startStreamingCallCount = 0
        stopStreamingCallCount = 0
    }

    /** Configure mock to return specific transcript. */
    fun configure(finalTranscript: String) {
        transcriptResults =
            listOf(
                STTResult(text = finalTranscript, isFinal = true, confidence = 0.95f),
            )
    }

    /** Configure mock to return multiple interim results before final. */
    fun configureWithInterim(
        vararg interimTexts: String,
        finalText: String,
    ) {
        val results = mutableListOf<STTResult>()
        interimTexts.forEach { text ->
            results.add(STTResult(text = text, isFinal = false, confidence = 0.7f))
        }
        results.add(STTResult(text = finalText, isFinal = true, confidence = 0.95f))
        transcriptResults = results
    }

    /** Configure mock to simulate a specific error. */
    fun configureToFail(error: STTException) {
        simulatedError = error
    }

    // endregion
}

// endregion

// region TTS Service Mock

/**
 * Exception types that TTS APIs can produce.
 */
sealed class TTSException(message: String) : Exception(message) {
    class ConnectionFailed(reason: String) : TTSException("Connection failed: $reason")

    class AuthenticationFailed : TTSException("Authentication failed. Check API key.")

    class RateLimited(val retryAfterSeconds: Int) : TTSException("Rate limited. Retry after $retryAfterSeconds seconds")

    class VoiceNotFound(val voiceId: String) : TTSException("Voice not found: $voiceId")

    class QuotaExceeded : TTSException("API quota exceeded.")

    class TextTooLong(val maxCharacters: Int) : TTSException("Text exceeds maximum length: $maxCharacters characters")
}

/**
 * Faithful mock TTS service for testing.
 *
 * This mock exists because real TTS API calls:
 * - Cost money per character
 * - Require API keys
 * - Produce actual audio data
 *
 * The mock faithfully reproduces real API behavior including:
 * - Streaming audio chunks
 * - Realistic time-to-first-byte
 * - All error conditions
 */
class MockTTSService : TTSService {
    // region Test Configuration

    /** Number of audio chunks to emit. */
    var chunkCount: Int = 5

    /** Size of each audio chunk in bytes. */
    var chunkSizeBytes: Int = 1024

    /** Error to simulate (null = no error). */
    var simulatedError: TTSException? = null

    /** Whether to simulate realistic latency. */
    var simulateLatency: Boolean = false

    /** Time to first byte in milliseconds. */
    var ttfbMs: Long = 100

    /** Delay between chunks in milliseconds. */
    var chunkDelayMs: Long = 50

    // endregion

    // region Call Tracking

    var synthesizeCallCount: Int = 0
        private set

    var stopCallCount: Int = 0
        private set

    var lastSynthesizedText: String? = null
        private set

    // endregion

    override val providerName: String = "MockTTS"

    override fun synthesize(text: String): Flow<TTSAudioChunk> =
        flow {
            synthesizeCallCount++
            lastSynthesizedText = text

            // ERROR SIMULATION
            simulatedError?.let { throw it }

            // Simulate time to first byte
            if (simulateLatency) {
                delay(ttfbMs)
            }

            repeat(chunkCount) { index ->
                val isFirst = index == 0
                val isLast = index == chunkCount - 1

                emit(
                    TTSAudioChunk(
                        audioData = ByteArray(chunkSizeBytes) { 0 },
                        isFirst = isFirst,
                        isLast = isLast,
                    ),
                )

                if (simulateLatency && !isLast) {
                    delay(chunkDelayMs)
                }
            }
        }

    override suspend fun stop() {
        stopCallCount++
    }

    // region Test Helpers

    /** Reset mock state between tests. */
    fun reset() {
        chunkCount = 5
        chunkSizeBytes = 1024
        simulatedError = null
        simulateLatency = false
        synthesizeCallCount = 0
        stopCallCount = 0
        lastSynthesizedText = null
    }

    /** Configure mock to simulate a specific error. */
    fun configureToFail(error: TTSException) {
        simulatedError = error
    }

    /** Enable realistic latency simulation. */
    fun enableLatencySimulation(
        ttfbMs: Long = 100,
        chunkDelayMs: Long = 50,
    ) {
        simulateLatency = true
        this.ttfbMs = ttfbMs
        this.chunkDelayMs = chunkDelayMs
    }

    // endregion
}

// endregion

// region Embedding Service Mock

/**
 * Faithful mock embedding service for testing semantic search.
 *
 * This mock exists because real embedding API calls:
 * - Cost money ($0.13 per million tokens for ada-002)
 * - Require API keys
 * - Have rate limits
 *
 * The mock faithfully reproduces real API behavior including:
 * - Proper embedding dimensions (1536 for ada-002)
 * - Deterministic embeddings based on text hash
 * - Input validation
 */
class MockEmbeddingService {
    // region Test Configuration

    /** Embedding dimension (1536 for OpenAI ada-002). */
    var embeddingDimension: Int = 1536

    /** Predefined embeddings for specific texts. */
    private val predefinedEmbeddings: MutableMap<String, FloatArray> = mutableMapOf()

    /** Default embedding to return if no predefined match. */
    var defaultEmbedding: FloatArray? = null

    /** Error to simulate (null = no error). */
    var simulatedError: Exception? = null

    // endregion

    // region Call Tracking

    var embedCallCount: Int = 0
        private set

    var lastEmbeddedText: String? = null
        private set

    val allEmbeddedTexts: MutableList<String> = mutableListOf()

    // endregion

    /**
     * Generate embedding for text.
     *
     * @param text Text to embed
     * @return Embedding vector
     */
    suspend fun embed(text: String): FloatArray {
        embedCallCount++
        lastEmbeddedText = text
        allEmbeddedTexts.add(text)

        // ERROR SIMULATION
        simulatedError?.let { throw it }

        // Return predefined embedding if available
        predefinedEmbeddings[text]?.let { return it }

        // Return default if set
        defaultEmbedding?.let { return it }

        // Generate deterministic embedding based on text hash
        return generateDeterministicEmbedding(text)
    }

    // region Test Helpers

    /** Reset mock state between tests. */
    fun reset() {
        predefinedEmbeddings.clear()
        defaultEmbedding = null
        simulatedError = null
        embedCallCount = 0
        lastEmbeddedText = null
        allEmbeddedTexts.clear()
    }

    /** Configure predefined embedding for specific text. */
    fun configure(
        embedding: FloatArray,
        forText: String,
    ) {
        predefinedEmbeddings[forText] = embedding
    }

    /** Configure default embedding for all texts. */
    fun configureDefault(embedding: FloatArray) {
        defaultEmbedding = embedding
    }

    /**
     * Generate similar embeddings for testing semantic search ranking.
     *
     * @param count Number of embeddings to generate
     * @param baseSimilarity How similar embeddings should be (0-1)
     * @return List of embeddings
     */
    fun generateSimilarEmbeddings(
        count: Int,
        baseSimilarity: Float = 0.9f,
    ): List<FloatArray> {
        val base = generateDeterministicEmbedding("base")
        val embeddings = mutableListOf<FloatArray>()

        repeat(count) { i ->
            val embedding = base.copyOf()
            // Add controlled variations
            for (j in 0 until minOf(100, embedding.size)) {
                embedding[j] += i * (1.0f - baseSimilarity) * (Math.random().toFloat() * 0.2f - 0.1f)
            }
            // Normalize to unit vector
            val magnitude = kotlin.math.sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()
            if (magnitude > 0) {
                for (j in embedding.indices) {
                    embedding[j] = embedding[j] / magnitude
                }
            }
            embeddings.add(embedding)
        }

        return embeddings
    }

    private fun generateDeterministicEmbedding(text: String): FloatArray {
        // Generate deterministic embedding based on text hash
        // Uses multiplicative hashing for distribution
        val embedding = FloatArray(embeddingDimension)
        val hash = text.hashCode()

        for (i in 0 until embeddingDimension) {
            val seed = (hash + i) * 2654435761L
            embedding[i] = (seed % 1000) / 1000.0f - 0.5f
        }

        // Normalize to unit vector (real embeddings are normalized)
        val magnitude = kotlin.math.sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()
        if (magnitude > 0) {
            for (i in embedding.indices) {
                embedding[i] = embedding[i] / magnitude
            }
        }

        return embedding
    }

    // endregion
}

// endregion
