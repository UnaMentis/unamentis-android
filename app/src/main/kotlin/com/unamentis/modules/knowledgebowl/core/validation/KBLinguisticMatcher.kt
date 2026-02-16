package com.unamentis.modules.knowledgebowl.core.validation

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Linguistic matching using stemming and lemmatization for answer comparison.
 *
 * Provides word-level analysis to match answers that differ only in inflection
 * (e.g., "running" vs "run", "countries" vs "country"). Uses a rule-based
 * Porter Stemmer approach since Android does not have a built-in NLP framework
 * like Apple's NaturalLanguage.
 *
 * Ported from iOS KBLinguisticMatcher for feature parity.
 */
@Singleton
class KBLinguisticMatcher
    @Inject
    constructor() {
        companion object {
            private const val TAG = "KBLinguisticMatcher"

            /** Minimum key term overlap ratio for [shareKeyTerms] to return true. */
            const val KEY_TERM_OVERLAP_THRESHOLD = 0.5f
        }

        /**
         * Lemmatize text by reducing words to their base forms using a rule-based stemmer.
         *
         * On Android we use a Porter Stemmer approach since there is no built-in
         * NaturalLanguage framework. This provides comparable behavior to iOS's
         * NLTagger lemmatization for answer validation purposes.
         *
         * @param text Input text to lemmatize
         * @return Text with all words reduced to their stem/base forms
         */
        fun lemmatize(text: String): String {
            val words = text.lowercase().trim().split(Regex("\\s+"))
            return words.joinToString(" ") { stem(it) }
        }

        /**
         * Extract key nouns and verbs for semantic core matching.
         *
         * Filters out stop words, articles, prepositions, and other function words
         * to identify the semantically significant terms in the text.
         *
         * @param text Input text to analyze
         * @return List of key terms (lowercased)
         */
        fun extractKeyTerms(text: String): List<String> {
            val words =
                text
                    .lowercase()
                    .trim()
                    .replace(Regex("[.,!?;:'\"()\\-]"), "")
                    .split(Regex("\\s+"))
                    .filter { it.isNotEmpty() }

            return words.filter { !STOP_WORDS.contains(it) }
        }

        /**
         * Check if two texts share key semantic terms.
         *
         * Computes the Jaccard overlap of extracted key terms and returns true
         * if the overlap meets or exceeds [KEY_TERM_OVERLAP_THRESHOLD] (50%),
         * matching the iOS implementation threshold.
         *
         * @param str1 First string
         * @param str2 Second string
         * @return True if they share significant key terms
         */
        fun shareKeyTerms(
            str1: String,
            str2: String,
        ): Boolean {
            val terms1 = extractKeyTerms(str1).toSet()
            val terms2 = extractKeyTerms(str2).toSet()

            if (terms1.isEmpty() && terms2.isEmpty()) {
                return false
            }

            if (terms1.isEmpty() || terms2.isEmpty()) {
                return false
            }

            val intersection = terms1.intersect(terms2)
            val union = terms1.union(terms2)

            val overlap = intersection.size.toFloat() / union.size.toFloat()
            Log.d(TAG, "Key term overlap: $overlap (terms1=$terms1, terms2=$terms2)")
            return overlap >= KEY_TERM_OVERLAP_THRESHOLD
        }

        /**
         * Check if two strings are equivalent after lemmatization.
         *
         * Both strings are lowercased, trimmed, and lemmatized before comparison.
         *
         * @param str1 First string
         * @param str2 Second string
         * @return True if lemmatized forms match
         */
        fun areLemmasEquivalent(
            str1: String,
            str2: String,
        ): Boolean {
            val lemma1 = lemmatize(str1.lowercase().trim())
            val lemma2 = lemmatize(str2.lowercase().trim())
            return lemma1 == lemma2
        }

        // -- Porter Stemmer Implementation --

        /**
         * Apply Porter Stemmer rules to reduce a word to its stem.
         *
         * This is a simplified implementation targeting common English inflections
         * encountered in Knowledge Bowl answers.
         */
        @Suppress("ReturnCount")
        internal fun stem(word: String): String {
            if (word.length <= 2) return word

            var result = word.lowercase()

            // Step 1a: Plurals
            result = stemStep1a(result)

            // Step 1b: Past tenses / gerunds
            result = stemStep1b(result)

            // Step 1c: Y -> I
            result = stemStep1c(result)

            // Step 2: Suffixes
            result = stemStep2(result)

            // Step 3: More suffixes
            result = stemStep3(result)

            return result
        }

        /**
         * Step 1a: Handle plurals (sses -> ss, ies -> i, ss -> ss, s -> "").
         */
        private fun stemStep1a(word: String): String =
            when {
                word.endsWith("sses") -> word.dropLast(2)
                word.endsWith("ies") -> word.dropLast(2)
                word.endsWith("ss") -> word
                word.endsWith("s") -> word.dropLast(1)
                else -> word
            }

        /**
         * Step 1b: Handle past tenses and gerunds (-eed, -ed, -ing).
         */
        private fun stemStep1b(word: String): String {
            if (word.endsWith("eed")) {
                return if (measureGreaterThan(word.dropLast(3), 0)) {
                    word.dropLast(1)
                } else {
                    word
                }
            }

            val stem =
                when {
                    word.endsWith("ed") && containsVowel(word.dropLast(2)) ->
                        word.dropLast(2)
                    word.endsWith("ing") && containsVowel(word.dropLast(3)) ->
                        word.dropLast(3)
                    else -> return word
                }

            // Post-processing after removing -ed/-ing
            return when {
                stem.endsWith("at") || stem.endsWith("bl") || stem.endsWith("iz") ->
                    stem + "e"
                stem.length >= 2 && isDoubleConsonant(stem) &&
                    stem.last() !in listOf('l', 's', 'z') ->
                    stem.dropLast(1)
                measureEquals(stem, 1) && isCVC(stem) ->
                    stem + "e"
                else -> stem
            }
        }

        /**
         * Step 1c: Replace trailing Y with I if stem contains a vowel.
         */
        private fun stemStep1c(word: String): String =
            if (word.endsWith("y") && containsVowel(word.dropLast(1))) {
                word.dropLast(1) + "i"
            } else {
                word
            }

        /**
         * Step 2: Map common double suffixes to simpler forms.
         */
        @Suppress("CyclomaticComplexity")
        private fun stemStep2(word: String): String {
            val suffixMap =
                listOf(
                    "ational" to "ate",
                    "tional" to "tion",
                    "enci" to "ence",
                    "anci" to "ance",
                    "izer" to "ize",
                    "abli" to "able",
                    "alli" to "al",
                    "entli" to "ent",
                    "eli" to "e",
                    "ousli" to "ous",
                    "ization" to "ize",
                    "ation" to "ate",
                    "ator" to "ate",
                    "alism" to "al",
                    "iveness" to "ive",
                    "fulness" to "ful",
                    "ousness" to "ous",
                    "aliti" to "al",
                    "iviti" to "ive",
                    "biliti" to "ble",
                )

            for ((suffix, replacement) in suffixMap) {
                if (word.endsWith(suffix)) {
                    val stem = word.dropLast(suffix.length)
                    return if (measureGreaterThan(stem, 0)) {
                        stem + replacement
                    } else {
                        word
                    }
                }
            }
            return word
        }

        /**
         * Step 3: Handle more suffixes (-icate, -ative, -alize, -iciti, -ful, -ness).
         */
        private fun stemStep3(word: String): String {
            val suffixMap =
                listOf(
                    "icate" to "ic",
                    "ative" to "",
                    "alize" to "al",
                    "iciti" to "ic",
                    "ical" to "ic",
                    "ful" to "",
                    "ness" to "",
                )

            for ((suffix, replacement) in suffixMap) {
                if (word.endsWith(suffix)) {
                    val stem = word.dropLast(suffix.length)
                    return if (measureGreaterThan(stem, 0)) {
                        stem + replacement
                    } else {
                        word
                    }
                }
            }
            return word
        }

        // -- Helper functions for Porter Stemmer --

        /**
         * Check if a character is a vowel (a, e, i, o, u; y when not at start).
         */
        private fun isVowel(
            word: String,
            index: Int,
        ): Boolean {
            if (index < 0 || index >= word.length) return false
            val ch = word[index]
            return when (ch) {
                'a', 'e', 'i', 'o', 'u' -> true
                'y' -> index > 0 && !isVowel(word, index - 1)
                else -> false
            }
        }

        /** Check if the word contains at least one vowel. */
        private fun containsVowel(word: String): Boolean = word.indices.any { isVowel(word, it) }

        /**
         * Compute the "measure" m of a word: the number of VC (vowel-consonant) sequences.
         * e.g., "tree" has m=0, "trouble" has m=1, "troubles" has m=1.
         */
        private fun measure(word: String): Int {
            if (word.isEmpty()) return 0
            var m = 0
            var i = 0
            val n = word.length

            // Skip leading consonants
            while (i < n && !isVowel(word, i)) i++

            while (i < n) {
                // Skip vowels
                while (i < n && isVowel(word, i)) i++
                if (i >= n) break
                // Skip consonants
                m++
                while (i < n && !isVowel(word, i)) i++
            }
            return m
        }

        /** Check if measure of word is greater than threshold. */
        private fun measureGreaterThan(
            word: String,
            threshold: Int,
        ): Boolean = measure(word) > threshold

        /** Check if measure of word equals value. */
        private fun measureEquals(
            word: String,
            value: Int,
        ): Boolean = measure(word) == value

        /** Check if the last two characters are the same consonant. */
        private fun isDoubleConsonant(word: String): Boolean {
            if (word.length < 2) return false
            return word[word.length - 1] == word[word.length - 2] &&
                !isVowel(word, word.length - 1)
        }

        /**
         * Check if the word ends with consonant-vowel-consonant pattern
         * where the last consonant is not w, x, or y.
         */
        private fun isCVC(word: String): Boolean {
            if (word.length < 3) return false
            val n = word.length
            return !isVowel(word, n - 1) &&
                isVowel(word, n - 2) &&
                !isVowel(word, n - 3) &&
                word[n - 1] !in listOf('w', 'x', 'y')
        }
    }

/**
 * Common English stop words to filter out during key term extraction.
 *
 * Includes articles, prepositions, conjunctions, and common function words.
 */
private val STOP_WORDS =
    setOf(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "as", "is", "was", "are", "were", "be",
        "been", "being", "have", "has", "had", "do", "does", "did", "will",
        "would", "shall", "should", "may", "might", "can", "could", "not",
        "this", "that", "these", "those", "it", "its", "he", "she", "they",
        "them", "his", "her", "their", "my", "your", "our", "what", "which",
        "who", "whom", "how", "when", "where", "why", "if", "then", "than",
        "so", "no", "yes", "up", "out", "about", "into", "over", "after",
        "before", "between", "under", "very", "just", "also",
    )
