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
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unamentis.BuildConfig
import com.unamentis.R
import com.unamentis.core.config.ConfigurationPreset
import com.unamentis.core.config.RecordingMode
import com.unamentis.core.device.DeviceCapabilityDetector
import com.unamentis.services.llm.LLMBackendType
import com.unamentis.services.llm.ModelDownloadManager
import com.unamentis.ui.components.BrandLogo
import com.unamentis.ui.components.IOSCard
import com.unamentis.ui.components.Size
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import com.unamentis.ui.util.safeProgress

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
    onNavigateToServerSettings: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {},
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
    val availableExtendedModels by viewModel.availableExtendedModels.collectAsStateWithLifecycle()

    // GLM-ASR On-Device STT settings
    val glmAsrModels by viewModel.glmAsrModels.collectAsStateWithLifecycle()

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
                navigationIcon = {
                    BrandLogo(
                        size = Size.Compact,
                        modifier = Modifier.padding(start = Dimensions.SpacingLarge),
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.tab_settings),
                        style = IOSTypography.headline,
                    )
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .testTag("SettingsLazyColumn"),
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
                    text = stringResource(R.string.settings_providers).uppercase(),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .padding(top = Dimensions.SpacingSmall)
                            .testTag("settings_providers_header"),
                )
            }

            // STT Provider
            item {
                ProviderCard(
                    title = stringResource(R.string.settings_speech_to_text_title),
                    providers =
                        listOf(
                            stringResource(R.string.provider_deepgram),
                            stringResource(R.string.provider_android),
                        ),
                    selectedProvider = uiState.selectedSTTProvider,
                    onProviderSelected = { viewModel.setSTTProvider(it) },
                    icon = Icons.Default.Mic,
                )
            }

            // TTS Provider
            item {
                ProviderCard(
                    title = stringResource(R.string.settings_text_to_speech_title),
                    providers =
                        listOf(
                            stringResource(R.string.provider_elevenlabs),
                            stringResource(R.string.provider_android),
                        ),
                    selectedProvider = uiState.selectedTTSProvider,
                    onProviderSelected = { viewModel.setTTSProvider(it) },
                    icon = Icons.Default.VolumeUp,
                )
            }

            // LLM Provider
            item {
                ProviderCard(
                    title = stringResource(R.string.settings_language_model_title),
                    providers =
                        listOf(
                            stringResource(R.string.provider_patchpanel),
                            stringResource(R.string.provider_openai),
                            stringResource(R.string.provider_anthropic),
                        ),
                    selectedProvider = uiState.selectedLLMProvider,
                    onProviderSelected = { viewModel.setLLMProvider(it) },
                    icon = Icons.Default.Psychology,
                )
            }

            // Recording Mode section
            item {
                Text(
                    text = stringResource(R.string.settings_recording).uppercase(),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimensions.SpacingLarge),
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
                    text = stringResource(R.string.settings_audio).uppercase(),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimensions.SpacingLarge),
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
                    text = stringResource(R.string.settings_vad).uppercase(),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .padding(top = Dimensions.SpacingLarge)
                            .testTag("settings_voice_detection_header"),
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
                    text = stringResource(R.string.settings_language_model).uppercase(),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimensions.SpacingLarge),
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
                    text = stringResource(R.string.settings_on_device_ai).uppercase(),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .padding(top = Dimensions.SpacingLarge)
                            .testTag("settings_on_device_ai_header"),
                )
            }

            item {
                OnDeviceLlmSection(
                    supportsOnDeviceLLM = viewModel.supportsOnDeviceLLM,
                    deviceRamMB = viewModel.deviceRamMB,
                    recommendedModel = viewModel.recommendedModel,
                    downloadState = downloadState,
                    availableModels = availableModels,
                    availableExtendedModels = availableExtendedModels,
                    recommendedExtendedModel = viewModel.recommendedExtendedModel,
                    onDownloadRecommended = { viewModel.downloadRecommendedModel() },
                    onDownloadModel = { viewModel.downloadModel(it) },
                    onCancelDownload = { viewModel.cancelDownload() },
                    onDeleteModel = { viewModel.deleteModel(it) },
                    onDownloadExtendedModel = { viewModel.downloadExtendedModel(it) },
                    onDeleteExtendedModel = { viewModel.deleteExtendedModel(it) },
                )
            }

            // GLM-ASR On-Device STT section
            item {
                Text(
                    text = stringResource(R.string.settings_on_device_stt).uppercase(),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimensions.SpacingLarge),
                )
            }

            item {
                GLMASRSttSection(
                    supportsGLMASR = viewModel.supportsGLMASR,
                    deviceRamMB = viewModel.deviceRamMB,
                    glmAsrModels = glmAsrModels,
                    downloadState = downloadState,
                    onDownloadAll = { viewModel.downloadAllGLMASRModels() },
                    onDownloadModel = { viewModel.downloadGLMASRModel(it) },
                    onCancelDownload = { viewModel.cancelDownload() },
                    onDeleteAll = { viewModel.deleteAllGLMASRModels() },
                    onDeleteModel = { viewModel.deleteGLMASRModel(it) },
                )
            }

            // TTS Settings section
            item {
                Text(
                    text = stringResource(R.string.settings_voice_output).uppercase(),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimensions.SpacingLarge),
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
                    text = stringResource(R.string.settings_curriculum_playback).uppercase(),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimensions.SpacingLarge),
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
                    text = stringResource(R.string.settings_api_keys).uppercase(),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimensions.SpacingLarge),
                )
            }

            item {
                ApiKeySection(
                    viewModel = viewModel,
                    uiState = uiState,
                )
            }

            // Self-Hosted Servers section
            item {
                Text(
                    text = stringResource(R.string.server_settings_self_hosted_mode).uppercase(),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimensions.SpacingLarge),
                )
            }

            item {
                SelfHostedServersCard(
                    onClick = onNavigateToServerSettings,
                )
            }

            // About & Debug section
            item {
                Text(
                    text = stringResource(R.string.settings_info_section).uppercase(),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimensions.SpacingLarge),
                )
            }

            item {
                AboutCard(onClick = onNavigateToAbout)
            }

            // Debug Tools (only in debug builds)
            if (BuildConfig.DEBUG) {
                item {
                    DebugCard(onClick = onNavigateToDebug)
                }
            }
        }
    }
}

/**
 * Card for navigating to Self-Hosted Servers settings.
 */
@Composable
private fun SelfHostedServersCard(onClick: () -> Unit) {
    IOSCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.server_settings_title),
                    style = IOSTypography.body,
                )
                Text(
                    text = stringResource(R.string.settings_self_hosted_desc),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = stringResource(R.string.server_settings_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Card for navigating to About screen.
 */
@Composable
private fun AboutCard(onClick: () -> Unit) {
    IOSCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.settings_about),
                    style = IOSTypography.body,
                )
                Text(
                    text = stringResource(R.string.settings_about_desc),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = stringResource(R.string.settings_about),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Card for navigating to Debug Tools screen.
 */
@Composable
private fun DebugCard(onClick: () -> Unit) {
    IOSCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.settings_debug_tools),
                    style = IOSTypography.body,
                )
                Text(
                    text = stringResource(R.string.settings_debug_desc),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = stringResource(R.string.settings_debug_tools),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall)) {
        Text(
            text = stringResource(R.string.settings_presets).uppercase(),
            style = IOSTypography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
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
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
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
    val presetLabel =
        when (preset) {
            ConfigurationPreset.FREE -> stringResource(R.string.settings_preset_free)
            ConfigurationPreset.PREMIUM -> stringResource(R.string.settings_preset_premium)
            ConfigurationPreset.LOW_LATENCY -> stringResource(R.string.settings_preset_low_latency)
            ConfigurationPreset.COST_OPTIMIZED -> stringResource(R.string.settings_preset_cost_optimized)
            ConfigurationPreset.OFFLINE -> stringResource(R.string.settings_preset_offline)
        }
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = presetLabel,
                style = IOSTypography.caption2,
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
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = title,
                    style = IOSTypography.subheadline,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
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

    val deepgramName = stringResource(R.string.provider_deepgram)
    val elevenLabsName = stringResource(R.string.provider_elevenlabs)
    val openAiName = stringResource(R.string.provider_openai)
    val anthropicName = stringResource(R.string.provider_anthropic)

    IOSCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            ApiKeyItem(
                name = deepgramName,
                hasKey = uiState.hasDeepgramKey,
                onEdit = { showDeepgramDialog = true },
            )
            ApiKeyItem(
                name = elevenLabsName,
                hasKey = uiState.hasElevenLabsKey,
                onEdit = { showElevenLabsDialog = true },
            )
            ApiKeyItem(
                name = openAiName,
                hasKey = uiState.hasOpenAIKey,
                onEdit = { showOpenAIDialog = true },
            )
            ApiKeyItem(
                name = anthropicName,
                hasKey = uiState.hasAnthropicKey,
                onEdit = { showAnthropicDialog = true },
            )
        }
    }

    // API Key dialogs
    if (showDeepgramDialog) {
        ApiKeyDialog(
            title = stringResource(R.string.settings_api_key_dialog_title, deepgramName),
            currentKey = viewModel.getDeepgramApiKey(),
            onDismiss = { showDeepgramDialog = false },
            onSave = { viewModel.updateDeepgramApiKey(it) },
        )
    }
    if (showElevenLabsDialog) {
        ApiKeyDialog(
            title = stringResource(R.string.settings_api_key_dialog_title, elevenLabsName),
            currentKey = viewModel.getElevenLabsApiKey(),
            onDismiss = { showElevenLabsDialog = false },
            onSave = { viewModel.updateElevenLabsApiKey(it) },
        )
    }
    if (showOpenAIDialog) {
        ApiKeyDialog(
            title = stringResource(R.string.settings_api_key_dialog_title, openAiName),
            currentKey = viewModel.getOpenAIApiKey(),
            onDismiss = { showOpenAIDialog = false },
            onSave = { viewModel.updateOpenAIApiKey(it) },
        )
    }
    if (showAnthropicDialog) {
        ApiKeyDialog(
            title = stringResource(R.string.settings_api_key_dialog_title, anthropicName),
            currentKey = viewModel.getAnthropicApiKey(),
            onDismiss = { showAnthropicDialog = false },
            onSave = { viewModel.updateAnthropicApiKey(it) },
        )
    }
}

/**
 * Individual API key list item - iOS style.
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
                style = IOSTypography.body,
            )
            Text(
                text =
                    if (hasKey) {
                        stringResource(R.string.settings_configured)
                    } else {
                        stringResource(R.string.settings_not_configured)
                    },
                style = IOSTypography.caption,
                color =
                    if (hasKey) {
                        com.unamentis.ui.theme.iOSGreen
                    } else {
                        com.unamentis.ui.theme.iOSOrange
                    },
            )
        }

        TextButton(onClick = onEdit) {
            Text(
                text =
                    if (hasKey) {
                        stringResource(R.string.settings_edit)
                    } else {
                        stringResource(R.string.settings_add)
                    },
            )
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
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall)) {
                if (currentKey != null) {
                    Text(
                        text = stringResource(R.string.settings_api_key_current, currentKey),
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text(stringResource(R.string.settings_api_key_label)) },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription =
                                    if (showKey) {
                                        stringResource(R.string.cd_hide_api_key)
                                    } else {
                                        stringResource(R.string.cd_show_api_key)
                                    },
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
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.settings_recording_mode_title),
                    style = IOSTypography.subheadline,
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
    val modeDisplayName = stringResource(mode.displayNameResId)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelected,
            modifier = Modifier.semantics { contentDescription = modeDisplayName },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(mode.displayNameResId),
                style = IOSTypography.body,
            )
            Text(
                text = stringResource(mode.descriptionResId),
                style = IOSTypography.caption,
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
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.settings_audio_quality),
                    style = IOSTypography.subheadline,
                )
            }

            // Sample rate picker
            Text(
                text = stringResource(R.string.settings_sample_rate),
                style = IOSTypography.body,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
            ) {
                listOf(16000, 24000, 48000).forEach { rate ->
                    FilterChip(
                        selected = sampleRate == rate,
                        onClick = { onSampleRateChange(rate) },
                        label = { Text(stringResource(R.string.settings_sample_rate_khz, rate / 1000)) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_sample_rate_hint),
                style = IOSTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            // Toggle options
            SettingsToggle(
                title = stringResource(R.string.settings_voice_processing),
                description = stringResource(R.string.settings_voice_processing_desc),
                checked = enableVoiceProcessing,
                onCheckedChange = onVoiceProcessingChange,
            )

            SettingsToggle(
                title = stringResource(R.string.settings_echo_cancellation),
                description = stringResource(R.string.settings_echo_cancellation_desc),
                checked = enableEchoCancellation,
                onCheckedChange = onEchoCancellationChange,
            )

            SettingsToggle(
                title = stringResource(R.string.settings_noise_suppression),
                description = stringResource(R.string.settings_noise_suppression_desc),
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
    val numberFormatter =
        remember {
            java.text.NumberFormat.getNumberInstance(java.util.Locale.getDefault()).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
        }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingLarge),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.settings_voice_detection_tuning),
                    style = IOSTypography.subheadline,
                )
            }

            // VAD Threshold slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.settings_detection_threshold),
                        style = IOSTypography.body,
                    )
                    Text(
                        text = numberFormatter.format(vadThreshold),
                        style = IOSTypography.body,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = vadThreshold,
                    onValueChange = onVadThresholdChange,
                    valueRange = 0.3f..0.9f,
                )
                Text(
                    text = stringResource(R.string.settings_detection_threshold_hint),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // Barge-in settings
            SettingsToggle(
                title = stringResource(R.string.settings_enable_interruptions),
                description = stringResource(R.string.settings_enable_interruptions_desc),
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
                            text = stringResource(R.string.settings_interruption_threshold),
                            style = IOSTypography.body,
                        )
                        Text(
                            text = numberFormatter.format(bargeInThreshold),
                            style = IOSTypography.body,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = bargeInThreshold,
                        onValueChange = onBargeInThresholdChange,
                        valueRange = 0.5f..0.95f,
                    )
                    Text(
                        text = stringResource(R.string.settings_interruption_threshold_hint),
                        style = IOSTypography.caption,
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
                        text = stringResource(R.string.settings_silence_timeout),
                        style = IOSTypography.body,
                    )
                    Text(
                        text = stringResource(R.string.settings_silence_timeout_ms, silenceThresholdMs),
                        style = IOSTypography.body,
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
                    text = stringResource(R.string.settings_silence_timeout_hint),
                    style = IOSTypography.caption,
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
    val numberFormatter =
        remember {
            java.text.NumberFormat.getNumberInstance(java.util.Locale.getDefault()).apply {
                minimumFractionDigits = 1
                maximumFractionDigits = 1
            }
        }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingLarge),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.settings_response_tuning),
                    style = IOSTypography.subheadline,
                )
            }

            // Temperature slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.settings_temperature),
                        style = IOSTypography.body,
                    )
                    Text(
                        text = numberFormatter.format(temperature),
                        style = IOSTypography.body,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = temperature,
                    onValueChange = onTemperatureChange,
                    valueRange = 0f..1f,
                )
                Text(
                    text = stringResource(R.string.settings_temperature_hint),
                    style = IOSTypography.caption,
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
                        text = stringResource(R.string.settings_max_response_length),
                        style = IOSTypography.body,
                    )
                    Text(
                        text = stringResource(R.string.settings_max_tokens_value, maxTokens),
                        style = IOSTypography.body,
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
                    text = stringResource(R.string.settings_max_response_hint),
                    style = IOSTypography.caption,
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
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingLarge),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
            ) {
                Icon(
                    Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.settings_voice_output_tuning),
                    style = IOSTypography.subheadline,
                )
            }

            // Speaking rate slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.settings_speaking_rate),
                        style = IOSTypography.body,
                    )
                    Text(
                        text = stringResource(R.string.settings_rate_format, speakingRate),
                        style = IOSTypography.body,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = speakingRate,
                    onValueChange = onSpeakingRateChange,
                    valueRange = 0.5f..2.0f,
                )
                Text(
                    text = stringResource(R.string.settings_speaking_rate_hint),
                    style = IOSTypography.caption,
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
                        text = stringResource(R.string.settings_playback_speed),
                        style = IOSTypography.body,
                    )
                    Text(
                        text = stringResource(R.string.settings_rate_format, playbackSpeed),
                        style = IOSTypography.body,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = playbackSpeed,
                    onValueChange = onPlaybackSpeedChange,
                    valueRange = 0.5f..2.0f,
                )
                Text(
                    text = stringResource(R.string.settings_playback_speed_hint),
                    style = IOSTypography.caption,
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
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
            ) {
                Icon(
                    Icons.Default.PlaylistPlay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.settings_session_behavior),
                    style = IOSTypography.subheadline,
                )
            }

            SettingsToggle(
                title = stringResource(R.string.settings_auto_continue),
                description = stringResource(R.string.settings_auto_continue_desc),
                checked = autoContinueTopics,
                onCheckedChange = onAutoContinueChange,
            )

            Text(
                text = stringResource(R.string.settings_auto_continue_hint),
                style = IOSTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Reusable settings toggle row - iOS style.
 */
@Composable
private fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val toggleStateDesc =
        if (checked) {
            stringResource(R.string.cd_toggle_on, title)
        } else {
            stringResource(R.string.cd_toggle_off, title)
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = IOSTypography.body,
            )
            Text(
                text = description,
                style = IOSTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = toggleStateDesc },
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
    availableExtendedModels: List<ModelDownloadManager.ExtendedModelInfo>,
    recommendedExtendedModel: ModelDownloadManager.ExtendedModelSpec?,
    onDownloadRecommended: () -> Unit,
    onDownloadModel: (DeviceCapabilityDetector.OnDeviceModelSpec) -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteModel: (DeviceCapabilityDetector.OnDeviceModelSpec) -> Unit,
    onDownloadExtendedModel: (ModelDownloadManager.ExtendedModelSpec) -> Unit,
    onDeleteExtendedModel: (ModelDownloadManager.ExtendedModelSpec) -> Unit,
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf<DeviceCapabilityDetector.OnDeviceModelSpec?>(null) }
    var showExtendedDeleteDialog by remember { mutableStateOf<ModelDownloadManager.ExtendedModelSpec?>(null) }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingLarge),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
            ) {
                Icon(
                    Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.settings_on_device_models),
                    style = IOSTypography.subheadline,
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
                    text = stringResource(R.string.settings_not_supported_message),
                    style = IOSTypography.caption,
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

            // High-performance models (NPU/GPU accelerated)
            if (availableExtendedModels.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.settings_high_performance_models),
                    style = IOSTypography.body,
                )
                Text(
                    text = stringResource(R.string.settings_high_performance_models_desc),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                availableExtendedModels.forEach { modelInfo ->
                    ExtendedModelCard(
                        modelInfo = modelInfo,
                        isRecommended = modelInfo.spec == recommendedExtendedModel,
                        isDownloading = downloadState is ModelDownloadManager.DownloadState.Downloading,
                        onDownload = { onDownloadExtendedModel(modelInfo.spec) },
                        onDelete = { showExtendedDeleteDialog = modelInfo.spec },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = Dimensions.SpacingSmall))
            }

            // Legacy models (CPU)
            if (availableModels.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.settings_legacy_models),
                    style = IOSTypography.body,
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
            }

            // Quick download for recommended extended model
            val hasAnyModelDownloaded =
                availableExtendedModels.any { it.isDownloaded } ||
                    availableModels.any { it.isDownloaded }
            if (!hasAnyModelDownloaded &&
                downloadState is ModelDownloadManager.DownloadState.Idle &&
                recommendedExtendedModel != null
            ) {
                TextButton(
                    onClick = onDownloadRecommended,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(Dimensions.SpacingSmall))
                    Text(stringResource(R.string.settings_download_recommended))
                }
            }

            // Storage info
            Text(
                text = stringResource(R.string.settings_models_storage_hint),
                style = IOSTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { spec ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.settings_delete_model_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_delete_model_body,
                        spec.displayName,
                        Formatter.formatFileSize(context, spec.sizeBytes),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteModel(spec)
                        showDeleteDialog = null
                    },
                ) {
                    Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Delete confirmation dialog for extended models
    showExtendedDeleteDialog?.let { spec ->
        AlertDialog(
            onDismissRequest = { showExtendedDeleteDialog = null },
            title = { Text(stringResource(R.string.settings_delete_model_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_delete_model_body,
                        spec.displayName,
                        Formatter.formatFileSize(context, spec.sizeBytes),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteExtendedModel(spec)
                        showExtendedDeleteDialog = null
                    },
                ) {
                    Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExtendedDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
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
    val supportedText = stringResource(R.string.settings_on_device_supported)
    val notSupportedText = stringResource(R.string.settings_on_device_not_supported)

    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.settings_device_ram_label),
                style = IOSTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_device_ram_value, deviceRamMB / 1024),
                style = IOSTypography.caption,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.settings_on_device_ai_label),
                style = IOSTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
            ) {
                Icon(
                    if (supportsOnDeviceLLM) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = if (supportsOnDeviceLLM) supportedText else notSupportedText,
                    tint =
                        if (supportsOnDeviceLLM) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    modifier = Modifier.height(16.dp),
                )
                Text(
                    text = if (supportsOnDeviceLLM) supportedText else notSupportedText,
                    style = IOSTypography.caption,
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
                    text = stringResource(R.string.settings_recommended_model_label),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = recommendedModel.displayName,
                    style = IOSTypography.caption,
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
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        ) {
            when (downloadState) {
                is ModelDownloadManager.DownloadState.Downloading -> {
                    val progressPercent = (downloadState.progress * 100).toInt()
                    val progressDescription = stringResource(R.string.cd_download_progress, progressPercent)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_downloading_label),
                                style = IOSTypography.body,
                            )
                            Text(
                                text =
                                    "${Formatter.formatFileSize(context, downloadState.downloadedBytes)} / " +
                                        Formatter.formatFileSize(context, downloadState.totalBytes),
                                style = IOSTypography.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = onCancel) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                    LinearProgressIndicator(
                        progress = { safeProgress(downloadState.progress) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription = progressDescription
                                },
                    )
                }
                is ModelDownloadManager.DownloadState.Verifying -> {
                    val verifyingDescription = stringResource(R.string.cd_verifying_download)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
                    ) {
                        LinearProgressIndicator(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = verifyingDescription },
                        )
                        Text(
                            text = stringResource(R.string.settings_verifying_label),
                            style = IOSTypography.caption,
                        )
                    }
                }
                is ModelDownloadManager.DownloadState.Complete -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.cd_success),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.settings_download_complete_label),
                            style = IOSTypography.body,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                is ModelDownloadManager.DownloadState.Error -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_error),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = stringResource(R.string.settings_error_label, downloadState.message),
                            style = IOSTypography.caption,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                is ModelDownloadManager.DownloadState.Cancelled -> {
                    Text(
                        text = stringResource(R.string.settings_download_cancelled_label),
                        style = IOSTypography.caption,
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
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
                ) {
                    Text(
                        text = modelInfo.spec.displayName,
                        style = IOSTypography.body,
                    )
                    if (isRecommended) {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = {
                                Text(
                                    stringResource(R.string.settings_recommended),
                                    style = IOSTypography.caption2,
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
                            append(
                                context.getString(
                                    R.string.settings_model_requires_ram,
                                    modelInfo.spec.minRamMB / 1024,
                                ),
                            )
                        },
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (modelInfo.isDownloaded) {
                    Text(
                        text = stringResource(R.string.settings_downloaded),
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (modelInfo.isDownloaded) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_delete_model),
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
                        contentDescription = stringResource(R.string.cd_download_model),
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

/**
 * Extended model card showing download/delete options with backend type badge.
 */
@Composable
private fun ExtendedModelCard(
    modelInfo: ModelDownloadManager.ExtendedModelInfo,
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
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
                ) {
                    Text(
                        text = modelInfo.spec.displayName,
                        style = IOSTypography.body,
                    )
                    // Backend type badge
                    BackendTypeBadge(backendType = modelInfo.spec.backendType)
                    if (isRecommended) {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = {
                                Text(
                                    stringResource(R.string.settings_recommended),
                                    style = IOSTypography.caption2,
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
                            append(
                                context.getString(
                                    R.string.settings_model_requires_ram,
                                    modelInfo.spec.minRamMB / 1024,
                                ),
                            )
                        },
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (modelInfo.isDownloaded) {
                    Text(
                        text = stringResource(R.string.settings_downloaded),
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (modelInfo.isDownloaded) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_delete_model),
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
                        contentDescription = stringResource(R.string.cd_download_model),
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

/**
 * Backend type badge showing NPU/GPU/CPU.
 */
@Composable
private fun BackendTypeBadge(backendType: LLMBackendType) {
    val (label, color) =
        when (backendType) {
            LLMBackendType.EXECUTORCH_QNN -> {
                stringResource(R.string.settings_backend_npu) to MaterialTheme.colorScheme.tertiary
            }
            LLMBackendType.MEDIAPIPE -> {
                stringResource(R.string.settings_backend_gpu) to MaterialTheme.colorScheme.secondary
            }
            LLMBackendType.LLAMA_CPP -> {
                stringResource(R.string.settings_backend_cpu) to MaterialTheme.colorScheme.outline
            }
        }

    FilterChip(
        selected = false,
        onClick = {},
        label = {
            Text(
                text = label,
                style = IOSTypography.caption2,
                color = color,
            )
        },
    )
}

/**
 * GLM-ASR On-Device STT section.
 *
 * Shows device capability, download status, and available GLM-ASR models.
 * Allows downloading models for fully offline speech-to-text.
 */
@Composable
private fun GLMASRSttSection(
    supportsGLMASR: Boolean,
    deviceRamMB: Int,
    glmAsrModels: List<ModelDownloadManager.GLMASRModelInfo>,
    downloadState: ModelDownloadManager.DownloadState,
    onDownloadAll: () -> Unit,
    onDownloadModel: (ModelDownloadManager.GLMASRModelSpec) -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteAll: () -> Unit,
    onDeleteModel: (ModelDownloadManager.GLMASRModelSpec) -> Unit,
) {
    val context = LocalContext.current
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<ModelDownloadManager.GLMASRModelSpec?>(null) }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingLarge),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.settings_glm_asr_models),
                    style = IOSTypography.subheadline,
                )
            }

            // Description
            Text(
                text = stringResource(R.string.settings_glm_asr_desc),
                style = IOSTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Features
            Text(
                text = stringResource(R.string.settings_glm_asr_features),
                style = IOSTypography.caption,
                color = MaterialTheme.colorScheme.primary,
            )

            // Device info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.settings_device_ram_label, deviceRamMB),
                    style = IOSTypography.caption,
                )
                if (supportsGLMASR) {
                    Text(
                        text = stringResource(R.string.settings_on_device_supported),
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.settings_on_device_not_supported),
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (!supportsGLMASR) {
                Text(
                    text = stringResource(R.string.settings_glm_asr_not_supported),
                    style = IOSTypography.caption,
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

            // Model list
            glmAsrModels.forEach { modelInfo ->
                GLMASRModelCard(
                    modelInfo = modelInfo,
                    isDownloading = downloadState is ModelDownloadManager.DownloadState.Downloading,
                    onDownload = { onDownloadModel(modelInfo.spec) },
                    onDelete = { showDeleteDialog = modelInfo.spec },
                )
            }

            // Download all / Delete all buttons
            val downloadedCount = glmAsrModels.count { it.isDownloaded }
            val allDownloaded = downloadedCount == glmAsrModels.size

            if (!allDownloaded && downloadState is ModelDownloadManager.DownloadState.Idle) {
                TextButton(
                    onClick = onDownloadAll,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(Dimensions.SpacingSmall))
                    Text(stringResource(R.string.settings_glm_asr_download_all))
                }

                Text(
                    text = stringResource(R.string.settings_glm_asr_total_size),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (downloadedCount > 0) {
                if (allDownloaded) {
                    Text(
                        text = stringResource(R.string.settings_glm_asr_ready),
                        style = IOSTypography.body,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text =
                            stringResource(
                                R.string.settings_glm_asr_partial,
                                downloadedCount,
                                glmAsrModels.size,
                            ),
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TextButton(
                    onClick = { showDeleteAllDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(Dimensions.SpacingSmall))
                    Text(
                        text = stringResource(R.string.settings_glm_asr_delete_all),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Storage hint
            Text(
                text = stringResource(R.string.settings_models_storage_hint),
                style = IOSTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Delete all confirmation dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.settings_delete_model_title)) },
            text = {
                Text(stringResource(R.string.settings_glm_asr_delete_all))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAll()
                        showDeleteAllDialog = false
                    },
                ) {
                    Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Delete single model confirmation dialog
    showDeleteDialog?.let { spec ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.settings_delete_model_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_delete_model_body,
                        spec.displayName,
                        Formatter.formatFileSize(context, spec.sizeBytes),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteModel(spec)
                        showDeleteDialog = null
                    },
                ) {
                    Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * Card for a single GLM-ASR model.
 */
@Composable
private fun GLMASRModelCard(
    modelInfo: ModelDownloadManager.GLMASRModelInfo,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Dimensions.SpacingSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = modelInfo.spec.displayName,
                style = IOSTypography.body,
            )
            Text(
                text = Formatter.formatFileSize(context, modelInfo.spec.sizeBytes),
                style = IOSTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (modelInfo.isDownloaded) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.cd_success),
                    tint = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_delete_model),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        } else {
            IconButton(
                onClick = onDownload,
                enabled = !isDownloading,
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = stringResource(R.string.cd_download_model),
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
