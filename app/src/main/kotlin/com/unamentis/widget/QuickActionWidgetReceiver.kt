package com.unamentis.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Receiver for the Quick Action Widget.
 *
 * This widget provides quick action buttons:
 * - Start Session
 * - Resume Learning
 * - View Progress
 * - View To-Dos
 *
 * Similar to iOS App Shortcuts, but as a home screen widget.
 */
class QuickActionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionWidget()
}
