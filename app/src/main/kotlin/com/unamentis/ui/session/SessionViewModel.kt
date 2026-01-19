package com.unamentis.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.core.config.ProviderConfig
import com.unamentis.core.config.RecordingMode
import com.unamentis.core.session.SessionManager
import com.unamentis.core.session.SessionMetrics
import com.unamentis.data.model.Session
import com.unamentis.data.model.SessionState
import com.unamentis.data.model.TranscriptEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Session screen.
 *
 * Responsibilities:
 * - Manage session lifecycle (start, pause, resume, stop)
 * - Expose session state to UI
 * - Handle user actions (mute, send text, etc.)
 * - Provide audio level visualization data
 *
 * StateFlows:
 * - sessionState: Current session state machine state
 * - transcript: List of transcript entries
 * - metrics: Real-time session metrics
 * - uiState: Derived UI state with computed properties
 *
 * @property sessionManager Core session orchestrator
 */
@HiltViewModel
class SessionViewModel
    @Inject
    constructor(
        private val sessionManager: SessionManager,
        private val providerConfig: ProviderConfig,
    ) : ViewModel() {
        /**
         * Session state from SessionManager.
         */
        val sessionState: StateFlow<SessionState> =
            sessionManager.sessionState
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = SessionState.IDLE,
                )

        /**
         * Current session data.
         */
        val currentSession: StateFlow<Session?> =
            sessionManager.currentSession
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = null,
                )

        /**
         * Transcript entries.
         */
        val transcript: StateFlow<List<TranscriptEntry>> =
            sessionManager.transcript
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        /**
         * Session metrics.
         */
        val metrics: StateFlow<SessionMetrics> =
            sessionManager.metrics
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = SessionMetrics(),
                )

        /**
         * Current recording mode from user settings.
         */
        val recordingMode: StateFlow<RecordingMode> =
            providerConfig.recordingMode
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = RecordingMode.VAD,
                )

        /**
         * Whether manual recording is currently active (for PTT/Toggle modes).
         */
        val isManuallyRecording: StateFlow<Boolean> =
            sessionManager.isManuallyRecording
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = false,
                )

        /**
         * Derived UI state.
         */
        val uiState: StateFlow<SessionUiState> =
            combine(
                sessionState,
                currentSession,
                transcript,
                combine(metrics, recordingMode, isManuallyRecording) { m, rm, imr -> Triple(m, rm, imr) },
            ) { state, session, transcriptList, (sessionMetrics, mode, manuallyRecording) ->
                SessionUiState(
                    sessionState = state,
                    isSessionActive = session != null,
                    canStart = state == SessionState.IDLE && session == null,
                    canPause = state !in listOf(SessionState.IDLE, SessionState.PAUSED, SessionState.ERROR) && session != null,
                    canResume = state == SessionState.PAUSED,
                    canStop = session != null,
                    transcript = transcriptList,
                    turnCount = session?.turnCount ?: 0,
                    metrics = sessionMetrics,
                    statusMessage = getStatusMessage(state, mode, manuallyRecording),
                    recordingMode = mode,
                    isManuallyRecording = manuallyRecording,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = SessionUiState(),
            )

        /**
         * Start a new session.
         *
         * @param curriculumId Optional curriculum to load
         * @param topicId Optional specific topic to start with
         */
        fun startSession(
            curriculumId: String? = null,
            topicId: String? = null,
        ) {
            viewModelScope.launch {
                val mode = providerConfig.getRecordingMode()
                sessionManager.startSession(curriculumId, topicId, mode)
            }
        }

        /**
         * Pause the current session.
         */
        fun pauseSession() {
            viewModelScope.launch {
                sessionManager.pauseSession()
            }
        }

        /**
         * Resume a paused session.
         */
        fun resumeSession() {
            viewModelScope.launch {
                sessionManager.resumeSession()
            }
        }

        /**
         * Stop the current session.
         */
        fun stopSession() {
            viewModelScope.launch {
                sessionManager.stopSession()
            }
        }

        /**
         * Send a text message (for testing or text input).
         */
        fun sendTextMessage(text: String) {
            viewModelScope.launch {
                sessionManager.sendTextMessage(text)
            }
        }

        /**
         * Start manual recording (for PUSH_TO_TALK and TOGGLE modes).
         */
        fun startManualRecording() {
            viewModelScope.launch {
                sessionManager.startManualRecording()
            }
        }

        /**
         * Stop manual recording (for PUSH_TO_TALK and TOGGLE modes).
         */
        fun stopManualRecording() {
            viewModelScope.launch {
                sessionManager.stopManualRecording()
            }
        }

        /**
         * Toggle manual recording (convenience for TOGGLE mode).
         */
        fun toggleManualRecording() {
            viewModelScope.launch {
                sessionManager.toggleManualRecording()
            }
        }

        /**
         * Set initial context from deep link.
         * If a curriculum/topic is specified and no session is active, auto-start a session.
         *
         * @param curriculumId The curriculum ID from deep link
         * @param topicId The topic ID from deep link
         */
        fun setInitialContext(
            curriculumId: String?,
            topicId: String?,
        ) {
            if (curriculumId == null && topicId == null) return

            viewModelScope.launch {
                // Only auto-start if we have context and no active session
                val currentState = sessionState.value
                if (currentState == SessionState.IDLE) {
                    val mode = providerConfig.getRecordingMode()
                    sessionManager.startSession(curriculumId, topicId, mode)
                }
            }
        }

        /**
         * Get status message for current state.
         */
        private fun getStatusMessage(
            state: SessionState,
            mode: RecordingMode,
            isManuallyRecording: Boolean,
        ): String {
            return when (state) {
                SessionState.IDLE -> {
                    when (mode) {
                        RecordingMode.VAD -> "Listening..."
                        RecordingMode.PUSH_TO_TALK -> "Hold to speak"
                        RecordingMode.TOGGLE -> "Tap to speak"
                    }
                }
                SessionState.USER_SPEAKING -> {
                    if (mode == RecordingMode.PUSH_TO_TALK) "Recording... release to send" else "Listening..."
                }
                SessionState.PROCESSING_UTTERANCE -> "Processing..."
                SessionState.AI_THINKING -> "Thinking..."
                SessionState.AI_SPEAKING -> "Speaking..."
                SessionState.INTERRUPTED -> "Interrupted"
                SessionState.PAUSED -> "Paused"
                SessionState.ERROR -> "Error occurred"
            }
        }

        override fun onCleared() {
            super.onCleared()
            // SessionManager cleanup handled by its own lifecycle
        }
    }

/**
 * UI state for the Session screen.
 */
data class SessionUiState(
    val sessionState: SessionState = SessionState.IDLE,
    val isSessionActive: Boolean = false,
    val canStart: Boolean = true,
    val canPause: Boolean = false,
    val canResume: Boolean = false,
    val canStop: Boolean = false,
    val transcript: List<TranscriptEntry> = emptyList(),
    val turnCount: Int = 0,
    val metrics: SessionMetrics = SessionMetrics(),
    val statusMessage: String = "Ready to start",
    val recordingMode: RecordingMode = RecordingMode.VAD,
    val isManuallyRecording: Boolean = false,
)
