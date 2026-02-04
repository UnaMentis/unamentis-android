package com.unamentis.ui.settings

import android.os.Build
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.unamentis.BuildConfig
import com.unamentis.R
import com.unamentis.ui.components.IOSCard
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import kotlinx.coroutines.launch
import java.io.File

/**
 * Debug tools screen for diagnostics and troubleshooting.
 *
 * This screen is only accessible in debug builds.
 *
 * Features:
 * - Device information
 * - Cache management
 * - Provider connectivity tests
 * - Memory usage
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val backDescription = stringResource(R.string.cd_go_back)

    // Cache state
    var cacheSize by remember { mutableStateOf(getCacheSize(context)) }
    var isClearing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_debug_tools)) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics { contentDescription = backDescription },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("debug_screen"),
            contentPadding = PaddingValues(Dimensions.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            // Device Information Section
            item {
                Text(
                    text = stringResource(R.string.debug_device_info),
                    style = IOSTypography.headline,
                    modifier = Modifier.padding(top = Dimensions.SpacingMedium),
                )
            }

            item {
                IOSCard {
                    Column(modifier = Modifier.padding(Dimensions.CardPadding)) {
                        DebugInfoRow(
                            label = stringResource(R.string.debug_device_model),
                            value = "${Build.MANUFACTURER} ${Build.MODEL}",
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = Dimensions.SpacingSmall),
                        )
                        DebugInfoRow(
                            label = stringResource(R.string.debug_android_version),
                            value = "Android ${Build.VERSION.RELEASE}",
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = Dimensions.SpacingSmall),
                        )
                        DebugInfoRow(
                            label = stringResource(R.string.debug_sdk_version),
                            value = "API ${Build.VERSION.SDK_INT}",
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = Dimensions.SpacingSmall),
                        )
                        DebugInfoRow(
                            label = stringResource(R.string.debug_app_version),
                            value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = Dimensions.SpacingSmall),
                        )
                        DebugInfoRow(
                            label = stringResource(R.string.debug_available_memory),
                            value = getAvailableMemory(context),
                        )
                    }
                }
            }

            // Cache Management Section
            item {
                Text(
                    text = stringResource(R.string.debug_cache_management),
                    style = IOSTypography.headline,
                    modifier = Modifier.padding(top = Dimensions.SpacingMedium),
                )
            }

            item {
                IOSCard {
                    Column(modifier = Modifier.padding(Dimensions.CardPadding)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column(modifier = Modifier.padding(start = Dimensions.SpacingMedium)) {
                                    Text(
                                        text = stringResource(R.string.debug_cache_size),
                                        style = IOSTypography.body,
                                    )
                                    Text(
                                        text = cacheSize,
                                        style = IOSTypography.caption,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        isClearing = true
                                        clearCache(context)
                                        cacheSize = getCacheSize(context)
                                        isClearing = false
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.debug_cache_cleared),
                                        )
                                    }
                                },
                                enabled = !isClearing,
                            ) {
                                if (isClearing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text(stringResource(R.string.debug_clear_cache))
                                }
                            }
                        }
                    }
                }
            }

            // Build Information Section
            item {
                Text(
                    text = "Build Information",
                    style = IOSTypography.headline,
                    modifier = Modifier.padding(top = Dimensions.SpacingMedium),
                )
            }

            item {
                IOSCard {
                    Column(modifier = Modifier.padding(Dimensions.CardPadding)) {
                        DebugInfoRow(
                            label = "Build Type",
                            value = BuildConfig.BUILD_TYPE,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = Dimensions.SpacingSmall),
                        )
                        DebugInfoRow(
                            label = "Application ID",
                            value = BuildConfig.APPLICATION_ID,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = Dimensions.SpacingSmall),
                        )
                        DebugInfoRow(
                            label = "Debug Mode",
                            value = if (BuildConfig.DEBUG) "Enabled" else "Disabled",
                        )
                    }
                }
            }

            // Spacer at bottom
            item {
                Spacer(modifier = Modifier.height(Dimensions.SpacingXLarge))
            }
        }
    }
}

/**
 * Row displaying a label-value pair for debug info.
 */
@Composable
private fun DebugInfoRow(
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
 * Get the total size of the app cache.
 */
private fun getCacheSize(context: android.content.Context): String {
    val cacheDir = context.cacheDir
    val externalCacheDir = context.externalCacheDir

    var totalSize = getDirSize(cacheDir)
    if (externalCacheDir != null) {
        totalSize += getDirSize(externalCacheDir)
    }

    return Formatter.formatFileSize(context, totalSize)
}

/**
 * Get size of a directory recursively.
 */
private fun getDirSize(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0L

    var size = 0L
    dir.listFiles()?.forEach { file ->
        size +=
            if (file.isDirectory) {
                getDirSize(file)
            } else {
                file.length()
            }
    }
    return size
}

/**
 * Clear the app cache.
 */
private fun clearCache(context: android.content.Context) {
    context.cacheDir.deleteRecursively()
    context.externalCacheDir?.deleteRecursively()
}

/**
 * Get available memory information.
 */
private fun getAvailableMemory(context: android.content.Context): String {
    val activityManager =
        context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val memInfo = android.app.ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)
    return Formatter.formatFileSize(context, memInfo.availMem)
}
