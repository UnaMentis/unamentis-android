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
     */
    suspend fun updateSessionStats(
        sessionId: String,
        endTime: Long,
        durationSeconds: Long,
        turnCount: Int,
        interruptionCount: Int,
        totalCost: Double
    ) {
        sessionDao.updateSessionStats(
            id = sessionId,
            endTime = endTime,
            durationSeconds = durationSeconds,
            turnCount = turnCount,
            interruptionCount = interruptionCount,
            totalCost = totalCost
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
                append("    {\"role\": \"${entry.role}\", \"text\": \"${entry.text}\", \"timestamp\": ${entry.timestamp}}")
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
        totalCost = totalCost
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
        totalCost = totalCost
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
        metadata = metadata
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
        metadata = metadata
    )
}
