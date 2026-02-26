package com.unamentis.modules.knowledgebowl.core.ml

import android.util.Log
import com.unamentis.services.embeddings.OpenAIEmbeddingService
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Knowledge Bowl embeddings service for semantic answer comparison.
 *
 * Wraps [OpenAIEmbeddingService] to provide convenient similarity
 * operations for answer validation. Used as Tier 2 validation when
 * exact and fuzzy matching are insufficient.
 *
 * Features:
 * - Pairwise cosine similarity between two texts
 * - Batch similarity comparing a reference against multiple candidates
 * - Graceful error handling (returns 0.0 on failure)
 *
 * @property embeddingService The underlying OpenAI embedding service
 */
@Singleton
class KBEmbeddingsService
    @Inject
    constructor(
        private val embeddingService: OpenAIEmbeddingService,
    ) {
        /**
         * Compute cosine similarity between two texts.
         *
         * Embeds both texts and returns their cosine similarity,
         * mapped to the 0.0-1.0 range.
         *
         * @param text1 First text to compare
         * @param text2 Second text to compare
         * @return Similarity score between 0.0 (unrelated) and 1.0 (identical),
         *         or 0.0 if an error occurs
         */
        suspend fun similarity(
            text1: String,
            text2: String,
        ): Float =
            try {
                val result = embeddingService.embedBatch(listOf(text1, text2))
                val embeddings = result.embeddings
                if (embeddings.size >= 2) {
                    cosineSimilarity(embeddings[0], embeddings[1])
                } else {
                    0.0f
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to compute similarity", e)
                0.0f
            }

        /**
         * Get the embedding vector for a text.
         *
         * @param text The text to embed
         * @return The embedding vector as a list of floats
         * @throws Exception if the embedding request fails
         */
        suspend fun embed(text: String): List<Float> {
            val result = embeddingService.embed(text)
            return result.embedding
        }

        /**
         * Compare a reference text against multiple candidate texts.
         *
         * Efficiently embeds the reference and all candidates in a single
         * batch request, then computes cosine similarity for each pair.
         *
         * @param reference The reference text to compare against
         * @param candidates The list of candidate texts
         * @return List of similarity scores (0.0-1.0), one per candidate,
         *         or a list of 0.0 values if an error occurs
         */
        suspend fun batchSimilarity(
            reference: String,
            candidates: List<String>,
        ): List<Float> {
            if (candidates.isEmpty()) {
                return emptyList()
            }

            return try {
                val allTexts = listOf(reference) + candidates
                val result = embeddingService.embedBatch(allTexts)
                val embeddings = result.embeddings

                val referenceEmbedding = embeddings[0]
                embeddings.drop(1).map { candidateEmbedding ->
                    cosineSimilarity(referenceEmbedding, candidateEmbedding)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to compute batch similarity", e)
                List(candidates.size) { 0.0f }
            }
        }

        /**
         * Compute cosine similarity between two embedding vectors.
         *
         * Calculates the normalized dot product of the two vectors,
         * mapped from the raw [-1, 1] range to [0, 1] for easier
         * threshold comparison.
         *
         * @param a First embedding vector
         * @param b Second embedding vector
         * @return Similarity score between 0.0 and 1.0
         */
        private fun cosineSimilarity(
            a: List<Float>,
            b: List<Float>,
        ): Float {
            if (a.size != b.size || a.isEmpty()) return 0.0f

            var dotProduct = 0.0f
            var magnitudeA = 0.0f
            var magnitudeB = 0.0f

            for (i in a.indices) {
                dotProduct += a[i] * b[i]
                magnitudeA += a[i] * a[i]
                magnitudeB += b[i] * b[i]
            }

            magnitudeA = sqrt(magnitudeA)
            magnitudeB = sqrt(magnitudeB)

            if (magnitudeA == 0.0f || magnitudeB == 0.0f) return 0.0f

            // Map from [-1, 1] to [0, 1]
            val rawSimilarity = dotProduct / (magnitudeA * magnitudeB)
            return ((rawSimilarity + 1.0f) / 2.0f).coerceIn(0.0f, 1.0f)
        }

        companion object {
            private const val TAG = "KBEmbeddingsService"
        }
    }
