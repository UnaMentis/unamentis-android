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
 * - Filter and sort sessions
 * - Search transcripts
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

        /**
         * Current filter settings.
         */
        private val _filterState = MutableStateFlow(SessionFilterState())
        val filterState: StateFlow<SessionFilterState> = _filterState.asStateFlow()

        /**
         * Current sort order.
         */
        private val _sortOrder = MutableStateFlow(SessionSortOrder.NEWEST_FIRST)
        val sortOrder: StateFlow<SessionSortOrder> = _sortOrder.asStateFlow()

        /**
         * Search query for transcript search.
         */
        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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
         * Combined UI state with filtering and sorting applied.
         */
        val uiState: StateFlow<HistoryUiState> =
            combine(
                sessions,
                selectedSession,
                transcript,
                combine(filterState, sortOrder, searchQuery) { f, s, q -> Triple(f, s, q) },
            ) { sessionsList, session, transcriptList, (filter, sort, query) ->
                val filteredSessions = applyFilters(sessionsList, filter, query)
                val sortedSessions = applySorting(filteredSessions, sort)
                HistoryUiState(
                    sessions = sortedSessions,
                    selectedSession = session,
                    transcript = transcriptList,
                    filterState = filter,
                    sortOrder = sort,
                    searchQuery = query,
                    isFiltering = filter.hasActiveFilters() || query.isNotBlank(),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = HistoryUiState(),
            )

        /**
         * Apply filters to the session list.
         */
        private fun applyFilters(
            sessions: List<Session>,
            filter: SessionFilterState,
            _searchQuery: String,
        ): List<Session> {
            var result = sessions

            // Filter by starred only
            if (filter.starredOnly) {
                result = result.filter { it.isStarred }
            }

            // Filter by date range
            if (filter.startDate != null) {
                result = result.filter { it.startTime >= filter.startDate }
            }
            if (filter.endDate != null) {
                result = result.filter { it.startTime <= filter.endDate }
            }

            // Filter by minimum duration
            if (filter.minDurationMinutes != null) {
                val minDurationMs = filter.minDurationMinutes * 60 * 1000
                result =
                    result.filter { session ->
                        session.endTime?.let { endTime ->
                            (endTime - session.startTime) >= minDurationMs
                        } ?: false
                    }
            }

            // Filter by curriculum
            if (filter.curriculumId != null) {
                result = result.filter { it.curriculumId == filter.curriculumId }
            }

            // Filter by minimum turn count
            if (filter.minTurns != null) {
                result = result.filter { it.turnCount >= filter.minTurns }
            }

            // Note: Search query filtering would require transcript data which is loaded separately
            // For now, we don't filter by search query at this level

            return result
        }

        /**
         * Apply sorting to the session list.
         */
        private fun applySorting(
            sessions: List<Session>,
            sortOrder: SessionSortOrder,
        ): List<Session> {
            return when (sortOrder) {
                SessionSortOrder.NEWEST_FIRST -> sessions.sortedByDescending { it.startTime }
                SessionSortOrder.OLDEST_FIRST -> sessions.sortedBy { it.startTime }
                SessionSortOrder.LONGEST_FIRST ->
                    sessions.sortedByDescending { session ->
                        session.endTime?.let { it - session.startTime } ?: 0
                    }
                SessionSortOrder.SHORTEST_FIRST ->
                    sessions.sortedBy { session ->
                        session.endTime?.let { it - session.startTime } ?: Long.MAX_VALUE
                    }
                SessionSortOrder.MOST_TURNS -> sessions.sortedByDescending { it.turnCount }
                SessionSortOrder.STARRED_FIRST ->
                    sessions.sortedWith(
                        compareByDescending<Session> { it.isStarred }
                            .thenByDescending { it.startTime },
                    )
            }
        }

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

        /**
         * Update the search query.
         */
        fun setSearchQuery(query: String) {
            _searchQuery.value = query
        }

        /**
         * Update the sort order.
         */
        fun setSortOrder(order: SessionSortOrder) {
            _sortOrder.value = order
        }

        /**
         * Update filter to show starred only.
         */
        fun setStarredOnly(starredOnly: Boolean) {
            _filterState.value = _filterState.value.copy(starredOnly = starredOnly)
        }

        /**
         * Set date range filter.
         */
        fun setDateRange(
            startDate: Long?,
            endDate: Long?,
        ) {
            _filterState.value =
                _filterState.value.copy(
                    startDate = startDate,
                    endDate = endDate,
                )
        }

        /**
         * Set minimum duration filter.
         */
        fun setMinDuration(minutes: Int?) {
            _filterState.value = _filterState.value.copy(minDurationMinutes = minutes)
        }

        /**
         * Set curriculum filter.
         */
        fun setCurriculumFilter(curriculumId: String?) {
            _filterState.value = _filterState.value.copy(curriculumId = curriculumId)
        }

        /**
         * Set minimum turns filter.
         */
        fun setMinTurns(minTurns: Int?) {
            _filterState.value = _filterState.value.copy(minTurns = minTurns)
        }

        /**
         * Clear all filters.
         */
        fun clearFilters() {
            _filterState.value = SessionFilterState()
            _searchQuery.value = ""
        }
    }

/**
 * UI state for History screen.
 */
data class HistoryUiState(
    val sessions: List<Session> = emptyList(),
    val selectedSession: Session? = null,
    val transcript: List<TranscriptEntry> = emptyList(),
    val filterState: SessionFilterState = SessionFilterState(),
    val sortOrder: SessionSortOrder = SessionSortOrder.NEWEST_FIRST,
    val searchQuery: String = "",
    val isFiltering: Boolean = false,
)

/**
 * Filter state for sessions.
 */
data class SessionFilterState(
    val starredOnly: Boolean = false,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val minDurationMinutes: Int? = null,
    val curriculumId: String? = null,
    val minTurns: Int? = null,
) {
    /**
     * Check if any filters are active.
     */
    fun hasActiveFilters(): Boolean {
        return starredOnly ||
            startDate != null ||
            endDate != null ||
            minDurationMinutes != null ||
            curriculumId != null ||
            minTurns != null
    }
}

/**
 * Sort order options for sessions.
 */
enum class SessionSortOrder(val displayName: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    LONGEST_FIRST("Longest First"),
    SHORTEST_FIRST("Shortest First"),
    MOST_TURNS("Most Turns"),
    STARRED_FIRST("Starred First"),
}
