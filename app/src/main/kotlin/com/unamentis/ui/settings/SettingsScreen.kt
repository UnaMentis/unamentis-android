package com.unamentis.ui.settings

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unamentis.core.config.ConfigurationPreset
import com.unamentis.core.config.RecordingMode
import com.unamentis.core.device.DeviceCapabilityDetector
import com.unamentis.services.llm.ModelDownloadManager
import com.unamentis.ui.components.IOSCard
import com.unamentis.ui.theme.Dimensions

/**
 * Settings sections that can be navigated to via deep link.
 */
enum class SettingsSection {
    PRESETS,
    PROVIDERS,
    RECORDING,
    AUDIO,
    VAD,
    LLM,
    ON_DEVICE_LLM,
    TTS,
    CURRICULUM,
    API_KEYS,
}

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
fun SettingsScreen(
    initialSection: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Collect advanced settings
    val sampleRate by viewModel.sampleRate.collectAsStateWithLifecycle()
    val enableVoiceProcessing by viewModel.enableVoiceProcessing.collectAsStateWithLifecycle()
    val enableEchoCancellation by viewModel.enableEchoCancellation.collectAsStateWithLifecycle()
    val enableNoiseSuppression by viewModel.enableNoiseSuppression.collectAsStateWithLifecycle()
    val vadThreshold by viewModel.vadThreshold.collectAsStateWithLifecycle()
    val bargeInThreshold by viewModel.bargeInThreshold.collectAsStateWithLifecycle()
    val enableBargeIn by viewModel.enableBargeIn.collectAsStateWithLifecycle()
    val silenceThresholdMs by viewModel.silenceThresholdMs.collectAsStateWithLifecycle()
    val llmTemperature by viewModel.llmTemperature.collectAsStateWithLifecycle()
    val llmMaxTokens by viewModel.llmMaxTokens.collectAsStateWithLifecycle()
    val ttsSpeakingRate by viewModel.ttsSpeakingRate.collectAsStateWithLifecycle()
    val ttsPlaybackSpeed by viewModel.ttsPlaybackSpeed.collectAsStateWithLifecycle()
    val autoContinueTopics by viewModel.autoContinueTopics.collectAsStateWithLifecycle()

    // On-Device LLM settings
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()

    // Handle deep link to specific section
    LaunchedEffect(initialSection) {
        if (initialSection != null) {
            val sectionIndex =
                when (initialSection.uppercase()) {
                    "PRESETS" -> 0
                    "PROVIDERS" -> 1
                    "RECORDING" -> 6
                    "AUDIO" -> 8
                    "VAD" -> 10
                    "LLM" -> 12
                    "ON_DEVICE_LLM" -> 14
                    "TTS" -> 16
                    "CURRICULUM" -> 18
                    "API_KEYS" -> 20
                    else -> null
                }
            sectionIndex?.let {
                listState.animateScrollToItem(it)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            contentPadding =
                PaddingValues(
                    horizontal = Dimensions.ScreenHorizontalPadding,
                    vertical = Dimensions.SpacingLarge,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingLarge),
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

            // Recording Mode section
            item {
                Text(
                    text = "Recording",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            item {
                RecordingModeSection(
                    currentMode = uiState.recordingMode,
                    onModeSelected = { viewModel.setRecordingMode(it) },
                )
            }

            // Advanced Audio Settings section
            item {
                Text(
                    text = "Audio Settings",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            item {
                AudioSettingsSection(
                    sampleRate = sampleRate,
                    onSampleRateChange = { viewModel.setSampleRate(it) },
                    enableVoiceProcessing = enableVoiceProcessing,
                    onVoiceProcessingChange = { viewModel.setEnableVoiceProcessing(it) },
                    enableEchoCancellation = enableEchoCancellation,
                    onEchoCancellationChange = { viewModel.setEnableEchoCancellation(it) },
                    enableNoiseSuppression = enableNoiseSuppression,
                    onNoiseSuppressionChange = { viewModel.setEnableNoiseSuppression(it) },
                )
            }

            // VAD Settings section
            item {
                Text(
                    text = "Voice Detection",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            item {
                VadSettingsSection(
                    vadThreshold = vadThreshold,
                    onVadThresholdChange = { viewModel.setVadThreshold(it) },
                    bargeInThreshold = bargeInThreshold,
                    onBargeInThresholdChange = { viewModel.setBargeInThreshold(it) },
                    enableBargeIn = enableBargeIn,
                    onEnableBargeInChange = { viewModel.setEnableBargeIn(it) },
                    silenceThresholdMs = silenceThresholdMs,
                    onSilenceThresholdChange = { viewModel.setSilenceThresholdMs(it) },
                )
            }

            // LLM Settings section
            item {
                Text(
                    text = "Language Model",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            item {
                LlmSettingsSection(
                    temperature = llmTemperature,
                    onTemperatureChange = { viewModel.setLlmTemperature(it) },
                    maxTokens = llmMaxTokens,
                    onMaxTokensChange = { viewModel.setLlmMaxTokens(it) },
                )
            }

            // On-Device LLM section
            item {
                Text(
                    text = "On-Device AI",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            item {
                OnDeviceLlmSection(
                    supportsOnDeviceLLM = viewModel.supportsOnDeviceLLM,
                    deviceRamMB = viewModel.deviceRamMB,
                    recommendedModel = viewModel.recommendedModel,
                    downloadState = downloadState,
                    availableModels = availableModels,
                    onDownloadRecommended = { viewModel.downloadRecommendedModel() },
                    onDownloadModel = { viewModel.downloadModel(it) },
                    onCancelDownload = { viewModel.cancelDownload() },
                    onDeleteModel = { viewModel.deleteModel(it) },
                )
            }

            // TTS Settings section
            item {
                Text(
                    text = "Voice Output",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            item {
                TtsSettingsSection(
                    speakingRate = ttsSpeakingRate,
                    onSpeakingRateChange = { viewModel.setTtsSpeakingRate(it) },
                    playbackSpeed = ttsPlaybackSpeed,
                    onPlaybackSpeedChange = { viewModel.setTtsPlaybackSpeed(it) },
                )
            }

            // Curriculum Playback section
            item {
                Text(
                    text = "Curriculum Playback",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            item {
                CurriculumSettingsSection(
                    autoContinueTopics = autoContinueTopics,
                    onAutoContinueChange = { viewModel.setAutoContinueTopics(it) },
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
 * Uses iOS-style card with 12dp corner radius.
 */
@Composable
private fun ProviderCard(
    title: String,
    providers: List<String>,
    selectedProvider: String,
    onProviderSelected: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    IOSCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
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

    IOSCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
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

/**
 * Recording mode selection section.
 */
@Composable
private fun RecordingModeSection(
    currentMode: RecordingMode,
    onModeSelected: (RecordingMode) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Recording Mode",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            RecordingMode.entries.forEach { mode ->
                RecordingModeOption(
                    mode = mode,
                    isSelected = currentMode == mode,
                    onSelected = { onModeSelected(mode) },
                )
            }
        }
    }
}

/**
 * Individual recording mode option with radio button.
 */
@Composable
private fun RecordingModeOption(
    mode: RecordingMode,
    isSelected: Boolean,
    onSelected: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelected,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mode.displayName,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = mode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Audio settings section with sample rate and processing options.
 */
@Composable
private fun AudioSettingsSection(
    sampleRate: Int,
    onSampleRateChange: (Int) -> Unit,
    enableVoiceProcessing: Boolean,
    onVoiceProcessingChange: (Boolean) -> Unit,
    enableEchoCancellation: Boolean,
    onEchoCancellationChange: (Boolean) -> Unit,
    enableNoiseSuppression: Boolean,
    onNoiseSuppressionChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Audio Quality",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            // Sample rate picker
            Text(
                text = "Sample Rate",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(16000, 24000, 48000).forEach { rate ->
                    FilterChip(
                        selected = sampleRate == rate,
                        onClick = { onSampleRateChange(rate) },
                        label = { Text("${rate / 1000} kHz") },
                    )
                }
            }
            Text(
                text = "Higher rates sound better but use more data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            // Toggle options
            SettingsToggle(
                title = "Voice Processing",
                description = "Enhances voice clarity",
                checked = enableVoiceProcessing,
                onCheckedChange = onVoiceProcessingChange,
            )

            SettingsToggle(
                title = "Echo Cancellation",
                description = "Prevents microphone from picking up AI's voice",
                checked = enableEchoCancellation,
                onCheckedChange = onEchoCancellationChange,
            )

            SettingsToggle(
                title = "Noise Suppression",
                description = "Filters background noise",
                checked = enableNoiseSuppression,
                onCheckedChange = onNoiseSuppressionChange,
            )
        }
    }
}

/**
 * VAD (Voice Activity Detection) settings section.
 */
@Composable
private fun VadSettingsSection(
    vadThreshold: Float,
    onVadThresholdChange: (Float) -> Unit,
    bargeInThreshold: Float,
    onBargeInThresholdChange: (Float) -> Unit,
    enableBargeIn: Boolean,
    onEnableBargeInChange: (Boolean) -> Unit,
    silenceThresholdMs: Int,
    onSilenceThresholdChange: (Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Voice Detection Tuning",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            // VAD Threshold slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Detection Threshold",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "%.2f".format(vadThreshold),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = vadThreshold,
                    onValueChange = onVadThresholdChange,
                    valueRange = 0.3f..0.9f,
                )
                Text(
                    text = "Lower values detect quieter speech but may pick up noise",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // Barge-in settings
            SettingsToggle(
                title = "Enable Interruptions",
                description = "Speaking while AI talks will pause it to listen",
                checked = enableBargeIn,
                onCheckedChange = onEnableBargeInChange,
            )

            if (enableBargeIn) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Interruption Threshold",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "%.2f".format(bargeInThreshold),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = bargeInThreshold,
                        onValueChange = onBargeInThresholdChange,
                        valueRange = 0.5f..0.95f,
                    )
                    Text(
                        text = "How loud you need to speak to interrupt the AI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            // Silence threshold
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Silence Timeout",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${silenceThresholdMs}ms",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = silenceThresholdMs.toFloat(),
                    onValueChange = { onSilenceThresholdChange(it.toInt()) },
                    valueRange = 500f..3000f,
                    steps = 4,
                )
                Text(
                    text = "How long to wait in silence before ending your turn",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * LLM settings section with temperature and max tokens.
 */
@Composable
private fun LlmSettingsSection(
    temperature: Float,
    onTemperatureChange: (Float) -> Unit,
    maxTokens: Int,
    onMaxTokensChange: (Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Response Tuning",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            // Temperature slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Temperature",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "%.1f".format(temperature),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = temperature,
                    onValueChange = onTemperatureChange,
                    valueRange = 0f..1f,
                )
                Text(
                    text = "Controls creativity. Lower for factual, higher for creative.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // Max tokens slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Max Response Length",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "$maxTokens tokens",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = maxTokens.toFloat(),
                    onValueChange = { onMaxTokensChange(it.toInt()) },
                    valueRange = 256f..4096f,
                    steps = 14,
                )
                Text(
                    text = "Maximum response length. One token is roughly 4 characters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * TTS settings section with speaking rate and playback speed.
 */
@Composable
private fun TtsSettingsSection(
    speakingRate: Float,
    onSpeakingRateChange: (Float) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Voice Output Tuning",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            // Speaking rate slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Speaking Rate",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "%.1fx".format(speakingRate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = speakingRate,
                    onValueChange = onSpeakingRateChange,
                    valueRange = 0.5f..2.0f,
                )
                Text(
                    text = "Adjust how fast the AI speaks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // Playback speed slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Playback Speed",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "%.1fx".format(playbackSpeed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = playbackSpeed,
                    onValueChange = onPlaybackSpeedChange,
                    valueRange = 0.5f..2.0f,
                )
                Text(
                    text = "Speed up or slow down audio playback",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Curriculum playback settings section.
 */
@Composable
private fun CurriculumSettingsSection(
    autoContinueTopics: Boolean,
    onAutoContinueChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.PlaylistPlay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Session Behavior",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            SettingsToggle(
                title = "Auto-continue to next topic",
                description = "Automatically start the next topic when current one finishes",
                checked = autoContinueTopics,
                onCheckedChange = onAutoContinueChange,
            )

            Text(
                text =
                    "When a topic completes, seamlessly continue to the next topic " +
                        "in the curriculum with an audio announcement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Reusable settings toggle row.
 */
@Composable
private fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * On-device LLM settings section.
 *
 * Shows device capability, download status, and available models.
 * Allows downloading models for fully offline AI tutoring.
 */
@Composable
private fun OnDeviceLlmSection(
    supportsOnDeviceLLM: Boolean,
    deviceRamMB: Int,
    recommendedModel: DeviceCapabilityDetector.OnDeviceModelSpec?,
    downloadState: ModelDownloadManager.DownloadState,
    availableModels: List<ModelDownloadManager.ModelInfo>,
    onDownloadRecommended: () -> Unit,
    onDownloadModel: (DeviceCapabilityDetector.OnDeviceModelSpec) -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteModel: (DeviceCapabilityDetector.OnDeviceModelSpec) -> Unit,
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf<DeviceCapabilityDetector.OnDeviceModelSpec?>(null) }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "On-Device AI Models",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            // Device capability info
            DeviceCapabilityInfo(
                supportsOnDeviceLLM = supportsOnDeviceLLM,
                deviceRamMB = deviceRamMB,
                recommendedModel = recommendedModel,
            )

            if (!supportsOnDeviceLLM) {
                Text(
                    text =
                        "Your device doesn't meet the minimum requirements for on-device AI. " +
                            "Cloud-based AI will be used instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            HorizontalDivider()

            // Download state indicator
            DownloadStateIndicator(
                downloadState = downloadState,
                onCancel = onCancelDownload,
            )

            // Available models
            Text(
                text = "Available Models",
                style = MaterialTheme.typography.bodyMedium,
            )

            availableModels.forEach { modelInfo ->
                ModelCard(
                    modelInfo = modelInfo,
                    isRecommended = modelInfo.spec == recommendedModel,
                    isDownloading = downloadState is ModelDownloadManager.DownloadState.Downloading,
                    onDownload = { onDownloadModel(modelInfo.spec) },
                    onDelete = { showDeleteDialog = modelInfo.spec },
                )
            }

            // Quick download for recommended model
            if (availableModels.none { it.isDownloaded } &&
                downloadState is ModelDownloadManager.DownloadState.Idle
            ) {
                TextButton(
                    onClick = onDownloadRecommended,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download Recommended Model")
                }
            }

            // Storage info
            Text(
                text = "Models are stored locally and can be deleted anytime to free space.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { spec ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Model?") },
            text = {
                Text(
                    "Delete ${spec.displayName}? You can re-download it later. " +
                        "This will free up ${Formatter.formatFileSize(context, spec.sizeBytes)}.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteModel(spec)
                        showDeleteDialog = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/**
 * Device capability information display.
 */
@Composable
private fun DeviceCapabilityInfo(
    supportsOnDeviceLLM: Boolean,
    deviceRamMB: Int,
    recommendedModel: DeviceCapabilityDetector.OnDeviceModelSpec?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Device RAM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${deviceRamMB / 1024} GB",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "On-Device AI",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    if (supportsOnDeviceLLM) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = if (supportsOnDeviceLLM) "Supported" else "Not supported",
                    tint =
                        if (supportsOnDeviceLLM) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    modifier = Modifier.height(16.dp),
                )
                Text(
                    text = if (supportsOnDeviceLLM) "Supported" else "Not Supported",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (supportsOnDeviceLLM) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )
            }
        }

        if (recommendedModel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Recommended Model",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = recommendedModel.displayName,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * Download state indicator with progress.
 */
@Composable
private fun DownloadStateIndicator(
    downloadState: ModelDownloadManager.DownloadState,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current

    AnimatedVisibility(visible = downloadState !is ModelDownloadManager.DownloadState.Idle) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (downloadState) {
                is ModelDownloadManager.DownloadState.Downloading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Downloading...",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text =
                                    "${Formatter.formatFileSize(context, downloadState.downloadedBytes)} / " +
                                        Formatter.formatFileSize(context, downloadState.totalBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                    }
                    val progressPercent = (downloadState.progress * 100).toInt()
                    LinearProgressIndicator(
                        progress = { downloadState.progress },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription = "Download progress: $progressPercent percent"
                                },
                    )
                }
                is ModelDownloadManager.DownloadState.Verifying -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LinearProgressIndicator(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = "Verifying download" },
                        )
                        Text(
                            text = "Verifying...",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                is ModelDownloadManager.DownloadState.Complete -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Download complete!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                is ModelDownloadManager.DownloadState.Error -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "Error: ${downloadState.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                is ModelDownloadManager.DownloadState.Cancelled -> {
                    Text(
                        text = "Download cancelled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ModelDownloadManager.DownloadState.Idle -> {
                    // No indicator when idle
                }
            }

            HorizontalDivider()
        }
    }
}

/**
 * Model card showing download/delete options.
 */
@Composable
private fun ModelCard(
    modelInfo: ModelDownloadManager.ModelInfo,
    isRecommended: Boolean,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = modelInfo.spec.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (isRecommended) {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = {
                                Text(
                                    "Recommended",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }

                Text(
                    text =
                        buildString {
                            append(Formatter.formatFileSize(context, modelInfo.spec.sizeBytes))
                            append(" • ")
                            append("Requires ${modelInfo.spec.minRamMB / 1024}GB+ RAM")
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (modelInfo.isDownloaded) {
                    Text(
                        text = "Downloaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (modelInfo.isDownloaded) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete model",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                IconButton(
                    onClick = onDownload,
                    enabled = !isDownloading,
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download model",
                        tint =
                            if (isDownloading) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                    )
                }
            }
        }
    }
}
