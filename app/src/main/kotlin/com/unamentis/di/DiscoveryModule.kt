package com.unamentis.di

import android.content.Context
import com.unamentis.core.config.ServerConfigManager
import com.unamentis.core.config.ServerConfigManagerDiscovery
import com.unamentis.core.discovery.CachedServerDiscovery
import com.unamentis.core.discovery.DeviceDiscoveryManager
import com.unamentis.core.discovery.NsdDiscovery
import com.unamentis.core.discovery.SubnetScanDiscovery
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Hilt module providing server discovery dependencies.
 *
 * Provides:
 * - [CachedServerDiscovery] - Tier 1: cached server lookup
 * - [NsdDiscovery] - Tier 2: NSD/mDNS discovery
 * - [SubnetScanDiscovery] - Tier 3: subnet scanning
 * - [DeviceDiscoveryManager] - Multi-tier discovery orchestrator
 * - [ServerConfigManagerDiscovery] - Integration bridge
 */
@Module
@InstallIn(SingletonComponent::class)
object DiscoveryModule {
    /**
     * Provides the cached server discovery tier.
     */
    @Provides
    @Singleton
    fun provideCachedServerDiscovery(
        @ApplicationContext context: Context,
        client: OkHttpClient,
    ): CachedServerDiscovery {
        return CachedServerDiscovery(context, client)
    }

    /**
     * Provides the NSD/mDNS discovery tier.
     */
    @Provides
    @Singleton
    fun provideNsdDiscovery(
        @ApplicationContext context: Context,
    ): NsdDiscovery {
        return NsdDiscovery(context)
    }

    /**
     * Provides the subnet scan discovery tier.
     */
    @Provides
    @Singleton
    fun provideSubnetScanDiscovery(client: OkHttpClient): SubnetScanDiscovery {
        return SubnetScanDiscovery(client)
    }

    /**
     * Provides the device discovery manager.
     */
    @Provides
    @Singleton
    fun provideDeviceDiscoveryManager(
        cachedDiscovery: CachedServerDiscovery,
        nsdDiscovery: NsdDiscovery,
        subnetDiscovery: SubnetScanDiscovery,
        client: OkHttpClient,
    ): DeviceDiscoveryManager {
        return DeviceDiscoveryManager(
            cachedDiscovery = cachedDiscovery,
            nsdDiscovery = nsdDiscovery,
            subnetDiscovery = subnetDiscovery,
            client = client,
        )
    }

    /**
     * Provides the integration bridge between discovery and server config.
     */
    @Provides
    @Singleton
    fun provideServerConfigManagerDiscovery(
        serverConfigManager: ServerConfigManager,
        discoveryManager: DeviceDiscoveryManager,
    ): ServerConfigManagerDiscovery {
        return ServerConfigManagerDiscovery(serverConfigManager, discoveryManager)
    }
}
