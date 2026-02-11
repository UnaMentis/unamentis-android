package com.unamentis.core.config

import androidx.annotation.StringRes
import com.unamentis.R
import java.util.UUID

/**
 * Type of self-hosted server.
 */
enum class ServerType(
    @StringRes val displayNameResId: Int,
) {
    /** UnaMentis Gateway (unified gateway for all services) */
    UNAMENTIS_GATEWAY(R.string.server_type_unamentis_gateway),

    /** Ollama server for LLM */
    OLLAMA(R.string.server_type_ollama),

    /** Whisper server for STT */
    WHISPER(R.string.server_type_whisper),

    /** Piper server for TTS */
    PIPER(R.string.server_type_piper),

    /** VibeVoice server for TTS */
    VIBE_VOICE(R.string.server_type_vibevoice),

    /** Chatterbox server for TTS */
    CHATTERBOX(R.string.server_type_chatterbox),

    /** llama.cpp server for LLM */
    LLAMA_CPP(R.string.server_type_llama_cpp),

    /** vLLM server for LLM */
    VLLM(R.string.server_type_vllm),

    /** Custom OpenAI-compatible server */
    CUSTOM(R.string.server_type_custom),
    ;

    /**
     * Whether this server type provides LLM functionality.
     */
    fun isLLM(): Boolean =
        when (this) {
            OLLAMA, LLAMA_CPP, VLLM, UNAMENTIS_GATEWAY, CUSTOM -> true
            else -> false
        }

    /**
     * Whether this server type provides STT functionality.
     */
    fun isSTT(): Boolean =
        when (this) {
            WHISPER, UNAMENTIS_GATEWAY, CUSTOM -> true
            else -> false
        }

    /**
     * Whether this server type provides TTS functionality.
     */
    fun isTTS(): Boolean =
        when (this) {
            PIPER, VIBE_VOICE, CHATTERBOX, UNAMENTIS_GATEWAY, CUSTOM -> true
            else -> false
        }

    /**
     * Default port for this server type.
     */
    fun defaultPort(): Int =
        when (this) {
            UNAMENTIS_GATEWAY -> 8766
            OLLAMA -> 11434
            WHISPER -> 9000
            PIPER -> 5002
            VIBE_VOICE -> 8880
            CHATTERBOX -> 8001
            LLAMA_CPP -> 8080
            VLLM -> 8000
            CUSTOM -> 8080
        }

    /**
     * Human-readable display name for this server type.
     */
    fun displayName(): String =
        when (this) {
            UNAMENTIS_GATEWAY -> "UnaMentis Gateway"
            OLLAMA -> "Ollama"
            WHISPER -> "Whisper STT"
            PIPER -> "Piper TTS"
            VIBE_VOICE -> "VibeVoice TTS"
            CHATTERBOX -> "Chatterbox TTS"
            LLAMA_CPP -> "llama.cpp"
            VLLM -> "vLLM"
            CUSTOM -> "Custom"
        }
}

/**
 * Health status of a server.
 */
enum class ServerHealthStatus {
    /** Health status is unknown (never checked) */
    UNKNOWN,

    /** Currently checking health */
    CHECKING,

    /** Server is healthy and responding normally */
    HEALTHY,

    /** Server is responding but with degraded performance or errors */
    DEGRADED,

    /** Server is not responding or returning errors */
    UNHEALTHY,
    ;

    /**
     * Whether the server can be used for requests.
     */
    fun isUsable(): Boolean =
        when (this) {
            HEALTHY, DEGRADED -> true
            else -> false
        }
}

/**
 * Discovered capability of a server (e.g., model or voice).
 */
data class ServerCapability(
    val id: String,
    val name: String,
    val type: CapabilityType,
)

/**
 * Type of capability.
 */
enum class CapabilityType {
    LLM_MODEL,
    TTS_VOICE,
    STT_MODEL,
}

/**
 * Configuration for a self-hosted server.
 *
 * @property id Unique identifier for this server configuration
 * @property name Display name for the server
 * @property host Server hostname or IP address
 * @property port Server port number
 * @property type Type of server (determines endpoints and capabilities)
 * @property enabled Whether this server is enabled for use
 * @property authToken Optional authentication token
 * @property healthStatus Current health status
 * @property lastHealthCheck Timestamp of last health check (milliseconds)
 * @property capabilities Discovered capabilities (models, voices, etc.)
 */
data class ServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int,
    val type: ServerType,
    val enabled: Boolean = true,
    val authToken: String? = null,
    val healthStatus: ServerHealthStatus = ServerHealthStatus.UNKNOWN,
    val lastHealthCheck: Long = 0L,
    val capabilities: List<ServerCapability> = emptyList(),
) {
    /**
     * Base URL for this server (http only for local servers).
     */
    val baseUrl: String
        get() = "http://$host:$port"

    /**
     * Whether this server can be used for requests.
     */
    fun isUsable(): Boolean = enabled && healthStatus.isUsable()

    /**
     * Create a copy with updated health status.
     */
    fun withHealthStatus(
        status: ServerHealthStatus,
        timestamp: Long = System.currentTimeMillis(),
    ): ServerConfig =
        copy(
            healthStatus = status,
            lastHealthCheck = timestamp,
        )

    /**
     * Create a copy with updated capabilities.
     */
    fun withCapabilities(newCapabilities: List<ServerCapability>): ServerConfig = copy(capabilities = newCapabilities)
}
