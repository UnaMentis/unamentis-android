package com.unamentis.modules.knowledgebowl.ui.progress

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unamentis.R
import com.unamentis.modules.knowledgebowl.core.stats.MasteryLevel
import com.unamentis.modules.knowledgebowl.data.model.KBSession
import com.unamentis.modules.knowledgebowl.ui.theme.KBTheme
import com.unamentis.modules.knowledgebowl.ui.theme.color

/**
 * Progress dashboard showing overall stats, trends, domain mastery,
 * and recent session history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KBProgressScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToDomainMastery: () -> Unit = {},
    viewModel: KBProgressViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.kb_progress_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_go_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // Stats Cards
                item { StatsGrid(uiState) }

                // Accuracy Trend
                if (uiState.accuracyTrend.isNotEmpty()) {
                    item { AccuracyTrendCard(uiState) }
                }

                // Domain Mastery
                if (uiState.topDomains.isNotEmpty()) {
                    item { DomainMasteryCard(uiState, onNavigateToDomainMastery) }
                }

                // Recent Sessions
                if (uiState.recentSessions.isNotEmpty()) {
                    item { RecentSessionsHeader() }
                    items(uiState.recentSessions) { session ->
                        RecentSessionRow(session)
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun StatsGrid(uiState: KBProgressUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                value = uiState.totalSessions.toString(),
                label = stringResource(R.string.kb_progress_total_sessions),
                icon = Icons.Default.Star,
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = uiState.totalQuestions.toString(),
                label = stringResource(R.string.kb_progress_questions_answered),
                icon = Icons.Default.CheckCircle,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                value = "${(uiState.overallAccuracy * 100).toInt()}%",
                label = stringResource(R.string.kb_progress_overall_accuracy),
                icon = Icons.Default.Info,
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = "${uiState.currentStreak}",
                label = stringResource(R.string.kb_progress_current_streak),
                icon = Icons.Default.Star,
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val desc = "$label: $value"
    Card(
        modifier = modifier.semantics { contentDescription = desc },
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = KBTheme.textSecondary(),
            )
        }
    }
}

@Composable
private fun AccuracyTrendCard(uiState: KBProgressUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.kb_progress_accuracy_trend),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Simple bar chart representation
            Row(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                for (dataPoint in uiState.accuracyTrend.takeLast(MAX_TREND_BARS)) {
                    val barColor =
                        when {
                            dataPoint.accuracy >= 0.8 -> KBTheme.mastered()
                            dataPoint.accuracy >= 0.5 -> KBTheme.intermediate()
                            else -> KBTheme.focusArea()
                        }
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .height((dataPoint.accuracy * 100).dp.coerceAtMost(100.dp))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(barColor),
                    )
                }
            }
        }
    }
}

private const val MAX_TREND_BARS = 14

@Composable
private fun DomainMasteryCard(
    uiState: KBProgressUiState,
    onNavigate: () -> Unit,
) {
    Card(
        onClick = onNavigate,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.kb_domain_mastery),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            for (item in uiState.topDomains) {
                DomainMasteryRow(item)
                if (item != uiState.topDomains.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DomainMasteryRow(item: DomainMasteryItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(item.domain.color()),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.domain.displayName,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Text(
            text = "${(item.analytics.accuracy * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = masteryColor(item.mastery),
        )
    }
}

@Composable
private fun masteryColor(level: MasteryLevel) =
    when (level) {
        MasteryLevel.NOT_STARTED -> KBTheme.textSecondary()
        MasteryLevel.BEGINNER -> KBTheme.beginner()
        MasteryLevel.INTERMEDIATE -> KBTheme.intermediate()
        MasteryLevel.ADVANCED -> MaterialTheme.colorScheme.primary
        MasteryLevel.MASTERED -> KBTheme.mastered()
    }

@Composable
private fun RecentSessionsHeader() {
    Text(
        text = stringResource(R.string.kb_progress_recent_sessions),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun RecentSessionRow(session: KBSession) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = session.config.roundType.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = session.config.region.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = KBTheme.textSecondary(),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${session.correctCount}/${session.attempts.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )

                val accuracyPercent = (session.accuracy * 100).toInt()
                val color =
                    when {
                        accuracyPercent >= 80 -> KBTheme.mastered()
                        accuracyPercent >= 50 -> KBTheme.intermediate()
                        else -> KBTheme.focusArea()
                    }
                LinearProgressIndicator(
                    progress = { session.accuracy },
                    modifier = Modifier.width(60.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = color,
                )
            }
        }
    }
}
