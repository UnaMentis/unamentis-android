package com.unamentis.modules.knowledgebowl.core.validation

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Token-based similarity matching for Knowledge Bowl answer validation.
 *
 * Handles word order variations, extra words, and missing words by tokenizing
 * answers and computing set-based similarity metrics (Jaccard and Dice coefficients).
 * Filters out common stop words (articles, prepositions, titles) before comparison.
 *
 * Ported from iOS KBTokenMatcher for feature parity. All stop words, tokenization
 * rules, and scoring formulas match the iOS implementation.
 */
@Singleton
class KBTokenMatcher
    @Inject
    constructor() {
        companion object {
            private const val TAG = "KBTokenMatcher"

            /**
             * Common articles, prepositions, and titles to remove during tokenization.
             *
             * Matches the iOS implementation's stop word list exactly.
             */
            val STOP_WORDS: Set<String> =
                setOf(
                    "the", "a", "an", "of", "in", "at", "on", "to", "for", "with", "by", "from",
                    "dr", "mr", "mrs", "ms", "jr", "sr",
                )
        }

        /**
         * Compute Jaccard similarity (intersection / union of tokens).
         *
         * Jaccard = |A intersection B| / |A union B|
         *
         * @param str1 First string
         * @param str2 Second string
         * @return Jaccard similarity (0.0-1.0)
         */
        @Suppress("ReturnCount")
        fun jaccardSimilarity(
            str1: String,
            str2: String,
        ): Float {
            val tokens1 = tokenize(str1)
            val tokens2 = tokenize(str2)

            // If both are empty (all stopwords removed), consider them equal
            if (tokens1.isEmpty() && tokens2.isEmpty()) {
                return 1.0f
            }

            if (tokens1.isEmpty() || tokens2.isEmpty()) {
                return 0.0f
            }

            val intersection = tokens1.intersect(tokens2).size
            val union = tokens1.union(tokens2).size

            if (union == 0) return 0.0f

            return intersection.toFloat() / union.toFloat()
        }

        /**
         * Compute Dice coefficient (2 * intersection / sum of sizes).
         *
         * Dice = 2 * |A intersection B| / (|A| + |B|)
         *
         * The Dice coefficient gives higher weight to shared tokens than Jaccard.
         *
         * @param str1 First string
         * @param str2 Second string
         * @return Dice similarity (0.0-1.0)
         */
        @Suppress("ReturnCount")
        fun diceSimilarity(
            str1: String,
            str2: String,
        ): Float {
            val tokens1 = tokenize(str1)
            val tokens2 = tokenize(str2)

            // If both are empty (all stopwords removed), consider them equal
            if (tokens1.isEmpty() && tokens2.isEmpty()) {
                return 1.0f
            }

            if (tokens1.isEmpty() || tokens2.isEmpty()) {
                return 0.0f
            }

            val intersection = tokens1.intersect(tokens2).size
            val sumOfSizes = tokens1.size + tokens2.size

            if (sumOfSizes == 0) return 0.0f

            return (2 * intersection).toFloat() / sumOfSizes.toFloat()
        }

        /**
         * Combined token score (average of Jaccard + Dice).
         *
         * Provides a balanced similarity score that accounts for both
         * set overlap ratio and shared token density.
         *
         * @param str1 First string
         * @param str2 Second string
         * @return Combined similarity score (0.0-1.0)
         */
        fun tokenScore(
            str1: String,
            str2: String,
        ): Float {
            val jaccard = jaccardSimilarity(str1, str2)
            val dice = diceSimilarity(str1, str2)

            val score = (jaccard + dice) / 2.0f
            Log.d(TAG, "Token score for '$str1' vs '$str2': $score (J=$jaccard, D=$dice)")
            return score
        }

        /**
         * Tokenize string into normalized words, removing stop words.
         *
         * Normalization includes:
         * - Lowercasing
         * - Splitting on whitespace and punctuation
         * - Trimming whitespace
         * - Filtering empty tokens
         * - Removing common stop words (articles, prepositions, titles)
         *
         * @param text Input text
         * @return Set of normalized, non-stop-word tokens
         */
        internal fun tokenize(text: String): Set<String> {
            val normalized = text.lowercase()

            // Split on whitespace
            val rawTokens =
                normalized
                    .split(Regex("\\s+"))
                    .flatMap { word ->
                        // Further split on punctuation
                        word.split(Regex("[.,!?;:'\"()\\-/]"))
                    }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

            // Remove stop words
            val filtered = rawTokens.filter { !STOP_WORDS.contains(it) }

            return filtered.toSet()
        }
    }
