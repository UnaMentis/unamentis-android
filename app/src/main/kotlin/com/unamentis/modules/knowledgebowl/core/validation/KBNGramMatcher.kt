package com.unamentis.modules.knowledgebowl.core.validation

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * N-gram similarity matching for Knowledge Bowl answer validation.
 *
 * Uses character-level and word-level n-grams with Dice coefficient to handle
 * transpositions, missing characters, and spelling variations. Provides a
 * combined score using weighted bigram, trigram, and word-level similarity.
 *
 * Ported from iOS KBNGramMatcher for feature parity. All thresholds and weights
 * match the iOS implementation exactly.
 */
@Singleton
class KBNGramMatcher
    @Inject
    constructor() {
        companion object {
            private const val TAG = "KBNGramMatcher"

            // Weights for single-word combined score
            /** Weight for character unigram similarity in single-word scoring. */
            const val SINGLE_WORD_UNIGRAM_WEIGHT = 0.3f

            /** Weight for character bigram similarity in single-word scoring. */
            const val SINGLE_WORD_BIGRAM_WEIGHT = 0.35f

            /** Weight for character trigram similarity in single-word scoring. */
            const val SINGLE_WORD_TRIGRAM_WEIGHT = 0.35f

            // Weights for multi-word combined score
            /** Weight for character unigram similarity in multi-word scoring. */
            const val MULTI_WORD_UNIGRAM_WEIGHT = 0.2f

            /** Weight for character bigram similarity in multi-word scoring. */
            const val MULTI_WORD_BIGRAM_WEIGHT = 0.25f

            /** Weight for character trigram similarity in multi-word scoring. */
            const val MULTI_WORD_TRIGRAM_WEIGHT = 0.25f

            /** Weight for word-level similarity in multi-word scoring. */
            const val MULTI_WORD_WORD_WEIGHT = 0.3f
        }

        /**
         * Compute character n-gram similarity between two strings using Dice coefficient with padding.
         *
         * Pads strings with boundary markers (#) to improve matching of string starts and ends.
         *
         * @param str1 First string
         * @param str2 Second string
         * @param n N-gram size (2 for bigrams, 3 for trigrams)
         * @return Similarity score (0.0-1.0)
         */
        fun characterNGramSimilarity(
            str1: String,
            str2: String,
            n: Int,
        ): Float {
            val padding = "#".repeat(n - 1)
            val padded1 = padding + str1.lowercase() + padding
            val padded2 = padding + str2.lowercase() + padding

            val ngrams1 = characterNGrams(padded1, n)
            val ngrams2 = characterNGrams(padded2, n)

            if (ngrams1.isEmpty() && ngrams2.isEmpty()) {
                return if (str1 == str2) 1.0f else 0.0f
            }
            if (ngrams1.isEmpty() || ngrams2.isEmpty()) {
                return if (str1 == str2) 1.0f else 0.0f
            }

            return diceMultisetSimilarity(ngrams1, ngrams2)
        }

        /**
         * Compute word n-gram similarity for multi-word answers using Dice coefficient.
         *
         * @param str1 First string
         * @param str2 Second string
         * @param n N-gram size (typically 2 for word bigrams)
         * @return Similarity score (0.0-1.0)
         */
        fun wordNGramSimilarity(
            str1: String,
            str2: String,
            n: Int,
        ): Float {
            val normalized1 = str1.lowercase()
            val normalized2 = str2.lowercase()

            val ngrams1 = wordNGrams(normalized1, n)
            val ngrams2 = wordNGrams(normalized2, n)

            if (ngrams1.isEmpty() && ngrams2.isEmpty()) {
                return if (str1 == str2) 1.0f else 0.0f
            }
            if (ngrams1.isEmpty() || ngrams2.isEmpty()) {
                return if (str1 == str2) 1.0f else 0.0f
            }

            return diceMultisetSimilarity(ngrams1, ngrams2)
        }

        /**
         * Combined n-gram score using multiple similarity measures.
         *
         * For single-word inputs, uses weighted character unigrams (0.3), bigrams (0.35),
         * and trigrams (0.35). For multi-word inputs, adds a word-level similarity (0.3)
         * while adjusting other weights (0.2, 0.25, 0.25).
         *
         * All weights match the iOS implementation exactly.
         *
         * @param str1 First string
         * @param str2 Second string
         * @return Weighted similarity score (0.0-1.0)
         */
        fun nGramScore(
            str1: String,
            str2: String,
        ): Float {
            val normalized1 = str1.lowercase().trim()
            val normalized2 = str2.lowercase().trim()

            // Exact match check
            if (normalized1 == normalized2) return 1.0f

            // Character unigrams (shared character ratio) - very forgiving
            val unigramScore = characterUnigramSimilarity(normalized1, normalized2)

            // Character bigrams with Dice coefficient
            val bigramScore = characterNGramSimilarity(normalized1, normalized2, n = 2)

            // Character trigrams with Dice coefficient
            val trigramScore = characterNGramSimilarity(normalized1, normalized2, n = 3)

            // For multi-word strings, check word-level similarity
            val isMultiWord = normalized1.contains(" ") || normalized2.contains(" ")

            val score =
                if (isMultiWord) {
                    val wordScore = wordBySimilarity(normalized1, normalized2)
                    (unigramScore * MULTI_WORD_UNIGRAM_WEIGHT) +
                        (bigramScore * MULTI_WORD_BIGRAM_WEIGHT) +
                        (trigramScore * MULTI_WORD_TRIGRAM_WEIGHT) +
                        (wordScore * MULTI_WORD_WORD_WEIGHT)
                } else {
                    (unigramScore * SINGLE_WORD_UNIGRAM_WEIGHT) +
                        (bigramScore * SINGLE_WORD_BIGRAM_WEIGHT) +
                        (trigramScore * SINGLE_WORD_TRIGRAM_WEIGHT)
                }

            Log.d(TAG, "N-gram score for '$normalized1' vs '$normalized2': $score")
            return score
        }

        // -- Private Helpers --

        /**
         * Compute character unigram (single character) similarity using Dice coefficient.
         */
        internal fun characterUnigramSimilarity(
            str1: String,
            str2: String,
        ): Float {
            val chars1 = str1.filter { !it.isWhitespace() }.toList()
            val chars2 = str2.filter { !it.isWhitespace() }.toList()

            if (chars1.isEmpty() && chars2.isEmpty()) return 1.0f
            if (chars1.isEmpty() || chars2.isEmpty()) return 0.0f

            // Count character frequencies
            val freq1 = mutableMapOf<Char, Int>()
            val freq2 = mutableMapOf<Char, Int>()

            for (ch in chars1) freq1[ch] = (freq1[ch] ?: 0) + 1
            for (ch in chars2) freq2[ch] = (freq2[ch] ?: 0) + 1

            // Intersection count (min of frequencies)
            var intersection = 0
            for ((ch, count1) in freq1) {
                val count2 = freq2[ch]
                if (count2 != null) {
                    intersection += minOf(count1, count2)
                }
            }

            // Dice coefficient for character multisets
            return (2 * intersection).toFloat() / (chars1.size + chars2.size).toFloat()
        }

        /**
         * Compute word-by-word similarity for multi-word strings.
         *
         * For each word in the first string, finds the best matching word in the
         * second string using character bigram similarity, then averages the scores.
         */
        private fun wordBySimilarity(
            str1: String,
            str2: String,
        ): Float {
            val words1 = str1.split(" ").filter { it.isNotEmpty() }
            val words2 = str2.split(" ").filter { it.isNotEmpty() }

            if (words1.isEmpty() || words2.isEmpty()) return 0.0f

            var totalScore = 0.0f

            for (word1 in words1) {
                var bestMatch = 0.0f
                for (word2 in words2) {
                    val sim = characterNGramSimilarity(word1, word2, n = 2)
                    bestMatch = maxOf(bestMatch, sim)
                }
                totalScore += bestMatch
            }

            return totalScore / words1.size.toFloat()
        }

        /**
         * Generate character n-grams from a string.
         */
        internal fun characterNGrams(
            text: String,
            n: Int,
        ): List<String> {
            if (text.length < n) return listOf(text)

            val ngrams = mutableListOf<String>()
            for (i in 0..(text.length - n)) {
                ngrams.add(text.substring(i, i + n))
            }
            return ngrams
        }

        /**
         * Generate word n-grams from a string.
         */
        internal fun wordNGrams(
            text: String,
            n: Int,
        ): List<String> {
            val words = text.split(" ").filter { it.isNotEmpty() }
            if (words.size < n) return listOf(text)

            val ngrams = mutableListOf<String>()
            for (i in 0..(words.size - n)) {
                ngrams.add(words.subList(i, i + n).joinToString(" "))
            }
            return ngrams
        }

        /**
         * Compute Dice coefficient similarity for multisets (lists with duplicates).
         *
         * Dice = 2 * |intersection| / (|A| + |B|)
         */
        internal fun diceMultisetSimilarity(
            arr1: List<String>,
            arr2: List<String>,
        ): Float {
            if (arr1.isEmpty() && arr2.isEmpty()) return 1.0f
            if (arr1.isEmpty() || arr2.isEmpty()) return 0.0f

            val counts1 = mutableMapOf<String, Int>()
            val counts2 = mutableMapOf<String, Int>()

            for (item in arr1) counts1[item] = (counts1[item] ?: 0) + 1
            for (item in arr2) counts2[item] = (counts2[item] ?: 0) + 1

            var intersectionCount = 0
            for ((key, count1) in counts1) {
                val count2 = counts2[key]
                if (count2 != null) {
                    intersectionCount += minOf(count1, count2)
                }
            }

            val totalCount = arr1.size + arr2.size
            return (2 * intersectionCount).toFloat() / totalCount.toFloat()
        }
    }
