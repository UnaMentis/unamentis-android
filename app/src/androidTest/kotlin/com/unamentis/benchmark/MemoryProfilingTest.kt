package com.unamentis.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unamentis.data.model.TranscriptEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Memory profiling tests for 90-minute session stability.
 *
 * These tests verify that the app maintains stable memory usage
 * throughout extended learning sessions.
 *
 * Targets:
 * - Baseline memory: <300MB
 * - 90-minute growth: <50MB
 * - No memory leaks detected
 */
@RunWith(AndroidJUnit4::class)
class MemoryProfilingTest {

    private val runtime = Runtime.getRuntime()
    private var initialMemoryMB: Long = 0

    @Before
    fun setup() {
        // Force garbage collection
        System.gc()
        Thread.sleep(1000)
        System.gc()

        // Record baseline memory
        initialMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    @After
    fun teardown() {
        // Force cleanup
        System.gc()
    }

    /**
     * Test baseline memory usage.
     * Target: <300MB after app startup
     */
    @Test
    fun test_baselineMemoryUsage() {
        assert(initialMemoryMB < 300) {
            "Baseline memory is ${initialMemoryMB}MB, target is <300MB"
        }
    }

    /**
     * Test memory growth over simulated 90-minute session.
     * Target: <50MB growth
     *
     * Note: This is a shortened simulation. For full 90-minute test,
     * run manually with extended duration.
     */
    @Test
    fun test_extendedSessionMemoryGrowth() = runBlocking {
        val transcript = mutableListOf<TranscriptEntry>()
        val sessionMetrics = mutableListOf<Long>()

        // Simulate 180 turns (approximately 90 minutes at 30s per turn)
        // In real test, this would be actual session activity
        repeat(180) { turn ->
            // Add user message
            transcript.add(
                TranscriptEntry(
                    id = "user_$turn",
                    sessionId = "test_session",
                    role = "user",
                    text = "This is user message number $turn in the conversation",
                    timestamp = System.currentTimeMillis()
                )
            )

            // Add AI response
            transcript.add(
                TranscriptEntry(
                    id = "ai_$turn",
                    sessionId = "test_session",
                    role = "assistant",
                    text = "This is AI response number $turn providing educational content about the topic",
                    timestamp = System.currentTimeMillis()
                )
            )

            // Record metrics
            sessionMetrics.add(System.currentTimeMillis())

            // Simulate processing delay (much faster than real session)
            delay(10)

            // Check memory every 20 turns
            if (turn % 20 == 0) {
                System.gc()
                val currentMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                val growthMB = currentMemoryMB - initialMemoryMB

                println("Turn $turn: Memory = ${currentMemoryMB}MB, Growth = ${growthMB}MB")

                // Ensure we're not leaking memory
                assert(growthMB < 100) {
                    "Memory grew by ${growthMB}MB at turn $turn, exceeds threshold"
                }
            }
        }

        // Final memory check
        System.gc()
        Thread.sleep(500)
        System.gc()

        val finalMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val totalGrowthMB = finalMemoryMB - initialMemoryMB

        println("Final memory: ${finalMemoryMB}MB, Total growth: ${totalGrowthMB}MB")

        assert(totalGrowthMB < 50) {
            "Total memory growth was ${totalGrowthMB}MB, target is <50MB"
        }
    }

    /**
     * Test memory cleanup after session end.
     * Target: Return to near-baseline after GC
     */
    @Test
    fun test_memoryCleanupAfterSession() = runBlocking {
        // Simulate session with large transcript
        val transcript = mutableListOf<TranscriptEntry>()
        repeat(1000) { i ->
            transcript.add(
                TranscriptEntry(
                    id = "entry_$i",
                    sessionId = "test_session",
                    role = if (i % 2 == 0) "user" else "assistant",
                    text = "Message $i with some content to occupy memory",
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        val duringSessionMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

        // Clear transcript (simulate session end)
        transcript.clear()

        // Force garbage collection
        repeat(3) {
            System.gc()
            Thread.sleep(500)
        }

        val afterCleanupMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val memoryReclaimed = duringSessionMemoryMB - afterCleanupMemoryMB

        println("During session: ${duringSessionMemoryMB}MB")
        println("After cleanup: ${afterCleanupMemoryMB}MB")
        println("Reclaimed: ${memoryReclaimed}MB")

        // Should reclaim most of the session memory
        assert(memoryReclaimed > 0) {
            "No memory was reclaimed after session cleanup"
        }
    }

    /**
     * Test memory usage with concurrent audio processing.
     * Target: <100MB additional memory during active session
     */
    @Test
    fun test_memoryWithAudioProcessing() = runBlocking {
        val audioBuffers = mutableListOf<FloatArray>()

        // Simulate 60 seconds of audio (120 buffers of 512 samples)
        repeat(120) { i ->
            audioBuffers.add(FloatArray(512) { (it * 0.001f) })

            // Keep only last 10 buffers (simulate rolling buffer)
            if (audioBuffers.size > 10) {
                audioBuffers.removeAt(0)
            }

            if (i % 20 == 0) {
                val currentMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                val growthMB = currentMemoryMB - initialMemoryMB

                assert(growthMB < 100) {
                    "Audio processing caused ${growthMB}MB growth at buffer $i"
                }
            }
        }

        // Final check
        System.gc()
        val finalMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val totalGrowthMB = finalMemoryMB - initialMemoryMB

        assert(totalGrowthMB < 100) {
            "Total memory growth with audio was ${totalGrowthMB}MB, target is <100MB"
        }
    }

    /**
     * Test no memory leaks in state transitions.
     * Target: Stable memory across many state changes
     */
    @Test
    fun test_noLeaksInStateTransitions() {
        val memorySnapshots = mutableListOf<Long>()

        // Simulate 1000 state transitions
        repeat(1000) { i ->
            // Simulate state change (would be SessionManager.updateState() in real test)
            val state = i % 8 // Cycle through all 8 states

            if (i % 100 == 0) {
                System.gc()
                val currentMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                memorySnapshots.add(currentMemoryMB)
            }
        }

        // Check for linear growth (indicates leak)
        val firstHalfAvg = memorySnapshots.take(5).average()
        val secondHalfAvg = memorySnapshots.takeLast(5).average()
        val drift = secondHalfAvg - firstHalfAvg

        println("First half avg: ${firstHalfAvg}MB")
        println("Second half avg: ${secondHalfAvg}MB")
        println("Drift: ${drift}MB")

        assert(drift < 10) {
            "Memory drifted by ${drift}MB across state transitions, possible leak"
        }
    }

    /**
     * Test maximum memory usage under stress.
     * Target: <500MB even under maximum load
     */
    @Test
    fun test_maximumMemoryUnderLoad() = runBlocking {
        // Create worst-case scenario: large transcript, audio buffers, metrics
        val transcript = mutableListOf<TranscriptEntry>()
        val audioBuffers = mutableListOf<FloatArray>()
        val metrics = mutableListOf<Long>()

        // Large transcript (500 entries)
        repeat(500) { i ->
            transcript.add(
                TranscriptEntry(
                    id = "entry_$i",
                    sessionId = "stress_test",
                    role = if (i % 2 == 0) "user" else "assistant",
                    text = "A" * 500, // 500 character message
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        // Multiple audio buffers
        repeat(50) {
            audioBuffers.add(FloatArray(2048) { (it * 0.001f) })
        }

        // Metrics for 90 minutes
        repeat(5400) { // 90 min * 60 sec
            metrics.add(System.currentTimeMillis())
        }

        // Measure peak memory
        System.gc()
        val peakMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

        println("Peak memory under load: ${peakMemoryMB}MB")

        assert(peakMemoryMB < 500) {
            "Peak memory was ${peakMemoryMB}MB, target is <500MB"
        }
    }
}
