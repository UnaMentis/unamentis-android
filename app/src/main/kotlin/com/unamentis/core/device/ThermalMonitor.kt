package com.unamentis.core.device

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thermal monitoring service.
 *
 * Monitors device thermal state and triggers fallback strategies
 * when thermal throttling occurs.
 *
 * Thermal states (Android 9+):
 * - NONE: No thermal throttling
 * - LIGHT: Light throttling (minor performance impact)
 * - MODERATE: Moderate throttling (noticeable performance impact)
 * - SEVERE: Severe throttling (significant performance reduction)
 * - CRITICAL: Critical throttling (device may shutdown)
 * - EMERGENCY: Emergency throttling
 * - SHUTDOWN: Device will shutdown
 *
 * Fallback strategies:
 * - MODERATE+: Switch to on-device TTS
 * - SEVERE+: Switch to on-device STT and TTS
 * - CRITICAL+: Pause session and warn user
 */
@Singleton
class ThermalMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val powerManager: PowerManager? = context.getSystemService()

        /**
         * Thermal state enum.
         */
        enum class ThermalState {
            NONE,
            LIGHT,
            MODERATE,
            SEVERE,
            CRITICAL,
            EMERGENCY,
            SHUTDOWN,
        }

        /**
         * Current thermal state.
         */
        private val _thermalState = MutableStateFlow(ThermalState.NONE)
        val thermalState: StateFlow<ThermalState> = _thermalState.asStateFlow()

        /**
         * Thermal event listener callback.
         */
        private val thermalStatusListener =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PowerManager.OnThermalStatusChangedListener { status ->
                    val state = mapThermalStatus(status)
                    _thermalState.value = state
                }
            } else {
                null
            }

        /**
         * Start monitoring thermal state.
         */
        fun startMonitoring() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalStatusListener != null) {
                powerManager?.addThermalStatusListener(thermalStatusListener)

                // Update initial state
                val currentStatus = powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
                _thermalState.value = mapThermalStatus(currentStatus)
            }
        }

        /**
         * Stop monitoring thermal state.
         */
        fun stopMonitoring() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalStatusListener != null) {
                powerManager?.removeThermalStatusListener(thermalStatusListener)
            }
        }

        /**
         * Get current thermal state.
         */
        fun getCurrentState(): ThermalState {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val status = powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
                return mapThermalStatus(status)
            }
            return ThermalState.NONE
        }

        /**
         * Check if thermal throttling is active.
         */
        fun isThrottling(): Boolean {
            return _thermalState.value != ThermalState.NONE
        }

        /**
         * Check if thermal state requires fallback.
         */
        fun requiresFallback(): Boolean {
            return _thermalState.value.ordinal >= ThermalState.MODERATE.ordinal
        }

        /**
         * Check if thermal state is critical.
         */
        fun isCritical(): Boolean {
            return _thermalState.value.ordinal >= ThermalState.CRITICAL.ordinal
        }

        /**
         * Get recommended action for current thermal state.
         */
        fun getRecommendedAction(): ThermalAction {
            return when (_thermalState.value) {
                ThermalState.NONE, ThermalState.LIGHT -> ThermalAction.NONE
                ThermalState.MODERATE -> ThermalAction.SWITCH_TO_ONDEVICE_TTS
                ThermalState.SEVERE -> ThermalAction.SWITCH_TO_ONDEVICE_ALL
                ThermalState.CRITICAL, ThermalState.EMERGENCY, ThermalState.SHUTDOWN ->
                    ThermalAction.PAUSE_SESSION
            }
        }

        /**
         * Map Android thermal status to ThermalState enum.
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        private fun mapThermalStatus(status: Int): ThermalState {
            return when (status) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalState.NONE
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalState.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL -> ThermalState.CRITICAL
                PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalState.EMERGENCY
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalState.SHUTDOWN
                else -> ThermalState.NONE
            }
        }

        /**
         * Thermal action recommendations.
         */
        enum class ThermalAction {
            NONE,
            SWITCH_TO_ONDEVICE_TTS,
            SWITCH_TO_ONDEVICE_ALL,
            PAUSE_SESSION,
        }

        /**
         * Get human-readable description of thermal state.
         */
        fun getThermalStateDescription(): String {
            return when (_thermalState.value) {
                ThermalState.NONE -> "Normal temperature"
                ThermalState.LIGHT -> "Slightly warm (minor throttling)"
                ThermalState.MODERATE -> "Warm (moderate throttling)"
                ThermalState.SEVERE -> "Hot (severe throttling)"
                ThermalState.CRITICAL -> "Very hot (critical throttling)"
                ThermalState.EMERGENCY -> "Emergency temperature"
                ThermalState.SHUTDOWN -> "Device will shutdown"
            }
        }

        /**
         * Check if device supports thermal monitoring.
         */
        fun isSupported(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        }
    }
