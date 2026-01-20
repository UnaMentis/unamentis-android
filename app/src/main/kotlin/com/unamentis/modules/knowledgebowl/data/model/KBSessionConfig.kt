package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for a Knowledge Bowl practice session.
 *
 * @property region The regional rules to apply
 * @property roundType Written or oral round
 * @property questionCount Number of questions in the session
 * @property timeLimitSeconds Optional time limit (null for no limit)
 * @property domains Optional filter to specific domains
 * @property difficulty Optional filter to specific difficulty
 * @property gradeLevel Optional filter to specific grade level
 * @property studyMode The study mode for this session
 * @property pointsPerQuestion Points awarded per correct answer
 */
@Serializable
data class KBSessionConfig(
    val region: KBRegion,
    @SerialName("round_type")
    val roundType: KBRoundType,
    @SerialName("question_count")
    val questionCount: Int,
    @SerialName("time_limit_seconds")
    val timeLimitSeconds: Int? = null,
    val domains: List<KBDomain>? = null,
    val difficulty: KBDifficulty? = null,
    @SerialName("grade_level")
    val gradeLevel: KBGradeLevel? = null,
    @SerialName("study_mode")
    val studyMode: KBStudyMode = KBStudyMode.DIAGNOSTIC,
    @SerialName("points_per_question")
    val pointsPerQuestion: Int = 1,
) {
    /**
     * Get the regional configuration.
     */
    val regionalConfig: KBRegionalConfig
        get() = region.config

    companion object {
        /**
         * Create a written practice session with regional defaults.
         */
        @Suppress("LongParameterList")
        fun writtenPractice(
            region: KBRegion,
            questionCount: Int? = null,
            timeLimitSeconds: Int? = null,
            domains: List<KBDomain>? = null,
            difficulty: KBDifficulty? = null,
            gradeLevel: KBGradeLevel? = null,
        ): KBSessionConfig {
            val config = region.config
            return KBSessionConfig(
                region = region,
                roundType = KBRoundType.WRITTEN,
                questionCount = questionCount ?: config.writtenQuestionCount,
                timeLimitSeconds = timeLimitSeconds ?: config.writtenTimeLimitSeconds,
                domains = domains,
                difficulty = difficulty,
                gradeLevel = gradeLevel,
            )
        }

        /**
         * Create an oral practice session with regional defaults.
         */
        fun oralPractice(
            region: KBRegion,
            questionCount: Int? = null,
            domains: List<KBDomain>? = null,
            difficulty: KBDifficulty? = null,
            gradeLevel: KBGradeLevel? = null,
        ): KBSessionConfig {
            val config = region.config
            return KBSessionConfig(
                region = region,
                roundType = KBRoundType.ORAL,
                questionCount = questionCount ?: config.oralQuestionCount,
                // Oral rounds don't have overall time limit
                timeLimitSeconds = null,
                domains = domains,
                difficulty = difficulty,
                gradeLevel = gradeLevel,
            )
        }

        /**
         * Create a quick practice session with custom question count.
         */
        fun quickPractice(
            region: KBRegion,
            roundType: KBRoundType,
            questionCount: Int = 10,
        ): KBSessionConfig {
            val timePerQuestion = if (roundType == KBRoundType.WRITTEN) 15 else 0
            return KBSessionConfig(
                region = region,
                roundType = roundType,
                questionCount = questionCount,
                timeLimitSeconds =
                    if (roundType == KBRoundType.WRITTEN) {
                        questionCount * timePerQuestion
                    } else {
                        null
                    },
                domains = null,
                difficulty = null,
                gradeLevel = null,
            )
        }
    }
}
