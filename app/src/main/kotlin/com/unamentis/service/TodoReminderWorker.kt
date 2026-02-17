package com.unamentis.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.unamentis.data.local.AppDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Background worker that checks for upcoming and overdue todos
 * and shows reminder notifications.
 *
 * Runs periodically (every 15 minutes by default) to check due dates.
 *
 * Notification triggers:
 * - 1 hour before due: "Due in 1 hour" notification
 * - 24 hours before due: "Due tomorrow" notification
 * - When overdue: "Overdue" notification
 */
@HiltWorker
class TodoReminderWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val database: AppDatabase,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            return try {
                checkTodosAndNotify()
                Result.success()
            } catch (e: Exception) {
                // Log error but don't fail the work - we'll retry
                android.util.Log.w("TodoReminderWorker", "Error checking todos: ${e.message}", e)
                Result.retry()
            }
        }

        private suspend fun checkTodosAndNotify() {
            val now = System.currentTimeMillis()
            val activeTodos = database.todoDao().getActiveTodosWithDueDates()

            var overdueCount = 0
            for (todo in activeTodos) {
                val dueDate = todo.dueDate ?: continue
                if (!todo.status.isActive) continue

                val timeUntilDue = dueDate - now
                if (timeUntilDue < 0) overdueCount++

                checkAndNotifyTodo(todo.id, todo.title, dueDate, timeUntilDue, now)
            }

            showOrCancelOverdueSummary(overdueCount)
        }

        private fun checkAndNotifyTodo(
            todoId: String,
            title: String,
            dueDate: Long,
            timeUntilDue: Long,
            now: Long,
        ) {
            val oneHour = 60 * 60 * 1000L
            val twentyFourHours = 24 * oneHour

            when {
                timeUntilDue < 0 ->
                    notifyOverdueIfNeeded(todoId, title, timeUntilDue, now, oneHour)
                timeUntilDue in 0..oneHour ->
                    notifyDueSoonIfNeeded(todoId, title, dueDate, timeUntilDue, now, oneHour)
                timeUntilDue in oneHour..twentyFourHours ->
                    notifyDueTomorrowIfNeeded(todoId, title, dueDate, now, twentyFourHours)
            }
        }

        private fun notifyOverdueIfNeeded(
            todoId: String,
            title: String,
            timeUntilDue: Long,
            now: Long,
            oneHour: Long,
        ) {
            val lastNotified = getLastNotifiedTime(todoId)
            if (now - lastNotified > oneHour) {
                NotificationHelper.showOverdueNotification(
                    context = applicationContext,
                    todoId = todoId,
                    title = title,
                    overdueBy = "${NotificationHelper.formatDuration(-timeUntilDue)} overdue",
                )
                setLastNotifiedTime(todoId, now)
            }
        }

        private fun notifyDueSoonIfNeeded(
            todoId: String,
            title: String,
            dueDate: Long,
            timeUntilDue: Long,
            now: Long,
            oneHour: Long,
        ) {
            val lastNotified = getLastNotifiedTime(todoId)
            if (lastNotified < dueDate - oneHour) {
                NotificationHelper.showReminderNotification(
                    context = applicationContext,
                    todoId = todoId,
                    title = title,
                    timeUntilDue = "in ${NotificationHelper.formatDuration(timeUntilDue)}",
                )
                setLastNotifiedTime(todoId, now)
            }
        }

        private fun notifyDueTomorrowIfNeeded(
            todoId: String,
            title: String,
            dueDate: Long,
            now: Long,
            twentyFourHours: Long,
        ) {
            val lastNotified = getLastNotifiedTime(todoId)
            if (lastNotified < dueDate - twentyFourHours) {
                NotificationHelper.showReminderNotification(
                    context = applicationContext,
                    todoId = todoId,
                    title = title,
                    timeUntilDue = "tomorrow",
                )
                setLastNotifiedTime(todoId, now)
            }
        }

        private fun showOrCancelOverdueSummary(overdueCount: Int) {
            if (overdueCount > 0) {
                NotificationHelper.showOverdueSummaryNotification(
                    context = applicationContext,
                    count = overdueCount,
                )
            } else {
                NotificationHelper.cancelOverdueSummaryNotification(applicationContext)
            }
        }

        private fun getLastNotifiedTime(todoId: String): Long {
            val prefs =
                applicationContext.getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE,
                )
            return prefs.getLong("notified_$todoId", 0L)
        }

        private fun setLastNotifiedTime(
            todoId: String,
            time: Long,
        ) {
            val prefs =
                applicationContext.getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE,
                )
            prefs.edit().putLong("notified_$todoId", time).apply()
        }

        companion object {
            private const val PREFS_NAME = "todo_notifications"
            private const val WORK_NAME_PERIODIC = "todo_reminder_periodic"
            private const val WORK_NAME_IMMEDIATE = "todo_reminder_immediate"

            /**
             * Schedule periodic reminder checks.
             * Runs every 15 minutes to check for upcoming/overdue todos.
             */
            fun schedulePeriodicReminders(context: Context) {
                val constraints =
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()

                val workRequest =
                    PeriodicWorkRequestBuilder<TodoReminderWorker>(
                        15,
                        TimeUnit.MINUTES,
                    )
                        .setConstraints(constraints)
                        .build()

                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(
                        WORK_NAME_PERIODIC,
                        ExistingPeriodicWorkPolicy.KEEP,
                        workRequest,
                    )
            }

            /**
             * Run an immediate check for reminders.
             * Called when due dates are modified.
             */
            fun runImmediateCheck(context: Context) {
                val workRequest =
                    OneTimeWorkRequestBuilder<TodoReminderWorker>()
                        .build()

                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        WORK_NAME_IMMEDIATE,
                        ExistingWorkPolicy.REPLACE,
                        workRequest,
                    )
            }

            /**
             * Cancel all reminder checks.
             */
            fun cancelReminders(context: Context) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
            }

            /**
             * Clear notification history for a specific todo.
             * Called when a todo is completed or deleted.
             */
            fun clearNotificationHistory(
                context: Context,
                todoId: String,
            ) {
                val prefs =
                    context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE,
                    )
                prefs.edit().remove("notified_$todoId").apply()
            }
        }
    }
