package com.unamentis.core.fov

import android.util.Log
import com.unamentis.core.fov.buffer.ExpansionScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Monitor for analyzing LLM responses for uncertainty and determining when
 * context expansion is needed.
 *
 * Implements the hybrid expansion strategy by detecting:
 * 1. Linguistic hedging markers in LLM responses
 * 2. Explicit uncertainty signals
 * 3. Question deflection patterns
 * 4. Vague language usage
 */
@Suppress("TooManyFunctions")
@Singleton
class ConfidenceMonitor
    @Inject
    constructor() {
        companion object {
            private const val TAG = "ConfidenceMonitor"
            private const val MAX_HISTORY_SIZE = 10
            private const val TREND_WINDOW_SIZE = 3
            private const val TREND_THRESHOLD = 0.15
            private const val VAGUE_OCCURRENCE_CAP = 3
            private const val LENGTH_NORMALIZATION_BASE = 500
        }

        // Configuration
        private var config: ConfidenceConfig = ConfidenceConfig.DEFAULT

        // History of recent confidence scores for trend analysis
        private val recentScores = mutableListOf<Double>()
        private val mutex = Mutex()

        // MARK: - Analysis

        /**
         * Analyze an LLM response for uncertainty markers.
         *
         * @param response The LLM's response text
         * @return Confidence analysis result
         */
        suspend fun analyzeResponse(response: String): ConfidenceAnalysis =
            mutex.withLock {
                val normalizedResponse = response.lowercase()

                // Calculate scores for each uncertainty dimension
                val hedgingScore = calculateHedgingScore(normalizedResponse)
                val questionDeflectionScore = calculateQuestionDeflectionScore(normalizedResponse)
                val knowledgeGapScore = calculateKnowledgeGapScore(normalizedResponse)
                val vagueLanguageScore = calculateVagueLanguageScore(normalizedResponse)

                // Weighted combination of scores
                val uncertaintyScore =
                    hedgingScore * config.hedgingWeight +
                        questionDeflectionScore * config.deflectionWeight +
                        knowledgeGapScore * config.knowledgeGapWeight +
                        vagueLanguageScore * config.vagueLanguageWeight

                // Confidence is inverse of uncertainty
                val confidenceScore = max(0.0, 1.0 - uncertaintyScore)

                // Detect specific markers
                val detectedMarkers = detectSpecificMarkers(normalizedResponse)

                // Record score for trend analysis
                recordScoreInternal(confidenceScore)

                val analysis =
                    ConfidenceAnalysis(
                        confidenceScore = confidenceScore,
                        uncertaintyScore = uncertaintyScore,
                        hedgingScore = hedgingScore,
                        questionDeflectionScore = questionDeflectionScore,
                        knowledgeGapScore = knowledgeGapScore,
                        vagueLanguageScore = vagueLanguageScore,
                        detectedMarkers = detectedMarkers,
                        trend = calculateTrendInternal(),
                    )

                Log.d(
                    TAG,
                    "Analyzed response: confidence=${"%.2f".format(confidenceScore)}, " +
                        "markers=${detectedMarkers.size}",
                )

                analysis
            }

        /**
         * Determine if context expansion should be triggered.
         *
         * @param analysis The confidence analysis result
         * @return Whether expansion should be triggered
         */
        fun shouldTriggerExpansion(analysis: ConfidenceAnalysis): Boolean {
            // Primary check: confidence below threshold
            if (analysis.confidenceScore < config.expansionThreshold) {
                return true
            }

            // Secondary check: specific high-signal markers detected
            val highSignalMarkers =
                analysis.detectedMarkers.filter { marker ->
                    marker in ConfidenceMarker.HIGH_SIGNAL_MARKERS
                }
            if (highSignalMarkers.isNotEmpty()) {
                return true
            }

            // Trend check: declining confidence over recent responses
            if (analysis.trend == ConfidenceTrend.DECLINING &&
                analysis.confidenceScore < config.trendThreshold
            ) {
                return true
            }

            return false
        }

        /**
         * Get expansion recommendation based on analysis.
         *
         * @param analysis The confidence analysis result
         * @return Expansion recommendation
         */
        fun getExpansionRecommendation(analysis: ConfidenceAnalysis): ExpansionRecommendation {
            if (!shouldTriggerExpansion(analysis)) {
                return ExpansionRecommendation(
                    shouldExpand = false,
                    priority = ExpansionPriority.NONE,
                    suggestedScope = ExpansionScope.CURRENT_TOPIC,
                    reason = null,
                )
            }

            // Determine priority based on severity
            val priority =
                when {
                    analysis.confidenceScore < 0.3 -> ExpansionPriority.HIGH
                    analysis.confidenceScore < 0.5 -> ExpansionPriority.MEDIUM
                    else -> ExpansionPriority.LOW
                }

            // Determine scope based on detected markers
            val scope =
                when {
                    ConfidenceMarker.OUT_OF_SCOPE in analysis.detectedMarkers ||
                        ConfidenceMarker.TOPIC_BOUNDARY in analysis.detectedMarkers ->
                        ExpansionScope.RELATED_TOPICS

                    analysis.knowledgeGapScore > 0.5 -> ExpansionScope.CURRENT_UNIT
                    else -> ExpansionScope.CURRENT_TOPIC
                }

            // Determine reason
            val reason = determineExpansionReason(analysis)

            return ExpansionRecommendation(
                shouldExpand = true,
                priority = priority,
                suggestedScope = scope,
                reason = reason,
            )
        }

        // MARK: - Score Calculations

        /**
         * Calculate hedging language score.
         */
        private fun calculateHedgingScore(text: String): Double {
            val hedgingPhrases =
                mapOf(
                    "i'm not sure" to 0.8,
                    "i think" to 0.4,
                    "i believe" to 0.4,
                    "i'm uncertain" to 0.9,
                    "i'm not certain" to 0.9,
                    "possibly" to 0.5,
                    "perhaps" to 0.5,
                    "maybe" to 0.5,
                    "might be" to 0.5,
                    "could be" to 0.5,
                    "it seems" to 0.4,
                    "it appears" to 0.4,
                    "to my knowledge" to 0.6,
                    "as far as i know" to 0.6,
                    "i would guess" to 0.7,
                    "if i recall" to 0.6,
                    "not entirely sure" to 0.8,
                    "don't quote me" to 0.9,
                    "take this with a grain" to 0.8,
                )

            var totalScore = 0.0
            var matchCount = 0

            for ((phrase, weight) in hedgingPhrases) {
                if (phrase in text) {
                    totalScore += weight
                    matchCount++
                }
            }

            // Normalize: more matches = higher uncertainty
            return if (matchCount > 0) min(1.0, totalScore / max(1, matchCount)) else 0.0
        }

        /**
         * Calculate question deflection score.
         */
        private fun calculateQuestionDeflectionScore(text: String): Double {
            val deflectionPhrases =
                mapOf(
                    "i don't have enough information" to 0.9,
                    "i can't answer that" to 0.8,
                    "that's beyond" to 0.7,
                    "outside my" to 0.7,
                    "you should ask" to 0.6,
                    "consult a" to 0.5,
                    "i'd recommend checking" to 0.6,
                    "let me redirect" to 0.6,
                    "that question is" to 0.5,
                    "i'm not the right" to 0.7,
                    "that's a great question for" to 0.6,
                )

            var maxScore = 0.0
            for ((phrase, weight) in deflectionPhrases) {
                if (phrase in text) {
                    maxScore = max(maxScore, weight)
                }
            }

            return maxScore
        }

        /**
         * Calculate knowledge gap score.
         */
        private fun calculateKnowledgeGapScore(text: String): Double {
            val gapIndicators =
                mapOf(
                    "i don't know" to 0.9,
                    "i'm not familiar" to 0.8,
                    "i haven't learned" to 0.8,
                    "that's not something i" to 0.7,
                    "my training doesn't" to 0.8,
                    "i lack the context" to 0.9,
                    "without more information" to 0.7,
                    "i need more details" to 0.6,
                    "could you clarify" to 0.5,
                    "what do you mean by" to 0.4,
                    "can you be more specific" to 0.5,
                    "i'm missing" to 0.7,
                    "there's a gap in" to 0.8,
                )

            var maxScore = 0.0
            for ((phrase, weight) in gapIndicators) {
                if (phrase in text) {
                    maxScore = max(maxScore, weight)
                }
            }

            return maxScore
        }

        /**
         * Calculate vague language score.
         */
        private fun calculateVagueLanguageScore(text: String): Double {
            val vagueTerms =
                mapOf(
                    "something like" to 0.5,
                    "sort of" to 0.4,
                    "kind of" to 0.4,
                    "more or less" to 0.5,
                    "roughly" to 0.4,
                    "approximately" to 0.3,
                    "in general" to 0.3,
                    "generally speaking" to 0.3,
                    "it depends" to 0.5,
                    "various" to 0.3,
                    "several" to 0.2,
                    // Low weight - very common
                    "some" to 0.1,
                    "typically" to 0.2,
                    "usually" to 0.2,
                )

            var totalScore = 0.0
            var matchCount = 0

            for ((term, weight) in vagueTerms) {
                // Count occurrences
                val count = text.split(term).size - 1
                if (count > 0) {
                    totalScore += weight * min(count, VAGUE_OCCURRENCE_CAP).toDouble()
                    matchCount += count
                }
            }

            // Normalize by response length (vague terms in short responses are more significant)
            val lengthFactor =
                min(LENGTH_NORMALIZATION_BASE, text.length).toDouble() /
                    LENGTH_NORMALIZATION_BASE
            return min(1.0, totalScore * (1.5 - lengthFactor * 0.5))
        }

        /**
         * Detect specific uncertainty markers.
         */
        private fun detectSpecificMarkers(text: String): Set<ConfidenceMarker> {
            val markers = mutableSetOf<ConfidenceMarker>()

            // Hedging markers
            if ("i'm not sure" in text || "i'm uncertain" in text) {
                markers.add(ConfidenceMarker.HEDGING)
            }

            // Knowledge gap markers
            if ("i don't know" in text || "i'm not familiar" in text) {
                markers.add(ConfidenceMarker.KNOWLEDGE_GAP)
            }

            // Deflection markers
            if ("you should ask" in text || "consult a" in text) {
                markers.add(ConfidenceMarker.DEFLECTION)
            }

            // Topic boundary markers
            if ("that's outside" in text || "beyond the scope" in text) {
                markers.add(ConfidenceMarker.TOPIC_BOUNDARY)
            }

            // Out of scope markers
            if ("i can't help with" in text || "not within my" in text) {
                markers.add(ConfidenceMarker.OUT_OF_SCOPE)
            }

            // Clarification request markers
            if ("could you clarify" in text || "what do you mean" in text) {
                markers.add(ConfidenceMarker.CLARIFICATION_NEEDED)
            }

            // Speculation markers
            if ("my guess" in text || "i would speculate" in text) {
                markers.add(ConfidenceMarker.SPECULATION)
            }

            return markers
        }

        // MARK: - Trend Analysis

        /**
         * Record a confidence score for trend analysis (internal, must hold mutex).
         */
        private fun recordScoreInternal(score: Double) {
            recentScores.add(score)
            if (recentScores.size > MAX_HISTORY_SIZE) {
                recentScores.removeAt(0)
            }
        }

        /**
         * Calculate confidence trend (internal, must hold mutex).
         */
        private fun calculateTrendInternal(): ConfidenceTrend {
            if (recentScores.size < TREND_WINDOW_SIZE) {
                return ConfidenceTrend.STABLE
            }

            val recent = recentScores.takeLast(TREND_WINDOW_SIZE)
            val oldest = recent.first()
            val newest = recent.last()

            val delta = newest - oldest

            return when {
                delta > TREND_THRESHOLD -> ConfidenceTrend.IMPROVING
                delta < -TREND_THRESHOLD -> ConfidenceTrend.DECLINING
                else -> ConfidenceTrend.STABLE
            }
        }

        /**
         * Determine the reason for expansion recommendation.
         */
        private fun determineExpansionReason(analysis: ConfidenceAnalysis): String =
            when {
                analysis.knowledgeGapScore > 0.5 -> "Knowledge gap detected in response"
                analysis.hedgingScore > 0.6 -> "High uncertainty in response language"
                analysis.questionDeflectionScore > 0.5 -> "Response deflected the question"
                ConfidenceMarker.CLARIFICATION_NEEDED in analysis.detectedMarkers ->
                    "Response requested clarification"
                analysis.trend == ConfidenceTrend.DECLINING -> "Declining confidence trend"
                else -> "Low overall confidence score"
            }

        // MARK: - Configuration

        /**
         * Update confidence configuration.
         *
         * @param config New configuration
         */
        suspend fun updateConfig(config: ConfidenceConfig) =
            mutex.withLock {
                this.config = config
                Log.i(TAG, "Updated confidence config")
            }

        /**
         * Reset history.
         */
        suspend fun reset() =
            mutex.withLock {
                recentScores.clear()
            }
    }

// MARK: - Supporting Types

/**
 * Configuration for confidence monitoring.
 */
data class ConfidenceConfig(
    /** Threshold below which expansion is triggered */
    val expansionThreshold: Double,
    /** Threshold for trend-based expansion */
    val trendThreshold: Double,
    /** Weight for hedging language */
    val hedgingWeight: Double,
    /** Weight for question deflection */
    val deflectionWeight: Double,
    /** Weight for knowledge gaps */
    val knowledgeGapWeight: Double,
    /** Weight for vague language */
    val vagueLanguageWeight: Double,
) {
    companion object {
        /** Default configuration */
        val DEFAULT =
            ConfidenceConfig(
                expansionThreshold = 0.6,
                trendThreshold = 0.7,
                hedgingWeight = 0.3,
                deflectionWeight = 0.25,
                knowledgeGapWeight = 0.3,
                vagueLanguageWeight = 0.15,
            )

        /** More sensitive configuration for tutoring */
        val TUTORING =
            ConfidenceConfig(
                expansionThreshold = 0.7,
                trendThreshold = 0.75,
                hedgingWeight = 0.25,
                deflectionWeight = 0.3,
                knowledgeGapWeight = 0.35,
                vagueLanguageWeight = 0.1,
            )
    }
}

/**
 * Result of analyzing response confidence.
 */
data class ConfidenceAnalysis(
    /** Overall confidence score (0.0 = uncertain, 1.0 = confident) */
    val confidenceScore: Double,
    /** Overall uncertainty score (inverse of confidence) */
    val uncertaintyScore: Double,
    /** Score from hedging language detection */
    val hedgingScore: Double,
    /** Score from question deflection detection */
    val questionDeflectionScore: Double,
    /** Score from knowledge gap detection */
    val knowledgeGapScore: Double,
    /** Score from vague language detection */
    val vagueLanguageScore: Double,
    /** Specific uncertainty markers detected */
    val detectedMarkers: Set<ConfidenceMarker>,
    /** Trend over recent responses */
    val trend: ConfidenceTrend,
) {
    /** Whether response indicates high confidence */
    val isHighConfidence: Boolean
        get() = confidenceScore >= 0.8 && detectedMarkers.isEmpty()

    /** Whether response indicates low confidence */
    val isLowConfidence: Boolean
        get() = confidenceScore < 0.5
}

/**
 * Specific markers of uncertainty.
 */
enum class ConfidenceMarker {
    HEDGING,
    KNOWLEDGE_GAP,
    DEFLECTION,
    TOPIC_BOUNDARY,
    OUT_OF_SCOPE,
    CLARIFICATION_NEEDED,
    SPECULATION,
    ;

    companion object {
        /** Markers that strongly indicate expansion is needed */
        val HIGH_SIGNAL_MARKERS =
            setOf(
                KNOWLEDGE_GAP,
                OUT_OF_SCOPE,
                TOPIC_BOUNDARY,
            )
    }
}

/**
 * Confidence trend over recent responses.
 */
enum class ConfidenceTrend {
    IMPROVING,
    STABLE,
    DECLINING,
}

/**
 * Recommendation for context expansion.
 */
data class ExpansionRecommendation(
    /** Whether expansion should be performed */
    val shouldExpand: Boolean,
    /** Priority level for expansion */
    val priority: ExpansionPriority,
    /** Suggested scope for expansion search */
    val suggestedScope: ExpansionScope,
    /** Reason for the recommendation */
    val reason: String?,
)

/**
 * Priority level for expansion.
 */
enum class ExpansionPriority : Comparable<ExpansionPriority> {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
}
