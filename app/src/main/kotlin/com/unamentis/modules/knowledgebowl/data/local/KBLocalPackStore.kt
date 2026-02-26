package com.unamentis.modules.knowledgebowl.data.local

import android.content.Context
import android.util.Log
import com.unamentis.modules.knowledgebowl.data.model.KBPack
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages creation, storage, and retrieval of local question packs.
 *
 * Packs are persisted as JSON in the app's files directory. All public
 * methods are suspending and safe to call from any coroutine context.
 *
 * @property context Application context for file access
 */
@Singleton
class KBLocalPackStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val _localPacks = MutableStateFlow<List<KBPack>>(emptyList())

        /** Observable list of local packs. */
        val localPacks: StateFlow<List<KBPack>> = _localPacks.asStateFlow()

        private val _isLoading = MutableStateFlow(false)

        /** Whether a load operation is in progress. */
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val mutex = Mutex()
        private val json =
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            }

        private val packFile: File
            get() = File(context.filesDir, PACK_FILE_NAME)

        /**
         * Load local packs from disk.
         *
         * Safe to call multiple times; will replace the current list.
         */
        suspend fun load() {
            _isLoading.value = true
            try {
                val packs = loadFromDisk()
                _localPacks.value = packs
                Log.i(TAG, "Loaded ${packs.size} local packs")
            } catch (e: Exception) {
                if (packFile.exists()) {
                    Log.e(TAG, "Failed to load local packs: ${e.message}")
                }
                _localPacks.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }

        /**
         * Create a new local pack from selected questions.
         *
         * @param name Display name for the pack
         * @param description Optional description
         * @param questions List of questions to include
         * @return The newly created pack
         */
        suspend fun createPack(
            name: String,
            description: String?,
            questions: List<KBQuestion>,
        ): KBPack =
            mutex.withLock {
                val domainDistribution = mutableMapOf<String, Int>()
                val difficultyDistribution = mutableMapOf<Int, Int>()

                for (question in questions) {
                    val domainKey = question.domain.name.lowercase()
                    domainDistribution[domainKey] =
                        (domainDistribution[domainKey] ?: 0) + 1

                    val difficultyKey = question.difficulty.level
                    difficultyDistribution[difficultyKey] =
                        (difficultyDistribution[difficultyKey] ?: 0) + 1
                }

                val pack =
                    KBPack(
                        id = "local-${UUID.randomUUID().toString().take(LOCAL_ID_PREFIX_LENGTH)}",
                        name = name,
                        description = description ?: DEFAULT_DESCRIPTION,
                        questionCount = questions.size,
                        domainDistribution = domainDistribution,
                        difficultyDistribution = difficultyDistribution,
                        packType = KBPack.PackType.CUSTOM,
                        isLocal = true,
                        questionIds = questions.map { it.id },
                        createdAtMillis = System.currentTimeMillis(),
                        updatedAtMillis = null,
                    )

                val updatedPacks = _localPacks.value + pack
                _localPacks.value = updatedPacks
                saveToDisk(updatedPacks)

                Log.i(TAG, "Created local pack '$name' with ${questions.size} questions")
                pack
            }

        /**
         * Update an existing local pack.
         *
         * @param id ID of the pack to update
         * @param name Optional new name
         * @param description Optional new description
         * @param questions Optional new question list (recalculates distributions)
         * @return true if the pack was found and updated
         */
        @Suppress("LongMethod")
        suspend fun updatePack(
            id: String,
            name: String? = null,
            description: String? = null,
            questions: List<KBQuestion>? = null,
        ): Boolean =
            mutex.withLock {
                val packs = _localPacks.value.toMutableList()
                val index = packs.indexOfFirst { it.id == id }

                if (index < 0) {
                    Log.w(TAG, "Pack not found for update: $id")
                    return false
                }

                val existing = packs[index]
                var domainDistribution = existing.domainDistribution
                var difficultyDistribution = existing.difficultyDistribution
                var questionIds = existing.questionIds
                var questionCount = existing.questionCount

                if (questions != null) {
                    val newDomainDist = mutableMapOf<String, Int>()
                    val newDiffDist = mutableMapOf<Int, Int>()

                    for (question in questions) {
                        val domainKey = question.domain.name.lowercase()
                        newDomainDist[domainKey] = (newDomainDist[domainKey] ?: 0) + 1

                        val difficultyKey = question.difficulty.level
                        newDiffDist[difficultyKey] = (newDiffDist[difficultyKey] ?: 0) + 1
                    }

                    domainDistribution = newDomainDist
                    difficultyDistribution = newDiffDist
                    questionIds = questions.map { it.id }
                    questionCount = questions.size
                }

                val updated =
                    existing.copy(
                        name = name ?: existing.name,
                        description = description ?: existing.description,
                        questionCount = questionCount,
                        domainDistribution = domainDistribution,
                        difficultyDistribution = difficultyDistribution,
                        questionIds = questionIds,
                        updatedAtMillis = System.currentTimeMillis(),
                    )

                packs[index] = updated
                _localPacks.value = packs
                saveToDisk(packs)

                Log.i(TAG, "Updated local pack '${updated.name}'")
                true
            }

        /**
         * Delete a local pack.
         *
         * @param id ID of the pack to delete
         * @return true if the pack was found and deleted
         */
        suspend fun deletePack(id: String): Boolean =
            mutex.withLock {
                val packs = _localPacks.value.toMutableList()
                val index = packs.indexOfFirst { it.id == id }

                if (index < 0) {
                    Log.w(TAG, "Pack not found for deletion: $id")
                    return false
                }

                val pack = packs.removeAt(index)
                _localPacks.value = packs
                saveToDisk(packs)

                Log.i(TAG, "Deleted local pack '${pack.name}'")
                true
            }

        /**
         * Get a pack by ID.
         *
         * @param id Pack identifier
         * @return The pack if found, null otherwise
         */
        fun packById(id: String): KBPack? {
            return _localPacks.value.firstOrNull { it.id == id }
        }

        private suspend fun loadFromDisk(): List<KBPack> =
            withContext(Dispatchers.IO) {
                if (!packFile.exists()) return@withContext emptyList()
                val data = packFile.readText()
                val container = json.decodeFromString<LocalPacksContainer>(data)
                container.packs
            }

        private suspend fun saveToDisk(packs: List<KBPack>) {
            withContext(Dispatchers.IO) {
                try {
                    val container =
                        LocalPacksContainer(
                            version = CONTAINER_VERSION,
                            packs = packs,
                        )
                    val data = json.encodeToString(LocalPacksContainer.serializer(), container)
                    packFile.writeText(data)
                    Log.d(TAG, "Saved ${packs.size} local packs to disk")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save local packs: ${e.message}")
                }
            }
        }

        companion object {
            private const val TAG = "KBLocalPackStore"
            private const val PACK_FILE_NAME = "kb_local_packs.json"
            private const val CONTAINER_VERSION = "1.0.0"
            private const val LOCAL_ID_PREFIX_LENGTH = 8
            private const val DEFAULT_DESCRIPTION = "Custom pack created on device"
        }
    }

/**
 * Container for serializing local packs to disk.
 */
@Serializable
internal data class LocalPacksContainer(
    val version: String,
    val packs: List<KBPack>,
)
