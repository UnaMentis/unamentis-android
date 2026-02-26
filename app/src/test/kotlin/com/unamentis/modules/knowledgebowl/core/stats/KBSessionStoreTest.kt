package com.unamentis.modules.knowledgebowl.core.stats

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for KBSessionStore.
 *
 * Uses a temporary directory for file-based storage.
 */
class KBSessionStoreTest {
    private lateinit var store: KBSessionStore
    private lateinit var tempDir: File
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        tempDir = createTempDir("kb_test_sessions")
        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns tempDir
        store = KBSessionStore(mockContext)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `save and load session round-trips correctly`() =
        runTest {
            val session = TestSessionFactory.createSession(questionCount = 5)
            store.save(session)

            val loaded = store.load(session.id)
            assertNotNull(loaded)
            assertEquals(session.id, loaded!!.id)
            assertEquals(session.config.region, loaded.config.region)
            assertEquals(session.config.roundType, loaded.config.roundType)
        }

    @Test
    fun `load returns null for missing session`() =
        runTest {
            val loaded = store.load("nonexistent-id")
            assertNull(loaded)
        }

    @Test
    fun `loadAll returns sessions sorted by start time descending`() =
        runTest {
            val session1 = TestSessionFactory.createSession(startTimeMillis = 1000L)
            val session2 = TestSessionFactory.createSession(startTimeMillis = 3000L)
            val session3 = TestSessionFactory.createSession(startTimeMillis = 2000L)

            store.save(session1)
            store.save(session2)
            store.save(session3)

            val all = store.loadAll()
            assertEquals(3, all.size)
            assertEquals(session2.id, all[0].id) // newest first
            assertEquals(session3.id, all[1].id)
            assertEquals(session1.id, all[2].id)
        }

    @Test
    fun `loadRecent respects limit`() =
        runTest {
            repeat(5) { i ->
                val session = TestSessionFactory.createSession(startTimeMillis = (i * 1000).toLong())
                store.save(session)
            }

            val recent = store.loadRecent(limit = 3)
            assertEquals(3, recent.size)
        }

    @Test
    fun `delete removes session`() =
        runTest {
            val session = TestSessionFactory.createSession()
            store.save(session)
            assertNotNull(store.load(session.id))

            store.delete(session.id)
            assertNull(store.load(session.id))
        }

    @Test
    fun `deleteAll removes all sessions`() =
        runTest {
            repeat(3) {
                store.save(TestSessionFactory.createSession())
            }
            assertEquals(3, store.loadAll().size)

            store.deleteAll()
            assertTrue(store.loadAll().isEmpty())
        }

    @Test
    fun `saveBatch saves multiple sessions`() =
        runTest {
            val sessions =
                List(3) {
                    TestSessionFactory.createSession()
                }
            store.saveBatch(sessions)

            val all = store.loadAll()
            assertEquals(3, all.size)
        }

    @Test
    fun `calculateStatistics returns zeros for empty store`() =
        runTest {
            val stats = store.calculateStatistics()
            assertEquals(0, stats.totalSessions)
            assertEquals(0, stats.totalQuestions)
            assertEquals(0.0, stats.overallAccuracy, 0.001)
        }

    @Test
    fun `calculateStatistics aggregates completed sessions`() =
        runTest {
            val session = TestSessionFactory.createCompletedSession(correct = 3, total = 5)
            store.save(session)

            val stats = store.calculateStatistics()
            assertEquals(1, stats.totalSessions)
            assertEquals(5, stats.totalQuestions)
            assertEquals(3, stats.totalCorrect)
            assertEquals(0.6, stats.overallAccuracy, 0.01)
        }

    @Test
    fun `loadSessions filters by region`() =
        runTest {
            val session1 =
                TestSessionFactory.createSession(
                    region = com.unamentis.modules.knowledgebowl.data.model.KBRegion.COLORADO,
                )
            val session2 =
                TestSessionFactory.createSession(
                    region = com.unamentis.modules.knowledgebowl.data.model.KBRegion.MINNESOTA,
                )
            store.save(session1)
            store.save(session2)

            val colorado =
                store.loadSessions(
                    com.unamentis.modules.knowledgebowl.data.model.KBRegion.COLORADO,
                )
            assertEquals(1, colorado.size)
            assertEquals(session1.id, colorado[0].id)
        }

    @Test
    fun `loadSessions filters by round type`() =
        runTest {
            val session1 =
                TestSessionFactory.createSession(
                    roundType = com.unamentis.modules.knowledgebowl.data.model.KBRoundType.WRITTEN,
                )
            val session2 =
                TestSessionFactory.createSession(
                    roundType = com.unamentis.modules.knowledgebowl.data.model.KBRoundType.ORAL,
                )
            store.save(session1)
            store.save(session2)

            val written =
                store.loadSessions(
                    com.unamentis.modules.knowledgebowl.data.model.KBRoundType.WRITTEN,
                )
            assertEquals(1, written.size)
            assertEquals(session1.id, written[0].id)
        }
}
