package com.unamentis.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unamentis.R
import com.unamentis.services.tts.ChatterboxLanguage
import com.unamentis.services.tts.ChatterboxParalinguisticTags
import com.unamentis.ui.components.IOSCard
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography

/**
 * Chatterbox TTS settings sub-screen.
 *
 * Provides detailed control over Chatterbox TTS parameters including
 * presets, emotion control, speed, language, and paralinguistic tags.
 *
 * @param onNavigateBack Callback to navigate back to parent screen
 * @param viewModel Shared settings ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatterboxSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preset by viewModel.chatterboxPreset.collectAsStateWithLifecycle()
    val exaggeration by viewModel.chatterboxExaggeration.collectAsStateWithLifecycle()
    val cfgWeight by viewModel.chatterboxCfgWeight.collectAsStateWithLifecycle()
    val speed by viewModel.chatterboxSpeed.collectAsStateWithLifecycle()
    val language by viewModel.chatterboxLanguage.collectAsStateWithLifecycle()
    val paralinguisticTags by viewModel.chatterboxParalinguisticTags.collectAsStateWithLifecycle()
    val streaming by viewModel.chatterboxStreaming.collectAsStateWithLifecycle()

    val navBackCd = stringResource(R.string.cd_go_back)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics { contentDescription = navBackCd },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.chatterbox_settings_title),
                        style = IOSTypography.headline,
                    )
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = Dimensions.ScreenHorizontalPadding)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingLarge),
        ) {
            // Preset section
            PresetSection(
                selectedPreset = preset,
                onPresetSelected = { viewModel.setChatterboxPreset(it) },
            )

            // Emotion control section
            EmotionSection(
                exaggeration = exaggeration,
                onExaggerationChange = { viewModel.setChatterboxExaggeration(it) },
                cfgWeight = cfgWeight,
                onCfgWeightChange = { viewModel.setChatterboxCfgWeight(it) },
            )

            // Speed section
            SpeedSection(
                speed = speed,
                onSpeedChange = { viewModel.setChatterboxSpeed(it) },
            )

            // Language section
            LanguageSection(
                selectedLanguage = language,
                onLanguageSelected = { viewModel.setChatterboxLanguage(it) },
            )

            // Paralinguistic tags section
            ParalinguisticSection(
                enabled = paralinguisticTags,
                onEnabledChange = { viewModel.setChatterboxParalinguisticTags(it) },
            )

            // Streaming section
            StreamingSection(
                enabled = streaming,
                onEnabledChange = { viewModel.setChatterboxStreaming(it) },
            )

            // Reset button
            TextButton(
                onClick = { viewModel.resetChatterboxSettings() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.chatterbox_reset),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Chatterbox preset picker section.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetSection(
    selectedPreset: String,
    onPresetSelected: (String) -> Unit,
) {
    val presets =
        listOf(
            "DEFAULT" to stringResource(R.string.chatterbox_preset_default),
            "NATURAL" to stringResource(R.string.chatterbox_preset_natural),
            "EXPRESSIVE" to stringResource(R.string.chatterbox_preset_expressive),
            "LOW_LATENCY" to stringResource(R.string.chatterbox_preset_low_latency),
            "TUTOR" to stringResource(R.string.chatterbox_preset_tutor),
        )

    IOSCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium)) {
            Text(
                text = stringResource(R.string.chatterbox_preset_label),
                style = IOSTypography.subheadline,
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
                verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
            ) {
                presets.forEach { (key, label) ->
                    FilterChip(
                        selected = selectedPreset == key,
                        onClick = {
                            onPresetSelected(key)
                        },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}

/**
 * Emotion control sliders for exaggeration and CFG weight.
 */
@Composable
private fun EmotionSection(
    exaggeration: Float,
    onExaggerationChange: (Float) -> Unit,
    cfgWeight: Float,
    onCfgWeightChange: (Float) -> Unit,
) {
    val exaggerationCd = stringResource(R.string.cd_chatterbox_exaggeration)
    val cfgWeightCd = stringResource(R.string.cd_chatterbox_cfg_weight)

    IOSCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium)) {
            Text(
                text = stringResource(R.string.chatterbox_emotion_section),
                style = IOSTypography.subheadline,
            )

            // Exaggeration slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.chatterbox_exaggeration),
                        style = IOSTypography.body,
                    )
                    Text(
                        text = stringResource(R.string.settings_rate_format, exaggeration),
                        style = IOSTypography.body,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = exaggeration,
                    onValueChange = onExaggerationChange,
                    valueRange = 0.0f..1.5f,
                    modifier = Modifier.semantics { contentDescription = exaggerationCd },
                )
                Text(
                    text = stringResource(R.string.chatterbox_exaggeration_hint),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // CFG weight slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.chatterbox_cfg_weight),
                        style = IOSTypography.body,
                    )
                    Text(
                        text = stringResource(R.string.settings_rate_format, cfgWeight),
                        style = IOSTypography.body,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = cfgWeight,
                    onValueChange = onCfgWeightChange,
                    valueRange = 0.0f..1.0f,
                    modifier = Modifier.semantics { contentDescription = cfgWeightCd },
                )
                Text(
                    text = stringResource(R.string.chatterbox_cfg_weight_hint),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Speed control slider.
 */
@Composable
private fun SpeedSection(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
) {
    val speedCd = stringResource(R.string.cd_chatterbox_speed)

    IOSCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium)) {
            Text(
                text = stringResource(R.string.chatterbox_speed_section),
                style = IOSTypography.subheadline,
            )

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.chatterbox_speed_label),
                        style = IOSTypography.body,
                    )
                    Text(
                        text = stringResource(R.string.settings_rate_format, speed),
                        style = IOSTypography.body,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = speed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..2.0f,
                    modifier = Modifier.semantics { contentDescription = speedCd },
                )
                Text(
                    text = stringResource(R.string.chatterbox_speed_hint),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Language picker section with selectable chips for all 23 supported languages.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageSection(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
) {
    // Show a subset of popular languages, with option to expand
    var showAllLanguages by remember { mutableStateOf(false) }
    val popularLanguages =
        listOf(
            ChatterboxLanguage.ENGLISH,
            ChatterboxLanguage.SPANISH,
            ChatterboxLanguage.FRENCH,
            ChatterboxLanguage.GERMAN,
            ChatterboxLanguage.JAPANESE,
            ChatterboxLanguage.CHINESE_SIMPLIFIED,
        )
    val displayLanguages =
        if (showAllLanguages) {
            ChatterboxLanguage.entries
        } else {
            popularLanguages
        }

    IOSCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium)) {
            Text(
                text = stringResource(R.string.chatterbox_language_section),
                style = IOSTypography.subheadline,
            )

            Text(
                text = stringResource(R.string.chatterbox_language_label),
                style = IOSTypography.body,
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
                verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
            ) {
                displayLanguages.forEach { lang ->
                    FilterChip(
                        selected = selectedLanguage == lang.code,
                        onClick = { onLanguageSelected(lang.code) },
                        label = { Text(lang.displayName) },
                    )
                }
            }

            if (!showAllLanguages) {
                TextButton(onClick = { showAllLanguages = true }) {
                    Text(
                        text = stringResource(R.string.chatterbox_show_all_languages),
                        style = IOSTypography.caption,
                    )
                }
            }
        }
    }
}

/**
 * Paralinguistic tags toggle with supported tags list.
 */
@Composable
private fun ParalinguisticSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    IOSCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium)) {
            Text(
                text = stringResource(R.string.chatterbox_paralinguistic_section),
                style = IOSTypography.subheadline,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.chatterbox_paralinguistic_toggle),
                        style = IOSTypography.body,
                    )
                    Text(
                        text = stringResource(R.string.chatterbox_paralinguistic_desc),
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }

            if (enabled) {
                Text(
                    text =
                        stringResource(
                            R.string.chatterbox_supported_tags,
                            ChatterboxParalinguisticTags.ALL_TAGS.joinToString(", "),
                        ),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Streaming mode toggle.
 */
@Composable
private fun StreamingSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    IOSCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium)) {
            Text(
                text = stringResource(R.string.chatterbox_streaming_section),
                style = IOSTypography.subheadline,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.chatterbox_streaming_toggle),
                        style = IOSTypography.body,
                    )
                    Text(
                        text = stringResource(R.string.chatterbox_streaming_desc),
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
        }
    }
}
