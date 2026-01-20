package com.unamentis.data.local

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.unamentis.data.remote.AuthTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage for authentication tokens using EncryptedSharedPreferences.
 *
 * All tokens are stored encrypted using AES-256 GCM encryption with a master key
 * stored in the Android Keystore. This provides hardware-backed security on
 * supported devices.
 *
 * Token Lifecycle:
 * - Access token: Short-lived (1 hour), used for API requests
 * - Refresh token: Long-lived (30 days), used to get new access tokens
 *
 * Usage:
 * ```kotlin
 * // Store tokens after login
 * tokenStorage.storeTokens(authTokens)
 *
 * // Get access token for API request
 * val accessToken = tokenStorage.getAccessToken()
 *
 * // Check if token needs refresh (< 5 minutes remaining)
 * if (tokenStorage.shouldRefreshToken()) {
 *     val refreshToken = tokenStorage.getRefreshToken()
 *     // Refresh the token...
 * }
 *
 * // Clear tokens on logout
 * tokenStorage.clearTokens()
 * ```
 */
@Singleton
class SecureTokenStorage
    @Inject
    constructor(
        private val context: Context,
    ) {
        companion object {
            private const val TAG = "SecureTokenStorage"
            private const val PREFS_NAME = "unamentis_secure_prefs"
            private const val KEY_ACCESS_TOKEN = "access_token"
            private const val KEY_REFRESH_TOKEN = "refresh_token"
            private const val KEY_TOKEN_EXPIRY = "token_expiry"
            private const val KEY_USER_ID = "user_id"
            private const val KEY_USER_EMAIL = "user_email"
            private const val KEY_USER_NAME = "user_name"
            private const val KEY_DEVICE_ID = "device_id"

            // Refresh token 5 minutes before expiry
            private const val REFRESH_BUFFER_MS = 5 * 60 * 1000L
        }

        private val encryptedPrefs by lazy {
            try {
                val masterKey =
                    MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create encrypted prefs, falling back to regular prefs", e)
                // Fallback to regular SharedPreferences in case of keystore issues
                // This should only happen on very old or misconfigured devices
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }

        /**
         * Store authentication tokens securely.
         *
         * @param tokens Authentication tokens from login/register/refresh
         */
        suspend fun storeTokens(tokens: AuthTokens) =
            withContext(Dispatchers.IO) {
                val expiryTime = System.currentTimeMillis() + (tokens.expiresIn * 1000L)

                encryptedPrefs.edit().apply {
                    putString(KEY_ACCESS_TOKEN, tokens.accessToken)
                    putString(KEY_REFRESH_TOKEN, tokens.refreshToken)
                    putLong(KEY_TOKEN_EXPIRY, expiryTime)
                    apply()
                }

                Log.i(TAG, "Tokens stored, expiry: $expiryTime")
            }

        /**
         * Get the current access token.
         *
         * @return Access token or null if not stored
         */
        suspend fun getAccessToken(): String? =
            withContext(Dispatchers.IO) {
                encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
            }

        /**
         * Get the current refresh token.
         *
         * @return Refresh token or null if not stored
         */
        suspend fun getRefreshToken(): String? =
            withContext(Dispatchers.IO) {
                encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
            }

        /**
         * Get the token expiry time.
         *
         * @return Expiry timestamp in milliseconds, or 0 if not stored
         */
        suspend fun getTokenExpiry(): Long =
            withContext(Dispatchers.IO) {
                encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, 0L)
            }

        /**
         * Check if the access token should be refreshed.
         *
         * Returns true if:
         * - Token expiry is within 5 minutes
         * - Token expiry is unknown (0)
         *
         * @return true if token should be refreshed
         */
        suspend fun shouldRefreshToken(): Boolean =
            withContext(Dispatchers.IO) {
                val expiry = getTokenExpiry()
                if (expiry == 0L) return@withContext true

                val timeRemaining = expiry - System.currentTimeMillis()
                timeRemaining < REFRESH_BUFFER_MS
            }

        /**
         * Check if the access token is expired.
         *
         * @return true if token is expired
         */
        suspend fun isTokenExpired(): Boolean =
            withContext(Dispatchers.IO) {
                val expiry = getTokenExpiry()
                if (expiry == 0L) return@withContext true

                System.currentTimeMillis() >= expiry
            }

        /**
         * Check if user is logged in (has valid tokens).
         *
         * @return true if user has stored tokens
         */
        suspend fun isLoggedIn(): Boolean =
            withContext(Dispatchers.IO) {
                val accessToken = getAccessToken()
                val refreshToken = getRefreshToken()
                !accessToken.isNullOrEmpty() && !refreshToken.isNullOrEmpty()
            }

        /**
         * Store user information.
         *
         * @param userId User's unique identifier
         * @param email User's email address
         * @param name User's display name
         */
        suspend fun storeUserInfo(
            userId: String,
            email: String,
            name: String,
        ) = withContext(Dispatchers.IO) {
            encryptedPrefs.edit().apply {
                putString(KEY_USER_ID, userId)
                putString(KEY_USER_EMAIL, email)
                putString(KEY_USER_NAME, name)
                apply()
            }

            Log.i(TAG, "User info stored: $email")
        }

        /**
         * Get stored user ID.
         *
         * @return User ID or null if not stored
         */
        suspend fun getUserId(): String? =
            withContext(Dispatchers.IO) {
                encryptedPrefs.getString(KEY_USER_ID, null)
            }

        /**
         * Get stored user email.
         *
         * @return User email or null if not stored
         */
        suspend fun getUserEmail(): String? =
            withContext(Dispatchers.IO) {
                encryptedPrefs.getString(KEY_USER_EMAIL, null)
            }

        /**
         * Get stored user name.
         *
         * @return User name or null if not stored
         */
        suspend fun getUserName(): String? =
            withContext(Dispatchers.IO) {
                encryptedPrefs.getString(KEY_USER_NAME, null)
            }

        /**
         * Store device ID for this installation.
         *
         * The device ID is used to identify this device for session management.
         *
         * @param deviceId Unique device identifier
         */
        suspend fun storeDeviceId(deviceId: String) =
            withContext(Dispatchers.IO) {
                encryptedPrefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
            }

        /**
         * Get stored device ID.
         *
         * @return Device ID or null if not stored
         */
        suspend fun getDeviceId(): String? =
            withContext(Dispatchers.IO) {
                encryptedPrefs.getString(KEY_DEVICE_ID, null)
            }

        /**
         * Clear all stored tokens and user info.
         *
         * Call this on logout to remove all sensitive data.
         */
        suspend fun clearTokens() =
            withContext(Dispatchers.IO) {
                encryptedPrefs.edit().apply {
                    remove(KEY_ACCESS_TOKEN)
                    remove(KEY_REFRESH_TOKEN)
                    remove(KEY_TOKEN_EXPIRY)
                    remove(KEY_USER_ID)
                    remove(KEY_USER_EMAIL)
                    remove(KEY_USER_NAME)
                    // Note: Keep device ID across logins
                    apply()
                }

                Log.i(TAG, "Tokens and user info cleared")
            }

        /**
         * Clear all stored data including device ID.
         *
         * Call this for complete data wipe (e.g., uninstall, factory reset).
         */
        suspend fun clearAll() =
            withContext(Dispatchers.IO) {
                encryptedPrefs.edit().clear().apply()
                Log.i(TAG, "All secure storage cleared")
            }

        /**
         * Export tokens for debugging (access token is truncated).
         *
         * NEVER log full tokens in production.
         *
         * @return Debug string with truncated token info
         */
        suspend fun debugInfo(): String =
            withContext(Dispatchers.IO) {
                val accessToken = getAccessToken()
                val refreshToken = getRefreshToken()
                val expiry = getTokenExpiry()
                val userId = getUserId()

                buildString {
                    appendLine("SecureTokenStorage Debug Info:")
                    appendLine("  Access Token: ${accessToken?.take(10)}...${accessToken?.takeLast(5) ?: "null"}")
                    appendLine("  Refresh Token: ${if (refreshToken != null) "[present]" else "null"}")
                    appendLine("  Token Expiry: $expiry (${if (expiry > 0) java.util.Date(expiry) else "N/A"})")
                    appendLine("  User ID: $userId")
                    appendLine("  Should Refresh: ${shouldRefreshToken()}")
                    appendLine("  Is Expired: ${isTokenExpired()}")
                }
            }
    }
