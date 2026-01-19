package com.unamentis.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.core.export.ExportFormat
import com.unamentis.core.export.ExportResult
import com.unamentis.core.export.SessionExporter
import com.unamentis.data.model.Session
import com.unamentis.data.model.TranscriptEntry
import com.unamentis.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
        private val sessionExporter: SessionExporter,
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
         * Toggle starred status for a session.
         */
        fun toggleStarred(sessionId: String) {
            viewModelScope.launch {
                sessionRepository.toggleStarred(sessionId)
                // Refresh the selected session if it's the one being toggled
                if (_selectedSessionId.value == sessionId) {
                    _selectedSession.value = sessionRepository.getSessionById(sessionId)
                }
            }
        }

        /**
         * Export current session in the specified format.
         *
         * @param format The export format to use
         * @return ExportResult with the exported content or an error
         */
        fun exportSession(format: ExportFormat): ExportResult {
            val session =
                _selectedSession.value
                    ?: return ExportResult.Error("No session selected")
            val transcriptList = _transcript.value
            return sessionExporter.export(session, transcriptList, format)
        }
    }

/**
 * UI state for History screen.
 */
data class HistoryUiState(
    val sessions: List<Session> = emptyList(),
    val selectedSession: Session? = null,
    val transcript: List<TranscriptEntry> = emptyList(),
)
