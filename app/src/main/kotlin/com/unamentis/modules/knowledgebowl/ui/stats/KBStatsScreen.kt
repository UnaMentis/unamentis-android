package com.unamentis.modules.knowledgebowl.ui.stats

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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unamentis.R
import com.unamentis.modules.knowledgebowl.core.stats.KBSessionRecord
import com.unamentis.modules.knowledgebowl.core.stats.KBStatsManager
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.ui.theme.KBTheme
import com.unamentis.modules.knowledgebowl.ui.theme.color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Knowledge Bowl statistics screen.
 *
 * Displays detailed practice statistics including overall performance,
 * domain mastery, recent sessions, and competition readiness.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KBStatsScreen(
    onNavigateBack: () -> Unit,
    statsManager: KBStatsManager = hiltViewModel<StatsScreenViewModel>().statsManager,
) {
    val totalQuestions by statsManager.totalQuestionsAnswered.collectAsState()
    val totalCorrect by statsManager.totalCorrectAnswers.collectAsState()
    val averageResponseTime by statsManager.averageResponseTime.collectAsState()
    val recentSessions by statsManager.recentSessions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.kb_statistics),
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
            // Overview section
            item {
                OverviewSection(
                    totalQuestions = totalQuestions,
                    totalCorrect = totalCorrect,
                    accuracy = statsManager.overallAccuracy,
                    averageResponseTime = averageResponseTime,
                )
            }

            // Competition readiness
            item {
                CompetitionReadinessCard(
                    readiness = statsManager.competitionReadiness,
                )
            }

            // Domain mastery
            item {
                Text(
                    text = stringResource(R.string.kb_domain_mastery),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = KBTheme.textPrimary(),
                )
            }

            item {
                DomainMasterySection(statsManager = statsManager)
            }

            // Weak and strong domains
            item {
                WeakStrongDomainsSection(statsManager = statsManager)
            }

            // Recent sessions
            if (recentSessions.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.kb_recent_sessions),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = KBTheme.textPrimary(),
                    )
                }

                items(recentSessions.take(5)) { session ->
                    RecentSessionCard(session = session)
                }
            }

            // Empty state
            if (totalQuestions == 0) {
                item {
                    EmptyStatsCard()
                }
            }
        }
    }
}

// region Overview Section

@Composable
private fun OverviewSection(
    totalQuestions: Int,
    totalCorrect: Int,
    accuracy: Float,
    averageResponseTime: Double,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = KBTheme.bgSecondary(),
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.kb_overview),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = KBTheme.textPrimary(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatItem(
                    icon = Icons.Default.CheckCircle,
                    value = "$totalQuestions",
                    label = stringResource(R.string.kb_questions_answered),
                    color = KBTheme.intermediate(),
                )
                StatItem(
                    icon = Icons.Default.Star,
                    value = "$totalCorrect",
                    label = stringResource(R.string.kb_correct),
                    color = KBTheme.mastered(),
                )
                StatItem(
                    icon = Icons.Default.EmojiEvents,
                    value = String.format("%.0f%%", accuracy * 100),
                    label = stringResource(R.string.kb_accuracy),
                    color = KBTheme.gold(),
                )
                StatItem(
                    icon = Icons.Default.Schedule,
                    value = String.format("%.1fs", averageResponseTime),
                    label = stringResource(R.string.kb_avg_time),
                    color = KBTheme.textSecondary(),
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    value: String,
    label: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = color,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = KBTheme.textPrimary(),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = KBTheme.textSecondary(),
            textAlign = TextAlign.Center,
        )
    }
}

// endregion

// region Competition Readiness

@Suppress("MagicNumber")
@Composable
private fun CompetitionReadinessCard(readiness: Float) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = KBTheme.bgSecondary(),
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.kb_competition_readiness),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = KBTheme.textPrimary(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Circular progress
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(100.dp),
                    color = KBTheme.border(),
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round,
                )

                CircularProgressIndicator(
                    progress = { readiness },
                    modifier = Modifier.size(100.dp),
                    color =
                        when {
                            readiness >= 0.8f -> KBTheme.mastered()
                            readiness >= 0.5f -> KBTheme.intermediate()
                            else -> KBTheme.beginner()
                        },
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round,
                )

                Text(
                    text = String.format("%.0f%%", readiness * 100),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = KBTheme.textPrimary(),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text =
                    when {
                        readiness >= 0.8f -> stringResource(R.string.kb_readiness_excellent)
                        readiness >= 0.6f -> stringResource(R.string.kb_readiness_good)
                        readiness >= 0.4f -> stringResource(R.string.kb_readiness_developing)
                        else -> stringResource(R.string.kb_readiness_keep_practicing)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = KBTheme.textSecondary(),
            )
        }
    }
}

// endregion

// region Domain Mastery

@Composable
private fun DomainMasterySection(statsManager: KBStatsManager) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = KBTheme.bgSecondary(),
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            KBDomain.entries.forEach { domain ->
                val mastery = statsManager.mastery(domain)
                val stats = statsManager.getDomainStats(domain)

                DomainMasteryRow(
                    domain = domain,
                    mastery = mastery,
                    questionsAnswered = stats.totalAnswered,
                )

                if (domain != KBDomain.entries.last()) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun DomainMasteryRow(
    domain: KBDomain,
    mastery: Float,
    questionsAnswered: Int,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(domain.color()),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = domain.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KBTheme.textPrimary(),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$questionsAnswered answered",
                    style = MaterialTheme.typography.labelSmall,
                    color = KBTheme.textSecondary(),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format("%.0f%%", mastery * 100),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = KBTheme.textPrimary(),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = { mastery },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            color = domain.color(),
            trackColor = KBTheme.border(),
        )
    }
}

// endregion

// region Weak/Strong Domains

@Composable
private fun WeakStrongDomainsSection(statsManager: KBStatsManager) {
    val weakDomains = statsManager.getWeakDomains(3)
    val strongDomains = statsManager.getStrongDomains(3)

    if (weakDomains.isEmpty() && strongDomains.isEmpty()) {
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Weak domains
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = KBTheme.focusArea().copy(alpha = 0.1f),
                ),
            modifier = Modifier.weight(1f),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = KBTheme.focusArea(),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.kb_focus_areas),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = KBTheme.focusArea(),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (weakDomains.isEmpty()) {
                    Text(
                        text = stringResource(R.string.kb_no_data_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = KBTheme.textSecondary(),
                    )
                } else {
                    weakDomains.forEach { (domain, mastery) ->
                        Text(
                            text = "${domain.displayName}: ${String.format("%.0f%%", mastery * 100)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = KBTheme.textPrimary(),
                        )
                    }
                }
            }
        }

        // Strong domains
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = KBTheme.mastered().copy(alpha = 0.1f),
                ),
            modifier = Modifier.weight(1f),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = KBTheme.mastered(),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.kb_strong_areas),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = KBTheme.mastered(),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (strongDomains.isEmpty()) {
                    Text(
                        text = stringResource(R.string.kb_no_data_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = KBTheme.textSecondary(),
                    )
                } else {
                    strongDomains.forEach { (domain, mastery) ->
                        Text(
                            text = "${domain.displayName}: ${String.format("%.0f%%", mastery * 100)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = KBTheme.textPrimary(),
                        )
                    }
                }
            }
        }
    }
}

// endregion

// region Recent Sessions

@Suppress("MagicNumber")
@Composable
private fun RecentSessionCard(session: KBSessionRecord) {
    val accuracy =
        if (session.questionsAnswered > 0) {
            session.correctAnswers.toFloat() / session.questionsAnswered
        } else {
            0f
        }

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = KBTheme.bgSecondary(),
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = session.studyMode.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = KBTheme.textPrimary(),
                )
                Text(
                    text = formatTimestamp(session.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = KBTheme.textSecondary(),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${session.correctAnswers}/${session.questionsAnswered}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = KBTheme.textPrimary(),
                    )
                    Text(
                        text = String.format("%.0f%%", accuracy * 100),
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            when {
                                accuracy >= 0.7f -> KBTheme.mastered()
                                accuracy >= 0.5f -> KBTheme.intermediate()
                                else -> KBTheme.beginner()
                            },
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "+${session.totalPoints}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = KBTheme.gold(),
                )
            }
        }
    }
}

// endregion

// region Empty State

@Composable
private fun EmptyStatsCard() {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = KBTheme.bgSecondary(),
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = KBTheme.textSecondary(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.kb_no_stats_yet),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = KBTheme.textPrimary(),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.kb_start_practicing_to_see_stats),
                style = MaterialTheme.typography.bodyMedium,
                color = KBTheme.textSecondary(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// endregion

// region Helpers

private fun formatTimestamp(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}

// endregion

// region ViewModel

@dagger.hilt.android.lifecycle.HiltViewModel
class StatsScreenViewModel
    @javax.inject.Inject
    constructor(
        val statsManager: KBStatsManager,
    ) : androidx.lifecycle.ViewModel()

// endregion
