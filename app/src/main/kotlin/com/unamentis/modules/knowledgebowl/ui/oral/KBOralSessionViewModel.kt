package com.unamentis.modules.knowledgebowl.ui.oral

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.modules.knowledgebowl.core.stats.KBStatsManager
import com.unamentis.modules.knowledgebowl.core.validation.KBAnswerValidator
import com.unamentis.modules.knowledgebowl.core.voice.KBVoiceCoordinator
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBQuestionAttempt
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import com.unamentis.modules.knowledgebowl.data.model.KBRegionalConfig
import com.unamentis.modules.knowledgebowl.data.model.KBRoundType
import com.unamentis.modules.knowledgebowl.data.model.KBSessionConfig
import com.unamentis.modules.knowledgebowl.data.model.KBSessionSummary
import com.unamentis.modules.knowledgebowl.data.model.KBStudyMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the oral session.
 */
enum class OralSessionState {
    NOT_STARTED,
    READING_QUESTION,
    CONFERENCE_TIME,
    LISTENING_FOR_ANSWER,
    SHOWING_FEEDBACK,
    COMPLETED,
}

/**
 * ViewModel for Knowledge Bowl oral practice sessions.
 *
 * Manages voice-first practice flow with TTS question reading,
 * conference timer, and STT answer capture.
 */
@Suppress("TooManyFunctions", "LongParameterList")
@HiltViewModel
class KBOralSessionViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val voiceCoordinator: KBVoiceCoordinator,
        private val answerValidator: KBAnswerValidator,
        private val statsManager: KBStatsManager,
    ) : ViewModel() {
        companion object {
            private const val CONFERENCE_TICK_MS = 100L
        }

        // Session configuration
        private var _questions: List<KBQuestion> = emptyList()
        val questions: List<KBQuestion> get() = _questions

        private var _regionalConfig: KBRegionalConfig = KBRegionalConfig.forRegion(KBRegion.COLORADO)
        val regionalConfig: KBRegionalConfig get() = _regionalConfig

        private var _sessionConfig: KBSessionConfig? = null

        // Session state
        private val _state = MutableStateFlow(OralSessionState.NOT_STARTED)
        val state: StateFlow<OralSessionState> = _state.asStateFlow()

        private val _currentQuestionIndex = MutableStateFlow(0)
        val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex.asStateFlow()

        private val _correctCount = MutableStateFlow(0)
        val correctCount: StateFlow<Int> = _correctCount.asStateFlow()

        private val _totalPoints = MutableStateFlow(0)
        val totalPoints: StateFlow<Int> = _totalPoints.asStateFlow()

        // TTS state
        private val _ttsProgress = MutableStateFlow(0f)
        val ttsProgress: StateFlow<Float> = _ttsProgress.asStateFlow()

        private val _isSpeaking = MutableStateFlow(false)
        val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

        // STT state
        private val _transcript = MutableStateFlow("")
        val transcript: StateFlow<String> = _transcript.asStateFlow()

        private val _isListening = MutableStateFlow(false)
        val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

        private val _sttError = MutableStateFlow<String?>(null)
        val sttError: StateFlow<String?> = _sttError.asStateFlow()

        // Conference state
        private val _conferenceTimeRemaining = MutableStateFlow(0.0)
        val conferenceTimeRemaining: StateFlow<Double> = _conferenceTimeRemaining.asStateFlow()

        private val _conferenceProgress = MutableStateFlow(1.0)
        val conferenceProgress: StateFlow<Double> = _conferenceProgress.asStateFlow()

        // Answer state
        private val _lastAnswerCorrect = MutableStateFlow<Boolean?>(null)
        val lastAnswerCorrect: StateFlow<Boolean?> = _lastAnswerCorrect.asStateFlow()

        private val _hasPermissions = MutableStateFlow(false)
        val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()

        // Session timing
        private var sessionStartTime: Long = 0L
        private var questionStartTime: Long = 0L

        // Attempts
        private val attempts = mutableListOf<KBQuestionAttempt>()

        // Jobs
        private var conferenceJob: Job? = null
        private var ttsProgressJob: Job? = null

        // Computed properties
        val currentQuestion: KBQuestion?
            get() =
                if (_currentQuestionIndex.value < _questions.size) {
                    _questions[_currentQuestionIndex.value]
                } else {
                    null
                }

        val progress: Float
            get() =
                if (_questions.isNotEmpty()) {
                    _currentQuestionIndex.value.toFloat() / _questions.size
                } else {
                    0f
                }

        val isLastQuestion: Boolean
            get() = _currentQuestionIndex.value >= _questions.size - 1

        val sessionAccuracy: Float
            get() =
                if (attempts.isNotEmpty()) {
                    _correctCount.value.toFloat() / attempts.size
                } else {
                    0f
                }

        val sessionDuration: Float
            get() =
                if (sessionStartTime > 0) {
                    (System.currentTimeMillis() - sessionStartTime) / 1000f
                } else {
                    0f
                }

        /**
         * Initialize the oral session with questions.
         */
        fun initialize(
            questions: List<KBQuestion>,
            config: KBSessionConfig,
        ) {
            _questions = questions
            _sessionConfig = config
            _regionalConfig = config.region.config
            _conferenceTimeRemaining.value = _regionalConfig.conferenceTime.toDouble()
            _state.value = OralSessionState.NOT_STARTED
            _currentQuestionIndex.value = 0
            _correctCount.value = 0
            _totalPoints.value = 0
            attempts.clear()
        }

        /**
         * Prepare voice services.
         */
        fun prepareServices() {
            viewModelScope.launch {
                voiceCoordinator.setup()
                _hasPermissions.value = voiceCoordinator.isReady.value

                // Observe voice coordinator state
                launch {
                    voiceCoordinator.isSpeaking.collect { speaking ->
                        _isSpeaking.value = speaking
                    }
                }
                launch {
                    voiceCoordinator.isListening.collect { listening ->
                        _isListening.value = listening
                    }
                }
                launch {
                    voiceCoordinator.currentTranscript.collect { text ->
                        _transcript.value = text
                    }
                }

                // Warm the audio cache
                if (_questions.isNotEmpty()) {
                    voiceCoordinator.warmCache(_questions)
                }
            }
        }

        /**
         * Start the oral session.
         */
        fun startSession() {
            if (!_hasPermissions.value) {
                _sttError.value = "Microphone and speech recognition permissions required"
                return
            }

            viewModelScope.launch {
                sessionStartTime = System.currentTimeMillis()
                _state.value = OralSessionState.READING_QUESTION
                readCurrentQuestion()
            }
        }

        /**
         * Read the current question via TTS.
         */
        private suspend fun readCurrentQuestion() {
            val question =
                currentQuestion ?: run {
                    endSession()
                    return
                }

            _state.value = OralSessionState.READING_QUESTION

            // Speak the question
            voiceCoordinator.speakQuestion(question)

            // Prefetch upcoming questions
            voiceCoordinator.prefetchUpcoming(_questions, _currentQuestionIndex.value)

            // Start conference time after speaking
            startConferenceTime()
        }

        /**
         * Start the conference timer.
         */
        private fun startConferenceTime() {
            val totalTime = _regionalConfig.conferenceTime.toDouble()
            _conferenceTimeRemaining.value = totalTime
            _conferenceProgress.value = 1.0
            _state.value = OralSessionState.CONFERENCE_TIME

            conferenceJob?.cancel()
            conferenceJob =
                viewModelScope.launch {
                    while (_conferenceTimeRemaining.value > 0) {
                        delay(CONFERENCE_TICK_MS)
                        _conferenceTimeRemaining.value -= CONFERENCE_TICK_MS / 1000.0
                        _conferenceProgress.value =
                            maxOf(0.0, _conferenceTimeRemaining.value / totalTime)
                    }

                    // Time's up - start listening phase
                    startListeningPhase()
                }
        }

        /**
         * Skip conference time and go directly to listening.
         */
        fun skipConference() {
            conferenceJob?.cancel()
            startListeningPhase()
        }

        /**
         * Start listening for the user's answer.
         */
        private fun startListeningPhase() {
            _transcript.value = ""
            _sttError.value = null
            questionStartTime = System.currentTimeMillis()
            _state.value = OralSessionState.LISTENING_FOR_ANSWER

            // Set up transcript callback
            voiceCoordinator.onTranscriptComplete { transcript ->
                // Auto-submit when utterance is complete
                if (transcript.isNotEmpty() && _state.value == OralSessionState.LISTENING_FOR_ANSWER) {
                    submitAnswer()
                }
            }
        }

        /**
         * Toggle listening state.
         */
        fun toggleListening() {
            viewModelScope.launch {
                if (_isListening.value) {
                    voiceCoordinator.stopListening()
                } else {
                    voiceCoordinator.startListening()
                }
            }
        }

        /**
         * Submit the current transcript as the answer.
         */
        fun submitAnswer() {
            viewModelScope.launch {
                // Stop listening
                voiceCoordinator.stopListening()

                val question = currentQuestion ?: return@launch
                val userAnswer = _transcript.value

                if (userAnswer.isEmpty()) {
                    _sttError.value = "No answer detected. Please try again."
                    return@launch
                }

                // Validate answer
                val result = answerValidator.validate(userAnswer, question)
                val responseTime = (System.currentTimeMillis() - questionStartTime) / 1000f
                val pointsEarned =
                    if (result.isCorrect) _regionalConfig.oralPointsPerCorrect else 0

                // Record attempt
                val attempt =
                    KBQuestionAttempt(
                        id = KBQuestionAttempt.generateId(),
                        questionId = question.id,
                        domain = question.domain,
                        userAnswer = userAnswer,
                        responseTimeSeconds = responseTime,
                        wasCorrect = result.isCorrect,
                        pointsEarned = pointsEarned,
                        roundType = KBRoundType.ORAL,
                        matchType =
                            result.matchType?.let {
                                com.unamentis.modules.knowledgebowl.data.model.KBMatchType.valueOf(it.name)
                            },
                    )
                attempts.add(attempt)

                if (result.isCorrect) {
                    _correctCount.value++
                    _totalPoints.value += pointsEarned
                }

                _lastAnswerCorrect.value = result.isCorrect
                _state.value = OralSessionState.SHOWING_FEEDBACK

                // Haptic feedback
                triggerHapticFeedback(result.isCorrect)

                // Speak feedback
                voiceCoordinator.speakFeedback(
                    isCorrect = result.isCorrect,
                    correctAnswer = question.answer.primary,
                    explanation = "",
                    question = question,
                )

                // Record domain attempt for stats
                statsManager.recordDomainAttempt(question.domain, result.isCorrect)
            }
        }

        /**
         * Move to the next question.
         */
        fun nextQuestion() {
            viewModelScope.launch {
                _transcript.value = ""
                _lastAnswerCorrect.value = null
                voiceCoordinator.resetTranscript()

                if (_currentQuestionIndex.value < _questions.size - 1) {
                    _currentQuestionIndex.value++
                    readCurrentQuestion()
                } else {
                    endSession()
                }
            }
        }

        /**
         * End the session and record results.
         */
        fun endSession() {
            viewModelScope.launch {
                conferenceJob?.cancel()
                ttsProgressJob?.cancel()

                voiceCoordinator.stopSpeaking()
                voiceCoordinator.stopListening()

                _state.value = OralSessionState.COMPLETED

                // Record session stats
                val config = _sessionConfig ?: return@launch

                // Create summary directly from our recorded attempts
                val averageResponseTime =
                    if (attempts.isNotEmpty()) {
                        attempts.map { it.responseTimeSeconds }.average().toFloat()
                    } else {
                        0f
                    }

                val summary =
                    KBSessionSummary(
                        sessionId = "oral-${System.currentTimeMillis()}",
                        roundType = KBRoundType.ORAL,
                        region = config.region,
                        totalQuestions = attempts.size,
                        totalCorrect = _correctCount.value,
                        totalPoints = _totalPoints.value,
                        accuracy = sessionAccuracy,
                        averageResponseTimeSeconds = averageResponseTime,
                        durationSeconds = sessionDuration,
                        completedAtMillis = System.currentTimeMillis(),
                    )
                statsManager.recordSession(summary, KBStudyMode.COMPETITION)

                // Speak completion message
                voiceCoordinator.speakCompletion(_correctCount.value, attempts.size)
            }
        }

        /**
         * Trigger haptic feedback for answer result.
         */
        @Suppress("DEPRECATION")
        private fun triggerHapticFeedback(isCorrect: Boolean) {
            val vibrator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager =
                        context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                            as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect =
                    if (isCorrect) {
                        VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                    } else {
                        VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50), -1)
                    }
                vibrator.vibrate(effect)
            } else {
                if (isCorrect) {
                    vibrator.vibrate(50)
                } else {
                    vibrator.vibrate(longArrayOf(0, 50, 50, 50), -1)
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            conferenceJob?.cancel()
            ttsProgressJob?.cancel()
        }
    }
