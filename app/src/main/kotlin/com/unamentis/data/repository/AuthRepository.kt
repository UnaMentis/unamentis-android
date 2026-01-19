package com.unamentis.data.repository

import android.os.Build
import android.util.Log
import com.unamentis.data.local.SecureTokenStorage
import com.unamentis.data.remote.ApiClient
import com.unamentis.data.remote.ApiResult
import com.unamentis.data.remote.ChangePasswordRequest
import com.unamentis.data.remote.Device
import com.unamentis.data.remote.LoginRequest
import com.unamentis.data.remote.RefreshTokenRequest
import com.unamentis.data.remote.RegisterRequest
import com.unamentis.data.remote.UpdateProfileRequest
import com.unamentis.data.remote.User
import com.unamentis.data.remote.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authentication state for the app.
 */
sealed class AuthState {
    /** User is not logged in. */
    object LoggedOut : AuthState()

    /** User is logged in with valid tokens. */
    data class LoggedIn(val user: User) : AuthState()

    /** Currently checking authentication status. */
    object Loading : AuthState()

    /** Authentication error occurred. */
    data class Error(val message: String) : AuthState()
}

/**
 * Repository managing user authentication state and token lifecycle.
 *
 * Responsibilities:
 * - User registration and login
 * - Token storage and refresh
 * - Session management
 * - Device registration
 *
 * Usage:
 * ```kotlin
 * // Login
 * val result = authRepository.login(email, password)
 * when (result) {
 *     is AuthResult.Success -> // Navigate to home
 *     is AuthResult.Error -> // Show error message
 * }
 *
 * // Check auth state
 * authRepository.authState.collect { state ->
 *     when (state) {
 *         is AuthState.LoggedIn -> // Show authenticated UI
 *         is AuthState.LoggedOut -> // Show login screen
 *     }
 * }
 *
 * // Provide token to ApiClient
 * val apiClient = ApiClient(
 *     tokenProvider = { authRepository.getAccessToken() },
 *     onTokenExpired = { authRepository.handleTokenExpired() }
 * )
 * ```
 */
@Singleton
class AuthRepository
    @Inject
    constructor(
        private val apiClient: ApiClient,
        private val tokenStorage: SecureTokenStorage,
    ) {
        companion object {
            private const val TAG = "AuthRepository"
        }

        private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
        val authState: StateFlow<AuthState> = _authState.asStateFlow()

        private val refreshMutex = Mutex()
        private var isRefreshing = false

        /**
         * Initialize auth state by checking stored tokens.
         *
         * Call this on app startup to restore authentication state.
         */
        suspend fun initialize() {
            Log.i(TAG, "Initializing auth repository")

            if (!tokenStorage.isLoggedIn()) {
                Log.i(TAG, "No stored tokens, user is logged out")
                _authState.value = AuthState.LoggedOut
                return
            }

            // Try to refresh if token is expired or close to expiring
            if (tokenStorage.shouldRefreshToken()) {
                Log.i(TAG, "Token needs refresh, attempting refresh")
                val refreshResult = refreshTokenIfNeeded()
                if (!refreshResult) {
                    Log.w(TAG, "Token refresh failed, clearing auth state")
                    logout()
                    return
                }
            }

            // Fetch current user profile
            when (val result = apiClient.getCurrentUser()) {
                is ApiResult.Success -> {
                    val user = result.data
                    tokenStorage.storeUserInfo(user.id, user.email, user.name)
                    _authState.value = AuthState.LoggedIn(user)
                    Log.i(TAG, "User restored: ${user.email}")
                }
                is ApiResult.Error -> {
                    if (result.httpCode == 401) {
                        Log.w(TAG, "Token invalid, clearing auth state")
                        logout()
                    } else {
                        Log.e(TAG, "Failed to fetch user: ${result.error.error}")
                        _authState.value = AuthState.Error(result.error.error)
                    }
                }
                is ApiResult.NetworkError -> {
                    // Network error but we have tokens, assume logged in
                    val userId = tokenStorage.getUserId()
                    val email = tokenStorage.getUserEmail()
                    val name = tokenStorage.getUserName()

                    if (userId != null && email != null && name != null) {
                        _authState.value =
                            AuthState.LoggedIn(
                                User(
                                    id = userId,
                                    email = email,
                                    name = name,
                                    createdAt = "",
                                    updatedAt = "",
                                ),
                            )
                        Log.i(TAG, "Offline mode, using cached user info")
                    } else {
                        _authState.value = AuthState.Error("Network error: ${result.message}")
                    }
                }
            }
        }

        /**
         * Register a new user account.
         *
         * @param email User's email address
         * @param password User's password (min 8 characters)
         * @param name User's display name
         * @return Registration result
         */
        suspend fun register(
            email: String,
            password: String,
            name: String,
        ): AuthResult {
            Log.i(TAG, "Registering user: $email")

            val request = RegisterRequest(email, password, name)
            return when (val result = apiClient.register(request)) {
                is ApiResult.Success -> {
                    val tokens = result.data
                    tokenStorage.storeTokens(tokens)
                    ensureDeviceId()

                    // Fetch user profile
                    when (val userResult = apiClient.getCurrentUser()) {
                        is ApiResult.Success -> {
                            val user = userResult.data
                            tokenStorage.storeUserInfo(user.id, user.email, user.name)
                            _authState.value = AuthState.LoggedIn(user)
                            Log.i(TAG, "Registration successful: ${user.email}")
                            AuthResult.Success(user)
                        }
                        else -> {
                            // Tokens stored but couldn't fetch profile
                            val user =
                                User(
                                    id = "",
                                    email = email,
                                    name = name,
                                    createdAt = "",
                                    updatedAt = "",
                                )
                            _authState.value = AuthState.LoggedIn(user)
                            AuthResult.Success(user)
                        }
                    }
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Registration failed: ${result.error.error}")
                    AuthResult.Error(result.error.error, result.error.code)
                }
                is ApiResult.NetworkError -> {
                    Log.e(TAG, "Registration network error: ${result.message}")
                    AuthResult.NetworkError(result.message)
                }
            }
        }

        /**
         * Login with email and password.
         *
         * @param email User's email address
         * @param password User's password
         * @return Login result
         */
        suspend fun login(
            email: String,
            password: String,
        ): AuthResult {
            Log.i(TAG, "Logging in user: $email")

            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            val request = LoginRequest(email, password, deviceName)

            return when (val result = apiClient.login(request)) {
                is ApiResult.Success -> {
                    val tokens = result.data
                    tokenStorage.storeTokens(tokens)
                    ensureDeviceId()

                    // Fetch user profile
                    when (val userResult = apiClient.getCurrentUser()) {
                        is ApiResult.Success -> {
                            val user = userResult.data
                            tokenStorage.storeUserInfo(user.id, user.email, user.name)
                            _authState.value = AuthState.LoggedIn(user)
                            Log.i(TAG, "Login successful: ${user.email}")
                            AuthResult.Success(user)
                        }
                        else -> {
                            val user =
                                User(
                                    id = "",
                                    email = email,
                                    name = "",
                                    createdAt = "",
                                    updatedAt = "",
                                )
                            _authState.value = AuthState.LoggedIn(user)
                            AuthResult.Success(user)
                        }
                    }
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Login failed: ${result.error.error}")
                    AuthResult.Error(result.error.error, result.error.code)
                }
                is ApiResult.NetworkError -> {
                    Log.e(TAG, "Login network error: ${result.message}")
                    AuthResult.NetworkError(result.message)
                }
            }
        }

        /**
         * Logout and revoke tokens.
         */
        suspend fun logout() {
            Log.i(TAG, "Logging out")

            // Try to revoke token on server (ignore errors)
            try {
                apiClient.logout()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to revoke token on server: ${e.message}")
            }

            // Clear local tokens
            tokenStorage.clearTokens()
            _authState.value = AuthState.LoggedOut

            Log.i(TAG, "Logout complete")
        }

        /**
         * Get current access token for API requests.
         *
         * Automatically refreshes if token is expired or close to expiring.
         *
         * @return Access token or null if not logged in
         */
        suspend fun getAccessToken(): String? {
            if (!tokenStorage.isLoggedIn()) {
                return null
            }

            // Refresh if needed
            if (tokenStorage.shouldRefreshToken()) {
                refreshTokenIfNeeded()
            }

            return tokenStorage.getAccessToken()
        }

        /**
         * Handle token expiration (called by ApiClient on 401).
         *
         * Attempts to refresh the token and re-authenticate.
         * If refresh fails, logs out the user.
         */
        suspend fun handleTokenExpired() {
            Log.w(TAG, "Token expired, attempting refresh")

            val refreshed = refreshTokenIfNeeded()
            if (!refreshed) {
                Log.e(TAG, "Token refresh failed after expiration")
                logout()
            }
        }

        /**
         * Refresh access token using refresh token.
         *
         * Uses a mutex to prevent concurrent refresh attempts.
         *
         * @return true if refresh succeeded
         */
        private suspend fun refreshTokenIfNeeded(): Boolean {
            return refreshMutex.withLock {
                if (isRefreshing) {
                    // Already refreshing in another coroutine
                    return@withLock true
                }

                isRefreshing = true
                try {
                    val refreshToken = tokenStorage.getRefreshToken()
                    if (refreshToken == null) {
                        Log.w(TAG, "No refresh token available")
                        return@withLock false
                    }

                    val request = RefreshTokenRequest(refreshToken)
                    when (val result = apiClient.refreshToken(request)) {
                        is ApiResult.Success -> {
                            tokenStorage.storeTokens(result.data)
                            Log.i(TAG, "Token refreshed successfully")
                            true
                        }
                        is ApiResult.Error -> {
                            Log.e(TAG, "Token refresh failed: ${result.error.error}")
                            false
                        }
                        is ApiResult.NetworkError -> {
                            Log.e(TAG, "Token refresh network error: ${result.message}")
                            // Don't fail on network error - token might still be valid
                            !tokenStorage.isTokenExpired()
                        }
                    }
                } finally {
                    isRefreshing = false
                }
            }
        }

        /**
         * Ensure device ID is stored.
         */
        private suspend fun ensureDeviceId() {
            if (tokenStorage.getDeviceId() == null) {
                val deviceId = UUID.randomUUID().toString()
                tokenStorage.storeDeviceId(deviceId)
                Log.i(TAG, "Generated device ID: $deviceId")
            }
        }

        /**
         * Update user profile.
         *
         * @param name New display name (optional)
         * @param email New email address (optional)
         * @return Updated user or error
         */
        suspend fun updateProfile(
            name: String? = null,
            email: String? = null,
        ): AuthResult {
            val request = UpdateProfileRequest(name, email)
            return when (val result = apiClient.updateProfile(request)) {
                is ApiResult.Success -> {
                    val user = result.data
                    tokenStorage.storeUserInfo(user.id, user.email, user.name)
                    _authState.value = AuthState.LoggedIn(user)
                    AuthResult.Success(user)
                }
                is ApiResult.Error -> {
                    AuthResult.Error(result.error.error, result.error.code)
                }
                is ApiResult.NetworkError -> {
                    AuthResult.NetworkError(result.message)
                }
            }
        }

        /**
         * Change password.
         *
         * @param currentPassword Current password
         * @param newPassword New password (min 8 characters)
         * @return Success or error
         */
        suspend fun changePassword(
            currentPassword: String,
            newPassword: String,
        ): AuthResult {
            val request = ChangePasswordRequest(currentPassword, newPassword)
            return when (val result = apiClient.changePassword(request)) {
                is ApiResult.Success -> {
                    val currentUser =
                        (_authState.value as? AuthState.LoggedIn)?.user
                            ?: return AuthResult.Error("Not logged in", null)
                    AuthResult.Success(currentUser)
                }
                is ApiResult.Error -> {
                    AuthResult.Error(result.error.error, result.error.code)
                }
                is ApiResult.NetworkError -> {
                    AuthResult.NetworkError(result.message)
                }
            }
        }

        /**
         * Get list of registered devices.
         *
         * @return List of devices or error
         */
        suspend fun getDevices(): ApiResult<List<Device>> {
            return apiClient.getDevices()
        }

        /**
         * Remove a registered device.
         *
         * @param deviceId Device ID to remove
         * @return Success or error
         */
        suspend fun removeDevice(deviceId: String): ApiResult<Unit> {
            return apiClient.removeDevice(deviceId)
        }

        /**
         * Get list of active login sessions.
         *
         * @return List of sessions or error
         */
        suspend fun getUserSessions(): ApiResult<List<UserSession>> {
            return apiClient.getUserSessions()
        }

        /**
         * Terminate a login session.
         *
         * @param sessionId Session ID to terminate
         * @return Success or error
         */
        suspend fun terminateSession(sessionId: String): ApiResult<Unit> {
            return apiClient.terminateUserSession(sessionId)
        }

        /**
         * Check if user is currently logged in.
         */
        fun isLoggedIn(): Boolean {
            return _authState.value is AuthState.LoggedIn
        }

        /**
         * Get current user if logged in.
         */
        fun getCurrentUser(): User? {
            return (_authState.value as? AuthState.LoggedIn)?.user
        }
    }

/**
 * Result of an authentication operation.
 */
sealed class AuthResult {
    data class Success(val user: User) : AuthResult()

    data class Error(val message: String, val code: String?) : AuthResult()

    data class NetworkError(val message: String) : AuthResult()
}
