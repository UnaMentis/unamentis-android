package com.unamentis.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observable state for tracking whether a tutoring session is active.
 *
 * Used to show/hide the tab bar during active sessions, matching iOS behavior.
 *
 * IMPORTANT: This class uses explicit change guards to prevent unnecessary
 * recompositions. Always use the update methods rather than setting
 * properties directly.
 *
 * Logic:
 * - Tab bar is HIDDEN when: isSessionActive && !isPaused
 * - Tab bar is VISIBLE when: !isSessionActive || isPaused
 */
@Singleton
class SessionActivityState
    @Inject
    constructor() {
        /** Whether a tutoring session is currently active */
        private val _isSessionActive = MutableStateFlow(false)
        val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

        /** Whether the session is paused (tab bar should be visible when paused) */
        private val _isPaused = MutableStateFlow(false)
        val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

        /** Whether the tab bar should be hidden (derived from isSessionActive and isPaused) */
        private val _shouldHideTabBar = MutableStateFlow(false)
        val shouldHideTabBar: StateFlow<Boolean> = _shouldHideTabBar.asStateFlow()

        /**
         * Update session active state with change guard to prevent unnecessary updates.
         */
        fun setSessionActive(newValue: Boolean) {
            if (_isSessionActive.value != newValue) {
                _isSessionActive.value = newValue
                updateShouldHideTabBar()
            }
        }

        /**
         * Update paused state with change guard to prevent unnecessary updates.
         */
        fun setPaused(newValue: Boolean) {
            if (_isPaused.value != newValue) {
                _isPaused.value = newValue
                updateShouldHideTabBar()
            }
        }

        /**
         * Reset all state (used when leaving session view).
         */
        fun reset() {
            if (_isSessionActive.value) {
                _isSessionActive.value = false
            }
            if (_isPaused.value) {
                _isPaused.value = false
            }
            if (_shouldHideTabBar.value) {
                _shouldHideTabBar.value = false
            }
        }

        /**
         * Internal method to update derived shouldHideTabBar state.
         *
         * Tab bar is hidden when session is active AND not paused.
         */
        private fun updateShouldHideTabBar() {
            val newValue = _isSessionActive.value && !_isPaused.value
            if (_shouldHideTabBar.value != newValue) {
                _shouldHideTabBar.value = newValue
            }
        }
    }
