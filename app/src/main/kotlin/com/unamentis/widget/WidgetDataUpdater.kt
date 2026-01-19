package com.unamentis.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service to update widget data from app state.
 *
 * Call this when:
 * - Session completes
 * - Topic progress changes
 * - User opens analytics
 * - App starts
 *
 * Mirrors iOS App Intents entity refresh.
 */
@Singleton
class WidgetDataUpdater
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * Update all widget data from current app state.
         *
         * Fetches latest progress data and updates both
         * ProgressWidget and QuickActionWidget.
         */
        suspend fun updateWidgetData(
            totalTopics: Int = 0,
            completedTopics: Int = 0,
            inProgressTopics: Int = 0,
            overallMastery: Float = 0f,
            totalSessionMinutes: Long = 0L,
            lastTopicTitle: String? = null,
            lastTopicId: String? = null,
            lastCurriculumName: String? = null,
            streakDays: Int = 0,
        ) {
            // Update DataStore
            context.widgetDataStore.edit { prefs ->
                prefs[WidgetDataKeys.TOTAL_TOPICS] = totalTopics
                prefs[WidgetDataKeys.COMPLETED_TOPICS] = completedTopics
                prefs[WidgetDataKeys.IN_PROGRESS_TOPICS] = inProgressTopics
                prefs[WidgetDataKeys.OVERALL_MASTERY] = overallMastery
                prefs[WidgetDataKeys.TOTAL_SESSION_MINUTES] = totalSessionMinutes
                lastTopicTitle?.let { prefs[WidgetDataKeys.LAST_TOPIC_TITLE] = it }
                lastTopicId?.let { prefs[WidgetDataKeys.LAST_TOPIC_ID] = it }
                lastCurriculumName?.let { prefs[WidgetDataKeys.LAST_CURRICULUM_NAME] = it }
                prefs[WidgetDataKeys.STREAK_DAYS] = streakDays
                prefs[WidgetDataKeys.LAST_UPDATED] = System.currentTimeMillis()
            }

            // Update all widget instances
            refreshAllWidgets()
        }

        /**
         * Update widget data from sessions.
         *
         * Convenience method that calculates streak from session history.
         */
        suspend fun updateFromSessions(sessions: List<com.unamentis.data.local.entity.SessionEntity>) {
            val totalMinutes = sessions.sumOf { it.durationSeconds / 60 }

            // Calculate streak (simplified: consecutive days with sessions)
            val streakDays = calculateStreak(sessions)

            updateWidgetData(
                totalSessionMinutes = totalMinutes,
                streakDays = streakDays,
            )
        }

        /**
         * Calculate learning streak (consecutive days with sessions).
         */
        private fun calculateStreak(sessions: List<com.unamentis.data.local.entity.SessionEntity>): Int {
            if (sessions.isEmpty()) return 0

            // Group sessions by day
            val calendar = java.util.Calendar.getInstance()
            val sessionDays =
                sessions
                    .map { session ->
                        calendar.timeInMillis = session.startTime
                        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                        calendar.set(java.util.Calendar.MINUTE, 0)
                        calendar.set(java.util.Calendar.SECOND, 0)
                        calendar.set(java.util.Calendar.MILLISECOND, 0)
                        calendar.timeInMillis
                    }.distinct()
                    .sortedDescending()

            if (sessionDays.isEmpty()) return 0

            // Check if today or yesterday has a session
            val today = java.util.Calendar.getInstance()
            today.set(java.util.Calendar.HOUR_OF_DAY, 0)
            today.set(java.util.Calendar.MINUTE, 0)
            today.set(java.util.Calendar.SECOND, 0)
            today.set(java.util.Calendar.MILLISECOND, 0)
            val todayMillis = today.timeInMillis

            val yesterday = todayMillis - 24 * 60 * 60 * 1000

            // If no session today or yesterday, streak is 0
            if (sessionDays[0] != todayMillis && sessionDays[0] != yesterday) {
                return 0
            }

            // Count consecutive days
            var streak = 1
            var expectedDay = sessionDays[0] - 24 * 60 * 60 * 1000

            for (i in 1 until sessionDays.size) {
                if (sessionDays[i] == expectedDay) {
                    streak++
                    expectedDay -= 24 * 60 * 60 * 1000
                } else {
                    break
                }
            }

            return streak
        }

        /**
         * Force refresh all widget instances.
         */
        suspend fun refreshAllWidgets() {
            try {
                // Update ProgressWidget instances
                val progressManager = GlanceAppWidgetManager(context)
                val progressWidgetIds = progressManager.getGlanceIds(ProgressWidget::class.java)
                for (id in progressWidgetIds) {
                    updateAppWidgetState(context, id) { prefs ->
                        // Copy from widget DataStore
                        val data = context.widgetDataStore.data.first()
                        data.asMap().forEach { (key, value) ->
                            @Suppress("UNCHECKED_CAST")
                            when (value) {
                                is Int ->
                                    prefs[key as androidx.datastore.preferences.core.Preferences.Key<Int>] =
                                        value
                                is Float ->
                                    prefs[key as androidx.datastore.preferences.core.Preferences.Key<Float>] =
                                        value
                                is Long ->
                                    prefs[key as androidx.datastore.preferences.core.Preferences.Key<Long>] =
                                        value
                                is String ->
                                    prefs[key as androidx.datastore.preferences.core.Preferences.Key<String>] =
                                        value
                            }
                        }
                    }
                }
                ProgressWidget().updateAll(context)

                // Update QuickActionWidget instances
                QuickActionWidget().updateAll(context)
            } catch (e: Exception) {
                // Widget update failures shouldn't crash the app
                android.util.Log.w("WidgetDataUpdater", "Failed to update widgets", e)
            }
        }
    }
