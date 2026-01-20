package com.unamentis.data.local.dao

import androidx.room.*
import com.unamentis.data.local.entity.CurriculumEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for curricula.
 *
 * Provides methods to query, insert, and manage downloaded curriculum content.
 */
@Dao
interface CurriculumDao {
    /**
     * Get all downloaded curricula (observable).
     *
     * @return Flow of curriculum list
     */
    @Query("SELECT * FROM curricula ORDER BY lastAccessedAt DESC")
    fun getAllCurricula(): Flow<List<CurriculumEntity>>

    /**
     * Get a specific curriculum by ID.
     *
     * @param id Curriculum identifier
     * @return Curriculum entity or null if not found
     */
    @Query("SELECT * FROM curricula WHERE id = :id")
    suspend fun getCurriculumById(id: String): CurriculumEntity?

    /**
     * Insert or update a curriculum.
     *
     * @param curriculum Curriculum to insert/update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurriculum(curriculum: CurriculumEntity)

    /**
     * Update last accessed timestamp.
     *
     * @param id Curriculum identifier
     * @param timestamp New timestamp
     */
    @Query("UPDATE curricula SET lastAccessedAt = :timestamp WHERE id = :id")
    suspend fun updateLastAccessed(
        id: String,
        timestamp: Long,
    )

    /**
     * Delete a curriculum.
     *
     * @param id Curriculum identifier
     */
    @Query("DELETE FROM curricula WHERE id = :id")
    suspend fun deleteCurriculum(id: String)

    /**
     * Delete all curricula.
     */
    @Query("DELETE FROM curricula")
    suspend fun deleteAllCurricula()
}
