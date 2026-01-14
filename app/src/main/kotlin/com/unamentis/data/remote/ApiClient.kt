package com.unamentis.data.remote

import android.content.Context
import android.os.Build
import com.unamentis.BuildConfig
import com.unamentis.data.model.Curriculum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.*

/**
 * API client for communicating with the UnaMentis management console.
 *
 * Handles all HTTP requests to the server including curriculum downloads,
 * metrics uploads, and client heartbeats.
 *
 * @property context Application context for device info
 * @property okHttpClient HTTP client instance
 * @property json JSON serializer
 * @property logServerUrl Log server base URL (default: emulator access to host)
 * @property managementUrl Management console base URL (default: emulator access to host)
 */
class ApiClient(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val logServerUrl: String = "http://10.0.2.2:8765",
    private val managementUrl: String = "http://10.0.2.2:8766",
) {
    private val deviceId: String by lazy {
        UUID.randomUUID().toString()
    }

    private val deviceName: String by lazy {
        "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    /**
     * Get list of available curricula.
     *
     * GET /api/curricula
     *
     * @return List of curriculum summaries
     * @throws IOException if network request fails
     */
    suspend fun getCurricula(): List<CurriculumSummary> =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url("$managementUrl/api/curricula")
                    .get()
                    .addClientHeaders()
                    .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("API error: ${response.code} ${response.message}")
                }
                val body = response.body?.string() ?: throw IOException("Empty response body")
                json.decodeFromString<List<CurriculumSummary>>(body)
            }
        }

    /**
     * Get full curriculum with all content.
     *
     * GET /api/curricula/{id}/full-with-assets
     *
     * @param id Curriculum identifier
     * @return Complete curriculum with assets
     * @throws IOException if network request fails
     */
    suspend fun getCurriculumFullWithAssets(id: String): Curriculum =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url("$managementUrl/api/curricula/$id/full-with-assets")
                    .get()
                    .addClientHeaders()
                    .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("API error: ${response.code} ${response.message}")
                }
                val body = response.body?.string() ?: throw IOException("Empty response body")
                // The response has "curriculum" and "assets" fields, we extract curriculum
                val fullResponse = json.decodeFromString<Map<String, Any>>(body)
                val curriculumJson = json.encodeToString(fullResponse["curriculum"])
                json.decodeFromString<Curriculum>(curriculumJson)
            }
        }

    /**
     * Upload session metrics.
     *
     * POST /api/metrics
     *
     * @param metrics Metrics to upload
     * @return Upload response
     * @throws IOException if network request fails
     */
    suspend fun uploadMetrics(metrics: MetricsUploadRequest): MetricsUploadResponse =
        withContext(Dispatchers.IO) {
            val bodyJson = json.encodeToString(metrics)
            val requestBody = bodyJson.toRequestBody("application/json".toMediaType())

            val request =
                Request.Builder()
                    .url("$managementUrl/api/metrics")
                    .post(requestBody)
                    .addClientHeaders()
                    .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Metrics upload failed: ${response.code} ${response.message}")
                }
                val body = response.body?.string() ?: throw IOException("Empty response body")
                json.decodeFromString<MetricsUploadResponse>(body)
            }
        }

    /**
     * Send client heartbeat.
     *
     * POST /api/clients/heartbeat
     *
     * @param heartbeat Heartbeat data
     * @return Heartbeat response
     * @throws IOException if network request fails
     */
    suspend fun sendHeartbeat(heartbeat: ClientHeartbeat): HeartbeatResponse =
        withContext(Dispatchers.IO) {
            val bodyJson = json.encodeToString(heartbeat)
            val requestBody = bodyJson.toRequestBody("application/json".toMediaType())

            val request =
                Request.Builder()
                    .url("$managementUrl/api/clients/heartbeat")
                    .post(requestBody)
                    .addClientHeaders()
                    .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Heartbeat failed: ${response.code} ${response.message}")
                }
                val body = response.body?.string() ?: throw IOException("Empty response body")
                json.decodeFromString<HeartbeatResponse>(body)
            }
        }

    /**
     * Send log entry to log server.
     *
     * POST /log
     *
     * @param logEntry Log entry to send
     */
    suspend fun sendLog(logEntry: LogEntry) =
        withContext(Dispatchers.IO) {
            try {
                val bodyJson = json.encodeToString(logEntry)
                val requestBody = bodyJson.toRequestBody("application/json".toMediaType())

                val request =
                    Request.Builder()
                        .url("$logServerUrl/log")
                        .post(requestBody)
                        .build()

                okHttpClient.newCall(request).execute().use { response ->
                    // Silently fail - don't want logging to crash the app
                    if (!response.isSuccessful) {
                        // Log to Android logcat but don't throw
                        android.util.Log.w("ApiClient", "Log upload failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                // Silently fail logging errors
                android.util.Log.w("ApiClient", "Failed to send log: ${e.message}")
            }
        }

    /**
     * Add client identification headers to request.
     */
    private fun Request.Builder.addClientHeaders(): Request.Builder {
        return this
            .addHeader("X-Client-ID", deviceId)
            .addHeader("X-Client-Name", deviceName)
            .addHeader("X-Client-Platform", "Android")
            .addHeader("X-Client-Version", BuildConfig.VERSION_NAME)
    }
}
