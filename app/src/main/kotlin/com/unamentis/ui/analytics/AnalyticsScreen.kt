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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unamentis.ui.components.IOSCard
import com.unamentis.ui.theme.Dimensions
import java.time.format.DateTimeFormatter

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
                title = { Text("Analytics") },
                actions = {
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.Download, contentDescription = "Export metrics")
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
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
            text = "Time Range",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
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
                                    TimeRange.LAST_7_DAYS -> "7 Days"
                                    TimeRange.LAST_30_DAYS -> "30 Days"
                                    TimeRange.LAST_90_DAYS -> "90 Days"
                                    TimeRange.ALL_TIME -> "All Time"
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
            text = "Overview",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        ) {
            StatCard(
                title = "Sessions",
                value = stats.totalSessions.toString(),
                icon = Icons.Default.PlayArrow,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "Turns",
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
                title = "Avg Latency",
                value = "${stats.avgE2ELatency}ms",
                icon = Icons.Default.Timer,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "Total Cost",
                value = "${'$'}${String.format("%.2f", stats.totalCost)}",
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
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
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
                text = "Latency Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            // Bar chart
            BarChart(
                data =
                    listOf(
                        "STT" to breakdown.avgSTT,
                        "LLM TTFT" to breakdown.avgLLM_TTFT,
                        "TTS TTFB" to breakdown.avgTTS_TTFB,
                        "E2E" to breakdown.avgE2E,
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
                text = "Cost Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
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
                            "STT" to breakdown.sttCost,
                            "TTS" to breakdown.ttsCost,
                            "LLM" to breakdown.llmCost,
                        ),
                    modifier = Modifier.size(120.dp),
                )

                // Legend
                Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall)) {
                    CostLegendItem("STT", breakdown.sttCost, MaterialTheme.colorScheme.primary)
                    CostLegendItem("TTS", breakdown.ttsCost, MaterialTheme.colorScheme.secondary)
                    CostLegendItem("LLM", breakdown.llmCost, MaterialTheme.colorScheme.tertiary)
                }
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Total Cost",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${'$'}${String.format("%.2f", breakdown.totalCost)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
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
        Text(
            text = "$label: ${'$'}${String.format("%.2f", cost)}",
            style = MaterialTheme.typography.bodySmall,
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
                text = "Provider Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            if (providers.isEmpty()) {
                Text(
                    text = "No provider data available",
                    style = MaterialTheme.typography.bodyMedium,
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
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            Column {
                Text(
                    text = provider.providerName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${provider.requestCount} requests",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = "${'$'}${String.format("%.4f", provider.totalCost)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
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
                text = "Session Trends",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            if (trends.isEmpty()) {
                Text(
                    text = "No data available",
                    style = MaterialTheme.typography.bodyMedium,
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
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(70.dp),
                )

                LinearProgressIndicator(
                    progress = { (value / maxValue).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = "${value}ms",
                    style = MaterialTheme.typography.labelSmall,
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

        data.forEachIndexed { index, (_, value) ->
            val sweepAngle = (value.toFloat() / total) * 360f
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
    val maxValue = data.maxOrNull() ?: 1f

    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height
        val spacing = width / (data.size - 1).coerceAtLeast(1)

        // Draw line
        for (i in 0 until data.size - 1) {
            val x1 = i * spacing
            val y1 = height - (data[i] / maxValue) * height
            val x2 = (i + 1) * spacing
            val y2 = height - (data[i + 1] / maxValue) * height

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
            val y = height - (value / maxValue) * height
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
        title = { Text("Export Metrics") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall)) {
                Text(
                    text = "Metrics in JSON format:",
                    style = MaterialTheme.typography.bodySmall,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimensions.CardCornerRadiusSmall),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = json,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(Dimensions.SpacingMedium),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
