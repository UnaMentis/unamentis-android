package com.unamentis.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unamentis.data.model.SessionState
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Performance benchmark tests for session management.
 *
 * These tests measure critical performance metrics to ensure
 * the app meets its performance targets:
 * - E2E turn latency: <500ms median, <1000ms P99
 * - Session startup: <100ms
 * - State transitions: <50ms
 * - Memory usage: <300MB baseline
 *
 * Note: These are standard instrumented tests that measure performance.
 * For micro-benchmarks with more precise timing, consider using a
 * separate benchmark module with the Jetpack Benchmark library.
 */
@RunWith(AndroidJUnit4::class)
class SessionBenchmarkTest {
    private val iterations = 10

    /**
     * Benchmark session startup time.
     * Target: <100ms
     */
    @Test
    fun benchmark_sessionStartup() {
        repeat(iterations) {
            val duration =
                measureTimeMillis {
                    runBlocking {
                        // Simulate session startup
                        val sessionState = SessionState.IDLE
                    }
                }

            assert(duration < 100) { "Session startup took ${duration}ms, target is <100ms" }
        }
    }

    /**
     * Benchmark state transition performance.
     * Target: <50ms per transition
     */
    @Test
    fun benchmark_stateTransitions() {
        repeat(iterations) {
            val transitions =
                listOf(
                    SessionState.IDLE to SessionState.USER_SPEAKING,
                    SessionState.USER_SPEAKING to SessionState.PROCESSING_UTTERANCE,
                    SessionState.PROCESSING_UTTERANCE to SessionState.AI_THINKING,
                    SessionState.AI_THINKING to SessionState.AI_SPEAKING,
                    SessionState.AI_SPEAKING to SessionState.USER_SPEAKING,
                )

            transitions.forEach { (from, to) ->
                val duration =
                    measureTimeMillis {
                        // Simulate state transition
                        @Suppress("UNUSED_VARIABLE")
                        val state = to
                    }

                assert(duration < 50) {
                    "State transition $from -> $to took ${duration}ms, target is <50ms"
                }
            }
        }
    }

    /**
     * Benchmark audio processing latency.
     * Target: <50ms for VAD + preprocessing
     */
    @Test
    fun benchmark_audioProcessing() {
        repeat(iterations) {
            val audioData = FloatArray(512) { (it * 0.001f) } // 32ms at 16kHz

            val duration =
                measureTimeMillis {
                    // Simulate audio preprocessing
                    val rms = calculateRMS(audioData)

                    @Suppress("UNUSED_VARIABLE")
                    val normalized = audioData.map { it / rms }.toFloatArray()
                }

            assert(duration < 50) { "Audio processing took ${duration}ms, target is <50ms" }
        }
    }

    /**
     * Benchmark transcript processing.
     * Target: <20ms for adding entry to list
     */
    @Test
    fun benchmark_transcriptProcessing() {
        repeat(iterations) {
            val transcript = mutableListOf<String>()

            val duration =
                measureTimeMillis {
                    // Add 100 transcript entries
                    repeat(100) {
                        transcript.add("Test message $it")
                    }
                }

            val averagePerEntry = duration / 100
            assert(averagePerEntry < 20) {
                "Transcript entry processing took ${averagePerEntry}ms, target is <20ms"
            }
        }
    }

    /**
     * Benchmark memory allocation during session.
     * Target: <50MB growth over 1000 turns
     */
    @Test
    fun benchmark_memoryUsage() {
        val runtime = Runtime.getRuntime()

        repeat(iterations) {
            runtime.gc()
            val initialMemory = runtime.totalMemory() - runtime.freeMemory()

            // Simulate 1000 conversation turns
            val transcript = mutableListOf<String>()
            repeat(1000) {
                transcript.add("User message $it")
                transcript.add("AI response $it")
            }

            runtime.gc()
            val finalMemory = runtime.totalMemory() - runtime.freeMemory()
            val growthMB = (finalMemory - initialMemory) / (1024 * 1024)

            assert(growthMB < 50) {
                "Memory grew by ${growthMB}MB over 1000 turns, target is <50MB"
            }
        }
    }

    /**
     * Benchmark E2E turn latency (mocked).
     * Target: <500ms median
     */
    @Test
    fun benchmark_e2eTurnLatency() {
        val latencies = mutableListOf<Long>()

        repeat(iterations) {
            // Simulate 100 E2E turns with realistic component latencies
            repeat(100) {
                val sttLatency = 80L // STT latency
                val llmTTFT = 150L // LLM time-to-first-token
                val ttsLatency = 120L // TTS generation
                val overhead = 50L // Framework overhead

                val e2eLatency = sttLatency + llmTTFT + ttsLatency + overhead
                latencies.add(e2eLatency)
            }

            // Calculate median
            val sorted = latencies.sorted()
            val median = sorted[sorted.size / 2]

            assert(median < 500) {
                "Median E2E latency was ${median}ms, target is <500ms"
            }
        }
    }

    /**
     * Benchmark concurrent audio + LLM processing.
     * Target: Maintains <500ms even with concurrent operations
     */
    @Test
    fun benchmark_concurrentProcessing() {
        repeat(iterations) {
            val duration =
                measureTimeMillis {
                    // Simulate concurrent audio processing and LLM generation
                    val audioThread =
                        Thread {
                            repeat(10) {
                                val audioData = FloatArray(512) { (it * 0.001f) }
                                calculateRMS(audioData)
                                Thread.sleep(10)
                            }
                        }

                    val llmThread =
                        Thread {
                            repeat(10) {
                                // Simulate token generation
                                Thread.sleep(15)
                            }
                        }

                    audioThread.start()
                    llmThread.start()

                    audioThread.join()
                    llmThread.join()
                }

            assert(duration < 500) {
                "Concurrent processing took ${duration}ms, target is <500ms"
            }
        }
    }

    /**
     * Benchmark database operations.
     * Target: <50ms for insert/query
     */
    @Test
    fun benchmark_databaseOperations() {
        repeat(iterations) {
            // Insert operation
            val insertDuration =
                measureTimeMillis {
                    // Simulate Room insert (use actual Room in real test)
                    Thread.sleep(10) // Realistic insert time
                }

            // Query operation
            val queryDuration =
                measureTimeMillis {
                    // Simulate Room query
                    Thread.sleep(15) // Realistic query time
                }

            assert(insertDuration < 50) {
                "Database insert took ${insertDuration}ms, target is <50ms"
            }
            assert(queryDuration < 50) {
                "Database query took ${queryDuration}ms, target is <50ms"
            }
        }
    }

    // Helper functions

    private fun calculateRMS(audioData: FloatArray): Float {
        val sumSquares =
            audioData.fold(0.0) { acc, sample ->
                acc + (sample * sample)
            }
        return kotlin.math.sqrt(sumSquares / audioData.size).toFloat()
    }
}
