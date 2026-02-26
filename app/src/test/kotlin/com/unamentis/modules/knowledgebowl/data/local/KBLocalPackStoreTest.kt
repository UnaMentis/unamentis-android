package com.unamentis.modules.knowledgebowl.data.local

import android.content.Context
import android.util.Log
import com.unamentis.modules.knowledgebowl.data.model.KBAnswer
import com.unamentis.modules.knowledgebowl.data.model.KBDifficulty
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBPack
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for [KBLocalPackStore].
 *
 * Uses Robolectric for Android Context, with real file I/O to temp
 * directories. Tests CRUD operations, persistence, and edge cases.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class KBLocalPackStoreTest {
    private lateinit var context: Context
    private lateinit var store: KBLocalPackStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        store = KBLocalPackStore(context)

        // Mock android.util.Log which is not available in unit tests
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        // Clean up pack file if it exists
        val packFile = File(context.filesDir, "kb_local_packs.json")
        if (packFile.exists()) {
            packFile.delete()
        }
        unmockkStatic(Log::class)
    }

    // MARK: - Initial State Tests

    @Test
    fun `initial localPacks is empty`() {
        assertTrue(store.localPacks.value.isEmpty())
    }

    @Test
    fun `initial isLoading is false`() {
        assertFalse(store.isLoading.value)
    }

    // MARK: - load Tests

    @Test
    fun `load with no file on disk results in empty list`() =
        runTest {
            store.load()

            assertTrue(store.localPacks.value.isEmpty())
            assertFalse(store.isLoading.value)
        }

    @Test
    fun `load with corrupted file results in empty list`() =
        runTest {
            // Write corrupted data
            val packFile = File(context.filesDir, "kb_local_packs.json")
            packFile.writeText("not valid json {{{")

            store.load()

            assertTrue(store.localPacks.value.isEmpty())
            assertFalse(store.isLoading.value)
        }

    // MARK: - createPack Tests

    @Test
    fun `createPack creates a pack with correct properties`() =
        runTest {
            val questions = createTestQuestions(5)
            val pack = store.createPack("Test Pack", "A test pack", questions)

            assertEquals("Test Pack", pack.name)
            assertEquals("A test pack", pack.description)
            assertEquals(5, pack.questionCount)
            assertTrue(pack.isLocal)
            assertEquals(KBPack.PackType.CUSTOM, pack.packType)
            assertNotNull(pack.createdAtMillis)
            assertNull(pack.updatedAtMillis)
        }

    @Test
    fun `createPack generates ID with local prefix`() =
        runTest {
            val questions = createTestQuestions(1)
            val pack = store.createPack("Test", null, questions)

            assertTrue(pack.id.startsWith("local-"))
        }

    @Test
    fun `createPack uses default description when null`() =
        runTest {
            val questions = createTestQuestions(1)
            val pack = store.createPack("Test", null, questions)

            assertEquals("Custom pack created on device", pack.description)
        }

    @Test
    fun `createPack calculates domain distribution`() =
        runTest {
            val questions =
                listOf(
                    createQuestion("q1", KBDomain.SCIENCE),
                    createQuestion("q2", KBDomain.SCIENCE),
                    createQuestion("q3", KBDomain.MATHEMATICS),
                )
            val pack = store.createPack("Mixed", null, questions)

            assertEquals(2, pack.domainDistribution["science"])
            assertEquals(1, pack.domainDistribution["mathematics"])
        }

    @Test
    fun `createPack calculates difficulty distribution`() =
        runTest {
            val questions =
                listOf(
                    createQuestion("q1", difficulty = KBDifficulty.VARSITY),
                    createQuestion("q2", difficulty = KBDifficulty.VARSITY),
                    createQuestion("q3", difficulty = KBDifficulty.CHAMPIONSHIP),
                )
            val pack = store.createPack("Difficulties", null, questions)

            assertEquals(2, pack.difficultyDistribution[KBDifficulty.VARSITY.level])
            assertEquals(1, pack.difficultyDistribution[KBDifficulty.CHAMPIONSHIP.level])
        }

    @Test
    fun `createPack stores question IDs`() =
        runTest {
            val questions =
                listOf(
                    createQuestion("q1"),
                    createQuestion("q2"),
                    createQuestion("q3"),
                )
            val pack = store.createPack("IDs", null, questions)

            assertEquals(listOf("q1", "q2", "q3"), pack.questionIds)
        }

    @Test
    fun `createPack adds pack to localPacks`() =
        runTest {
            val questions = createTestQuestions(3)
            store.createPack("Pack 1", null, questions)

            assertEquals(1, store.localPacks.value.size)
            assertEquals("Pack 1", store.localPacks.value.first().name)
        }

    @Test
    fun `createPack multiple packs accumulate`() =
        runTest {
            store.createPack("Pack 1", null, createTestQuestions(2))
            store.createPack("Pack 2", null, createTestQuestions(3))
            store.createPack("Pack 3", null, createTestQuestions(1))

            assertEquals(3, store.localPacks.value.size)
        }

    @Test
    fun `createPack persists to disk`() =
        runTest {
            store.createPack("Persistent", null, createTestQuestions(2))

            // Create a new store and load from disk
            val store2 = KBLocalPackStore(context)
            store2.load()

            assertEquals(1, store2.localPacks.value.size)
            assertEquals("Persistent", store2.localPacks.value.first().name)
        }

    // MARK: - updatePack Tests

    @Test
    fun `updatePack updates name`() =
        runTest {
            val pack = store.createPack("Original", null, createTestQuestions(2))
            val result = store.updatePack(pack.id, name = "Updated")

            assertTrue(result)
            assertEquals("Updated", store.localPacks.value.first().name)
        }

    @Test
    fun `updatePack updates description`() =
        runTest {
            val pack = store.createPack("Test", "Original desc", createTestQuestions(2))
            val result = store.updatePack(pack.id, description = "Updated desc")

            assertTrue(result)
            assertEquals("Updated desc", store.localPacks.value.first().description)
        }

    @Test
    fun `updatePack updates questions and recalculates distributions`() =
        runTest {
            val pack =
                store.createPack(
                    "Test",
                    null,
                    listOf(createQuestion("q1", KBDomain.SCIENCE)),
                )

            val newQuestions =
                listOf(
                    createQuestion("q10", KBDomain.MATHEMATICS),
                    createQuestion("q11", KBDomain.MATHEMATICS),
                )
            store.updatePack(pack.id, questions = newQuestions)

            val updated = store.localPacks.value.first()
            assertEquals(2, updated.questionCount)
            assertEquals(2, updated.domainDistribution["mathematics"])
            assertNull(updated.domainDistribution["science"])
            assertEquals(listOf("q10", "q11"), updated.questionIds)
        }

    @Test
    fun `updatePack sets updatedAtMillis`() =
        runTest {
            val pack = store.createPack("Test", null, createTestQuestions(1))
            assertNull(pack.updatedAtMillis)

            store.updatePack(pack.id, name = "Updated")

            val updated = store.localPacks.value.first()
            assertNotNull(updated.updatedAtMillis)
        }

    @Test
    fun `updatePack returns false for non-existent pack`() =
        runTest {
            val result = store.updatePack("non-existent-id", name = "Updated")

            assertFalse(result)
        }

    @Test
    fun `updatePack preserves unchanged fields`() =
        runTest {
            val questions = createTestQuestions(3)
            val pack = store.createPack("Test", "Description", questions)

            store.updatePack(pack.id, name = "New Name")

            val updated = store.localPacks.value.first()
            assertEquals("New Name", updated.name)
            assertEquals("Description", updated.description)
            assertEquals(3, updated.questionCount)
        }

    // MARK: - deletePack Tests

    @Test
    fun `deletePack removes pack from list`() =
        runTest {
            val pack = store.createPack("To Delete", null, createTestQuestions(2))

            val result = store.deletePack(pack.id)

            assertTrue(result)
            assertTrue(store.localPacks.value.isEmpty())
        }

    @Test
    fun `deletePack returns false for non-existent pack`() =
        runTest {
            val result = store.deletePack("non-existent-id")

            assertFalse(result)
        }

    @Test
    fun `deletePack persists to disk`() =
        runTest {
            val pack = store.createPack("To Delete", null, createTestQuestions(2))
            store.deletePack(pack.id)

            val store2 = KBLocalPackStore(context)
            store2.load()

            assertTrue(store2.localPacks.value.isEmpty())
        }

    @Test
    fun `deletePack removes only the targeted pack`() =
        runTest {
            val pack1 = store.createPack("Keep", null, createTestQuestions(1))
            val pack2 = store.createPack("Delete", null, createTestQuestions(1))

            store.deletePack(pack2.id)

            assertEquals(1, store.localPacks.value.size)
            assertEquals(pack1.id, store.localPacks.value.first().id)
        }

    // MARK: - packById Tests

    @Test
    fun `packById returns pack when found`() =
        runTest {
            val pack = store.createPack("Find Me", null, createTestQuestions(2))

            val found = store.packById(pack.id)

            assertNotNull(found)
            assertEquals(pack.id, found!!.id)
            assertEquals("Find Me", found.name)
        }

    @Test
    fun `packById returns null when not found`() {
        val found = store.packById("non-existent-id")
        assertNull(found)
    }

    // MARK: - Persistence Round-trip Tests

    @Test
    fun `full CRUD cycle persists correctly`() =
        runTest {
            // Create
            val pack1 = store.createPack("Pack 1", "First", createTestQuestions(3))
            val pack2 = store.createPack("Pack 2", "Second", createTestQuestions(5))

            // Update
            store.updatePack(pack1.id, name = "Updated Pack 1")

            // Delete
            store.deletePack(pack2.id)

            // Load in new store
            val store2 = KBLocalPackStore(context)
            store2.load()

            assertEquals(1, store2.localPacks.value.size)
            assertEquals("Updated Pack 1", store2.localPacks.value.first().name)
        }

    // MARK: - Helper Functions

    private fun createTestQuestions(count: Int): List<KBQuestion> {
        return (1..count).map { i ->
            createQuestion("test-q-$i")
        }
    }

    private fun createQuestion(
        id: String,
        domain: KBDomain = KBDomain.SCIENCE,
        difficulty: KBDifficulty = KBDifficulty.VARSITY,
    ): KBQuestion {
        return KBQuestion(
            id = id,
            text = "Question $id",
            answer = KBAnswer(primary = "Answer $id"),
            domain = domain,
            difficulty = difficulty,
        )
    }
}
