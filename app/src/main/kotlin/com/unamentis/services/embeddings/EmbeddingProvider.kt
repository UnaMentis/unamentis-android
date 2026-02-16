package com.unamentis.services.embeddings

import kotlin.math.sqrt

/**
 * Model options for OpenAI embedding generation.
 *
 * Each model produces embeddings of a specific dimension, which determines
 * the vector space size for similarity calculations.
 *
 * @property modelId The API model identifier string
 * @property dimension The number of dimensions in the output embedding vector
 * @property costPerToken The cost in USD per token for this model
 */
enum class EmbeddingModel(
    val modelId: String,
    val dimension: Int,
    val costPerToken: Double,
) {
    /** OpenAI text-embedding-3-small: 1536 dimensions, lower cost */
    TEXT_EMBEDDING_3_SMALL(
        modelId = "text-embedding-3-small",
        dimension = 1536,
        costPerToken = 0.00000002,
    ),

    /** OpenAI text-embedding-ada-002 (legacy): 1536 dimensions */
    TEXT_EMBEDDING_ADA_002(
        modelId = "text-embedding-ada-002",
        dimension = 1536,
        costPerToken = 0.0000001,
    ),
}

/**
 * Result of an embedding generation request.
 *
 * Contains the generated embedding vector along with metadata about
 * token usage for cost tracking.
 *
 * @property embedding The generated embedding vector as a list of floats
 * @property tokensUsed The number of tokens consumed by this request
 * @property model The model used to generate the embedding
 */
data class EmbeddingResult(
    val embedding: List<Float>,
    val tokensUsed: Int,
    val model: EmbeddingModel,
)

/**
 * Result of a batch embedding generation request.
 *
 * Contains multiple embeddings generated from a list of input texts,
 * along with aggregated token usage.
 *
 * @property embeddings The list of generated embedding vectors, in the same order as inputs
 * @property totalTokensUsed The total number of tokens consumed across all inputs
 * @property model The model used to generate the embeddings
 */
data class BatchEmbeddingResult(
    val embeddings: List<List<Float>>,
    val totalTokensUsed: Int,
    val model: EmbeddingModel,
)

/**
 * Errors that can occur during embedding generation.
 *
 * Provides structured error types for different failure modes,
 * enabling callers to handle each case appropriately.
 */
sealed class EmbeddingError : Exception() {
    /** The API key is missing or invalid. */
    data object InvalidApiKey : EmbeddingError()

    /** The API rate limit has been exceeded. Retry after the specified delay. */
    data class RateLimited(val retryAfterSeconds: Int?) : EmbeddingError()

    /** The input text exceeds the model's maximum token limit. */
    data class InputTooLong(val maxTokens: Int) : EmbeddingError()

    /** A network error occurred during the request. */
    data class NetworkError(override val message: String) : EmbeddingError()

    /** The API returned an unexpected error. */
    data class ApiError(val statusCode: Int, override val message: String) : EmbeddingError()
}

/**
 * Interface for embedding generation services.
 *
 * Embedding providers generate vector representations of text that can be used
 * for semantic search, similarity comparison, and knowledge base answer validation.
 *
 * Implementations should handle:
 * - Text preprocessing (newline replacement, truncation)
 * - API authentication and error handling
 * - Cost tracking via [com.unamentis.core.telemetry.TelemetryEngine]
 *
 * Usage:
 * ```kotlin
 * val result = embeddingProvider.embed("What is photosynthesis?")
 * val similarity = cosineSimilarity(result.embedding, storedEmbedding)
 * ```
 */
interface EmbeddingProvider {
    /** The name of this embedding provider (e.g., "OpenAI"). */
    val providerName: String

    /** The dimension of embeddings produced by the current model. */
    val embeddingDimension: Int

    /**
     * Generate an embedding for a single text input.
     *
     * @param text The text to generate an embedding for
     * @return The embedding result containing the vector and usage metadata
     * @throws EmbeddingError if the request fails
     */
    suspend fun embed(text: String): EmbeddingResult

    /**
     * Generate embeddings for multiple text inputs in a single request.
     *
     * Batch requests are more efficient than individual requests when
     * embedding multiple texts, as they reduce API overhead.
     *
     * @param texts The list of texts to generate embeddings for
     * @return The batch embedding result containing all vectors and usage metadata
     * @throws EmbeddingError if the request fails
     */
    suspend fun embedBatch(texts: List<String>): BatchEmbeddingResult
}

/**
 * Calculate cosine similarity between two embedding vectors.
 *
 * Cosine similarity measures the cosine of the angle between two vectors,
 * producing a value between -1 (opposite) and 1 (identical).
 * A value of 0 indicates orthogonal (unrelated) vectors.
 *
 * @param a First embedding vector
 * @param b Second embedding vector
 * @return Similarity score between -1.0 and 1.0, or 0.0 if vectors are
 *         empty or have different dimensions
 */
fun cosineSimilarity(
    a: List<Float>,
    b: List<Float>,
): Float {
    if (a.size != b.size || a.isEmpty()) return 0f

    var dotProduct = 0f
    var magnitudeA = 0f
    var magnitudeB = 0f

    for (i in a.indices) {
        dotProduct += a[i] * b[i]
        magnitudeA += a[i] * a[i]
        magnitudeB += b[i] * b[i]
    }

    magnitudeA = sqrt(magnitudeA)
    magnitudeB = sqrt(magnitudeB)

    if (magnitudeA == 0f || magnitudeB == 0f) return 0f

    return dotProduct / (magnitudeA * magnitudeB)
}
