package com.unamentis.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unamentis.core.network.ConnectionQuality
import com.unamentis.core.network.ConnectionState
import com.unamentis.core.network.ConnectivityMonitor

/**
 * Banner that displays when the device is offline.
 *
 * Features:
 * - Animates in/out based on connectivity
 * - Shows different messages based on connection state
 * - Indicates connection quality when online but degraded
 */
@Composable
fun OfflineBanner(
    connectivityMonitor: ConnectivityMonitor,
    modifier: Modifier = Modifier,
) {
    val connectionState by connectivityMonitor.connectionState.collectAsStateWithLifecycle()

    OfflineBannerContent(
        connectionState = connectionState,
        modifier = modifier,
    )
}

/**
 * Banner content that shows connection state.
 */
@Composable
fun OfflineBannerContent(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier,
) {
    val showBanner =
        !connectionState.isConnected ||
            connectionState.connectionQuality == ConnectionQuality.POOR

    AnimatedVisibility(
        visible = showBanner,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        val (icon, message, backgroundColor) = getBannerContent(connectionState)

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onError,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onError,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * Get banner content based on connection state.
 */
@Composable
private fun getBannerContent(connectionState: ConnectionState): Triple<ImageVector, String, androidx.compose.ui.graphics.Color> {
    return when {
        !connectionState.isConnected -> {
            Triple(
                Icons.Default.WifiOff,
                "No internet connection",
                MaterialTheme.colorScheme.error,
            )
        }
        connectionState.connectionQuality == ConnectionQuality.POOR -> {
            Triple(
                Icons.Default.SignalWifiOff,
                "Weak connection - some features may be limited",
                MaterialTheme.colorScheme.errorContainer,
            )
        }
        else -> {
            Triple(
                Icons.Default.SignalWifi4Bar,
                "Connected",
                MaterialTheme.colorScheme.primaryContainer,
            )
        }
    }
}

/**
 * Compact offline indicator for use in headers/toolbars.
 */
@Composable
fun OfflineIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = !isOnline,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = "Offline",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "Offline",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Connection quality indicator icon.
 */
@Composable
fun ConnectionQualityIndicator(
    quality: ConnectionQuality,
    modifier: Modifier = Modifier,
) {
    val (icon, tint, contentDescription) =
        when (quality) {
            ConnectionQuality.EXCELLENT ->
                Triple(
                    Icons.Default.SignalWifi4Bar,
                    MaterialTheme.colorScheme.primary,
                    "Excellent connection",
                )
            ConnectionQuality.GOOD ->
                Triple(
                    Icons.Default.SignalWifi4Bar,
                    MaterialTheme.colorScheme.primary,
                    "Good connection",
                )
            ConnectionQuality.MODERATE ->
                Triple(
                    Icons.Default.SignalWifi4Bar,
                    MaterialTheme.colorScheme.tertiary,
                    "Moderate connection",
                )
            ConnectionQuality.POOR ->
                Triple(
                    Icons.Default.SignalWifiOff,
                    MaterialTheme.colorScheme.error,
                    "Poor connection",
                )
            ConnectionQuality.UNKNOWN ->
                Triple(
                    Icons.Default.SignalWifi4Bar,
                    MaterialTheme.colorScheme.outline,
                    "Unknown connection quality",
                )
            ConnectionQuality.NONE ->
                Triple(
                    Icons.Default.WifiOff,
                    MaterialTheme.colorScheme.error,
                    "No connection",
                )
        }

    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier.size(20.dp),
        tint = tint,
    )
}
