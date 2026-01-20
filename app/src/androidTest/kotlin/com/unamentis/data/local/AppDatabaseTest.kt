package com.unamentis.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unamentis.data.local.entity.SessionEntity
import com.unamentis.data.local.entity.TopicProgressEntity
import com.unamentis.data.local.entity.TranscriptEntryEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for Room database operations.
 *
 * These tests run on an Android device or emulator to verify
 * database functionality.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(
                context,
                AppDatabase::class.java,
            ).build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testInsertAndRetrieveSession() =
        runBlocking {
            // Given
            val session =
                SessionEntity(
                    id = "session-001",
                    startTime = System.currentTimeMillis(),
                    turnCount = 5,
                    totalCost = 0.50,
                )

            // When
            database.sessionDao().insertSession(session)
            val retrieved = database.sessionDao().getSessionById("session-001")

            // Then
            assertNotNull(retrieved)
            assertEquals("session-001", retrieved?.id)
            assertEquals(5, retrieved?.turnCount)
            assertEquals(0.50, retrieved?.totalCost, 0.001)
        }

    @Test
    fun testInsertTranscriptEntry() =
        runBlocking {
            // Given
            val session =
                SessionEntity(
                    id = "session-001",
                    startTime = System.currentTimeMillis(),
                )
            val entry =
                TranscriptEntryEntity(
                    id = "entry-001",
                    sessionId = "session-001",
                    role = "user",
                    text = "Hello",
                    timestamp = System.currentTimeMillis(),
                )

            // When
            database.sessionDao().insertSession(session)
            database.sessionDao().insertTranscriptEntry(entry)
            val transcript = database.sessionDao().getTranscriptBySessionId("session-001")

            // Then
            assertEquals(1, transcript.size)
            assertEquals("user", transcript[0].role)
            assertEquals("Hello", transcript[0].text)
        }

    @Test
    fun testTopicProgressTracking() =
        runBlocking {
            // Given
            val progress =
                TopicProgressEntity(
                    topicId = "topic-001",
                    curriculumId = "curriculum-001",
                    timeSpentSeconds = 300,
                    masteryLevel = 0.5f,
                    lastAccessedAt = System.currentTimeMillis(),
                )

            // When
            database.topicProgressDao().insertProgress(progress)
            val retrieved = database.topicProgressDao().getProgressByTopic("topic-001")

            // Then
            assertNotNull(retrieved)
            assertEquals(300L, retrieved?.timeSpentSeconds)
            assertEquals(0.5f, retrieved?.masteryLevel, 0.001f)
        }

    @Test
    fun testUpdateTimeSpent() =
        runBlocking {
            // Given
            val progress =
                TopicProgressEntity(
                    topicId = "topic-001",
                    curriculumId = "curriculum-001",
                    timeSpentSeconds = 300,
                    masteryLevel = 0.5f,
                    lastAccessedAt = System.currentTimeMillis(),
                )
            database.topicProgressDao().insertProgress(progress)

            // When
            database.topicProgressDao().updateTimeSpent("topic-001", 150, System.currentTimeMillis())
            val updated = database.topicProgressDao().getProgressByTopic("topic-001")

            // Then
            assertEquals(450L, updated?.timeSpentSeconds) // 300 + 150
        }
}
