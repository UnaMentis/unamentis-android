package com.unamentis.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors network connectivity and provides real-time status updates.
 *
 * Features:
 * - Real-time connectivity monitoring
 * - Network type detection (WiFi, Cellular, etc.)
 * - Connection quality assessment
 * - Offline mode detection
 */
@Singleton
class ConnectivityMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        private val _connectionState = MutableStateFlow(getInitialConnectionState())
        val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

        private val _isOnline = MutableStateFlow(isCurrentlyOnline())
        val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

        init {
            registerNetworkCallback()
        }

        /**
         * Get the initial connection state on startup.
         */
        private fun getInitialConnectionState(): ConnectionState {
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

            return if (capabilities != null) {
                ConnectionState(
                    isConnected = true,
                    networkType = getNetworkType(capabilities),
                    connectionQuality = getConnectionQuality(capabilities),
                )
            } else {
                ConnectionState(
                    isConnected = false,
                    networkType = NetworkType.NONE,
                    connectionQuality = ConnectionQuality.NONE,
                )
            }
        }

        /**
         * Check if currently online.
         */
        private fun isCurrentlyOnline(): Boolean {
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
            return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        /**
         * Register network callback to monitor connectivity changes.
         */
        private fun registerNetworkCallback() {
            val networkRequest =
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

            val networkCallback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        updateConnectionState(network)
                    }

                    override fun onLost(network: Network) {
                        _connectionState.value =
                            ConnectionState(
                                isConnected = false,
                                networkType = NetworkType.NONE,
                                connectionQuality = ConnectionQuality.NONE,
                            )
                        _isOnline.value = false
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        _connectionState.value =
                            ConnectionState(
                                isConnected = true,
                                networkType = getNetworkType(capabilities),
                                connectionQuality = getConnectionQuality(capabilities),
                            )
                        _isOnline.value =
                            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    }
                }

            try {
                connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            } catch (e: Exception) {
                // Handle cases where the callback cannot be registered
            }
        }

        /**
         * Update connection state when network becomes available.
         */
        private fun updateConnectionState(network: Network) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            _connectionState.value =
                if (capabilities != null) {
                    ConnectionState(
                        isConnected = true,
                        networkType = getNetworkType(capabilities),
                        connectionQuality = getConnectionQuality(capabilities),
                    )
                } else {
                    ConnectionState(
                        isConnected = true,
                        networkType = NetworkType.UNKNOWN,
                        connectionQuality = ConnectionQuality.UNKNOWN,
                    )
                }
            _isOnline.value = isCurrentlyOnline()
        }

        /**
         * Determine network type from capabilities.
         */
        private fun getNetworkType(capabilities: NetworkCapabilities): NetworkType {
            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkType.BLUETOOTH
                else -> NetworkType.UNKNOWN
            }
        }

        /**
         * Estimate connection quality from bandwidth.
         */
        private fun getConnectionQuality(capabilities: NetworkCapabilities): ConnectionQuality {
            val downstreamBandwidth = capabilities.linkDownstreamBandwidthKbps

            return when {
                downstreamBandwidth <= 0 -> ConnectionQuality.UNKNOWN
                downstreamBandwidth < 150 -> ConnectionQuality.POOR
                downstreamBandwidth < 550 -> ConnectionQuality.MODERATE
                downstreamBandwidth < 2000 -> ConnectionQuality.GOOD
                else -> ConnectionQuality.EXCELLENT
            }
        }

        /**
         * Flow that emits connectivity changes.
         */
        fun observeConnectivity(): Flow<ConnectionState> =
            callbackFlow {
                val networkCallback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            val capabilities = connectivityManager.getNetworkCapabilities(network)
                            if (capabilities != null) {
                                trySend(
                                    ConnectionState(
                                        isConnected = true,
                                        networkType = getNetworkType(capabilities),
                                        connectionQuality = getConnectionQuality(capabilities),
                                    ),
                                )
                            }
                        }

                        override fun onLost(network: Network) {
                            trySend(
                                ConnectionState(
                                    isConnected = false,
                                    networkType = NetworkType.NONE,
                                    connectionQuality = ConnectionQuality.NONE,
                                ),
                            )
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            capabilities: NetworkCapabilities,
                        ) {
                            trySend(
                                ConnectionState(
                                    isConnected = true,
                                    networkType = getNetworkType(capabilities),
                                    connectionQuality = getConnectionQuality(capabilities),
                                ),
                            )
                        }
                    }

                val networkRequest =
                    NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()

                connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

                awaitClose {
                    connectivityManager.unregisterNetworkCallback(networkCallback)
                }
            }.distinctUntilChanged()
    }

/**
 * Current connection state.
 */
data class ConnectionState(
    val isConnected: Boolean,
    val networkType: NetworkType,
    val connectionQuality: ConnectionQuality,
)

/**
 * Type of network connection.
 */
enum class NetworkType {
    WIFI,
    CELLULAR,
    ETHERNET,
    BLUETOOTH,
    UNKNOWN,
    NONE,
}

/**
 * Quality of the network connection.
 */
enum class ConnectionQuality {
    EXCELLENT,
    GOOD,
    MODERATE,
    POOR,
    UNKNOWN,
    NONE,
}
