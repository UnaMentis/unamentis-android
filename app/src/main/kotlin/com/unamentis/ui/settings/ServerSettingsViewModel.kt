package com.unamentis.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.core.config.ServerConfig
import com.unamentis.core.config.ServerConfigManager
import com.unamentis.core.config.ServerHealthStatus
import com.unamentis.core.config.ServerType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Server Settings screen.
 */
data class ServerSettingsUiState(
    val servers: List<ServerConfig> = emptyList(),
    val isDiscovering: Boolean = false,
    val discoveredServers: List<ServerConfig> = emptyList(),
    val showAddServerDialog: Boolean = false,
    val showDiscoveryResultsDialog: Boolean = false,
    val testingServerId: String? = null,
)

/**
 * ViewModel for the Server Settings screen.
 *
 * Manages server configurations, health checks, and discovery.
 */
@HiltViewModel
class ServerSettingsViewModel
    @Inject
    constructor(
        private val serverConfigManager: ServerConfigManager,
    ) : ViewModel() {
        val servers: StateFlow<List<ServerConfig>> =
            serverConfigManager.servers
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        private val _isDiscovering = MutableStateFlow(false)
        val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

        private val _discoveredServers = MutableStateFlow<List<ServerConfig>>(emptyList())
        val discoveredServers: StateFlow<List<ServerConfig>> = _discoveredServers.asStateFlow()

        private val _showAddServerDialog = MutableStateFlow(false)
        val showAddServerDialog: StateFlow<Boolean> = _showAddServerDialog.asStateFlow()

        private val _showDiscoveryResultsDialog = MutableStateFlow(false)
        val showDiscoveryResultsDialog: StateFlow<Boolean> = _showDiscoveryResultsDialog.asStateFlow()

        private val _testingServerId = MutableStateFlow<String?>(null)
        val testingServerId: StateFlow<String?> = _testingServerId.asStateFlow()

        init {
            // Start health monitoring when the screen is shown
            serverConfigManager.startHealthMonitoring()
        }

        /**
         * Overall health status of all enabled servers.
         */
        fun getOverallStatus(): ServerHealthStatus {
            val enabledServers = servers.value.filter { it.enabled }
            return when {
                enabledServers.isEmpty() -> ServerHealthStatus.UNKNOWN
                enabledServers.all { it.healthStatus == ServerHealthStatus.HEALTHY } -> ServerHealthStatus.HEALTHY
                enabledServers.any { it.healthStatus.isUsable() } -> ServerHealthStatus.DEGRADED
                else -> ServerHealthStatus.UNHEALTHY
            }
        }

        /**
         * Status message describing the current server state.
         */
        fun getStatusMessage(): String {
            val enabledServers = servers.value.filter { it.enabled }
            if (enabledServers.isEmpty()) return "No servers configured"
            val healthy = enabledServers.count { it.healthStatus.isUsable() }
            return "$healthy/${enabledServers.size} servers available"
        }

        /**
         * Show the add server dialog.
         */
        fun showAddServerDialog() {
            _showAddServerDialog.value = true
        }

        /**
         * Hide the add server dialog.
         */
        fun hideAddServerDialog() {
            _showAddServerDialog.value = false
        }

        /**
         * Hide the discovery results dialog.
         */
        fun hideDiscoveryResultsDialog() {
            _showDiscoveryResultsDialog.value = false
        }

        /**
         * Add a new server configuration.
         */
        fun addServer(config: ServerConfig) {
            serverConfigManager.addServer(config)
            _showAddServerDialog.value = false
        }

        /**
         * Update an existing server configuration.
         */
        fun updateServer(config: ServerConfig) {
            serverConfigManager.updateServer(config)
        }

        /**
         * Remove a server configuration.
         */
        fun removeServer(serverId: String) {
            serverConfigManager.removeServer(serverId)
        }

        /**
         * Toggle server enabled state.
         */
        fun toggleServer(serverId: String) {
            val server = servers.value.find { it.id == serverId } ?: return
            serverConfigManager.setServerEnabled(serverId, !server.enabled)
        }

        /**
         * Test connection to a specific server.
         */
        fun testServer(serverId: String) {
            viewModelScope.launch {
                _testingServerId.value = serverId
                val server = servers.value.find { it.id == serverId } ?: return@launch
                serverConfigManager.checkServerHealth(server)
                _testingServerId.value = null
            }
        }

        /**
         * Refresh all server health statuses.
         */
        fun refreshServers() {
            viewModelScope.launch {
                serverConfigManager.checkAllServersHealth()
            }
        }

        /**
         * Discover servers on the local network.
         */
        fun discoverServers() {
            viewModelScope.launch {
                _isDiscovering.value = true
                val discovered = serverConfigManager.discoverServers()
                _discoveredServers.value = discovered
                _isDiscovering.value = false

                if (discovered.isNotEmpty()) {
                    _showDiscoveryResultsDialog.value = true
                }
            }
        }

        /**
         * Add all discovered servers.
         */
        fun addDiscoveredServers() {
            serverConfigManager.addDiscoveredServers(_discoveredServers.value)
            _discoveredServers.value = emptyList()
            _showDiscoveryResultsDialog.value = false
        }

        /**
         * Add a default localhost server configuration.
         *
         * This is a convenience method for quickly adding the management gateway server.
         */
        fun addDefaultServer() {
            // Android emulator's host machine IP
            val defaultServer =
                ServerConfig(
                    name = "Local Mac Server",
                    host = "10.0.2.2",
                    port = ServerType.UNAMENTIS_GATEWAY.defaultPort(),
                    type = ServerType.UNAMENTIS_GATEWAY,
                )
            serverConfigManager.addServer(defaultServer)
        }

        /**
         * Get the currently configured management server URL.
         *
         * Returns the base URL of the first healthy UnaMentis Gateway server,
         * or the default emulator URL if none configured.
         */
        fun getManagementServerUrl(): String {
            val gatewayServers =
                servers.value.filter {
                    it.type == ServerType.UNAMENTIS_GATEWAY && it.enabled && it.healthStatus.isUsable()
                }
            return gatewayServers.firstOrNull()?.baseUrl ?: "http://10.0.2.2:8766"
        }

        override fun onCleared() {
            super.onCleared()
            // Keep monitoring running - it's a singleton service
        }
    }
