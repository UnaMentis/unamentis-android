package com.unamentis.modules.knowledgebowl.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unamentis.R
import com.unamentis.modules.knowledgebowl.core.stats.KBStatsManager
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import com.unamentis.modules.knowledgebowl.ui.theme.KBTheme

/**
 * Knowledge Bowl settings screen.
 *
 * Allows configuration of competition region and other preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KBSettingsScreen(
    selectedRegion: KBRegion,
    onRegionSelected: (KBRegion) -> Unit,
    onResetStats: () -> Unit,
    onNavigateBack: () -> Unit,
    statsManager: KBStatsManager? = null,
) {
    var showResetConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.kb_settings),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = KBTheme.bgSecondary(),
                    ),
            )
        },
        containerColor = KBTheme.bgPrimary(),
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Region section
            item {
                Text(
                    text = stringResource(R.string.kb_competition_region),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = KBTheme.textPrimary(),
                )
            }

            items(KBRegion.entries) { region ->
                RegionCard(
                    region = region,
                    isSelected = selectedRegion == region,
                    onClick = { onRegionSelected(region) },
                )
            }

            // Region info
            item {
                RegionInfoCard(region = selectedRegion)
            }

            // Danger zone
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.kb_danger_zone),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = KBTheme.focusArea(),
                )
            }

            item {
                ResetStatsCard(
                    onClick = { showResetConfirmation = true },
                )
            }
        }
    }

    // Reset confirmation dialog
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = {
                Text(text = stringResource(R.string.kb_reset_stats_title))
            },
            text = {
                Text(text = stringResource(R.string.kb_reset_stats_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetStats()
                        statsManager?.resetStats()
                        showResetConfirmation = false
                    },
                ) {
                    Text(
                        text = stringResource(R.string.kb_reset),
                        color = KBTheme.focusArea(),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun RegionCard(
    region: KBRegion,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) {
                        KBTheme.mastered().copy(alpha = 0.1f)
                    } else {
                        KBTheme.bgSecondary()
                    },
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = region.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = KBTheme.textPrimary(),
                )
                Text(
                    text = region.config.conferringRuleDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = KBTheme.textSecondary(),
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = KBTheme.mastered(),
                )
            }
        }
    }
}

@Composable
private fun RegionInfoCard(region: KBRegion) {
    val config = region.config

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = KBTheme.bgSecondary(),
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = KBTheme.intermediate(),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.kb_region_rules),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = KBTheme.textPrimary(),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            InfoRow(
                label = stringResource(R.string.kb_written_questions),
                value = "${config.writtenQuestionCount} questions",
            )
            InfoRow(
                label = stringResource(R.string.kb_written_time),
                value = "${config.writtenTimeLimitMinutes} minutes",
            )
            InfoRow(
                label = stringResource(R.string.kb_written_points),
                value = "${config.writtenPointsPerCorrect} per correct",
            )
            InfoRow(
                label = stringResource(R.string.kb_oral_points),
                value = "${config.oralPointsPerCorrect} per correct",
            )
            InfoRow(
                label = stringResource(R.string.kb_conference_time),
                value = "${config.conferenceTime} seconds",
            )
            InfoRow(
                label = stringResource(R.string.kb_verbal_conferring),
                value =
                    if (config.verbalConferringAllowed) {
                        stringResource(R.string.kb_allowed)
                    } else {
                        stringResource(R.string.kb_not_allowed)
                    },
            )

            if (config.hasSOS) {
                InfoRow(
                    label = stringResource(R.string.kb_sos_bonus),
                    value = stringResource(R.string.kb_available),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = KBTheme.textSecondary(),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = KBTheme.textPrimary(),
        )
    }
}

@Composable
private fun ResetStatsCard(onClick: () -> Unit) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = KBTheme.focusArea().copy(alpha = 0.1f),
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.kb_reset_statistics),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = KBTheme.focusArea(),
                )
                Text(
                    text = stringResource(R.string.kb_reset_stats_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = KBTheme.textSecondary(),
                )
            }

            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = null,
                tint = KBTheme.focusArea(),
            )
        }
    }
}
