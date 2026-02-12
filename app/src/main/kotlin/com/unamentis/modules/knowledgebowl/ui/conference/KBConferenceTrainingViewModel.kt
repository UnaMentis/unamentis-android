package com.unamentis.modules.knowledgebowl.ui.conference

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.modules.knowledgebowl.core.conference.KBConferenceConfig
import com.unamentis.modules.knowledgebowl.core.conference.KBConferenceManager
import com.unamentis.modules.knowledgebowl.core.conference.KBConferenceTrainingResult
import com.unamentis.modules.knowledgebowl.core.conference.KBHandSignal
import com.unamentis.modules.knowledgebowl.core.engine.KBQuestionEngine
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
 * Training state for conference training.
 */
enum class ConferenceTrainingState {
    SETUP,
    TRAINING,
    SIGNAL_PRACTICE,
    RESULTS,
}

/**
 * UI state for the conference training screen.
 */
data class ConferenceTrainingUiState(
    // State
    val state: ConferenceTrainingState = ConferenceTrainingState.SETUP,
    val config: KBConferenceConfig? = null,
    // Setup options
    val selectedRegion: KBRegion = KBRegion.DEFAULT,
    val progressiveDifficulty: Boolean = true,
    val questionCount: Int = 15,
    // Training state
    val currentQuestionIndex: Int = 0,
    val totalQuestions: Int = 0,
    val currentLevel: Int = 0,
    val remainingTime: Double = 15.0,
    val currentTimeLimit: Double = 15.0,
    val canRequestMoreTime: Boolean = true,
    val currentQuestion: KBQuestion? = null,
    // Signal practice state
    val currentSignalPrompt: Pair<KBHandSignal, String>? = null,
    val selectedSignal: KBHandSignal? = null,
    val lastSignalResult: Boolean? = null,
    // Results
    val trainingResult: KBConferenceTrainingResult? = null,
    // Loading/error
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val timerProgress: Double
        get() = if (currentTimeLimit > 0) remainingTime / currentTimeLimit else 0.0

    val handSignalsOnly: Boolean
        get() = config?.handSignalsOnly ?: false
}

/**
 * ViewModel for the conference training screen.
 */
@HiltViewModel
class KBConferenceTrainingViewModel
    @Inject
    constructor(
        private val conferenceManager: KBConferenceManager,
        private val questionEngine: KBQuestionEngine,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ConferenceTrainingUiState())
        val uiState: StateFlow<ConferenceTrainingUiState> = _uiState.asStateFlow()

        private var timerJob: Job? = null
        private var questions: List<KBQuestion> = emptyList()
        private var sessionStartTime: Long = 0L

        init {
            // Observe difficulty level changes
            viewModelScope.launch {
                conferenceManager.currentLevel.collect { level ->
                    _uiState.update { it.copy(currentLevel = level) }
                }
            }
        }

        // MARK: - Setup Actions

        fun selectRegion(region: KBRegion) {
            val config = KBConferenceConfig.forRegion(region)
            _uiState.update {
                it.copy(
                    selectedRegion = region,
                    config = config,
                    currentTimeLimit = config.baseTimeLimit,
                    remainingTime = config.baseTimeLimit,
                )
            }
        }

        fun setProgressiveDifficulty(enabled: Boolean) {
            _uiState.update { it.copy(progressiveDifficulty = enabled) }
        }

        fun setQuestionCount(count: Int) {
            _uiState.update { it.copy(questionCount = count.coerceIn(5, 30)) }
        }

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
                        KBConferenceConfig(
                            region = state.selectedRegion,
                            baseTimeLimit = KBConferenceConfig.forRegion(state.selectedRegion).baseTimeLimit,
                            progressiveDifficulty = state.progressiveDifficulty,
                            handSignalsOnly = KBConferenceConfig.forRegion(state.selectedRegion).handSignalsOnly,
                            questionCount = state.questionCount,
                        )

                    conferenceManager.startSession(config)
                    sessionStartTime = System.currentTimeMillis()

                    val totalQ = minOf(state.questionCount, questions.size)
                    val timeLimit = config.timeLimit(0)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            state = ConferenceTrainingState.TRAINING,
                            config = config,
                            currentQuestionIndex = 0,
                            totalQuestions = totalQ,
                            currentLevel = 0,
                            currentTimeLimit = timeLimit,
                            remainingTime = timeLimit,
                            canRequestMoreTime = true,
                            currentQuestion = questions.firstOrNull(),
                        )
                    }

                    startTimer()
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Failed to start training")
                    }
                }
            }
        }

        // MARK: - Training Actions

        fun submitAnswer(wasCorrect: Boolean) {
            viewModelScope.launch {
                stopTimer()

                val state = _uiState.value
                val question = state.currentQuestion ?: return@launch
                val conferenceTime = state.currentTimeLimit - state.remainingTime

                conferenceManager.recordAttempt(
                    questionId = question.id,
                    domain = question.domain,
                    conferenceTime = conferenceTime,
                    wasCorrect = wasCorrect,
                )

                advanceToNextQuestion()
            }
        }

        fun skipQuestion() {
            stopTimer()
            advanceToNextQuestion()
        }

        fun requestMoreTime() {
            val state = _uiState.value
            if (state.canRequestMoreTime) {
                _uiState.update {
                    it.copy(
                        canRequestMoreTime = false,
                        remainingTime = it.remainingTime + 5.0,
                    )
                }
            }
        }

        private fun advanceToNextQuestion() {
            val state = _uiState.value
            val nextIndex = state.currentQuestionIndex + 1

            if (nextIndex >= state.totalQuestions) {
                endTraining()
            } else {
                val config = state.config ?: return
                val timeLimit = config.timeLimit(state.currentLevel)
                val nextQuestion = questions.getOrNull(nextIndex)

                _uiState.update {
                    it.copy(
                        currentQuestionIndex = nextIndex,
                        currentTimeLimit = timeLimit,
                        remainingTime = timeLimit,
                        canRequestMoreTime = true,
                        currentQuestion = nextQuestion,
                    )
                }

                startTimer()
            }
        }

        private fun endTraining() {
            viewModelScope.launch {
                stopTimer()

                val stats = conferenceManager.endSession()
                val state = _uiState.value

                val result =
                    KBConferenceTrainingResult(
                        region = state.selectedRegion,
                        startTime = sessionStartTime,
                        endTime = System.currentTimeMillis(),
                        stats = stats,
                        finalDifficultyLevel = stats.currentDifficultyLevel,
                        recommendation = KBConferenceTrainingResult.generateRecommendation(stats),
                    )

                _uiState.update {
                    it.copy(
                        state = ConferenceTrainingState.RESULTS,
                        trainingResult = result,
                    )
                }
            }
        }

        // MARK: - Timer

        private fun startTimer() {
            timerJob?.cancel()
            timerJob =
                viewModelScope.launch {
                    while (_uiState.value.remainingTime > 0) {
                        delay(100)
                        _uiState.update {
                            it.copy(remainingTime = (it.remainingTime - 0.1).coerceAtLeast(0.0))
                        }

                        if (_uiState.value.remainingTime <= 0) {
                            // Time expired
                            submitAnswer(wasCorrect = false)
                            break
                        }
                    }
                }
        }

        private fun stopTimer() {
            timerJob?.cancel()
            timerJob = null
        }

        // MARK: - Signal Practice

        fun startSignalPractice() {
            _uiState.update {
                it.copy(
                    state = ConferenceTrainingState.SIGNAL_PRACTICE,
                    currentSignalPrompt = KBConferenceManager.randomSignalPrompt(),
                    selectedSignal = null,
                    lastSignalResult = null,
                )
            }
        }

        fun selectSignal(signal: KBHandSignal) {
            val prompt = _uiState.value.currentSignalPrompt ?: return
            val isCorrect = KBConferenceManager.validateSignal(prompt.first, signal)

            _uiState.update {
                it.copy(
                    selectedSignal = signal,
                    lastSignalResult = isCorrect,
                )
            }

            // After a delay, show next prompt
            viewModelScope.launch {
                delay(1500)
                if (_uiState.value.state == ConferenceTrainingState.SIGNAL_PRACTICE) {
                    _uiState.update {
                        it.copy(
                            currentSignalPrompt = KBConferenceManager.randomSignalPrompt(),
                            selectedSignal = null,
                            lastSignalResult = null,
                        )
                    }
                }
            }
        }

        fun endSignalPractice() {
            _uiState.update { it.copy(state = ConferenceTrainingState.SETUP) }
        }

        // MARK: - Results Actions

        fun restartTraining() {
            viewModelScope.launch {
                conferenceManager.reset()
                _uiState.update {
                    ConferenceTrainingUiState(
                        selectedRegion = it.selectedRegion,
                        progressiveDifficulty = it.progressiveDifficulty,
                        questionCount = it.questionCount,
                    )
                }
            }
        }

        fun reset() {
            viewModelScope.launch {
                stopTimer()
                conferenceManager.reset()
                _uiState.value = ConferenceTrainingUiState()
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }

        override fun onCleared() {
            super.onCleared()
            stopTimer()
        }
    }
