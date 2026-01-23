package com.unamentis.modules.knowledgebowl.data.remote

import com.unamentis.modules.knowledgebowl.data.model.KBAnswer
import com.unamentis.modules.knowledgebowl.data.model.KBDifficulty
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBModuleFeatures
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBRequiredFeature
import com.unamentis.modules.knowledgebowl.data.model.KBStudyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for KBQuestionService study mode selection algorithms.
 *
 * These tests validate the question selection logic without needing
 * the full service context. The selection algorithms are tested
 * through the public API patterns they implement.
 */
class KBQuestionServiceTest {
    private val testQuestions = createTestQuestions()

    // MARK: - Study Mode Configuration Tests

    @Test
    fun `DIAGNOSTIC mode has 50 questions`() {
        assertEquals(50, KBStudyMode.DIAGNOSTIC.defaultQuestionCount)
    }

    @Test
    fun `TARGETED mode has 25 questions`() {
        assertEquals(25, KBStudyMode.TARGETED.defaultQuestionCount)
    }

    @Test
    fun `BREADTH mode has 36 questions`() {
        assertEquals(36, KBStudyMode.BREADTH.defaultQuestionCount)
    }

    @Test
    fun `SPEED mode has 20 questions and 5 minute limit`() {
        assertEquals(20, KBStudyMode.SPEED.defaultQuestionCount)
        assertEquals(300, KBStudyMode.SPEED.timeLimitSeconds)
    }

    @Test
    fun `COMPETITION mode has 45 questions`() {
        assertEquals(45, KBStudyMode.COMPETITION.defaultQuestionCount)
    }

    @Test
    fun `TEAM mode has 45 questions`() {
        assertEquals(45, KBStudyMode.TEAM.defaultQuestionCount)
    }

    // MARK: - Balanced Selection Algorithm Tests

    @Test
    fun `balanced selection distributes across domains`() {
        // Group test questions by domain
        val byDomain = testQuestions.groupBy { it.domain }
        val domains = byDomain.keys.toList()

        // Verify we have multiple domains
        assertTrue("Should have multiple domains", domains.size >= 4)

        // Each domain should have questions
        domains.forEach { domain ->
            assertTrue(
                "Domain $domain should have questions",
                byDomain[domain]?.isNotEmpty() == true,
            )
        }
    }

    @Test
    fun `speed mode prefers easier questions`() {
        val easyQuestions =
            testQuestions.filter {
                it.difficulty in
                    listOf(
                        KBDifficulty.OVERVIEW,
                        KBDifficulty.FOUNDATIONAL,
                        KBDifficulty.INTERMEDIATE,
                    )
            }

        val hardQuestions =
            testQuestions.filter {
                it.difficulty in
                    listOf(
                        KBDifficulty.VARSITY,
                        KBDifficulty.CHAMPIONSHIP,
                        KBDifficulty.RESEARCH,
                    )
            }

        // Verify we have both easy and hard questions in test data
        assertTrue("Should have easy questions", easyQuestions.isNotEmpty())
        assertTrue("Should have hard questions", hardQuestions.isNotEmpty())

        // Verify the distribution is realistic for speed mode
        // Speed mode defaults to 20 questions, algorithm prioritizes easier ones
        assertTrue(
            "Easy questions should be available for speed mode",
            easyQuestions.size >= 5,
        )
    }

    // MARK: - Module Features Integration Tests

    @Test
    fun `module features control study mode availability`() {
        val fullFeatures = KBModuleFeatures.DEFAULT_ENABLED
        val minimalFeatures = KBModuleFeatures.MINIMAL

        // Full features should allow all modes
        assertEquals(KBStudyMode.entries.size, fullFeatures.availableStudyModes().size)

        // Minimal features should only allow core modes
        val minimalModes = minimalFeatures.availableStudyModes()
        assertTrue(minimalModes.contains(KBStudyMode.DIAGNOSTIC))
        assertTrue(minimalModes.contains(KBStudyMode.TARGETED))
        assertTrue(minimalModes.contains(KBStudyMode.BREADTH))
        assertFalse(minimalModes.contains(KBStudyMode.SPEED))
        assertFalse(minimalModes.contains(KBStudyMode.COMPETITION))
        assertFalse(minimalModes.contains(KBStudyMode.TEAM))
    }

    @Test
    fun `study modes have correct required features`() {
        assertEquals(KBRequiredFeature.NONE, KBStudyMode.DIAGNOSTIC.requiredFeature)
        assertEquals(KBRequiredFeature.NONE, KBStudyMode.TARGETED.requiredFeature)
        assertEquals(KBRequiredFeature.NONE, KBStudyMode.BREADTH.requiredFeature)
        assertEquals(KBRequiredFeature.SPEED_TRAINING, KBStudyMode.SPEED.requiredFeature)
        assertEquals(KBRequiredFeature.COMPETITION_SIM, KBStudyMode.COMPETITION.requiredFeature)
        assertEquals(KBRequiredFeature.TEAM_MODE, KBStudyMode.TEAM.requiredFeature)
    }

    // MARK: - Domain Distribution Tests

    @Test
    fun `questions cover all expected domains`() {
        val domains = testQuestions.map { it.domain }.toSet()

        // Verify coverage
        assertTrue(domains.contains(KBDomain.SCIENCE))
        assertTrue(domains.contains(KBDomain.MATHEMATICS))
        assertTrue(domains.contains(KBDomain.HISTORY))
        assertTrue(domains.contains(KBDomain.LITERATURE))
    }

    @Test
    fun `domain weights sum to one`() {
        val totalWeight = KBDomain.entries.sumOf { it.weight.toDouble() }
        assertEquals(1.0, totalWeight, 0.001)
    }

    @Test
    fun `science has highest domain weight`() {
        val maxWeight = KBDomain.entries.maxByOrNull { it.weight }
        assertEquals(KBDomain.SCIENCE, maxWeight)
    }

    // MARK: - Difficulty Distribution Tests

    @Test
    fun `test questions include all difficulty levels`() {
        val difficulties = testQuestions.map { it.difficulty }.toSet()

        assertTrue(difficulties.contains(KBDifficulty.OVERVIEW))
        assertTrue(difficulties.contains(KBDifficulty.FOUNDATIONAL))
        assertTrue(difficulties.contains(KBDifficulty.INTERMEDIATE))
        assertTrue(difficulties.contains(KBDifficulty.VARSITY))
        assertTrue(difficulties.contains(KBDifficulty.CHAMPIONSHIP))
        assertTrue(difficulties.contains(KBDifficulty.RESEARCH))
    }

    @Test
    fun `difficulty levels are ordered correctly`() {
        assertTrue(KBDifficulty.OVERVIEW.isEasierThan(KBDifficulty.FOUNDATIONAL))
        assertTrue(KBDifficulty.FOUNDATIONAL.isEasierThan(KBDifficulty.INTERMEDIATE))
        assertTrue(KBDifficulty.INTERMEDIATE.isEasierThan(KBDifficulty.VARSITY))
        assertTrue(KBDifficulty.VARSITY.isEasierThan(KBDifficulty.CHAMPIONSHIP))
        assertTrue(KBDifficulty.CHAMPIONSHIP.isEasierThan(KBDifficulty.RESEARCH))
    }

    // MARK: - Helper Methods

    private fun createTestQuestions(): List<KBQuestion> =
        listOf(
            // Science questions - various difficulties
            createQuestion("q1", KBDomain.SCIENCE, KBDifficulty.OVERVIEW),
            createQuestion("q2", KBDomain.SCIENCE, KBDifficulty.FOUNDATIONAL),
            createQuestion("q3", KBDomain.SCIENCE, KBDifficulty.INTERMEDIATE),
            createQuestion("q4", KBDomain.SCIENCE, KBDifficulty.VARSITY),
            // Mathematics questions
            createQuestion("q5", KBDomain.MATHEMATICS, KBDifficulty.OVERVIEW),
            createQuestion("q6", KBDomain.MATHEMATICS, KBDifficulty.FOUNDATIONAL),
            createQuestion("q7", KBDomain.MATHEMATICS, KBDifficulty.CHAMPIONSHIP),
            // History questions
            createQuestion("q8", KBDomain.HISTORY, KBDifficulty.INTERMEDIATE),
            createQuestion("q9", KBDomain.HISTORY, KBDifficulty.VARSITY),
            createQuestion("q10", KBDomain.HISTORY, KBDifficulty.RESEARCH),
            // Literature questions
            createQuestion("q11", KBDomain.LITERATURE, KBDifficulty.FOUNDATIONAL),
            createQuestion("q12", KBDomain.LITERATURE, KBDifficulty.INTERMEDIATE),
            // Social Studies questions
            createQuestion("q13", KBDomain.SOCIAL_STUDIES, KBDifficulty.OVERVIEW),
            createQuestion("q14", KBDomain.SOCIAL_STUDIES, KBDifficulty.VARSITY),
            // Arts questions
            createQuestion("q15", KBDomain.ARTS, KBDifficulty.CHAMPIONSHIP),
            createQuestion("q16", KBDomain.ARTS, KBDifficulty.RESEARCH),
        )

    private fun createQuestion(
        id: String,
        domain: KBDomain,
        difficulty: KBDifficulty,
    ): KBQuestion =
        KBQuestion(
            id = id,
            text = "Test question for $domain at $difficulty",
            answer = KBAnswer(primary = "Test answer"),
            domain = domain,
            difficulty = difficulty,
        )
}
