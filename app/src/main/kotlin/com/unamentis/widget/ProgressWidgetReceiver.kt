package com.unamentis.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * DataStore for widget data sharing between app and widgets.
 */
val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "widget_data",
)

/**
 * Widget data keys for sharing state between app and widgets.
 */
object WidgetDataKeys {
    val TOTAL_TOPICS = intPreferencesKey("total_topics")
    val COMPLETED_TOPICS = intPreferencesKey("completed_topics")
    val IN_PROGRESS_TOPICS = intPreferencesKey("in_progress_topics")
    val OVERALL_MASTERY = floatPreferencesKey("overall_mastery")
    val TOTAL_SESSION_MINUTES = longPreferencesKey("total_session_minutes")
    val LAST_TOPIC_TITLE = stringPreferencesKey("last_topic_title")
    val LAST_TOPIC_ID = stringPreferencesKey("last_topic_id")
    val LAST_CURRICULUM_NAME = stringPreferencesKey("last_curriculum_name")
    val STREAK_DAYS = intPreferencesKey("streak_days")
    val LAST_UPDATED = longPreferencesKey("last_updated")
}

/**
 * Receiver for the Progress Widget.
 *
 * This widget displays learning progress statistics:
 * - Overall mastery percentage
 * - Topics completed / total
 * - Current streak
 * - Quick action to resume learning
 */
class ProgressWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProgressWidget()
}
