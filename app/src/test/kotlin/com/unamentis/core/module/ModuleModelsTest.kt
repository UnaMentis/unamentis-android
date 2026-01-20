package com.unamentis.core.module

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModuleModelsTest {
    private lateinit var json: Json

    @Before
    fun setup() {
        json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            }
    }

    @Test
    fun `ModuleSummary serialization roundtrip`() {
        val summary =
            ModuleSummary(
                id = "knowledge-bowl",
                name = "Knowledge Bowl",
                description = "Academic competition preparation",
                version = "1.0.0",
                iconName = "brain",
                themeColorHex = "#4A90D9",
                enabled = true,
                supportsTeamMode = true,
                supportsSpeedTraining = true,
                supportsCompetitionSim = false,
                downloadSizeBytes = 1024000L,
                isInstalled = false,
            )

        val encoded = json.encodeToString(summary)
        val decoded = json.decodeFromString<ModuleSummary>(encoded)

        assertEquals(summary.id, decoded.id)
        assertEquals(summary.name, decoded.name)
        assertEquals(summary.version, decoded.version)
        assertEquals(summary.supportsTeamMode, decoded.supportsTeamMode)
        assertEquals(summary.downloadSizeBytes, decoded.downloadSizeBytes)
    }

    @Test
    fun `ModuleSummary default values`() {
        val summary =
            ModuleSummary(
                id = "test",
                name = "Test",
                description = "Test module",
                version = "1.0.0",
            )

        assertEquals("extension", summary.iconName)
        assertEquals("#6200EE", summary.themeColorHex)
        assertTrue(summary.enabled)
        assertEquals(false, summary.supportsTeamMode)
        assertNull(summary.downloadSizeBytes)
    }

    @Test
    fun `ModuleDetail serialization roundtrip`() {
        val detail =
            ModuleDetail(
                id = "knowledge-bowl",
                name = "Knowledge Bowl",
                description = "Academic competition prep",
                longDescription = "Detailed description of the module",
                version = "1.0.0",
                domains =
                    listOf(
                        ModuleDomain(
                            id = "science",
                            name = "Science",
                            weight = 0.2f,
                            questionCount = 100,
                        ),
                    ),
                studyModes =
                    listOf(
                        ModuleStudyMode(
                            id = "written",
                            name = "Written Practice",
                            questionCount = 60,
                        ),
                    ),
                totalQuestions = 500,
                estimatedStudyHours = 20f,
            )

        val encoded = json.encodeToString(detail)
        val decoded = json.decodeFromString<ModuleDetail>(encoded)

        assertEquals(detail.id, decoded.id)
        assertEquals(detail.domains.size, decoded.domains.size)
        assertEquals(detail.totalQuestions, decoded.totalQuestions)
    }

    @Test
    fun `ModuleDomain serialization`() {
        val domain =
            ModuleDomain(
                id = "science",
                name = "Science",
                iconName = "science",
                weight = 0.2f,
                subcategories = listOf("Biology", "Chemistry", "Physics"),
                questionCount = 100,
            )

        val encoded = json.encodeToString(domain)
        val decoded = json.decodeFromString<ModuleDomain>(encoded)

        assertEquals(domain.id, decoded.id)
        assertEquals(domain.subcategories.size, decoded.subcategories.size)
        assertEquals(domain.weight, decoded.weight, 0.001f)
    }

    @Test
    fun `ModuleStudyMode serialization`() {
        val mode =
            ModuleStudyMode(
                id = "oral",
                name = "Oral Practice",
                description = "Voice-based practice",
                questionCount = 50,
                timeLimitSeconds = 15,
                allowHints = false,
                shuffleQuestions = true,
            )

        val encoded = json.encodeToString(mode)
        val decoded = json.decodeFromString<ModuleStudyMode>(encoded)

        assertEquals(mode.id, decoded.id)
        assertEquals(mode.timeLimitSeconds, decoded.timeLimitSeconds)
        assertEquals(mode.allowHints, decoded.allowHints)
    }

    @Test
    fun `ModuleSettings serialization`() {
        val settings =
            ModuleSettings(
                defaultTimePerQuestion = 25f,
                conferenceTimeSeconds = 20f,
                enableSpokenQuestions = true,
                enableSpokenAnswers = false,
                minimumMasteryForCompletion = 0.85f,
            )

        val encoded = json.encodeToString(settings)
        val decoded = json.decodeFromString<ModuleSettings>(encoded)

        assertEquals(settings.defaultTimePerQuestion, decoded.defaultTimePerQuestion, 0.001f)
        assertEquals(settings.conferenceTimeSeconds, decoded.conferenceTimeSeconds)
        assertEquals(settings.enableSpokenAnswers, decoded.enableSpokenAnswers)
    }

    @Test
    fun `ModuleContent serialization`() {
        val content =
            ModuleContent(
                questionsJson = """[{"id": "q1", "text": "What is 2+2?"}]""",
                configJson = """{"difficulty": "medium"}""",
                domains =
                    listOf(
                        ModuleDomain(
                            id = "math",
                            name = "Mathematics",
                            questionCount = 50,
                        ),
                    ),
                totalQuestions = 50,
            )

        val encoded = json.encodeToString(content)
        val decoded = json.decodeFromString<ModuleContent>(encoded)

        assertEquals(content.questionsJson, decoded.questionsJson)
        assertEquals(content.totalQuestions, decoded.totalQuestions)
    }

    @Test
    fun `DownloadedModule serialization roundtrip`() {
        val module =
            DownloadedModule(
                id = "knowledge-bowl",
                name = "Knowledge Bowl",
                version = "1.0.0",
                description = "Academic competition prep",
                downloadedAt = 1700000000000L,
                content =
                    ModuleContent(
                        totalQuestions = 500,
                        domains =
                            listOf(
                                ModuleDomain(
                                    id = "science",
                                    name = "Science",
                                    questionCount = 100,
                                ),
                            ),
                    ),
            )

        val encoded = json.encodeToString(module)
        val decoded = json.decodeFromString<DownloadedModule>(encoded)

        assertEquals(module.id, decoded.id)
        assertEquals(module.version, decoded.version)
        assertEquals(module.totalQuestions, decoded.totalQuestions)
        assertEquals(module.domains.size, decoded.domains.size)
    }

    @Test
    fun `DownloadedModule computed properties`() {
        val module =
            DownloadedModule(
                id = "test",
                name = "Test",
                version = "1.0.0",
                content =
                    ModuleContent(
                        totalQuestions = 150,
                        domains =
                            listOf(
                                ModuleDomain(id = "a", name = "A", questionCount = 100),
                                ModuleDomain(id = "b", name = "B", questionCount = 50),
                            ),
                        studyModes =
                            listOf(
                                ModuleStudyMode(id = "practice", name = "Practice"),
                            ),
                    ),
            )

        assertEquals(150, module.totalQuestions)
        assertEquals(2, module.domains.size)
        assertEquals(1, module.studyModes.size)
    }

    @Test
    fun `ModuleListResponse serialization`() {
        val response =
            ModuleListResponse(
                modules =
                    listOf(
                        ModuleSummary(
                            id = "kb",
                            name = "KB",
                            description = "Test",
                            version = "1.0.0",
                        ),
                    ),
                total = 1,
            )

        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<ModuleListResponse>(encoded)

        assertEquals(1, decoded.total)
        assertEquals("kb", decoded.modules[0].id)
    }

    @Test
    fun `ModuleDownloadRequest serialization`() {
        val request =
            ModuleDownloadRequest(
                moduleId = "knowledge-bowl",
                includeAudio = true,
            )

        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<ModuleDownloadRequest>(encoded)

        assertEquals(request.moduleId, decoded.moduleId)
        assertEquals(request.includeAudio, decoded.includeAudio)
    }

    @Test
    fun `ModuleDownloadProgress properties`() {
        val progress =
            ModuleDownloadProgress(
                moduleId = "test",
                totalBytes = 1000L,
                downloadedBytes = 500L,
                status = ModuleDownloadProgress.DownloadStatus.DOWNLOADING,
            )

        assertEquals(0.5f, progress.progress, 0.001f)
    }

    @Test
    fun `ModuleDownloadProgress zero total bytes`() {
        val progress =
            ModuleDownloadProgress(
                moduleId = "test",
                totalBytes = 0L,
                downloadedBytes = 0L,
                status = ModuleDownloadProgress.DownloadStatus.PENDING,
            )

        assertEquals(0f, progress.progress, 0.001f)
    }

    @Test
    fun `ModuleServiceError types`() {
        val notConfigured = ModuleServiceError.NotConfigured
        val networkError = ModuleServiceError.NetworkError("Connection refused")
        val invalidResponse = ModuleServiceError.InvalidResponse
        val serverError = ModuleServiceError.ServerError(500)
        val notFound = ModuleServiceError.ModuleNotFound("test-id")
        val downloadFailed = ModuleServiceError.DownloadFailed("Failed to download")
        val decodingError = ModuleServiceError.DecodingError("Invalid JSON")

        assertTrue(notConfigured is ModuleServiceError)
        assertEquals("Connection refused", networkError.message)
        assertTrue(invalidResponse is ModuleServiceError)
        assertEquals(500, serverError.code)
        assertEquals("test-id", notFound.moduleId)
        assertEquals("Failed to download", downloadFailed.message)
        assertEquals("Invalid JSON", decodingError.message)
    }
}
