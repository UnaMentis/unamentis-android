package com.unamentis.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unamentis.data.local.entity.DownloadedModuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for downloaded modules.
 *
 * Provides methods to query, insert, and manage downloaded module content.
 */
@Dao
interface ModuleDao {
    /**
     * Get all downloaded modules (observable).
     *
     * @return Flow of module list ordered by last access time
     */
    @Query("SELECT * FROM downloaded_modules ORDER BY lastAccessedAt DESC")
    fun getAllModules(): Flow<List<DownloadedModuleEntity>>

    /**
     * Get all downloaded modules (one-shot).
     *
     * @return List of all modules
     */
    @Query("SELECT * FROM downloaded_modules ORDER BY lastAccessedAt DESC")
    suspend fun getAllModulesOnce(): List<DownloadedModuleEntity>

    /**
     * Get a specific module by ID.
     *
     * @param id Module identifier
     * @return Module entity or null if not found
     */
    @Query("SELECT * FROM downloaded_modules WHERE id = :id")
    suspend fun getModuleById(id: String): DownloadedModuleEntity?

    /**
     * Check if a module is downloaded.
     *
     * @param id Module identifier
     * @return True if module exists
     */
    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_modules WHERE id = :id)")
    suspend fun isModuleDownloaded(id: String): Boolean

    /**
     * Get module version for update checking.
     *
     * @param id Module identifier
     * @return Version string or null if not found
     */
    @Query("SELECT version FROM downloaded_modules WHERE id = :id")
    suspend fun getModuleVersion(id: String): String?

    /**
     * Insert or update a module.
     *
     * @param module Module to insert/update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModule(module: DownloadedModuleEntity)

    /**
     * Update last accessed timestamp.
     *
     * @param id Module identifier
     * @param timestamp New timestamp
     */
    @Query("UPDATE downloaded_modules SET lastAccessedAt = :timestamp WHERE id = :id")
    suspend fun updateLastAccessed(
        id: String,
        timestamp: Long,
    )

    /**
     * Delete a module.
     *
     * @param id Module identifier
     * @return Number of rows deleted (0 or 1)
     */
    @Query("DELETE FROM downloaded_modules WHERE id = :id")
    suspend fun deleteModule(id: String): Int

    /**
     * Delete all modules.
     */
    @Query("DELETE FROM downloaded_modules")
    suspend fun deleteAllModules()

    /**
     * Get total storage used by all modules.
     *
     * @return Total bytes used
     */
    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM downloaded_modules")
    suspend fun getTotalStorageUsed(): Long

    /**
     * Get module count.
     *
     * @return Number of downloaded modules
     */
    @Query("SELECT COUNT(*) FROM downloaded_modules")
    suspend fun getModuleCount(): Int
}
