package com.unamentis.core.module

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModuleServiceTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var context: Context
    private lateinit var registry: ModuleRegistry
    private lateinit var json: Json
    private lateinit var service: ModuleService

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        okHttpClient = OkHttpClient.Builder().build()
        context = mockk(relaxed = true)
        registry = mockk(relaxed = true)
        json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            }

        service = ModuleService(context, okHttpClient, registry, json)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `initial state is not configured`() {
        assertFalse(service.isConfigured.value)
        assertTrue(service.availableModules.value.isEmpty())
        assertFalse(service.isLoading.value)
    }

    @Test
    fun `configure sets isConfigured to true`() {
        service.configure("localhost", 8766)

        assertTrue(service.isConfigured.value)
    }

    @Test
    fun `configure with default port`() {
        service.configure("localhost")

        assertTrue(service.isConfigured.value)
    }

    @Test
    fun `fetchAvailableModules throws when not configured`() =
        runTest {
            try {
                service.fetchAvailableModules()
                assertTrue("Should have thrown", false)
            } catch (_: ModuleServiceError.NotConfigured) {
                // Expected - service should throw when not configured
            }
        }

    @Test
    fun `fetchAvailableModules returns modules on success`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port
            service.configure(host, port)

            val expectedModules =
                listOf(
                    ModuleSummary(
                        id = "knowledge-bowl",
                        name = "Knowledge Bowl",
                        description = "Academic competition prep",
                        version = "1.0.0",
                    ),
                )

            val responseBody = json.encodeToString(ModuleListResponse(expectedModules, 1))
            mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

            every { registry.isDownloaded(any()) } returns false

            val result = service.fetchAvailableModules()

            assertEquals(1, result.size)
            assertEquals("knowledge-bowl", result[0].id)
            assertEquals("Knowledge Bowl", result[0].name)
        }

    @Test
    fun `fetchAvailableModules marks installed modules`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port
            service.configure(host, port)

            val modules =
                listOf(
                    ModuleSummary(
                        id = "knowledge-bowl",
                        name = "Knowledge Bowl",
                        description = "Academic competition prep",
                        version = "1.0.0",
                        isInstalled = false,
                    ),
                )

            val responseBody = json.encodeToString(ModuleListResponse(modules, 1))
            mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

            every { registry.isDownloaded("knowledge-bowl") } returns true

            val result = service.fetchAvailableModules()

            assertTrue(result[0].isInstalled)
        }

    @Test
    fun `fetchAvailableModules updates availableModules state`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port
            service.configure(host, port)

            val modules =
                listOf(
                    ModuleSummary(
                        id = "knowledge-bowl",
                        name = "Knowledge Bowl",
                        description = "Test",
                        version = "1.0.0",
                    ),
                )

            val responseBody = json.encodeToString(ModuleListResponse(modules, 1))
            mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

            every { registry.isDownloaded(any()) } returns false

            service.fetchAvailableModules()

            assertEquals(1, service.availableModules.value.size)
        }

    @Test
    fun `fetchAvailableModules handles server error`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port
            service.configure(host, port)

            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            try {
                service.fetchAvailableModules()
                assertTrue("Should have thrown", false)
            } catch (e: ModuleServiceError.ServerError) {
                assertEquals(500, e.code)
            }

            assertTrue(service.lastError.value is ModuleServiceError.ServerError)
        }

    @Test
    fun `fetchAvailableModules handles 404 error`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port
            service.configure(host, port)

            mockWebServer.enqueue(MockResponse().setResponseCode(404))

            try {
                service.fetchAvailableModules()
                assertTrue("Should have thrown", false)
            } catch (_: ModuleServiceError.ModuleNotFound) {
                // Expected - 404 should throw ModuleNotFound
            }
        }

    @Test
    fun `fetchModuleDetail returns module on success`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port
            service.configure(host, port)

            val detail =
                ModuleDetail(
                    id = "knowledge-bowl",
                    name = "Knowledge Bowl",
                    description = "Academic competition prep",
                    version = "1.0.0",
                    totalQuestions = 500,
                )

            val responseBody = json.encodeToString(detail)
            mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

            val result = service.fetchModuleDetail("knowledge-bowl")

            assertEquals("knowledge-bowl", result.id)
            assertEquals(500, result.totalQuestions)
        }

    @Test
    fun `fetchModuleDetail handles module not found`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port
            service.configure(host, port)

            mockWebServer.enqueue(MockResponse().setResponseCode(404))

            try {
                service.fetchModuleDetail("non-existent")
                assertTrue("Should have thrown", false)
            } catch (_: ModuleServiceError.ModuleNotFound) {
                // Expected - 404 should throw ModuleNotFound
            }
        }

    @Test
    fun `downloadModule returns module on success`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port
            service.configure(host, port)

            val downloadedModule =
                DownloadedModule(
                    id = "knowledge-bowl",
                    name = "Knowledge Bowl",
                    version = "1.0.0",
                    description = "Test",
                    content = ModuleContent(totalQuestions = 100),
                )

            val responseBody = json.encodeToString(downloadedModule)
            mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

            val result = service.downloadModule("knowledge-bowl")

            assertEquals("knowledge-bowl", result.id)
        }

    @Test
    fun `downloadModule handles download failure`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port
            service.configure(host, port)

            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            try {
                service.downloadModule("knowledge-bowl")
                assertTrue("Should have thrown", false)
            } catch (e: ModuleServiceError.ServerError) {
                assertEquals(500, e.code)
            }
        }

    @Test
    fun `deleteModule delegates to registry`() {
        every { registry.removeDownloaded("test-module") } returns true

        val result = service.deleteModule("test-module")

        assertTrue(result)
    }

    @Test
    fun `hasUpdateAvailable checks against available modules`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port
            service.configure(host, port)

            val modules =
                listOf(
                    ModuleSummary(
                        id = "knowledge-bowl",
                        name = "Knowledge Bowl",
                        description = "Test",
                        version = "2.0.0",
                    ),
                )

            val responseBody = json.encodeToString(ModuleListResponse(modules, 1))
            mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

            every { registry.isDownloaded(any()) } returns false
            every { registry.hasUpdate("knowledge-bowl", "2.0.0") } returns true

            service.fetchAvailableModules()

            assertTrue(service.hasUpdateAvailable("knowledge-bowl"))
        }

    @Test
    fun `hasUpdateAvailable returns false for unknown module`() {
        assertFalse(service.hasUpdateAvailable("non-existent"))
    }

    @Test
    fun `refresh returns true on success`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port
            service.configure(host, port)

            val responseBody = json.encodeToString(ModuleListResponse(emptyList(), 0))
            mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

            val result = service.refresh()

            assertTrue(result)
        }

    @Test
    fun `refresh returns false on failure`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port
            service.configure(host, port)

            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            val result = service.refresh()

            assertFalse(result)
        }

    @Test
    fun `lastError is cleared on configure`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port
            service.configure(host, port)

            // Cause an error
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            try {
                service.fetchAvailableModules()
            } catch (_: ModuleServiceError) {
                // Expected - 500 should throw ModuleServiceError
            }

            assertTrue(service.lastError.value != null)

            // Reconfigure should clear error
            service.configure(host, port)

            assertTrue(service.lastError.value == null)
        }
}
