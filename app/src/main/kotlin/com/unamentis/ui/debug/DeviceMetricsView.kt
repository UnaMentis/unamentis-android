package com.unamentis.ui.debug

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.unamentis.R
import com.unamentis.ui.components.IOSCard
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import com.unamentis.ui.theme.iOSGreen
import com.unamentis.ui.theme.iOSOrange
import com.unamentis.ui.theme.iOSRed
import com.unamentis.ui.util.safeProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Real-time device metrics view for debug builds.
 *
 * Displays live device health information including memory usage,
 * battery level, storage availability, and network type. Metrics
 * are refreshed every second while the view is visible.
 *
 * Matches iOS DeviceMetricsView feature parity with Android-specific
 * metrics (Runtime memory, BatteryManager, StatFs).
 *
 * @param onNavigateBack Callback to navigate back
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceMetricsView(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backDescription = stringResource(R.string.cd_go_back)

    // Metric states
    var jvmUsedMemory by remember { mutableLongStateOf(0L) }
    var jvmTotalMemory by remember { mutableLongStateOf(0L) }
    var jvmMaxMemory by remember { mutableLongStateOf(0L) }
    var systemAvailableMemory by remember { mutableLongStateOf(0L) }
    var systemTotalMemory by remember { mutableLongStateOf(0L) }
    var batteryLevel by remember { mutableIntStateOf(-1) }
    var isCharging by remember { mutableStateOf(false) }
    var storageAvailable by remember { mutableLongStateOf(0L) }
    var storageTotal by remember { mutableLongStateOf(0L) }
    var networkType by remember { mutableStateOf("") }
    var isMonitoring by remember { mutableStateOf(true) }

    // Periodic refresh
    DisposableEffect(isMonitoring) {
        val job = scope.launch {
            while (isActive && isMonitoring) {
                withContext(Dispatchers.IO) {
                    val metrics = collectDeviceMetrics(context)
                    jvmUsedMemory = metrics.jvmUsedMemory
                    jvmTotalMemory = metrics.jvmTotalMemory
                    jvmMaxMemory = metrics.jvmMaxMemory
                    systemAvailableMemory = metrics.systemAvailableMemory
                    systemTotalMemory = metrics.systemTotalMemory
                    batteryLevel = metrics.batteryLevel
                    isCharging = metrics.isCharging
                    storageAvailable = metrics.storageAvailable
                    storageTotal = metrics.storageTotal
                    networkType = metrics.networkType
                }
                delay(REFRESH_INTERVAL_MS)
            }
        }
        onDispose { job.cancel() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_device_metrics_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics { contentDescription = backDescription },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isMonitoring = !isMonitoring
                        },
                        modifier = Modifier.semantics {
                            contentDescription = if (isMonitoring) "Stop monitoring" else "Start monitoring"
                        },
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint =
                                if (isMonitoring) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("device_metrics_screen"),
            contentPadding = PaddingValues(Dimensions.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            // JVM Memory Section
            item {
                Text(
                    text = stringResource(R.string.debug_metrics_jvm_memory),
                    style = IOSTypography.headline,
                    modifier = Modifier.padding(top = Dimensions.SpacingMedium),
                )
            }

            item {
                val memoryUsagePercent =
                    if (jvmMaxMemory > 0) {
                        jvmUsedMemory.toFloat() / jvmMaxMemory.toFloat()
                    } else {
                        0f
                    }

                MetricCard(
                    icon = Icons.Default.Memory,
                    title = stringResource(R.string.debug_metrics_heap_usage),
                    value = stringResource(
                        R.string.debug_metrics_memory_of,
                        Formatter.formatFileSize(context, jvmUsedMemory),
                        Formatter.formatFileSize(context, jvmMaxMemory),
                    ),
                    progress = memoryUsagePercent,
                    progressColor = getStatusColor(memoryUsagePercent),
                    accessibilityDescription = stringResource(
                        R.string.cd_debug_memory_usage,
                        Formatter.formatFileSize(context, jvmUsedMemory),
                        Formatter.formatFileSize(context, jvmMaxMemory),
                    ),
                )
            }

            // System Memory Section
            item {
                Text(
                    text = stringResource(R.string.debug_metrics_system_memory),
                    style = IOSTypography.headline,
                    modifier = Modifier.padding(top = Dimensions.SpacingMedium),
                )
            }

            item {
                val systemUsed = systemTotalMemory - systemAvailableMemory
                val systemUsagePercent =
                    if (systemTotalMemory > 0) {
                        systemUsed.toFloat() / systemTotalMemory.toFloat()
                    } else {
                        0f
                    }

                MetricCard(
                    icon = Icons.Default.Memory,
                    title = stringResource(R.string.debug_metrics_system_ram),
                    value = stringResource(
                        R.string.debug_metrics_memory_of,
                        Formatter.formatFileSize(context, systemUsed),
                        Formatter.formatFileSize(context, systemTotalMemory),
                    ),
                    progress = systemUsagePercent,
                    progressColor = getStatusColor(systemUsagePercent),
                    accessibilityDescription = stringResource(
                        R.string.cd_debug_system_memory,
                        Formatter.formatFileSize(context, systemAvailableMemory),
                    ),
                )
            }

            // Battery Section
            item {
                Text(
                    text = stringResource(R.string.debug_metrics_battery),
                    style = IOSTypography.headline,
                    modifier = Modifier.padding(top = Dimensions.SpacingMedium),
                )
            }

            item {
                val batteryPercent = if (batteryLevel >= 0) batteryLevel / 100f else 0f
                val batteryText =
                    if (batteryLevel >= 0) {
                        if (isCharging) {
                            stringResource(R.string.debug_metrics_battery_charging, batteryLevel)
                        } else {
                            stringResource(R.string.debug_metrics_battery_level, batteryLevel)
                        }
                    } else {
                        stringResource(R.string.debug_metrics_battery_unknown)
                    }

                MetricCard(
                    icon = Icons.Default.Battery4Bar,
                    title = stringResource(R.string.debug_metrics_battery_status),
                    value = batteryText,
                    progress = batteryPercent,
                    progressColor = getBatteryColor(batteryLevel),
                    accessibilityDescription = batteryText,
                )
            }

            // Storage Section
            item {
                Text(
                    text = stringResource(R.string.debug_metrics_storage),
                    style = IOSTypography.headline,
                    modifier = Modifier.padding(top = Dimensions.SpacingMedium),
                )
            }

            item {
                val storageUsed = storageTotal - storageAvailable
                val storagePercent =
                    if (storageTotal > 0) {
                        storageUsed.toFloat() / storageTotal.toFloat()
                    } else {
                        0f
                    }

                MetricCard(
                    icon = Icons.Default.SdStorage,
                    title = stringResource(R.string.debug_metrics_internal_storage),
                    value = stringResource(
                        R.string.debug_metrics_storage_available,
                        Formatter.formatFileSize(context, storageAvailable),
                        Formatter.formatFileSize(context, storageTotal),
                    ),
                    progress = storagePercent,
                    progressColor = getStatusColor(storagePercent),
                    accessibilityDescription = stringResource(
                        R.string.cd_debug_storage,
                        Formatter.formatFileSize(context, storageAvailable),
                    ),
                )
            }

            // Network Section
            item {
                Text(
                    text = stringResource(R.string.debug_metrics_network),
                    style = IOSTypography.headline,
                    modifier = Modifier.padding(top = Dimensions.SpacingMedium),
                )
            }

            item {
                IOSCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.debug_metrics_network_type),
                                style = IOSTypography.body,
                            )
                            Text(
                                text = networkType,
                                style = IOSTypography.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Device Info Section
            item {
                Text(
                    text = stringResource(R.string.debug_device_info),
                    style = IOSTypography.headline,
                    modifier = Modifier.padding(top = Dimensions.SpacingMedium),
                )
            }

            item {
                IOSCard {
                    DeviceInfoRow(
                        label = stringResource(R.string.debug_device_model),
                        value = "${Build.MANUFACTURER} ${Build.MODEL}",
                    )
                    Spacer(modifier = Modifier.height(Dimensions.SpacingSmall))
                    DeviceInfoRow(
                        label = stringResource(R.string.debug_android_version),
                        value = "Android ${Build.VERSION.RELEASE}",
                    )
                    Spacer(modifier = Modifier.height(Dimensions.SpacingSmall))
                    DeviceInfoRow(
                        label = stringResource(R.string.debug_sdk_version),
                        value = "API ${Build.VERSION.SDK_INT}",
                    )
                }
            }

            // Bottom spacer
            item {
                Spacer(modifier = Modifier.height(Dimensions.SpacingXLarge))
            }
        }
    }
}

/**
 * Card displaying a single metric with icon, progress bar, and value.
 *
 * @param icon Icon representing the metric type
 * @param title Title label for the metric
 * @param value Formatted value text
 * @param progress Progress value between 0f and 1f
 * @param progressColor Color for the progress indicator
 * @param accessibilityDescription Content description for accessibility
 */
@Composable
private fun MetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    progress: Float,
    progressColor: Color,
    accessibilityDescription: String,
) {
    IOSCard(
        modifier = Modifier.semantics { contentDescription = accessibilityDescription },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = progressColor,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = IOSTypography.body,
                )
                Spacer(modifier = Modifier.height(Dimensions.SpacingXSmall))
                LinearProgressIndicator(
                    progress = { safeProgress(progress) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(Dimensions.ProgressBarHeight)
                            .clip(
                                androidx.compose.foundation.shape.RoundedCornerShape(
                                    Dimensions.ProgressBarCornerRadius,
                                ),
                            ),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )
                Spacer(modifier = Modifier.height(Dimensions.SpacingXSmall))
                Text(
                    text = value,
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Row displaying a label-value pair for device information.
 */
@Composable
private fun DeviceInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = IOSTypography.body,
        )
        Text(
            text = value,
            style = IOSTypography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Get status color based on usage percentage.
 *
 * Uses iOS traffic-light pattern: green (good), orange (warning), red (critical).
 */
private fun getStatusColor(usagePercent: Float): Color {
    return when {
        usagePercent > 0.85f -> iOSRed
        usagePercent > 0.70f -> iOSOrange
        else -> iOSGreen
    }
}

/**
 * Get battery status color based on battery level.
 */
private fun getBatteryColor(batteryLevel: Int): Color {
    return when {
        batteryLevel < 0 -> Color.Gray
        batteryLevel <= 20 -> iOSRed
        batteryLevel <= 50 -> iOSOrange
        else -> iOSGreen
    }
}

/** Refresh interval for metrics collection in milliseconds. */
private const val REFRESH_INTERVAL_MS = 1000L

/**
 * Data class holding all collected device metrics.
 */
private data class DeviceMetricsSnapshot(
    val jvmUsedMemory: Long,
    val jvmTotalMemory: Long,
    val jvmMaxMemory: Long,
    val systemAvailableMemory: Long,
    val systemTotalMemory: Long,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val storageAvailable: Long,
    val storageTotal: Long,
    val networkType: String,
)

/**
 * Collects device metrics from system APIs.
 *
 * Must be called from a background thread (IO dispatcher).
 */
@Suppress("DEPRECATION")
private fun collectDeviceMetrics(context: Context): DeviceMetricsSnapshot {
    // JVM memory
    val runtime = Runtime.getRuntime()
    val jvmUsed = runtime.totalMemory() - runtime.freeMemory()
    val jvmTotal = runtime.totalMemory()
    val jvmMax = runtime.maxMemory()

    // System memory
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)

    // Battery
    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val batteryPct = if (scale > 0) (level * 100) / scale else -1
    val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging =
        status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

    // Storage
    val stat = StatFs(Environment.getDataDirectory().path)
    val storageAvail = stat.availableBlocksLong * stat.blockSizeLong
    val storageTot = stat.totalBlocksLong * stat.blockSizeLong

    // Network
    val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connManager.activeNetwork
    val capabilities = network?.let { connManager.getNetworkCapabilities(it) }
    val netType = when {
        capabilities == null -> context.getString(R.string.debug_metrics_network_none)
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
            context.getString(R.string.debug_metrics_network_wifi)
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
            context.getString(R.string.debug_metrics_network_cellular)
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
            context.getString(R.string.debug_metrics_network_ethernet)
        else -> context.getString(R.string.debug_metrics_network_other)
    }

    return DeviceMetricsSnapshot(
        jvmUsedMemory = jvmUsed,
        jvmTotalMemory = jvmTotal,
        jvmMaxMemory = jvmMax,
        systemAvailableMemory = memInfo.availMem,
        systemTotalMemory = memInfo.totalMem,
        batteryLevel = batteryPct,
        isCharging = charging,
        storageAvailable = storageAvail,
        storageTotal = storageTot,
        networkType = netType,
    )
}
