package com.unamentis.modules.knowledgebowl.ui.rebound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.modules.knowledgebowl.core.engine.KBQuestionEngine
import com.unamentis.modules.knowledgebowl.core.rebound.KBReboundConfig
import com.unamentis.modules.knowledgebowl.core.rebound.KBReboundScenario
import com.unamentis.modules.knowledgebowl.core.rebound.KBReboundSimulator
import com.unamentis.modules.knowledgebowl.core.rebound.KBReboundTrainingResult
import com.unamentis.modules.knowledgebowl.core.rebound.ReboundFeedback
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Training states for rebound mode.
 */
enum class ReboundTrainingState {
    SETUP,
    WAITING_FOR_OPPONENT,
    OPPONENT_ANSWERING,
    REBOUND_OPPORTUNITY,
    USER_TURN,
    FEEDBACK,
    RESULTS,
}

/**
 * UI state for the rebound training screen.
 */
data class ReboundTrainingUiState(
    val state: ReboundTrainingState = ReboundTrainingState.SETUP,
    val selectedRegion: KBRegion = KBRegion.DEFAULT,
    // Setup options
    val opponentAccuracy: Float = 0.6f,
    val reboundProbability: Float = 0.5f,
    val showOpponentAnswer: Boolean = true,
    val questionCount: Int = 15,
    val showingPracticeScenarios: Boolean = false,
    // Training state
    val currentQuestionIndex: Int = 0,
    val totalQuestions: Int = 0,
    val currentScenario: KBReboundScenario? = null,
    val totalPoints: Int = 0,
    val reboundTimeRemaining: Float = 5.0f,
    val opponentName: String = "Team Alpha",
    val lastFeedback: ReboundFeedback? = null,
    // Results
    val trainingResult: KBReboundTrainingResult? = null,
    // Loading/error
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * ViewModel for the rebound training screen.
 */
@HiltViewModel
class KBReboundTrainingViewModel
    @Inject
    constructor(
        private val reboundSimulator: KBReboundSimulator,
        private val questionEngine: KBQuestionEngine,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ReboundTrainingUiState())
        val uiState: StateFlow<ReboundTrainingUiState> = _uiState.asStateFlow()

        private var questions: List<KBQuestion> = emptyList()
        private var turnStartTime: Long = 0L
        private var timerJob: Job? = null
        private var sessionStartTime: Long = 0L

        // MARK: - Setup Actions

        fun setOpponentAccuracy(accuracy: Float) {
            _uiState.update { it.copy(opponentAccuracy = accuracy.coerceIn(0.3f, 0.9f)) }
        }

        fun setReboundProbability(probability: Float) {
            _uiState.update { it.copy(reboundProbability = probability.coerceIn(0.3f, 0.8f)) }
        }

        fun setShowOpponentAnswer(show: Boolean) {
            _uiState.update { it.copy(showOpponentAnswer = show) }
        }

        fun setQuestionCount(count: Int) {
            _uiState.update { it.copy(questionCount = count.coerceIn(5, 30)) }
        }

        fun togglePracticeScenarios() {
            _uiState.update { it.copy(showingPracticeScenarios = !it.showingPracticeScenarios) }
        }

        fun dismissPracticeScenarios() {
            _uiState.update { it.copy(showingPracticeScenarios = false) }
        }

        // MARK: - Training Control

        fun startTraining() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }

                try {
                    // Load questions
                    questionEngine.loadBundledQuestions()
                    questions = questionEngine.questions.value.shuffled()

                    if (questions.isEmpty()) {
                        _uiState.update {
                            it.copy(isLoading = false, error = "No questions available")
                        }
                        return@launch
                    }

                    val state = _uiState.value
                    val config =
                        KBReboundConfig(
                            region = state.selectedRegion,
                            reboundProbability = state.reboundProbability.toDouble(),
                            opponentAccuracy = state.opponentAccuracy.toDouble(),
                            questionCount = state.questionCount,
                            showOpponentAnswer = state.showOpponentAnswer,
                            useProgressiveDifficulty = true,
                        )

                    reboundSimulator.startSession(config)
                    sessionStartTime = System.currentTimeMillis()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            state = ReboundTrainingState.WAITING_FOR_OPPONENT,
                            currentQuestionIndex = 0,
                            totalQuestions = minOf(state.questionCount, questions.size),
                            totalPoints = 0,
                            opponentName = reboundSimulator.getCurrentOpponent(),
                        )
                    }

                    simulateOpponent()
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Failed to start training")
                    }
                }
            }
        }

        private fun simulateOpponent() {
            viewModelScope.launch {
                val state = _uiState.value
                if (state.currentQuestionIndex >= state.totalQuestions) {
                    endTraining()
                    return@launch
                }

                val question = questions[state.currentQuestionIndex]
                val scenario = reboundSimulator.generateScenario(question)

                _uiState.update { it.copy(currentScenario = scenario) }

                // Simulate reading time
                delay(2000)

                if (scenario.opponentBuzzed) {
                    _uiState.update { it.copy(state = ReboundTrainingState.OPPONENT_ANSWERING) }

                    // Show opponent answering
                    delay(1500)

                    if (scenario.isReboundOpportunity) {
                        startReboundTimer()
                        _uiState.update { it.copy(state = ReboundTrainingState.REBOUND_OPPORTUNITY) }
                    } else {
                        // Opponent got it right, show feedback and move on
                        showOpponentCorrectFeedback()
                    }
                } else {
                    // No opponent buzz, user's turn
                    turnStartTime = System.currentTimeMillis()
                    _uiState.update { it.copy(state = ReboundTrainingState.USER_TURN) }
                }
            }
        }

        fun buzzOnRebound() {
            stopReboundTimer()
            turnStartTime = System.currentTimeMillis()
            _uiState.update { it.copy(state = ReboundTrainingState.USER_TURN) }
        }

        fun holdStrategically() {
            viewModelScope.launch {
                stopReboundTimer()

                val state = _uiState.value
                val scenario = state.currentScenario ?: return@launch

                reboundSimulator.recordAttempt(
                    buzzedOnRebound = false,
                    userAnswer = null,
                    wasCorrect = false,
                    responseTime = (5.0 - state.reboundTimeRemaining).toDouble(),
                    knewAnswer = false,
                )

                val points = 2
                val answer = scenario.question.answer.primary
                val feedback =
                    ReboundFeedback(
                        title = "Strategic Hold",
                        message = "Good decision to hold when unsure. Correct answer: $answer",
                        isPositive = true,
                        points = points,
                    )

                _uiState.update {
                    it.copy(
                        state = ReboundTrainingState.FEEDBACK,
                        totalPoints = it.totalPoints + points,
                        lastFeedback = feedback,
                    )
                }
            }
        }

        fun submitAnswer(wasCorrect: Boolean) {
            viewModelScope.launch {
                val state = _uiState.value
                val scenario = state.currentScenario ?: return@launch

                val responseTime = (System.currentTimeMillis() - turnStartTime) / 1000.0

                reboundSimulator.recordAttempt(
                    buzzedOnRebound = scenario.isReboundOpportunity,
                    userAnswer = if (wasCorrect) scenario.question.answer.primary else "wrong",
                    wasCorrect = wasCorrect,
                    responseTime = responseTime,
                    knewAnswer = true,
                )

                val points = if (wasCorrect) 10 else -5

                val feedback =
                    if (wasCorrect) {
                        ReboundFeedback(
                            title = "Correct!",
                            message =
                                if (scenario.isReboundOpportunity) {
                                    "Great rebound! You capitalized on their mistake."
                                } else {
                                    "Nice answer!"
                                },
                            isPositive = true,
                            points = points,
                        )
                    } else {
                        ReboundFeedback(
                            title = "Incorrect",
                            message = "The answer was: ${scenario.question.answer.primary}",
                            isPositive = false,
                            points = points,
                        )
                    }

                _uiState.update {
                    it.copy(
                        state = ReboundTrainingState.FEEDBACK,
                        totalPoints = it.totalPoints + points,
                        lastFeedback = feedback,
                    )
                }
            }
        }

        fun nextQuestion() {
            viewModelScope.launch {
                val state = _uiState.value
                val nextIndex = state.currentQuestionIndex + 1

                if (nextIndex >= state.totalQuestions) {
                    endTraining()
                } else {
                    reboundSimulator.rotateOpponent()
                    _uiState.update {
                        it.copy(
                            state = ReboundTrainingState.WAITING_FOR_OPPONENT,
                            currentQuestionIndex = nextIndex,
                            opponentName = reboundSimulator.getCurrentOpponent(),
                        )
                    }
                    simulateOpponent()
                }
            }
        }

        fun restartTraining() {
            _uiState.update {
                ReboundTrainingUiState(
                    selectedRegion = it.selectedRegion,
                    opponentAccuracy = it.opponentAccuracy,
                    reboundProbability = it.reboundProbability,
                    showOpponentAnswer = it.showOpponentAnswer,
                    questionCount = it.questionCount,
                )
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }

        // MARK: - Private Helpers

        private fun showOpponentCorrectFeedback() {
            viewModelScope.launch {
                val state = _uiState.value
                val scenario = state.currentScenario ?: return@launch

                reboundSimulator.recordAttempt(
                    buzzedOnRebound = false,
                    userAnswer = null,
                    wasCorrect = false,
                    responseTime = 0.0,
                    knewAnswer = false,
                )

                val points = 1
                val feedback =
                    ReboundFeedback(
                        title = "${state.opponentName} Got It!",
                        message = "No rebound opportunity. Answer: ${scenario.question.answer.primary}",
                        isPositive = true,
                        points = points,
                    )

                _uiState.update {
                    it.copy(
                        state = ReboundTrainingState.FEEDBACK,
                        totalPoints = it.totalPoints + points,
                        lastFeedback = feedback,
                    )
                }
            }
        }

        private fun startReboundTimer() {
            _uiState.update { it.copy(reboundTimeRemaining = 5.0f) }

            timerJob =
                viewModelScope.launch {
                    while (_uiState.value.reboundTimeRemaining > 0) {
                        delay(100)
                        _uiState.update {
                            it.copy(reboundTimeRemaining = (it.reboundTimeRemaining - 0.1f).coerceAtLeast(0f))
                        }

                        if (_uiState.value.reboundTimeRemaining <= 0) {
                            missedReboundOpportunity()
                            break
                        }
                    }
                }
        }

        private fun stopReboundTimer() {
            timerJob?.cancel()
            timerJob = null
        }

        private fun missedReboundOpportunity() {
            viewModelScope.launch {
                val state = _uiState.value
                val scenario = state.currentScenario ?: return@launch

                reboundSimulator.recordAttempt(
                    buzzedOnRebound = false,
                    userAnswer = null,
                    wasCorrect = false,
                    responseTime = 5.0,
                    knewAnswer = false,
                )

                val points = -2
                val feedback =
                    ReboundFeedback(
                        title = "Missed Opportunity",
                        message = "Time ran out! Correct answer: ${scenario.question.answer.primary}",
                        isPositive = false,
                        points = points,
                    )

                _uiState.update {
                    it.copy(
                        state = ReboundTrainingState.FEEDBACK,
                        totalPoints = it.totalPoints + points,
                        lastFeedback = feedback,
                    )
                }
            }
        }

        private fun endTraining() {
            viewModelScope.launch {
                stopReboundTimer()

                val stats = reboundSimulator.endSession()
                val state = _uiState.value

                val result =
                    KBReboundTrainingResult(
                        region = state.selectedRegion,
                        startTime = sessionStartTime,
                        endTime = System.currentTimeMillis(),
                        stats = stats,
                        recommendation = KBReboundTrainingResult.generateRecommendation(stats),
                    )

                _uiState.update {
                    it.copy(
                        state = ReboundTrainingState.RESULTS,
                        trainingResult = result,
                    )
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            stopReboundTimer()
        }
    }
