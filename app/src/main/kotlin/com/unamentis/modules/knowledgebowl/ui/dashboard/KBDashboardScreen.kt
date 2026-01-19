package com.unamentis.modules.knowledgebowl.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import com.unamentis.modules.knowledgebowl.ui.theme.KBTheme
import com.unamentis.modules.knowledgebowl.ui.theme.color

/**
 * Knowledge Bowl Dashboard - Entry point for the KB module.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KBDashboardScreen(
    onNavigateToWrittenSession: () -> Unit,
    onNavigateToOralSession: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: KBDashboardViewModel = hiltViewModel(),
) {
    val selectedRegion by viewModel.selectedRegion.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val questionsByDomain by viewModel.questionsByDomain.collectAsState()
    val totalQuestionCount by viewModel.totalQuestionCount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Knowledge Bowl") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = KBTheme.bgPrimary(),
                    ),
            )
        },
        containerColor = KBTheme.bgPrimary(),
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Header card
            HeaderCard(
                isLoading = isLoading,
                error = error,
                totalQuestionCount = totalQuestionCount,
                selectedRegion = selectedRegion,
            )

            // Quick start section
            QuickStartSection(
                enabled = totalQuestionCount > 0,
                onWrittenClick = onNavigateToWrittenSession,
                onOralClick = onNavigateToOralSession,
            )

            // Region selector
            RegionSelector(
                selectedRegion = selectedRegion,
                onRegionSelected = { viewModel.selectRegion(it) },
            )

            // Stats section
            if (totalQuestionCount > 0) {
                QuestionBankSection(questionsByDomain = questionsByDomain)
            }
        }
    }
}

@Composable
private fun HeaderCard(
    isLoading: Boolean,
    error: String?,
    totalQuestionCount: Int,
    selectedRegion: KBRegion,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = KBTheme.bgSecondary(),
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = KBTheme.mastered(),
                    modifier = Modifier.size(40.dp),
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Knowledge Bowl",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = KBTheme.textPrimary(),
                    )
                    Text(
                        text = "Train for academic competitions",
                        style = MaterialTheme.typography.bodySmall,
                        color = KBTheme.textSecondary(),
                    )
                }
            }

            when {
                isLoading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = KBTheme.mastered(),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Loading questions...",
                            color = KBTheme.textSecondary(),
                        )
                    }
                }
                error != null -> {
                    Text(
                        text = "Error: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = KBTheme.focusArea(),
                    )
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatBadge(
                            value = "$totalQuestionCount",
                            label = "Questions",
                        )
                        StatBadge(
                            value = "${KBDomain.entries.size}",
                            label = "Domains",
                        )
                        StatBadge(
                            value = selectedRegion.abbreviation,
                            label = "Region",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadge(
    value: String,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = KBTheme.mastered(),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = KBTheme.textSecondary(),
        )
    }
}

@Composable
private fun QuickStartSection(
    enabled: Boolean,
    onWrittenClick: () -> Unit,
    onOralClick: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Quick Start",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = KBTheme.textPrimary(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickStartButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Edit,
                title = "Written",
                subtitle = "MCQ Practice",
                accentColor = KBTheme.intermediate(),
                enabled = enabled,
                onClick = onWrittenClick,
            )

            QuickStartButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Mic,
                title = "Oral",
                subtitle = "Voice Q&A",
                accentColor = KBTheme.mastered(),
                enabled = enabled,
                onClick = onOralClick,
            )
        }
    }
}

@Composable
private fun QuickStartButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .border(
                    width = 2.dp,
                    color = accentColor.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                ),
        colors =
            CardDefaults.cardColors(
                containerColor = KBTheme.bgSecondary(),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) accentColor else KBTheme.textSecondary(),
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) KBTheme.textPrimary() else KBTheme.textSecondary(),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = KBTheme.textSecondary(),
            )
        }
    }
}

@Composable
private fun RegionSelector(
    selectedRegion: KBRegion,
    onRegionSelected: (KBRegion) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Competition Region",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = KBTheme.textPrimary(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(KBRegion.COLORADO, KBRegion.MINNESOTA, KBRegion.WASHINGTON).forEach { region ->
                RegionButton(
                    region = region,
                    isSelected = selectedRegion == region,
                    onClick = { onRegionSelected(region) },
                )
            }
        }

        Text(
            text = selectedRegion.config.conferringRuleDescription,
            style = MaterialTheme.typography.bodySmall,
            color = KBTheme.textSecondary(),
        )
    }
}

@Composable
private fun RegionButton(
    region: KBRegion,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) KBTheme.mastered() else KBTheme.bgSecondary())
                .border(
                    width = 1.dp,
                    color = if (isSelected) Color.Transparent else KBTheme.border(),
                    shape = RoundedCornerShape(8.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = region.abbreviation,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else KBTheme.textPrimary(),
        )
    }
}

@Composable
private fun QuestionBankSection(questionsByDomain: Map<KBDomain, Int>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Question Bank",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = KBTheme.textPrimary(),
        )

        val domains = KBDomain.entries.take(6) // Show first 6 domains
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(200.dp),
        ) {
            items(domains) { domain ->
                DomainCard(
                    domain = domain,
                    count = questionsByDomain[domain] ?: 0,
                )
            }
        }
    }
}

@Composable
private fun DomainCard(
    domain: KBDomain,
    count: Int,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = KBTheme.bgSecondary(),
            ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Use a simple text icon since we don't have Material icons for all domains
            Text(
                text = domain.icon,
                style = MaterialTheme.typography.titleLarge,
                color = domain.color(),
            )

            Text(
                text = "$count",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = KBTheme.textPrimary(),
            )

            Text(
                text = domain.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = KBTheme.textSecondary(),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * Extension to get a simple icon character for a domain.
 */
private val KBDomain.icon: String
    get() =
        when (this) {
            KBDomain.SCIENCE -> "\u269B" // atom symbol
            KBDomain.MATHEMATICS -> "\u2211" // sigma
            KBDomain.LITERATURE -> "\uD83D\uDCD6" // books
            KBDomain.HISTORY -> "\u23F0" // clock
            KBDomain.SOCIAL_STUDIES -> "\uD83C\uDF0D" // globe
            KBDomain.ARTS -> "\uD83C\uDFA8" // palette
            KBDomain.CURRENT_EVENTS -> "\uD83D\uDCF0" // newspaper
            KBDomain.LANGUAGE -> "\uD83D\uDCDD" // memo
            KBDomain.TECHNOLOGY -> "\uD83D\uDCBB" // computer
            KBDomain.POP_CULTURE -> "\u2B50" // star
            KBDomain.RELIGION_PHILOSOPHY -> "\u2728" // sparkles
            KBDomain.MISCELLANEOUS -> "\u2753" // question mark
        }
