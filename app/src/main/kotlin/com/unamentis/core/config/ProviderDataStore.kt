package com.unamentis.core.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Singleton holder for the provider configuration DataStore.
 *
 * This ensures only one DataStore instance exists for the "provider_config" file,
 * preventing the "multiple DataStores active for same file" error that occurs
 * when multiple ProviderConfig instances are created (e.g., in tests).
 *
 * Usage:
 * - In Hilt modules, provide this singleton DataStore via dependency injection
 * - ProviderConfig receives the DataStore instance through its constructor
 */
object ProviderDataStore {
    /**
     * The singleton DataStore instance for provider configuration.
     *
     * This extension property on Context creates exactly one DataStore instance
     * for the "provider_config" file, regardless of how many times it's accessed.
     */
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "provider_config",
    )

    /**
     * Get the DataStore instance for the given context.
     *
     * @param context Application context (must be application context for singleton behavior)
     * @return The singleton DataStore instance
     */
    fun getInstance(context: Context): DataStore<Preferences> = context.dataStore
}
