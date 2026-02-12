package com.unamentis.modules.knowledgebowl.core.match

import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date
import javax.inject.Inject
import kotlin.random.Random

/**
 * Manages full Knowledge Bowl match simulation.
 *
 * Handles:
 * - Match setup with questions and teams
 * - Written round flow
 * - Oral round flow with buzz simulation
 * - Score tracking
 * - Match summary generation
 */
@Suppress("TooManyFunctions")
class KBMatchEngine
    @Inject
    constructor() {
        private val mutex = Mutex()

        // Configuration
        private var config: KBMatchConfig? = null

        // Teams
        private val _teams = MutableStateFlow<List<KBTeam>>(emptyList())
        val teams: StateFlow<List<KBTeam>> = _teams.asStateFlow()

        // Match state
        private val _currentPhase = MutableStateFlow<MatchPhase>(MatchPhase.NotStarted)
        val currentPhase: StateFlow<MatchPhase> = _currentPhase.asStateFlow()

        private var startTime: Date? = null
        private val results = mutableListOf<KBMatchQuestionResult>()

        // Question pools
        private var writtenQuestions: List<KBQuestion> = emptyList()
        private var oralQuestions: List<List<KBQuestion>> = emptyList()
        private var currentQuestionIndex: Int = 0
        private var currentOralRound: Int = 0

        // Opponent simulators
        private val opponentSimulators = mutableListOf<KBOpponentSimulator>()

        /**
         * Initialize match with questions and teams.
         *
         * @param config Match configuration
         * @param questions Pool of questions to use
         * @param playerTeamName Name for the player's team
         */
        suspend fun setupMatch(
            config: KBMatchConfig,
            questions: List<KBQuestion>,
            playerTeamName: String,
        ) = mutex.withLock {
            this.config = config

            // Create player team
            val newTeams =
                mutableListOf(
                    KBTeam(name = playerTeamName, isPlayer = true),
                )

            // Add opponent teams
            opponentSimulators.clear()
            config.opponentStrengths.forEachIndexed { index, strength ->
                val teamName = KBOpponentSimulator.getTeamName(index)
                val team = KBTeam(name = teamName, strength = strength)
                newTeams.add(team)

                opponentSimulators.add(
                    KBOpponentSimulator(teamId = team.id, strength = strength),
                )
            }

            _teams.value = newTeams

            // Shuffle and distribute questions
            val shuffled = questions.shuffled()

            // Written questions
            val writtenCount = config.matchFormat.writtenQuestions
            writtenQuestions = shuffled.take(writtenCount)

            // Oral questions (distributed by rounds)
            val oralPerRound = config.matchFormat.questionsPerOralRound
            val totalOral = config.matchFormat.totalOralQuestions
            val oralPool = shuffled.drop(writtenCount).take(totalOral)

            oralQuestions =
                (0 until config.matchFormat.oralRounds).map { round ->
                    val start = round * oralPerRound
                    val end = minOf(start + oralPerRound, oralPool.size)
                    if (start < oralPool.size) {
                        oralPool.subList(start, end)
                    } else {
                        emptyList()
                    }
                }

            // Reset state
            _currentPhase.value = MatchPhase.NotStarted
            currentQuestionIndex = 0
            currentOralRound = 0
            results.clear()
            startTime = null
        }

        /**
         * Start the match.
         */
        suspend fun startMatch() =
            mutex.withLock {
                startTime = Date()
                _currentPhase.value = MatchPhase.WrittenRound
                currentQuestionIndex = 0
            }

        /**
         * Get the current written question.
         */
        fun getCurrentWrittenQuestion(): KBQuestion? {
            if (_currentPhase.value !is MatchPhase.WrittenRound) return null
            return writtenQuestions.getOrNull(currentQuestionIndex)
        }

        /**
         * Submit an answer for the current written question.
         *
         * @param isCorrect Whether the player's answer was correct
         * @param responseTime Time taken to answer in seconds
         */
        suspend fun submitWrittenAnswer(
            isCorrect: Boolean,
            responseTime: Double,
        ) = mutex.withLock {
            val cfg = config ?: return@withLock
            if (_currentPhase.value !is MatchPhase.WrittenRound) return@withLock

            val question = writtenQuestions.getOrNull(currentQuestionIndex) ?: return@withLock
            val playerTeam = _teams.value.find { it.isPlayer } ?: return@withLock

            val points = if (isCorrect) cfg.region.writtenPointValue else 0

            // Record player's result
            val result =
                KBMatchQuestionResult(
                    question = question,
                    phase = _currentPhase.value,
                    answeringTeamId = playerTeam.id,
                    wasCorrect = isCorrect,
                    pointsAwarded = points,
                    responseTime = responseTime,
                )
            results.add(result)

            // Update player score
            if (isCorrect) {
                updateTeamScore(playerTeam.id, writtenPoints = points)
            }

            // Simulate opponent answers
            simulateOpponentWrittenAnswers(question)

            // Advance to next question
            currentQuestionIndex++

            if (currentQuestionIndex >= writtenQuestions.size) {
                _currentPhase.value = MatchPhase.WrittenReview
            }
        }

        /**
         * Start oral rounds after written review.
         */
        suspend fun startOralRounds() =
            mutex.withLock {
                currentOralRound = 0
                currentQuestionIndex = 0
                _currentPhase.value = MatchPhase.OralRound(roundNumber = 1)
            }

        /**
         * Get the current oral question.
         */
        fun getCurrentOralQuestion(): KBQuestion? {
            if (_currentPhase.value !is MatchPhase.OralRound) return null
            return oralQuestions.getOrNull(currentOralRound)?.getOrNull(currentQuestionIndex)
        }

        /**
         * Simulate who buzzes first for an oral question.
         *
         * @return The fastest buzz, or null if no one would buzz
         */
        suspend fun simulateBuzz(): BuzzResult? =
            mutex.withLock {
                val question = getCurrentOralQuestion() ?: return@withLock null

                val buzzAttempts = mutableListOf<BuzzResult>()

                // Opponent buzz times
                opponentSimulators.forEach { simulator ->
                    simulator.attemptBuzz(question)?.let { buzzTime ->
                        buzzAttempts.add(BuzzResult(simulator.teamId, buzzTime))
                    }
                }

                // Player's potential buzz time (simulated as if they would buzz)
                val playerTeam = _teams.value.find { it.isPlayer }
                if (playerTeam != null) {
                    // Player buzz time is random, representing their reaction time
                    val playerBuzzTime = Random.nextDouble(1.0, 4.0)
                    buzzAttempts.add(BuzzResult(playerTeam.id, playerBuzzTime))
                }

                // Return fastest buzz
                buzzAttempts.minByOrNull { it.buzzTime }
            }

        /**
         * Record the result of an oral question.
         *
         * @param answeringTeamId ID of the team that answered (null if no one)
         * @param wasCorrect Whether the answer was correct
         * @param responseTime Time taken to answer
         */
        suspend fun recordOralResult(
            answeringTeamId: String?,
            wasCorrect: Boolean,
            responseTime: Double,
        ) = mutex.withLock {
            val cfg = config ?: return@withLock
            val phase = _currentPhase.value
            if (phase !is MatchPhase.OralRound) return@withLock

            val question = getCurrentOralQuestion() ?: return@withLock

            val points =
                if (wasCorrect) {
                    cfg.region.oralPointValue
                } else {
                    cfg.region.incorrectOralPenalty
                }

            val result =
                KBMatchQuestionResult(
                    question = question,
                    phase = phase,
                    answeringTeamId = answeringTeamId,
                    wasCorrect = wasCorrect,
                    pointsAwarded = points,
                    responseTime = responseTime,
                )
            results.add(result)

            // Update team score
            answeringTeamId?.let { teamId ->
                if (wasCorrect) {
                    updateTeamScore(teamId, oralPoints = points)
                } else {
                    updateTeamScore(teamId, oralPoints = points) // Penalty is negative
                }
            }

            // Advance to next question
            currentQuestionIndex++

            val questionsInRound = oralQuestions.getOrNull(currentOralRound)?.size ?: 0
            if (currentQuestionIndex >= questionsInRound) {
                _currentPhase.value = MatchPhase.OralReview(roundNumber = phase.roundNumber)
            }
        }

        /**
         * Start the next oral round.
         */
        suspend fun startNextOralRound() =
            mutex.withLock {
                currentOralRound++
                currentQuestionIndex = 0

                if (currentOralRound >= oralQuestions.size) {
                    _currentPhase.value = MatchPhase.FinalResults
                } else {
                    _currentPhase.value = MatchPhase.OralRound(roundNumber = currentOralRound + 1)
                }
            }

        /**
         * Get the match summary (only available when match is complete).
         */
        fun getMatchSummary(): KBMatchSummary? {
            if (_currentPhase.value !is MatchPhase.FinalResults) return null
            val cfg = config ?: return null
            val start = startTime ?: return null

            val sortedTeams = _teams.value.sortedByDescending { it.totalScore }
            val playerRank = sortedTeams.indexOfFirst { it.isPlayer } + 1

            val playerTeamId = _teams.value.find { it.isPlayer }?.id
            val playerResults = results.filter { it.answeringTeamId == playerTeamId }

            val writtenResults = playerResults.filter { it.phase is MatchPhase.WrittenRound }
            val oralResults = playerResults.filter { it.phase is MatchPhase.OralRound }

            // Calculate domain strengths
            val domainCorrect = mutableMapOf<KBDomain, Int>()
            val domainTotal = mutableMapOf<KBDomain, Int>()

            playerResults.forEach { result ->
                val domain = result.question.domain
                domainTotal[domain] = (domainTotal[domain] ?: 0) + 1
                if (result.wasCorrect) {
                    domainCorrect[domain] = (domainCorrect[domain] ?: 0) + 1
                }
            }

            val domainStrengths =
                KBDomain.entries.associateWith { domain ->
                    val total = domainTotal[domain] ?: 0
                    val correct = domainCorrect[domain] ?: 0
                    if (total > 0) correct.toDouble() / total else 0.0
                }

            val stats =
                PlayerMatchStats(
                    writtenCorrect = writtenResults.count { it.wasCorrect },
                    writtenTotal = writtenResults.size,
                    oralCorrect = oralResults.count { it.wasCorrect },
                    oralTotal = oralResults.size,
                    averageResponseTime =
                        if (playerResults.isNotEmpty()) {
                            playerResults.map { it.responseTime }.average()
                        } else {
                            0.0
                        },
                    domainsStrength = domainStrengths,
                )

            return KBMatchSummary(
                config = cfg,
                teams = sortedTeams,
                results = results.toList(),
                startTime = start,
                endTime = Date(),
                playerRank = playerRank,
                playerStats = stats,
            )
        }

        /**
         * Get written round progress.
         */
        fun getWrittenProgress(): Pair<Int, Int> {
            return currentQuestionIndex to writtenQuestions.size
        }

        /**
         * Get oral round progress.
         */
        fun getOralProgress(): OralProgress {
            return OralProgress(
                currentRound = currentOralRound + 1,
                totalRounds = oralQuestions.size,
                currentQuestion = currentQuestionIndex,
                questionsPerRound = oralQuestions.firstOrNull()?.size ?: 0,
            )
        }

        /**
         * Get an opponent simulator by team ID.
         */
        fun getOpponentSimulator(teamId: String): KBOpponentSimulator? {
            return opponentSimulators.find { it.teamId == teamId }
        }

        /**
         * Check if a team is the player's team.
         */
        fun isPlayerTeam(teamId: String): Boolean {
            return _teams.value.find { it.id == teamId }?.isPlayer == true
        }

        /**
         * Reset the engine for a new match.
         */
        suspend fun reset() =
            mutex.withLock {
                config = null
                _teams.value = emptyList()
                _currentPhase.value = MatchPhase.NotStarted
                startTime = null
                results.clear()
                writtenQuestions = emptyList()
                oralQuestions = emptyList()
                currentQuestionIndex = 0
                currentOralRound = 0
                opponentSimulators.clear()
            }

        // MARK: - Private Helpers

        private fun updateTeamScore(
            teamId: String,
            writtenPoints: Int = 0,
            oralPoints: Int = 0,
        ) {
            _teams.value =
                _teams.value.map { team ->
                    if (team.id == teamId) {
                        team.copy(
                            writtenScore = team.writtenScore + writtenPoints,
                            oralScore = team.oralScore + oralPoints,
                        )
                    } else {
                        team
                    }
                }
        }

        private fun simulateOpponentWrittenAnswers(question: KBQuestion) {
            val cfg = config ?: return

            opponentSimulators.forEachIndexed { index, simulator ->
                val isCorrect = simulator.answerWrittenQuestion(question)
                if (isCorrect) {
                    updateTeamScore(simulator.teamId, writtenPoints = cfg.region.writtenPointValue)
                }
            }
        }
    }

// MARK: - Region Extensions

/**
 * Points awarded for correct written answers.
 */
val KBRegion.writtenPointValue: Int
    get() =
        when (this) {
            KBRegion.COLORADO, KBRegion.COLORADO_SPRINGS -> 1
            KBRegion.MINNESOTA -> 1
            KBRegion.WASHINGTON -> 1
        }

/**
 * Points awarded for correct oral answers.
 */
val KBRegion.oralPointValue: Int
    get() =
        when (this) {
            KBRegion.COLORADO, KBRegion.COLORADO_SPRINGS -> 10
            KBRegion.MINNESOTA -> 10
            KBRegion.WASHINGTON -> 10
        }

/**
 * Penalty for incorrect oral answers.
 */
val KBRegion.incorrectOralPenalty: Int
    get() =
        when (this) {
            KBRegion.COLORADO, KBRegion.COLORADO_SPRINGS -> -5
            KBRegion.MINNESOTA -> 0
            KBRegion.WASHINGTON -> 0
        }
