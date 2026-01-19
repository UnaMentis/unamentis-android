package com.unamentis.core.module

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for fetching and downloading modules from the server.
 *
 * Communicates with the management API to:
 * - List available modules
 * - Fetch module details
 * - Download module content
 *
 * ## Server Endpoints
 * - `GET /api/modules` - List all available modules
 * - `GET /api/modules/{id}` - Get module details
 * - `POST /api/modules/{id}/download` - Download module content
 *
 * ## Usage
 * ```kotlin
 * // Configure the service
 * service.configure("localhost", 8766)
 *
 * // Fetch available modules
 * val modules = service.fetchAvailableModules()
 *
 * // Download a module
 * val downloaded = service.downloadModule("knowledge-bowl")
 * registry.registerDownloaded(downloaded)
 * ```
 *
 * @property context Application context
 * @property okHttpClient HTTP client for API requests
 * @property registry Module registry for storing downloads
 * @property json JSON serializer
 */
@Singleton
class ModuleService
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val okHttpClient: OkHttpClient,
        private val registry: ModuleRegistry,
        private val json: Json,
    ) {
        private var baseUrl: String? = null

        private val _isConfigured = MutableStateFlow(false)
        val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

        private val _availableModules = MutableStateFlow<List<ModuleSummary>>(emptyList())
        val availableModules: StateFlow<List<ModuleSummary>> = _availableModules.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _lastError = MutableStateFlow<ModuleServiceError?>(null)
        val lastError: StateFlow<ModuleServiceError?> = _lastError.asStateFlow()

        companion object {
            private const val TAG = "ModuleService"
            private const val DEFAULT_PORT = 8766
            private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        }

        /**
         * Configure the service with server connection details.
         *
         * @param host Server hostname (e.g., "localhost" or "10.0.2.2" for emulator)
         * @param port Server port (default: 8766)
         */
        fun configure(
            host: String,
            port: Int = DEFAULT_PORT,
        ) {
            baseUrl = "http://$host:$port"
            _isConfigured.value = true
            _lastError.value = null
            Log.d(TAG, "Configured module service: $baseUrl")
        }

        /**
         * Fetch list of available modules from server.
         *
         * Updates [availableModules] state on success.
         *
         * @return List of available modules
         * @throws ModuleServiceError on failure
         */
        suspend fun fetchAvailableModules(): List<ModuleSummary> {
            ensureConfigured()

            _isLoading.value = true
            _lastError.value = null

            return try {
                val url = "$baseUrl/api/modules"
                Log.d(TAG, "Fetching modules from: $url")

                val response = executeGet<ModuleListResponse>(url)
                val modules =
                    response.modules.map { summary ->
                        // Mark installed status based on registry
                        summary.copy(isInstalled = registry.isDownloaded(summary.id))
                    }
                _availableModules.value = modules
                Log.d(TAG, "Fetched ${modules.size} available modules")
                modules
            } catch (e: ModuleServiceError) {
                _lastError.value = e
                throw e
            } finally {
                _isLoading.value = false
            }
        }

        /**
         * Fetch detailed information about a specific module.
         *
         * @param moduleId Module ID to fetch
         * @return Module details
         * @throws ModuleServiceError on failure
         */
        suspend fun fetchModuleDetail(moduleId: String): ModuleDetail {
            ensureConfigured()

            _isLoading.value = true
            _lastError.value = null

            return try {
                val url = "$baseUrl/api/modules/$moduleId"
                Log.d(TAG, "Fetching module detail: $url")

                executeGet<ModuleDetail>(url)
            } catch (e: ModuleServiceError) {
                _lastError.value = e
                throw e
            } finally {
                _isLoading.value = false
            }
        }

        /**
         * Download a module and register it locally.
         *
         * @param moduleId Module ID to download
         * @param includeAudio Whether to include pre-generated audio (increases size)
         * @return Downloaded module ready for use
         * @throws ModuleServiceError on failure
         */
        suspend fun downloadModule(
            moduleId: String,
            includeAudio: Boolean = false,
        ): DownloadedModule {
            ensureConfigured()

            _isLoading.value = true
            _lastError.value = null

            return try {
                val url = "$baseUrl/api/modules/$moduleId/download"
                Log.d(TAG, "Downloading module: $url")

                val request =
                    ModuleDownloadRequest(
                        moduleId = moduleId,
                        includeAudio = includeAudio,
                    )

                val module =
                    executePost<ModuleDownloadRequest, DownloadedModule>(url, request)
                        .copy(downloadedAt = System.currentTimeMillis())

                registry.registerDownloaded(module)
                Log.d(TAG, "Downloaded and registered module: ${module.name}")
                module
            } catch (e: ModuleServiceError) {
                _lastError.value = e
                throw e
            } finally {
                _isLoading.value = false
            }
        }

        /**
         * Delete a downloaded module.
         *
         * @param moduleId Module ID to delete
         * @return True if module was deleted
         */
        fun deleteModule(moduleId: String): Boolean {
            return registry.removeDownloaded(moduleId)
        }

        /**
         * Check if a module has an update available.
         *
         * @param moduleId Module ID to check
         * @return True if update is available
         */
        fun hasUpdateAvailable(moduleId: String): Boolean {
            val available =
                _availableModules.value.find { it.id == moduleId }
                    ?: return false
            return registry.hasUpdate(moduleId, available.version)
        }

        /**
         * Refresh the available modules list.
         *
         * Convenience method that handles errors gracefully.
         *
         * @return True if refresh succeeded
         */
        suspend fun refresh(): Boolean {
            return try {
                fetchAvailableModules()
                true
            } catch (e: ModuleServiceError) {
                Log.e(TAG, "Failed to refresh modules", e)
                false
            }
        }

        private fun ensureConfigured() {
            if (baseUrl == null) {
                throw ModuleServiceError.NotConfigured
            }
        }

        /**
         * Execute a GET request and parse the response.
         */
        private suspend inline fun <reified T> executeGet(url: String): T =
            withContext(Dispatchers.IO) {
                try {
                    val request =
                        Request.Builder()
                            .url(url)
                            .get()
                            .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        val bodyString = response.body?.string()

                        when {
                            response.code == 404 -> {
                                throw ModuleServiceError.ModuleNotFound(
                                    url.substringAfterLast("/"),
                                )
                            }
                            response.code in 500..599 -> {
                                throw ModuleServiceError.ServerError(response.code)
                            }
                            !response.isSuccessful -> {
                                throw ModuleServiceError.NetworkError(
                                    "HTTP ${response.code}: ${response.message}",
                                )
                            }
                            bodyString.isNullOrEmpty() -> {
                                throw ModuleServiceError.InvalidResponse
                            }
                            else -> {
                                try {
                                    json.decodeFromString<T>(bodyString)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to parse response: $bodyString", e)
                                    throw ModuleServiceError.DecodingError(
                                        e.message ?: "Failed to decode response",
                                    )
                                }
                            }
                        }
                    }
                } catch (e: ModuleServiceError) {
                    throw e
                } catch (e: IOException) {
                    Log.e(TAG, "Network error: ${e.message}", e)
                    throw ModuleServiceError.NetworkError(e.message ?: "Network error")
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error: ${e.message}", e)
                    throw ModuleServiceError.NetworkError(e.message ?: "Unexpected error")
                }
            }

        /**
         * Execute a POST request with a body and parse the response.
         */
        private suspend inline fun <reified T, reified R> executePost(
            url: String,
            body: T,
        ): R =
            withContext(Dispatchers.IO) {
                try {
                    val bodyJson =
                        json.encodeToString(
                            kotlinx.serialization.serializer<T>(),
                            body,
                        )
                    val requestBody = bodyJson.toRequestBody(JSON_MEDIA_TYPE)

                    val request =
                        Request.Builder()
                            .url(url)
                            .post(requestBody)
                            .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        val bodyString = response.body?.string()

                        when {
                            response.code == 404 -> {
                                throw ModuleServiceError.ModuleNotFound(
                                    url.substringAfterLast("/download").substringAfterLast("/"),
                                )
                            }
                            response.code in 500..599 -> {
                                throw ModuleServiceError.ServerError(response.code)
                            }
                            !response.isSuccessful -> {
                                throw ModuleServiceError.DownloadFailed(
                                    "HTTP ${response.code}: ${response.message}",
                                )
                            }
                            bodyString.isNullOrEmpty() -> {
                                throw ModuleServiceError.InvalidResponse
                            }
                            else -> {
                                try {
                                    json.decodeFromString<R>(bodyString)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to parse response: $bodyString", e)
                                    throw ModuleServiceError.DecodingError(
                                        e.message ?: "Failed to decode response",
                                    )
                                }
                            }
                        }
                    }
                } catch (e: ModuleServiceError) {
                    throw e
                } catch (e: IOException) {
                    Log.e(TAG, "Network error: ${e.message}", e)
                    throw ModuleServiceError.DownloadFailed(e.message ?: "Network error")
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error: ${e.message}", e)
                    throw ModuleServiceError.DownloadFailed(e.message ?: "Unexpected error")
                }
            }
    }
