package com.unamentis.data.repository

import android.util.Log
import com.unamentis.data.local.dao.SessionDao
import com.unamentis.data.local.entity.SessionEntity
import com.unamentis.data.local.entity.TranscriptEntryEntity
import com.unamentis.data.model.Session
import com.unamentis.data.model.TranscriptEntry
import com.unamentis.data.remote.AddTurnRequest
import com.unamentis.data.remote.ApiClient
import com.unamentis.data.remote.ApiResult
import com.unamentis.data.remote.ApiSession
import com.unamentis.data.remote.BargeInRequest
import com.unamentis.data.remote.ChangeTopicRequest
import com.unamentis.data.remote.CreateSessionRequest
import com.unamentis.data.remote.FOVContext
import com.unamentis.data.remote.SessionFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync state for session data.
 */
enum class SyncState {
    /** Not synced with server. */
    NOT_SYNCED,

    /** Currently syncing. */
    SYNCING,

    /** Successfully synced. */
    SYNCED,

    /** Sync failed. */
    FAILED,
}

/**
 * Repository for session data management with server synchronization.
 *
 * Responsibilities:
 * - Save sessions to local database
 * - Retrieve session history
 * - Manage transcript entries
 * - Sync session data with server
 * - Handle offline mode with sync queue
 * - Export session data
 *
 * Architecture:
 * - Local-first: All operations work offline
 * - Background sync: Data synced when connection available
 * - Conflict resolution: Server wins for turn data
 *
 * @property sessionDao Room DAO for session data
 * @property apiClient API client for server communication
 */
@Singleton
class SessionRepository
    @Inject
    constructor(
        private val sessionDao: SessionDao,
        private val apiClient: ApiClient,
    ) {
        companion object {
            private const val TAG = "SessionRepository"
        }

        private val syncMutex = Mutex()

        // Sync state per session
        private val _syncStates = MutableStateFlow<Map<String, SyncState>>(emptyMap())
        val syncStates: StateFlow<Map<String, SyncState>> = _syncStates.asStateFlow()

        // Server session ID mapping (local ID -> server ID)
        private val serverSessionIds = mutableMapOf<String, String>()

        // Pending sync queue
        private val pendingTurns = mutableListOf<PendingTurn>()

        private data class PendingTurn(
            val localSessionId: String,
            val role: String,
            val content: String,
            val timestamp: Long,
            val audioUrl: String?,
            val latencyMs: Long?,
        )

        /**
         * Get all sessions ordered by start time (most recent first).
         */
        fun getAllSessions(): Flow<List<Session>> {
            return sessionDao.getAllSessions().map { entities ->
                entities.map { it.toModel() }
            }
        }

        /**
         * Get a specific session by ID.
         */
        suspend fun getSessionById(sessionId: String): Session? {
            return sessionDao.getSessionById(sessionId)?.toModel()
        }

        /**
         * Get sessions for a specific topic.
         */
        fun getSessionsByTopic(topicId: String): Flow<List<Session>> {
            return sessionDao.getSessionsByTopic(topicId).map { entities ->
                entities.map { it.toModel() }
            }
        }

        /**
         * Get transcript for a specific session.
         */
        suspend fun getTranscript(sessionId: String): List<TranscriptEntry> {
            return sessionDao.getTranscriptBySessionId(sessionId).map { it.toModel() }
        }

        /**
         * Save a session.
         */
        suspend fun saveSession(session: Session) {
            sessionDao.insertSession(session.toEntity())
        }

        /**
         * Update session statistics.
         *
         * @param sessionId Session identifier
         * @param stats Statistics to update
         */
        suspend fun updateSessionStats(
            sessionId: String,
            stats: com.unamentis.data.local.dao.SessionStatsUpdate,
        ) {
            sessionDao.updateSessionStats(
                id = sessionId,
                endTime = stats.endTime,
                durationSeconds = stats.durationSeconds,
                turnCount = stats.turnCount,
                interruptionCount = stats.interruptionCount,
                totalCost = stats.totalCost,
            )
        }

        /**
         * Save a transcript entry.
         */
        suspend fun saveTranscriptEntry(entry: TranscriptEntry) {
            sessionDao.insertTranscriptEntry(entry.toEntity())
        }

        /**
         * Delete a session and its transcript.
         */
        suspend fun deleteSession(sessionId: String) {
            sessionDao.deleteSession(sessionId)
        }

        /**
         * Delete all sessions.
         */
        suspend fun deleteAllSessions() {
            sessionDao.deleteAllTranscriptEntries()
            sessionDao.deleteAllSessions()
        }

        /**
         * Get all starred sessions.
         */
        fun getStarredSessions(): Flow<List<Session>> {
            return sessionDao.getStarredSessions().map { entities ->
                entities.map { it.toModel() }
            }
        }

        /**
         * Toggle starred status for a session.
         */
        suspend fun toggleStarred(sessionId: String) {
            val session = sessionDao.getSessionById(sessionId)
            if (session != null) {
                sessionDao.updateStarredStatus(sessionId, !session.isStarred)
            }
        }

        /**
         * Set starred status for a session.
         */
        suspend fun setStarred(
            sessionId: String,
            isStarred: Boolean,
        ) {
            sessionDao.updateStarredStatus(sessionId, isStarred)
        }

        /**
         * Export session as JSON.
         */
        suspend fun exportSessionAsJson(sessionId: String): String {
            val session = getSessionById(sessionId) ?: return "{}"
            val transcript = getTranscript(sessionId)

            return buildString {
                appendLine("{")
                appendLine("  \"sessionId\": \"${session.id}\",")
                appendLine("  \"startTime\": ${session.startTime},")
                appendLine("  \"endTime\": ${session.endTime},")
                appendLine("  \"turnCount\": ${session.turnCount},")
                appendLine("  \"transcript\": [")
                transcript.forEachIndexed { index, entry ->
                    val roleText = entry.role
                    val entryText = entry.text
                    val ts = entry.timestamp
                    append("    {\"role\": \"$roleText\", \"text\": \"$entryText\", \"timestamp\": $ts}")
                    if (index < transcript.lastIndex) append(",")
                    appendLine()
                }
                appendLine("  ]")
                appendLine("}")
            }
        }

        /**
         * Export session as plain text.
         */
        suspend fun exportSessionAsText(sessionId: String): String {
            val session = getSessionById(sessionId) ?: return ""
            val transcript = getTranscript(sessionId)

            return buildString {
                appendLine("Session: ${session.id}")
                appendLine("Date: ${java.util.Date(session.startTime)}")
                appendLine("Turn Count: ${session.turnCount}")
                appendLine()
                appendLine("Transcript:")
                appendLine("=".repeat(50))
                appendLine()

                transcript.forEach { entry ->
                    val speaker = if (entry.role == "user") "User" else "Assistant"
                    appendLine("[$speaker] ${entry.text}")
                    appendLine()
                }
            }
        }

        // =====================================================================
        // SERVER SYNC METHODS
        // =====================================================================

        /**
         * Create a new session with server sync.
         *
         * Creates locally first, then syncs to server in background.
         *
         * @param curriculumId Curriculum ID
         * @param topicId Optional starting topic ID
         * @return Created session
         */
        suspend fun createSessionWithSync(
            curriculumId: String,
            topicId: String? = null,
        ): Session {
            val sessionId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val session =
                Session(
                    id = sessionId,
                    curriculumId = curriculumId,
                    topicId = topicId,
                    startTime = now,
                )

            // Save locally first
            sessionDao.insertSession(session.toEntity())
            _syncStates.value = _syncStates.value + (sessionId to SyncState.NOT_SYNCED)

            Log.i(TAG, "Created local session: $sessionId")

            // Sync to server
            syncSessionToServer(session.toEntity())

            return session
        }

        /**
         * Start a session on the server.
         *
         * @param sessionId Session ID
         */
        suspend fun startSessionOnServer(sessionId: String) {
            val serverSessionId = serverSessionIds[sessionId] ?: sessionId

            when (val result = apiClient.startSession(serverSessionId)) {
                is ApiResult.Success<*> -> {
                    Log.i(TAG, "Session started on server: $serverSessionId")
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to start session on server: ${result.error.error}")
                }
                is ApiResult.NetworkError -> {
                    Log.w(TAG, "Network error starting session")
                }
            }
        }

        /**
         * Pause a session on the server.
         *
         * @param sessionId Session ID
         */
        suspend fun pauseSessionOnServer(sessionId: String) {
            val serverSessionId = serverSessionIds[sessionId] ?: sessionId

            when (val result = apiClient.pauseSession(serverSessionId)) {
                is ApiResult.Success<*> -> {
                    Log.i(TAG, "Session paused on server: $serverSessionId")
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to pause session: ${result.error.error}")
                }
                is ApiResult.NetworkError -> {
                    Log.w(TAG, "Network error pausing session")
                }
            }
        }

        /**
         * Resume a session on the server.
         *
         * @param sessionId Session ID
         */
        suspend fun resumeSessionOnServer(sessionId: String) {
            val serverSessionId = serverSessionIds[sessionId] ?: sessionId

            when (val result = apiClient.resumeSession(serverSessionId)) {
                is ApiResult.Success<*> -> {
                    Log.i(TAG, "Session resumed on server: $serverSessionId")
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to resume session: ${result.error.error}")
                }
                is ApiResult.NetworkError -> {
                    Log.w(TAG, "Network error resuming session")
                }
            }
        }

        /**
         * End a session on the server.
         *
         * @param sessionId Session ID
         */
        suspend fun endSessionOnServer(sessionId: String) {
            val serverSessionId = serverSessionIds[sessionId] ?: sessionId

            when (val result = apiClient.endSession(serverSessionId)) {
                is ApiResult.Success<*> -> {
                    _syncStates.value = _syncStates.value + (sessionId to SyncState.SYNCED)
                    Log.i(TAG, "Session ended on server: $serverSessionId")
                }
                is ApiResult.Error -> {
                    _syncStates.value = _syncStates.value + (sessionId to SyncState.FAILED)
                    Log.e(TAG, "Failed to end session on server: ${result.error.error}")
                }
                is ApiResult.NetworkError -> {
                    _syncStates.value = _syncStates.value + (sessionId to SyncState.FAILED)
                    Log.w(TAG, "Network error ending session")
                }
            }

            // Flush pending turns
            syncPendingTurns()
        }

        /**
         * Add a conversation turn with server sync.
         *
         * @param sessionId Session ID
         * @param role Speaker role ("user" or "assistant")
         * @param content Text content
         * @param audioUrl Optional audio URL
         * @param latencyMs Optional latency in milliseconds
         */
        suspend fun addConversationTurnWithSync(
            sessionId: String,
            role: String,
            content: String,
            audioUrl: String? = null,
            latencyMs: Long? = null,
        ) {
            val entryId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()

            // Store locally
            val entry =
                TranscriptEntryEntity(
                    id = entryId,
                    sessionId = sessionId,
                    role = role,
                    text = content,
                    timestamp = timestamp,
                )
            sessionDao.insertTranscriptEntry(entry)

            Log.d(TAG, "Added local turn: $role -> ${content.take(50)}...")

            // Create turn data for sync
            val turn =
                PendingTurn(
                    localSessionId = sessionId,
                    role = role,
                    content = content,
                    timestamp = timestamp,
                    audioUrl = audioUrl,
                    latencyMs = latencyMs,
                )

            // Queue for server sync
            val serverSessionId = serverSessionIds[sessionId]
            if (serverSessionId != null) {
                sendTurnToServer(serverSessionId, turn)
            } else {
                syncMutex.withLock {
                    pendingTurns.add(turn)
                }
            }
        }

        /**
         * Record a barge-in event on the server.
         *
         * @param sessionId Session ID
         * @param aiPositionMs AI playback position when interrupted
         * @param userAudioUrl Optional user audio URL
         */
        suspend fun recordBargeIn(
            sessionId: String,
            aiPositionMs: Long,
            userAudioUrl: String? = null,
        ) {
            val serverSessionId = serverSessionIds[sessionId] ?: sessionId

            val request =
                BargeInRequest(
                    timestamp = java.time.Instant.now().toString(),
                    aiPositionMs = aiPositionMs.toInt(),
                    userAudioUrl = userAudioUrl,
                )
            when (val result = apiClient.recordBargeIn(serverSessionId, request)) {
                is ApiResult.Success<*> -> {
                    Log.d(TAG, "Barge-in recorded on server")
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to record barge-in: ${result.error.error}")
                }
                is ApiResult.NetworkError -> {
                    Log.w(TAG, "Network error recording barge-in")
                }
            }
        }

        /**
         * Change the current topic within a session.
         *
         * @param sessionId Session ID
         * @param topicId New topic ID
         */
        suspend fun changeSessionTopic(
            sessionId: String,
            topicId: String,
        ) {
            val serverSessionId = serverSessionIds[sessionId] ?: sessionId

            val request = ChangeTopicRequest(topicId = topicId)
            when (val result = apiClient.changeSessionTopic(serverSessionId, request)) {
                is ApiResult.Success<*> -> {
                    Log.i(TAG, "Topic changed to: $topicId")
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to change topic: ${result.error.error}")
                }
                is ApiResult.NetworkError -> {
                    Log.w(TAG, "Network error changing topic")
                }
            }
        }

        /**
         * Get FOV context from server.
         *
         * @param sessionId Session ID
         * @return FOV context or null if unavailable
         */
        suspend fun getFOVContext(sessionId: String): FOVContext? {
            val serverSessionId = serverSessionIds[sessionId] ?: sessionId

            return when (val result = apiClient.getSessionContext(serverSessionId)) {
                is ApiResult.Success<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    result.data as? FOVContext
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to get FOV context: ${result.error.error}")
                    null
                }
                is ApiResult.NetworkError -> {
                    Log.w(TAG, "Network error getting FOV context")
                    null
                }
            }
        }

        /**
         * Sync all pending data to server.
         *
         * Call this when network becomes available.
         */
        suspend fun syncAll() {
            Log.i(TAG, "Starting full sync")

            // Sync sessions that haven't been synced
            val notSynced =
                _syncStates.value
                    .filter { it.value == SyncState.NOT_SYNCED || it.value == SyncState.FAILED }
                    .keys

            for (sessionId in notSynced) {
                val session = sessionDao.getSessionById(sessionId)
                if (session != null) {
                    syncSessionToServer(session)
                }
            }

            // Sync pending turns
            syncPendingTurns()
        }

        /**
         * Fetch sessions from server and merge with local.
         *
         * @param curriculumId Optional curriculum filter
         * @return Number of new sessions fetched
         */
        suspend fun fetchFromServer(curriculumId: String? = null): Int {
            val filter =
                if (curriculumId != null) {
                    SessionFilter(curriculumId = curriculumId)
                } else {
                    null
                }
            val result = apiClient.getSessions(filter)

            return when (result) {
                is ApiResult.Success<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val serverSessions = result.data as? List<ApiSession> ?: emptyList()
                    var newCount = 0

                    for (apiSession in serverSessions) {
                        val localSession = sessionDao.getSessionById(apiSession.id)
                        if (localSession == null) {
                            val entity =
                                SessionEntity(
                                    id = apiSession.id,
                                    curriculumId = apiSession.curriculumId,
                                    topicId = apiSession.topicId,
                                    startTime = parseTimestamp(apiSession.startedAt),
                                    endTime = apiSession.endedAt?.let { parseTimestamp(it) },
                                    durationSeconds = apiSession.durationSeconds.toLong(),
                                    turnCount = apiSession.turnCount,
                                    interruptionCount = 0,
                                    totalCost = 0.0,
                                    isStarred = false,
                                )
                            sessionDao.insertSession(entity)
                            serverSessionIds[apiSession.id] = apiSession.id
                            _syncStates.value = _syncStates.value + (apiSession.id to SyncState.SYNCED)
                            newCount++
                        }
                    }

                    Log.i(TAG, "Fetched $newCount new sessions from server")
                    newCount
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to fetch sessions: ${result.error.error}")
                    0
                }
                is ApiResult.NetworkError -> {
                    Log.w(TAG, "Network error fetching sessions")
                    0
                }
            }
        }

        /**
         * Delete a session from both local and server.
         *
         * @param sessionId Session ID
         */
        suspend fun deleteSessionWithSync(sessionId: String) {
            val serverSessionId = serverSessionIds[sessionId]

            sessionDao.deleteSession(sessionId)

            if (serverSessionId != null) {
                when (val result = apiClient.deleteSession(serverSessionId)) {
                    is ApiResult.Success<*> -> {
                        Log.i(TAG, "Session deleted on server: $serverSessionId")
                    }
                    is ApiResult.Error -> {
                        Log.e(TAG, "Failed to delete session on server: ${result.error.error}")
                    }
                    is ApiResult.NetworkError -> {
                        Log.w(TAG, "Network error deleting session")
                    }
                }
                serverSessionIds.remove(sessionId)
            }

            _syncStates.value = _syncStates.value - sessionId
        }

        private suspend fun syncSessionToServer(session: SessionEntity) {
            if (serverSessionIds.containsKey(session.id)) {
                return
            }

            _syncStates.value = _syncStates.value + (session.id to SyncState.SYNCING)

            val request =
                CreateSessionRequest(
                    curriculumId = session.curriculumId ?: "",
                    topicId = session.topicId,
                )
            when (val result = apiClient.createSession(request)) {
                is ApiResult.Success<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val serverSession = result.data as? ApiSession
                    if (serverSession != null) {
                        serverSessionIds[session.id] = serverSession.id
                        _syncStates.value = _syncStates.value + (session.id to SyncState.SYNCED)
                        Log.i(TAG, "Session synced to server: ${session.id} -> ${serverSession.id}")

                        syncPendingTurnsForSession(session.id)
                    }
                }
                is ApiResult.Error -> {
                    _syncStates.value = _syncStates.value + (session.id to SyncState.FAILED)
                    Log.e(TAG, "Failed to sync session: ${result.error.error}")
                }
                is ApiResult.NetworkError -> {
                    _syncStates.value = _syncStates.value + (session.id to SyncState.FAILED)
                    Log.w(TAG, "Network error syncing session")
                }
            }
        }

        private suspend fun syncPendingTurns() {
            val turns =
                syncMutex.withLock {
                    val copy = pendingTurns.toList()
                    pendingTurns.clear()
                    copy
                }

            for (turn in turns) {
                val serverSessionId = serverSessionIds[turn.localSessionId]
                if (serverSessionId != null) {
                    sendTurnToServer(serverSessionId, turn)
                } else {
                    syncMutex.withLock {
                        pendingTurns.add(turn)
                    }
                }
            }
        }

        private suspend fun syncPendingTurnsForSession(localSessionId: String) {
            val serverSessionId = serverSessionIds[localSessionId] ?: return

            val turnsToSync =
                syncMutex.withLock {
                    val matching = pendingTurns.filter { it.localSessionId == localSessionId }
                    pendingTurns.removeAll(matching.toSet())
                    matching
                }

            for (turn in turnsToSync) {
                sendTurnToServer(serverSessionId, turn)
            }
        }

        private suspend fun sendTurnToServer(
            serverSessionId: String,
            turn: PendingTurn,
        ) {
            val request =
                AddTurnRequest(
                    role = turn.role,
                    content = turn.content,
                    audioUrl = turn.audioUrl,
                    latencyMs = turn.latencyMs?.toInt(),
                )
            when (val result = apiClient.addConversationTurn(serverSessionId, request)) {
                is ApiResult.Success<*> -> {
                    Log.d(TAG, "Turn synced to server: ${turn.role}")
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to sync turn: ${result.error.error}")
                }
                is ApiResult.NetworkError -> {
                    Log.w(TAG, "Network error syncing turn")
                    syncMutex.withLock {
                        pendingTurns.add(turn.copy(localSessionId = serverSessionId))
                    }
                }
            }
        }

        private fun parseTimestamp(isoString: String?): Long {
            if (isoString == null) return System.currentTimeMillis()
            return try {
                java.time.Instant.parse(isoString).toEpochMilli()
            } catch (_: Exception) {
                // Failed to parse ISO timestamp, fall back to current time
                System.currentTimeMillis()
            }
        }
    }

/**
 * Extension: Convert Session model to entity.
 */
private fun Session.toEntity(): SessionEntity {
    return SessionEntity(
        id = id,
        topicId = topicId,
        curriculumId = curriculumId,
        startTime = startTime,
        endTime = endTime,
        durationSeconds = durationSeconds,
        turnCount = turnCount,
        interruptionCount = interruptionCount,
        totalCost = totalCost,
        isStarred = isStarred,
    )
}

/**
 * Extension: Convert SessionEntity to model.
 */
private fun SessionEntity.toModel(): Session {
    return Session(
        id = id,
        topicId = topicId,
        curriculumId = curriculumId,
        startTime = startTime,
        endTime = endTime,
        durationSeconds = durationSeconds,
        turnCount = turnCount,
        interruptionCount = interruptionCount,
        totalCost = totalCost,
        isStarred = isStarred,
    )
}

/**
 * Extension: Convert TranscriptEntry model to entity.
 */
private fun TranscriptEntry.toEntity(): TranscriptEntryEntity {
    return TranscriptEntryEntity(
        id = id,
        sessionId = sessionId,
        role = role,
        text = text,
        timestamp = timestamp,
        metadata = metadata,
    )
}

/**
 * Extension: Convert TranscriptEntryEntity to model.
 */
private fun TranscriptEntryEntity.toModel(): TranscriptEntry {
    return TranscriptEntry(
        id = id,
        sessionId = sessionId,
        role = role,
        text = text,
        timestamp = timestamp,
        metadata = metadata,
    )
}
