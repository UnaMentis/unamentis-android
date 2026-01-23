package com.unamentis.core.module

import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import com.unamentis.data.local.AppDatabase
import com.unamentis.data.local.dao.ModuleDao
import com.unamentis.data.local.entity.DownloadedModuleEntity
import io.mockk.clearAllMocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
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
import org.robolectric.annotation.Config

/**
 * Unit tests for ModuleRegistry.
 *
 * Uses Robolectric with real in-memory Room database for testing
 * database interactions, following the project's testing philosophy
 * of using real implementations over mocks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModuleRegistryTest {
    private lateinit var database: AppDatabase
    private lateinit var moduleDao: ModuleDao
    private lateinit var json: Json
    private lateinit var registry: ModuleRegistry

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Create real in-memory Room database
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        moduleDao = database.moduleDao()

        json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            }

        // Inject testScope to control coroutines launched in ModuleRegistry's init block
        registry = ModuleRegistry(moduleDao, json, externalScope = testScope)
    }

    @After
    fun tearDown() {
        // Close the registry to cancel its coroutines
        registry.close()
        // Reset Main dispatcher
        Dispatchers.resetMain()
        // Close the in-memory database
        database.close()
        // Clear any mocks that may have been created in tests
        clearAllMocks()
    }

    @Test
    fun `initial state has empty downloaded modules`() {
        assertTrue(registry.getAllDownloaded().isEmpty())
        assertFalse(registry.isDownloaded("any-module"))
    }

    @Test
    fun `registerImplementation adds module implementation`() {
        val testModule = createTestModuleProtocol("test-module")

        registry.registerImplementation(testModule)

        val impl = registry.getImplementation("test-module")
        assertNotNull(impl)
        assertEquals("test-module", impl?.moduleId)
    }

    @Test
    fun `getImplementation returns null for unregistered module`() {
        assertNull(registry.getImplementation("non-existent"))
    }

    @Test
    fun `unregisterImplementation removes module implementation`() {
        val testModule = createTestModuleProtocol("test-module")

        registry.registerImplementation(testModule)
        assertNotNull(registry.getImplementation("test-module"))

        registry.unregisterImplementation("test-module")
        assertNull(registry.getImplementation("test-module"))
    }

    @Test
    fun `getAllImplementations returns all registered implementations`() {
        val module1 = createTestModuleProtocol("module-1")
        val module2 = createTestModuleProtocol("module-2")

        registry.registerImplementation(module1)
        registry.registerImplementation(module2)

        val implementations = registry.getAllImplementations()
        assertEquals(2, implementations.size)
        assertTrue(implementations.any { it.moduleId == "module-1" })
        assertTrue(implementations.any { it.moduleId == "module-2" })
    }

    @Test
    fun `hasUpdate returns false when module not downloaded`() {
        assertFalse(registry.hasUpdate("non-existent", "1.0.0"))
    }

    @Test
    fun `hasUpdate returns true when newer version available`() =
        runTest {
            // Insert module directly into the database
            val entity = createTestModuleEntity("test-module", "1.0.0")
            moduleDao.insertModule(entity)

            // Create new registry with a fresh test scope to pick up the inserted module
            val newTestScope = TestScope(testDispatcher)
            val newRegistry = ModuleRegistry(moduleDao, json, externalScope = newTestScope)

            try {
                // Await for IO dispatcher to collect flow
                awaitCondition { newRegistry.isDownloaded("test-module") }

                assertTrue(newRegistry.hasUpdate("test-module", "2.0.0"))
                assertTrue(newRegistry.hasUpdate("test-module", "1.0.1"))
                assertTrue(newRegistry.hasUpdate("test-module", "1.1.0"))
            } finally {
                newRegistry.close()
            }
        }

    @Test
    fun `hasUpdate returns false when same or older version`() =
        runTest {
            // Insert module directly into the database
            val entity = createTestModuleEntity("test-module", "2.0.0")
            moduleDao.insertModule(entity)

            // Create new registry with a fresh test scope to pick up the inserted module
            val newTestScope = TestScope(testDispatcher)
            val newRegistry = ModuleRegistry(moduleDao, json, externalScope = newTestScope)

            try {
                // Await for IO dispatcher to collect flow
                awaitCondition { newRegistry.isDownloaded("test-module") }

                assertFalse(newRegistry.hasUpdate("test-module", "2.0.0"))
                assertFalse(newRegistry.hasUpdate("test-module", "1.9.9"))
                assertFalse(newRegistry.hasUpdate("test-module", "1.0.0"))
            } finally {
                newRegistry.close()
            }
        }

    @Test
    fun `registerDownloadedSuspend inserts module into database`() =
        runTest {
            val module = createTestDownloadedModule("test-module", "1.0.0")

            registry.registerDownloadedSuspend(module)

            // Verify module was inserted by retrieving it
            val allModules = moduleDao.getAllModulesOnce()
            assertEquals(1, allModules.size)
            assertEquals("test-module", allModules[0].id)
            assertEquals("1.0.0", allModules[0].version)
        }

    @Test
    fun `removeDownloadedSuspend deletes module from database`() =
        runTest {
            // First insert a module
            val entity = createTestModuleEntity("test-module", "1.0.0")
            moduleDao.insertModule(entity)

            // Verify it was inserted
            assertEquals(1, moduleDao.getAllModulesOnce().size)

            // Now delete it
            val result = registry.removeDownloadedSuspend("test-module")

            assertTrue(result)
            assertEquals(0, moduleDao.getAllModulesOnce().size)
        }

    @Test
    fun `removeDownloadedSuspend returns false when module not found`() =
        runTest {
            val result = registry.removeDownloadedSuspend("non-existent")

            assertFalse(result)
        }

    @Test
    fun `clearAllSuspend deletes all modules`() =
        runTest {
            // Insert multiple modules
            moduleDao.insertModule(createTestModuleEntity("module-1", "1.0.0"))
            moduleDao.insertModule(createTestModuleEntity("module-2", "1.0.0"))

            // Verify they were inserted
            assertEquals(2, moduleDao.getAllModulesOnce().size)

            // Clear all
            registry.clearAllSuspend()

            assertEquals(0, moduleDao.getAllModulesOnce().size)
        }

    @Test
    fun `updateLastAccessed updates timestamp`() =
        runTest {
            // Insert a module
            val originalTimestamp = System.currentTimeMillis() - 10000
            val entity = createTestModuleEntity("test-module", "1.0.0", lastAccessedAt = originalTimestamp)
            moduleDao.insertModule(entity)

            // Update last accessed
            registry.updateLastAccessed("test-module")

            // Verify timestamp was updated
            val updated = moduleDao.getAllModulesOnce().first()
            assertTrue(updated.lastAccessedAt > originalTimestamp)
        }

    @Test
    fun `getTotalStorageUsed returns sum of all module sizes`() =
        runTest {
            // Insert modules with known sizes
            moduleDao.insertModule(createTestModuleEntity("module-1", "1.0.0", sizeBytes = 500L))
            moduleDao.insertModule(createTestModuleEntity("module-2", "1.0.0", sizeBytes = 524L))

            val result = registry.getTotalStorageUsed()

            assertEquals(1024L, result)
        }

    @Test
    fun `getTotalStorageUsed returns 0 when no modules`() =
        runTest {
            val result = registry.getTotalStorageUsed()

            assertEquals(0L, result)
        }

    @Test
    fun `version comparison handles major version differences`() =
        runTest {
            val entity = createTestModuleEntity("test-module", "1.0.0")
            moduleDao.insertModule(entity)

            val newTestScope = TestScope(testDispatcher)
            val newRegistry = ModuleRegistry(moduleDao, json, externalScope = newTestScope)

            try {
                // Await for IO dispatcher to collect flow
                awaitCondition { newRegistry.isDownloaded("test-module") }

                assertTrue(newRegistry.hasUpdate("test-module", "2.0.0"))
                assertFalse(newRegistry.hasUpdate("test-module", "0.9.0"))
            } finally {
                newRegistry.close()
            }
        }

    @Test
    fun `version comparison handles minor version differences`() =
        runTest {
            val entity = createTestModuleEntity("test-module", "1.5.0")
            moduleDao.insertModule(entity)

            val newTestScope = TestScope(testDispatcher)
            val newRegistry = ModuleRegistry(moduleDao, json, externalScope = newTestScope)

            try {
                // Await for IO dispatcher to collect flow
                awaitCondition { newRegistry.isDownloaded("test-module") }

                assertTrue(newRegistry.hasUpdate("test-module", "1.6.0"))
                assertFalse(newRegistry.hasUpdate("test-module", "1.4.0"))
            } finally {
                newRegistry.close()
            }
        }

    @Test
    fun `version comparison handles patch version differences`() =
        runTest {
            val entity = createTestModuleEntity("test-module", "1.0.5")
            moduleDao.insertModule(entity)

            val newTestScope = TestScope(testDispatcher)
            val newRegistry = ModuleRegistry(moduleDao, json, externalScope = newTestScope)

            try {
                // Await for IO dispatcher to collect flow
                awaitCondition { newRegistry.isDownloaded("test-module") }

                assertTrue(newRegistry.hasUpdate("test-module", "1.0.6"))
                assertFalse(newRegistry.hasUpdate("test-module", "1.0.4"))
            } finally {
                newRegistry.close()
            }
        }

    private fun createTestModuleProtocol(id: String): ModuleProtocol {
        return object : ModuleProtocol {
            override val moduleId = id
            override val moduleName = "Test Module"
            override val moduleVersion = "1.0.0"
            override val shortDescription = "Test description"

            override suspend fun initialize() {}

            @Composable
            override fun getUIEntryPoint() {
            }
        }
    }

    private fun createTestDownloadedModule(
        id: String,
        version: String,
    ): DownloadedModule {
        return DownloadedModule(
            id = id,
            name = "Test Module",
            version = version,
            description = "Test description",
            downloadedAt = System.currentTimeMillis(),
            content = ModuleContent(),
        )
    }

    private fun createTestModuleEntity(
        id: String,
        version: String,
        lastAccessedAt: Long = System.currentTimeMillis(),
        sizeBytes: Long = 100L,
    ): DownloadedModuleEntity {
        return DownloadedModuleEntity(
            id = id,
            name = "Test Module",
            version = version,
            description = "Test description",
            downloadedAt = System.currentTimeMillis(),
            lastAccessedAt = lastAccessedAt,
            contentJson = "{}",
            configJson = null,
            sizeBytes = sizeBytes,
        )
    }

    /**
     * Coroutine-friendly polling helper that awaits a condition with timeout.
     * Uses real time delays on Dispatchers.Default to allow IO operations
     * (like Flow collection on Dispatchers.IO) to complete.
     *
     * @param timeoutMs Maximum time to wait for the condition in milliseconds
     * @param pollIntervalMs Time between condition checks in milliseconds
     * @param condition The condition to check, returns true when satisfied
     */
    private suspend fun awaitCondition(
        timeoutMs: Long = 1000L,
        pollIntervalMs: Long = 10L,
        condition: () -> Boolean,
    ) {
        withContext(Dispatchers.Default) {
            withTimeout(timeoutMs) {
                while (!condition()) {
                    delay(pollIntervalMs)
                }
            }
        }
    }
}
