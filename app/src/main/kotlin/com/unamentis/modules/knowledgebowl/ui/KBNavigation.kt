package com.unamentis.modules.knowledgebowl.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBSessionConfig
import com.unamentis.modules.knowledgebowl.data.model.KBStudyMode
import com.unamentis.modules.knowledgebowl.ui.dashboard.KBDashboardScreen
import com.unamentis.modules.knowledgebowl.ui.dashboard.KBDashboardViewModel
import com.unamentis.modules.knowledgebowl.ui.oral.KBOralSessionScreen
import com.unamentis.modules.knowledgebowl.ui.session.KBPracticeSessionScreen
import com.unamentis.modules.knowledgebowl.ui.settings.KBSettingsScreen
import com.unamentis.modules.knowledgebowl.ui.written.KBWrittenSessionScreen

/**
 * Navigation destinations within the Knowledge Bowl module.
 */
sealed class KBDestination {
    data object Dashboard : KBDestination()

    data object WrittenSession : KBDestination()

    data class OralSession(
        val questions: List<KBQuestion>,
        val config: KBSessionConfig,
    ) : KBDestination()

    data object Settings : KBDestination()

    data class PracticeSession(
        val mode: KBStudyMode,
        val questions: List<KBQuestion>,
    ) : KBDestination()
}

/**
 * Main navigation host for the Knowledge Bowl module.
 *
 * Handles all internal navigation between KB screens:
 * - Dashboard (entry point)
 * - Written Session (legacy)
 * - Oral Session (legacy)
 * - Practice Session (unified, new)
 * - Settings
 *
 * @param onBack Callback when user wants to exit the module
 */
@Suppress("UnusedParameter")
@Composable
fun KBNavigationHost(onBack: () -> Unit = {}) {
    var currentDestination by remember { mutableStateOf<KBDestination>(KBDestination.Dashboard) }

    // Get a shared dashboard ViewModel for state needed across navigation
    val dashboardViewModel: KBDashboardViewModel = hiltViewModel()
    val selectedRegion by dashboardViewModel.selectedRegion.collectAsState()

    when (val destination = currentDestination) {
        is KBDestination.Dashboard -> {
            KBDashboardScreen(
                onNavigateToWrittenSession = {
                    currentDestination = KBDestination.WrittenSession
                },
                onNavigateToOralSession = {
                    // Get questions and config from the ViewModel for oral session
                    val questions = dashboardViewModel.getQuestionsForOral()
                    val config = KBSessionConfig.oralPractice(selectedRegion)
                    currentDestination = KBDestination.OralSession(questions, config)
                },
                onNavigateToSettings = {
                    currentDestination = KBDestination.Settings
                },
                onNavigateToPracticeSession = { mode, questions ->
                    currentDestination = KBDestination.PracticeSession(mode, questions)
                },
                viewModel = dashboardViewModel,
            )
        }

        is KBDestination.WrittenSession -> {
            KBWrittenSessionScreen(
                onNavigateBack = { currentDestination = KBDestination.Dashboard },
            )
        }

        is KBDestination.OralSession -> {
            KBOralSessionScreen(
                questions = destination.questions,
                config = destination.config,
                onNavigateBack = { currentDestination = KBDestination.Dashboard },
            )
        }

        is KBDestination.Settings -> {
            KBSettingsScreen(
                selectedRegion = selectedRegion,
                onRegionSelected = { dashboardViewModel.selectRegion(it) },
                onResetStats = { dashboardViewModel.resetStats() },
                onNavigateBack = { currentDestination = KBDestination.Dashboard },
            )
        }

        is KBDestination.PracticeSession -> {
            KBPracticeSessionScreen(
                mode = destination.mode,
                questions = destination.questions,
                onComplete = { _ -> currentDestination = KBDestination.Dashboard },
                onBack = { currentDestination = KBDestination.Dashboard },
            )
        }
    }
}
