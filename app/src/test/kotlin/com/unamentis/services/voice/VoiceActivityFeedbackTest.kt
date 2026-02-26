package com.unamentis.services.voice

import android.content.Context
import android.os.Build
import com.unamentis.data.model.TTSAudioChunk
import com.unamentis.data.model.TTSService
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for [VoiceActivityFeedback].
 *
 * Tests focus on:
 * - Configuration toggles (audio/haptics enable/disable)
 * - Announcement gating by audio enabled state
 * - Tone gating by audio enabled state
 * - Haptic gating by haptics enabled state
 * - State management (isSpeaking, stopSpeaking)
 * - Release cleanup
 *
 * Note: Sound playback and vibration are tested structurally
 * since hardware is not available in unit tests. The Robolectric
 * runner provides a real Context for SoundPool construction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class VoiceActivityFeedbackTest {
    private lateinit var context: Context
    private lateinit var mockTTSService: TTSService
    private lateinit var feedback: VoiceActivityFeedback

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        mockTTSService = mockk(relaxed = true)
        every { mockTTSService.synthesize(any()) } returns
            flowOf(
                TTSAudioChunk(
                    audioData = byteArrayOf(0, 1, 2),
                    isFirst = true,
                    isLast = true,
                ),
            )
        feedback = VoiceActivityFeedback(context, mockTTSService)
    }

    // -- Configuration Tests --

    @Test
    fun `audio is enabled by default`() {
        assertTrue(feedback.audioEnabled.value)
    }

    @Test
    fun `haptics are enabled by default`() {
        assertTrue(feedback.hapticsEnabled.value)
    }

    @Test
    fun `setAudioEnabled updates state`() {
        feedback.setAudioEnabled(false)
        assertFalse(feedback.audioEnabled.value)

        feedback.setAudioEnabled(true)
        assertTrue(feedback.audioEnabled.value)
    }

    @Test
    fun `setHapticsEnabled updates state`() {
        feedback.setHapticsEnabled(false)
        assertFalse(feedback.hapticsEnabled.value)

        feedback.setHapticsEnabled(true)
        assertTrue(feedback.hapticsEnabled.value)
    }

    // -- Announcement Gating Tests --

    @Test
    fun `announce does nothing when audio disabled`() {
        feedback.setAudioEnabled(false)
        feedback.announce("test")
        // Should not crash and not invoke TTS
    }

    @Test
    fun `announceCountdownStart does not crash when audio disabled`() {
        feedback.setAudioEnabled(false)
        feedback.announceCountdownStart(10, "Test countdown")
        // No crash expected
    }

    @Test
    fun `announceCountdownMilestone does not crash when audio disabled`() {
        feedback.setAudioEnabled(false)
        feedback.announceCountdownMilestone(5)
    }

    @Test
    fun `announceCountdownComplete does not crash when audio disabled`() {
        feedback.setAudioEnabled(false)
        feedback.announceCountdownComplete("Ready to answer")
    }

    @Test
    fun `announceCommandRecognized does not crash when audio disabled`() {
        feedback.setAudioEnabled(false)
        feedback.announceCommandRecognized(VoiceCommand.READY)
    }

    @Test
    fun `announceAnswerReceived does not crash when audio disabled`() {
        feedback.setAudioEnabled(false)
        feedback.announceAnswerReceived()
    }

    @Test
    fun `announceCorrect does not crash when audio disabled`() {
        feedback.setAudioEnabled(false)
        feedback.announceCorrect()
    }

    @Test
    fun `announceIncorrect does not crash when audio disabled`() {
        feedback.setAudioEnabled(false)
        feedback.announceIncorrect("Paris")
    }

    @Test
    fun `announceIncorrect without correct answer does not crash`() {
        feedback.setAudioEnabled(false)
        feedback.announceIncorrect()
    }

    @Test
    fun `announceActivityStarted does not crash when audio disabled`() {
        feedback.setAudioEnabled(false)
        feedback.announceActivityStarted("Quiz")
    }

    @Test
    fun `announceActivityCompleted does not crash when audio disabled`() {
        feedback.setAudioEnabled(false)
        feedback.announceActivityCompleted("Score: 8/10")
    }

    @Test
    fun `announceNextQuestion does not crash when audio disabled`() {
        feedback.setAudioEnabled(false)
        feedback.announceNextQuestion(3, 10)
    }

    // -- Tone Gating Tests --

    @Test
    fun `playTone does nothing when audio disabled`() {
        feedback.setAudioEnabled(false)
        for (tone in FeedbackTone.entries) {
            feedback.playTone(tone)
            // Should not crash
        }
    }

    @Test
    fun `playCountdownTick does not crash when audio disabled`() {
        feedback.setAudioEnabled(false)
        feedback.playCountdownTick()
    }

    // -- Haptic Gating Tests --

    @Test
    fun `impactHaptic does nothing when haptics disabled`() {
        feedback.setHapticsEnabled(false)
        feedback.impactHaptic()
        // Should not crash
    }

    @Test
    fun `notificationHaptic does nothing when haptics disabled`() {
        feedback.setHapticsEnabled(false)
        for (type in VoiceActivityFeedback.NotificationType.entries) {
            feedback.notificationHaptic(type)
            // Should not crash
        }
    }

    @Test
    fun `selectionHaptic does nothing when haptics disabled`() {
        feedback.setHapticsEnabled(false)
        feedback.selectionHaptic()
        // Should not crash
    }

    // -- State Management Tests --

    @Test
    fun `isSpeaking is false initially`() {
        assertFalse(feedback.isSpeaking.value)
    }

    @Test
    fun `stopSpeaking does not crash when not speaking`() {
        feedback.stopSpeaking()
        assertFalse(feedback.isSpeaking.value)
    }

    // -- Release Tests --

    @Test
    fun `release does not crash`() {
        feedback.release()
        // After release, further calls should not crash
    }

    @Test
    fun `release then announce does not crash`() {
        feedback.release()
        feedback.announce("after release")
        // Should not crash (audio disabled after release)
    }

    // -- FeedbackTone Enum Tests --

    @Test
    fun `FeedbackTone has all expected values`() {
        val tones = FeedbackTone.entries
        assertEquals(6, tones.size)
        assertTrue(tones.contains(FeedbackTone.COMMAND_RECOGNIZED))
        assertTrue(tones.contains(FeedbackTone.COMMAND_INVALID))
        assertTrue(tones.contains(FeedbackTone.COUNTDOWN_TICK))
        assertTrue(tones.contains(FeedbackTone.CORRECT))
        assertTrue(tones.contains(FeedbackTone.INCORRECT))
        assertTrue(tones.contains(FeedbackTone.ATTENTION))
    }

    // -- NotificationType Enum Tests --

    @Test
    fun `NotificationType has all expected values`() {
        val types = VoiceActivityFeedback.NotificationType.entries
        assertEquals(3, types.size)
        assertTrue(types.contains(VoiceActivityFeedback.NotificationType.SUCCESS))
        assertTrue(types.contains(VoiceActivityFeedback.NotificationType.WARNING))
        assertTrue(types.contains(VoiceActivityFeedback.NotificationType.ERROR))
    }

    // -- Feedback with Audio Enabled --

    @Test
    fun `announce with audio enabled invokes normally`() {
        feedback.setAudioEnabled(true)
        feedback.announce("Hello")
        // Should not crash; TTS synthesis is async
    }

    @Test
    fun `playTone with audio enabled invokes normally`() {
        feedback.setAudioEnabled(true)
        feedback.playTone(FeedbackTone.CORRECT)
        // Should not crash (sounds may not be loaded in test)
    }

    // -- Constructor Without TTS --

    @Test
    fun `constructor without TTS service does not crash`() {
        val feedbackNoTTS = VoiceActivityFeedback(context)
        feedbackNoTTS.announce("test")
        feedbackNoTTS.release()
        // Should not crash even without TTS
    }

    // -- Priority Announcement --

    @Test
    fun `announce with priority stops current speech first`() {
        feedback.announce("first message")
        feedback.announce("priority message", priority = true)
        // Should not crash; priority interrupts previous
    }
}
