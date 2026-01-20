package com.unamentis

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.unamentis.core.module.ModuleProtocol
import com.unamentis.core.module.ModuleRegistry
import com.unamentis.service.NotificationHelper
import com.unamentis.service.TodoReminderWorker
import com.unamentis.shortcuts.ShortcutsManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Main application class for UnaMentis.
 *
 * This class serves as the entry point for the application and initializes
 * dependency injection via Hilt.
 */
@HiltAndroidApp
class UnaMentisApp : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var shortcutsManager: ShortcutsManager

    @Inject
    lateinit var moduleRegistry: ModuleRegistry

    @Inject
    lateinit var moduleImplementations: Set<@JvmSuppressWildcards ModuleProtocol>

    override fun onCreate() {
        super.onCreate()

        // Create notification channels
        NotificationHelper.createNotificationChannels(this)

        // Schedule periodic todo reminders
        TodoReminderWorker.schedulePeriodicReminders(this)

        // Publish dynamic shortcuts for Google Assistant and launcher
        shortcutsManager.publishShortcuts()

        // Register all module implementations with the registry
        moduleImplementations.forEach { module ->
            moduleRegistry.registerImplementation(module)
        }
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()
}
