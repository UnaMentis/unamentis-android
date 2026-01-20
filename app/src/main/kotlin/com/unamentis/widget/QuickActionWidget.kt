package com.unamentis.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.unamentis.MainActivity
import com.unamentis.R

/**
 * Quick Action Widget using Jetpack Glance.
 *
 * Provides quick access to common actions:
 * - Start a new voice session
 * - Resume learning
 * - View analytics/progress
 * - View to-do list
 *
 * This mirrors the iOS App Shortcuts functionality.
 */
class QuickActionWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            GlanceTheme {
                QuickActionWidgetContent()
            }
        }
    }

    @Composable
    private fun QuickActionWidgetContent() {
        val context = LocalContext.current

        Box(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .background(GlanceTheme.colors.widgetBackground)
                    .cornerRadius(16.dp)
                    .padding(12.dp),
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
            ) {
                // Header
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_shortcut_mic),
                        contentDescription = context.getString(R.string.app_name),
                        modifier = GlanceModifier.size(20.dp),
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = context.getString(R.string.widget_quick_actions_title),
                        style =
                            TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                    )
                }

                Spacer(modifier = GlanceModifier.height(12.dp))

                // Action buttons grid (2x2)
                Column(
                    modifier = GlanceModifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                    ) {
                        ActionButton(
                            icon = R.drawable.ic_shortcut_mic,
                            label = context.getString(R.string.shortcut_start_session_short),
                            deepLink = "unamentis://session",
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        ActionButton(
                            icon = R.drawable.ic_shortcut_play,
                            label = context.getString(R.string.shortcut_resume_short),
                            deepLink = "unamentis://session?resume=true",
                            modifier = GlanceModifier.defaultWeight(),
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                    ) {
                        ActionButton(
                            icon = R.drawable.ic_shortcut_analytics,
                            label = context.getString(R.string.shortcut_progress_short),
                            deepLink = "unamentis://analytics",
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        ActionButton(
                            icon = R.drawable.ic_shortcut_checklist,
                            label = context.getString(R.string.shortcut_todos_short),
                            deepLink = "unamentis://todo",
                            modifier = GlanceModifier.defaultWeight(),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ActionButton(
        icon: Int,
        label: String,
        @Suppress("unused") deepLink: String,
        modifier: GlanceModifier = GlanceModifier,
    ) {
        val context = LocalContext.current

        Box(
            modifier =
                modifier
                    .background(GlanceTheme.colors.secondaryContainer)
                    .cornerRadius(12.dp)
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    provider = ImageProvider(icon),
                    contentDescription = label,
                    modifier = GlanceModifier.size(24.dp),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = label,
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.onSecondaryContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                )
            }
        }
    }
}
