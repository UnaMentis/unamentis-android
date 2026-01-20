package com.unamentis.core.featureflags

import android.os.Build
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Protocol for feature flag evaluation.
 */
interface FeatureFlagEvaluating {
    /**
     * Check if a feature flag is enabled.
     *
     * @param flagName Name of the flag to check
     * @return True if enabled, false otherwise
     */
    suspend fun isEnabled(flagName: String): Boolean

    /**
     * Check if a feature flag is enabled with context.
     *
     * @param flagName Name of the flag to check
     * @param context Context for evaluation (user targeting, etc.)
     * @return True if enabled, false otherwise
     */
    suspend fun isEnabled(
        flagName: String,
        context: FeatureFlagContext,
    ): Boolean

    /**
     * Get a variant value for a flag.
     *
     * @param flagName Name of the flag
     * @return Variant if available, null otherwise
     */
    suspend fun getVariant(flagName: String): FeatureFlagVariant?

    /**
     * Get a variant value for a flag with context.
     *
     * @param flagName Name of the flag
     * @param context Context for evaluation
     * @return Variant if available, null otherwise
     */
    suspend fun getVariant(
        flagName: String,
        context: FeatureFlagContext,
    ): FeatureFlagVariant?

    /**
     * Force refresh flags from server.
     */
    suspend fun refresh()
}

/**
 * Context for feature flag evaluation (user targeting, etc.)
 */
@Serializable
data class FeatureFlagContext(
    val userId: String? = null,
    val sessionId: String? = null,
    val appVersion: String? = null,
    val platform: String = "Android",
    val properties: Map<String, String> = emptyMap(),
) {
    companion object {
        /**
         * Create context with current app version.
         */
        fun current(
            userId: String? = null,
            sessionId: String? = null,
        ): FeatureFlagContext {
            // Get app version from BuildConfig if available
            val version =
                try {
                    Class.forName("com.unamentis.BuildConfig")
                        .getField("VERSION_NAME")
                        .get(null) as? String
                } catch (_: Exception) {
                    // BuildConfig may not be available in all contexts (e.g., tests)
                    null
                }

            return FeatureFlagContext(
                userId = userId,
                sessionId = sessionId,
                appVersion = version,
                platform = "Android",
                properties =
                    mapOf(
                        "os_version" to Build.VERSION.SDK_INT.toString(),
                        "device_model" to Build.MODEL,
                    ),
            )
        }
    }
}

/**
 * A feature flag variant (for A/B tests, etc.)
 */
@Serializable
data class FeatureFlagVariant(
    val name: String,
    val enabled: Boolean,
    val payload: FeatureFlagPayload? = null,
)

/**
 * Payload for feature flag variants.
 */
@Serializable
sealed class FeatureFlagPayload {
    @Serializable
    @SerialName("string")
    data class StringPayload(val value: String) : FeatureFlagPayload()

    @Serializable
    @SerialName("number")
    data class NumberPayload(val value: Double) : FeatureFlagPayload()

    @Serializable
    @SerialName("json")
    data class JsonPayload(val value: Map<String, String>) : FeatureFlagPayload()

    val stringValue: String?
        get() = (this as? StringPayload)?.value

    val numberValue: Double?
        get() = (this as? NumberPayload)?.value

    val jsonValue: Map<String, String>?
        get() = (this as? JsonPayload)?.value

    companion object {
        fun fromString(value: String): FeatureFlagPayload = StringPayload(value)

        fun fromNumber(value: Double): FeatureFlagPayload = NumberPayload(value)

        fun fromJson(value: Map<String, String>): FeatureFlagPayload = JsonPayload(value)
    }
}

/**
 * Configuration for the feature flag service.
 */
data class FeatureFlagConfig(
    val proxyURL: String,
    val clientKey: String,
    val appName: String = "UnaMentis-Android",
    val refreshIntervalMs: Long = 30_000L,
    val enableOfflineMode: Boolean = true,
    val enableMetrics: Boolean = true,
) {
    companion object {
        /**
         * Default development configuration.
         */
        val DEVELOPMENT =
            FeatureFlagConfig(
                proxyURL = "http://10.0.2.2:3063/proxy",
                clientKey = "proxy-client-key",
                appName = "UnaMentis-Android-Dev",
            )

        /**
         * Production configuration (requires actual proxy URL).
         */
        fun production(
            proxyURL: String,
            clientKey: String,
        ) = FeatureFlagConfig(
            proxyURL = proxyURL,
            clientKey = clientKey,
            appName = "UnaMentis-Android",
        )
    }
}

/**
 * Errors from the feature flag service.
 */
sealed class FeatureFlagError : Exception() {
    data class NetworkError(val reason: String) : FeatureFlagError() {
        override val message: String = "Network error: $reason"
    }

    data object InvalidResponse : FeatureFlagError() {
        override val message: String = "Invalid response from feature flag server"
    }

    data object Unauthorized : FeatureFlagError() {
        override val message: String = "Unauthorized: Invalid client key"
    }

    data class ServerError(val code: Int) : FeatureFlagError() {
        override val message: String = "Server error: $code"
    }

    data class CacheError(val reason: String) : FeatureFlagError() {
        override val message: String = "Cache error: $reason"
    }

    data object NotInitialized : FeatureFlagError() {
        override val message: String = "Feature flag service not initialized"
    }
}

/**
 * Response from Unleash proxy.
 */
@Serializable
internal data class UnleashProxyResponse(
    val toggles: List<UnleashToggle>,
)

/**
 * A single toggle from Unleash.
 */
@Serializable
internal data class UnleashToggle(
    val name: String,
    val enabled: Boolean,
    val variant: UnleashVariant? = null,
    val impressionData: Boolean? = null,
)

/**
 * Variant from Unleash.
 */
@Serializable
internal data class UnleashVariant(
    val name: String,
    val enabled: Boolean,
    val payload: UnleashPayload? = null,
)

/**
 * Payload from Unleash.
 */
@Serializable
internal data class UnleashPayload(
    val type: String,
    val value: String,
)

/**
 * Metrics for feature flag usage.
 */
data class FeatureFlagMetrics(
    val totalEvaluations: Int,
    val cacheHits: Int,
    val cacheMisses: Int,
    val lastRefreshTime: Long?,
    val flagCount: Int,
) {
    val cacheHitRate: Double
        get() = if (totalEvaluations > 0) cacheHits.toDouble() / totalEvaluations else 0.0
}
