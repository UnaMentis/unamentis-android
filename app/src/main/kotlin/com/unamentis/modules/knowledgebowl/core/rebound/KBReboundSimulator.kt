package com.unamentis.modules.knowledgebowl.core.rebound

import android.util.Log
import com.unamentis.R
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Simulates opponent behavior and manages rebound training sessions.
 *
 * Rebound training teaches students to capitalize on opponent mistakes
 * during oral rounds. When an opponent buzzes and answers incorrectly,
 * it creates a "rebound opportunity" where another team can attempt
 * to answer.
 */
@Singleton
class KBReboundSimulator
    @Inject
    constructor() {
        companion object {
            private const val TAG = "KBReboundSimulator"

            /** Generate practice scenarios for demonstration */
            fun generatePracticeScenarios(): List<Pair<Int, Int>> =
                listOf(
                    R.string.kb_practice_scenario_1_situation to R.string.kb_practice_scenario_1_tip,
                    R.string.kb_practice_scenario_2_situation to R.string.kb_practice_scenario_2_tip,
                    R.string.kb_practice_scenario_3_situation to R.string.kb_practice_scenario_3_tip,
                    R.string.kb_practice_scenario_4_situation to R.string.kb_practice_scenario_4_tip,
                    R.string.kb_practice_scenario_5_situation to R.string.kb_practice_scenario_5_tip,
                )

            /** Wrong answers by domain for simulation */
            private val WRONG_ANSWERS: Map<KBDomain, List<String>> =
                mapOf(
                    KBDomain.SCIENCE to listOf("Carbon dioxide", "Nitrogen", "Helium", "The sun"),
                    KBDomain.MATHEMATICS to listOf("42", "π", "Zero", "Infinity"),
                    KBDomain.LITERATURE to listOf("Shakespeare", "Dickens", "Hemingway", "Twain"),
                    KBDomain.HISTORY to listOf("1776", "1492", "1066", "1945"),
                    KBDomain.SOCIAL_STUDIES to listOf("Washington D.C.", "Paris", "London", "Beijing"),
                    KBDomain.ARTS to listOf("Picasso", "Van Gogh", "Da Vinci", "Monet"),
                    KBDomain.CURRENT_EVENTS to listOf("United Nations", "NATO", "EU", "WHO"),
                    KBDomain.LANGUAGE to listOf("Latin", "Greek", "Sanskrit", "Hebrew"),
                    KBDomain.TECHNOLOGY to listOf("Silicon", "Binary", "Algorithm", "Protocol"),
                    KBDomain.POP_CULTURE to listOf("The Beatles", "Elvis", "Michael Jackson", "Madonna"),
                    KBDomain.RELIGION_PHILOSOPHY to listOf("Aristotle", "Plato", "Socrates", "Confucius"),
                    KBDomain.MISCELLANEOUS to listOf("Blue", "Seven", "Tuesday", "North"),
                )
        }

        private val mutex = Mutex()

        // Session state
        private var config: KBReboundConfig? = null
        private val attempts = mutableListOf<KBReboundAttempt>()
        private var currentScenario: KBReboundScenario? = null
        private var sessionStartTime: Long = 0L
        private var isActive = false
        private var currentDifficultyModifier = 1.0

        // Opponent simulation state
        private val opponentNames = listOf("Team Alpha", "Team Beta", "Team Gamma")
        private var currentOpponentIndex = 0

        /**
         * Start a new rebound training session.
         */
        suspend fun startSession(config: KBReboundConfig) =
            mutex.withLock {
                this.config = config
                attempts.clear()
                currentScenario = null
                sessionStartTime = System.currentTimeMillis()
                isActive = true
                currentDifficultyModifier = 1.0
                currentOpponentIndex = Random.nextInt(opponentNames.size)
                Log.i(TAG, "Rebound training session started for ${config.region.displayName}")
            }

        /**
         * End the session and return statistics.
         */
        suspend fun endSession(): KBReboundStats =
            mutex.withLock {
                isActive = false
                val stats = KBReboundStats.fromAttempts(attempts)
                val accuracy = (stats.reboundAccuracy * 100).toInt()
                Log.i(TAG, "Rebound session ended: ${stats.totalScenarios} scenarios, $accuracy% rebound accuracy")
                stats
            }

        /**
         * Check if session is active.
         */
        fun isSessionActive(): Boolean = isActive

        /**
         * Get current opponent team name.
         */
        fun getCurrentOpponent(): String = opponentNames[currentOpponentIndex]

        /**
         * Rotate to next opponent.
         */
        suspend fun rotateOpponent() =
            mutex.withLock {
                currentOpponentIndex = (currentOpponentIndex + 1) % opponentNames.size
            }

        /**
         * Generate a rebound scenario for a question.
         */
        suspend fun generateScenario(question: KBQuestion): KBReboundScenario =
            mutex.withLock {
                val cfg = config ?: KBReboundConfig.forRegion(KBRegion.DEFAULT)

                val effectiveProbability =
                    if (cfg.useProgressiveDifficulty) {
                        cfg.reboundProbability * currentDifficultyModifier
                    } else {
                        cfg.reboundProbability
                    }

                val opponentBuzzed = Random.nextDouble() < effectiveProbability
                var opponentAnswer: String? = null
                var opponentWasCorrect = false
                var isReboundOpportunity = false
                var timeAfterAnswer = 0.0

                if (opponentBuzzed) {
                    opponentWasCorrect = Random.nextDouble() < cfg.opponentAccuracy
                    isReboundOpportunity = !opponentWasCorrect

                    opponentAnswer =
                        if (opponentWasCorrect) {
                            question.answer.primary
                        } else {
                            generateWrongAnswer(question)
                        }

                    timeAfterAnswer = Random.nextDouble(1.0, 3.0)
                }

                val scenario =
                    KBReboundScenario(
                        question = question,
                        opponentBuzzed = opponentBuzzed,
                        opponentAnswer = opponentAnswer,
                        opponentWasCorrect = opponentWasCorrect,
                        isReboundOpportunity = isReboundOpportunity,
                        timeAfterOpponentAnswer = timeAfterAnswer,
                    )

                currentScenario = scenario
                Log.d(TAG, "Generated scenario: opponentBuzzed=$opponentBuzzed, isRebound=$isReboundOpportunity")
                scenario
            }

        /**
         * Record a user's rebound decision.
         */
        suspend fun recordAttempt(
            buzzedOnRebound: Boolean,
            userAnswer: String?,
            wasCorrect: Boolean,
            responseTime: Double,
            knewAnswer: Boolean,
        ) = mutex.withLock {
            val scenario = currentScenario ?: return@withLock

            val decision =
                determineDecision(
                    scenario = scenario,
                    buzzed = buzzedOnRebound,
                    wasCorrect = wasCorrect,
                    knewAnswer = knewAnswer,
                )

            val points = calculatePoints(decision, wasCorrect)

            val attempt =
                KBReboundAttempt(
                    scenarioId = scenario.id,
                    questionId = scenario.question.id,
                    domain = scenario.question.domain,
                    wasReboundOpportunity = scenario.isReboundOpportunity,
                    userBuzzedOnRebound = buzzedOnRebound,
                    userAnswer = userAnswer,
                    wasCorrect = wasCorrect,
                    responseTime = responseTime,
                    pointsEarned = points,
                    strategicDecision = decision,
                )

            attempts.add(attempt)
            updateDifficulty(decision)

            Log.d(TAG, "Rebound attempt: decision=${decision.name}, points=$points, correct=$wasCorrect")
        }

        /**
         * Get all recorded attempts.
         */
        fun getAllAttempts(): List<KBReboundAttempt> = attempts.toList()

        /**
         * Reset the simulator for a new session.
         */
        suspend fun reset() =
            mutex.withLock {
                config = null
                attempts.clear()
                currentScenario = null
                sessionStartTime = 0L
                isActive = false
                currentDifficultyModifier = 1.0
            }

        // MARK: - Private Helpers

        private fun generateWrongAnswer(question: KBQuestion): String {
            val domainAnswers = WRONG_ANSWERS[question.domain] ?: listOf("Unknown")
            return domainAnswers.filter { it != question.answer.primary }.randomOrNull() ?: "Unknown"
        }

        private fun determineDecision(
            scenario: KBReboundScenario,
            buzzed: Boolean,
            wasCorrect: Boolean,
            knewAnswer: Boolean,
        ): ReboundDecision {
            return if (scenario.isReboundOpportunity) {
                if (buzzed) {
                    if (wasCorrect) ReboundDecision.BUZZED_CORRECTLY else ReboundDecision.BUZZED_INCORRECTLY
                } else {
                    if (knewAnswer) ReboundDecision.STRATEGIC_HOLD else ReboundDecision.MISSED_OPPORTUNITY
                }
            } else {
                ReboundDecision.CORRECTLY_IGNORED
            }
        }

        private fun calculatePoints(
            decision: ReboundDecision,
            @Suppress("UnusedParameter") wasCorrect: Boolean,
        ): Int {
            return when (decision) {
                ReboundDecision.BUZZED_CORRECTLY -> 10
                ReboundDecision.BUZZED_INCORRECTLY -> -5
                ReboundDecision.STRATEGIC_HOLD -> 2
                ReboundDecision.MISSED_OPPORTUNITY -> -2
                ReboundDecision.CORRECTLY_IGNORED -> 1
            }
        }

        private fun updateDifficulty(decision: ReboundDecision) {
            val cfg = config ?: return
            if (!cfg.useProgressiveDifficulty) return

            when (decision) {
                ReboundDecision.BUZZED_CORRECTLY -> {
                    currentDifficultyModifier = (currentDifficultyModifier + 0.1).coerceAtMost(1.5)
                }
                ReboundDecision.BUZZED_INCORRECTLY, ReboundDecision.MISSED_OPPORTUNITY -> {
                    currentDifficultyModifier = (currentDifficultyModifier - 0.05).coerceAtLeast(0.5)
                }
                else -> { /* No change */ }
            }
        }
    }
