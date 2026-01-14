package com.unamentis.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.unamentis.MainActivity
import com.unamentis.R
import com.unamentis.core.session.SessionManager
import com.unamentis.data.model.SessionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

/**
 * Foreground service for voice sessions.
 *
 * Keeps the app alive during voice conversations to prevent
 * interruption when the app goes to background.
 *
 * Features:
 * - Persistent notification with session controls
 * - Real-time state updates in notification
 * - Pause/Resume/Stop actions from notification
 * - Auto-stop when session ends
 *
 * Usage:
 * - Start with startForegroundService(context, SessionForegroundService.start(context))
 * - Service auto-stops when session ends or user stops it
 */
@AndroidEntryPoint
class SessionForegroundService : Service() {
    @Inject
    lateinit var sessionManager: SessionManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val notificationManager: NotificationManager by lazy {
        getSystemService()!!
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification(SessionState.IDLE))
                observeSessionState()
            }
            ACTION_PAUSE -> {
                serviceScope.launch {
                    sessionManager.pauseSession()
                }
            }
            ACTION_RESUME -> {
                serviceScope.launch {
                    sessionManager.resumeSession()
                }
            }
            ACTION_STOP -> {
                serviceScope.launch {
                    sessionManager.stopSession()
                }
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    /**
     * Observe session state and update notification.
     */
    private fun observeSessionState() {
        serviceScope.launch {
            sessionManager.sessionState.collectLatest { state ->
                // Update notification with current state
                val notification = createNotification(state)
                notificationManager.notify(NOTIFICATION_ID, notification)

                // Auto-stop service when session ends
                if (state == SessionState.IDLE) {
                    delay(1000) // Give user time to see final state
                    stopSelf()
                }
            }
        }
    }

    /**
     * Create notification for current session state.
     */
    private fun createNotification(state: SessionState): Notification {
        val openAppIntent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        val openAppPendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // TODO: Create proper notification icon
                .setContentTitle("UnaMentis Session")
                .setContentText(getStateMessage(state))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(openAppPendingIntent)

        // Add action buttons based on state
        when (state) {
            SessionState.IDLE, SessionState.ERROR -> {
                // No actions in idle/error state
            }
            SessionState.PAUSED -> {
                builder.addAction(
                    R.drawable.ic_launcher_foreground, // TODO: Create proper icons
                    "Resume",
                    createActionPendingIntent(ACTION_RESUME),
                )
                builder.addAction(
                    R.drawable.ic_launcher_foreground,
                    "Stop",
                    createActionPendingIntent(ACTION_STOP),
                )
            }
            else -> {
                builder.addAction(
                    R.drawable.ic_launcher_foreground,
                    "Pause",
                    createActionPendingIntent(ACTION_PAUSE),
                )
                builder.addAction(
                    R.drawable.ic_launcher_foreground,
                    "Stop",
                    createActionPendingIntent(ACTION_STOP),
                )
            }
        }

        return builder.build()
    }

    /**
     * Create PendingIntent for action button.
     */
    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent =
            Intent(this, SessionForegroundService::class.java).apply {
                this.action = action
            }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Get user-friendly message for session state.
     */
    private fun getStateMessage(state: SessionState): String {
        return when (state) {
            SessionState.IDLE -> "Session idle"
            SessionState.USER_SPEAKING -> "Listening..."
            SessionState.PROCESSING_UTTERANCE -> "Processing..."
            SessionState.AI_THINKING -> "Thinking..."
            SessionState.AI_SPEAKING -> "Speaking..."
            SessionState.INTERRUPTED -> "Interrupted"
            SessionState.PAUSED -> "Session paused"
            SessionState.ERROR -> "Session error"
        }
    }

    /**
     * Create notification channel (Android 8+).
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Voice Sessions",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Ongoing voice learning sessions"
                    setShowBadge(false)
                }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "session_channel"
        private const val NOTIFICATION_ID = 1

        private const val ACTION_START = "com.unamentis.action.START_SESSION"
        private const val ACTION_PAUSE = "com.unamentis.action.PAUSE_SESSION"
        private const val ACTION_RESUME = "com.unamentis.action.RESUME_SESSION"
        private const val ACTION_STOP = "com.unamentis.action.STOP_SESSION"

        /**
         * Create intent to start the foreground service.
         */
        fun start(context: Context): Intent {
            return Intent(context, SessionForegroundService::class.java).apply {
                action = ACTION_START
            }
        }

        /**
         * Create intent to stop the foreground service.
         */
        fun stop(context: Context): Intent {
            return Intent(context, SessionForegroundService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}
