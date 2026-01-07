package com.unamentis.ui.analytics

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.unamentis.data.model.SessionMetrics
import com.unamentis.ui.theme.UnaMentisTheme
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for AnalyticsScreen.
 *
 * Tests metrics display, charts, filtering, and export functionality.
 */
class AnalyticsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testMetrics = SessionMetrics(
        sessionId = "session-1",
        timestamp = System.currentTimeMillis(),
        e2eLatencyMs = 450L,
        sttLatencyMs = 80L,
        llmTTFTMs = 150L,
        ttsTTFBMs = 120L,
        totalCostCents = 2.5,
        sttCostCents = 0.5,
        llmCostCents = 1.5,
        ttsCostCents = 0.5,
        turnCount = 42,
        totalDurationMs = 1800000L // 30 minutes
    )

    @Test
    fun analyticsScreen_initialState_displaysQuickStats() {
        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen()
            }
        }

        // Verify quick stats section is displayed
        composeTestRule.onNodeWithText("Quick Stats").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_quickStats_showsLatency() {
        val metrics = listOf(testMetrics)

        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen(metrics = metrics)
            }
        }

        // Verify latency stats are displayed
        composeTestRule.onNodeWithText("Avg E2E Latency").assertIsDisplayed()
        composeTestRule.onNodeWithText("450ms").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_quickStats_showsCost() {
        val metrics = listOf(testMetrics)

        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen(metrics = metrics)
            }
        }

        // Verify cost stats are displayed
        composeTestRule.onNodeWithText("Total Cost").assertIsDisplayed()
        composeTestRule.onNodeWithText("$0.03").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_quickStats_showsTurnCount() {
        val metrics = listOf(testMetrics)

        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen(metrics = metrics)
            }
        }

        // Verify turn count is displayed
        composeTestRule.onNodeWithText("Total Turns").assertIsDisplayed()
        composeTestRule.onNodeWithText("42").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_latencyChart_displaysBreakdown() {
        val metrics = listOf(testMetrics)

        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen(metrics = metrics)
            }
        }

        // Verify latency breakdown section exists
        composeTestRule.onNodeWithText("Latency Breakdown").assertIsDisplayed()
        composeTestRule.onNodeWithText("STT: 80ms").assertIsDisplayed()
        composeTestRule.onNodeWithText("LLM TTFT: 150ms").assertIsDisplayed()
        composeTestRule.onNodeWithText("TTS TTFB: 120ms").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_costChart_displaysBreakdown() {
        val metrics = listOf(testMetrics)

        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen(metrics = metrics)
            }
        }

        // Verify cost breakdown section exists
        composeTestRule.onNodeWithText("Cost Breakdown").assertIsDisplayed()
        composeTestRule.onNodeWithText("STT").assertIsDisplayed()
        composeTestRule.onNodeWithText("LLM").assertIsDisplayed()
        composeTestRule.onNodeWithText("TTS").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_timeRangeFilter_updatesMetrics() {
        var filterChanged = false
        val metrics = listOf(testMetrics)

        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen(
                    metrics = metrics,
                    onTimeRangeChange = { filterChanged = true }
                )
            }
        }

        // Click time range dropdown
        composeTestRule.onNodeWithContentDescription("Time range filter").performClick()

        // Select "Last 7 days"
        composeTestRule.onNodeWithText("Last 7 days").performClick()

        // Verify filter was applied
        assert(filterChanged)
    }

    @Test
    fun analyticsScreen_sessionHistory_displaysTrend() {
        val multipleMetrics = List(10) { index ->
            testMetrics.copy(
                sessionId = "session-$index",
                timestamp = System.currentTimeMillis() - (index * 86400000L), // Days apart
                e2eLatencyMs = 400L + (index * 10L)
            )
        }

        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen(metrics = multipleMetrics)
            }
        }

        // Verify trend chart section exists
        composeTestRule.onNodeWithText("Session History").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Latency trend chart").assertExists()
    }

    @Test
    fun analyticsScreen_exportButton_triggersExport() {
        var exportTriggered = false
        val metrics = listOf(testMetrics)

        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen(
                    metrics = metrics,
                    onExport = { exportTriggered = true }
                )
            }
        }

        // Click export button
        composeTestRule.onNodeWithText("Export").performClick()

        // Verify export was triggered
        assert(exportTriggered)
    }

    @Test
    fun analyticsScreen_exportFormat_showsOptions() {
        val metrics = listOf(testMetrics)

        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen(metrics = metrics)
            }
        }

        // Click export button
        composeTestRule.onNodeWithText("Export").performClick()

        // Verify format options are displayed
        composeTestRule.onNodeWithText("CSV").assertIsDisplayed()
        composeTestRule.onNodeWithText("JSON").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_emptyState_displaysMessage() {
        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen(metrics = emptyList())
            }
        }

        // Verify empty state is displayed
        composeTestRule.onNodeWithText("No analytics data yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Complete a session to see metrics").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_percentileStats_showsP50AndP99() {
        val multipleMetrics = List(100) { index ->
            testMetrics.copy(
                sessionId = "session-$index",
                e2eLatencyMs = 300L + (index * 5L) // 300ms to 795ms
            )
        }

        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen(metrics = multipleMetrics)
            }
        }

        // Verify percentile stats are displayed
        composeTestRule.onNodeWithText("P50 Latency").assertIsDisplayed()
        composeTestRule.onNodeWithText("P99 Latency").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_performanceTarget_showsIndicator() {
        val fastMetrics = testMetrics.copy(e2eLatencyMs = 400L) // Under 500ms target
        val slowMetrics = testMetrics.copy(
            sessionId = "session-2",
            e2eLatencyMs = 600L // Over 500ms target
        )

        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen(metrics = listOf(fastMetrics, slowMetrics))
            }
        }

        // Verify target indicator is shown
        composeTestRule.onNodeWithText("Target: <500ms").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_costPerTurn_calculatesAverage() {
        val metrics = listOf(testMetrics) // 2.5 cents / 42 turns = 0.06 cents per turn

        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen(metrics = metrics)
            }
        }

        // Verify cost per turn is calculated and displayed
        composeTestRule.onNodeWithText("Cost per Turn").assertIsDisplayed()
        composeTestRule.onNodeWithText("$0.0006").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_providerBreakdown_showsUsage() {
        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen(
                    metrics = listOf(testMetrics),
                    providerUsage = mapOf(
                        "Deepgram" to 45,
                        "ElevenLabs" to 32,
                        "OpenAI" to 23
                    )
                )
            }
        }

        // Verify provider usage is displayed
        composeTestRule.onNodeWithText("Provider Usage").assertIsDisplayed()
        composeTestRule.onNodeWithText("Deepgram: 45%").assertIsDisplayed()
        composeTestRule.onNodeWithText("ElevenLabs: 32%").assertIsDisplayed()
        composeTestRule.onNodeWithText("OpenAI: 23%").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_chartTypeSwitch_togglesVisualization() {
        var chartType = "bar"
        val metrics = listOf(testMetrics)

        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen(
                    metrics = metrics,
                    chartType = chartType,
                    onChartTypeChange = { chartType = it }
                )
            }
        }

        // Switch to line chart
        composeTestRule.onNodeWithContentDescription("Switch to line chart").performClick()

        // Verify chart type changed
        assert(chartType == "line")
    }

    @Test
    fun analyticsScreen_detailView_expandsMetrics() {
        val metrics = listOf(testMetrics)

        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen(metrics = metrics)
            }
        }

        // Click on a metric card to expand details
        composeTestRule.onNodeWithText("Latency Breakdown").performClick()

        // Verify detailed metrics are shown
        composeTestRule.onNodeWithText("Detailed Latency Analysis").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_scrolling_showsAllSections() {
        val metrics = listOf(testMetrics)

        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen(metrics = metrics)
            }
        }

        // Verify top section is visible
        composeTestRule.onNodeWithText("Quick Stats").assertIsDisplayed()

        // Scroll to bottom
        composeTestRule.onNodeWithText("Session History").performScrollTo()

        // Verify bottom section is now visible
        composeTestRule.onNodeWithText("Session History").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_refreshButton_updatesMetrics() {
        var refreshTriggered = false
        val metrics = listOf(testMetrics)

        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen(
                    metrics = metrics,
                    onRefresh = { refreshTriggered = true }
                )
            }
        }

        // Pull to refresh or click refresh button
        composeTestRule.onNodeWithContentDescription("Refresh analytics").performClick()

        // Verify refresh was triggered
        assert(refreshTriggered)
    }

    @Test
    fun analyticsScreen_accessibility_hasContentDescriptions() {
        val metrics = listOf(testMetrics)

        composeTestRule.setContent {
            UnaMentisTheme {
                AnalyticsScreen(metrics = metrics)
            }
        }

        // Verify accessibility for interactive elements
        composeTestRule.onNodeWithContentDescription("Time range filter").assertExists()
        composeTestRule.onNodeWithContentDescription("Latency trend chart").assertExists()
        composeTestRule.onNodeWithContentDescription("Refresh analytics").assertExists()
    }

    @Test
    fun analyticsScreen_darkMode_rendersCorrectly() {
        val metrics = listOf(testMetrics)

        composeTestRule.setContent {
            UnaMentisTheme(darkTheme = true) {
                AnalyticsScreen(metrics = metrics)
            }
        }

        // Verify screen renders in dark mode
        composeTestRule.onNodeWithText("Quick Stats").assertIsDisplayed()
        composeTestRule.onNodeWithText("450ms").assertIsDisplayed()
    }
}
