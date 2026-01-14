package com.unamentis.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unamentis.core.config.ConfigurationPreset

/**
 * Settings screen - App configuration.
 *
 * Features:
 * - Configuration presets (BALANCED, LOW_LATENCY, etc.)
 * - Provider selection (STT, TTS, LLM)
 * - API key management
 * - Cost preference settings
 *
 * Layout:
 * - Preset cards at top
 * - Provider selection sections
 * - API key input fields (secure)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Preset selection
            item {
                PresetSection(
                    currentPreset = uiState.currentPreset,
                    onPresetSelected = { viewModel.applyPreset(it) },
                )
            }

            // Provider sections
            item {
                Text(
                    text = "Providers",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // STT Provider
            item {
                ProviderCard(
                    title = "Speech-to-Text",
                    providers = listOf("Deepgram", "Android"),
                    selectedProvider = uiState.selectedSTTProvider,
                    onProviderSelected = { viewModel.setSTTProvider(it) },
                    icon = Icons.Default.Mic,
                )
            }

            // TTS Provider
            item {
                ProviderCard(
                    title = "Text-to-Speech",
                    providers = listOf("ElevenLabs", "Android"),
                    selectedProvider = uiState.selectedTTSProvider,
                    onProviderSelected = { viewModel.setTTSProvider(it) },
                    icon = Icons.Default.VolumeUp,
                )
            }

            // LLM Provider
            item {
                ProviderCard(
                    title = "Language Model",
                    providers = listOf("PatchPanel", "OpenAI", "Anthropic"),
                    selectedProvider = uiState.selectedLLMProvider,
                    onProviderSelected = { viewModel.setLLMProvider(it) },
                    icon = Icons.Default.Psychology,
                )
            }

            // API Keys section
            item {
                Text(
                    text = "API Keys",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            item {
                ApiKeySection(
                    viewModel = viewModel,
                    uiState = uiState,
                )
            }
        }
    }
}

/**
 * Preset selection cards.
 */
@Composable
private fun PresetSection(
    currentPreset: ConfigurationPreset,
    onPresetSelected: (ConfigurationPreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Configuration Presets",
            style = MaterialTheme.typography.titleMedium,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PresetChip(
                preset = ConfigurationPreset.FREE,
                isSelected = currentPreset == ConfigurationPreset.FREE,
                onClick = { onPresetSelected(ConfigurationPreset.FREE) },
                modifier = Modifier.weight(1f),
            )
            PresetChip(
                preset = ConfigurationPreset.PREMIUM,
                isSelected = currentPreset == ConfigurationPreset.PREMIUM,
                onClick = { onPresetSelected(ConfigurationPreset.PREMIUM) },
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PresetChip(
                preset = ConfigurationPreset.COST_OPTIMIZED,
                isSelected = currentPreset == ConfigurationPreset.COST_OPTIMIZED,
                onClick = { onPresetSelected(ConfigurationPreset.COST_OPTIMIZED) },
                modifier = Modifier.weight(1f),
            )
            PresetChip(
                preset = ConfigurationPreset.OFFLINE,
                isSelected = currentPreset == ConfigurationPreset.OFFLINE,
                onClick = { onPresetSelected(ConfigurationPreset.OFFLINE) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Individual preset chip.
 */
@Composable
private fun PresetChip(
    preset: ConfigurationPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = preset.name.replace("_", " "),
                style = MaterialTheme.typography.labelMedium,
            )
        },
        modifier = modifier,
    )
}

/**
 * Provider selection card.
 */
@Composable
private fun ProviderCard(
    title: String,
    providers: List<String>,
    selectedProvider: String,
    onProviderSelected: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                providers.forEach { provider ->
                    FilterChip(
                        selected = selectedProvider == provider,
                        onClick = { onProviderSelected(provider) },
                        label = { Text(provider) },
                    )
                }
            }
        }
    }
}

/**
 * API key input section.
 */
@Composable
private fun ApiKeySection(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
) {
    var showDeepgramDialog by remember { mutableStateOf(false) }
    var showElevenLabsDialog by remember { mutableStateOf(false) }
    var showOpenAIDialog by remember { mutableStateOf(false) }
    var showAnthropicDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ApiKeyItem(
                name = "Deepgram",
                hasKey = uiState.hasDeepgramKey,
                onEdit = { showDeepgramDialog = true },
            )
            ApiKeyItem(
                name = "ElevenLabs",
                hasKey = uiState.hasElevenLabsKey,
                onEdit = { showElevenLabsDialog = true },
            )
            ApiKeyItem(
                name = "OpenAI",
                hasKey = uiState.hasOpenAIKey,
                onEdit = { showOpenAIDialog = true },
            )
            ApiKeyItem(
                name = "Anthropic",
                hasKey = uiState.hasAnthropicKey,
                onEdit = { showAnthropicDialog = true },
            )
        }
    }

    // API Key dialogs
    if (showDeepgramDialog) {
        ApiKeyDialog(
            title = "Deepgram API Key",
            currentKey = viewModel.getDeepgramApiKey(),
            onDismiss = { showDeepgramDialog = false },
            onSave = { viewModel.updateDeepgramApiKey(it) },
        )
    }
    if (showElevenLabsDialog) {
        ApiKeyDialog(
            title = "ElevenLabs API Key",
            currentKey = viewModel.getElevenLabsApiKey(),
            onDismiss = { showElevenLabsDialog = false },
            onSave = { viewModel.updateElevenLabsApiKey(it) },
        )
    }
    if (showOpenAIDialog) {
        ApiKeyDialog(
            title = "OpenAI API Key",
            currentKey = viewModel.getOpenAIApiKey(),
            onDismiss = { showOpenAIDialog = false },
            onSave = { viewModel.updateOpenAIApiKey(it) },
        )
    }
    if (showAnthropicDialog) {
        ApiKeyDialog(
            title = "Anthropic API Key",
            currentKey = viewModel.getAnthropicApiKey(),
            onDismiss = { showAnthropicDialog = false },
            onSave = { viewModel.updateAnthropicApiKey(it) },
        )
    }
}

/**
 * Individual API key list item.
 */
@Composable
private fun ApiKeyItem(
    name: String,
    hasKey: Boolean,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (hasKey) "Configured" else "Not configured",
                style = MaterialTheme.typography.bodySmall,
                color = if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }

        TextButton(onClick = onEdit) {
            Text(if (hasKey) "Edit" else "Add")
        }
    }
}

/**
 * API key input dialog.
 */
@Composable
private fun ApiKeyDialog(
    title: String,
    currentKey: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var keyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentKey != null) {
                    Text(
                        text = "Current: $currentKey",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("API Key") },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showKey) "Hide" else "Show",
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (keyInput.isNotBlank()) {
                        onSave(keyInput)
                    }
                    onDismiss()
                },
                enabled = keyInput.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
