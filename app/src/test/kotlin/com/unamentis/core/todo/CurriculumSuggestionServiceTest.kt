package com.unamentis.core.todo

import com.unamentis.core.config.ServerConfigManager
import com.unamentis.data.local.dao.CurriculumDao
import com.unamentis.data.local.entity.CurriculumEntity
import com.unamentis.data.model.Todo
import com.unamentis.data.model.TodoItemType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CurriculumSuggestionServiceTest {
    private lateinit var curriculumDao: CurriculumDao
    private lateinit var serverConfigManager: ServerConfigManager
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var json: Json
    private lateinit var service: CurriculumSuggestionService

    @Before
    fun setup() {
        curriculumDao = mockk(relaxed = true)
        serverConfigManager = mockk(relaxed = true)
        okHttpClient = mockk(relaxed = true)
        json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        service =
            CurriculumSuggestionService(
                curriculumDao = curriculumDao,
                serverConfigManager = serverConfigManager,
                okHttpClient = okHttpClient,
                json = json,
            )
    }

    // region Local Suggestions

    @Test
    fun `localSuggestions finds matching curricula by title`() =
        runTest {
            val curricula =
                listOf(
                    createCurriculum("c1", "Introduction to Calculus", "Math course"),
                    createCurriculum("c2", "Biology 101", "Life science course"),
                    createCurriculum("c3", "Advanced Calculus", "Advanced math"),
                )
            coEvery { curriculumDao.getAllCurriculaList() } returns curricula

            val result = service.localSuggestions("calculus")

            assertEquals(2, result.size)
            assertTrue(result.contains("c1"))
            assertTrue(result.contains("c3"))
        }

    @Test
    fun `localSuggestions finds matching curricula by description`() =
        runTest {
            val curricula =
                listOf(
                    createCurriculum("c1", "Physics", "Study of mechanics and motion"),
                    createCurriculum("c2", "Chemistry", "Study of matter and reactions"),
                )
            coEvery { curriculumDao.getAllCurriculaList() } returns curricula

            val result = service.localSuggestions("mechanics")

            assertEquals(1, result.size)
            assertEquals("c1", result[0])
        }

    @Test
    fun `localSuggestions is case insensitive`() =
        runTest {
            val curricula =
                listOf(
                    createCurriculum("c1", "CALCULUS", "MATH"),
                )
            coEvery { curriculumDao.getAllCurriculaList() } returns curricula

            val result = service.localSuggestions("calculus")

            assertEquals(1, result.size)
        }

    @Test
    fun `localSuggestions returns empty for no matches`() =
        runTest {
            val curricula =
                listOf(
                    createCurriculum("c1", "Physics", "Force and motion"),
                )
            coEvery { curriculumDao.getAllCurriculaList() } returns curricula

            val result = service.localSuggestions("chemistry")

            assertTrue(result.isEmpty())
        }

    @Test
    fun `localSuggestions limits results to 5`() =
        runTest {
            val curricula =
                (1..10).map { i ->
                    createCurriculum("c$i", "Math $i", "Mathematics course $i")
                }
            coEvery { curriculumDao.getAllCurriculaList() } returns curricula

            val result = service.localSuggestions("math")

            assertEquals(5, result.size)
        }

    @Test
    fun `localSuggestions handles empty query`() =
        runTest {
            val result = service.localSuggestions("")

            assertTrue(result.isEmpty())
        }

    @Test
    fun `localSuggestions matches any word in multi-word query`() =
        runTest {
            val curricula =
                listOf(
                    createCurriculum("c1", "Introduction to Physics", "Mechanics basics"),
                    createCurriculum("c2", "Calculus", "Introduction to derivatives"),
                    createCurriculum("c3", "Biology", "No match here"),
                )
            coEvery { curriculumDao.getAllCurriculaList() } returns curricula

            val result = service.localSuggestions("introduction physics")

            // Should match c1 (has both words in title) and c2 (has "introduction" in description)
            assertTrue(result.contains("c1"))
            assertTrue(result.contains("c2"))
        }

    // endregion

    // region updateTodoWithSuggestions

    @Test
    fun `updateTodoWithSuggestions skips non-learning-target items`() =
        runTest {
            val todo =
                Todo(
                    id = "r1",
                    title = "Review topic",
                    itemType = TodoItemType.REINFORCEMENT,
                )

            service.updateTodoWithSuggestions(todo)

            coVerify(exactly = 0) { curriculumDao.getAllCurriculaList() }
        }

    @Test
    fun `updateTodoWithSuggestions calls updater when suggestions found`() =
        runTest {
            var updatedId: String? = null
            var updatedIds: String? = null
            service.internalTodoUpdater = { id, ids ->
                updatedId = id
                updatedIds = ids
            }

            val curricula =
                listOf(
                    createCurriculum("c1", "Calculus Course", "Math"),
                )
            coEvery { curriculumDao.getAllCurriculaList() } returns curricula

            // Make server call fail so it falls back to local
            every { serverConfigManager.getManagementServerUrl() } returns "http://invalid:9999"

            val todo =
                Todo(
                    id = "lt1",
                    title = "Learn calculus",
                    itemType = TodoItemType.LEARNING_TARGET,
                )

            service.updateTodoWithSuggestions(todo)

            assertEquals("lt1", updatedId)
            assertTrue(updatedIds?.contains("c1") == true)
        }

    @Test
    fun `updateTodoWithSuggestions does not call updater when no suggestions`() =
        runTest {
            var updaterCalled = false
            service.internalTodoUpdater = { _, _ ->
                updaterCalled = true
            }

            coEvery { curriculumDao.getAllCurriculaList() } returns emptyList()

            // Make server call fail
            every { serverConfigManager.getManagementServerUrl() } returns "http://invalid:9999"

            val todo =
                Todo(
                    id = "lt1",
                    title = "Learn obscure topic xyz123",
                    itemType = TodoItemType.LEARNING_TARGET,
                )

            service.updateTodoWithSuggestions(todo)

            assertTrue(!updaterCalled)
        }

    // endregion

    // region fetchSuggestions fallback

    @Test
    fun `fetchSuggestions falls back to local on network error`() =
        runTest {
            // Server will fail (invalid URL)
            every { serverConfigManager.getManagementServerUrl() } returns "http://invalid:9999"

            val curricula =
                listOf(
                    createCurriculum("c1", "Calculus", "Math"),
                )
            coEvery { curriculumDao.getAllCurriculaList() } returns curricula

            val result = service.fetchSuggestions("calculus")

            assertEquals(1, result.size)
            assertEquals("c1", result[0])
        }

    // endregion

    // region Helper

    private fun createCurriculum(
        id: String,
        title: String,
        description: String,
    ): CurriculumEntity {
        return CurriculumEntity(
            id = id,
            title = title,
            description = description,
            version = "1.0",
            contentJson = "{}",
            downloadedAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis(),
            topicCount = 5,
        )
    }

    // endregion
}
