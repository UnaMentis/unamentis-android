package com.unamentis.data.repository

import com.unamentis.data.local.dao.SessionDao
import com.unamentis.data.local.entity.SessionEntity
import com.unamentis.data.local.entity.TranscriptEntryEntity
import com.unamentis.data.model.Session
import com.unamentis.data.model.TranscriptEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for session data management.
 *
 * Responsibilities:
 * - Save sessions to local database
 * - Retrieve session history
 * - Manage transcript entries
 * - Export session data
 *
 * @property sessionDao Room DAO for session data
 */
@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao
) {

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
     * Get sessions for a specific curriculum.
     */
    fun getSessionsByCurriculum(curriculumId: String): Flow<List<Session>> {
        return sessionDao.getSessionsByCurriculum(curriculumId).map { entities ->
            entities.map { it.toModel() }
        }
    }

    /**
     * Get transcript for a specific session.
     */
    fun getTranscript(sessionId: String): Flow<List<TranscriptEntry>> {
        return sessionDao.getTranscriptEntries(sessionId).map { entities ->
            entities.map { it.toModel() }
        }
    }

    /**
     * Save a session.
     */
    suspend fun saveSession(session: Session) {
        sessionDao.insertSession(session.toEntity())
    }

    /**
     * Update a session.
     */
    suspend fun updateSession(session: Session) {
        sessionDao.updateSession(session.toEntity())
    }

    /**
     * Save a transcript entry.
     */
    suspend fun saveTranscriptEntry(entry: TranscriptEntry) {
        sessionDao.insertTranscriptEntry(entry.toEntity())
    }

    /**
     * Save multiple transcript entries.
     */
    suspend fun saveTranscriptEntries(entries: List<TranscriptEntry>) {
        sessionDao.insertTranscriptEntries(entries.map { it.toEntity() })
    }

    /**
     * Delete a session and its transcript.
     */
    suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteSession(sessionId)
        sessionDao.deleteTranscriptEntries(sessionId)
    }

    /**
     * Get total number of sessions.
     */
    suspend fun getSessionCount(): Int {
        return sessionDao.getSessionCount()
    }

    /**
     * Get total number of turns across all sessions.
     */
    suspend fun getTotalTurns(): Int {
        return sessionDao.getTotalTurns()
    }

    /**
     * Get sessions within a date range.
     */
    fun getSessionsInRange(startTime: Long, endTime: Long): Flow<List<Session>> {
        return sessionDao.getSessionsInRange(startTime, endTime).map { entities ->
            entities.map { it.toModel() }
        }
    }

    /**
     * Export session as JSON.
     */
    suspend fun exportSessionAsJson(sessionId: String): String {
        val session = getSessionById(sessionId) ?: return "{}"
        val transcript = sessionDao.getTranscriptEntries(sessionId)
            .map { entities -> entities.map { it.toModel() } }

        // TODO: Serialize to JSON using kotlinx.serialization
        return buildString {
            appendLine("{")
            appendLine("  \"sessionId\": \"${session.id}\",")
            appendLine("  \"startTime\": ${session.startTime},")
            appendLine("  \"endTime\": ${session.endTime},")
            appendLine("  \"totalTurns\": ${session.totalTurns},")
            appendLine("  \"transcript\": [")
            // Add transcript entries
            appendLine("  ]")
            appendLine("}")
        }
    }

    /**
     * Export session as plain text.
     */
    suspend fun exportSessionAsText(sessionId: String): String {
        val session = getSessionById(sessionId) ?: return ""
        val transcriptEntities = sessionDao.getTranscriptEntries(sessionId)

        return buildString {
            appendLine("Session: ${session.id}")
            appendLine("Date: ${java.util.Date(session.startTime)}")
            appendLine("Total Turns: ${session.totalTurns}")
            appendLine()
            appendLine("Transcript:")
            appendLine("=" .repeat(50))
            appendLine()

            // Collect transcript synchronously for export
            // Note: This is simplified - in production would use first() on Flow
            appendLine("(Transcript entries would be listed here)")
        }
    }
}

/**
 * Extension: Convert Session model to entity.
 */
private fun Session.toEntity(): SessionEntity {
    return SessionEntity(
        id = id,
        curriculumId = curriculumId,
        currentTopicId = currentTopicId,
        startTime = startTime,
        endTime = endTime,
        totalTurns = totalTurns
    )
}

/**
 * Extension: Convert SessionEntity to model.
 */
private fun SessionEntity.toModel(): Session {
    return Session(
        id = id,
        curriculumId = curriculumId,
        currentTopicId = currentTopicId,
        startTime = startTime,
        endTime = endTime,
        totalTurns = totalTurns
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
        content = content,
        timestamp = timestamp
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
        content = content,
        timestamp = timestamp
    )
}
