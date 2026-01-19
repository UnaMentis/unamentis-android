package com.unamentis.core.module

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Protocol/interface that all specialized modules must implement.
 *
 * A module is a self-contained learning experience with its own UI,
 * configuration, and functionality. Examples include:
 * - Knowledge Bowl (academic competition training)
 * - Speed Reading (comprehension exercises)
 * - Language Practice (vocabulary drills)
 *
 * ## Lifecycle
 * 1. Module is discovered via [ModuleRegistry]
 * 2. User selects module from module browser
 * 3. [initialize] is called to prepare resources
 * 4. [getUIEntryPoint] provides the main UI
 * 5. [pause]/[resume] handle lifecycle events
 * 6. [stop] is called when user exits module
 *
 * ## Example Implementation
 * ```kotlin
 * class KnowledgeBowlModule @Inject constructor(
 *     private val questionEngine: KBQuestionEngine
 * ) : ModuleProtocol {
 *     override val moduleId = "knowledge-bowl"
 *     override val moduleName = "Knowledge Bowl"
 *     override val moduleVersion = "1.0.0"
 *
 *     override suspend fun initialize() {
 *         questionEngine.loadQuestions()
 *     }
 *
 *     override fun getUIEntryPoint(): @Composable () -> Unit = {
 *         KBDashboardScreen()
 *     }
 * }
 * ```
 */
interface ModuleProtocol {
    /**
     * Unique identifier for this module.
     *
     * Used for storage, navigation, and server communication.
     * Should be lowercase with hyphens (e.g., "knowledge-bowl").
     */
    val moduleId: String

    /**
     * Human-readable display name.
     */
    val moduleName: String

    /**
     * Semantic version string (e.g., "1.0.0").
     */
    val moduleVersion: String

    /**
     * Short description for module browser (1-2 sentences).
     */
    val shortDescription: String
        get() = ""

    /**
     * Detailed description for module detail view.
     */
    val longDescription: String
        get() = ""

    /**
     * Material icon name for display (e.g., "school", "quiz").
     *
     * Should be a valid Material Icons name.
     */
    val iconName: String
        get() = "extension"

    /**
     * Theme color for the module.
     *
     * Used for accent colors in the module's UI.
     */
    val themeColor: Color
        get() = Color(0xFF6200EE) // Default purple

    /**
     * Whether this module supports team/collaborative mode.
     */
    val supportsTeamMode: Boolean
        get() = false

    /**
     * Whether this module supports speed training/timed challenges.
     */
    val supportsSpeedTraining: Boolean
        get() = false

    /**
     * Whether this module supports competition simulation.
     */
    val supportsCompetitionSim: Boolean
        get() = false

    /**
     * Initialize the module and load required resources.
     *
     * Called before the module UI is displayed. Use this to:
     * - Load question banks or content
     * - Initialize services
     * - Prepare cached data
     *
     * @throws ModuleInitializationException if initialization fails
     */
    suspend fun initialize()

    /**
     * Start the module session.
     *
     * Called when the user enters the module.
     */
    suspend fun start() {
        // Default no-op
    }

    /**
     * Pause the module (e.g., app backgrounded).
     *
     * Save any state that needs to be preserved.
     */
    suspend fun pause() {
        // Default no-op
    }

    /**
     * Resume the module from paused state.
     */
    suspend fun resume() {
        // Default no-op
    }

    /**
     * Stop the module and release resources.
     *
     * Called when the user exits the module.
     */
    suspend fun stop() {
        // Default no-op
    }

    /**
     * Get the main UI entry point for this module.
     *
     * This composable will be displayed when the user
     * navigates to this module.
     *
     * @return Composable function for the module's main screen
     */
    @Composable
    fun getUIEntryPoint()

    /**
     * Get an optional configuration/settings screen.
     *
     * @return Composable for settings, or null if no settings
     */
    @Composable
    fun getConfigurationScreen() {
        // Default no settings screen
    }

    /**
     * Get a summary widget for the main dashboard.
     *
     * This is displayed in the Learning tab to show module status.
     *
     * @return Composable for dashboard widget, or null for default
     */
    @Composable
    fun getDashboardWidget() {
        // Default no widget
    }
}

/**
 * Exception thrown when module initialization fails.
 *
 * @property moduleId ID of the module that failed
 * @property reason Reason for the failure
 */
class ModuleInitializationException(
    val moduleId: String,
    val reason: String,
    cause: Throwable? = null,
) : Exception("Module '$moduleId' failed to initialize: $reason", cause)
