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
import com.unamentis.data.model.TodoStatus
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
                Result.retry()
            }
        }

        private suspend fun checkTodosAndNotify() {
            val now = System.currentTimeMillis()
            val oneHour = 60 * 60 * 1000L
            val twentyFourHours = 24 * oneHour

            // Get all active todos with due dates
            val activeTodos =
                database.todoDao().getActiveTodosWithDueDates()

            var overdueCount = 0

            for (todo in activeTodos) {
                val dueDate = todo.dueDate ?: continue

                // Skip if already completed
                if (todo.status != TodoStatus.ACTIVE) continue

                val timeUntilDue = dueDate - now

                when {
                    // Overdue
                    timeUntilDue < 0 -> {
                        overdueCount++
                        // Only notify once per overdue period (check if we recently notified)
                        val lastNotified = getLastNotifiedTime(todo.id)
                        if (now - lastNotified > oneHour) {
                            NotificationHelper.showOverdueNotification(
                                context = applicationContext,
                                todoId = todo.id,
                                title = todo.title,
                                overdueBy = "${NotificationHelper.formatDuration(-timeUntilDue)} overdue",
                            )
                            setLastNotifiedTime(todo.id, now)
                        }
                    }

                    // Due within 1 hour
                    timeUntilDue in 0..oneHour -> {
                        val lastNotified = getLastNotifiedTime(todo.id)
                        // Only notify once for this threshold
                        if (lastNotified < dueDate - oneHour) {
                            NotificationHelper.showReminderNotification(
                                context = applicationContext,
                                todoId = todo.id,
                                title = todo.title,
                                timeUntilDue = "in ${NotificationHelper.formatDuration(timeUntilDue)}",
                            )
                            setLastNotifiedTime(todo.id, now)
                        }
                    }

                    // Due within 24 hours (notify once per day)
                    timeUntilDue in oneHour..twentyFourHours -> {
                        val lastNotified = getLastNotifiedTime(todo.id)
                        // Only notify once for the "due tomorrow" notification
                        if (lastNotified < dueDate - twentyFourHours) {
                            NotificationHelper.showReminderNotification(
                                context = applicationContext,
                                todoId = todo.id,
                                title = todo.title,
                                timeUntilDue = "tomorrow",
                            )
                            setLastNotifiedTime(todo.id, now)
                        }
                    }
                }
            }

            // Show summary notification for overdue todos
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
