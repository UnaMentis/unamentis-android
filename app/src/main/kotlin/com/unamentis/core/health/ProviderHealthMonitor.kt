package com.unamentis.core.health

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Health status for a provider.
 *
 * State machine: HEALTHY -> DEGRADED -> UNHEALTHY
 * - HEALTHY: Provider is responding normally
 * - DEGRADED: Intermittent issues, transitioning
 * - UNHEALTHY: Provider is down, use fallback
 */
enum class HealthStatus {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
}

/**
 * Configuration for health monitoring.
 *
 * @property healthEndpoint URL to check for health
 * @property checkIntervalMs Interval between health checks in milliseconds
 * @property unhealthyThreshold Consecutive failures before marking unhealthy
 * @property healthyThreshold Consecutive successes before marking healthy
 * @property timeoutMs HTTP timeout for health checks in milliseconds
 */
data class HealthMonitorConfig(
    val healthEndpoint: String,
    val checkIntervalMs: Long = 30_000L,
    val unhealthyThreshold: Int = 3,
    val healthyThreshold: Int = 2,
    val timeoutMs: Long = 5_000L,
)

/**
 * Monitors provider health and triggers failover.
 *
 * Features:
 * - Periodic HTTP health checks
 * - State machine: healthy -> degraded -> unhealthy
 * - Configurable thresholds for state transitions
 * - StateFlow for reactive status updates
 *
 * Usage:
 * ```kotlin
 * val monitor = ProviderHealthMonitor(
 *     config = HealthMonitorConfig(healthEndpoint = "http://localhost:8080/health"),
 *     client = okHttpClient
 * )
 * monitor.startMonitoring()
 *
 * // Observe status changes
 * monitor.status.collect { status ->
 *     when (status) {
 *         HealthStatus.HEALTHY -> // Use primary provider
 *         HealthStatus.DEGRADED -> // Monitor closely
 *         HealthStatus.UNHEALTHY -> // Switch to fallback
 *     }
 * }
 * ```
 *
 * @property config Health monitoring configuration
 * @property client OkHttpClient for health checks
 * @property providerName Name of the provider being monitored (for logging)
 */
class ProviderHealthMonitor(
    private val config: HealthMonitorConfig,
    private val client: OkHttpClient,
    private val providerName: String = "Provider",
) {
    companion object {
        private const val TAG = "ProviderHealthMonitor"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null

    private val _status = MutableStateFlow(HealthStatus.HEALTHY)

    /**
     * Current health status as StateFlow for reactive observation.
     */
    val status: StateFlow<HealthStatus> = _status.asStateFlow()

    /**
     * Current health status (synchronous access).
     */
    val currentStatus: HealthStatus
        get() = _status.value

    private var consecutiveFailures: Int = 0
    private var consecutiveSuccesses: Int = 0

    /**
     * Start health monitoring.
     *
     * Begins periodic health checks at the configured interval.
     * Status updates are emitted via the [status] StateFlow.
     */
    fun startMonitoring() {
        if (monitorJob?.isActive == true) {
            Log.d(TAG, "[$providerName] Health monitoring already active")
            return
        }

        Log.i(TAG, "[$providerName] Starting health monitoring: ${config.healthEndpoint}")

        monitorJob =
            scope.launch {
                while (isActive) {
                    checkHealth()
                    delay(config.checkIntervalMs)
                }
            }
    }

    /**
     * Stop health monitoring.
     */
    fun stopMonitoring() {
        Log.i(TAG, "[$providerName] Stopping health monitoring")
        monitorJob?.cancel()
        monitorJob = null
    }

    /**
     * Perform a single health check.
     *
     * @return Updated health status
     */
    suspend fun checkHealth(): HealthStatus {
        return try {
            val request =
                Request.Builder()
                    .url(config.healthEndpoint)
                    .get()
                    .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                response.close()
                recordSuccess()
            } else {
                response.close()
                Log.w(TAG, "[$providerName] Health check returned ${response.code}")
                recordFailure()
            }
        } catch (e: IOException) {
            Log.w(TAG, "[$providerName] Health check failed: ${e.message}")
            recordFailure()
        } catch (e: Exception) {
            Log.e(TAG, "[$providerName] Health check error", e)
            recordFailure()
        }
    }

    /**
     * Manually record a success (for external callers).
     *
     * Call this when a provider operation succeeds to update health status.
     *
     * @return Updated health status
     */
    fun recordSuccess(): HealthStatus {
        consecutiveSuccesses++
        consecutiveFailures = 0

        val previousStatus = _status.value

        val newStatus =
            when {
                consecutiveSuccesses >= config.healthyThreshold -> HealthStatus.HEALTHY
                previousStatus == HealthStatus.UNHEALTHY -> HealthStatus.DEGRADED
                else -> previousStatus
            }

        if (newStatus != previousStatus) {
            Log.i(TAG, "[$providerName] Health status changed: $previousStatus -> $newStatus")
            _status.value = newStatus
        }

        return newStatus
    }

    /**
     * Manually record a failure (for external callers).
     *
     * Call this when a provider operation fails to update health status.
     *
     * @return Updated health status
     */
    fun recordFailure(): HealthStatus {
        consecutiveFailures++
        consecutiveSuccesses = 0

        val previousStatus = _status.value

        val newStatus =
            when {
                consecutiveFailures >= config.unhealthyThreshold -> HealthStatus.UNHEALTHY
                previousStatus == HealthStatus.HEALTHY -> HealthStatus.DEGRADED
                else -> previousStatus
            }

        if (newStatus != previousStatus) {
            Log.i(TAG, "[$providerName] Health status changed: $previousStatus -> $newStatus")
            _status.value = newStatus
        }

        return newStatus
    }

    /**
     * Reset health status to healthy.
     *
     * Use this when the health endpoint changes or the provider is reconfigured.
     */
    fun reset() {
        consecutiveFailures = 0
        consecutiveSuccesses = 0
        _status.value = HealthStatus.HEALTHY
        Log.i(TAG, "[$providerName] Health status reset to HEALTHY")
    }

    /**
     * Check if the provider is available (healthy or degraded).
     *
     * @return true if provider can be used, false if unhealthy
     */
    fun isAvailable(): Boolean {
        return _status.value != HealthStatus.UNHEALTHY
    }
}
