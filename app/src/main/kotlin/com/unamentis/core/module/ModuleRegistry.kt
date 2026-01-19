package com.unamentis.core.module

import android.util.Log
import com.unamentis.data.local.dao.ModuleDao
import com.unamentis.data.local.entity.DownloadedModuleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local registry for managing downloaded and available modules.
 *
 * The registry maintains:
 * - List of downloaded modules (persisted to Room database)
 * - Runtime-registered module implementations
 * - Module state (enabled/disabled, update available)
 *
 * ## Usage
 * ```kotlin
 * // Check if a module is downloaded
 * if (registry.isDownloaded("knowledge-bowl")) {
 *     val module = registry.getDownloaded("knowledge-bowl")
 *     // Use module...
 * }
 *
 * // Register a module implementation
 * registry.registerImplementation(knowledgeBowlModule)
 *
 * // Get implementation to display UI
 * val impl = registry.getImplementation("knowledge-bowl")
 * impl?.getUIEntryPoint()
 * ```
 *
 * @property moduleDao DAO for module persistence
 * @property json JSON serializer for content serialization
 */
@Singleton
class ModuleRegistry
    @Inject
    constructor(
        private val moduleDao: ModuleDao,
        private val json: Json,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val _downloadedModules = MutableStateFlow<List<DownloadedModule>>(emptyList())
        val downloadedModules: StateFlow<List<DownloadedModule>> = _downloadedModules.asStateFlow()

        private val implementations = mutableMapOf<String, ModuleProtocol>()

        init {
            // Load modules from database and observe changes
            scope.launch {
                moduleDao.getAllModules().collect { entities ->
                    _downloadedModules.value = entities.map { it.toDownloadedModule() }
                    Log.d(TAG, "Loaded ${entities.size} downloaded modules from database")
                }
            }
        }

        /**
         * Get all downloaded modules.
         */
        fun getAllDownloaded(): List<DownloadedModule> {
            return _downloadedModules.value
        }

        /**
         * Check if a module is downloaded.
         *
         * @param moduleId Module ID to check
         * @return True if downloaded
         */
        fun isDownloaded(moduleId: String): Boolean {
            return _downloadedModules.value.any { it.id == moduleId }
        }

        /**
         * Get a downloaded module by ID.
         *
         * @param moduleId Module ID
         * @return Downloaded module or null if not found
         */
        fun getDownloaded(moduleId: String): DownloadedModule? {
            return _downloadedModules.value.find { it.id == moduleId }
        }

        /**
         * Register a downloaded module.
         *
         * Adds the module to the registry and persists to database.
         *
         * @param module Downloaded module to register
         */
        fun registerDownloaded(module: DownloadedModule) {
            scope.launch {
                try {
                    moduleDao.insertModule(module.toEntity())
                    Log.d(TAG, "Registered downloaded module: ${module.id} v${module.version}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register module: ${module.id}", e)
                }
            }
        }

        /**
         * Register a downloaded module (suspending).
         *
         * @param module Downloaded module to register
         */
        suspend fun registerDownloadedSuspend(module: DownloadedModule) {
            try {
                moduleDao.insertModule(module.toEntity())
                Log.d(TAG, "Registered downloaded module: ${module.id} v${module.version}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register module: ${module.id}", e)
                throw e
            }
        }

        /**
         * Remove a downloaded module.
         *
         * @param moduleId Module ID to remove
         * @return True if module was removed
         */
        fun removeDownloaded(moduleId: String): Boolean {
            var removed = false
            scope.launch {
                try {
                    val count = moduleDao.deleteModule(moduleId)
                    removed = count > 0
                    if (removed) {
                        Log.d(TAG, "Removed downloaded module: $moduleId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to remove module: $moduleId", e)
                }
            }
            // Optimistically update local state
            val current = _downloadedModules.value
            if (current.any { it.id == moduleId }) {
                _downloadedModules.value = current.filter { it.id != moduleId }
                return true
            }
            return false
        }

        /**
         * Remove a downloaded module (suspending).
         *
         * @param moduleId Module ID to remove
         * @return True if module was removed
         */
        suspend fun removeDownloadedSuspend(moduleId: String): Boolean {
            return try {
                val count = moduleDao.deleteModule(moduleId)
                val removed = count > 0
                if (removed) {
                    Log.d(TAG, "Removed downloaded module: $moduleId")
                }
                removed
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove module: $moduleId", e)
                false
            }
        }

        /**
         * Clear all downloaded modules.
         */
        fun clearAll() {
            scope.launch {
                try {
                    moduleDao.deleteAllModules()
                    Log.d(TAG, "Cleared all downloaded modules")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear modules", e)
                }
            }
        }

        /**
         * Clear all downloaded modules (suspending).
         */
        suspend fun clearAllSuspend() {
            try {
                moduleDao.deleteAllModules()
                Log.d(TAG, "Cleared all downloaded modules")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear modules", e)
                throw e
            }
        }

        /**
         * Register a module implementation.
         *
         * Implementations provide the actual UI and logic for modules.
         * They are registered at app startup via Hilt.
         *
         * @param module Module implementation
         */
        fun registerImplementation(module: ModuleProtocol) {
            implementations[module.moduleId] = module
            Log.d(TAG, "Registered module implementation: ${module.moduleId}")
        }

        /**
         * Unregister a module implementation.
         *
         * @param moduleId Module ID to unregister
         */
        fun unregisterImplementation(moduleId: String) {
            implementations.remove(moduleId)
            Log.d(TAG, "Unregistered module implementation: $moduleId")
        }

        /**
         * Get a module implementation by ID.
         *
         * @param moduleId Module ID
         * @return Module implementation or null if not registered
         */
        fun getImplementation(moduleId: String): ModuleProtocol? {
            return implementations[moduleId]
        }

        /**
         * Get all registered module implementations.
         */
        fun getAllImplementations(): List<ModuleProtocol> {
            return implementations.values.toList()
        }

        /**
         * Check if a module has an available update.
         *
         * Compares downloaded version with available version.
         *
         * @param moduleId Module ID
         * @param availableVersion Latest available version
         * @return True if update is available
         */
        fun hasUpdate(
            moduleId: String,
            availableVersion: String,
        ): Boolean {
            val downloaded = getDownloaded(moduleId) ?: return false
            return compareVersions(availableVersion, downloaded.version) > 0
        }

        /**
         * Update last accessed timestamp for a module.
         *
         * @param moduleId Module ID
         */
        suspend fun updateLastAccessed(moduleId: String) {
            try {
                moduleDao.updateLastAccessed(moduleId, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update last accessed for: $moduleId", e)
            }
        }

        /**
         * Get total storage used by all modules.
         *
         * @return Total bytes used
         */
        suspend fun getTotalStorageUsed(): Long {
            return try {
                moduleDao.getTotalStorageUsed()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get total storage used", e)
                0L
            }
        }

        /**
         * Compare two semantic version strings.
         *
         * @return Positive if v1 > v2, negative if v1 < v2, 0 if equal
         */
        private fun compareVersions(
            v1: String,
            v2: String,
        ): Int {
            val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
            val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLength = maxOf(parts1.size, parts2.size)

            for (i in 0 until maxLength) {
                val p1 = parts1.getOrElse(i) { 0 }
                val p2 = parts2.getOrElse(i) { 0 }

                if (p1 != p2) {
                    return p1.compareTo(p2)
                }
            }

            return 0
        }

        /**
         * Convert DownloadedModule to database entity.
         */
        private fun DownloadedModule.toEntity(): DownloadedModuleEntity {
            val contentJsonStr = json.encodeToString(content)
            return DownloadedModuleEntity(
                id = id,
                name = name,
                version = version,
                description = description,
                downloadedAt = downloadedAt,
                lastAccessedAt = downloadedAt,
                contentJson = contentJsonStr,
                configJson = settings?.let { json.encodeToString(it) },
                sizeBytes = contentJsonStr.length.toLong(),
            )
        }

        /**
         * Convert database entity to DownloadedModule.
         */
        private fun DownloadedModuleEntity.toDownloadedModule(): DownloadedModule {
            return DownloadedModule(
                id = id,
                name = name,
                version = version,
                description = description,
                downloadedAt = downloadedAt,
                content =
                    try {
                        json.decodeFromString<ModuleContent>(contentJson)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse module content for: $id", e)
                        ModuleContent()
                    },
                settings =
                    configJson?.let {
                        try {
                            json.decodeFromString<ModuleSettings>(it)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse module settings for: $id", e)
                            null
                        }
                    },
            )
        }

        companion object {
            private const val TAG = "ModuleRegistry"
        }
    }
