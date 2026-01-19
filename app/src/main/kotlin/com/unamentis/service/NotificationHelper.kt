package com.unamentis.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.unamentis.MainActivity
import com.unamentis.R

/**
 * Helper class for creating and managing notifications.
 *
 * Responsibilities:
 * - Create notification channels
 * - Show todo reminder notifications
 * - Show overdue todo notifications
 * - Handle notification permissions
 */
object NotificationHelper {
    // Notification channel IDs
    const val CHANNEL_TODO_REMINDERS = "todo_reminders"
    const val CHANNEL_TODO_OVERDUE = "todo_overdue"

    // Notification IDs
    private const val NOTIFICATION_ID_TODO_REMINDER_BASE = 10000
    private const val NOTIFICATION_ID_OVERDUE_SUMMARY = 20000

    /**
     * Create all required notification channels.
     * Should be called at app startup.
     */
    fun createNotificationChannels(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Todo reminder channel
        val reminderChannel =
            NotificationChannel(
                CHANNEL_TODO_REMINDERS,
                "Todo Reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications for upcoming todo due dates"
                enableVibration(true)
            }

        // Overdue channel (higher importance)
        val overdueChannel =
            NotificationChannel(
                CHANNEL_TODO_OVERDUE,
                "Overdue Todos",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notifications for overdue todos"
                enableVibration(true)
            }

        notificationManager.createNotificationChannels(
            listOf(reminderChannel, overdueChannel),
        )
    }

    /**
     * Check if notification permission is granted.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * Show a notification for an upcoming todo due date.
     *
     * @param context Application context
     * @param todoId Unique ID of the todo
     * @param title Todo title
     * @param timeUntilDue Human-readable time until due (e.g., "in 1 hour")
     */
    fun showReminderNotification(
        context: Context,
        todoId: String,
        title: String,
        timeUntilDue: String,
    ) {
        if (!hasNotificationPermission(context)) return

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("deepLink", "unamentis://todo")
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                todoId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(context, CHANNEL_TODO_REMINDERS)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Todo Due $timeUntilDue")
                .setContentText(title)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID_TODO_REMINDER_BASE + todoId.hashCode().mod(10000),
            notification,
        )
    }

    /**
     * Show a notification for an overdue todo.
     *
     * @param context Application context
     * @param todoId Unique ID of the todo
     * @param title Todo title
     * @param overdueBy Human-readable time overdue (e.g., "2 hours overdue")
     */
    fun showOverdueNotification(
        context: Context,
        todoId: String,
        title: String,
        overdueBy: String,
    ) {
        if (!hasNotificationPermission(context)) return

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("deepLink", "unamentis://todo")
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                todoId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(context, CHANNEL_TODO_OVERDUE)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Overdue: $title")
                .setContentText(overdueBy)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setColor(0xFFFF5252.toInt())
                .build()

        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID_TODO_REMINDER_BASE + todoId.hashCode().mod(10000),
            notification,
        )
    }

    /**
     * Show a summary notification for multiple overdue todos.
     *
     * @param context Application context
     * @param count Number of overdue todos
     */
    fun showOverdueSummaryNotification(
        context: Context,
        count: Int,
    ) {
        if (!hasNotificationPermission(context)) return
        if (count <= 0) {
            cancelOverdueSummaryNotification(context)
            return
        }

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("deepLink", "unamentis://todo")
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_OVERDUE_SUMMARY,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val title =
            if (count == 1) {
                "1 overdue todo"
            } else {
                "$count overdue todos"
            }

        val notification =
            NotificationCompat.Builder(context, CHANNEL_TODO_OVERDUE)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText("Tap to view your todo list")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setColor(0xFFFF5252.toInt())
                .setOnlyAlertOnce(true)
                .build()

        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID_OVERDUE_SUMMARY,
            notification,
        )
    }

    /**
     * Cancel the overdue summary notification.
     */
    fun cancelOverdueSummaryNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_OVERDUE_SUMMARY)
    }

    /**
     * Cancel all todo reminder notifications.
     */
    fun cancelAllReminderNotifications(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)
        // Cancel all notifications in the range we use for todos
        for (i in 0 until 10000) {
            notificationManager.cancel(NOTIFICATION_ID_TODO_REMINDER_BASE + i)
        }
        notificationManager.cancel(NOTIFICATION_ID_OVERDUE_SUMMARY)
    }

    /**
     * Format time duration for display.
     *
     * @param durationMs Duration in milliseconds
     * @return Human-readable duration string
     */
    fun formatDuration(durationMs: Long): String {
        val absDuration = kotlin.math.abs(durationMs)
        val minutes = absDuration / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> if (days == 1L) "1 day" else "$days days"
            hours > 0 -> if (hours == 1L) "1 hour" else "$hours hours"
            minutes > 0 -> if (minutes == 1L) "1 minute" else "$minutes minutes"
            else -> "now"
        }
    }
}
