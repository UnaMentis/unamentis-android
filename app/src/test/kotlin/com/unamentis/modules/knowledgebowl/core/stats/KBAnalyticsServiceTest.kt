package com.unamentis.modules.knowledgebowl.core.stats

import android.content.Context
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBRoundType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for KBAnalyticsService.
 */
class KBAnalyticsServiceTest {
    private lateinit var analytics: KBAnalyticsService
    private lateinit var store: KBSessionStore
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = createTempDir("kb_analytics_test")
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.filesDir } returns tempDir
        store = KBSessionStore(mockContext)
        analytics = KBAnalyticsService(store)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `getDomainPerformance returns empty map for no sessions`() =
        runTest {
            val performance = analytics.getDomainPerformance()
            assertTrue(performance.isEmpty())
        }

    @Test
    fun `getDomainPerformance aggregates across sessions`() =
        runTest {
            val session1 = TestSessionFactory.createSessionWithDomainAttempts(KBDomain.SCIENCE, correct = 3, total = 5)
            val session2 = TestSessionFactory.createSessionWithDomainAttempts(KBDomain.SCIENCE, correct = 2, total = 5)
            store.save(session1)
            store.save(session2)

            val performance = analytics.getDomainPerformance()
            val scienceStats = performance[KBDomain.SCIENCE]!!
            assertEquals(10, scienceStats.totalQuestions)
            assertEquals(5, scienceStats.correctAnswers)
            assertEquals(0.5, scienceStats.accuracy, 0.01)
        }

    @Test
    fun `getWeakDomains returns domains sorted by accuracy ascending`() =
        runTest {
            val weak = TestSessionFactory.createSessionWithDomainAttempts(KBDomain.MATHEMATICS, correct = 1, total = 5)
            val strong = TestSessionFactory.createSessionWithDomainAttempts(KBDomain.SCIENCE, correct = 4, total = 5)
            store.save(weak)
            store.save(strong)

            val weakDomains = analytics.getWeakDomains()
            assertTrue(weakDomains.isNotEmpty())
            assertEquals(KBDomain.MATHEMATICS, weakDomains.first())
        }

    @Test
    fun `getStrongDomains returns domains sorted by accuracy descending`() =
        runTest {
            val weak = TestSessionFactory.createSessionWithDomainAttempts(KBDomain.MATHEMATICS, correct = 1, total = 5)
            val strong = TestSessionFactory.createSessionWithDomainAttempts(KBDomain.SCIENCE, correct = 4, total = 5)
            store.save(weak)
            store.save(strong)

            val strongDomains = analytics.getStrongDomains()
            assertTrue(strongDomains.isNotEmpty())
            assertEquals(KBDomain.SCIENCE, strongDomains.first())
        }

    @Test
    fun `getRoundTypeComparison calculates written and oral accuracy`() =
        runTest {
            val written =
                TestSessionFactory.createCompletedSession(
                    correct = 4,
                    total = 5,
                    roundType = KBRoundType.WRITTEN,
                )
            val oral =
                TestSessionFactory.createCompletedSession(
                    correct = 2,
                    total = 5,
                    roundType = KBRoundType.ORAL,
                )
            store.save(written)
            store.save(oral)

            val comparison = analytics.getRoundTypeComparison()
            assertEquals(0.8, comparison.writtenAccuracy, 0.01)
            assertEquals(0.4, comparison.oralAccuracy, 0.01)
            assertEquals(5, comparison.writtenQuestions)
            assertEquals(5, comparison.oralQuestions)
            assertTrue(comparison.hasSignificantGap)
        }

    @Test
    fun `getAccuracyTrend returns empty for no sessions`() =
        runTest {
            val trend = analytics.getAccuracyTrend()
            assertTrue(trend.isEmpty())
        }

    @Test
    fun `getDomainMastery returns NOT_STARTED for unpracticed domains`() =
        runTest {
            val mastery = analytics.getDomainMastery()
            assertTrue(mastery.isNotEmpty())
            assertEquals(MasteryLevel.NOT_STARTED, mastery[KBDomain.ARTS])
        }

    @Test
    fun `getDomainMastery returns appropriate level for practiced domain`() =
        runTest {
            // 20 correct out of 20 = 100% accuracy, 20 questions
            val session = TestSessionFactory.createSessionWithDomainAttempts(KBDomain.SCIENCE, correct = 20, total = 20)
            store.save(session)

            val mastery = analytics.getDomainMastery()
            assertEquals(MasteryLevel.MASTERED, mastery[KBDomain.SCIENCE])
        }

    @Test
    fun `generateInsights returns list for sessions with data`() =
        runTest {
            // Create a session with weak domain to trigger domain weakness insight
            val session = TestSessionFactory.createSessionWithDomainAttempts(KBDomain.HISTORY, correct = 1, total = 10)
            store.save(session)

            val insights = analytics.generateInsights()
            // Should have at least domain weakness insight
            assertTrue(insights.isNotEmpty())
        }

    @Test
    fun `MasteryLevel from returns correct levels`() {
        assertEquals(MasteryLevel.NOT_STARTED, MasteryLevel.from(0.0, 0))
        assertEquals(MasteryLevel.BEGINNER, MasteryLevel.from(0.0, 3))
        assertEquals(MasteryLevel.BEGINNER, MasteryLevel.from(0.35, 10))
        assertEquals(MasteryLevel.INTERMEDIATE, MasteryLevel.from(0.55, 10))
        assertEquals(MasteryLevel.ADVANCED, MasteryLevel.from(0.80, 10))
        assertEquals(MasteryLevel.MASTERED, MasteryLevel.from(0.95, 25))
    }

    @Test
    fun `RoundTypeComparison gap calculated correctly`() {
        val comparison =
            RoundTypeComparison(
                writtenAccuracy = 0.8,
                oralAccuracy = 0.5,
                writtenQuestions = 10,
                oralQuestions = 10,
            )
        assertEquals(0.3, comparison.gap, 0.001)
        assertTrue(comparison.hasSignificantGap)
    }

    @Test
    fun `RoundTypeComparison no significant gap when close`() {
        val comparison =
            RoundTypeComparison(
                writtenAccuracy = 0.8,
                oralAccuracy = 0.75,
                writtenQuestions = 10,
                oralQuestions = 10,
            )
        assertEquals(0.05, comparison.gap, 0.001)
        assertTrue(!comparison.hasSignificantGap)
    }
}
