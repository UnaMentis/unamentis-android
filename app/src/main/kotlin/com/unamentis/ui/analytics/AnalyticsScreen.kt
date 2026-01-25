package com.unamentis.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unamentis.R
import com.unamentis.ui.components.BrandLogo
import com.unamentis.ui.components.IOSCard
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import com.unamentis.ui.util.safeProgress
import com.unamentis.ui.util.safeProgressRatio
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.unamentis.ui.components.Size as LogoSize

/**
 * Analytics screen - Telemetry dashboard.
 *
 * Features:
 * - Quick stats cards
 * - Latency breakdown chart
 * - Cost breakdown pie chart
 * - Session history trends
 * - Export metrics functionality
 *
 * Layout:
 * - Time range selector
 * - Quick stats grid
 * - Charts section
 * - Export button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showExportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    BrandLogo(
                        size = LogoSize.Compact,
                        modifier = Modifier.padding(start = Dimensions.SpacingLarge),
                    )
                },
                title = { Text(stringResource(R.string.analytics_title)) },
                actions = {
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = stringResource(R.string.analytics_export_metrics),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .testTag("AnalyticsLazyColumn"),
            contentPadding =
                PaddingValues(
                    horizontal = Dimensions.ScreenHorizontalPadding,
                    vertical = Dimensions.ScreenVerticalPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingLarge),
        ) {
            // Time range selector
            item {
                TimeRangeSelector(
                    selectedRange = uiState.timeRange,
                    onRangeSelected = { viewModel.setTimeRange(it) },
                )
            }

            // Quick stats cards
            item {
                QuickStatsSection(stats = uiState.quickStats)
            }

            // Latency breakdown chart
            item {
                LatencyBreakdownCard(breakdown = uiState.latencyBreakdown)
            }

            // Cost breakdown chart
            item {
                CostBreakdownCard(breakdown = uiState.costBreakdown)
            }

            // Provider-specific breakdown
            item {
                ProviderBreakdownCard(providers = uiState.providerBreakdown)
            }

            // Session trends
            item {
                SessionTrendsCard(trends = uiState.sessionTrends)
            }
        }
    }

    // Export dialog
    if (showExportDialog) {
        ExportDialog(
            json = viewModel.exportMetrics(),
            onDismiss = { showExportDialog = false },
        )
    }
}

/**
 * Time range selector chips.
 */
@Composable
private fun TimeRangeSelector(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall)) {
        Text(
            text = stringResource(R.string.analytics_time_range),
            style = IOSTypography.headline,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        ) {
            TimeRange.entries.forEach { range ->
                FilterChip(
                    selected = selectedRange == range,
                    onClick = { onRangeSelected(range) },
                    label = {
                        Text(
                            text =
                                when (range) {
                                    TimeRange.LAST_7_DAYS -> stringResource(R.string.analytics_7_days)
                                    TimeRange.LAST_30_DAYS -> stringResource(R.string.analytics_30_days)
                                    TimeRange.LAST_90_DAYS -> stringResource(R.string.analytics_90_days)
                                    TimeRange.ALL_TIME -> stringResource(R.string.analytics_all_time)
                                },
                        )
                    },
                )
            }
        }
    }
}

/**
 * Quick stats cards section.
 */
@Composable
private fun QuickStatsSection(stats: QuickStats) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall)) {
        Text(
            text = stringResource(R.string.analytics_overview),
            style = IOSTypography.headline,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        ) {
            StatCard(
                title = stringResource(R.string.analytics_sessions),
                value = stats.totalSessions.toString(),
                icon = Icons.Default.PlayArrow,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = stringResource(R.string.analytics_turns),
                value = stats.totalTurns.toString(),
                icon = Icons.Default.Chat,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        ) {
            StatCard(
                title = stringResource(R.string.analytics_avg_latency),
                value = stringResource(R.string.analytics_latency_ms, stats.avgE2ELatency),
                icon = Icons.Default.Timer,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = stringResource(R.string.analytics_total_cost),
                value = NumberFormat.getCurrencyInstance(Locale.getDefault()).format(stats.totalCost),
                icon = Icons.Default.AttachMoney,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Individual stat card.
 */
@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    IOSCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(Dimensions.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimensions.IconSizeSmall),
                )
                Text(
                    text = title,
                    style = IOSTypography.caption2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value,
                style = IOSTypography.title2,
            )
        }
    }
}

/**
 * Latency breakdown bar chart card.
 */
@Composable
private fun LatencyBreakdownCard(breakdown: LatencyBreakdown) {
    IOSCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimensions.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            Text(
                text = stringResource(R.string.analytics_latency_breakdown),
                style = IOSTypography.headline,
            )

            // Bar chart
            BarChart(
                data =
                    listOf(
                        stringResource(R.string.analytics_label_stt) to breakdown.avgSTT,
                        stringResource(R.string.analytics_label_llm_ttft) to breakdown.avgLLM_TTFT,
                        stringResource(R.string.analytics_label_tts_ttfb) to breakdown.avgTTS_TTFB,
                        stringResource(R.string.analytics_label_e2e) to breakdown.avgE2E,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp),
            )
        }
    }
}

/**
 * Cost breakdown pie chart card.
 */
@Composable
private fun CostBreakdownCard(breakdown: CostBreakdown) {
    IOSCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimensions.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            Text(
                text = stringResource(R.string.analytics_cost_breakdown),
                style = IOSTypography.headline,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Pie chart
                PieChart(
                    data =
                        listOf(
                            stringResource(R.string.analytics_label_stt) to breakdown.sttCost,
                            stringResource(R.string.analytics_label_tts) to breakdown.ttsCost,
                            stringResource(R.string.analytics_label_llm) to breakdown.llmCost,
                        ),
                    modifier = Modifier.size(120.dp),
                )

                // Legend
                Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall)) {
                    CostLegendItem(
                        stringResource(R.string.analytics_label_stt),
                        breakdown.sttCost,
                        MaterialTheme.colorScheme.primary,
                    )
                    CostLegendItem(
                        stringResource(R.string.analytics_label_tts),
                        breakdown.ttsCost,
                        MaterialTheme.colorScheme.secondary,
                    )
                    CostLegendItem(
                        stringResource(R.string.analytics_label_llm),
                        breakdown.llmCost,
                        MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.analytics_total_cost),
                    style = IOSTypography.subheadline,
                )
                Text(
                    text = NumberFormat.getCurrencyInstance(Locale.getDefault()).format(breakdown.totalCost),
                    style = IOSTypography.subheadline,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Cost legend item.
 */
@Composable
private fun CostLegendItem(
    label: String,
    cost: Double,
    color: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(12.dp)
                    .background(color, RoundedCornerShape(2.dp)),
        )
        val formattedCost = NumberFormat.getCurrencyInstance(Locale.getDefault()).format(cost)
        Text(
            text = stringResource(R.string.analytics_cost_legend_format, label, formattedCost),
            style = IOSTypography.caption,
        )
    }
}

/**
 * Provider-specific breakdown card showing individual provider costs.
 */
@Composable
private fun ProviderBreakdownCard(providers: List<ProviderCostItem>) {
    IOSCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimensions.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            Text(
                text = stringResource(R.string.analytics_provider_details),
                style = IOSTypography.headline,
            )

            if (providers.isEmpty()) {
                Text(
                    text = stringResource(R.string.analytics_no_provider_data),
                    style = IOSTypography.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Dimensions.CardPadding),
                )
            } else {
                providers.forEach { provider ->
                    ProviderCostRow(provider = provider)
                }
            }
        }
    }
}

/**
 * Single row for provider cost information.
 */
@Composable
private fun ProviderCostRow(provider: ProviderCostItem) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Type indicator chip
            Surface(
                shape = MaterialTheme.shapes.small,
                color = getProviderTypeColor(provider.providerType),
            ) {
                Text(
                    text = provider.providerType,
                    style = IOSTypography.caption2,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            Column {
                Text(
                    text = provider.providerName,
                    style = IOSTypography.body,
                )
                Text(
                    text = stringResource(R.string.analytics_requests_count, provider.requestCount),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val formattedCost =
            NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
                minimumFractionDigits = 4
                maximumFractionDigits = 4
            }.format(provider.totalCost)
        Text(
            text = formattedCost,
            style = IOSTypography.subheadline,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Get color for provider type badge.
 */
@Composable
private fun getProviderTypeColor(type: String): Color {
    return when (type.uppercase()) {
        "STT" -> MaterialTheme.colorScheme.primary
        "TTS" -> MaterialTheme.colorScheme.secondary
        "LLM" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
}

/**
 * Session trends line chart card.
 */
@Composable
private fun SessionTrendsCard(trends: List<DailyStats>) {
    IOSCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimensions.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            Text(
                text = stringResource(R.string.analytics_session_trends),
                style = IOSTypography.headline,
            )

            if (trends.isEmpty()) {
                Text(
                    text = stringResource(R.string.analytics_no_data),
                    style = IOSTypography.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Dimensions.SpacingXXLarge),
                )
            } else {
                LineChart(
                    data = trends.map { it.sessionCount.toFloat() },
                    labels =
                        trends.map {
                            it.date.format(DateTimeFormatter.ofPattern("MM/dd"))
                        },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                )
            }
        }
    }
}

/**
 * Simple bar chart implementation.
 */
@Composable
private fun BarChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
) {
    val maxValue = data.maxOfOrNull { it.second }?.toFloat() ?: 1f

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        data.forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
            ) {
                Text(
                    text = label,
                    style = IOSTypography.caption2,
                    modifier = Modifier.width(70.dp),
                )

                LinearProgressIndicator(
                    progress = { safeProgressRatio(value, maxValue) },
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = stringResource(R.string.analytics_latency_ms, value),
                    style = IOSTypography.caption2,
                    modifier = Modifier.width(50.dp),
                )
            }
        }
    }
}

/**
 * Simple pie chart implementation.
 */
@Composable
private fun PieChart(
    data: List<Pair<String, Double>>,
    modifier: Modifier = Modifier,
) {
    val total = data.sumOf { it.second }.toFloat()
    val colors =
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
        )

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        var startAngle = -90f

        // Skip drawing if total is zero or invalid to avoid NaN
        if (total <= 0f || total.isNaN() || total.isInfinite()) {
            return@Canvas
        }

        data.forEachIndexed { index, (_, value) ->
            val sweepAngle = safeProgress(value.toFloat() / total) * 360f
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
            )
            startAngle += sweepAngle
        }

        // Draw white circle in center for donut effect
        drawCircle(
            color = Color.White,
            radius = radius * 0.5f,
            center = center,
        )
    }
}

/**
 * Simple line chart implementation.
 */
@Composable
private fun LineChart(
    data: List<Float>,
    @Suppress("unused") labels: List<String>,
    modifier: Modifier = Modifier,
) {
    // Use a safe max value to prevent division by zero
    val maxValue = (data.maxOrNull() ?: 1f).let { if (it <= 0f) 1f else it }

    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height
        val spacing = width / (data.size - 1).coerceAtLeast(1)

        // Draw line
        for (i in 0 until data.size - 1) {
            val x1 = i * spacing
            val y1 = height - safeProgressRatio(data[i], maxValue) * height
            val x2 = (i + 1) * spacing
            val y2 = height - safeProgressRatio(data[i + 1], maxValue) * height

            drawLine(
                color = androidx.compose.ui.graphics.Color.Blue,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
        }

        // Draw points
        data.forEachIndexed { index, value ->
            val x = index * spacing
            val y = height - safeProgressRatio(value, maxValue) * height
            drawCircle(
                color = androidx.compose.ui.graphics.Color.Blue,
                radius = 6f,
                center = Offset(x, y),
            )
        }
    }
}

/**
 * Export dialog showing JSON metrics.
 */
@Composable
private fun ExportDialog(
    json: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.analytics_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall)) {
                Text(
                    text = stringResource(R.string.analytics_json_format),
                    style = IOSTypography.caption,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimensions.CardCornerRadiusSmall),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = json,
                        style = IOSTypography.caption,
                        modifier = Modifier.padding(Dimensions.SpacingMedium),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.analytics_close))
            }
        },
    )
}
