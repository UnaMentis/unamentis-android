package com.unamentis.modules.knowledgebowl.core.engine

import android.util.Log
import com.unamentis.modules.knowledgebowl.core.validation.KBAnswerValidator
import com.unamentis.modules.knowledgebowl.core.validation.KBValidationResult
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBQuestionAttempt
import com.unamentis.modules.knowledgebowl.data.model.KBSession
import com.unamentis.modules.knowledgebowl.data.model.KBSessionConfig
import com.unamentis.modules.knowledgebowl.data.model.KBSessionSummary
import com.unamentis.modules.knowledgebowl.data.model.KBStudyMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the flow of Knowledge Bowl practice sessions.
 *
 * Handles question presentation, answer submission, timing, and scoring
 * for different practice modes.
 */
@Suppress("TooManyFunctions")
@Singleton
class KBPracticeEngine
    @Inject
    constructor(
        private val answerValidator: KBAnswerValidator,
    ) {
        companion object {
            private const val TAG = "KBPracticeEngine"
            private const val SPEED_DRILL_TIME_SECONDS = 300 // 5 minutes
        }

        // Session state
        private val _session = MutableStateFlow<KBSession?>(null)
        val session: StateFlow<KBSession?> = _session.asStateFlow()

        private val _currentQuestion = MutableStateFlow<KBQuestion?>(null)
        val currentQuestion: StateFlow<KBQuestion?> = _currentQuestion.asStateFlow()

        private val _questionIndex = MutableStateFlow(0)
        val questionIndex: StateFlow<Int> = _questionIndex.asStateFlow()

        private val _sessionState = MutableStateFlow(KBPracticeState.NOT_STARTED)
        val sessionState: StateFlow<KBPracticeState> = _sessionState.asStateFlow()

        private val _timeRemaining = MutableStateFlow(0L)
        val timeRemaining: StateFlow<Long> = _timeRemaining.asStateFlow()

        private val _lastValidationResult = MutableStateFlow<KBValidationResult?>(null)
        val lastValidationResult: StateFlow<KBValidationResult?> = _lastValidationResult.asStateFlow()

        // Internal state
        private var questions: List<KBQuestion> = emptyList()
        private var studyMode: KBStudyMode = KBStudyMode.DIAGNOSTIC
        private var questionStartTime: Long = 0
        private var sessionStartTime: Long = 0
        private var timerJob: Job? = null
        private val scope = CoroutineScope(Dispatchers.Main)

        /**
         * Start a new practice session with the given questions and configuration.
         *
         * @param sessionQuestions Questions for this session
         * @param config Session configuration
         */
        fun startSession(
            sessionQuestions: List<KBQuestion>,
            config: KBSessionConfig,
        ) {
            // Stop any existing timer
            stopTimer()

            // Initialize session
            val newSession = KBSession(id = KBSession.generateId(), config = config)
            _session.value = newSession

            // Shuffle questions unless diagnostic mode
            questions =
                if (config.studyMode == KBStudyMode.DIAGNOSTIC) {
                    sessionQuestions
                } else {
                    sessionQuestions.shuffled()
                }

            studyMode = config.studyMode
            _questionIndex.value = 0
            _sessionState.value = KBPracticeState.IN_PROGRESS
            sessionStartTime = System.currentTimeMillis()
            _lastValidationResult.value = null

            // Set time limit for speed mode
            if (config.studyMode == KBStudyMode.SPEED) {
                _timeRemaining.value = SPEED_DRILL_TIME_SECONDS.toLong()
                startTimer()
            } else if (config.timeLimitSeconds != null) {
                _timeRemaining.value = config.timeLimitSeconds.toLong()
                startTimer()
            }

            presentNextQuestion()
            Log.i(TAG, "Started ${config.studyMode.displayName} session with ${questions.size} questions")
        }

        /**
         * Submit an answer for the current question.
         *
         * @param answer The user's answer text
         * @return Validation result
         */
        @Suppress("ReturnCount")
        fun submitAnswer(answer: String): KBValidationResult? {
            val question = _currentQuestion.value ?: return null
            val currentSession = _session.value ?: return null

            if (_sessionState.value != KBPracticeState.IN_PROGRESS) {
                return null
            }

            val responseTime = (System.currentTimeMillis() - questionStartTime) / 1000f

            // Validate the answer
            val result = answerValidator.validate(answer, question)
            _lastValidationResult.value = result

            // Create and record the attempt
            val attempt =
                KBQuestionAttempt(
                    id = KBQuestionAttempt.generateId(),
                    questionId = question.id,
                    domain = question.domain,
                    timestamp = System.currentTimeMillis(),
                    userAnswer = answer,
                    responseTimeSeconds = responseTime,
                    wasCorrect = result.isCorrect,
                    pointsEarned = if (result.isCorrect) currentSession.config.pointsPerQuestion else 0,
                    roundType = currentSession.config.roundType,
                )

            currentSession.attempts.add(attempt)

            // Update state
            _sessionState.value = KBPracticeState.SHOWING_ANSWER

            val status = if (result.isCorrect) "correct" else "incorrect"
            Log.d(TAG, "Answer submitted: $status in ${"%.1f".format(responseTime)}s")

            return result
        }

        /**
         * Submit an MCQ selection for the current question.
         *
         * @param selectedIndex The index of the selected option (0-based)
         * @return Validation result
         */
        @Suppress("ReturnCount")
        fun submitMCQAnswer(selectedIndex: Int): KBValidationResult? {
            val question = _currentQuestion.value ?: return null
            val currentSession = _session.value ?: return null

            if (_sessionState.value != KBPracticeState.IN_PROGRESS) {
                return null
            }

            val responseTime = (System.currentTimeMillis() - questionStartTime) / 1000f

            // Validate the MCQ selection
            val result = answerValidator.validateMCQ(selectedIndex, question)
            _lastValidationResult.value = result

            // Get the selected answer text
            val selectedAnswer = question.mcqOptions?.getOrNull(selectedIndex) ?: ""

            // Create and record the attempt
            val attempt =
                KBQuestionAttempt(
                    id = KBQuestionAttempt.generateId(),
                    questionId = question.id,
                    domain = question.domain,
                    timestamp = System.currentTimeMillis(),
                    userAnswer = selectedAnswer,
                    responseTimeSeconds = responseTime,
                    wasCorrect = result.isCorrect,
                    pointsEarned = if (result.isCorrect) currentSession.config.pointsPerQuestion else 0,
                    roundType = currentSession.config.roundType,
                )

            currentSession.attempts.add(attempt)

            // Update state
            _sessionState.value = KBPracticeState.SHOWING_ANSWER

            val mcqStatus = if (result.isCorrect) "correct" else "incorrect"
            Log.d(TAG, "MCQ answer submitted: $mcqStatus in ${"%.1f".format(responseTime)}s")

            return result
        }

        /**
         * Skip the current question.
         */
        @Suppress("ReturnCount")
        fun skipQuestion() {
            val question = _currentQuestion.value ?: return
            val currentSession = _session.value ?: return

            if (_sessionState.value != KBPracticeState.IN_PROGRESS) {
                return
            }

            // Create a skipped attempt
            val attempt =
                KBQuestionAttempt(
                    id = KBQuestionAttempt.generateId(),
                    questionId = question.id,
                    domain = question.domain,
                    timestamp = System.currentTimeMillis(),
                    userAnswer = "",
                    responseTimeSeconds = 0f,
                    wasCorrect = false,
                    pointsEarned = 0,
                    roundType = currentSession.config.roundType,
                    wasSkipped = true,
                )

            currentSession.attempts.add(attempt)
            _lastValidationResult.value = null
            _sessionState.value = KBPracticeState.SHOWING_ANSWER

            Log.d(TAG, "Question skipped")
        }

        /**
         * Move to the next question after viewing the answer.
         */
        fun nextQuestion() {
            if (_sessionState.value != KBPracticeState.SHOWING_ANSWER) {
                return
            }

            _questionIndex.value++

            val isTimeUp = _timeRemaining.value <= 0 && studyMode == KBStudyMode.SPEED
            val isDone = _questionIndex.value >= questions.size

            if (isDone || isTimeUp) {
                endSession()
            } else {
                _sessionState.value = KBPracticeState.IN_PROGRESS
                presentNextQuestion()
            }
        }

        /**
         * Pause the session (if pausable).
         */
        fun pauseSession() {
            if (_sessionState.value == KBPracticeState.IN_PROGRESS) {
                _sessionState.value = KBPracticeState.PAUSED
                stopTimer()
                Log.d(TAG, "Session paused")
            }
        }

        /**
         * Resume a paused session.
         */
        fun resumeSession() {
            if (_sessionState.value == KBPracticeState.PAUSED) {
                _sessionState.value = KBPracticeState.IN_PROGRESS
                if (_timeRemaining.value > 0) {
                    startTimer()
                }
                questionStartTime = System.currentTimeMillis()
                Log.d(TAG, "Session resumed")
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
        fun generateSummary(): KBSessionSummary? {
            val currentSession = _session.value ?: return null
            val duration = System.currentTimeMillis() - sessionStartTime

            return currentSession.generateSummary(duration)
        }

        /**
         * Reset the engine state.
         */
        fun reset() {
            stopTimer()
            _session.value = null
            _currentQuestion.value = null
            _questionIndex.value = 0
            _sessionState.value = KBPracticeState.NOT_STARTED
            _timeRemaining.value = 0
            _lastValidationResult.value = null
            questions = emptyList()
            Log.d(TAG, "Engine reset")
        }

        // Private Helpers

        private fun presentNextQuestion() {
            if (_questionIndex.value < questions.size) {
                _currentQuestion.value = questions[_questionIndex.value]
                questionStartTime = System.currentTimeMillis()
            } else {
                endSession()
            }
        }

        private fun endSession() {
            stopTimer()
            _sessionState.value = KBPracticeState.COMPLETED

            val attempts = _session.value?.attempts ?: emptyList()
            val correctCount = attempts.count { it.wasCorrect }
            Log.i(TAG, "Session completed: $correctCount/${attempts.size} correct")
        }

        private fun startTimer() {
            timerJob?.cancel()
            timerJob =
                scope.launch {
                    while (_timeRemaining.value > 0 && _sessionState.value == KBPracticeState.IN_PROGRESS) {
                        delay(1000)
                        if (_sessionState.value == KBPracticeState.IN_PROGRESS) {
                            _timeRemaining.value--
                            if (_timeRemaining.value <= 0) {
                                endSession()
                            }
                        }
                    }
                }
        }

        private fun stopTimer() {
            timerJob?.cancel()
            timerJob = null
        }

        /**
         * Get question count for a given study mode.
         */
        fun questionCountForMode(mode: KBStudyMode): Int =
            when (mode) {
                KBStudyMode.DIAGNOSTIC -> 50
                KBStudyMode.TARGETED -> 25
                KBStudyMode.BREADTH -> 36
                KBStudyMode.SPEED -> 20
                KBStudyMode.COMPETITION -> 45
                KBStudyMode.TEAM -> 45
            }
    }
