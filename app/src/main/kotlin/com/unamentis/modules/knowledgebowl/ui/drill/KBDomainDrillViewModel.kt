package com.unamentis.modules.knowledgebowl.ui.drill

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.modules.knowledgebowl.core.engine.KBQuestionEngine
import com.unamentis.modules.knowledgebowl.core.validation.KBAnswerValidator
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
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
 * Training state for domain drill.
 */
enum class DrillState {
    SETUP,
    DRILLING,
    FEEDBACK,
    RESULTS,
}

/**
 * Result for a single question.
 */
data class DrillQuestionResult(
    val question: KBQuestion,
    val userAnswer: String,
    val correct: Boolean,
    val responseTime: Double,
)

/**
 * UI state for domain drill.
 */
data class DrillUiState(
    val state: DrillState = DrillState.SETUP,
    // Setup
    val selectedDomain: KBDomain? = null,
    val questionCount: Int = 10,
    val progressiveDifficulty: Boolean = true,
    val timePressureMode: Boolean = false,
    val timePerQuestion: Double = 30.0,
    // Drilling
    val questions: List<KBQuestion> = emptyList(),
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val userAnswer: String = "",
    // Timer
    val timeRemaining: Double = 30.0,
    val timerProgress: Float = 1f,
    // Feedback
    val lastWasCorrect: Boolean = false,
    val lastQuestion: KBQuestion? = null,
    // Results
    val questionResults: List<DrillQuestionResult> = emptyList(),
    val bestStreak: Int = 0,
) {
    val currentQuestion: KBQuestion?
        get() = questions.getOrNull(currentIndex)

    val hasMoreQuestions: Boolean
        get() = currentIndex < questions.size - 1

    val totalQuestions: Int
        get() = questions.size

    val accuracy: Double
        get() = if (questionResults.isEmpty()) 0.0 else correctCount.toDouble() / questionResults.size

    val averageTime: Double
        get() =
            if (questionResults.isEmpty()) {
                0.0
            } else {
                questionResults.sumOf { it.responseTime } / questionResults.size
            }

    val finalDifficulty: String
        get() =
            if (progressiveDifficulty) {
                val level = minOf(5, 1 + correctCount / 3)
                "Level $level"
            } else {
                "Standard"
            }
}

/**
 * ViewModel for Domain Drill training mode.
 */
@HiltViewModel
class KBDomainDrillViewModel
    @Inject
    constructor(
        private val questionEngine: KBQuestionEngine,
        private val answerValidator: KBAnswerValidator,
    ) : ViewModel() {
        companion object {
            private const val TAG = "KBDomainDrillVM"
        }

        private val _uiState = MutableStateFlow(DrillUiState())
        val uiState: StateFlow<DrillUiState> = _uiState.asStateFlow()

        private var timerJob: Job? = null
        private var questionStartTime: Long = 0L
        private var currentStreak: Int = 0

        // MARK: - Setup Actions

        fun selectDomain(domain: KBDomain) {
            _uiState.update { it.copy(selectedDomain = domain) }
        }

        fun setQuestionCount(count: Int) {
            _uiState.update { it.copy(questionCount = count.coerceIn(5, 30)) }
        }

        fun setProgressiveDifficulty(enabled: Boolean) {
            _uiState.update { it.copy(progressiveDifficulty = enabled) }
        }

        fun setTimePressureMode(enabled: Boolean) {
            _uiState.update { it.copy(timePressureMode = enabled) }
        }

        fun updateUserAnswer(answer: String) {
            _uiState.update { it.copy(userAnswer = answer) }
        }

        // MARK: - Drill Actions

        fun startDrill() {
            val domain = _uiState.value.selectedDomain ?: return

            viewModelScope.launch {
                val allQuestions =
                    questionEngine.filter(
                        domains = listOf(domain),
                    ).shuffled().take(_uiState.value.questionCount)

                currentStreak = 0

                _uiState.update {
                    it.copy(
                        state = DrillState.DRILLING,
                        questions = allQuestions,
                        currentIndex = 0,
                        correctCount = 0,
                        userAnswer = "",
                        questionResults = emptyList(),
                        bestStreak = 0,
                    )
                }

                startQuestionTimer()
                Log.i(TAG, "Started drill for ${domain.name} with ${allQuestions.size} questions")
            }
        }

        fun submitAnswer() {
            val question = _uiState.value.currentQuestion ?: return

            stopTimer()

            val responseTime = (System.currentTimeMillis() - questionStartTime) / 1000.0
            val userAnswer = _uiState.value.userAnswer
            val validationResult = answerValidator.validate(userAnswer, question)
            val isCorrect = validationResult.isCorrect

            val newCorrectCount = _uiState.value.correctCount + if (isCorrect) 1 else 0

            if (isCorrect) {
                currentStreak++
            } else {
                currentStreak = 0
            }

            val newBestStreak = maxOf(_uiState.value.bestStreak, currentStreak)

            val result =
                DrillQuestionResult(
                    question = question,
                    userAnswer = userAnswer,
                    correct = isCorrect,
                    responseTime = responseTime,
                )

            _uiState.update {
                it.copy(
                    state = DrillState.FEEDBACK,
                    correctCount = newCorrectCount,
                    lastWasCorrect = isCorrect,
                    lastQuestion = question,
                    questionResults = it.questionResults + result,
                    bestStreak = newBestStreak,
                    userAnswer = "",
                )
            }

            Log.d(TAG, "Answer submitted: correct=$isCorrect, time=${responseTime}s")
        }

        fun nextQuestion() {
            if (_uiState.value.hasMoreQuestions) {
                _uiState.update {
                    it.copy(
                        state = DrillState.DRILLING,
                        currentIndex = it.currentIndex + 1,
                    )
                }
                startQuestionTimer()
            } else {
                _uiState.update { it.copy(state = DrillState.RESULTS) }
            }
        }

        fun endDrill() {
            stopTimer()
            _uiState.update { it.copy(state = DrillState.RESULTS) }
        }

        fun restartDrill() {
            startDrill()
        }

        fun resetToSetup() {
            stopTimer()
            _uiState.update {
                it.copy(
                    state = DrillState.SETUP,
                    questions = emptyList(),
                    currentIndex = 0,
                    correctCount = 0,
                    userAnswer = "",
                    questionResults = emptyList(),
                    bestStreak = 0,
                )
            }
        }

        // MARK: - Timer

        private fun startQuestionTimer() {
            questionStartTime = System.currentTimeMillis()

            if (!_uiState.value.timePressureMode) return

            _uiState.update {
                it.copy(
                    timeRemaining = it.timePerQuestion,
                    timerProgress = 1f,
                )
            }

            timerJob =
                viewModelScope.launch {
                    val totalTime = _uiState.value.timePerQuestion
                    while (_uiState.value.timeRemaining > 0) {
                        delay(100)
                        _uiState.update {
                            val newRemaining = (it.timeRemaining - 0.1).coerceAtLeast(0.0)
                            it.copy(
                                timeRemaining = newRemaining,
                                timerProgress = (newRemaining / totalTime).toFloat(),
                            )
                        }

                        if (_uiState.value.timeRemaining <= 0) {
                            submitAnswer()
                            break
                        }
                    }
                }
        }

        private fun stopTimer() {
            timerJob?.cancel()
            timerJob = null
        }

        override fun onCleared() {
            super.onCleared()
            stopTimer()
        }
    }
