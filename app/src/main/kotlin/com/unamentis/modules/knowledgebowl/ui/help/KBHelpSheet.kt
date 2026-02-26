package com.unamentis.modules.knowledgebowl.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unamentis.R
import com.unamentis.modules.knowledgebowl.ui.theme.KBTheme

/**
 * Help sheet with Getting Started, Training Modes, Strategy, and Regional Rules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KBHelpSheet(onDismiss: () -> Unit = {}) {
    var currentSection by remember { mutableStateOf<HelpSection?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (currentSection == null) {
                            stringResource(R.string.kb_help_title)
                        } else {
                            currentSection!!.title
                        },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentSection != null) {
                                currentSection = null
                            } else {
                                onDismiss()
                            }
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_go_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (currentSection == null) {
            HelpOverview(
                modifier = Modifier.padding(padding),
                onSectionSelected = { currentSection = it },
            )
        } else {
            HelpSectionContent(
                section = currentSection!!,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun HelpOverview(
    modifier: Modifier = Modifier,
    onSectionSelected: (HelpSection) -> Unit,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Text(
                text = stringResource(R.string.kb_help_overview),
                style = MaterialTheme.typography.bodyMedium,
                color = KBTheme.textSecondary(),
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Help sections
        item {
            HelpSectionRow(
                icon = Icons.Default.Star,
                title = stringResource(R.string.kb_help_getting_started),
                description = stringResource(R.string.kb_help_getting_started_desc),
                onClick = { onSectionSelected(HelpSection.GETTING_STARTED) },
            )
        }
        item {
            HelpSectionRow(
                icon = Icons.Default.Info,
                title = stringResource(R.string.kb_help_training_modes),
                description = stringResource(R.string.kb_help_training_modes_desc),
                onClick = { onSectionSelected(HelpSection.TRAINING_MODES) },
            )
        }
        item {
            HelpSectionRow(
                icon = Icons.Default.CheckCircle,
                title = stringResource(R.string.kb_help_strategy),
                description = stringResource(R.string.kb_help_strategy_desc),
                onClick = { onSectionSelected(HelpSection.STRATEGY) },
            )
        }
        item {
            HelpSectionRow(
                icon = Icons.Default.Info,
                title = stringResource(R.string.kb_help_regional_rules),
                description = stringResource(R.string.kb_help_regional_rules_desc),
                onClick = { onSectionSelected(HelpSection.REGIONAL_RULES) },
            )
        }

        // Tips for success
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.kb_help_tips_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        val tips =
            listOf(
                R.string.kb_help_tip_1,
                R.string.kb_help_tip_2,
                R.string.kb_help_tip_3,
                R.string.kb_help_tip_4,
                R.string.kb_help_tip_5,
            )

        tips.forEach { tipRes ->
            item {
                TipRow(text = stringResource(tipRes))
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun HelpSectionRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$title. $description" },
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = KBTheme.textSecondary(),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = KBTheme.textSecondary(),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun TipRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun HelpSectionContent(
    section: HelpSection,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        when (section) {
            HelpSection.GETTING_STARTED -> {
                val steps =
                    listOf(
                        R.string.kb_help_step_1,
                        R.string.kb_help_step_2,
                        R.string.kb_help_step_3,
                        R.string.kb_help_step_4,
                        R.string.kb_help_step_5,
                    )
                itemsIndexed(steps) { index, stepRes ->
                    StepRow(stepNumber = index + 1, text = stringResource(stepRes))
                }
            }
            HelpSection.TRAINING_MODES -> {
                val modes =
                    listOf(
                        R.string.kb_help_mode_written to R.string.kb_help_mode_written_desc,
                        R.string.kb_help_mode_oral to R.string.kb_help_mode_oral_desc,
                        R.string.kb_help_mode_match to R.string.kb_help_mode_match_desc,
                        R.string.kb_help_mode_conference to R.string.kb_help_mode_conference_desc,
                        R.string.kb_help_mode_rebound to R.string.kb_help_mode_rebound_desc,
                        R.string.kb_help_mode_drill to R.string.kb_help_mode_drill_desc,
                    )
                modes.forEach { (titleRes, descRes) ->
                    item {
                        ModeCard(
                            title = stringResource(titleRes),
                            description = stringResource(descRes),
                        )
                    }
                }
            }
            HelpSection.STRATEGY -> {
                val strategies =
                    listOf(
                        R.string.kb_help_strategy_written to R.string.kb_help_strategy_written_desc,
                        R.string.kb_help_strategy_oral to R.string.kb_help_strategy_oral_desc,
                        R.string.kb_help_strategy_buzzing to R.string.kb_help_strategy_buzzing_desc,
                        R.string.kb_help_strategy_time to R.string.kb_help_strategy_time_desc,
                    )
                strategies.forEach { (titleRes, descRes) ->
                    item {
                        ModeCard(
                            title = stringResource(titleRes),
                            description = stringResource(descRes),
                        )
                    }
                }
            }
            HelpSection.REGIONAL_RULES -> {
                item {
                    RegionalComparisonTable()
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun StepRow(
    stepNumber: Int,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = stepNumber.toString(),
                modifier = Modifier.padding(6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = KBTheme.textSecondary(),
            )
        }
    }
}

@Composable
private fun RegionalComparisonTable() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.kb_help_regional_comparison),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Header row
            ComparisonRow(
                label = "",
                co = stringResource(R.string.kb_region_colorado_short),
                mn = stringResource(R.string.kb_region_minnesota_short),
                wa = stringResource(R.string.kb_region_washington_short),
                isHeader = true,
            )

            ComparisonRow(
                label = stringResource(R.string.kb_help_written_qs),
                co = "60",
                mn = "60",
                wa = "50",
            )
            ComparisonRow(
                label = stringResource(R.string.kb_help_written_time),
                co = "15 min",
                mn = "15 min",
                wa = "45 min",
            )
            ComparisonRow(
                label = stringResource(R.string.kb_help_conferring),
                co = stringResource(R.string.kb_help_hand_signals),
                mn = stringResource(R.string.kb_help_verbal),
                wa = stringResource(R.string.kb_help_hand_signals),
            )
        }
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    co: String,
    mn: String,
    wa: String,
    isHeader: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val weight = if (isHeader) FontWeight.Bold else FontWeight.Normal
        Text(
            text = label,
            modifier = Modifier.weight(1.5f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = weight,
        )
        Text(
            text = co,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = weight,
        )
        Text(
            text = mn,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = weight,
        )
        Text(
            text = wa,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = weight,
        )
    }
}

/**
 * Help content sections.
 */
enum class HelpSection(val title: String) {
    GETTING_STARTED("Getting Started"),
    TRAINING_MODES("Training Modes"),
    STRATEGY("Competition Strategy"),
    REGIONAL_RULES("Regional Rules"),
}
