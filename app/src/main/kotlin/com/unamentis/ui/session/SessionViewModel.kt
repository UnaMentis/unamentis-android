package com.unamentis.ui.session

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.R
import com.unamentis.core.config.ProviderConfig
import com.unamentis.core.config.RecordingMode
import com.unamentis.core.session.SessionManager
import com.unamentis.core.session.SessionMetrics
import com.unamentis.data.model.Session
import com.unamentis.data.model.SessionState
import com.unamentis.data.model.TranscriptEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
 * @property providerConfig Provider configuration for recording mode
 * @property sessionActivityState Session activity state for tab bar visibility control
 */
@HiltViewModel
class SessionViewModel
    @Inject
    constructor(
        private val sessionManager: SessionManager,
        private val providerConfig: ProviderConfig,
        private val sessionActivityState: SessionActivityState,
    ) : ViewModel() {
        init {
            // Update tab bar visibility based on session state
            viewModelScope.launch {
                combine(
                    sessionManager.currentSession,
                    sessionManager.sessionState,
                ) { session, state ->
                    val isActive = session != null
                    val isPaused = state == SessionState.PAUSED
                    Pair(isActive, isPaused)
                }.collect { (isActive, isPaused) ->
                    sessionActivityState.setSessionActive(isActive)
                    sessionActivityState.setPaused(isPaused)
                }
            }
        }

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
         * Whether the microphone is currently muted.
         */
        private val isMuted: StateFlow<Boolean> =
            sessionManager.isMuted
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = false,
                )

        /**
         * Set the microphone muted state.
         * When muted, the user can hear AI but won't trigger speech detection.
         */
        fun setMuted(muted: Boolean) {
            sessionManager.setMuted(muted)
        }

        /**
         * Whether a curriculum is loaded.
         */
        private val isCurriculumMode: StateFlow<Boolean> =
            sessionManager.isCurriculumMode
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = false,
                )

        /**
         * Current segment index within the topic.
         */
        private val currentSegmentIndex: StateFlow<Int> =
            sessionManager.currentSegmentIndex
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = 0,
                )

        /**
         * Total segments in the current topic.
         */
        private val totalSegments: StateFlow<Int> =
            sessionManager.totalSegments
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = 0,
                )

        /**
         * Whether there is a next topic available.
         */
        private val hasNextTopic: StateFlow<Boolean> =
            sessionManager.hasNextTopic
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = false,
                )

        /**
         * Audio level in dB for VU meter.
         */
        private val audioLevelDb: StateFlow<Float> =
            sessionManager.audioLevelDb
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = -60f,
                )

        /**
         * Derived UI state.
         */
        val uiState: StateFlow<SessionUiState> =
            combine(
                combine(sessionState, currentSession, transcript) { state, session, transcriptList ->
                    SessionCoreState(state, session, transcriptList)
                },
                combine(metrics, recordingMode, isManuallyRecording, isMuted) { m, rm, imr, muted ->
                    RecordingState(m, rm, imr, muted)
                },
                combine(isCurriculumMode, currentSegmentIndex, totalSegments, hasNextTopic) { icm, csi, ts, hnt ->
                    CurriculumUiState(icm, csi, ts, hnt)
                },
                audioLevelDb,
            ) { core, recording, curriculum, audioLevel ->
                SessionUiState(
                    sessionState = core.state,
                    isSessionActive = core.session != null,
                    isLoading =
                        core.state == SessionState.PROCESSING_UTTERANCE ||
                            core.state == SessionState.AI_THINKING,
                    canStart = core.state == SessionState.IDLE && core.session == null,
                    canPause =
                        core.state !in
                            listOf(
                                SessionState.IDLE,
                                SessionState.PAUSED,
                                SessionState.ERROR,
                            ) && core.session != null,
                    canResume = core.state == SessionState.PAUSED,
                    canStop = core.session != null,
                    transcript = core.transcriptList,
                    turnCount = core.session?.turnCount ?: 0,
                    metrics = recording.metrics,
                    statusMessageResId =
                        getStatusMessageResId(
                            core.state,
                            recording.mode,
                            recording.isManuallyRecording,
                        ),
                    recordingMode = recording.mode,
                    isManuallyRecording = recording.isManuallyRecording,
                    isMuted = recording.isMuted,
                    isCurriculumMode = curriculum.isCurriculumMode,
                    currentSegmentIndex = curriculum.currentSegmentIndex,
                    totalSegments = curriculum.totalSegments,
                    hasNextTopic = curriculum.hasNextTopic,
                    audioLevel = audioLevel,
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

        // ==========================================================================
        // CURRICULUM PLAYBACK CONTROLS
        // ==========================================================================

        /**
         * Go back one segment in curriculum playback.
         */
        fun goBackSegment() {
            viewModelScope.launch {
                sessionManager.goBackSegment().onFailure { error ->
                    Log.w("SessionViewModel", "Failed to go back segment: ${error.message}")
                }
            }
        }

        /**
         * Replay the current topic from the beginning.
         */
        fun replayTopic() {
            viewModelScope.launch {
                sessionManager.replayTopic().onFailure { error ->
                    Log.w("SessionViewModel", "Failed to replay topic: ${error.message}")
                }
            }
        }

        /**
         * Skip to the next topic in the curriculum.
         */
        fun nextTopic() {
            viewModelScope.launch {
                sessionManager.nextTopic().onFailure { error ->
                    Log.w("SessionViewModel", "Failed to skip to next topic: ${error.message}")
                }
            }
        }

        /**
         * Get status message resource ID for current state.
         */
        @StringRes
        private fun getStatusMessageResId(
            state: SessionState,
            mode: RecordingMode,
            @Suppress("UNUSED_PARAMETER") isManuallyRecording: Boolean,
        ): Int {
            return when (state) {
                SessionState.IDLE -> {
                    when (mode) {
                        RecordingMode.VAD -> R.string.session_status_listening
                        RecordingMode.PUSH_TO_TALK -> R.string.session_status_hold_to_speak
                        RecordingMode.TOGGLE -> R.string.session_status_tap_to_speak
                    }
                }
                SessionState.USER_SPEAKING -> {
                    if (mode == RecordingMode.PUSH_TO_TALK) {
                        R.string.session_status_recording
                    } else {
                        R.string.session_status_listening
                    }
                }
                SessionState.PROCESSING_UTTERANCE -> R.string.session_status_processing
                SessionState.AI_THINKING -> R.string.session_status_ai_thinking
                SessionState.AI_SPEAKING -> R.string.session_status_ai_speaking
                SessionState.INTERRUPTED -> R.string.session_status_interrupted
                SessionState.PAUSED -> R.string.session_status_paused
                SessionState.ERROR -> R.string.session_status_error
            }
        }

        override fun onCleared() {
            super.onCleared()
            // Reset tab bar visibility when leaving session screen
            sessionActivityState.reset()
            // SessionManager cleanup handled by its own lifecycle
        }
    }

/**
 * UI state for the Session screen.
 */
data class SessionUiState(
    val sessionState: SessionState = SessionState.IDLE,
    val isSessionActive: Boolean = false,
    val isLoading: Boolean = false,
    val canStart: Boolean = true,
    val canPause: Boolean = false,
    val canResume: Boolean = false,
    val canStop: Boolean = false,
    val transcript: List<TranscriptEntry> = emptyList(),
    val turnCount: Int = 0,
    val metrics: SessionMetrics = SessionMetrics(),
    @StringRes val statusMessageResId: Int = R.string.session_status_ready,
    val recordingMode: RecordingMode = RecordingMode.VAD,
    val isManuallyRecording: Boolean = false,
    val isMuted: Boolean = false,
    // Curriculum mode fields
    val isCurriculumMode: Boolean = false,
    val currentSegmentIndex: Int = 0,
    val totalSegments: Int = 0,
    val hasNextTopic: Boolean = false,
    // Audio level for VU meter
    val audioLevel: Float = -60f,
)

/**
 * Internal data class for combining core session state in the uiState flow.
 */
private data class SessionCoreState(
    val state: SessionState,
    val session: Session?,
    val transcriptList: List<TranscriptEntry>,
)

/**
 * Internal data class for combining recording state in the uiState flow.
 */
private data class RecordingState(
    val metrics: SessionMetrics,
    val mode: RecordingMode,
    val isManuallyRecording: Boolean,
    val isMuted: Boolean,
)

/**
 * Internal data class for combining curriculum state in the uiState flow.
 */
private data class CurriculumUiState(
    val isCurriculumMode: Boolean,
    val currentSegmentIndex: Int,
    val totalSegments: Int,
    val hasNextTopic: Boolean,
)
