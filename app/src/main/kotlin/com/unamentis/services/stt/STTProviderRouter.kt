package com.unamentis.services.stt

import android.util.Log
import com.unamentis.core.health.HealthStatus
import com.unamentis.core.health.ProviderHealthMonitor
import com.unamentis.data.model.STTResult
import com.unamentis.data.model.STTService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * Priority level for STT providers.
 *
 * Lower values = higher priority.
 */
enum class STTProviderPriority(val value: Int) {
    ON_DEVICE(0),
    SELF_HOSTED(1),
    CLOUD_PRIMARY(2),
    CLOUD_SECONDARY(3),
    CLOUD_FALLBACK(4),
}

/**
 * Registration info for an STT provider.
 *
 * @property service The STT service instance
 * @property priority Priority level for routing
 * @property healthMonitor Optional health monitor for this provider
 * @property requiresApiKey Whether this provider requires an API key
 * @property isOnDevice Whether this provider runs on-device
 */
data class STTProviderRegistration(
    val service: STTService,
    val priority: STTProviderPriority,
    val healthMonitor: ProviderHealthMonitor? = null,
    val requiresApiKey: Boolean = true,
    val isOnDevice: Boolean = false,
)

/**
 * Routes STT requests to appropriate provider with automatic failover.
 *
 * Features:
 * - Priority-based provider selection
 * - Automatic failover on provider failure
 * - Health monitoring integration
 * - Transparent provider switching between sessions
 *
 * Priority order (configurable):
 * 1. On-device providers (if available and healthy)
 * 2. Self-hosted servers (if healthy)
 * 3. Primary cloud provider (if healthy and API key available)
 * 4. Secondary cloud providers as fallbacks
 *
 * Usage:
 * ```kotlin
 * val router = STTProviderRouter()
 * router.registerProvider(
 *     STTProviderRegistration(
 *         service = deepgramService,
 *         priority = STTProviderPriority.CLOUD_PRIMARY,
 *         healthMonitor = deepgramHealthMonitor
 *     )
 * )
 * router.registerProvider(
 *     STTProviderRegistration(
 *         service = androidSTTService,
 *         priority = STTProviderPriority.ON_DEVICE,
 *         isOnDevice = true,
 *         requiresApiKey = false
 *     )
 * )
 *
 * // Use like any STT service - routing is transparent
 * router.startStreaming().collect { result ->
 *     println(result.text)
 * }
 * ```
 */
class STTProviderRouter : STTService {
    companion object {
        private const val TAG = "STTProviderRouter"
    }

    override val providerName: String
        get() = "Router -> ${activeProvider?.providerName ?: "none"}"

    private val providers = mutableListOf<STTProviderRegistration>()
    private var activeProvider: STTService? = null
    private var forcedProvider: STTService? = null

    /**
     * Current active provider name for debugging/telemetry.
     */
    val currentProviderIdentifier: String
        get() = activeProvider?.providerName ?: "none"

    /**
     * Register a provider with the router.
     *
     * Providers are sorted by priority after registration.
     * Health monitoring is started automatically if a health monitor is provided.
     *
     * @param registration Provider registration info
     */
    fun registerProvider(registration: STTProviderRegistration) {
        providers.add(registration)
        providers.sortBy { it.priority.value }

        Log.i(
            TAG,
            "Registered provider: ${registration.service.providerName} " +
                "(priority: ${registration.priority})",
        )

        // Start health monitoring if configured
        registration.healthMonitor?.startMonitoring()
    }

    /**
     * Unregister a provider from the router.
     *
     * @param providerName Name of the provider to unregister
     */
    fun unregisterProvider(providerName: String) {
        val registration = providers.find { it.service.providerName == providerName }
        if (registration != null) {
            registration.healthMonitor?.stopMonitoring()
            providers.remove(registration)
            Log.i(TAG, "Unregistered provider: $providerName")
        }
    }

    /**
     * Get all registered provider names.
     */
    fun getRegisteredProviders(): List<String> {
        return providers.map { it.service.providerName }
    }

    /**
     * Get health status of a specific provider.
     *
     * @param providerName Name of the provider
     * @return Health status, or null if provider not found or has no health monitor
     */
    fun getProviderHealth(providerName: String): HealthStatus? {
        return providers
            .find { it.service.providerName == providerName }
            ?.healthMonitor
            ?.currentStatus
    }

    /**
     * Start streaming transcription.
     *
     * Selects the best available provider based on priority and health status.
     * If the selected provider fails during streaming, the flow completes with an error.
     * The next call to startStreaming will select the next available provider.
     *
     * @return Flow of STT results
     */
    override fun startStreaming(): Flow<STTResult> =
        flow {
            val provider =
                forcedProvider ?: selectProvider()
                    ?: throw IllegalStateException("No STT providers available")

            activeProvider = provider

            Log.i(TAG, "Starting streaming with provider: ${provider.providerName}")

            // Delegate to selected provider's flow
            emitAll(
                provider.startStreaming()
                    .onStart {
                        Log.d(TAG, "Provider stream started: ${provider.providerName}")
                    }
                    .onCompletion { error ->
                        if (error != null) {
                            Log.w(
                                TAG,
                                "Provider stream completed with error: ${provider.providerName}",
                                error,
                            )
                            // Record failure for health monitoring
                            recordProviderFailure(provider.providerName)
                        } else {
                            Log.d(TAG, "Provider stream completed: ${provider.providerName}")
                            // Record success for health monitoring
                            recordProviderSuccess(provider.providerName)
                        }
                    }
                    .catch { e ->
                        Log.e(TAG, "Provider stream error: ${provider.providerName}", e)
                        recordProviderFailure(provider.providerName)
                        throw e
                    },
            )
        }

    /**
     * Stop streaming and release resources.
     */
    override suspend fun stopStreaming() {
        Log.d(TAG, "Stopping streaming")
        activeProvider?.stopStreaming()
    }

    /**
     * Select the best available provider based on priority and health.
     *
     * @return Selected provider, or null if none available
     */
    private fun selectProvider(): STTService? {
        for (registration in providers) {
            // Check health status
            val healthStatus = registration.healthMonitor?.currentStatus ?: HealthStatus.HEALTHY

            if (healthStatus == HealthStatus.UNHEALTHY) {
                Log.d(
                    TAG,
                    "Skipping unhealthy provider: ${registration.service.providerName}",
                )
                continue
            }

            // Provider is available
            Log.d(
                TAG,
                "Selected provider: ${registration.service.providerName} " +
                    "(priority: ${registration.priority}, health: $healthStatus)",
            )
            return registration.service
        }

        Log.w(TAG, "No healthy providers available")
        return null
    }

    /**
     * Record a successful operation for a provider.
     */
    private fun recordProviderSuccess(providerName: String) {
        providers
            .find { it.service.providerName == providerName }
            ?.healthMonitor
            ?.recordSuccess()
    }

    /**
     * Record a failed operation for a provider.
     */
    private fun recordProviderFailure(providerName: String) {
        providers
            .find { it.service.providerName == providerName }
            ?.healthMonitor
            ?.recordFailure()
    }

    /**
     * Force switch to a specific provider by name.
     *
     * This is useful for testing or user preference override.
     * The provider must be registered with the router.
     *
     * @param providerName Name of the provider to switch to
     * @return true if switch successful, false if provider not found
     */
    fun forceProvider(providerName: String): Boolean {
        val registration = providers.find { it.service.providerName == providerName }
        return if (registration != null) {
            forcedProvider = registration.service
            Log.i(TAG, "Forced provider: $providerName")
            true
        } else {
            Log.w(TAG, "Cannot force provider: $providerName (not registered)")
            false
        }
    }

    /**
     * Clear forced provider and return to automatic selection.
     */
    fun clearForcedProvider() {
        Log.i(TAG, "Cleared forced provider, returning to automatic selection")
        forcedProvider = null
    }

    /**
     * Check if any provider is available.
     */
    fun hasAvailableProvider(): Boolean {
        return selectProvider() != null
    }

    /**
     * Shutdown the router and stop all health monitoring.
     */
    fun shutdown() {
        Log.i(TAG, "Shutting down STT provider router")
        providers.forEach { it.healthMonitor?.stopMonitoring() }
        providers.clear()
        activeProvider = null
        forcedProvider = null
    }
}
