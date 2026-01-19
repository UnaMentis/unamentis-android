package com.unamentis.core.module

import androidx.compose.runtime.Composable
import com.unamentis.data.local.dao.ModuleDao
import com.unamentis.data.local.entity.DownloadedModuleEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModuleRegistryTest {
    private lateinit var moduleDao: ModuleDao
    private lateinit var json: Json
    private lateinit var registry: ModuleRegistry

    @Before
    fun setup() {
        moduleDao = mockk(relaxed = true)
        json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            }

        // Setup default flow to emit empty list
        every { moduleDao.getAllModules() } returns flowOf(emptyList())

        registry = ModuleRegistry(moduleDao, json)
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
    fun `hasUpdate returns true when newer version available`() {
        // Setup registry with a downloaded module
        val entity = createTestModuleEntity("test-module", "1.0.0")
        every { moduleDao.getAllModules() } returns flowOf(listOf(entity))

        val newRegistry = ModuleRegistry(moduleDao, json)

        // Give real time for IO dispatcher to collect flow
        Thread.sleep(200)

        assertTrue(newRegistry.hasUpdate("test-module", "2.0.0"))
        assertTrue(newRegistry.hasUpdate("test-module", "1.0.1"))
        assertTrue(newRegistry.hasUpdate("test-module", "1.1.0"))
    }

    @Test
    fun `hasUpdate returns false when same or older version`() {
        val entity = createTestModuleEntity("test-module", "2.0.0")
        every { moduleDao.getAllModules() } returns flowOf(listOf(entity))

        val newRegistry = ModuleRegistry(moduleDao, json)

        // Give real time for IO dispatcher to collect flow
        Thread.sleep(200)

        assertFalse(newRegistry.hasUpdate("test-module", "2.0.0"))
        assertFalse(newRegistry.hasUpdate("test-module", "1.9.9"))
        assertFalse(newRegistry.hasUpdate("test-module", "1.0.0"))
    }

    @Test
    fun `registerDownloadedSuspend inserts module into database`() =
        runTest {
            val module = createTestDownloadedModule("test-module", "1.0.0")
            val entitySlot = slot<DownloadedModuleEntity>()
            coEvery { moduleDao.insertModule(capture(entitySlot)) } returns Unit

            registry.registerDownloadedSuspend(module)

            coVerify { moduleDao.insertModule(any()) }
            assertEquals("test-module", entitySlot.captured.id)
            assertEquals("1.0.0", entitySlot.captured.version)
        }

    @Test
    fun `removeDownloadedSuspend deletes module from database`() =
        runTest {
            coEvery { moduleDao.deleteModule("test-module") } returns 1

            val result = registry.removeDownloadedSuspend("test-module")

            assertTrue(result)
            coVerify { moduleDao.deleteModule("test-module") }
        }

    @Test
    fun `removeDownloadedSuspend returns false when module not found`() =
        runTest {
            coEvery { moduleDao.deleteModule("non-existent") } returns 0

            val result = registry.removeDownloadedSuspend("non-existent")

            assertFalse(result)
        }

    @Test
    fun `clearAllSuspend deletes all modules`() =
        runTest {
            coEvery { moduleDao.deleteAllModules() } returns Unit

            registry.clearAllSuspend()

            coVerify { moduleDao.deleteAllModules() }
        }

    @Test
    fun `updateLastAccessed updates timestamp`() =
        runTest {
            coEvery { moduleDao.updateLastAccessed(any(), any()) } returns Unit

            registry.updateLastAccessed("test-module")

            coVerify { moduleDao.updateLastAccessed("test-module", any()) }
        }

    @Test
    fun `getTotalStorageUsed returns value from dao`() =
        runTest {
            coEvery { moduleDao.getTotalStorageUsed() } returns 1024L

            val result = registry.getTotalStorageUsed()

            assertEquals(1024L, result)
        }

    @Test
    fun `getTotalStorageUsed returns 0 on error`() =
        runTest {
            coEvery { moduleDao.getTotalStorageUsed() } throws RuntimeException("DB error")

            val result = registry.getTotalStorageUsed()

            assertEquals(0L, result)
        }

    @Test
    fun `version comparison handles major version differences`() {
        val entity = createTestModuleEntity("test-module", "1.0.0")
        every { moduleDao.getAllModules() } returns flowOf(listOf(entity))

        val newRegistry = ModuleRegistry(moduleDao, json)

        // Give real time for IO dispatcher to collect flow
        Thread.sleep(200)

        assertTrue(newRegistry.hasUpdate("test-module", "2.0.0"))
        assertFalse(newRegistry.hasUpdate("test-module", "0.9.0"))
    }

    @Test
    fun `version comparison handles minor version differences`() {
        val entity = createTestModuleEntity("test-module", "1.5.0")
        every { moduleDao.getAllModules() } returns flowOf(listOf(entity))

        val newRegistry = ModuleRegistry(moduleDao, json)

        // Give real time for IO dispatcher to collect flow
        Thread.sleep(200)

        assertTrue(newRegistry.hasUpdate("test-module", "1.6.0"))
        assertFalse(newRegistry.hasUpdate("test-module", "1.4.0"))
    }

    @Test
    fun `version comparison handles patch version differences`() {
        val entity = createTestModuleEntity("test-module", "1.0.5")
        every { moduleDao.getAllModules() } returns flowOf(listOf(entity))

        val newRegistry = ModuleRegistry(moduleDao, json)

        // Give real time for IO dispatcher to collect flow
        Thread.sleep(200)

        assertTrue(newRegistry.hasUpdate("test-module", "1.0.6"))
        assertFalse(newRegistry.hasUpdate("test-module", "1.0.4"))
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
    ): DownloadedModuleEntity {
        return DownloadedModuleEntity(
            id = id,
            name = "Test Module",
            version = version,
            description = "Test description",
            downloadedAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis(),
            contentJson = "{}",
            configJson = null,
            sizeBytes = 100L,
        )
    }
}
