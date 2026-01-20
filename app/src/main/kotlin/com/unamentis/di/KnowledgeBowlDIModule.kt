package com.unamentis.di

import com.unamentis.core.module.ModuleProtocol
import com.unamentis.modules.knowledgebowl.KnowledgeBowlModule
import com.unamentis.modules.knowledgebowl.core.engine.KBQuestionEngine
import com.unamentis.modules.knowledgebowl.core.validation.KBAnswerValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Hilt module providing Knowledge Bowl dependencies.
 *
 * Note: KBQuestionEngine, KBStatsManager, and KBVoiceCoordinator use constructor
 * injection with @Inject and are singletons, so they don't need explicit @Provides.
 * Only classes without @Inject constructors need providers here.
 */
@Module
@InstallIn(SingletonComponent::class)
object KnowledgeBowlDIModule {
    /**
     * Provides the KBAnswerValidator.
     */
    @Provides
    @Singleton
    fun provideKBAnswerValidator(): KBAnswerValidator {
        return KBAnswerValidator()
    }

    /**
     * Provides the KnowledgeBowlModule and adds it to the set of module implementations.
     *
     * This allows the ModuleRegistry to automatically discover and register
     * the Knowledge Bowl module at startup.
     */
    @Provides
    @Singleton
    fun provideKnowledgeBowlModule(questionEngine: KBQuestionEngine): KnowledgeBowlModule {
        return KnowledgeBowlModule(questionEngine)
    }

    /**
     * Provides the KnowledgeBowlModule as a ModuleProtocol for multibinding.
     */
    @Provides
    @IntoSet
    fun provideKnowledgeBowlModuleAsProtocol(module: KnowledgeBowlModule): ModuleProtocol {
        return module
    }
}
