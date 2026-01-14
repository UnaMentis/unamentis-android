package com.unamentis.data.repository

import com.unamentis.data.local.dao.CurriculumDao
import com.unamentis.data.local.entity.CurriculumEntity
import com.unamentis.data.model.Curriculum
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for curriculum data management.
 *
 * Responsibilities:
 * - Store downloaded curricula locally
 * - Retrieve curriculum content from server
 * - Manage curriculum metadata
 * - Handle curriculum downloads with progress
 *
 * @property curriculumDao Room DAO for curriculum data
 */
@Singleton
class CurriculumRepository
    @Inject
    constructor(
        private val curriculumDao: CurriculumDao,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        // Cache for server curricula (would be fetched from API in real implementation)
        private val serverCurriculaCache = MutableStateFlow<List<Curriculum>>(emptyList())

        /**
         * Get all downloaded curricula (local).
         */
        fun getAllCurricula(): Flow<List<Curriculum>> {
            return curriculumDao.getAllCurricula().map { entities ->
                entities.mapNotNull { entity ->
                    try {
                        json.decodeFromString<Curriculum>(entity.contentJson)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }

        /**
         * Get local curricula (alias for getAllCurricula).
         */
        fun getLocalCurricula(): Flow<List<Curriculum>> = getAllCurricula()

        /**
         * Get server curricula (available for download).
         */
        fun getServerCurricula(): Flow<List<Curriculum>> = serverCurriculaCache

        /**
         * Refresh curricula list from server.
         */
        suspend fun refreshCurricula() {
            // In a real implementation, this would fetch from the management console API
            // For now, we return an empty list (curricula would come from server)
            // The management console at http://10.0.2.2:8766 would provide this data
            serverCurriculaCache.value = emptyList()
        }

        /**
         * Download a curriculum from the server.
         * Returns a Flow emitting download progress (0.0 to 1.0).
         */
        fun downloadCurriculum(curriculumId: String): Flow<Float> =
            flow {
                // Find curriculum in server cache
                val curriculum = serverCurriculaCache.value.find { it.id == curriculumId }

                if (curriculum != null) {
                    // Simulate download progress
                    emit(0.0f)
                    emit(0.25f)
                    emit(0.5f)
                    emit(0.75f)

                    // Save to local storage
                    saveCurriculum(curriculum)

                    emit(1.0f)
                } else {
                    // Curriculum not found, emit completion anyway
                    emit(1.0f)
                }
            }

        /**
         * Get a specific curriculum by ID.
         */
        suspend fun getCurriculumById(curriculumId: String): Curriculum? {
            return curriculumDao.getCurriculumById(curriculumId)?.let { entity ->
                try {
                    json.decodeFromString<Curriculum>(entity.contentJson)
                } catch (e: Exception) {
                    null
                }
            }
        }

        /**
         * Save a curriculum to local storage.
         */
        suspend fun saveCurriculum(curriculum: Curriculum) {
            val entity =
                CurriculumEntity(
                    id = curriculum.id,
                    title = curriculum.title,
                    description = curriculum.description,
                    version = curriculum.version,
                    contentJson = json.encodeToString(curriculum),
                    downloadedAt = System.currentTimeMillis(),
                    lastAccessedAt = System.currentTimeMillis(),
                    topicCount = curriculum.topics.size,
                )
            curriculumDao.insertCurriculum(entity)
        }

        /**
         * Update last accessed timestamp for a curriculum.
         */
        suspend fun updateLastAccessed(curriculumId: String) {
            curriculumDao.updateLastAccessed(curriculumId, System.currentTimeMillis())
        }

        /**
         * Delete a curriculum from local storage.
         */
        suspend fun deleteCurriculum(curriculumId: String) {
            curriculumDao.deleteCurriculum(curriculumId)
        }

        /**
         * Delete all downloaded curricula.
         */
        suspend fun deleteAllCurricula() {
            curriculumDao.deleteAllCurricula()
        }
    }
