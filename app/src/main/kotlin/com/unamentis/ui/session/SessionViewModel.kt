package com.unamentis.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
         * Derived UI state.
         */
        val uiState: StateFlow<SessionUiState> =
            combine(
                sessionState,
                currentSession,
                transcript,
                metrics,
            ) { state, session, transcriptList, sessionMetrics ->
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
                    statusMessage = getStatusMessage(state),
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
                sessionManager.startSession(curriculumId, topicId)
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
         * Get status message for current state.
         */
        private fun getStatusMessage(state: SessionState): String {
            return when (state) {
                SessionState.IDLE -> "Ready to start"
                SessionState.USER_SPEAKING -> "Listening..."
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
)
