package com.unamentis.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.unamentis.MainActivity
import com.unamentis.R

/**
 * Learning Progress Widget using Jetpack Glance.
 *
 * Displays:
 * - Overall mastery percentage with circular progress
 * - Topics completed / total
 * - Learning streak
 * - Quick action button to resume or start learning
 *
 * Sizes supported:
 * - Small (2x1): Mastery percentage only
 * - Medium (2x2): Full stats with progress
 * - Large (4x2): Full stats with recent topic and actions
 */
class ProgressWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            GlanceTheme {
                ProgressWidgetContent()
            }
        }
    }

    @Composable
    private fun ProgressWidgetContent() {
        val prefs = currentState<Preferences>()
        val context = LocalContext.current

        val totalTopics = prefs[WidgetDataKeys.TOTAL_TOPICS] ?: 0
        val completedTopics = prefs[WidgetDataKeys.COMPLETED_TOPICS] ?: 0
        val inProgressTopics = prefs[WidgetDataKeys.IN_PROGRESS_TOPICS] ?: 0
        val overallMastery = prefs[WidgetDataKeys.OVERALL_MASTERY] ?: 0f
        val totalMinutes = prefs[WidgetDataKeys.TOTAL_SESSION_MINUTES] ?: 0L
        val lastTopicTitle = prefs[WidgetDataKeys.LAST_TOPIC_TITLE]
        val streakDays = prefs[WidgetDataKeys.STREAK_DAYS] ?: 0

        val masteryPercent = (overallMastery * 100).toInt()
        val totalHours = totalMinutes / 60

        Box(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .background(GlanceTheme.colors.widgetBackground)
                    .cornerRadius(16.dp)
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.Start,
            ) {
                // Header row with app name and mastery
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_shortcut_analytics),
                        contentDescription = context.getString(R.string.app_name),
                        modifier = GlanceModifier.size(20.dp),
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = context.getString(R.string.app_name),
                        style =
                            TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "$masteryPercent%",
                        style =
                            TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                    )
                }

                Spacer(modifier = GlanceModifier.height(12.dp))

                // Stats row
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StatItem(
                        value = "$completedTopics/$totalTopics",
                        label = context.getString(R.string.widget_topics_label),
                    )
                    Spacer(modifier = GlanceModifier.width(16.dp))
                    StatItem(
                        value = "${totalHours}h",
                        label = context.getString(R.string.widget_time_label),
                    )
                    Spacer(modifier = GlanceModifier.width(16.dp))
                    StatItem(
                        value = "$streakDays",
                        label = context.getString(R.string.widget_streak_label),
                    )
                }

                Spacer(modifier = GlanceModifier.height(12.dp))

                // Last topic or action button
                if (lastTopicTitle != null && inProgressTopics > 0) {
                    // Resume learning button
                    Box(
                        modifier =
                            GlanceModifier
                                .fillMaxWidth()
                                .background(GlanceTheme.colors.primaryContainer)
                                .cornerRadius(8.dp)
                                .padding(8.dp)
                                .clickable(actionStartActivity<MainActivity>()),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_shortcut_play),
                                contentDescription = context.getString(R.string.shortcut_resume_short),
                                modifier = GlanceModifier.size(16.dp),
                            )
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Column {
                                Text(
                                    text = context.getString(R.string.widget_resume_label),
                                    style =
                                        TextStyle(
                                            color = GlanceTheme.colors.onPrimaryContainer,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                )
                                Text(
                                    text = lastTopicTitle.take(30) + if (lastTopicTitle.length > 30) "..." else "",
                                    style =
                                        TextStyle(
                                            color = GlanceTheme.colors.onPrimaryContainer,
                                            fontSize = 10.sp,
                                        ),
                                )
                            }
                        }
                    }
                } else {
                    // Start learning button
                    Box(
                        modifier =
                            GlanceModifier
                                .fillMaxWidth()
                                .background(GlanceTheme.colors.primary)
                                .cornerRadius(8.dp)
                                .padding(vertical = 10.dp)
                                .clickable(actionStartActivity<MainActivity>()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = context.getString(R.string.widget_start_learning),
                            style =
                                TextStyle(
                                    color = GlanceTheme.colors.onPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun StatItem(
        value: String,
        label: String,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
            )
            Text(
                text = label,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 10.sp,
                    ),
            )
        }
    }
}
