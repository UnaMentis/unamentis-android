package com.unamentis.data.local.dao

import androidx.room.*
import com.unamentis.data.local.entity.SessionEntity
import com.unamentis.data.local.entity.TranscriptEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for sessions and transcripts.
 *
 * Provides methods to query and manage session history.
 */
@Dao
interface SessionDao {
    /**
     * Get all sessions ordered by start time (most recent first).
     *
     * @return Flow of session list
     */
    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    /**
     * Get a specific session by ID.
     *
     * @param id Session identifier
     * @return Session entity or null if not found
     */
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: String): SessionEntity?

    /**
     * Get sessions for a specific topic.
     *
     * @param topicId Topic identifier
     * @return Flow of session list
     */
    @Query("SELECT * FROM sessions WHERE topicId = :topicId ORDER BY startTime DESC")
    fun getSessionsByTopic(topicId: String): Flow<List<SessionEntity>>

    /**
     * Insert or update a session.
     *
     * @param session Session to insert/update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    /**
     * Update session end time and stats.
     *
     * @param id Session identifier
     * @param endTime End timestamp
     * @param durationSeconds Session duration
     * @param turnCount Number of turns
     * @param interruptionCount Number of interruptions
     * @param totalCost Total cost
     */
    @Query(
        """
        UPDATE sessions
        SET endTime = :endTime,
            durationSeconds = :durationSeconds,
            turnCount = :turnCount,
            interruptionCount = :interruptionCount,
            totalCost = :totalCost
        WHERE id = :id
        """,
    )
    suspend fun updateSessionStats(
        id: String,
        endTime: Long,
        durationSeconds: Long,
        turnCount: Int,
        interruptionCount: Int,
        totalCost: Double,
    )

    /**
     * Get transcript entries for a session.
     *
     * @param sessionId Session identifier
     * @return List of transcript entries ordered by timestamp
     */
    @Query("SELECT * FROM transcript_entries WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getTranscriptBySessionId(sessionId: String): List<TranscriptEntryEntity>

    /**
     * Insert a transcript entry.
     *
     * @param entry Transcript entry to insert
     */
    @Insert
    suspend fun insertTranscriptEntry(entry: TranscriptEntryEntity)

    /**
     * Delete a session and its transcript entries.
     *
     * @param id Session identifier
     */
    @Transaction
    suspend fun deleteSession(id: String) {
        deleteTranscriptEntries(id)
        deleteSessionById(id)
    }

    @Query("DELETE FROM transcript_entries WHERE sessionId = :sessionId")
    suspend fun deleteTranscriptEntries(sessionId: String)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSessionById(id: String)

    /**
     * Delete all sessions and transcripts.
     */
    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM transcript_entries")
    suspend fun deleteAllTranscriptEntries()

    /**
     * Get all starred sessions.
     *
     * @return Flow of starred session list
     */
    @Query("SELECT * FROM sessions WHERE isStarred = 1 ORDER BY startTime DESC")
    fun getStarredSessions(): Flow<List<SessionEntity>>

    /**
     * Update a session's starred status.
     *
     * @param id Session identifier
     * @param isStarred New starred status
     */
    @Query("UPDATE sessions SET isStarred = :isStarred WHERE id = :id")
    suspend fun updateStarredStatus(
        id: String,
        isStarred: Boolean,
    )

    /**
     * Get sessions within a date range.
     *
     * @param startTimestamp Start of date range
     * @param endTimestamp End of date range
     * @return Flow of session list
     */
    @Query(
        """
        SELECT * FROM sessions
        WHERE startTime >= :startTimestamp AND startTime <= :endTimestamp
        ORDER BY startTime DESC
        """,
    )
    fun getSessionsInDateRange(
        startTimestamp: Long,
        endTimestamp: Long,
    ): Flow<List<SessionEntity>>

    /**
     * Get sessions by curriculum.
     *
     * @param curriculumId Curriculum identifier
     * @return Flow of session list
     */
    @Query("SELECT * FROM sessions WHERE curriculumId = :curriculumId ORDER BY startTime DESC")
    fun getSessionsByCurriculum(curriculumId: String): Flow<List<SessionEntity>>

    /**
     * Get sessions with minimum duration.
     *
     * @param minDurationSeconds Minimum duration in seconds
     * @return Flow of session list
     */
    @Query("SELECT * FROM sessions WHERE durationSeconds >= :minDurationSeconds ORDER BY startTime DESC")
    fun getSessionsByMinDuration(minDurationSeconds: Long): Flow<List<SessionEntity>>

    /**
     * Search transcript entries for text.
     *
     * @param query Search query
     * @return List of sessions containing matching transcript entries
     */
    @Query(
        """
        SELECT DISTINCT s.* FROM sessions s
        INNER JOIN transcript_entries t ON s.id = t.sessionId
        WHERE t.text LIKE '%' || :query || '%'
        ORDER BY s.startTime DESC
        """,
    )
    fun searchSessionsByTranscript(query: String): Flow<List<SessionEntity>>

    /**
     * Get total session count.
     */
    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun getSessionCount(): Int

    /**
     * Get starred session count.
     */
    @Query("SELECT COUNT(*) FROM sessions WHERE isStarred = 1")
    suspend fun getStarredSessionCount(): Int
}
