package com.unamentis.modules.knowledgebowl.core.stats

import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBQuestionAttempt
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import com.unamentis.modules.knowledgebowl.data.model.KBRoundType
import com.unamentis.modules.knowledgebowl.data.model.KBSession
import com.unamentis.modules.knowledgebowl.data.model.KBSessionConfig
import java.util.UUID

/**
 * Factory for creating test sessions with configurable properties.
 */
object TestSessionFactory {
    /**
     * Create a basic session with the given parameters.
     */
    fun createSession(
        id: String = UUID.randomUUID().toString(),
        region: KBRegion = KBRegion.COLORADO,
        roundType: KBRoundType = KBRoundType.WRITTEN,
        questionCount: Int = 10,
        startTimeMillis: Long = System.currentTimeMillis(),
    ): KBSession =
        KBSession(
            id = id,
            config =
                KBSessionConfig(
                    region = region,
                    roundType = roundType,
                    questionCount = questionCount,
                ),
            startTimeMillis = startTimeMillis,
        )

    /**
     * Create a completed session with the specified correct/total attempts.
     */
    fun createCompletedSession(
        correct: Int = 3,
        total: Int = 5,
        region: KBRegion = KBRegion.COLORADO,
        roundType: KBRoundType = KBRoundType.WRITTEN,
    ): KBSession {
        val session =
            KBSession(
                id = UUID.randomUUID().toString(),
                config =
                    KBSessionConfig(
                        region = region,
                        roundType = roundType,
                        questionCount = total,
                    ),
                startTimeMillis = System.currentTimeMillis() - 300_000,
                endTimeMillis = System.currentTimeMillis(),
                isComplete = true,
            )

        val domains = KBDomain.entries
        for (i in 0 until total) {
            session.attempts.add(
                KBQuestionAttempt(
                    id = UUID.randomUUID().toString(),
                    questionId = "q-$i",
                    domain = domains[i % domains.size],
                    responseTimeSeconds = 3.0f + (i * 0.5f),
                    wasCorrect = i < correct,
                    pointsEarned = if (i < correct) 1 else 0,
                    roundType = roundType,
                ),
            )
        }

        return session
    }

    /**
     * Create a completed session with specific domain attempts.
     */
    fun createSessionWithDomainAttempts(
        domain: KBDomain,
        correct: Int,
        total: Int,
        roundType: KBRoundType = KBRoundType.WRITTEN,
    ): KBSession {
        val session =
            KBSession(
                id = UUID.randomUUID().toString(),
                config =
                    KBSessionConfig(
                        region = KBRegion.COLORADO,
                        roundType = roundType,
                        questionCount = total,
                    ),
                startTimeMillis = System.currentTimeMillis() - 300_000,
                endTimeMillis = System.currentTimeMillis(),
                isComplete = true,
            )

        for (i in 0 until total) {
            session.attempts.add(
                KBQuestionAttempt(
                    id = UUID.randomUUID().toString(),
                    questionId = "q-$domain-$i",
                    domain = domain,
                    responseTimeSeconds = 4.0f,
                    wasCorrect = i < correct,
                    pointsEarned = if (i < correct) 1 else 0,
                    roundType = roundType,
                ),
            )
        }

        return session
    }
}
