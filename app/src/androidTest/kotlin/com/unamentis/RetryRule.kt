package com.unamentis

import android.util.Log
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * JUnit rule that retries a test up to [maxRetries] times on failure.
 *
 * This mitigates transient CI flakiness caused by emulator timing issues,
 * software rendering delays, and other infrastructure instabilities.
 * On a local machine with a real device/hardware-accelerated emulator,
 * tests pass on the first attempt so the retry never fires.
 */
class RetryRule(private val maxRetries: Int = 2) : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                var lastError: Throwable? = null
                for (attempt in 1..maxRetries) {
                    try {
                        base.evaluate()
                        return // Test passed
                    } catch (t: Throwable) {
                        lastError = t
                        Log.w(
                            "RetryRule",
                            "${description.displayName}: attempt $attempt/$maxRetries failed",
                            t,
                        )
                    }
                }
                throw lastError!!
            }
        }
}
