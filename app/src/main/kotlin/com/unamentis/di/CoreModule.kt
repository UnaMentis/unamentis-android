package com.unamentis.di

import android.content.Context
import com.unamentis.core.audio.AudioEngine
import com.unamentis.core.curriculum.CurriculumEngine
import com.unamentis.core.session.SessionManager
import com.unamentis.data.model.LLMService
import com.unamentis.data.model.STTService
import com.unamentis.data.model.TTSService
import com.unamentis.data.model.VADService
import com.unamentis.data.repository.CurriculumRepository
import com.unamentis.data.repository.TopicProgressRepository
import com.unamentis.services.vad.SileroOnnxVADService
import com.unamentis.services.vad.SimpleVADService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Hilt module providing core application dependencies.
 *
 * Provides:
 * - AudioEngine for low-latency audio I/O
 * - VADService for voice activity detection
 * - CurriculumEngine for curriculum management
 * - SessionManager for voice session orchestration
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    /**
     * Provides the AudioEngine for low-latency audio capture and playback.
     */
    @Provides
    @Singleton
    fun provideAudioEngine(): AudioEngine {
        return AudioEngine().also { engine ->
            engine.initialize()
        }
    }

    /**
     * Provides the VADService for voice activity detection.
     *
     * Priority order:
     * 1. Silero VAD (ONNX) - Neural network-based, high accuracy
     * 2. SimpleVAD - Amplitude-based fallback
     */
    @Provides
    @Singleton
    fun provideVADService(
        @ApplicationContext context: Context,
    ): VADService {
        // Try Silero VAD (ONNX) first - highest accuracy
        return try {
            SileroOnnxVADService(context).also { vad ->
                vad.initialize()
                android.util.Log.i("CoreModule", "Using Silero VAD (ONNX)")
            }
        } catch (e: Exception) {
            android.util.Log.w("CoreModule", "Silero ONNX VAD failed, falling back to SimpleVAD", e)
            // Fall back to simple amplitude-based VAD
            SimpleVADService().also { vad ->
                vad.initialize()
                android.util.Log.i("CoreModule", "Using SimpleVAD (amplitude-based fallback)")
            }
        }
    }

    /**
     * Provides the CurriculumEngine for curriculum management.
     */
    @Provides
    @Singleton
    fun provideCurriculumEngine(
        curriculumRepository: CurriculumRepository,
        topicProgressRepository: TopicProgressRepository,
    ): CurriculumEngine {
        return CurriculumEngine(curriculumRepository, topicProgressRepository)
    }

    /**
     * Provides the application-level CoroutineScope.
     */
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    /**
     * Provides the SessionManager for voice session orchestration.
     */
    @Provides
    @Singleton
    fun provideSessionManager(
        audioEngine: AudioEngine,
        vadService: VADService,
        sttService: STTService,
        ttsService: TTSService,
        llmService: LLMService,
        curriculumEngine: CurriculumEngine,
        scope: CoroutineScope,
    ): SessionManager {
        return SessionManager(
            audioEngine = audioEngine,
            vadService = vadService,
            sttService = sttService,
            ttsService = ttsService,
            llmService = llmService,
            curriculumEngine = curriculumEngine,
            scope = scope,
        )
    }
}
