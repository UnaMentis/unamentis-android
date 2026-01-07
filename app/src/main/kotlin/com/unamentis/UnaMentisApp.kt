package com.unamentis

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Main application class for UnaMentis.
 *
 * This class serves as the entry point for the application and initializes
 * dependency injection via Hilt.
 */
@HiltAndroidApp
class UnaMentisApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Application-level initialization will be added here
        // e.g., RemoteLogger setup, WorkManager configuration, etc.
    }
}
