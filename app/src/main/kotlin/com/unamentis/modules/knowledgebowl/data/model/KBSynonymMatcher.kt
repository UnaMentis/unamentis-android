package com.unamentis.modules.knowledgebowl.data.model

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Matcher for domain-specific synonyms used during answer validation.
 *
 * Uses [KBSynonymDictionaries] to find equivalent terms for a given
 * answer type. For example, "H2O" and "water" are synonyms for
 * scientific answer types.
 *
 * This class is thread-safe; all methods are pure functions operating
 * on immutable dictionaries.
 */
@Singleton
class KBSynonymMatcher
    @Inject
    constructor() {
        /**
         * Find all synonyms for a given text in a specific domain.
         *
         * Searches both as a dictionary key and as a value within
         * any dictionary entry, returning the full synonym set including
         * the original text.
         *
         * @param text Input text to find synonyms for
         * @param answerType Answer type to determine which dictionary to use
         * @return Set of synonyms (always includes the normalized original text)
         */
        @Suppress("ReturnCount")
        fun findSynonyms(
            text: String,
            answerType: KBAnswerType,
        ): Set<String> {
            val normalized = text.lowercase().trim()

            val dictionary =
                KBSynonymDictionaries.dictionaryForType(answerType)
                    ?: return setOf(normalized)

            // Check if text is a dictionary key
            dictionary[normalized]?.let { synonyms ->
                return synonyms + normalized
            }

            // Check if text is a synonym value of any key
            for ((key, synonyms) in dictionary) {
                if (normalized in synonyms) {
                    return synonyms + key + normalized
                }
            }

            // No synonyms found
            return setOf(normalized)
        }

        /**
         * Check if two strings are synonyms for a given answer type.
         *
         * Returns true if:
         * - The strings match exactly (after normalization)
         * - They share at least one common synonym in the relevant dictionary
         *
         * @param str1 First string
         * @param str2 Second string
         * @param answerType Answer type to determine which dictionary to use
         * @return True if the strings are synonyms or identical
         */
        fun areSynonyms(
            str1: String,
            str2: String,
            answerType: KBAnswerType,
        ): Boolean {
            val normalized1 = str1.lowercase().trim()
            val normalized2 = str2.lowercase().trim()

            // Exact match
            if (normalized1 == normalized2) {
                return true
            }

            // Get synonym sets and check for overlap
            val synonyms1 = findSynonyms(normalized1, answerType)
            val synonyms2 = findSynonyms(normalized2, answerType)

            return synonyms1.any { it in synonyms2 }
        }
    }
