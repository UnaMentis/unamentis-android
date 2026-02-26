package com.unamentis.ui.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unamentis.R
import com.unamentis.core.module.DownloadedModule
import com.unamentis.core.module.ModuleProtocol
import com.unamentis.core.module.ModuleSummary
import com.unamentis.ui.components.IOSCard
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography

/**
 * Modules browser screen showing bundled, downloaded, and server-available modules.
 *
 * Matches iOS ModulesView structure with sectioned list layout.
 *
 * @param onNavigateToModule Callback when a module is selected to launch
 * @param viewModel Modules ViewModel
 */
@Composable
fun ModulesScreen(
    onNavigateToModule: (String) -> Unit = {},
    viewModel: LearningViewModel = hiltViewModel(),
) {
    val downloadedModules by viewModel.downloadedModules.collectAsStateWithLifecycle()
    val availableModules by viewModel.availableModules.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val serverError by viewModel.serverError.collectAsStateWithLifecycle()

    val bundledModules = viewModel.bundledModules

    // Filter server modules that aren't already bundled
    val bundledIds = bundledModules.map { it.moduleId }.toSet()
    val filteredServerModules = availableModules.filter { it.id !in bundledIds }

    // Delete confirmation state
    var moduleToDelete by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("modules_list"),
        verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        contentPadding =
            androidx.compose.foundation.layout.PaddingValues(
                horizontal = Dimensions.ScreenHorizontalPadding,
                vertical = Dimensions.SpacingLarge,
            ),
    ) {
        // Section header
        item {
            Column(
                modifier = Modifier.padding(bottom = Dimensions.SpacingSmall),
            ) {
                Text(
                    text = stringResource(R.string.modules_section_title),
                    style = IOSTypography.headline,
                )
                Text(
                    text = stringResource(R.string.modules_section_subtitle),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Installed (bundled) modules section
        if (bundledModules.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.modules_section_installed).uppercase(),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimensions.SpacingMedium),
                )
            }

            items(
                items = bundledModules,
                key = { it.moduleId },
            ) { module ->
                BundledModuleRow(
                    module = module,
                    onClick = { onNavigateToModule(module.moduleId) },
                )
            }
        }

        // Downloaded modules section
        if (downloadedModules.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.modules_section_downloaded).uppercase(),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimensions.SpacingMedium),
                )
            }

            items(
                items = downloadedModules,
                key = { it.id },
            ) { module ->
                DownloadedModuleRow(
                    module = module,
                    onClick = { onNavigateToModule(module.id) },
                    onDelete = { moduleToDelete = module.id },
                )
            }
        }

        // Server-available modules section
        if (filteredServerModules.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.modules_section_available).uppercase(),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimensions.SpacingMedium),
                )
            }

            items(
                items = filteredServerModules,
                key = { it.id },
            ) { module ->
                ServerModuleRow(
                    module = module,
                    isDownloaded = viewModel.isModuleDownloaded(module.id),
                    onDownload = { viewModel.downloadModule(module.id) },
                    onClick = {
                        if (viewModel.isModuleDownloaded(module.id)) {
                            onNavigateToModule(module.id)
                        }
                    },
                )
            }
        }

        // Server connection status
        item {
            ServerStatusRow(
                isLoading = isLoading,
                error = serverError,
                onRefresh = { viewModel.refreshModules() },
            )
        }

        // Empty state
        val hasNoModules =
            bundledModules.isEmpty() && downloadedModules.isEmpty() &&
                filteredServerModules.isEmpty()
        if (hasNoModules && !isLoading) {
            item {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimensions.SpacingXLarge),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.modules_empty_state),
                        style = IOSTypography.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    moduleToDelete?.let { moduleId ->
        AlertDialog(
            onDismissRequest = { moduleToDelete = null },
            title = { Text(stringResource(R.string.modules_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteModule(moduleId)
                    moduleToDelete = null
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { moduleToDelete = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

/**
 * Row for a bundled (always-available) module.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BundledModuleRow(
    module: ModuleProtocol,
    onClick: () -> Unit,
) {
    val moduleCd = stringResource(R.string.cd_module_card, module.moduleName)

    IOSCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = moduleCd },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Module icon
            ModuleIconBox(
                color = module.themeColor,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
            ) {
                Text(
                    text = module.moduleName,
                    style = IOSTypography.subheadline,
                )
                Text(
                    text = module.shortDescription,
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )

                // Feature badges
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
                ) {
                    if (module.supportsTeamMode) {
                        FeatureBadge(
                            label = stringResource(R.string.modules_feature_team),
                            icon = Icons.Default.Groups,
                        )
                    }
                    if (module.supportsSpeedTraining) {
                        FeatureBadge(
                            label = stringResource(R.string.modules_feature_speed),
                            icon = Icons.Default.Speed,
                        )
                    }
                    if (module.supportsCompetitionSim) {
                        FeatureBadge(
                            label = stringResource(R.string.modules_feature_competition),
                            icon = Icons.Default.EmojiEvents,
                        )
                    }
                }
            }

            Icon(
                Icons.Default.PlayCircle,
                contentDescription = stringResource(R.string.modules_start_practicing),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Row for a downloaded server module.
 */
@Composable
private fun DownloadedModuleRow(
    module: DownloadedModule,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val moduleCd = stringResource(R.string.cd_module_card, module.name)

    IOSCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = moduleCd },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModuleIconBox(color = MaterialTheme.colorScheme.tertiary)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
            ) {
                Text(
                    text = module.name,
                    style = IOSTypography.subheadline,
                )
                Text(
                    text = stringResource(R.string.modules_ready_to_practice),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = stringResource(R.string.modules_start_practicing),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Row for a server-available module (may or may not be downloaded).
 */
@Composable
private fun ServerModuleRow(
    module: ModuleSummary,
    isDownloaded: Boolean,
    onDownload: () -> Unit,
    onClick: () -> Unit,
) {
    val moduleCd = stringResource(R.string.cd_module_card, module.name)

    IOSCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = moduleCd },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModuleIconBox(color = MaterialTheme.colorScheme.secondary)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
            ) {
                Text(
                    text = module.name,
                    style = IOSTypography.subheadline,
                )
                Text(
                    text = module.description,
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }

            if (isDownloaded) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.modules_ready_to_practice),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = stringResource(R.string.modules_download),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * Server connection status indicator.
 */
@Composable
private fun ServerStatusRow(
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Dimensions.SpacingMedium),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Text(
                    text = stringResource(R.string.modules_server_connecting),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Dimensions.SpacingSmall),
                )
            }
            error != null -> {
                Text(
                    text = stringResource(R.string.modules_server_error),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRefresh) {
                    Text(
                        text = stringResource(R.string.server_settings_refresh),
                        style = IOSTypography.caption,
                    )
                }
            }
        }
    }
}

/**
 * Colored icon box for module rows.
 */
@Composable
private fun ModuleIconBox(color: Color) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.School,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Small feature badge chip.
 */
@Composable
private fun FeatureBadge(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val badgeCd = stringResource(R.string.cd_module_feature_badge, label)

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.semantics { contentDescription = badgeCd },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = label,
                style = IOSTypography.caption,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
