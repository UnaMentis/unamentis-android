package com.unamentis.services.stt

import android.util.Log
import com.unamentis.core.health.HealthMonitorConfig
import com.unamentis.core.health.HealthStatus
import com.unamentis.core.health.ProviderHealthMonitor
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Health monitor specifically for GLM-ASR server.
 *
 * Provides health monitoring with:
 * - Periodic HTTP health checks
 * - State machine: healthy -> degraded -> unhealthy
 * - Configurable thresholds for state transitions
 * - StateFlow for reactive status updates
 *
 * @property healthEndpoint HTTP endpoint for health checks
 * @property client OkHttp client for health checks
 * @property config Health monitoring configuration
 */
@Singleton
class GLMASRHealthMonitor
    @Inject
    constructor(
        healthEndpoint: String,
        client: OkHttpClient,
        config: GLMASRHealthConfig = GLMASRHealthConfig.DEFAULT,
    ) {
        companion object {
            private const val TAG = "GLMASRHealthMonitor"
        }

        private val monitor: ProviderHealthMonitor

        init {
            val healthConfig =
                HealthMonitorConfig(
                    healthEndpoint = healthEndpoint,
                    checkIntervalMs = config.checkIntervalMs,
                    unhealthyThreshold = config.unhealthyThreshold,
                    healthyThreshold = config.healthyThreshold,
                    timeoutMs = config.timeoutMs,
                )

            monitor =
                ProviderHealthMonitor(
                    config = healthConfig,
                    client = client,
                    providerName = "GLMASR",
                )

            Log.i(TAG, "GLMASRHealthMonitor initialized with endpoint: $healthEndpoint")
        }

        /**
         * Current health status as StateFlow for reactive observation.
         */
        val status: StateFlow<HealthStatus>
            get() = monitor.status

        /**
         * Current health status (synchronous access).
         */
        val currentStatus: HealthStatus
            get() = monitor.currentStatus

        /**
         * Start health monitoring.
         */
        fun startMonitoring() {
            monitor.startMonitoring()
        }

        /**
         * Stop health monitoring.
         */
        fun stopMonitoring() {
            monitor.stopMonitoring()
        }

        /**
         * Manually record a success (from STT operations).
         */
        fun recordSuccess(): HealthStatus {
            return monitor.recordSuccess()
        }

        /**
         * Manually record a failure (from STT operations).
         */
        fun recordFailure(): HealthStatus {
            return monitor.recordFailure()
        }

        /**
         * Reset health status to healthy.
         */
        fun reset() {
            monitor.reset()
        }

        /**
         * Check if the provider is available (healthy or degraded).
         */
        fun isAvailable(): Boolean {
            return monitor.isAvailable()
        }

        /**
         * Get the underlying ProviderHealthMonitor for router integration.
         */
        fun asProviderHealthMonitor(): ProviderHealthMonitor = monitor
    }

/**
 * Configuration for GLM-ASR health monitoring.
 *
 * @property checkIntervalMs Interval between health checks in milliseconds
 * @property unhealthyThreshold Consecutive failures before marking unhealthy
 * @property healthyThreshold Consecutive successes before marking healthy
 * @property timeoutMs HTTP timeout for health checks
 */
data class GLMASRHealthConfig(
    val checkIntervalMs: Long = 30_000L,
    val unhealthyThreshold: Int = 3,
    val healthyThreshold: Int = 2,
    val timeoutMs: Long = 5_000L,
) {
    companion object {
        /** Default configuration with 30s check interval. */
        val DEFAULT = GLMASRHealthConfig()

        /** Aggressive monitoring with 10s check interval. */
        val AGGRESSIVE =
            GLMASRHealthConfig(
                checkIntervalMs = 10_000L,
                unhealthyThreshold = 2,
            )

        /** Relaxed monitoring with 60s check interval. */
        val RELAXED =
            GLMASRHealthConfig(
                checkIntervalMs = 60_000L,
                unhealthyThreshold = 5,
                healthyThreshold = 3,
            )
    }
}
