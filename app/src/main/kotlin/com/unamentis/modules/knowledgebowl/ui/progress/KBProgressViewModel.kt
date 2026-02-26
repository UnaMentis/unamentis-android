package com.unamentis.modules.knowledgebowl.ui.progress

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.modules.knowledgebowl.core.stats.DateAccuracy
import com.unamentis.modules.knowledgebowl.core.stats.DomainAnalytics
import com.unamentis.modules.knowledgebowl.core.stats.KBAnalyticsService
import com.unamentis.modules.knowledgebowl.core.stats.KBSessionStore
import com.unamentis.modules.knowledgebowl.core.stats.MasteryLevel
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the KB Progress dashboard.
 */
@HiltViewModel
class KBProgressViewModel
    @Inject
    constructor(
        private val analyticsService: KBAnalyticsService,
        private val sessionStore: KBSessionStore,
    ) : ViewModel() {
        companion object {
            private const val TAG = "KBProgressVM"
        }

        private val _uiState = MutableStateFlow(KBProgressUiState())
        val uiState: StateFlow<KBProgressUiState> = _uiState.asStateFlow()

        init {
            loadData()
        }

        fun refresh() {
            loadData()
        }

        @Suppress("TooGenericExceptionCaught")
        private fun loadData() {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true)

                try {
                    val stats = sessionStore.calculateStatistics()
                    val domainPerformance = analyticsService.getDomainPerformance()
                    val mastery = analyticsService.getDomainMastery()
                    val trend = analyticsService.getAccuracyTrend()
                    val insights = analyticsService.generateInsights()
                    val recentSessions = sessionStore.loadRecent(limit = 5)

                    val topDomains =
                        domainPerformance
                            .filter { it.value.totalQuestions > 0 }
                            .entries
                            .sortedByDescending { it.value.accuracy }
                            .take(TOP_DOMAINS_COUNT)
                            .map { DomainMasteryItem(it.key, mastery[it.key] ?: MasteryLevel.NOT_STARTED, it.value) }

                    _uiState.value =
                        KBProgressUiState(
                            isLoading = false,
                            totalSessions = stats.totalSessions,
                            totalQuestions = stats.totalQuestions,
                            overallAccuracy = stats.overallAccuracy,
                            currentStreak = stats.currentStreak,
                            accuracyTrend = trend,
                            topDomains = topDomains,
                            recentSessions = recentSessions,
                            insightCount = insights.size,
                        )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load progress data: ${e.message}", e)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

private const val TOP_DOMAINS_COUNT = 5

/**
 * UI state for the progress dashboard.
 */
data class KBProgressUiState(
    val isLoading: Boolean = true,
    val totalSessions: Int = 0,
    val totalQuestions: Int = 0,
    val overallAccuracy: Double = 0.0,
    val currentStreak: Int = 0,
    val accuracyTrend: List<DateAccuracy> = emptyList(),
    val topDomains: List<DomainMasteryItem> = emptyList(),
    val recentSessions: List<KBSession> = emptyList(),
    val insightCount: Int = 0,
)

/**
 * A domain with its mastery level and analytics.
 */
data class DomainMasteryItem(
    val domain: KBDomain,
    val mastery: MasteryLevel,
    val analytics: DomainAnalytics,
)
