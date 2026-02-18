package com.unamentis.modules.knowledgebowl.core.ml

import android.util.Log
import com.unamentis.data.model.LLMMessage
import com.unamentis.data.model.LLMService
import kotlinx.coroutines.flow.fold
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LLM-based answer validation service for Knowledge Bowl.
 *
 * Provides Tier 3 validation for complex answers that cannot be
 * adequately evaluated by exact match, fuzzy match, or embedding
 * similarity alone. Sends the question, correct answer, and user
 * answer to an LLM and asks it to judge correctness.
 *
 * The LLM is prompted to respond with exactly "CORRECT" or "INCORRECT",
 * enabling deterministic parsing of the result.
 *
 * @property llmService The LLM service used for completion requests
 */
@Singleton
class KBLLMValidator
    @Inject
    constructor(
        private val llmService: LLMService,
    ) {
        /** Maximum tokens for the LLM response. */
        private val maxTokens = 32

        /** Low temperature for deterministic responses. */
        private val temperature = 0.1f

        /**
         * Validate whether a user's answer is correct using LLM judgment.
         *
         * Constructs a prompt containing the question, correct answer, and
         * user answer, then asks the LLM to judge equivalence. Supports
         * optional answer type context and validation guidance.
         *
         * @param userAnswer The answer provided by the user
         * @param correctAnswer The expected correct answer
         * @param question The question that was asked
         * @param answerType The type of answer expected (e.g., "text", "number", "date")
         * @param guidance Optional additional guidance for the LLM validator
         * @return `true` if the LLM judges the answer as correct, `false` otherwise
         *         or if an error occurs
         */
        suspend fun validate(
            userAnswer: String,
            correctAnswer: String,
            question: String,
            answerType: String = "text",
            guidance: String? = null,
        ): Boolean =
            try {
                val prompt = buildPrompt(question, correctAnswer, userAnswer, answerType, guidance)
                val messages =
                    listOf(
                        LLMMessage(role = "system", content = SYSTEM_MESSAGE),
                        LLMMessage(role = "user", content = prompt),
                    )

                val response =
                    llmService
                        .streamCompletion(
                            messages = messages,
                            temperature = temperature,
                            maxTokens = maxTokens,
                        ).fold(StringBuilder()) { builder, token ->
                            builder.append(token.content)
                        }.toString()

                val isCorrect = parseResponse(response)
                Log.d(
                    TAG,
                    "LLM validation: userAnswer=\"$userAnswer\", " +
                        "correctAnswer=\"$correctAnswer\", " +
                        "response=\"$response\", isCorrect=$isCorrect",
                )
                isCorrect
            } catch (e: Exception) {
                Log.e(TAG, "LLM validation failed", e)
                false
            }

        /**
         * Build the validation prompt for the LLM.
         *
         * Includes rules for semantic equivalence, abbreviation handling,
         * factual accuracy, answer type context, and optional guidance.
         *
         * @param question The question that was asked
         * @param correctAnswer The expected correct answer
         * @param userAnswer The answer provided by the user
         * @param answerType The type of answer (for context)
         * @param guidance Optional additional validation guidance
         * @return The formatted prompt string
         */
        private fun buildPrompt(
            question: String,
            correctAnswer: String,
            userAnswer: String,
            answerType: String,
            guidance: String?,
        ): String =
            buildString {
                appendLine("Judge whether the user's answer is correct.")
                appendLine()
                appendLine("Question: $question")
                appendLine("Correct Answer: $correctAnswer")
                appendLine("User's Answer: $userAnswer")
                appendLine("Answer Type: $answerType")
                appendLine()
                appendLine("Rules:")
                appendLine("- Accept semantic equivalence (same meaning, different wording)")
                appendLine("- Accept common abbreviations and alternate forms")
                appendLine("- Require factual accuracy (the core fact must be correct)")
                appendLine("- Consider the answer type when judging (e.g., numbers must be numerically equivalent)")
                if (guidance != null) {
                    appendLine("- Additional guidance: $guidance")
                }
                appendLine()
                append("Respond with exactly one word: CORRECT or INCORRECT")
            }

        /**
         * Parse the LLM response to determine if the answer was judged correct.
         *
         * Checks if the trimmed response starts with "correct" (case-insensitive).
         *
         * @param response The raw LLM response string
         * @return `true` if the response indicates correctness
         */
        private fun parseResponse(response: String): Boolean = response.trim().lowercase().startsWith("correct")

        companion object {
            private const val TAG = "KBLLMValidator"
            private const val SYSTEM_MESSAGE =
                "You are an answer validation judge. " +
                    "Respond with exactly one word: CORRECT or INCORRECT."
        }
    }
