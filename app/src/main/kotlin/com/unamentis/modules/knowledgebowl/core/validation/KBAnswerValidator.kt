package com.unamentis.modules.knowledgebowl.core.validation

import android.util.Log
import com.unamentis.modules.knowledgebowl.data.model.KBAnswer
import com.unamentis.modules.knowledgebowl.data.model.KBAnswerType
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates user answers against correct answers using various matching strategies.
 *
 * Validation pipeline:
 * 1. Exact match against primary answer
 * 2. Exact match against acceptable alternatives
 * 3. Fuzzy matching (if not in strict mode)
 *
 * Each step uses type-specific normalization for the answer type.
 */
@Singleton
class KBAnswerValidator
    @Inject
    constructor() {
        companion object {
            private const val TAG = "KBAnswerValidator"
        }

        /**
         * Configuration for answer validation.
         *
         * @property fuzzyThresholdPercent Maximum Levenshtein distance as percentage of answer length
         * @property minimumConfidence Minimum confidence for a fuzzy match to be accepted
         * @property strictMode Whether to use strict mode (exact match only)
         */
        data class Config(
            val fuzzyThresholdPercent: Double = 0.20,
            val minimumConfidence: Float = 0.6f,
            val strictMode: Boolean = false,
        ) {
            companion object {
                val STANDARD = Config()
                val STRICT = Config(strictMode = true)
                val LENIENT = Config(fuzzyThresholdPercent = 0.30, minimumConfidence = 0.5f)
            }
        }

        private var config: Config = Config.STANDARD

        /**
         * Set the validation configuration.
         */
        fun setConfig(config: Config) {
            this.config = config
        }

        /**
         * Get the current validation configuration.
         */
        fun getConfig(): Config = config

        /**
         * Validate a user's answer against the question's correct answer.
         *
         * @param userAnswer The user's submitted answer
         * @param question The question being answered
         * @return Validation result with correctness, confidence, and match type
         */
        @Suppress("ReturnCount")
        fun validate(
            userAnswer: String,
            question: KBQuestion,
        ): KBValidationResult {
            val answer = question.answer

            // Normalize the user answer
            val normalizedUser = AnswerNormalizer.normalize(userAnswer, answer.answerType)

            // 1. Check exact primary match
            val normalizedPrimary = AnswerNormalizer.normalize(answer.primary, answer.answerType)
            if (normalizedUser == normalizedPrimary) {
                Log.d(TAG, "Exact match for: $userAnswer")
                return KBValidationResult.exactMatch(answer.primary)
            }

            // 2. Check acceptable alternatives
            answer.acceptable?.forEach { alt ->
                val normalizedAlt = AnswerNormalizer.normalize(alt, answer.answerType)
                if (normalizedUser == normalizedAlt) {
                    Log.d(TAG, "Acceptable match for: $userAnswer -> $alt")
                    return KBValidationResult.acceptableMatch(alt)
                }
            }

            // 3. Fuzzy matching (if not in strict mode)
            if (!config.strictMode) {
                val fuzzyResult = fuzzyMatch(normalizedUser, answer)
                if (fuzzyResult.isCorrect) {
                    return fuzzyResult
                }
            }

            // No match found
            Log.d(TAG, "No match for: $userAnswer")
            return KBValidationResult.noMatch()
        }

        /**
         * Validate an MCQ selection.
         *
         * @param selectedIndex The index of the selected option (0-based)
         * @param question The question being answered
         * @return Validation result
         */
        fun validateMCQ(
            selectedIndex: Int,
            question: KBQuestion,
        ): KBValidationResult {
            val options = question.mcqOptions
            if (options == null || selectedIndex < 0 || selectedIndex >= options.size) {
                return KBValidationResult.noMatch()
            }

            val selectedOption = options[selectedIndex]
            val isCorrect =
                AnswerNormalizer.normalize(selectedOption, KBAnswerType.TEXT) ==
                    AnswerNormalizer.normalize(question.answer.primary, KBAnswerType.TEXT)

            return if (isCorrect) {
                KBValidationResult.exactMatch(selectedOption)
            } else {
                KBValidationResult.noMatch()
            }
        }

        /**
         * Perform fuzzy matching against the answer.
         */
        @Suppress("ReturnCount")
        private fun fuzzyMatch(
            normalizedUserAnswer: String,
            answer: KBAnswer,
        ): KBValidationResult {
            val threshold = maxOf(2, (answer.primary.length * config.fuzzyThresholdPercent).toInt())

            // Check against primary answer
            val normalizedPrimary = AnswerNormalizer.normalize(answer.primary, answer.answerType)
            val primaryDistance = LevenshteinDistance.calculate(normalizedUserAnswer, normalizedPrimary)

            if (primaryDistance <= threshold) {
                val confidence = 1.0f - (primaryDistance.toFloat() / maxOf(1, answer.primary.length))
                if (confidence >= config.minimumConfidence) {
                    Log.d(TAG, "Fuzzy match (distance $primaryDistance): $normalizedUserAnswer -> ${answer.primary}")
                    return KBValidationResult.fuzzyMatch(answer.primary, confidence)
                }
            }

            // Check against acceptable alternatives
            answer.acceptable?.forEach { alt ->
                val normalizedAlt = AnswerNormalizer.normalize(alt, answer.answerType)
                val altDistance = LevenshteinDistance.calculate(normalizedUserAnswer, normalizedAlt)
                val altThreshold = maxOf(2, (alt.length * config.fuzzyThresholdPercent).toInt())

                if (altDistance <= altThreshold) {
                    val confidence = 1.0f - (altDistance.toFloat() / maxOf(1, alt.length))
                    if (confidence >= config.minimumConfidence) {
                        Log.d(TAG, "Fuzzy match (distance $altDistance): $normalizedUserAnswer -> $alt")
                        return KBValidationResult.fuzzyMatch(alt, confidence)
                    }
                }
            }

            return KBValidationResult.noMatch()
        }
    }
