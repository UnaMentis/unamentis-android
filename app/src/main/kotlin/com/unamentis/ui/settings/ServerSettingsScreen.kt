package com.unamentis.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unamentis.R
import com.unamentis.core.config.ServerConfig
import com.unamentis.core.config.ServerHealthStatus
import com.unamentis.core.config.ServerType
import com.unamentis.ui.components.IOSCard
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import com.unamentis.ui.theme.iOSGreen
import com.unamentis.ui.theme.iOSOrange
import com.unamentis.ui.theme.iOSRed

/**
 * Server Settings screen for configuring self-hosted servers.
 *
 * Features:
 * - View configured servers with health status
 * - Add/edit/remove server configurations
 * - Toggle servers enabled/disabled
 * - Test server connections
 * - Auto-discover servers on local network
 * - Quick add default localhost server
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ServerSettingsViewModel = hiltViewModel(),
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val isDiscovering by viewModel.isDiscovering.collectAsStateWithLifecycle()
    val discoveredServers by viewModel.discoveredServers.collectAsStateWithLifecycle()
    val showAddServerDialog by viewModel.showAddServerDialog.collectAsStateWithLifecycle()
    val showDiscoveryResultsDialog by viewModel.showDiscoveryResultsDialog.collectAsStateWithLifecycle()
    val testingServerId by viewModel.testingServerId.collectAsStateWithLifecycle()

    var serverToEdit by remember { mutableStateOf<ServerConfig?>(null) }
    var serverToDelete by remember { mutableStateOf<ServerConfig?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.server_settings_title),
                        style = IOSTypography.headline,
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshServers() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.server_settings_refresh),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .testTag("ServerSettingsLazyColumn"),
            contentPadding =
                PaddingValues(
                    horizontal = Dimensions.ScreenHorizontalPadding,
                    vertical = Dimensions.SpacingLarge,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingLarge),
        ) {
            // Status Section
            item {
                StatusSection(
                    overallStatus = viewModel.getOverallStatus(),
                    statusMessage = viewModel.getStatusMessage(),
                )
            }

            // Servers Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.server_settings_servers).uppercase(),
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { viewModel.showAddServerDialog() }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.server_settings_add))
                    }
                }
            }

            // Server List
            if (servers.isEmpty()) {
                item {
                    IOSCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.server_settings_no_servers),
                            style = IOSTypography.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Dimensions.SpacingMedium),
                        )
                    }
                }
            } else {
                items(servers, key = { it.id }) { server ->
                    ServerRow(
                        server = server,
                        isTesting = testingServerId == server.id,
                        onToggle = { viewModel.toggleServer(server.id) },
                        onTest = { viewModel.testServer(server.id) },
                        onEdit = { serverToEdit = server },
                        onDelete = { serverToDelete = server },
                    )
                }
            }

            // Footer text
            item {
                Text(
                    text = stringResource(R.string.server_settings_servers_footer),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Quick Setup Section
            item {
                Text(
                    text = stringResource(R.string.server_settings_quick_setup).uppercase(),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimensions.SpacingLarge),
                )
            }

            item {
                QuickSetupSection(
                    isDiscovering = isDiscovering,
                    onDiscover = { viewModel.discoverServers() },
                    onAddDefault = { viewModel.addDefaultServer() },
                )
            }

            // Quick setup footer
            item {
                Text(
                    text = stringResource(R.string.server_settings_quick_setup_footer),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // Add Server Dialog
    if (showAddServerDialog) {
        AddServerDialog(
            onDismiss = { viewModel.hideAddServerDialog() },
            onAdd = { viewModel.addServer(it) },
        )
    }

    // Edit Server Dialog
    serverToEdit?.let { server ->
        EditServerDialog(
            server = server,
            onDismiss = { serverToEdit = null },
            onSave = { updatedServer ->
                viewModel.updateServer(updatedServer)
                serverToEdit = null
            },
        )
    }

    // Delete Confirmation Dialog
    serverToDelete?.let { server ->
        DeleteServerDialog(
            serverName = server.name,
            onDismiss = { serverToDelete = null },
            onConfirm = {
                viewModel.removeServer(server.id)
                serverToDelete = null
            },
        )
    }

    // Discovery Results Dialog
    if (showDiscoveryResultsDialog && discoveredServers.isNotEmpty()) {
        DiscoveryResultsDialog(
            discoveredCount = discoveredServers.size,
            onDismiss = { viewModel.hideDiscoveryResultsDialog() },
            onAddAll = { viewModel.addDiscoveredServers() },
        )
    }
}

/**
 * Status section showing overall server health.
 */
@Composable
private fun StatusSection(
    overallStatus: ServerHealthStatus,
    statusMessage: String,
) {
    IOSCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.server_settings_self_hosted_mode),
                    style = IOSTypography.subheadline,
                )
                Text(
                    text = statusMessage,
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HealthStatusIndicator(
                status = overallStatus,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/**
 * Health status indicator (colored circle).
 */
@Composable
private fun HealthStatusIndicator(
    status: ServerHealthStatus,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue =
            when (status) {
                ServerHealthStatus.HEALTHY -> iOSGreen
                ServerHealthStatus.DEGRADED -> iOSOrange
                ServerHealthStatus.UNHEALTHY -> iOSRed
                ServerHealthStatus.CHECKING -> iOSOrange
                ServerHealthStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        label = "health_status_color",
    )

    val statusDescription =
        stringResource(
            when (status) {
                ServerHealthStatus.HEALTHY -> R.string.server_settings_status_healthy
                ServerHealthStatus.DEGRADED -> R.string.server_settings_status_degraded
                ServerHealthStatus.UNHEALTHY -> R.string.server_settings_status_unhealthy
                ServerHealthStatus.CHECKING -> R.string.server_settings_status_checking
                ServerHealthStatus.UNKNOWN -> R.string.server_settings_status_unknown
            },
        )

    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(color)
                .semantics { contentDescription = statusDescription },
    )
}

/**
 * Individual server row with actions.
 */
@Composable
private fun ServerRow(
    server: ServerConfig,
    isTesting: Boolean,
    onToggle: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val toggleStateDesc =
        if (server.enabled) {
            stringResource(R.string.cd_toggle_on, server.name)
        } else {
            stringResource(R.string.cd_toggle_off, server.name)
        }

    IOSCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Health status indicator
            HealthStatusIndicator(
                status = server.healthStatus,
                modifier = Modifier.size(10.dp),
            )

            // Server info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    style = IOSTypography.body,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
                ) {
                    Text(
                        text = "${server.host}:${server.port}",
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "•",
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = server.type.displayName(),
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Toggle enabled
            Switch(
                checked = server.enabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.semantics { contentDescription = toggleStateDesc },
            )

            // Test connection button
            IconButton(onClick = onTest, enabled = !isTesting) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.server_settings_test_connection),
                    )
                }
            }

            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.server_settings_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Quick setup section with discovery and default server options.
 */
@Composable
private fun QuickSetupSection(
    isDiscovering: Boolean,
    onDiscover: () -> Unit,
    onAddDefault: () -> Unit,
) {
    IOSCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            // Discover servers button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.server_settings_discover),
                        style = IOSTypography.body,
                    )
                }

                if (isDiscovering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    TextButton(onClick = onDiscover) {
                        Text(stringResource(R.string.server_settings_scan))
                    }
                }
            }

            HorizontalDivider()

            // Add default server button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Computer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.server_settings_add_local_mac),
                        style = IOSTypography.body,
                    )
                }

                TextButton(onClick = onAddDefault) {
                    Text(stringResource(R.string.server_settings_add))
                }
            }
        }
    }
}

/**
 * Dialog for adding a new server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddServerDialog(
    onDismiss: () -> Unit,
    onAdd: (ServerConfig) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ServerType.UNAMENTIS_GATEWAY) }
    var expanded by remember { mutableStateOf(false) }

    // Update port when type changes
    fun updatePortForType(type: ServerType) {
        port = type.defaultPort().toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.server_settings_add_server)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.server_settings_name)) },
                    placeholder = { Text(stringResource(R.string.server_settings_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.server_settings_host)) },
                    placeholder = { Text(stringResource(R.string.server_settings_host_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.server_settings_port)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Server type dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = selectedType.displayName(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.server_settings_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier =
                            Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        ServerType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName()) },
                                onClick = {
                                    selectedType = type
                                    updatePortForType(type)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val config =
                        ServerConfig(
                            name =
                                name.ifBlank {
                                    "${selectedType.displayName()} ($host)"
                                },
                            host = host,
                            port = port.toIntOrNull() ?: selectedType.defaultPort(),
                            type = selectedType,
                        )
                    onAdd(config)
                },
                enabled = host.isNotBlank() && port.isNotBlank(),
            ) {
                Text(stringResource(R.string.server_settings_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * Dialog for editing an existing server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditServerDialog(
    server: ServerConfig,
    onDismiss: () -> Unit,
    onSave: (ServerConfig) -> Unit,
) {
    var name by remember { mutableStateOf(server.name) }
    var host by remember { mutableStateOf(server.host) }
    var port by remember { mutableStateOf(server.port.toString()) }
    var selectedType by remember { mutableStateOf(server.type) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.server_settings_edit_server)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.server_settings_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.server_settings_host)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.server_settings_port)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = selectedType.displayName(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.server_settings_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier =
                            Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        ServerType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName()) },
                                onClick = {
                                    selectedType = type
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedConfig =
                        server.copy(
                            name = name.ifBlank { "${selectedType.displayName()} ($host)" },
                            host = host,
                            port = port.toIntOrNull() ?: selectedType.defaultPort(),
                            type = selectedType,
                        )
                    onSave(updatedConfig)
                },
                enabled = host.isNotBlank() && port.isNotBlank(),
            ) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * Dialog confirming server deletion.
 */
@Composable
private fun DeleteServerDialog(
    serverName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.server_settings_delete_server_title)) },
        text = {
            Text(stringResource(R.string.server_settings_delete_server_message, serverName))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * Dialog showing discovery results.
 */
@Composable
private fun DiscoveryResultsDialog(
    discoveredCount: Int,
    onDismiss: () -> Unit,
    onAddAll: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.server_settings_discovered_servers)) },
        text = {
            Text(stringResource(R.string.server_settings_discovered_message, discoveredCount))
        },
        confirmButton = {
            TextButton(onClick = onAddAll) {
                Text(stringResource(R.string.server_settings_add_all))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
