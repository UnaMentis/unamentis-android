package com.unamentis.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.core.telemetry.TelemetryEngine
import com.unamentis.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Convert epoch milliseconds to LocalDate using compatible API.
 */
private fun epochMillisToLocalDate(epochMillis: Long): LocalDate {
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

/**
 * ViewModel for the Analytics screen.
 *
 * Responsibilities:
 * - Aggregate session metrics
 * - Calculate cost breakdowns
 * - Generate latency statistics
 * - Provide data for charts
 * - Export metrics
 *
 * @property sessionRepository Repository for session data
 * @property telemetryEngine Engine for metrics aggregation
 */
@HiltViewModel
class AnalyticsViewModel
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
        private val telemetryEngine: TelemetryEngine,
    ) : ViewModel() {
        /**
         * Selected time range.
         */
        private val _timeRange = MutableStateFlow(TimeRange.LAST_7_DAYS)
        val timeRange: StateFlow<TimeRange> = _timeRange.asStateFlow()

        /**
         * Loading state.
         */
        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        /**
         * Quick stats (aggregated metrics).
         */
        private val quickStats: StateFlow<QuickStats> =
            combine(
                timeRange,
                sessionRepository.getAllSessions(),
            ) { range, sessions ->
                val filtered =
                    sessions.filter { session ->
                        val sessionDate =
                            epochMillisToLocalDate(session.startTime)
                        isInRange(sessionDate, range)
                    }

                val totalTurns = filtered.sumOf { it.turnCount }
                val avgE2ELatency =
                    filtered
                        .flatMap { telemetryEngine.getSessionMetrics(it.id) }
                        .map { it.e2eLatency }
                        .filter { it > 0 }
                        .average()
                        .takeIf { !it.isNaN() } ?: 0.0

                val totalCost =
                    filtered
                        .flatMap { telemetryEngine.getSessionMetrics(it.id) }
                        .sumOf { it.estimatedCost }

                QuickStats(
                    totalSessions = filtered.size,
                    totalTurns = totalTurns,
                    avgE2ELatency = avgE2ELatency.toInt(),
                    totalCost = totalCost,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = QuickStats(),
            )

        /**
         * Latency breakdown by type.
         */
        private val latencyBreakdown: StateFlow<LatencyBreakdown> =
            combine(
                timeRange,
                sessionRepository.getAllSessions(),
            ) { range, sessions ->
                val filtered =
                    sessions.filter { session ->
                        val sessionDate =
                            epochMillisToLocalDate(session.startTime)
                        isInRange(sessionDate, range)
                    }

                val metrics = filtered.flatMap { telemetryEngine.getSessionMetrics(it.id) }

                val avgSTT =
                    metrics.map { it.sttLatency }.filter { it > 0 }.average()
                        .takeIf { !it.isNaN() } ?: 0.0
                val avgLLM_TTFT =
                    metrics.map { it.llmTTFT }.filter { it > 0 }.average()
                        .takeIf { !it.isNaN() } ?: 0.0
                val avgTTS_TTFB =
                    metrics.map { it.ttsTTFB }.filter { it > 0 }.average()
                        .takeIf { !it.isNaN() } ?: 0.0
                val avgE2E =
                    metrics.map { it.e2eLatency }.filter { it > 0 }.average()
                        .takeIf { !it.isNaN() } ?: 0.0

                LatencyBreakdown(
                    avgSTT = avgSTT.toInt(),
                    avgLLM_TTFT = avgLLM_TTFT.toInt(),
                    avgTTS_TTFB = avgTTS_TTFB.toInt(),
                    avgE2E = avgE2E.toInt(),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = LatencyBreakdown(),
            )

        /**
         * Cost breakdown by provider type.
         */
        private val costBreakdown: StateFlow<CostBreakdown> =
            combine(
                timeRange,
                sessionRepository.getAllSessions(),
            ) { range, sessions ->
                val filtered =
                    sessions.filter { session ->
                        val sessionDate =
                            epochMillisToLocalDate(session.startTime)
                        isInRange(sessionDate, range)
                    }

                // Use telemetry engine's aggregated cost breakdown by provider type
                val sessionIds = filtered.map { it.id }
                val breakdown = telemetryEngine.getAggregatedCostBreakdown(sessionIds)

                CostBreakdown(
                    sttCost = breakdown.sttCost,
                    ttsCost = breakdown.ttsCost,
                    llmCost = breakdown.llmCost,
                    totalCost = breakdown.totalCost,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = CostBreakdown(),
            )

        /**
         * Detailed provider breakdown with individual provider costs.
         */
        private val providerBreakdown: StateFlow<List<ProviderCostItem>> =
            combine(
                timeRange,
                sessionRepository.getAllSessions(),
            ) { range, sessions ->
                val filtered =
                    sessions.filter { session ->
                        val sessionDate =
                            epochMillisToLocalDate(session.startTime)
                        isInRange(sessionDate, range)
                    }

                val sessionIds = filtered.map { it.id }
                telemetryEngine.getAggregatedProviderBreakdown(sessionIds).map { cost ->
                    ProviderCostItem(
                        providerName = cost.providerName,
                        providerType = cost.providerType,
                        totalCost = cost.totalCost,
                        requestCount = cost.requestCount,
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

        /**
         * Session history trends (last 30 days).
         */
        private val sessionTrends: StateFlow<List<DailyStats>> =
            combine(
                sessionRepository.getAllSessions(),
                timeRange,
            ) { sessions, range ->
                val days =
                    when (range) {
                        TimeRange.LAST_7_DAYS -> 7
                        TimeRange.LAST_30_DAYS -> 30
                        TimeRange.LAST_90_DAYS -> 90
                        TimeRange.ALL_TIME -> 365
                    }

                val today = LocalDate.now()

                (0 until days).map { daysAgo ->
                    val date = today.minusDays(daysAgo.toLong())
                    val sessionsOnDate =
                        sessions.filter { session ->
                            val sessionDate = epochMillisToLocalDate(session.startTime)
                            sessionDate == date
                        }

                    DailyStats(
                        date = date,
                        sessionCount = sessionsOnDate.size,
                        totalTurns = sessionsOnDate.sumOf { it.turnCount },
                    )
                }.reversed()
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

        /**
         * Combined UI state.
         */
        val uiState: StateFlow<AnalyticsUiState> =
            combine(
                timeRange,
                quickStats,
                latencyBreakdown,
                combine(costBreakdown, providerBreakdown) { cost, providers -> cost to providers },
                sessionTrends,
            ) { range, stats, latency, (cost, providers), trends ->
                AnalyticsUiState(
                    timeRange = range,
                    quickStats = stats,
                    latencyBreakdown = latency,
                    costBreakdown = cost,
                    providerBreakdown = providers,
                    sessionTrends = trends,
                    isLoading = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = AnalyticsUiState(),
            )

        /**
         * Set time range filter.
         */
        fun setTimeRange(range: TimeRange) {
            _timeRange.value = range
        }

        /**
         * Export metrics to JSON.
         */
        fun exportMetrics(): String {
            val state = uiState.value
            return buildString {
                appendLine("{")
                appendLine("  \"timeRange\": \"${state.timeRange}\",")
                appendLine("  \"quickStats\": {")
                appendLine("    \"totalSessions\": ${state.quickStats.totalSessions},")
                appendLine("    \"totalTurns\": ${state.quickStats.totalTurns},")
                appendLine("    \"avgE2ELatency\": ${state.quickStats.avgE2ELatency},")
                appendLine("    \"totalCost\": ${state.quickStats.totalCost}")
                appendLine("  },")
                appendLine("  \"latencyBreakdown\": {")
                appendLine("    \"avgSTT\": ${state.latencyBreakdown.avgSTT},")
                appendLine("    \"avgLLM_TTFT\": ${state.latencyBreakdown.avgLLM_TTFT},")
                appendLine("    \"avgTTS_TTFB\": ${state.latencyBreakdown.avgTTS_TTFB},")
                appendLine("    \"avgE2E\": ${state.latencyBreakdown.avgE2E}")
                appendLine("  },")
                appendLine("  \"costBreakdown\": {")
                appendLine("    \"sttCost\": ${state.costBreakdown.sttCost},")
                appendLine("    \"ttsCost\": ${state.costBreakdown.ttsCost},")
                appendLine("    \"llmCost\": ${state.costBreakdown.llmCost},")
                appendLine("    \"totalCost\": ${state.costBreakdown.totalCost}")
                appendLine("  },")
                appendLine("  \"providerBreakdown\": [")
                state.providerBreakdown.forEachIndexed { index, provider ->
                    val comma = if (index < state.providerBreakdown.lastIndex) "," else ""
                    appendLine("    {")
                    appendLine("      \"provider\": \"${provider.providerName}\",")
                    appendLine("      \"type\": \"${provider.providerType}\",")
                    appendLine("      \"cost\": ${provider.totalCost},")
                    appendLine("      \"requests\": ${provider.requestCount}")
                    appendLine("    }$comma")
                }
                appendLine("  ]")
                append("}")
            }
        }

        /**
         * Check if date is in the selected range.
         */
        private fun isInRange(
            date: LocalDate,
            range: TimeRange,
        ): Boolean {
            val today = LocalDate.now()
            return when (range) {
                TimeRange.LAST_7_DAYS -> date.isAfter(today.minusDays(7))
                TimeRange.LAST_30_DAYS -> date.isAfter(today.minusDays(30))
                TimeRange.LAST_90_DAYS -> date.isAfter(today.minusDays(90))
                TimeRange.ALL_TIME -> true
            }
        }
    }

/**
 * Time range filter options.
 */
enum class TimeRange {
    LAST_7_DAYS,
    LAST_30_DAYS,
    LAST_90_DAYS,
    ALL_TIME,
}

/**
 * Quick stats summary.
 */
data class QuickStats(
    val totalSessions: Int = 0,
    val totalTurns: Int = 0,
    /** milliseconds */
    val avgE2ELatency: Int = 0,
    /** USD */
    val totalCost: Double = 0.0,
)

/**
 * Latency breakdown by component.
 */
data class LatencyBreakdown(
    /** milliseconds */
    val avgSTT: Int = 0,
    /** milliseconds */
    val avgLLM_TTFT: Int = 0,
    /** milliseconds */
    val avgTTS_TTFB: Int = 0,
    /** milliseconds */
    val avgE2E: Int = 0,
)

/**
 * Cost breakdown by provider type.
 */
data class CostBreakdown(
    val sttCost: Double = 0.0,
    val ttsCost: Double = 0.0,
    val llmCost: Double = 0.0,
    val totalCost: Double = 0.0,
)

/**
 * Daily stats for trend chart.
 */
data class DailyStats(
    val date: LocalDate,
    val sessionCount: Int,
    val totalTurns: Int,
)

/**
 * UI state for Analytics screen.
 */
data class AnalyticsUiState(
    val timeRange: TimeRange = TimeRange.LAST_7_DAYS,
    val quickStats: QuickStats = QuickStats(),
    val latencyBreakdown: LatencyBreakdown = LatencyBreakdown(),
    val costBreakdown: CostBreakdown = CostBreakdown(),
    val providerBreakdown: List<ProviderCostItem> = emptyList(),
    val sessionTrends: List<DailyStats> = emptyList(),
    val isLoading: Boolean = false,
)

/**
 * Cost breakdown for a specific provider.
 */
data class ProviderCostItem(
    val providerName: String,
    val providerType: String,
    val totalCost: Double,
    val requestCount: Int,
)
