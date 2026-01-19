package com.unamentis.modules.knowledgebowl.ui.written

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.modules.knowledgebowl.core.stats.KBStatsManager
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import com.unamentis.modules.knowledgebowl.data.model.KBRegionalConfig
import com.unamentis.modules.knowledgebowl.data.model.KBRoundType
import com.unamentis.modules.knowledgebowl.data.model.KBSessionConfig
import com.unamentis.modules.knowledgebowl.data.model.KBSessionSummary
import com.unamentis.modules.knowledgebowl.data.model.KBStudyMode
import com.unamentis.modules.knowledgebowl.ui.theme.KBTimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Written Session practice screen.
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class KBWrittenSessionViewModel
    @Inject
    constructor(
        private val statsManager: KBStatsManager,
    ) : ViewModel() {
        // Configuration (set by caller before navigation)
        private var _questions: List<KBQuestion> = emptyList()
        private var _config: KBSessionConfig? = null

        val questions: List<KBQuestion> get() = _questions
        val config: KBSessionConfig get() = _config ?: defaultConfig()
        val regionalConfig: KBRegionalConfig get() = config.region.config

        // State
        private val _uiState = MutableStateFlow(KBWrittenSessionUiState())
        val uiState: StateFlow<KBWrittenSessionUiState> = _uiState.asStateFlow()

        private val _currentQuestionIndex = MutableStateFlow(0)
        val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex.asStateFlow()

        private val _selectedAnswer = MutableStateFlow<Int?>(null)
        val selectedAnswer: StateFlow<Int?> = _selectedAnswer.asStateFlow()

        private val _showingFeedback = MutableStateFlow(false)
        val showingFeedback: StateFlow<Boolean> = _showingFeedback.asStateFlow()

        private val _lastAnswerCorrect = MutableStateFlow<Boolean?>(null)
        val lastAnswerCorrect: StateFlow<Boolean?> = _lastAnswerCorrect.asStateFlow()

        private val _remainingTime = MutableStateFlow(0.0)
        val remainingTime: StateFlow<Double> = _remainingTime.asStateFlow()

        private val _correctCount = MutableStateFlow(0)
        val correctCount: StateFlow<Int> = _correctCount.asStateFlow()

        private val _totalPoints = MutableStateFlow(0)
        val totalPoints: StateFlow<Int> = _totalPoints.asStateFlow()

        private var timerJob: Job? = null
        private var sessionStartTime: Long = 0L

        // Computed Properties
        val currentQuestion: KBQuestion?
            get() = _questions.getOrNull(_currentQuestionIndex.value)

        val progress: Float
            get() =
                if (_questions.isEmpty()) {
                    0f
                } else {
                    _currentQuestionIndex.value.toFloat() / _questions.size
                }

        val isLastQuestion: Boolean
            get() = _currentQuestionIndex.value >= _questions.size - 1

        val timerState: KBTimerState
            get() {
                val timeLimit = config.timeLimitSeconds?.toDouble() ?: return KBTimerState.NORMAL
                val remainingPercent = (_remainingTime.value / timeLimit).toFloat()
                return KBTimerState.from(remainingPercent)
            }

        val accuracy: Float
            get() {
                val answered = _currentQuestionIndex.value
                return if (answered > 0) {
                    _correctCount.value.toFloat() / answered
                } else {
                    0f
                }
            }

        val sessionDuration: Double
            get() =
                if (sessionStartTime > 0) {
                    (System.currentTimeMillis() - sessionStartTime) / 1000.0
                } else {
                    0.0
                }

        /**
         * Initialize the session with questions and config.
         */
        fun initialize(
            questions: List<KBQuestion>,
            config: KBSessionConfig,
        ) {
            _questions = questions
            _config = config
            _remainingTime.value = config.timeLimitSeconds?.toDouble() ?: 0.0
            _uiState.value = KBWrittenSessionUiState(state = WrittenSessionState.NOT_STARTED)
        }

        /**
         * Start the practice session.
         */
        fun startSession() {
            _uiState.value = _uiState.value.copy(state = WrittenSessionState.IN_PROGRESS)
            sessionStartTime = System.currentTimeMillis()

            // Start timer if time limit is set
            config.timeLimitSeconds?.toDouble()?.let { limit ->
                _remainingTime.value = limit
                startTimer()
            }
        }

        /**
         * Select an MCQ answer option.
         */
        fun selectAnswer(index: Int) {
            if (!_showingFeedback.value) {
                _selectedAnswer.value = index
            }
        }

        /**
         * Submit the selected answer.
         */
        fun submitAnswer() {
            val question = currentQuestion ?: return
            val selectedIndex = _selectedAnswer.value ?: return
            val options = question.mcqOptions ?: return

            if (selectedIndex >= options.size) return

            val selectedOption = options[selectedIndex]
            val isCorrect = selectedOption.equals(question.answer.primary, ignoreCase = true)

            _lastAnswerCorrect.value = isCorrect
            _showingFeedback.value = true

            if (isCorrect) {
                _correctCount.value++
                _totalPoints.value += regionalConfig.writtenPointsPerCorrect
            }
        }

        /**
         * Move to the next question or end session.
         */
        fun nextQuestion() {
            _selectedAnswer.value = null
            _showingFeedback.value = false
            _lastAnswerCorrect.value = null

            if (_currentQuestionIndex.value < _questions.size - 1) {
                _currentQuestionIndex.value++
            } else {
                endSession()
            }
        }

        /**
         * End the session early or normally.
         */
        fun endSession() {
            timerJob?.cancel()
            _uiState.value = _uiState.value.copy(state = WrittenSessionState.COMPLETED)

            // Record stats
            val summary = createSummary()
            statsManager.recordSession(summary, KBStudyMode.DIAGNOSTIC)
        }

        private fun startTimer() {
            timerJob?.cancel()
            timerJob =
                viewModelScope.launch {
                    while (_remainingTime.value > 0) {
                        delay(TIMER_INTERVAL_MS)
                        _remainingTime.value = (_remainingTime.value - TIMER_DECREMENT).coerceAtLeast(0.0)

                        if (_remainingTime.value <= 0) {
                            _uiState.value = _uiState.value.copy(state = WrittenSessionState.EXPIRED)
                            break
                        }
                    }
                }
        }

        private fun createSummary(): KBSessionSummary {
            val totalQuestions = _currentQuestionIndex.value + if (_showingFeedback.value) 1 else 0
            return KBSessionSummary(
                sessionId = "session-${System.currentTimeMillis()}",
                roundType = KBRoundType.WRITTEN,
                region = config.region,
                totalQuestions = totalQuestions.coerceAtLeast(1),
                totalCorrect = _correctCount.value,
                totalPoints = _totalPoints.value,
                accuracy = accuracy,
                // Not tracked in written mode - MCQ has no typed input timing
                averageResponseTimeSeconds = 0f,
                durationSeconds = sessionDuration.toFloat(),
                completedAtMillis = System.currentTimeMillis(),
            )
        }

        private fun defaultConfig(): KBSessionConfig =
            KBSessionConfig(
                region = KBRegion.COLORADO,
                roundType = KBRoundType.WRITTEN,
                questionCount = 10,
            )

        override fun onCleared() {
            super.onCleared()
            timerJob?.cancel()
        }

        companion object {
            private const val TIMER_INTERVAL_MS = 100L
            private const val TIMER_DECREMENT = 0.1
        }
    }

/**
 * UI state for the Written Session screen.
 */
data class KBWrittenSessionUiState(
    val state: WrittenSessionState = WrittenSessionState.NOT_STARTED,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Session state enum.
 */
enum class WrittenSessionState {
    NOT_STARTED,
    IN_PROGRESS,
    PAUSED,
    REVIEWING,
    COMPLETED,
    EXPIRED,
}
