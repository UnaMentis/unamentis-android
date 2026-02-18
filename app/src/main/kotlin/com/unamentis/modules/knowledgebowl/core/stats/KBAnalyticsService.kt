package com.unamentis.modules.knowledgebowl.core.stats

import android.util.Log
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBRoundType
import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rich analytics service for Knowledge Bowl performance data.
 *
 * Aggregates session data to provide:
 * - Domain performance breakdown
 * - Accuracy trends over time
 * - Round type comparison (written vs oral)
 * - Actionable insights with priorities
 * - Domain mastery levels
 * - Streak calculation
 *
 * Maps to iOS KBAnalyticsService actor.
 */
@Suppress("TooManyFunctions")
@Singleton
class KBAnalyticsService
    @Inject
    constructor(
        private val sessionStore: KBSessionStore,
    ) {
        companion object {
            private const val TAG = "KBAnalyticsService"
            private const val DEFAULT_TREND_DAYS = 30
            private const val MASTERY_MIN_QUESTIONS = 5
            private const val SLOW_RESPONSE_THRESHOLD = 8.0
            private const val LOW_ACTIVITY_DAYS = 7
        }

        // MARK: - Domain Performance

        /**
         * Get performance analytics for each domain.
         */
        suspend fun getDomainPerformance(): Map<KBDomain, DomainAnalytics> {
            val sessions = sessionStore.loadAll().filter { it.isComplete }
            val analytics = mutableMapOf<KBDomain, DomainAnalytics>()

            for (session in sessions) {
                for (attempt in session.attempts) {
                    val existing = analytics[attempt.domain] ?: DomainAnalytics(attempt.domain)
                    analytics[attempt.domain] =
                        existing.copy(
                            totalQuestions = existing.totalQuestions + 1,
                            correctAnswers = existing.correctAnswers + if (attempt.wasCorrect) 1 else 0,
                            totalResponseTime = existing.totalResponseTime + attempt.responseTimeSeconds,
                        )
                }
            }

            return analytics
        }

        /**
         * Get domains sorted by accuracy (weakest first).
         */
        suspend fun getWeakDomains(): List<KBDomain> =
            getDomainPerformance()
                .filter { it.value.totalQuestions > 0 }
                .entries
                .sortedBy { it.value.accuracy }
                .map { it.key }

        /**
         * Get domains sorted by accuracy (strongest first).
         */
        suspend fun getStrongDomains(): List<KBDomain> =
            getDomainPerformance()
                .filter { it.value.totalQuestions > 0 }
                .entries
                .sortedByDescending { it.value.accuracy }
                .map { it.key }

        // MARK: - Round Type Comparison

        /**
         * Compare performance between written and oral rounds.
         */
        suspend fun getRoundTypeComparison(): RoundTypeComparison {
            val sessions = sessionStore.loadAll().filter { it.isComplete }

            val writtenSessions = sessions.filter { it.config.roundType == KBRoundType.WRITTEN }
            val oralSessions = sessions.filter { it.config.roundType == KBRoundType.ORAL }

            val writtenTotal = writtenSessions.sumOf { it.attempts.size }
            val writtenCorrect = writtenSessions.sumOf { it.correctCount }
            val oralTotal = oralSessions.sumOf { it.attempts.size }
            val oralCorrect = oralSessions.sumOf { it.correctCount }

            return RoundTypeComparison(
                writtenAccuracy = if (writtenTotal > 0) writtenCorrect.toDouble() / writtenTotal else 0.0,
                oralAccuracy = if (oralTotal > 0) oralCorrect.toDouble() / oralTotal else 0.0,
                writtenQuestions = writtenTotal,
                oralQuestions = oralTotal,
            )
        }

        // MARK: - Trends

        /**
         * Get accuracy trend over the specified number of days.
         *
         * @param days Number of days to look back (default 30)
         */
        suspend fun getAccuracyTrend(days: Int = DEFAULT_TREND_DAYS): List<DateAccuracy> {
            val cutoffMillis = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
            val sessions =
                sessionStore.loadAll().filter {
                    it.isComplete && it.startTimeMillis >= cutoffMillis
                }

            val calendar = Calendar.getInstance()
            val byDay =
                sessions.groupBy { session ->
                    calendar.timeInMillis = session.startTimeMillis
                    Triple(
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH),
                    )
                }

            return byDay.map { (dayKey, daySessions) ->
                val totalQuestions = daySessions.sumOf { it.attempts.size }
                val totalCorrect = daySessions.sumOf { it.correctCount }
                calendar.set(dayKey.first, dayKey.second, dayKey.third, 0, 0, 0)

                DateAccuracy(
                    dateMillis = calendar.timeInMillis,
                    accuracy = if (totalQuestions > 0) totalCorrect.toDouble() / totalQuestions else 0.0,
                    questionsAnswered = totalQuestions,
                )
            }.sortedBy { it.dateMillis }
        }

        /**
         * Calculate the current streak (consecutive days with sessions).
         */
        suspend fun calculateStreak(): Int {
            val stats = sessionStore.calculateStatistics()
            return stats.currentStreak
        }

        // MARK: - Mastery

        /**
         * Get mastery level for each domain based on accuracy and volume.
         */
        suspend fun getDomainMastery(): Map<KBDomain, MasteryLevel> {
            val performance = getDomainPerformance()
            return KBDomain.entries.associateWith { domain ->
                val analytics = performance[domain]
                if (analytics == null) {
                    MasteryLevel.NOT_STARTED
                } else {
                    MasteryLevel.from(analytics.accuracy, analytics.totalQuestions)
                }
            }
        }

        // MARK: - Insights

        /**
         * Generate actionable insights based on performance data.
         */
        @Suppress("CyclomaticComplexMethod")
        suspend fun generateInsights(): List<KBInsight> {
            val insights = mutableListOf<KBInsight>()

            try {
                val performance = getDomainPerformance()
                val comparison = getRoundTypeComparison()
                val trend = getAccuracyTrend()
                val streak = calculateStreak()
                val stats = sessionStore.calculateStatistics()

                // Performance gap between written and oral
                if (comparison.hasSignificantGap && comparison.writtenQuestions > 0 && comparison.oralQuestions > 0) {
                    insights.add(generatePerformanceGapInsight(comparison))
                }

                // Weak domains
                val weakDomains =
                    performance.filter {
                        it.value.totalQuestions >= MASTERY_MIN_QUESTIONS &&
                            it.value.accuracy < 0.5
                    }
                for ((domain, analytics) in weakDomains) {
                    insights.add(generateDomainWeaknessInsight(domain, analytics))
                }

                // Low activity
                if (stats.mostRecentSessionMillis != null) {
                    val daysSinceLastSession =
                        (System.currentTimeMillis() - stats.mostRecentSessionMillis) / (24 * 60 * 60 * 1000)
                    if (daysSinceLastSession >= LOW_ACTIVITY_DAYS) {
                        insights.add(generateLowActivityInsight(daysSinceLastSession.toInt()))
                    }
                }

                // Streak
                if (streak == 0 && stats.totalSessions > 0) {
                    insights.add(generateStreakBrokenInsight())
                } else if (streak >= 5) {
                    insights.add(generateStreakAchievementInsight(streak))
                }

                // Slow response time
                val avgResponseTime =
                    performance.values
                        .filter { it.totalQuestions > 0 }
                        .map { it.averageResponseTime }
                        .average()
                if (avgResponseTime > SLOW_RESPONSE_THRESHOLD && performance.values.any { it.totalQuestions > 0 }) {
                    insights.add(generateSlowResponseInsight(avgResponseTime))
                }

                // Improvement trend
                if (trend.size >= 3) {
                    val recentAvg = trend.takeLast(3).map { it.accuracy }.average()
                    val olderAvg = trend.take(3).map { it.accuracy }.average()
                    if (recentAvg > olderAvg + 0.05) {
                        insights.add(generateImprovementInsight(recentAvg, olderAvg))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate insights: ${e.message}", e)
            }

            return insights.sortedByDescending { it.priority.value }
        }

        // MARK: - Insight Generators

        private fun generatePerformanceGapInsight(comparison: RoundTypeComparison): KBInsight {
            val weaker = if (comparison.writtenAccuracy < comparison.oralAccuracy) "Written" else "Oral"
            val destination =
                if (weaker == "Written") InsightDestination.WrittenPractice else InsightDestination.OralPractice

            return KBInsight(
                type = InsightType.PERFORMANCE_GAP,
                title = "$weaker Round Needs Work",
                message = "Your $weaker accuracy is significantly lower. Focus practice there.",
                priority = InsightPriority.HIGH,
                recommendedAction = "Start a $weaker practice session",
                navigationDestination = destination,
            )
        }

        private fun generateDomainWeaknessInsight(
            domain: KBDomain,
            analytics: DomainAnalytics,
        ): KBInsight =
            KBInsight(
                type = InsightType.DOMAIN_WEAKNESS,
                title = "${domain.displayName} Needs Practice",
                message = "Your accuracy in ${domain.displayName} is ${(analytics.accuracy * 100).toInt()}%.",
                priority = InsightPriority.HIGH,
                recommendedAction = "Practice ${domain.displayName} questions",
                navigationDestination = InsightDestination.DomainDrill(domain),
            )

        private fun generateLowActivityInsight(daysSince: Int): KBInsight =
            KBInsight(
                type = InsightType.LOW_ACTIVITY,
                title = "Time to Practice",
                message = "It's been $daysSince days since your last session. Consistency helps retention.",
                priority = InsightPriority.MEDIUM,
                recommendedAction = "Start a quick practice session",
                navigationDestination = null,
            )

        private fun generateStreakBrokenInsight(): KBInsight =
            KBInsight(
                type = InsightType.STREAK_BROKEN,
                title = "Streak Lost",
                message = "Your daily practice streak was broken. Start a new one today!",
                priority = InsightPriority.MEDIUM,
                recommendedAction = "Practice today to start a new streak",
                navigationDestination = null,
            )

        private fun generateStreakAchievementInsight(streak: Int): KBInsight =
            KBInsight(
                type = InsightType.ACHIEVEMENT,
                title = "$streak-Day Streak!",
                message = "Great consistency! You've practiced $streak days in a row.",
                priority = InsightPriority.LOW,
                recommendedAction = "Keep it up!",
                navigationDestination = null,
            )

        private fun generateSlowResponseInsight(avgTime: Double): KBInsight =
            KBInsight(
                type = InsightType.RESPONSE_TIME,
                title = "Speed Up Responses",
                message =
                    "Your average response time is " +
                        "${String.format("%.1f", avgTime)}s. " +
                        "Competition target is under 5s.",
                priority = InsightPriority.MEDIUM,
                recommendedAction = "Try a speed practice session",
                navigationDestination = null,
            )

        private fun generateImprovementInsight(
            recentAvg: Double,
            olderAvg: Double,
        ): KBInsight {
            val improvement = ((recentAvg - olderAvg) * 100).toInt()
            return KBInsight(
                type = InsightType.IMPROVEMENT_TREND,
                title = "Improving!",
                message = "Your accuracy has improved by $improvement% recently. Keep it up!",
                priority = InsightPriority.LOW,
                recommendedAction = "Continue your current practice routine",
                navigationDestination = null,
            )
        }
    }

// MARK: - Analytics Data Models

/**
 * Performance analytics for a specific domain.
 */
data class DomainAnalytics(
    val domain: KBDomain,
    val totalQuestions: Int = 0,
    val correctAnswers: Int = 0,
    val totalResponseTime: Float = 0f,
) {
    /** Accuracy for this domain (0.0 to 1.0). */
    val accuracy: Double
        get() = if (totalQuestions > 0) correctAnswers.toDouble() / totalQuestions else 0.0

    /** Average response time in seconds. */
    val averageResponseTime: Double
        get() = if (totalQuestions > 0) totalResponseTime.toDouble() / totalQuestions else 0.0
}

/**
 * Comparison between written and oral round performance.
 */
data class RoundTypeComparison(
    val writtenAccuracy: Double,
    val oralAccuracy: Double,
    val writtenQuestions: Int,
    val oralQuestions: Int,
) {
    /** Absolute accuracy gap between round types. */
    val gap: Double
        get() = kotlin.math.abs(writtenAccuracy - oralAccuracy)

    /** Whether the gap is large enough to be actionable. */
    val hasSignificantGap: Boolean
        get() = gap >= 0.15
}

/**
 * Accuracy data for a single day.
 */
data class DateAccuracy(
    val dateMillis: Long,
    val accuracy: Double,
    val questionsAnswered: Int,
)

/**
 * An actionable insight based on performance data.
 */
data class KBInsight(
    val id: String = UUID.randomUUID().toString(),
    val type: InsightType,
    val title: String,
    val message: String,
    val priority: InsightPriority,
    val recommendedAction: String,
    val navigationDestination: InsightDestination?,
)

/**
 * Types of insights that can be generated.
 */
enum class InsightType {
    PERFORMANCE_GAP,
    DOMAIN_WEAKNESS,
    LOW_ACTIVITY,
    STREAK_BROKEN,
    ACHIEVEMENT,
    RESPONSE_TIME,
    IMPROVEMENT_TREND,
    COMPETITION_READY,
    REBOUND_SKILL,
    CONFERENCE_SKILL,
    DIFFICULTY_PROGRESSION,
    TIME_PATTERNS,
    MATCH_PERFORMANCE,
}

/**
 * Insight priority levels.
 */
enum class InsightPriority(val value: Int) {
    HIGH(3),
    MEDIUM(2),
    LOW(1),
}

/**
 * Navigation destinations for acting on insights.
 */
sealed class InsightDestination {
    data object OralPractice : InsightDestination()

    data object WrittenPractice : InsightDestination()

    data class DomainDrill(val domain: KBDomain) : InsightDestination()

    data object ConferencePractice : InsightDestination()

    data object ReboundPractice : InsightDestination()

    data object MatchSimulation : InsightDestination()

    data object DomainMastery : InsightDestination()

    data object Progress : InsightDestination()
}

/**
 * Domain mastery levels based on accuracy and question volume.
 */
enum class MasteryLevel {
    NOT_STARTED,
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    MASTERED,
    ;

    companion object {
        private const val BEGINNER_THRESHOLD = 0.3
        private const val INTERMEDIATE_THRESHOLD = 0.5
        private const val ADVANCED_THRESHOLD = 0.75
        private const val MASTERED_THRESHOLD = 0.9
        private const val MIN_QUESTIONS_FOR_MASTERY = 20

        /**
         * Determine mastery level from accuracy and question count.
         */
        @Suppress("MagicNumber")
        fun from(
            accuracy: Double,
            questionsAttempted: Int,
        ): MasteryLevel =
            when {
                questionsAttempted == 0 -> NOT_STARTED
                questionsAttempted < 5 -> BEGINNER
                accuracy >= MASTERED_THRESHOLD && questionsAttempted >= MIN_QUESTIONS_FOR_MASTERY -> MASTERED
                accuracy >= ADVANCED_THRESHOLD -> ADVANCED
                accuracy >= INTERMEDIATE_THRESHOLD -> INTERMEDIATE
                accuracy >= BEGINNER_THRESHOLD -> BEGINNER
                else -> BEGINNER
            }
    }
}

/**
 * Serializable version of round type comparison for API responses.
 */
@Serializable
data class RoundTypeComparisonData(
    @kotlinx.serialization.SerialName("written_accuracy")
    val writtenAccuracy: Double,
    @kotlinx.serialization.SerialName("oral_accuracy")
    val oralAccuracy: Double,
    @kotlinx.serialization.SerialName("written_questions")
    val writtenQuestions: Int,
    @kotlinx.serialization.SerialName("oral_questions")
    val oralQuestions: Int,
)
