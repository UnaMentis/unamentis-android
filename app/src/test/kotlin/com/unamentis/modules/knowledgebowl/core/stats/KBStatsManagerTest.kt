package com.unamentis.modules.knowledgebowl.core.stats

import android.content.Context
import android.content.SharedPreferences
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import com.unamentis.modules.knowledgebowl.data.model.KBRoundType
import com.unamentis.modules.knowledgebowl.data.model.KBSessionSummary
import com.unamentis.modules.knowledgebowl.data.model.KBStudyMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for KBStatsManager.
 */
class KBStatsManagerTest {
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var statsManager: KBStatsManager

    // Storage for mock SharedPreferences
    private val prefsStorage = mutableMapOf<String, Any?>()

    @Before
    fun setup() {
        mockEditor = mockk(relaxed = true)
        mockPrefs = mockk()
        mockContext = mockk()

        // Mock editor chain returns
        every { mockEditor.putInt(any(), any()) } answers {
            prefsStorage[firstArg()] = secondArg<Int>()
            mockEditor
        }
        every { mockEditor.putFloat(any(), any()) } answers {
            prefsStorage[firstArg()] = secondArg<Float>()
            mockEditor
        }
        every { mockEditor.putString(any(), any()) } answers {
            prefsStorage[firstArg()] = secondArg<String>()
            mockEditor
        }
        every { mockEditor.clear() } answers {
            prefsStorage.clear()
            mockEditor
        }
        every { mockEditor.apply() } returns Unit

        // Mock prefs reads
        every { mockPrefs.getInt(any(), any()) } answers {
            (prefsStorage[firstArg()] as? Int) ?: secondArg()
        }
        every { mockPrefs.getFloat(any(), any()) } answers {
            (prefsStorage[firstArg()] as? Float) ?: secondArg()
        }
        every { mockPrefs.getString(any(), any()) } answers {
            prefsStorage[firstArg()] as? String
        }
        every { mockPrefs.edit() } returns mockEditor

        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs

        prefsStorage.clear()
        statsManager = KBStatsManager(mockContext)
    }

    // Initial state tests

    @Test
    fun `initial state has zero stats`() {
        assertEquals(0, statsManager.totalQuestionsAnswered.value)
        assertEquals(0, statsManager.totalCorrectAnswers.value)
        assertEquals(0.0, statsManager.averageResponseTime.value, 0.01)
    }

    @Test
    fun `initial overall accuracy is zero`() {
        assertEquals(0f, statsManager.overallAccuracy, 0.01f)
    }

    @Test
    fun `initial competition readiness is zero`() {
        assertEquals(0f, statsManager.competitionReadiness, 0.01f)
    }

    // Recording session tests

    @Test
    fun `recordSession updates total questions`() {
        val summary = createSummary(totalQuestions = 10, totalCorrect = 7)

        statsManager.recordSession(summary, KBStudyMode.DIAGNOSTIC)

        assertEquals(10, statsManager.totalQuestionsAnswered.value)
    }

    @Test
    fun `recordSession updates total correct`() {
        val summary = createSummary(totalQuestions = 10, totalCorrect = 7)

        statsManager.recordSession(summary, KBStudyMode.DIAGNOSTIC)

        assertEquals(7, statsManager.totalCorrectAnswers.value)
    }

    @Test
    fun `recordSession updates average response time`() {
        val summary =
            createSummary(
                totalQuestions = 10,
                totalCorrect = 7,
                averageResponseTime = 5.0f,
            )

        statsManager.recordSession(summary, KBStudyMode.DIAGNOSTIC)

        assertEquals(5.0, statsManager.averageResponseTime.value, 0.01)
    }

    @Test
    fun `recordSession accumulates stats from multiple sessions`() {
        val summary1 = createSummary(totalQuestions = 10, totalCorrect = 7)
        val summary2 = createSummary(totalQuestions = 5, totalCorrect = 5)

        statsManager.recordSession(summary1, KBStudyMode.DIAGNOSTIC)
        statsManager.recordSession(summary2, KBStudyMode.TARGETED)

        assertEquals(15, statsManager.totalQuestionsAnswered.value)
        assertEquals(12, statsManager.totalCorrectAnswers.value)
    }

    @Test
    fun `recordSession adds to recent sessions`() {
        val summary = createSummary(totalQuestions = 10, totalCorrect = 7)

        statsManager.recordSession(summary, KBStudyMode.DIAGNOSTIC)

        assertEquals(1, statsManager.recentSessions.value.size)
    }

    @Test
    fun `recent sessions are limited to 20`() {
        repeat(25) { i ->
            val summary =
                createSummary(
                    sessionId = "session-$i",
                    totalQuestions = 10,
                    totalCorrect = 5,
                )
            statsManager.recordSession(summary, KBStudyMode.DIAGNOSTIC)
        }

        assertEquals(20, statsManager.recentSessions.value.size)
    }

    @Test
    fun `recent sessions are in reverse chronological order`() {
        repeat(5) { i ->
            val summary =
                createSummary(
                    sessionId = "session-$i",
                    totalQuestions = 10,
                    totalCorrect = 5,
                )
            statsManager.recordSession(summary, KBStudyMode.DIAGNOSTIC)
        }

        // Most recent should be first
        assertEquals("session-4", statsManager.recentSessions.value[0].id)
        assertEquals("session-0", statsManager.recentSessions.value[4].id)
    }

    // Overall accuracy tests

    @Test
    fun `overallAccuracy calculates correctly`() {
        val summary = createSummary(totalQuestions = 10, totalCorrect = 7)
        statsManager.recordSession(summary, KBStudyMode.DIAGNOSTIC)

        assertEquals(0.7f, statsManager.overallAccuracy, 0.01f)
    }

    // Domain stats tests

    @Test
    fun `recordDomainAttempt tracks correct answers`() {
        statsManager.recordDomainAttempt(KBDomain.SCIENCE, wasCorrect = true)
        statsManager.recordDomainAttempt(KBDomain.SCIENCE, wasCorrect = true)
        statsManager.recordDomainAttempt(KBDomain.SCIENCE, wasCorrect = false)

        val stats = statsManager.getDomainStats(KBDomain.SCIENCE)
        assertEquals(3, stats.totalAnswered)
        assertEquals(2, stats.totalCorrect)
    }

    @Test
    fun `mastery calculates correctly for domain`() {
        statsManager.recordDomainAttempt(KBDomain.MATHEMATICS, wasCorrect = true)
        statsManager.recordDomainAttempt(KBDomain.MATHEMATICS, wasCorrect = true)
        statsManager.recordDomainAttempt(KBDomain.MATHEMATICS, wasCorrect = false)
        statsManager.recordDomainAttempt(KBDomain.MATHEMATICS, wasCorrect = false)

        assertEquals(0.5f, statsManager.mastery(KBDomain.MATHEMATICS), 0.01f)
    }

    @Test
    fun `mastery returns zero for unpracticed domain`() {
        assertEquals(0f, statsManager.mastery(KBDomain.ARTS), 0.01f)
    }

    @Test
    fun `getWeakDomains returns lowest mastery domains`() {
        // Practice science with high accuracy
        repeat(10) {
            statsManager.recordDomainAttempt(KBDomain.SCIENCE, wasCorrect = true)
        }

        // Practice math with low accuracy
        repeat(10) {
            statsManager.recordDomainAttempt(KBDomain.MATHEMATICS, wasCorrect = it < 2)
        }

        val weakDomains = statsManager.getWeakDomains(2)

        assertEquals(2, weakDomains.size)
        assertEquals(KBDomain.MATHEMATICS, weakDomains[0].first)
    }

    @Test
    fun `getStrongDomains returns highest mastery domains`() {
        // Practice science with high accuracy
        repeat(10) {
            statsManager.recordDomainAttempt(KBDomain.SCIENCE, wasCorrect = it < 9)
        }

        // Practice math with low accuracy
        repeat(10) {
            statsManager.recordDomainAttempt(KBDomain.MATHEMATICS, wasCorrect = it < 2)
        }

        val strongDomains = statsManager.getStrongDomains(2)

        assertEquals(2, strongDomains.size)
        assertEquals(KBDomain.SCIENCE, strongDomains[0].first)
    }

    @Test
    fun `getUnderPracticedDomains returns domains below threshold`() {
        // Practice just one domain
        repeat(10) {
            statsManager.recordDomainAttempt(KBDomain.SCIENCE, wasCorrect = true)
        }

        val underPracticed = statsManager.getUnderPracticedDomains(5)

        // Should include all domains except science
        assertEquals(KBDomain.entries.size - 1, underPracticed.size)
        assertTrue(underPracticed.none { it == KBDomain.SCIENCE })
    }

    // Competition readiness tests

    @Test
    fun `competitionReadiness reflects accuracy weight`() {
        // Perfect accuracy, minimal volume
        val summary = createSummary(totalQuestions = 10, totalCorrect = 10)
        statsManager.recordSession(summary, KBStudyMode.DIAGNOSTIC)

        // Should have some readiness from accuracy
        assertTrue(statsManager.competitionReadiness > 0f)
    }

    // Reset tests

    @Test
    fun `resetStats clears all data`() {
        val summary = createSummary(totalQuestions = 10, totalCorrect = 7)
        statsManager.recordSession(summary, KBStudyMode.DIAGNOSTIC)
        statsManager.recordDomainAttempt(KBDomain.SCIENCE, wasCorrect = true)

        statsManager.resetStats()

        assertEquals(0, statsManager.totalQuestionsAnswered.value)
        assertEquals(0, statsManager.totalCorrectAnswers.value)
        assertEquals(0.0, statsManager.averageResponseTime.value, 0.01)
        assertTrue(statsManager.domainStats.value.isEmpty())
        assertTrue(statsManager.recentSessions.value.isEmpty())
    }

    @Test
    fun `resetStats clears SharedPreferences`() {
        statsManager.resetStats()

        verify { mockEditor.clear() }
    }

    // Persistence tests

    @Test
    fun `recordSession persists stats`() {
        val summary = createSummary(totalQuestions = 10, totalCorrect = 7)

        statsManager.recordSession(summary, KBStudyMode.DIAGNOSTIC)

        verify { mockEditor.putInt("total_questions", 10) }
        verify { mockEditor.putInt("total_correct", 7) }
        verify { mockEditor.apply() }
    }

    // Helper functions

    @Suppress("LongParameterList")
    private fun createSummary(
        sessionId: String = "test-session",
        totalQuestions: Int = 10,
        totalCorrect: Int = 5,
        totalPoints: Int = totalCorrect,
        averageResponseTime: Float = 3.0f,
        durationSeconds: Float = 60f,
    ): KBSessionSummary =
        KBSessionSummary(
            sessionId = sessionId,
            roundType = KBRoundType.WRITTEN,
            region = KBRegion.COLORADO,
            totalQuestions = totalQuestions,
            totalCorrect = totalCorrect,
            totalPoints = totalPoints,
            accuracy = if (totalQuestions > 0) totalCorrect.toFloat() / totalQuestions else 0f,
            averageResponseTimeSeconds = averageResponseTime,
            durationSeconds = durationSeconds,
            completedAtMillis = System.currentTimeMillis(),
        )
}
