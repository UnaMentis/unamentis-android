package com.unamentis.di

import android.content.Context
import com.unamentis.BuildConfig
import com.unamentis.data.local.AppDatabase
import com.unamentis.data.local.SecureTokenStorage
import com.unamentis.data.local.dao.CurriculumDao
import com.unamentis.data.local.dao.ModuleDao
import com.unamentis.data.local.dao.SessionDao
import com.unamentis.data.local.dao.TodoDao
import com.unamentis.data.local.dao.TopicProgressDao
import com.unamentis.data.remote.ApiClient
import com.unamentis.data.remote.CertificatePinning
import com.unamentis.data.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module providing application-level dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /**
     * Provides the Room database instance.
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    /**
     * Provides the SessionDao.
     */
    @Provides
    @Singleton
    fun provideSessionDao(database: AppDatabase): SessionDao {
        return database.sessionDao()
    }

    /**
     * Provides the CurriculumDao.
     */
    @Provides
    @Singleton
    fun provideCurriculumDao(database: AppDatabase): CurriculumDao {
        return database.curriculumDao()
    }

    /**
     * Provides the TopicProgressDao.
     */
    @Provides
    @Singleton
    fun provideTopicProgressDao(database: AppDatabase): TopicProgressDao {
        return database.topicProgressDao()
    }

    /**
     * Provides the TodoDao.
     */
    @Provides
    @Singleton
    fun provideTodoDao(database: AppDatabase): TodoDao {
        return database.todoDao()
    }

    /**
     * Provides the ModuleDao.
     */
    @Provides
    @Singleton
    fun provideModuleDao(database: AppDatabase): ModuleDao {
        return database.moduleDao()
    }

    /**
     * Provides the OkHttpClient for networking.
     *
     * Includes certificate pinning for provider APIs in release builds.
     * Pinning is disabled in debug builds to allow proxy tools (Charles, mitmproxy).
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor =
            HttpLoggingInterceptor().apply {
                level =
                    if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
            }

        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .apply {
                // Only enable certificate pinning in release builds
                if (CertificatePinning.isEnabled()) {
                    certificatePinner(CertificatePinning.pinner)
                    android.util.Log.i("AppModule", "Certificate pinning ENABLED")
                } else {
                    android.util.Log.i("AppModule", "Certificate pinning DISABLED (debug build)")
                }
            }
            .build()
    }

    /**
     * Provides JSON serialization configuration.
     */
    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }

    /**
     * Provides secure token storage for authentication.
     */
    @Provides
    @Singleton
    fun provideSecureTokenStorage(
        @ApplicationContext context: Context,
    ): SecureTokenStorage {
        return SecureTokenStorage(context)
    }

    /**
     * Provides the API client for server communication.
     *
     * The API client is configured with token provider and expiration callback
     * from the AuthRepository for automatic token management.
     */
    @Provides
    @Singleton
    fun provideApiClient(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        json: Json,
    ): ApiClient {
        // Note: tokenProvider and onTokenExpired are set to null here
        // and will be configured by AuthRepository after initialization.
        // This avoids circular dependency issues.
        return ApiClient(
            context = context,
            okHttpClient = okHttpClient,
            json = json,
            tokenProvider = null,
            onTokenExpired = null,
        )
    }

    /**
     * Provides the authentication repository.
     */
    @Provides
    @Singleton
    fun provideAuthRepository(
        apiClient: ApiClient,
        tokenStorage: SecureTokenStorage,
    ): AuthRepository {
        return AuthRepository(apiClient, tokenStorage)
    }
}
