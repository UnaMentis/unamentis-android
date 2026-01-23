package com.unamentis.modules.knowledgebowl.ui.session

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.modules.knowledgebowl.core.stats.KBStatsManager
import com.unamentis.modules.knowledgebowl.data.model.KBDomainScore
import com.unamentis.modules.knowledgebowl.data.model.KBPracticeSessionSummary
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBQuestionResult
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import com.unamentis.modules.knowledgebowl.data.model.KBRoundType
import com.unamentis.modules.knowledgebowl.data.model.KBSessionSummary
import com.unamentis.modules.knowledgebowl.data.model.KBStudyMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the unified Practice Session screen.
 *
 * Manages the flow of practice sessions across all study modes,
 * handling question presentation, timing, scoring, and voice integration.
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class KBPracticeSessionViewModel
    @Inject
    constructor(
        private val statsManager: KBStatsManager,
    ) : ViewModel() {
        companion object {
            private const val TAG = "KBPracticeSessionVM"
            private const val SPEED_MODE_TIME_LIMIT_SECONDS = 300
            private const val DEFAULT_SPEED_TARGET_SECONDS = 10.0
            private const val TIMER_INTERVAL_MS = 1000L
        }

        // Session configuration
        private var questions: List<KBQuestion> = emptyList()
        private var mode: KBStudyMode = KBStudyMode.DIAGNOSTIC

        // State
        private val _sessionState = MutableStateFlow<KBSessionState>(KBSessionState.NotStarted)
        val sessionState: StateFlow<KBSessionState> = _sessionState.asStateFlow()

        private val _currentQuestion = MutableStateFlow<KBQuestion?>(null)
        val currentQuestion: StateFlow<KBQuestion?> = _currentQuestion.asStateFlow()

        private val _questionIndex = MutableStateFlow(0)
        val questionIndex: StateFlow<Int> = _questionIndex.asStateFlow()

        private val _totalQuestions = MutableStateFlow(0)
        val totalQuestions: StateFlow<Int> = _totalQuestions.asStateFlow()

        private val _results = MutableStateFlow<List<KBQuestionResult>>(emptyList())
        val results: StateFlow<List<KBQuestionResult>> = _results.asStateFlow()

        private val _timeRemaining = MutableStateFlow<Int?>(null)
        val timeRemaining: StateFlow<Int?> = _timeRemaining.asStateFlow()

        private val _userAnswer = MutableStateFlow("")
        val userAnswer: StateFlow<String> = _userAnswer.asStateFlow()

        private var questionStartTime: Long = 0L
        private var sessionStartTime: Long = 0L
        private var timerJob: Job? = null

        // Computed properties
        val correctCount: Int
            get() = _results.value.count { it.isCorrect }

        val selectedMode: KBStudyMode
            get() = mode

        val isLastQuestion: Boolean
            get() = _questionIndex.value >= _totalQuestions.value - 1

        /**
         * Start a new practice session.
         *
         * @param sessionQuestions Questions to practice with
         * @param studyMode The study mode for this session
         */
        fun startSession(
            sessionQuestions: List<KBQuestion>,
            studyMode: KBStudyMode,
        ) {
            // Stop any existing timer
            stopTimer()

            // Configure session
            questions =
                if (studyMode == KBStudyMode.DIAGNOSTIC) {
                    sessionQuestions
                } else {
                    sessionQuestions.shuffled()
                }
            mode = studyMode
            _totalQuestions.value = minOf(sessionQuestions.size, studyMode.defaultQuestionCount)
            _questionIndex.value = 0
            _results.value = emptyList()
            _userAnswer.value = ""
            sessionStartTime = System.currentTimeMillis()

            // Set time limit for speed mode
            if (studyMode == KBStudyMode.SPEED) {
                _timeRemaining.value = SPEED_MODE_TIME_LIMIT_SECONDS
                startTimer()
            } else {
                _timeRemaining.value = null
            }

            // Start session
            _sessionState.value = KBSessionState.InProgress
            presentNextQuestion()

            Log.i(TAG, "Started ${studyMode.displayName} session with ${_totalQuestions.value} questions")
        }

        /**
         * Update the user's answer text.
         */
        fun updateUserAnswer(answer: String) {
            _userAnswer.value = answer
        }

        /**
         * Submit the current answer.
         */
        fun submitAnswer() {
            submitAnswer(_userAnswer.value)
        }

        /**
         * Submit an answer for the current question.
         *
         * @param answer The answer to submit
         */
        fun submitAnswer(answer: String) {
            val question = _currentQuestion.value ?: return
            if (_sessionState.value != KBSessionState.InProgress) return

            val responseTime = (System.currentTimeMillis() - questionStartTime) / 1000.0
            val result =
                KBQuestionResult.create(
                    question = question,
                    userAnswer = answer,
                    responseTimeSeconds = responseTime,
                    speedTargetSeconds = DEFAULT_SPEED_TARGET_SECONDS,
                )

            _results.value = _results.value + result
            _sessionState.value = KBSessionState.ShowingAnswer(result.isCorrect)

            Log.d(
                TAG,
                "Answer submitted: ${if (result.isCorrect) "correct" else "incorrect"} " +
                    "in ${String.format("%.1f", responseTime)}s",
            )
        }

        /**
         * Skip the current question.
         */
        fun skipQuestion() {
            val question = _currentQuestion.value ?: return
            if (_sessionState.value != KBSessionState.InProgress) return

            val result =
                KBQuestionResult.create(
                    question = question,
                    userAnswer = "",
                    responseTimeSeconds = 0.0,
                    wasSkipped = true,
                )

            _results.value = _results.value + result
            _sessionState.value = KBSessionState.ShowingAnswer(false)

            Log.d(TAG, "Question skipped")
        }

        /**
         * Move to the next question after viewing the answer.
         */
        fun nextQuestion() {
            if (_sessionState.value !is KBSessionState.ShowingAnswer) return

            _questionIndex.value++
            _userAnswer.value = ""

            val timeUp = _timeRemaining.value?.let { it <= 0 } ?: false
            if (_questionIndex.value >= _totalQuestions.value || timeUp) {
                endSession()
            } else {
                _sessionState.value = KBSessionState.InProgress
                presentNextQuestion()
            }
        }

        /**
         * End the session early.
         */
        fun endSessionEarly() {
            endSession()
        }

        /**
         * Generate a summary of the completed session.
         */
        fun generateSummary(): KBPracticeSessionSummary {
            val resultsList = _results.value
            val correctCount = resultsList.count { it.isCorrect }
            val answeredResults = resultsList.filter { !it.wasSkipped }
            val avgTime =
                if (answeredResults.isEmpty()) {
                    0.0
                } else {
                    answeredResults.sumOf { it.responseTimeSeconds } / answeredResults.size
                }
            val speedTargetCount = resultsList.count { it.wasWithinSpeedTarget }

            val domainBreakdown = mutableMapOf<String, KBDomainScore>()
            for (result in resultsList) {
                val domainId = result.question.domain.name.lowercase()
                val existing = domainBreakdown[domainId] ?: KBDomainScore(0, 0)
                domainBreakdown[domainId] =
                    KBDomainScore(
                        total = existing.total + 1,
                        correct = existing.correct + if (result.isCorrect) 1 else 0,
                    )
            }

            val duration = (System.currentTimeMillis() - sessionStartTime) / 1000.0

            return KBPracticeSessionSummary(
                totalQuestions = resultsList.size,
                correctAnswers = correctCount,
                averageResponseTime = avgTime,
                questionsWithinSpeedTarget = speedTargetCount,
                domainBreakdown = domainBreakdown,
                durationSeconds = duration,
            )
        }

        private fun presentNextQuestion() {
            val index = _questionIndex.value
            if (index >= questions.size || index >= _totalQuestions.value) {
                endSession()
                return
            }

            _currentQuestion.value = questions[index]
            questionStartTime = System.currentTimeMillis()
        }

        private fun endSession() {
            stopTimer()
            _sessionState.value = KBSessionState.Completed

            // Record stats
            val summary = generateSummary()
            recordSessionStats(summary)

            Log.i(TAG, "Session completed: $correctCount/${_results.value.size} correct")
        }

        private fun recordSessionStats(summary: KBPracticeSessionSummary) {
            val sessionSummary =
                KBSessionSummary(
                    sessionId = "session-${System.currentTimeMillis()}",
                    roundType = KBRoundType.WRITTEN,
                    region = KBRegion.COLORADO,
                    totalQuestions = summary.totalQuestions,
                    totalCorrect = summary.correctAnswers,
                    totalPoints = summary.correctAnswers,
                    accuracy = summary.accuracy.toFloat(),
                    averageResponseTimeSeconds = summary.averageResponseTime.toFloat(),
                    durationSeconds = summary.durationSeconds.toFloat(),
                    completedAtMillis = System.currentTimeMillis(),
                )
            statsManager.recordSession(sessionSummary, mode)
        }

        private fun startTimer() {
            timerJob?.cancel()
            timerJob =
                viewModelScope.launch {
                    while (true) {
                        val remaining = _timeRemaining.value ?: break
                        if (remaining <= 0) {
                            endSession()
                            break
                        }
                        delay(TIMER_INTERVAL_MS)
                        _timeRemaining.value = remaining - 1
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

/**
 * Session state for the practice session.
 */
sealed class KBSessionState {
    /** Session not yet started. */
    data object NotStarted : KBSessionState()

    /** Session in progress, answering a question. */
    data object InProgress : KBSessionState()

    /** Showing the answer feedback. */
    data class ShowingAnswer(val isCorrect: Boolean) : KBSessionState()

    /** Session completed. */
    data object Completed : KBSessionState()
}
