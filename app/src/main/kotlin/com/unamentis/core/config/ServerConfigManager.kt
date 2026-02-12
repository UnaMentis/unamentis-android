package com.unamentis.core.config

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages self-hosted server configurations.
 *
 * Features:
 * - Add, update, remove server configurations
 * - Periodic health monitoring
 * - Auto-discovery of local servers
 * - Capability discovery (models, voices)
 * - Persistence to SharedPreferences
 *
 * Usage:
 * ```kotlin
 * val manager = ServerConfigManager(context, client)
 *
 * // Add a server
 * manager.addServer(
 *     ServerConfig(
 *         name = "Local Ollama",
 *         host = "192.168.1.100",
 *         port = 11434,
 *         type = ServerType.OLLAMA
 *     )
 * )
 *
 * // Get best endpoint for LLM
 * val endpoint = manager.getBestLLMEndpoint()
 *
 * // Start health monitoring
 * manager.startHealthMonitoring()
 * ```
 */
@Singleton
class ServerConfigManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val client: OkHttpClient,
    ) {
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private var healthMonitorJob: Job? = null

        private val _servers = MutableStateFlow<List<ServerConfig>>(emptyList())
        val servers: StateFlow<List<ServerConfig>> = _servers.asStateFlow()

        private val healthCheckClient =
            client.newBuilder()
                .connectTimeout(HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()

        init {
            loadServers()
        }

        /**
         * Add a new server configuration.
         */
        fun addServer(config: ServerConfig) {
            val currentServers = _servers.value.toMutableList()
            currentServers.add(config)
            _servers.value = currentServers
            saveServers()
            Log.i(TAG, "Added server: ${config.name} (${config.type})")
        }

        /**
         * Update an existing server configuration.
         */
        fun updateServer(config: ServerConfig) {
            val currentServers = _servers.value.toMutableList()
            val index = currentServers.indexOfFirst { it.id == config.id }
            if (index >= 0) {
                currentServers[index] = config
                _servers.value = currentServers
                saveServers()
                Log.i(TAG, "Updated server: ${config.name}")
            }
        }

        /**
         * Remove a server configuration.
         */
        fun removeServer(serverId: String) {
            val currentServers = _servers.value.toMutableList()
            val removed = currentServers.removeIf { it.id == serverId }
            if (removed) {
                _servers.value = currentServers
                saveServers()
                Log.i(TAG, "Removed server: $serverId")
            }
        }

        /**
         * Enable or disable a server.
         */
        fun setServerEnabled(
            serverId: String,
            enabled: Boolean,
        ) {
            val server = _servers.value.find { it.id == serverId } ?: return
            updateServer(server.copy(enabled = enabled))
        }

        /**
         * Get all LLM-capable servers.
         */
        fun getLLMServers(): List<ServerConfig> = _servers.value.filter { it.type.isLLM() && it.enabled }

        /**
         * Get all STT-capable servers.
         */
        fun getSTTServers(): List<ServerConfig> = _servers.value.filter { it.type.isSTT() && it.enabled }

        /**
         * Get all TTS-capable servers.
         */
        fun getTTSServers(): List<ServerConfig> = _servers.value.filter { it.type.isTTS() && it.enabled }

        /**
         * Get the best available LLM endpoint.
         * Returns null if no healthy LLM server is available.
         */
        fun getBestLLMEndpoint(): String? =
            getLLMServers()
                .filter { it.isUsable() }
                .minByOrNull { it.healthStatus.ordinal }
                ?.let { "${it.baseUrl}/v1/chat/completions" }

        /**
         * Get the best available STT endpoint.
         * Returns null if no healthy STT server is available.
         */
        fun getBestSTTEndpoint(): String? =
            getSTTServers()
                .filter { it.isUsable() }
                .minByOrNull { it.healthStatus.ordinal }
                ?.let { "${it.baseUrl}/v1/audio/transcriptions" }

        /**
         * Get the best available TTS endpoint.
         * Returns null if no healthy TTS server is available.
         */
        fun getBestTTSEndpoint(): String? =
            getTTSServers()
                .filter { it.isUsable() }
                .minByOrNull { it.healthStatus.ordinal }
                ?.let { "${it.baseUrl}/v1/audio/speech" }

        /**
         * Get the management server (UnaMentis Gateway) base URL.
         *
         * Returns the base URL of the first healthy UnaMentis Gateway server,
         * or the default emulator URL (10.0.2.2:8766) if none configured.
         */
        fun getManagementServerUrl(): String {
            val gatewayServers =
                _servers.value.filter {
                    it.type == ServerType.UNAMENTIS_GATEWAY && it.enabled
                }
            // Prefer healthy servers, fall back to any enabled gateway
            val usableServer =
                gatewayServers
                    .filter { it.healthStatus.isUsable() }
                    .minByOrNull { it.healthStatus.ordinal }
                    ?: gatewayServers.firstOrNull()

            return usableServer?.baseUrl ?: DEFAULT_MANAGEMENT_URL
        }

        /**
         * Get the WebSocket URL for the management server.
         *
         * Converts the HTTP URL to a WebSocket URL (ws://).
         */
        fun getManagementWebSocketUrl(): String {
            val httpUrl = getManagementServerUrl()
            return httpUrl.replace("http://", "ws://")
        }

        companion object {
            private const val TAG = "ServerConfigManager"
            private const val PREFS_NAME = "unamentis_server_configs"
            private const val KEY_CONFIGS = "server_configs"
            private const val HEALTH_CHECK_INTERVAL_MS = 30_000L
            private const val HEALTH_CHECK_TIMEOUT_MS = 5_000L

            /** Default management URL for Android emulator */
            const val DEFAULT_MANAGEMENT_URL = "http://10.0.2.2:8766"

            /** Default log server URL for Android emulator */
            const val DEFAULT_LOG_SERVER_URL = "http://10.0.2.2:8765"

            // Common ports to probe during auto-discovery
            private val DISCOVERY_PORTS =
                listOf(
                    8766 to ServerType.UNAMENTIS_GATEWAY,
                    11434 to ServerType.OLLAMA,
                    9000 to ServerType.WHISPER,
                    5002 to ServerType.PIPER,
                    8880 to ServerType.VIBE_VOICE,
                    8001 to ServerType.CHATTERBOX,
                    8080 to ServerType.LLAMA_CPP,
                )
        }

        /**
         * Start periodic health monitoring.
         */
        fun startHealthMonitoring() {
            if (healthMonitorJob?.isActive == true) return

            healthMonitorJob =
                scope.launch {
                    while (isActive) {
                        checkAllServersHealth()
                        delay(HEALTH_CHECK_INTERVAL_MS)
                    }
                }
            Log.i(TAG, "Started health monitoring")
        }

        /**
         * Stop health monitoring.
         */
        fun stopHealthMonitoring() {
            healthMonitorJob?.cancel()
            healthMonitorJob = null
            Log.i(TAG, "Stopped health monitoring")
        }

        /**
         * Check health of all enabled servers.
         */
        suspend fun checkAllServersHealth() {
            val currentServers = _servers.value
            val updatedServers =
                currentServers.map { server ->
                    if (server.enabled) {
                        scope.async {
                            checkServerHealth(server)
                        }
                    } else {
                        scope.async { server }
                    }
                }.awaitAll()

            _servers.value = updatedServers
            saveServers()
        }

        /**
         * Check health of a specific server.
         */
        suspend fun checkServerHealth(server: ServerConfig): ServerConfig =
            withContext(Dispatchers.IO) {
                val updatedServer = server.copy(healthStatus = ServerHealthStatus.CHECKING)
                updateServerInList(updatedServer)

                try {
                    val healthUrl = "${server.baseUrl}/health"
                    val request =
                        Request.Builder()
                            .url(healthUrl)
                            .get()
                            .apply {
                                server.authToken?.let { token ->
                                    addHeader("Authorization", "Bearer $token")
                                }
                            }
                            .build()

                    val response = healthCheckClient.newCall(request).execute()
                    val status =
                        when {
                            response.code == 200 -> ServerHealthStatus.HEALTHY
                            response.code == 503 -> ServerHealthStatus.DEGRADED
                            else -> ServerHealthStatus.UNHEALTHY
                        }
                    response.close()

                    Log.d(TAG, "Health check for ${server.name}: $status")
                    server.withHealthStatus(status)
                } catch (e: Exception) {
                    Log.w(TAG, "Health check failed for ${server.name}: ${e.message}")
                    server.withHealthStatus(ServerHealthStatus.UNHEALTHY)
                }
            }

        /**
         * Auto-discover servers on localhost and local network.
         *
         * @param hostAddresses List of host addresses to probe (e.g., "127.0.0.1", "192.168.1.100")
         * @return List of discovered server configurations
         */
        suspend fun discoverServers(hostAddresses: List<String> = listOf("127.0.0.1", "10.0.2.2")): List<ServerConfig> =
            withContext(Dispatchers.IO) {
                val discovered = mutableListOf<ServerConfig>()

                for (host in hostAddresses) {
                    for ((port, type) in DISCOVERY_PORTS) {
                        try {
                            val config = probeServer(host, port, type)
                            if (config != null) {
                                discovered.add(config)
                                Log.i(TAG, "Discovered server: ${config.name} at $host:$port")
                            }
                        } catch (_: Exception) {
                            // Server not available at this address/port - expected during discovery
                        }
                    }
                }

                discovered
            }

        /**
         * Probe a specific address/port for a server.
         */
        private suspend fun probeServer(
            host: String,
            port: Int,
            type: ServerType,
        ): ServerConfig? =
            withContext(Dispatchers.IO) {
                try {
                    val baseUrl = "http://$host:$port"
                    val healthUrl = "$baseUrl/health"

                    val request =
                        Request.Builder()
                            .url(healthUrl)
                            .get()
                            .build()

                    val response =
                        healthCheckClient.newCall(request).execute().use { resp ->
                            if (resp.isSuccessful) {
                                // Check if this is a UnaMentis gateway
                                val body = resp.body?.string()
                                val isGateway = body?.contains("unamentis_server") == true

                                val actualType = if (isGateway) ServerType.UNAMENTIS_GATEWAY else type
                                val name =
                                    when (actualType) {
                                        ServerType.UNAMENTIS_GATEWAY -> "UnaMentis Gateway"
                                        ServerType.OLLAMA -> "Ollama"
                                        ServerType.WHISPER -> "Whisper"
                                        ServerType.PIPER -> "Piper"
                                        ServerType.VIBE_VOICE -> "VibeVoice"
                                        ServerType.CHATTERBOX -> "Chatterbox"
                                        ServerType.LLAMA_CPP -> "llama.cpp"
                                        ServerType.VLLM -> "vLLM"
                                        ServerType.CUSTOM -> "Custom Server"
                                    }

                                ServerConfig(
                                    name = "$name ($host)",
                                    host = host,
                                    port = port,
                                    type = actualType,
                                    healthStatus = ServerHealthStatus.HEALTHY,
                                    lastHealthCheck = System.currentTimeMillis(),
                                )
                            } else {
                                null
                            }
                        }

                    response
                } catch (_: Exception) {
                    // Server not reachable at this address/port
                    null
                }
            }

        /**
         * Discover capabilities for a server (models, voices, etc.).
         */
        suspend fun discoverCapabilities(server: ServerConfig): ServerConfig =
            withContext(Dispatchers.IO) {
                val capabilities = mutableListOf<ServerCapability>()

                try {
                    when (server.type) {
                        ServerType.OLLAMA -> {
                            capabilities.addAll(discoverOllamaModels(server))
                        }
                        ServerType.PIPER -> {
                            capabilities.addAll(discoverPiperVoices(server))
                        }
                        ServerType.VIBE_VOICE -> {
                            capabilities.addAll(discoverVibeVoiceVoices(server))
                        }
                        else -> {
                            // Other server types may not have discoverable capabilities
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to discover capabilities for ${server.name}: ${e.message}")
                }

                server.withCapabilities(capabilities)
            }

        /**
         * Discover Ollama models.
         */
        private suspend fun discoverOllamaModels(server: ServerConfig): List<ServerCapability> =
            withContext(Dispatchers.IO) {
                try {
                    val request =
                        Request.Builder()
                            .url("${server.baseUrl}/api/tags")
                            .get()
                            .build()

                    healthCheckClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: return@withContext emptyList()
                            val modelsResponse = json.decodeFromString<OllamaModelsResponse>(body)
                            modelsResponse.models.map { model ->
                                ServerCapability(
                                    id = model.name,
                                    name = model.name,
                                    type = CapabilityType.LLM_MODEL,
                                )
                            }
                        } else {
                            emptyList()
                        }
                    }
                } catch (_: Exception) {
                    // Failed to discover Ollama models - return empty list
                    emptyList()
                }
            }

        /**
         * Discover Piper voices.
         */
        private suspend fun discoverPiperVoices(server: ServerConfig): List<ServerCapability> =
            withContext(Dispatchers.IO) {
                try {
                    val request =
                        Request.Builder()
                            .url("${server.baseUrl}/voices")
                            .get()
                            .build()

                    healthCheckClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: return@withContext emptyList()
                            val voices = json.decodeFromString<List<String>>(body)
                            voices.map { voice ->
                                ServerCapability(
                                    id = voice,
                                    name = voice,
                                    type = CapabilityType.TTS_VOICE,
                                )
                            }
                        } else {
                            emptyList()
                        }
                    }
                } catch (_: Exception) {
                    // Failed to discover Piper voices - return empty list
                    emptyList()
                }
            }

        /**
         * Discover VibeVoice voices.
         */
        private suspend fun discoverVibeVoiceVoices(server: ServerConfig): List<ServerCapability> =
            withContext(Dispatchers.IO) {
                try {
                    val request =
                        Request.Builder()
                            .url("${server.baseUrl}/v1/audio/voices")
                            .get()
                            .build()

                    healthCheckClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: return@withContext emptyList()
                            val voicesResponse = json.decodeFromString<VibeVoiceVoicesResponse>(body)
                            voicesResponse.voices.map { voice ->
                                ServerCapability(
                                    id = voice.id,
                                    name = voice.name,
                                    type = CapabilityType.TTS_VOICE,
                                )
                            }
                        } else {
                            emptyList()
                        }
                    }
                } catch (_: Exception) {
                    // Failed to discover VibeVoice voices - return empty list
                    emptyList()
                }
            }

        /**
         * Add discovered servers that are not already configured.
         */
        fun addDiscoveredServers(discovered: List<ServerConfig>) {
            val existing = _servers.value
            val newServers =
                discovered.filter { disc ->
                    existing.none { it.host == disc.host && it.port == disc.port }
                }
            if (newServers.isNotEmpty()) {
                _servers.value = existing + newServers
                saveServers()
                Log.i(TAG, "Added ${newServers.size} discovered servers")
            }
        }

        private fun updateServerInList(server: ServerConfig) {
            val currentServers = _servers.value.toMutableList()
            val index = currentServers.indexOfFirst { it.id == server.id }
            if (index >= 0) {
                currentServers[index] = server
                _servers.value = currentServers
            }
        }

        private fun loadServers() {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val jsonStr = prefs.getString(KEY_CONFIGS, null)
                if (jsonStr != null) {
                    val servers = json.decodeFromString<List<ServerConfigDto>>(jsonStr)
                    _servers.value = servers.map { it.toServerConfig() }
                    Log.i(TAG, "Loaded ${servers.size} server configurations")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load server configurations", e)
            }
        }

        private fun saveServers() {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val dtos = _servers.value.map { ServerConfigDto.fromServerConfig(it) }
                val jsonStr = json.encodeToString(dtos)
                prefs.edit().putString(KEY_CONFIGS, jsonStr).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save server configurations", e)
            }
        }
    }

/**
 * DTO for ServerConfig serialization.
 */
@Serializable
private data class ServerConfigDto(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val type: String,
    val enabled: Boolean = true,
    val authToken: String? = null,
    val healthStatus: String = "UNKNOWN",
    val lastHealthCheck: Long = 0L,
    val capabilities: List<ServerCapabilityDto> = emptyList(),
) {
    fun toServerConfig(): ServerConfig =
        ServerConfig(
            id = id,
            name = name,
            host = host,
            port = port,
            type = ServerType.valueOf(type),
            enabled = enabled,
            authToken = authToken,
            healthStatus = ServerHealthStatus.valueOf(healthStatus),
            lastHealthCheck = lastHealthCheck,
            capabilities =
                capabilities.map {
                    ServerCapability(
                        id = it.id,
                        name = it.name,
                        type = CapabilityType.valueOf(it.type),
                    )
                },
        )

    companion object {
        fun fromServerConfig(config: ServerConfig): ServerConfigDto =
            ServerConfigDto(
                id = config.id,
                name = config.name,
                host = config.host,
                port = config.port,
                type = config.type.name,
                enabled = config.enabled,
                authToken = config.authToken,
                healthStatus = config.healthStatus.name,
                lastHealthCheck = config.lastHealthCheck,
                capabilities =
                    config.capabilities.map {
                        ServerCapabilityDto(
                            id = it.id,
                            name = it.name,
                            type = it.type.name,
                        )
                    },
            )
    }
}

@Serializable
private data class ServerCapabilityDto(
    val id: String,
    val name: String,
    val type: String,
)

/**
 * Response from Ollama /api/tags endpoint.
 */
@Serializable
private data class OllamaModelsResponse(
    val models: List<OllamaModel> = emptyList(),
)

@Serializable
private data class OllamaModel(
    val name: String,
    val size: Long = 0L,
    val digest: String = "",
)

/**
 * Response from VibeVoice /v1/audio/voices endpoint.
 */
@Serializable
private data class VibeVoiceVoicesResponse(
    val voices: List<VibeVoiceVoice> = emptyList(),
)

@Serializable
private data class VibeVoiceVoice(
    val id: String,
    val name: String,
)
