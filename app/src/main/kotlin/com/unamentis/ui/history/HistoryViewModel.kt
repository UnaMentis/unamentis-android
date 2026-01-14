package com.unamentis.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.data.model.Session
import com.unamentis.data.model.TranscriptEntry
import com.unamentis.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the History screen.
 *
 * Responsibilities:
 * - Display session history list
 * - Show session details with transcript
 * - Export session data (JSON, text)
 * - Display session metrics summary
 * - Delete sessions
 *
 * @property sessionRepository Repository for session data
 */
@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
    ) : ViewModel() {
        /**
         * All sessions from database.
         */
        private val sessions: StateFlow<List<Session>> =
            sessionRepository
                .getAllSessions()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        /**
         * Selected session for detail view.
         */
        private val _selectedSessionId = MutableStateFlow<String?>(null)
        val selectedSessionId: StateFlow<String?> = _selectedSessionId.asStateFlow()

        /**
         * Selected session details.
         */
        private val _selectedSession = MutableStateFlow<Session?>(null)
        val selectedSession: StateFlow<Session?> = _selectedSession.asStateFlow()

        /**
         * Transcript for selected session.
         */
        private val _transcript = MutableStateFlow<List<TranscriptEntry>>(emptyList())
        val transcript: StateFlow<List<TranscriptEntry>> = _transcript.asStateFlow()

        init {
            // Load session details when selection changes
            viewModelScope.launch {
                selectedSessionId.collect { id ->
                    if (id != null) {
                        _selectedSession.value = sessionRepository.getSessionById(id)
                        _transcript.value = sessionRepository.getTranscript(id)
                    } else {
                        _selectedSession.value = null
                        _transcript.value = emptyList()
                    }
                }
            }
        }

        /**
         * Combined UI state.
         */
        val uiState: StateFlow<HistoryUiState> =
            combine(
                sessions,
                selectedSession,
                transcript,
            ) { sessionsList, session, transcriptList ->
                HistoryUiState(
                    sessions = sessionsList.sortedByDescending { it.startTime },
                    selectedSession = session,
                    transcript = transcriptList,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = HistoryUiState(),
            )

        /**
         * Select session for detail view.
         */
        fun selectSession(sessionId: String) {
            _selectedSessionId.value = sessionId
        }

        /**
         * Clear session selection (back to list).
         */
        fun clearSelection() {
            _selectedSessionId.value = null
        }

        /**
         * Delete session.
         */
        fun deleteSession(sessionId: String) {
            viewModelScope.launch {
                sessionRepository.deleteSession(sessionId)
                if (_selectedSessionId.value == sessionId) {
                    _selectedSessionId.value = null
                }
            }
        }

        /**
         * Export session as JSON.
         */
        fun exportAsJson(
            session: Session,
            transcript: List<TranscriptEntry>,
        ): String {
            return buildString {
                appendLine("{")
                appendLine("  \"sessionId\": \"${session.id}\",")
                appendLine("  \"curriculumId\": ${session.curriculumId?.let { "\"$it\"" } ?: "null"},")
                appendLine("  \"topicId\": ${session.topicId?.let { "\"$it\"" } ?: "null"},")
                appendLine("  \"startTime\": ${session.startTime},")
                appendLine("  \"endTime\": ${session.endTime},")
                appendLine("  \"turnCount\": ${session.turnCount},")
                appendLine("  \"transcript\": [")
                transcript.forEachIndexed { index, entry ->
                    appendLine("    {")
                    appendLine("      \"id\": \"${entry.id}\",")
                    appendLine("      \"role\": \"${entry.role}\",")
                    appendLine("      \"text\": \"${entry.text.replace("\"", "\\\"")}\",")
                    appendLine("      \"timestamp\": ${entry.timestamp}")
                    append("    }")
                    if (index < transcript.size - 1) {
                        appendLine(",")
                    } else {
                        appendLine()
                    }
                }
                appendLine("  ]")
                append("}")
            }
        }

        /**
         * Export session as plain text.
         */
        fun exportAsText(
            session: Session,
            transcript: List<TranscriptEntry>,
        ): String {
            return buildString {
                appendLine("Session Export")
                appendLine("=" * 50)
                appendLine()
                appendLine("Session ID: ${session.id}")
                session.curriculumId?.let { appendLine("Curriculum ID: $it") }
                session.topicId?.let { appendLine("Topic ID: $it") }
                appendLine(
                    "Start Time: ${java.text.SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        java.util.Locale.getDefault(),
                    ).format(java.util.Date(session.startTime))}",
                )
                session.endTime?.let {
                    appendLine(
                        "End Time: ${java.text.SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss",
                            java.util.Locale.getDefault(),
                        ).format(java.util.Date(it))}",
                    )
                }
                appendLine("Turn Count: ${session.turnCount}")
                appendLine()
                appendLine("Transcript")
                appendLine("-" * 50)
                appendLine()

                transcript.forEach { entry ->
                    val role = if (entry.role == "user") "You" else "AI Tutor"
                    val timestamp =
                        java.text.SimpleDateFormat(
                            "HH:mm:ss",
                            java.util.Locale.getDefault(),
                        ).format(java.util.Date(entry.timestamp))
                    appendLine("[$timestamp] $role:")
                    appendLine(entry.text)
                    appendLine()
                }
            }
        }

        private operator fun String.times(count: Int): String = this.repeat(count)
    }

/**
 * UI state for History screen.
 */
data class HistoryUiState(
    val sessions: List<Session> = emptyList(),
    val selectedSession: Session? = null,
    val transcript: List<TranscriptEntry> = emptyList(),
)
