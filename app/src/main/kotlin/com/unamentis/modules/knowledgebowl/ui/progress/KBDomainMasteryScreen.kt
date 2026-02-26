package com.unamentis.modules.knowledgebowl.ui.progress

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.R
import com.unamentis.modules.knowledgebowl.core.stats.KBAnalyticsService
import com.unamentis.modules.knowledgebowl.core.stats.MasteryLevel
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.ui.theme.KBTheme
import com.unamentis.modules.knowledgebowl.ui.theme.color
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Domain Mastery screen.
 */
@HiltViewModel
class KBDomainMasteryViewModel
    @Inject
    constructor(
        private val analyticsService: KBAnalyticsService,
    ) : ViewModel() {
        private val _domains = MutableStateFlow<List<DomainMasteryDetail>>(emptyList())
        val domains: StateFlow<List<DomainMasteryDetail>> = _domains.asStateFlow()

        private val _isLoading = MutableStateFlow(true)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        init {
            loadData()
        }

        @Suppress("TooGenericExceptionCaught")
        private fun loadData() {
            viewModelScope.launch {
                try {
                    val performance = analyticsService.getDomainPerformance()
                    val mastery = analyticsService.getDomainMastery()

                    _domains.value =
                        KBDomain.entries.map { domain ->
                            val analytics = performance[domain]
                            DomainMasteryDetail(
                                domain = domain,
                                mastery = mastery[domain] ?: MasteryLevel.NOT_STARTED,
                                totalQuestions = analytics?.totalQuestions ?: 0,
                                accuracy = analytics?.accuracy ?: 0.0,
                                averageResponseTime = analytics?.averageResponseTime ?: 0.0,
                            )
                        }
                } catch (_: Exception) {
                    _domains.value = KBDomain.entries.map { DomainMasteryDetail(domain = it) }
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

/**
 * Detail data for a single domain's mastery.
 */
data class DomainMasteryDetail(
    val domain: KBDomain,
    val mastery: MasteryLevel = MasteryLevel.NOT_STARTED,
    val totalQuestions: Int = 0,
    val accuracy: Double = 0.0,
    val averageResponseTime: Double = 0.0,
)

/**
 * Domain mastery screen showing a 3-column grid with detailed domain progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KBDomainMasteryScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: KBDomainMasteryViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val domains by viewModel.domains.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var selectedDomain by remember { mutableStateOf<KBDomain?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.kb_domain_mastery)) },
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
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            ) {
                // 3-column grid of domains
                LazyVerticalGrid(
                    columns = GridCells.Fixed(GRID_COLUMNS),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(domains) { detail ->
                        DomainCell(
                            detail = detail,
                            isSelected = selectedDomain == detail.domain,
                            onClick = {
                                selectedDomain =
                                    if (selectedDomain == detail.domain) null else detail.domain
                            },
                        )
                    }
                }

                // Detail card for selected domain
                val selected = selectedDomain
                if (selected != null) {
                    val detail = domains.find { it.domain == selected }
                    if (detail != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        DomainDetailCard(detail)
                    }
                }
            }
        }
    }
}

private const val GRID_COLUMNS = 3

@Composable
private fun DomainCell(
    detail: DomainMasteryDetail,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val desc = "${detail.domain.displayName}: ${(detail.accuracy * 100).toInt()}%"
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .semantics { contentDescription = desc },
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
        border =
            if (isSelected) {
                androidx.compose.foundation.BorderStroke(2.dp, detail.domain.color())
            } else {
                null
            },
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Circular progress indicator
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { detail.accuracy.toFloat() },
                    modifier = Modifier.size(48.dp),
                    color = detail.domain.color(),
                    trackColor = KBTheme.border(),
                    strokeWidth = 4.dp,
                )
                Text(
                    text = "${(detail.accuracy * 100).toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = detail.domain.displayName,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )

            Text(
                text = masteryLabel(detail.mastery),
                style = MaterialTheme.typography.labelSmall,
                color = KBTheme.textSecondary(),
            )
        }
    }
}

@Composable
private fun masteryLabel(level: MasteryLevel): String =
    when (level) {
        MasteryLevel.NOT_STARTED -> stringResource(R.string.kb_mastery_not_started)
        MasteryLevel.BEGINNER -> stringResource(R.string.kb_mastery_beginner)
        MasteryLevel.INTERMEDIATE -> stringResource(R.string.kb_mastery_intermediate)
        MasteryLevel.ADVANCED -> stringResource(R.string.kb_mastery_advanced)
        MasteryLevel.MASTERED -> stringResource(R.string.kb_mastery_mastered)
    }

@Composable
private fun DomainDetailCard(detail: DomainMasteryDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with domain color dot
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(detail.domain.color()),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = detail.domain.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = masteryLabel(detail.mastery),
                    style = MaterialTheme.typography.labelMedium,
                    color = detail.domain.color(),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                DetailStat(
                    value = detail.totalQuestions.toString(),
                    label = stringResource(R.string.kb_progress_questions_answered),
                )
                DetailStat(
                    value = "${(detail.accuracy * 100).toInt()}%",
                    label = stringResource(R.string.kb_progress_overall_accuracy),
                )
                DetailStat(
                    value = String.format("%.1fs", detail.averageResponseTime),
                    label = stringResource(R.string.kb_progress_avg_time),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { detail.accuracy.toFloat() },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                color = detail.domain.color(),
            )
        }
    }
}

@Composable
private fun DetailStat(
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = KBTheme.textSecondary(),
        )
    }
}
