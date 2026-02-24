package com.unamentis

/**
 * Shared timeout configuration for instrumented UI tests.
 *
 * Uses higher timeouts to accommodate CI environments where emulators
 * run on shared hardware with software rendering (swiftshader) and take
 * longer to stabilize. Since [waitUntil] returns immediately when its
 * condition is met, higher timeouts do not slow down passing tests —
 * they only provide more headroom before a legitimate failure is reported.
 */
object CiTestConfig {
    /** Standard timeout for UI element appearance after navigation (30 s). */
    const val DEFAULT_TIMEOUT = 30_000L

    /** Extended timeout for data loading, complex transitions, or first-screen render (45 s). */
    const val LONG_TIMEOUT = 45_000L
}
