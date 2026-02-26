package com.unamentis.core.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Tier 2: Discovers UnaMentis servers via Android NSD (mDNS/Bonjour).
 *
 * Uses [NsdManager] to browse for services advertised with the
 * `_unamentis._tcp` service type on the local network.
 *
 * Android NSD is the platform equivalent of Apple's Bonjour/NWBrowser.
 * It discovers services advertised via mDNS (multicast DNS) and resolves
 * them to host/port pairs.
 *
 * @property context Application context for accessing NsdManager
 * @property serviceType The mDNS service type to browse for
 */
class NsdDiscovery(
    private val context: Context,
    private val serviceType: String = SERVICE_TYPE,
) : DiscoveryTierStrategy {
    override val tier: DiscoveryTier = DiscoveryTier.NSD

    private val isCancelled = AtomicBoolean(false)

    @Volatile
    private var activeListener: NsdManager.DiscoveryListener? = null

    override suspend fun discover(timeoutMs: Long): DiscoveredServer? {
        isCancelled.set(false)

        val nsdManager =
            context.getSystemService(Context.NSD_SERVICE) as? NsdManager
                ?: run {
                    Log.w(TAG, "NsdManager not available")
                    return null
                }

        return withTimeoutOrNull(timeoutMs) {
            discoverWithNsd(nsdManager)
        }
    }

    override fun cancel() {
        isCancelled.set(true)
        stopDiscovery()
    }

    /**
     * Performs NSD discovery using a cancellable coroutine.
     *
     * Starts an NSD browser, waits for a service to be found and resolved,
     * then returns the discovered server.
     */
    private suspend fun discoverWithNsd(nsdManager: NsdManager): DiscoveredServer? =
        suspendCancellableCoroutine { continuation ->
            val hasResumed = AtomicBoolean(false)

            val safeResume: (DiscoveredServer?) -> Unit = { server ->
                if (hasResumed.compareAndSet(false, true)) {
                    stopDiscovery()
                    continuation.resume(server)
                }
            }

            val discoveryListener =
                object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {
                        Log.d(TAG, "NSD discovery started for $serviceType")
                    }

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        Log.i(TAG, "Found NSD service: ${serviceInfo.serviceName}")

                        if (isCancelled.get()) return

                        resolveService(nsdManager, serviceInfo, continuation, hasResumed)
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        Log.d(TAG, "NSD service lost: ${serviceInfo.serviceName}")
                    }

                    override fun onDiscoveryStopped(serviceType: String) {
                        Log.d(TAG, "NSD discovery stopped for $serviceType")
                    }

                    override fun onStartDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) {
                        Log.w(TAG, "NSD discovery start failed: error code $errorCode")
                        safeResume(null)
                    }

                    override fun onStopDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) {
                        Log.w(TAG, "NSD discovery stop failed: error code $errorCode")
                    }
                }

            activeListener = discoveryListener

            continuation.invokeOnCancellation {
                stopDiscovery()
            }

            try {
                nsdManager.discoverServices(
                    serviceType,
                    NsdManager.PROTOCOL_DNS_SD,
                    discoveryListener,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start NSD discovery", e)
                safeResume(null)
            }
        }

    /**
     * Resolve a discovered NSD service to get its host and port.
     */
    private fun resolveService(
        nsdManager: NsdManager,
        serviceInfo: NsdServiceInfo,
        continuation: CancellableContinuation<DiscoveredServer?>,
        hasResumed: AtomicBoolean,
    ) {
        val resolveListener =
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(
                    info: NsdServiceInfo,
                    errorCode: Int,
                ) {
                    Log.w(TAG, "Failed to resolve ${info.serviceName}: error code $errorCode")
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host?.hostAddress ?: return
                    val port = info.port

                    Log.i(TAG, "Resolved ${info.serviceName} to $host:$port")

                    val server =
                        DiscoveredServer(
                            name = info.serviceName,
                            host = host,
                            port = port,
                            discoveryMethod = DiscoveryMethod.NSD,
                        )

                    if (hasResumed.compareAndSet(false, true)) {
                        stopDiscovery()
                        continuation.resume(server)
                    }
                }
            }

        try {
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve service", e)
        }
    }

    /**
     * Stop the active NSD discovery listener.
     */
    private fun stopDiscovery() {
        val listener = activeListener ?: return
        activeListener = null

        val nsdManager =
            context.getSystemService(Context.NSD_SERVICE) as? NsdManager
                ?: return

        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (e: IllegalArgumentException) {
            // Listener was not registered or already stopped
            Log.d(TAG, "NSD listener already stopped")
        }
    }

    companion object {
        private const val TAG = "NsdDiscovery"

        /** The mDNS service type for UnaMentis servers */
        const val SERVICE_TYPE = "_unamentis._tcp."
    }
}
