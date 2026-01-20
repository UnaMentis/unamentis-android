package com.unamentis.modules.knowledgebowl

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.unamentis.core.module.ModuleProtocol
import com.unamentis.modules.knowledgebowl.core.engine.KBQuestionEngine
import com.unamentis.modules.knowledgebowl.ui.theme.KBColors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Knowledge Bowl module for academic competition training.
 *
 * This module provides:
 * - Written round practice (MCQ-style)
 * - Oral round practice (voice-based)
 * - Regional rule configurations (Colorado, Minnesota, Washington)
 * - Domain-based question filtering across 12 academic areas
 * - Progress tracking and statistics
 *
 * Supports team mode and competition simulation.
 */
@Singleton
class KnowledgeBowlModule
    @Inject
    constructor(
        private val questionEngine: KBQuestionEngine,
    ) : ModuleProtocol {
        override val moduleId: String = "knowledge-bowl"
        override val moduleName: String = "Knowledge Bowl"
        override val moduleVersion: String = "1.0.0"

        override val shortDescription: String =
            "Train for Knowledge Bowl competitions across 12 academic domains"

        override val longDescription: String =
            """
            Knowledge Bowl is an academic competition training module that helps students
            prepare for regional Knowledge Bowl competitions.

            Features:
            • Written Round Practice: Multiple-choice questions with timed sessions
            • Oral Round Practice: Voice-based Q&A with conference timers
            • 12 Academic Domains: Science, Math, Literature, History, and more
            • Regional Rules: Colorado, Minnesota, and Washington configurations
            • Progress Tracking: Domain mastery, accuracy, and response time stats
            • Competition Simulation: Practice under real competition conditions

            Perfect for individual study or team preparation.
            """.trimIndent()

        override val iconName: String = "school"

        override val themeColor: Color = KBColors.scienceLight

        override val supportsTeamMode: Boolean = true
        override val supportsSpeedTraining: Boolean = true
        override val supportsCompetitionSim: Boolean = true

        override suspend fun initialize() {
            // Load bundled questions
            questionEngine.loadBundledQuestions()
        }

        override suspend fun start() {
            // Module started - could log analytics here
        }

        override suspend fun pause() {
            // Module paused - save any in-progress session state
        }

        override suspend fun resume() {
            // Module resumed - restore session state if needed
        }

        override suspend fun stop() {
            // Module stopped - cleanup resources
        }

        @Composable
        override fun getUIEntryPoint() {
            // Note: Navigation callbacks will be provided by the NavHost when
            // this module is displayed. The module entry point is registered
            // in the app's navigation graph with proper callbacks.
            // This composable serves as a marker for module registration.
        }

        @Composable
        override fun getConfigurationScreen() {
            // Settings screen is accessed via navigation, not directly from module
        }

        @Composable
        override fun getDashboardWidget() {
            // Could add a summary widget showing recent stats
            // For now, use default (no widget)
        }
    }
