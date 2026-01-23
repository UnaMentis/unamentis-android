package com.unamentis.modules.knowledgebowl.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unamentis.R
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import com.unamentis.modules.knowledgebowl.data.model.KBStudyMode
import com.unamentis.modules.knowledgebowl.ui.theme.KBTheme
import com.unamentis.modules.knowledgebowl.ui.theme.color
import com.unamentis.ui.util.safeProgress

/**
 * Knowledge Bowl Dashboard - Entry point for the KB module.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KBDashboardScreen(
    onNavigateToWrittenSession: () -> Unit,
    onNavigateToOralSession: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPracticeSession: (KBStudyMode, List<KBQuestion>) -> Unit,
    viewModel: KBDashboardViewModel = hiltViewModel(),
) {
    val selectedRegion by viewModel.selectedRegion.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val questionsByDomain by viewModel.questionsByDomain.collectAsState()
    val totalQuestionCount by viewModel.totalQuestionCount.collectAsState()
    val competitionReadiness by viewModel.competitionReadiness.collectAsState()
    val availableStudyModes by viewModel.availableStudyModes.collectAsState()
    val selectedStudyMode by viewModel.selectedStudyMode.collectAsState()
    val totalQuestionsAnswered by viewModel.totalQuestionsAnswered.collectAsState()
    val averageResponseTime by viewModel.averageResponseTime.collectAsState()
    val overallAccuracy by viewModel.overallAccuracy.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.kb_knowledge_bowl)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.kb_settings),
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
            // Hero section with competition readiness
            HeroSection(
                readiness = competitionReadiness,
                totalQuestionsAnswered = totalQuestionsAnswered,
            )

            // Study modes section
            StudyModesSection(
                availableModes = availableStudyModes,
                onModeSelected = { viewModel.selectStudyMode(it) },
            )

            // Quick start section (legacy)
            QuickStartSection(
                enabled = totalQuestionCount > 0,
                onWrittenClick = onNavigateToWrittenSession,
                onOralClick = onNavigateToOralSession,
            )

            // Stats section
            StatsSection(
                totalQuestionsAnswered = totalQuestionsAnswered,
                averageResponseTime = averageResponseTime,
                accuracy = overallAccuracy,
            )

            // Domain mastery section
            if (totalQuestionCount > 0) {
                DomainMasterySection(
                    onDomainClick = { viewModel.showDomainDetail(it) },
                    getDomainMastery = viewModel::getDomainMastery,
                )
            }

            // Region selector
            RegionSelector(
                selectedRegion = selectedRegion,
                onRegionSelected = { viewModel.selectRegion(it) },
            )
        }
    }

    // Practice launcher sheet
    selectedStudyMode?.let { mode ->
        com.unamentis.modules.knowledgebowl.ui.launcher.KBPracticeLauncherSheet(
            mode = mode,
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(),
            onDismiss = { viewModel.clearStudyMode() },
            onStart = { questions ->
                viewModel.clearStudyMode()
                onNavigateToPracticeSession(mode, questions)
            },
        )
    }
}

@Composable
private fun HeroSection(
    readiness: Float,
    totalQuestionsAnswered: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Circular progress indicator
            val safeReadiness = safeProgress(readiness)
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(100.dp),
                    strokeWidth = 12.dp,
                    color = KBTheme.mathematics().copy(alpha = 0.2f),
                )
                CircularProgressIndicator(
                    progress = { safeReadiness },
                    modifier = Modifier.size(100.dp),
                    strokeWidth = 12.dp,
                    color = KBTheme.mathematics(),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(safeReadiness * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = KBTheme.textPrimary(),
                    )
                    Text(
                        text = stringResource(R.string.kb_ready_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = KBTheme.textSecondary(),
                    )
                }
            }

            Text(
                text = stringResource(R.string.kb_competition_readiness),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = KBTheme.textPrimary(),
            )

            Text(
                text =
                    if (totalQuestionsAnswered == 0) {
                        stringResource(R.string.kb_diagnostic_prompt)
                    } else {
                        pluralStringResource(
                            R.plurals.kb_questions_answered_count,
                            totalQuestionsAnswered,
                            totalQuestionsAnswered,
                        )
                    },
                style = MaterialTheme.typography.bodySmall,
                color = KBTheme.textSecondary(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StudyModesSection(
    availableModes: List<KBStudyMode>,
    onModeSelected: (KBStudyMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.kb_study_sessions),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = KBTheme.textPrimary(),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
            userScrollEnabled = false,
        ) {
            items(availableModes) { mode ->
                StudyModeCard(
                    mode = mode,
                    onClick = { onModeSelected(mode) },
                )
            }
        }
    }
}

@Composable
private fun StudyModeCard(
    mode: KBStudyMode,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = mode.displayName }
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = mode.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = KBTheme.textPrimary(),
            )
            Text(
                text = mode.description,
                style = MaterialTheme.typography.bodySmall,
                color = KBTheme.textSecondary(),
            )
        }
    }
}

@Composable
private fun StatsSection(
    totalQuestionsAnswered: Int,
    averageResponseTime: Double,
    accuracy: Float,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.kb_your_stats),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = KBTheme.textPrimary(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatBadge(
                    value = "$totalQuestionsAnswered",
                    label = stringResource(R.string.kb_questions),
                )
                StatBadge(
                    value =
                        if (averageResponseTime > 0) {
                            String.format("%.1fs", averageResponseTime)
                        } else {
                            "--"
                        },
                    label = stringResource(R.string.kb_avg_speed),
                )
                StatBadge(
                    value =
                        if (totalQuestionsAnswered > 0) {
                            String.format("%.0f%%", accuracy * 100)
                        } else {
                            "--%"
                        },
                    label = stringResource(R.string.kb_accuracy),
                )
            }
        }
    }
}

@Composable
private fun DomainMasterySection(
    onDomainClick: (KBDomain) -> Unit,
    getDomainMastery: (KBDomain) -> Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.kb_domain_mastery),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = KBTheme.textPrimary(),
        )

        // Display all 12 domains in a 3-column grid with sufficient height
        val domains = KBDomain.entries
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // Height accommodates 4 rows of domains (12 domains / 3 columns = 4 rows)
            modifier = Modifier.height(400.dp),
        ) {
            items(domains) { domain ->
                DomainMasteryCard(
                    domain = domain,
                    mastery = getDomainMastery(domain),
                    onClick = { onDomainClick(domain) },
                )
            }
        }
    }
}

@Composable
private fun DomainMasteryCard(
    domain: KBDomain,
    mastery: Float,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = domain.displayName }
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = domain.color().copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = domain.icon,
                style = MaterialTheme.typography.titleLarge,
                color = domain.color(),
            )
            Text(
                text = domain.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = KBTheme.textPrimary(),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = "${(mastery * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = KBTheme.textSecondary(),
            )
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
            text = stringResource(R.string.kb_quick_start),
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
                title = stringResource(R.string.kb_written_practice),
                subtitle = stringResource(R.string.kb_mcq_practice),
                accentColor = KBTheme.intermediate(),
                enabled = enabled,
                onClick = onWrittenClick,
            )

            QuickStartButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Mic,
                title = stringResource(R.string.kb_oral_practice),
                subtitle = stringResource(R.string.kb_voice_qa),
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
                .semantics { contentDescription = "$title, $subtitle" }
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
            text = stringResource(R.string.kb_competition_region),
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
