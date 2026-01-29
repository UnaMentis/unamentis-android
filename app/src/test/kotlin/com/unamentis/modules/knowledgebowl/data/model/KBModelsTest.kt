package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Knowledge Bowl data models.
 *
 * Tests serialization, computed properties, and factory methods.
 * Comprehensive test coverage requires testing all model variations.
 */
@Suppress("LargeClass")
class KBModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    // MARK: - KBDomain Tests

    @Test
    fun `KBDomain has correct display names`() {
        assertEquals("Science", KBDomain.SCIENCE.displayName)
        assertEquals("Mathematics", KBDomain.MATHEMATICS.displayName)
        assertEquals("Literature", KBDomain.LITERATURE.displayName)
        assertEquals("History", KBDomain.HISTORY.displayName)
    }

    @Test
    fun `KBDomain weights sum to 1`() {
        val totalWeight = KBDomain.entries.sumOf { it.weight.toDouble() }
        assertEquals(1.0, totalWeight, 0.001)
    }

    @Test
    fun `KBDomain serializes correctly`() {
        val encoded = json.encodeToString(KBDomain.SCIENCE)
        assertEquals("\"science\"", encoded)

        val decoded = json.decodeFromString<KBDomain>("\"science\"")
        assertEquals(KBDomain.SCIENCE, decoded)
    }

    // MARK: - KBDifficulty Tests

    @Test
    fun `KBDifficulty levels are ordered correctly`() {
        assertTrue(KBDifficulty.OVERVIEW.isEasierThan(KBDifficulty.FOUNDATIONAL))
        assertTrue(KBDifficulty.VARSITY.isHarderThan(KBDifficulty.INTERMEDIATE))
        assertTrue(KBDifficulty.CHAMPIONSHIP.isHarderThan(KBDifficulty.VARSITY))
        assertTrue(KBDifficulty.RESEARCH.isHarderThan(KBDifficulty.CHAMPIONSHIP))
    }

    @Test
    fun `KBDifficulty fromLevel returns correct difficulty`() {
        assertEquals(KBDifficulty.OVERVIEW, KBDifficulty.fromLevel(1))
        assertEquals(KBDifficulty.VARSITY, KBDifficulty.fromLevel(4))
        assertEquals(KBDifficulty.RESEARCH, KBDifficulty.fromLevel(6))
        assertNull(KBDifficulty.fromLevel(7))
        assertNull(KBDifficulty.fromLevel(0))
    }

    @Test
    fun `KBDifficulty default is VARSITY`() {
        assertEquals(KBDifficulty.VARSITY, KBDifficulty.DEFAULT)
    }

    @Test
    fun `KBDifficulty serializes correctly`() {
        val encoded = json.encodeToString(KBDifficulty.VARSITY)
        assertEquals("\"varsity\"", encoded)

        val decoded = json.decodeFromString<KBDifficulty>("\"varsity\"")
        assertEquals(KBDifficulty.VARSITY, decoded)
    }

    // MARK: - KBGradeLevel Tests

    @Test
    fun `KBGradeLevel grade ranges are correct`() {
        assertEquals(6..8, KBGradeLevel.MIDDLE_SCHOOL.gradeRange)
        assertEquals(9..12, KBGradeLevel.HIGH_SCHOOL.gradeRange)
        assertEquals(11..14, KBGradeLevel.ADVANCED.gradeRange)
    }

    @Test
    fun `KBGradeLevel serializes correctly`() {
        val encoded = json.encodeToString(KBGradeLevel.HIGH_SCHOOL)
        assertEquals("\"highSchool\"", encoded)

        val decoded = json.decodeFromString<KBGradeLevel>("\"highSchool\"")
        assertEquals(KBGradeLevel.HIGH_SCHOOL, decoded)
    }

    // MARK: - KBRoundType Tests

    @Test
    fun `KBRoundType has correct display names`() {
        assertEquals("Written Round", KBRoundType.WRITTEN.displayName)
        assertEquals("Oral Round", KBRoundType.ORAL.displayName)
    }

    @Test
    fun `KBRoundType serializes correctly`() {
        val encoded = json.encodeToString(KBRoundType.WRITTEN)
        assertEquals("\"written\"", encoded)

        val decoded = json.decodeFromString<KBRoundType>("\"oral\"")
        assertEquals(KBRoundType.ORAL, decoded)
    }

    // MARK: - KBAnswer Tests

    @Test
    fun `KBAnswer allValidAnswers includes primary and acceptable`() {
        val answer =
            KBAnswer(
                primary = "United States",
                acceptable = listOf("USA", "US", "America"),
            )

        assertEquals(4, answer.allValidAnswers.size)
        assertTrue(answer.allValidAnswers.contains("United States"))
        assertTrue(answer.allValidAnswers.contains("USA"))
        assertTrue(answer.allValidAnswers.contains("US"))
        assertTrue(answer.allValidAnswers.contains("America"))
    }

    @Test
    fun `KBAnswer with no alternatives has single valid answer`() {
        val answer = KBAnswer(primary = "42")

        assertEquals(1, answer.allValidAnswers.size)
        assertEquals("42", answer.allValidAnswers.first())
    }

    @Test
    fun `KBAnswer serializes correctly`() {
        val answer =
            KBAnswer(
                primary = "Einstein",
                acceptable = listOf("Albert Einstein"),
                answerType = KBAnswerType.PERSON,
            )

        val encoded = json.encodeToString(answer)
        assertTrue(encoded.contains("\"primary\":\"Einstein\""))
        assertTrue(encoded.contains("\"answerType\":\"person\""))

        val decoded = json.decodeFromString<KBAnswer>(encoded)
        assertEquals("Einstein", decoded.primary)
        assertEquals(KBAnswerType.PERSON, decoded.answerType)
    }

    // MARK: - KBQuestion Tests

    @Test
    fun `KBQuestion serializes correctly`() {
        val question =
            KBQuestion(
                id = "test-q-1",
                text = "What is the capital of France?",
                answer = KBAnswer(primary = "Paris"),
                domain = KBDomain.SOCIAL_STUDIES,
                difficulty = KBDifficulty.FOUNDATIONAL,
            )

        val encoded = json.encodeToString(question)
        assertTrue(encoded.contains("\"id\":\"test-q-1\""))
        assertTrue(encoded.contains("\"domain\":\"socialStudies\""))

        val decoded = json.decodeFromString<KBQuestion>(encoded)
        assertEquals("test-q-1", decoded.id)
        assertEquals("What is the capital of France?", decoded.text)
        assertEquals(KBDomain.SOCIAL_STUDIES, decoded.domain)
    }

    @Test
    fun `KBQuestion defaults are correct`() {
        val question =
            KBQuestion(
                id = "test-q-2",
                text = "Test question",
                answer = KBAnswer(primary = "Test answer"),
                domain = KBDomain.SCIENCE,
            )

        assertEquals(KBDifficulty.VARSITY, question.difficulty)
        assertEquals(KBGradeLevel.HIGH_SCHOOL, question.gradeLevel)
        assertTrue(question.suitability.forWritten)
        assertTrue(question.suitability.forOral)
    }

    @Test
    fun `KBQuestionBundle serialization roundtrip works`() {
        val bundle =
            KBQuestionBundle(
                version = "1.0.0",
                questions =
                    listOf(
                        KBQuestion(
                            id = "q1",
                            text = "Question 1",
                            answer = KBAnswer(primary = "Answer 1"),
                            domain = KBDomain.SCIENCE,
                        ),
                    ),
            )

        val encoded = json.encodeToString(bundle)
        val decoded = json.decodeFromString<KBQuestionBundle>(encoded)

        assertEquals("1.0.0", decoded.version)
        assertEquals(1, decoded.questions.size)
        assertEquals("q1", decoded.questions[0].id)
        assertEquals("Question 1", decoded.questions[0].text)
    }

    // MARK: - KBRegion Tests

    @Test
    fun `KBRegion has correct display names`() {
        assertEquals("Colorado", KBRegion.COLORADO.displayName)
        assertEquals("Colorado Springs", KBRegion.COLORADO_SPRINGS.displayName)
        assertEquals("Minnesota", KBRegion.MINNESOTA.displayName)
        assertEquals("Washington", KBRegion.WASHINGTON.displayName)
    }

    @Test
    fun `KBRegion has correct abbreviations`() {
        assertEquals("CO", KBRegion.COLORADO.abbreviation)
        assertEquals("CO", KBRegion.COLORADO_SPRINGS.abbreviation)
        assertEquals("MN", KBRegion.MINNESOTA.abbreviation)
        assertEquals("WA", KBRegion.WASHINGTON.abbreviation)
    }

    @Test
    fun `KBRegion default is COLORADO`() {
        assertEquals(KBRegion.COLORADO, KBRegion.DEFAULT)
    }

    @Test
    fun `KBRegion serializes correctly`() {
        val encoded = json.encodeToString(KBRegion.MINNESOTA)
        assertEquals("\"minnesota\"", encoded)

        val decoded = json.decodeFromString<KBRegion>("\"minnesota\"")
        assertEquals(KBRegion.MINNESOTA, decoded)
    }

    // MARK: - KBRegionalConfig Tests

    @Test
    fun `KBRegionalConfig Colorado has correct rules`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)

        assertEquals(60, config.writtenQuestionCount)
        assertEquals(900, config.writtenTimeLimitSeconds)
        assertEquals(1, config.writtenPointsPerCorrect)
        assertFalse(config.verbalConferringAllowed)
        assertTrue(config.handSignalsAllowed)
        assertFalse(config.sosBonus)
    }

    @Test
    fun `KBRegionalConfig Minnesota has correct rules`() {
        val config = KBRegionalConfig.forRegion(KBRegion.MINNESOTA)

        assertEquals(60, config.writtenQuestionCount)
        assertEquals(2, config.writtenPointsPerCorrect)
        assertTrue(config.verbalConferringAllowed)
        assertTrue(config.sosBonus)
    }

    @Test
    fun `KBRegionalConfig Washington has correct rules`() {
        val config = KBRegionalConfig.forRegion(KBRegion.WASHINGTON)

        assertEquals(50, config.writtenQuestionCount)
        assertEquals(2700, config.writtenTimeLimitSeconds)
        assertEquals(2, config.writtenPointsPerCorrect)
        assertTrue(config.verbalConferringAllowed)
        assertFalse(config.sosBonus)
    }

    @Test
    fun `KBRegionalConfig display properties are formatted correctly`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)

        assertEquals("15 min", config.writtenTimeLimitDisplay)
        assertEquals("15 sec", config.conferenceTimeDisplay)
        assertEquals("1 pt", config.writtenPointsDisplay)
        assertEquals("5 pts", config.oralPointsDisplay)
        assertEquals("Hand signals only (no verbal)", config.conferringRuleDescription)
    }

    @Test
    fun `KBRegionalConfig Minnesota conferring rule is correct`() {
        val config = KBRegionalConfig.forRegion(KBRegion.MINNESOTA)
        assertEquals("Verbal discussion allowed", config.conferringRuleDescription)
    }

    @Test
    fun `KBRegionalConfig keyDifferences identifies differences`() {
        val colorado = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        val minnesota = KBRegionalConfig.forRegion(KBRegion.MINNESOTA)

        val differences = colorado.keyDifferences(minnesota)

        assertTrue(differences.any { it.contains("points") })
        assertTrue(differences.any { it.contains("Conferring") })
        assertTrue(differences.any { it.contains("SOS") })
    }

    @Test
    fun `KBRegionalConfig serializes correctly`() {
        val config = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        val encoded = json.encodeToString(config)

        assertTrue(encoded.contains("\"region\":\"colorado\""))
        assertTrue(encoded.contains("\"written_question_count\":60"))

        val decoded = json.decodeFromString<KBRegionalConfig>(encoded)
        assertEquals(KBRegion.COLORADO, decoded.region)
        assertEquals(60, decoded.writtenQuestionCount)
    }

    // MARK: - KBSessionConfig Tests

    @Test
    fun `KBSessionConfig writtenPractice uses regional defaults`() {
        val config = KBSessionConfig.writtenPractice(KBRegion.COLORADO)

        assertEquals(KBRegion.COLORADO, config.region)
        assertEquals(KBRoundType.WRITTEN, config.roundType)
        assertEquals(60, config.questionCount)
        assertEquals(900, config.timeLimitSeconds)
    }

    @Test
    fun `KBSessionConfig writtenPractice allows overrides`() {
        val config =
            KBSessionConfig.writtenPractice(
                region = KBRegion.COLORADO,
                questionCount = 30,
                timeLimitSeconds = 600,
            )

        assertEquals(30, config.questionCount)
        assertEquals(600, config.timeLimitSeconds)
    }

    @Test
    fun `KBSessionConfig oralPractice has no time limit`() {
        val config = KBSessionConfig.oralPractice(KBRegion.MINNESOTA)

        assertEquals(KBRoundType.ORAL, config.roundType)
        assertNull(config.timeLimitSeconds)
    }

    @Test
    fun `KBSessionConfig quickPractice creates short sessions`() {
        val writtenConfig =
            KBSessionConfig.quickPractice(
                region = KBRegion.COLORADO,
                roundType = KBRoundType.WRITTEN,
                questionCount = 10,
            )

        assertEquals(10, writtenConfig.questionCount)
        assertEquals(150, writtenConfig.timeLimitSeconds)

        val oralConfig =
            KBSessionConfig.quickPractice(
                region = KBRegion.COLORADO,
                roundType = KBRoundType.ORAL,
                questionCount = 10,
            )

        assertEquals(10, oralConfig.questionCount)
        assertNull(oralConfig.timeLimitSeconds)
    }

    @Test
    fun `KBSessionConfig serializes correctly`() {
        val config = KBSessionConfig.writtenPractice(KBRegion.MINNESOTA)
        val encoded = json.encodeToString(config)

        assertTrue(encoded.contains("\"region\":\"minnesota\""))
        assertTrue(encoded.contains("\"round_type\":\"written\""))

        val decoded = json.decodeFromString<KBSessionConfig>(encoded)
        assertEquals(KBRegion.MINNESOTA, decoded.region)
        assertEquals(KBRoundType.WRITTEN, decoded.roundType)
    }

    // MARK: - KBQuestionAttempt Tests

    @Test
    fun `KBQuestionAttempt serializes correctly`() {
        val attempt =
            KBQuestionAttempt(
                id = "attempt-1",
                questionId = "q-1",
                domain = KBDomain.SCIENCE,
                timestamp = System.currentTimeMillis(),
                userAnswer = "Paris",
                responseTimeSeconds = 5.2f,
                wasCorrect = true,
                pointsEarned = 1,
                roundType = KBRoundType.WRITTEN,
            )

        val encoded = json.encodeToString(attempt)
        assertTrue(encoded.contains("\"id\":\"attempt-1\""))
        assertTrue(encoded.contains("\"domain\":\"science\""))
        assertTrue(encoded.contains("\"was_correct\":true"))

        val decoded = json.decodeFromString<KBQuestionAttempt>(encoded)
        assertEquals("attempt-1", decoded.id)
        assertTrue(decoded.wasCorrect)
    }

    @Test
    fun `KBQuestionAttempt generateId creates unique IDs`() {
        val id1 = KBQuestionAttempt.generateId()
        val id2 = KBQuestionAttempt.generateId()

        assertNotNull(id1)
        assertNotNull(id2)
        assertTrue(id1 != id2)
    }

    // MARK: - KBSession Tests

    @Test
    fun `KBSession accuracy is calculated correctly`() {
        val session = createSession()
        session.attempts.add(createAttempt(wasCorrect = true))
        session.attempts.add(createAttempt(wasCorrect = true))
        session.attempts.add(createAttempt(wasCorrect = true))
        session.attempts.add(createAttempt(wasCorrect = false))

        assertEquals(0.75f, session.accuracy, 0.001f)
        assertEquals(3, session.correctCount)
    }

    @Test
    fun `KBSession progress is calculated correctly`() {
        val config =
            KBSessionConfig.writtenPractice(
                KBRegion.COLORADO,
                questionCount = 10,
            )
        val session = KBSession(id = "test", config = config)
        session.attempts.add(createAttempt())
        session.attempts.add(createAttempt())
        session.attempts.add(createAttempt())

        assertEquals(0.3f, session.progress, 0.001f)
    }

    @Test
    fun `KBSession totalPoints is calculated correctly`() {
        val session = createSession()
        session.attempts.add(createAttempt(wasCorrect = true, pointsEarned = 5))
        session.attempts.add(createAttempt(wasCorrect = true, pointsEarned = 5))
        session.attempts.add(createAttempt(wasCorrect = false, pointsEarned = 0))

        assertEquals(10, session.totalPoints)
    }

    @Test
    fun `KBSession averageResponseTimeSeconds is calculated correctly`() {
        val session = createSession()
        session.attempts.add(createAttempt(responseTimeSeconds = 3.0f))
        session.attempts.add(createAttempt(responseTimeSeconds = 5.0f))
        session.attempts.add(createAttempt(responseTimeSeconds = 7.0f))

        assertEquals(5.0f, session.averageResponseTimeSeconds, 0.001f)
    }

    @Test
    fun `KBSession durationSeconds is calculated correctly`() {
        val startTime = 1000000L
        val endTime = 1005000L

        val session =
            KBSession(
                id = "test-session",
                config = KBSessionConfig.writtenPractice(KBRegion.COLORADO),
                startTimeMillis = startTime,
                endTimeMillis = endTime,
            )

        assertEquals(5.0f, session.durationSeconds, 0.001f)
    }

    @Test
    fun `KBSession performanceByDomain groups correctly`() {
        val session = createSession()
        session.attempts.add(createAttempt(domain = KBDomain.SCIENCE, wasCorrect = true))
        session.attempts.add(createAttempt(domain = KBDomain.SCIENCE, wasCorrect = false))
        session.attempts.add(createAttempt(domain = KBDomain.MATHEMATICS, wasCorrect = true))

        val performance = session.performanceByDomain

        assertEquals(2, performance[KBDomain.SCIENCE]?.total)
        assertEquals(1, performance[KBDomain.SCIENCE]?.correct)
        assertEquals(1, performance[KBDomain.MATHEMATICS]?.total)
        assertEquals(1, performance[KBDomain.MATHEMATICS]?.correct)
    }

    @Test
    fun `KBSession serializes correctly`() {
        val session = createSession()
        val encoded = json.encodeToString(session)

        assertTrue(encoded.contains("\"id\""))
        assertTrue(encoded.contains("\"config\""))

        val decoded = json.decodeFromString<KBSession>(encoded)
        assertNotNull(decoded.id)
    }

    @Test
    fun `KBSession create factory method works`() {
        val config = KBSessionConfig.writtenPractice(KBRegion.MINNESOTA)
        val session = KBSession.create(config)

        assertNotNull(session.id)
        assertEquals(config, session.config)
        assertTrue(session.attempts.isEmpty())
        assertFalse(session.isComplete)
    }

    // MARK: - KBTimerState Tests

    @Test
    fun `KBTimerState from returns correct states`() {
        assertEquals(KBTimerState.NORMAL, KBTimerState.from(0.8f))
        assertEquals(KBTimerState.NORMAL, KBTimerState.from(0.6f))
        assertEquals(KBTimerState.FOCUSED, KBTimerState.from(0.5f))
        assertEquals(KBTimerState.FOCUSED, KBTimerState.from(0.3f))
        assertEquals(KBTimerState.URGENT, KBTimerState.from(0.2f))
        assertEquals(KBTimerState.URGENT, KBTimerState.from(0.1f))
        assertEquals(KBTimerState.CRITICAL, KBTimerState.from(0.05f))
    }

    @Test
    fun `KBTimerState pulseSpeedSeconds is correct`() {
        assertNull(KBTimerState.NORMAL.pulseSpeedSeconds)
        assertNull(KBTimerState.FOCUSED.pulseSpeedSeconds)
        assertEquals(1.0f, KBTimerState.URGENT.pulseSpeedSeconds)
        assertEquals(0.3f, KBTimerState.CRITICAL.pulseSpeedSeconds)
    }

    // MARK: - KBSessionSummary Tests

    @Test
    fun `KBSessionSummary accuracyPercent formats correctly`() {
        val summary = createSummary(accuracy = 0.85f)
        assertEquals("85%", summary.accuracyPercent)
    }

    @Test
    fun `KBSessionSummary durationDisplay formats correctly`() {
        val summary = createSummary(durationSeconds = 330f)
        assertEquals("5:30", summary.durationDisplay)
    }

    @Test
    fun `KBSessionSummary averageResponseTimeDisplay formats correctly`() {
        val summary = createSummary(averageResponseTimeSeconds = 4.25f)
        assertEquals("4.3s", summary.averageResponseTimeDisplay)
    }

    @Test
    fun `KBSessionSummary from creates summary from session`() {
        val session =
            KBSession(
                id = "test-session",
                config = KBSessionConfig.writtenPractice(KBRegion.COLORADO),
                startTimeMillis = 1000L,
                endTimeMillis = 61000L,
            )
        session.attempts.add(createAttempt(wasCorrect = true, pointsEarned = 1, responseTimeSeconds = 3.0f))
        session.attempts.add(createAttempt(wasCorrect = true, pointsEarned = 1, responseTimeSeconds = 5.0f))
        session.attempts.add(createAttempt(wasCorrect = false, pointsEarned = 0, responseTimeSeconds = 7.0f))

        val summary = KBSessionSummary.from(session)

        assertEquals(session.id, summary.sessionId)
        assertEquals(3, summary.totalQuestions)
        assertEquals(2, summary.totalCorrect)
        assertEquals(2, summary.totalPoints)
        assertEquals(5.0f, summary.averageResponseTimeSeconds, 0.001f)
        assertEquals(60.0f, summary.durationSeconds, 0.001f)
    }

    @Test
    fun `KBSessionSummary serializes correctly`() {
        val summary = createSummary()
        val encoded = json.encodeToString(summary)

        assertTrue(encoded.contains("\"session_id\""))
        assertTrue(encoded.contains("\"accuracy\""))

        val decoded = json.decodeFromString<KBSessionSummary>(encoded)
        assertEquals(summary.sessionId, decoded.sessionId)
    }

    // MARK: - DomainPerformance Tests

    @Test
    fun `DomainPerformance accuracy is calculated correctly`() {
        val performance =
            DomainPerformance(
                domain = KBDomain.SCIENCE,
                correct = 3,
                total = 4,
                averageTimeSeconds = 5.0f,
            )

        assertEquals(0.75f, performance.accuracy, 0.001f)
    }

    @Test
    fun `DomainPerformance serializes correctly`() {
        val performance =
            DomainPerformance(
                domain = KBDomain.MATHEMATICS,
                correct = 5,
                total = 8,
                averageTimeSeconds = 4.5f,
            )

        val encoded = json.encodeToString(performance)
        assertTrue(encoded.contains("\"domain\":\"mathematics\""))
        assertTrue(encoded.contains("\"correct\":5"))

        val decoded = json.decodeFromString<DomainPerformance>(encoded)
        assertEquals(KBDomain.MATHEMATICS, decoded.domain)
        assertEquals(5, decoded.correct)
    }

    // MARK: - KBStudyMode Feature Flag Tests

    @Test
    fun `KBStudyMode requiredFeature returns correct values`() {
        assertEquals(KBRequiredFeature.NONE, KBStudyMode.DIAGNOSTIC.requiredFeature)
        assertEquals(KBRequiredFeature.NONE, KBStudyMode.TARGETED.requiredFeature)
        assertEquals(KBRequiredFeature.NONE, KBStudyMode.BREADTH.requiredFeature)
        assertEquals(KBRequiredFeature.SPEED_TRAINING, KBStudyMode.SPEED.requiredFeature)
        assertEquals(KBRequiredFeature.COMPETITION_SIM, KBStudyMode.COMPETITION.requiredFeature)
        assertEquals(KBRequiredFeature.TEAM_MODE, KBStudyMode.TEAM.requiredFeature)
    }

    @Test
    fun `KBStudyMode iconName returns non-empty values for all modes`() {
        KBStudyMode.entries.forEach { mode ->
            assertTrue(
                "Mode ${mode.name} should have an icon",
                mode.iconName.isNotEmpty(),
            )
        }
    }

    @Test
    fun `KBRequiredFeature has all expected values`() {
        assertEquals(4, KBRequiredFeature.entries.size)
        assertTrue(KBRequiredFeature.entries.contains(KBRequiredFeature.NONE))
        assertTrue(KBRequiredFeature.entries.contains(KBRequiredFeature.TEAM_MODE))
        assertTrue(KBRequiredFeature.entries.contains(KBRequiredFeature.SPEED_TRAINING))
        assertTrue(KBRequiredFeature.entries.contains(KBRequiredFeature.COMPETITION_SIM))
    }

    // MARK: - KBModuleFeatures Tests

    @Test
    fun `KBModuleFeatures DEFAULT_ENABLED has all features enabled`() {
        val features = KBModuleFeatures.DEFAULT_ENABLED

        assertTrue(features.supportsTeamMode)
        assertTrue(features.supportsSpeedTraining)
        assertTrue(features.supportsCompetitionSim)
    }

    @Test
    fun `KBModuleFeatures MINIMAL has all special features disabled`() {
        val features = KBModuleFeatures.MINIMAL

        assertFalse(features.supportsTeamMode)
        assertFalse(features.supportsSpeedTraining)
        assertFalse(features.supportsCompetitionSim)
    }

    @Test
    fun `KBModuleFeatures isAvailable returns correct values`() {
        val features =
            KBModuleFeatures(
                supportsTeamMode = true,
                supportsSpeedTraining = false,
                supportsCompetitionSim = true,
            )

        assertTrue(features.isAvailable(KBRequiredFeature.NONE))
        assertTrue(features.isAvailable(KBRequiredFeature.TEAM_MODE))
        assertFalse(features.isAvailable(KBRequiredFeature.SPEED_TRAINING))
        assertTrue(features.isAvailable(KBRequiredFeature.COMPETITION_SIM))
    }

    @Test
    fun `KBModuleFeatures availableStudyModes returns correct modes`() {
        val features =
            KBModuleFeatures(
                supportsTeamMode = false,
                supportsSpeedTraining = true,
                supportsCompetitionSim = false,
            )

        val available = features.availableStudyModes()

        // Core modes should always be available
        assertTrue(available.contains(KBStudyMode.DIAGNOSTIC))
        assertTrue(available.contains(KBStudyMode.TARGETED))
        assertTrue(available.contains(KBStudyMode.BREADTH))

        // Speed should be available
        assertTrue(available.contains(KBStudyMode.SPEED))

        // Team and Competition should not be available
        assertFalse(available.contains(KBStudyMode.TEAM))
        assertFalse(available.contains(KBStudyMode.COMPETITION))
    }

    @Test
    fun `KBModuleFeatures restrictedStudyModes returns correct modes`() {
        val features =
            KBModuleFeatures(
                supportsTeamMode = false,
                supportsSpeedTraining = true,
                supportsCompetitionSim = false,
            )

        val restricted = features.restrictedStudyModes()

        assertEquals(2, restricted.size)
        assertTrue(restricted.contains(KBStudyMode.TEAM))
        assertTrue(restricted.contains(KBStudyMode.COMPETITION))
    }

    @Test
    fun `KBModuleFeatures serializes correctly`() {
        // Use explicit values that differ from defaults to ensure they're serialized
        val features =
            KBModuleFeatures(
                supportsTeamMode = false,
                supportsSpeedTraining = false,
                supportsCompetitionSim = false,
            )

        val encoded = json.encodeToString(features)

        // Verify the encoded JSON contains the expected keys
        assertTrue(
            "Encoded should contain supports_team_mode, got: $encoded",
            encoded.contains("supports_team_mode"),
        )
        assertTrue(
            "Encoded should contain supports_speed_training, got: $encoded",
            encoded.contains("supports_speed_training"),
        )
        assertTrue(
            "Encoded should contain supports_competition_sim, got: $encoded",
            encoded.contains("supports_competition_sim"),
        )

        // Verify roundtrip works
        val decoded = json.decodeFromString<KBModuleFeatures>(encoded)
        assertEquals(false, decoded.supportsTeamMode)
        assertEquals(false, decoded.supportsSpeedTraining)
        assertEquals(false, decoded.supportsCompetitionSim)
    }

    @Test
    fun `KBModuleFeatures deserialization with missing fields uses defaults`() {
        // Server may omit fields that are true (defaults)
        val partialJson = """{"supports_speed_training":false}"""

        val decoded = json.decodeFromString<KBModuleFeatures>(partialJson)

        // Missing fields should use defaults (true)
        assertEquals(true, decoded.supportsTeamMode)
        assertEquals(false, decoded.supportsSpeedTraining)
        assertEquals(true, decoded.supportsCompetitionSim)
    }

    @Test
    fun `KBModuleFeatures with all features enabled shows all study modes`() {
        val features = KBModuleFeatures.DEFAULT_ENABLED
        val available = features.availableStudyModes()

        assertEquals(KBStudyMode.entries.size, available.size)
        KBStudyMode.entries.forEach { mode ->
            assertTrue(
                "Mode ${mode.name} should be available",
                available.contains(mode),
            )
        }
    }

    // MARK: - Helper Functions

    private fun createAttempt(
        wasCorrect: Boolean = true,
        pointsEarned: Int = 1,
        responseTimeSeconds: Float = 5.0f,
        domain: KBDomain = KBDomain.SCIENCE,
    ): KBQuestionAttempt {
        return KBQuestionAttempt(
            id = KBQuestionAttempt.generateId(),
            questionId = "q-1",
            domain = domain,
            timestamp = System.currentTimeMillis(),
            userAnswer = "Test",
            responseTimeSeconds = responseTimeSeconds,
            wasCorrect = wasCorrect,
            pointsEarned = pointsEarned,
            roundType = KBRoundType.WRITTEN,
        )
    }

    private fun createSession(): KBSession {
        return KBSession(
            id = "test-session",
            config = KBSessionConfig.writtenPractice(KBRegion.COLORADO),
        )
    }

    private fun createSummary(
        accuracy: Float = 0.8f,
        durationSeconds: Float = 300f,
        averageResponseTimeSeconds: Float = 5.0f,
    ): KBSessionSummary {
        return KBSessionSummary(
            sessionId = "test-session",
            roundType = KBRoundType.WRITTEN,
            region = KBRegion.COLORADO,
            totalQuestions = 10,
            totalCorrect = 8,
            totalPoints = 8,
            accuracy = accuracy,
            averageResponseTimeSeconds = averageResponseTimeSeconds,
            durationSeconds = durationSeconds,
            completedAtMillis = System.currentTimeMillis(),
        )
    }
}
