package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [KBPack], [KBPackDTO], and [KBPackStats].
 *
 * Tests serialization, computed properties, DTO-to-domain conversion,
 * and pack type parsing.
 */
class KBPackTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    // MARK: - KBPack Basic Tests

    @Test
    fun `KBPack can be created with required fields`() {
        val pack =
            KBPack(
                id = "test-pack-1",
                name = "Test Pack",
                questionCount = 50,
            )

        assertEquals("test-pack-1", pack.id)
        assertEquals("Test Pack", pack.name)
        assertEquals(50, pack.questionCount)
    }

    @Test
    fun `KBPack has correct defaults`() {
        val pack =
            KBPack(
                id = "test-pack",
                name = "Test",
                questionCount = 10,
            )

        assertNull(pack.description)
        assertEquals(emptyMap<String, Int>(), pack.domainDistribution)
        assertEquals(emptyMap<Int, Int>(), pack.difficultyDistribution)
        assertEquals(KBPack.PackType.CUSTOM, pack.packType)
        assertTrue(pack.isLocal)
        assertNull(pack.questionIds)
        assertNotNull(pack.createdAtMillis)
        assertNull(pack.updatedAtMillis)
    }

    @Test
    fun `KBPack can be created with all fields`() {
        val now = System.currentTimeMillis()
        val pack =
            KBPack(
                id = "full-pack",
                name = "Full Pack",
                description = "A full pack",
                questionCount = 100,
                domainDistribution = mapOf("science" to 40, "mathematics" to 30, "history" to 30),
                difficultyDistribution = mapOf(3 to 50, 4 to 50),
                packType = KBPack.PackType.SYSTEM,
                isLocal = false,
                questionIds = listOf("q1", "q2", "q3"),
                createdAtMillis = now,
                updatedAtMillis = now + 1000,
            )

        assertEquals("Full Pack", pack.name)
        assertEquals("A full pack", pack.description)
        assertEquals(100, pack.questionCount)
        assertEquals(3, pack.domainDistribution.size)
        assertEquals(KBPack.PackType.SYSTEM, pack.packType)
        assertFalse(pack.isLocal)
        assertEquals(3, pack.questionIds!!.size)
    }

    // MARK: - topDomains Tests

    @Test
    fun `topDomains returns domains sorted by count descending`() {
        val pack =
            KBPack(
                id = "test",
                name = "Test",
                questionCount = 100,
                domainDistribution =
                    mapOf(
                        "science" to 40,
                        "mathematics" to 30,
                        "history" to 20,
                        "literature" to 10,
                    ),
            )

        val topDomains = pack.topDomains
        assertEquals(4, topDomains.size)
        assertEquals(KBDomain.SCIENCE, topDomains[0])
        assertEquals(KBDomain.MATHEMATICS, topDomains[1])
        assertEquals(KBDomain.HISTORY, topDomains[2])
        assertEquals(KBDomain.LITERATURE, topDomains[3])
    }

    @Test
    fun `topDomains limits to 4 domains`() {
        val pack =
            KBPack(
                id = "test",
                name = "Test",
                questionCount = 120,
                domainDistribution =
                    mapOf(
                        "science" to 30,
                        "mathematics" to 25,
                        "history" to 20,
                        "literature" to 15,
                        "arts" to 15,
                        "language" to 15,
                    ),
            )

        val topDomains = pack.topDomains
        assertTrue(topDomains.size <= 4)
    }

    @Test
    fun `topDomains returns empty list when no distribution`() {
        val pack =
            KBPack(
                id = "test",
                name = "Test",
                questionCount = 0,
            )

        assertTrue(pack.topDomains.isEmpty())
    }

    @Test
    fun `topDomains skips unrecognized domain names`() {
        val pack =
            KBPack(
                id = "test",
                name = "Test",
                questionCount = 30,
                domainDistribution =
                    mapOf(
                        "science" to 20,
                        "unknown_domain" to 10,
                    ),
            )

        val topDomains = pack.topDomains
        assertEquals(1, topDomains.size)
        assertEquals(KBDomain.SCIENCE, topDomains[0])
    }

    // MARK: - PackType Tests

    @Test
    fun `PackType serializes correctly`() {
        val encoded = json.encodeToString(KBPack.PackType.SYSTEM)
        assertEquals("\"system\"", encoded)

        val decoded = json.decodeFromString<KBPack.PackType>("\"custom\"")
        assertEquals(KBPack.PackType.CUSTOM, decoded)
    }

    @Test
    fun `all PackType values serialize correctly`() {
        assertEquals("\"system\"", json.encodeToString(KBPack.PackType.SYSTEM))
        assertEquals("\"custom\"", json.encodeToString(KBPack.PackType.CUSTOM))
        assertEquals("\"bundle\"", json.encodeToString(KBPack.PackType.BUNDLE))
    }

    // MARK: - KBPack Serialization Tests

    @Test
    fun `KBPack serialization roundtrip works`() {
        val original =
            KBPack(
                id = "pack-1",
                name = "Science Pack",
                description = "All science questions",
                questionCount = 50,
                domainDistribution = mapOf("science" to 50),
                difficultyDistribution = mapOf(4 to 30, 5 to 20),
                packType = KBPack.PackType.SYSTEM,
                isLocal = false,
                questionIds = listOf("q1", "q2"),
                createdAtMillis = 1000L,
                updatedAtMillis = 2000L,
            )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<KBPack>(encoded)

        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(original.description, decoded.description)
        assertEquals(original.questionCount, decoded.questionCount)
        assertEquals(original.domainDistribution, decoded.domainDistribution)
        assertEquals(original.difficultyDistribution, decoded.difficultyDistribution)
        assertEquals(original.packType, decoded.packType)
        assertEquals(original.isLocal, decoded.isLocal)
        assertEquals(original.questionIds, decoded.questionIds)
    }

    @Test
    fun `KBPack serialized JSON uses snake_case keys`() {
        val pack =
            KBPack(
                id = "test",
                name = "Test",
                questionCount = 10,
                createdAtMillis = 1000L,
            )

        val encoded = json.encodeToString(pack)
        assertTrue(encoded.contains("\"question_count\""))
        assertTrue(encoded.contains("\"pack_type\""))
        assertTrue(encoded.contains("\"is_local\""))
        assertTrue(encoded.contains("\"created_at_millis\""))
    }

    // MARK: - KBPackDTO Tests

    @Test
    fun `KBPackDTO toPack converts correctly`() {
        val dto =
            KBPackDTO(
                id = "dto-1",
                name = "DTO Pack",
                description = "From server",
                packType = "system",
                questionIds = listOf("q1", "q2", "q3"),
                stats =
                    KBPackStats(
                        questionCount = 3,
                        domainDistribution = mapOf("science" to 2, "mathematics" to 1),
                        difficultyDistribution = mapOf(4 to 3),
                    ),
            )

        val pack = dto.toPack()

        assertEquals("dto-1", pack.id)
        assertEquals("DTO Pack", pack.name)
        assertEquals("From server", pack.description)
        assertEquals(3, pack.questionCount)
        assertEquals(mapOf("science" to 2, "mathematics" to 1), pack.domainDistribution)
        assertEquals(mapOf(4 to 3), pack.difficultyDistribution)
        assertEquals(KBPack.PackType.SYSTEM, pack.packType)
        assertFalse(pack.isLocal)
        assertEquals(listOf("q1", "q2", "q3"), pack.questionIds)
    }

    @Test
    fun `KBPackDTO toPack uses questionIds size when no stats`() {
        val dto =
            KBPackDTO(
                id = "dto-2",
                name = "No Stats",
                questionIds = listOf("q1", "q2"),
            )

        val pack = dto.toPack()
        assertEquals(2, pack.questionCount)
    }

    @Test
    fun `KBPackDTO toPack returns 0 count when no stats and no questionIds`() {
        val dto =
            KBPackDTO(
                id = "dto-3",
                name = "Empty",
            )

        val pack = dto.toPack()
        assertEquals(0, pack.questionCount)
    }

    @Test
    fun `KBPackDTO toPack parses custom pack type`() {
        val dto =
            KBPackDTO(
                id = "dto-4",
                name = "Custom",
                packType = "custom",
            )

        assertEquals(KBPack.PackType.CUSTOM, dto.toPack().packType)
    }

    @Test
    fun `KBPackDTO toPack parses bundle pack type`() {
        val dto =
            KBPackDTO(
                id = "dto-5",
                name = "Bundle",
                packType = "bundle",
            )

        assertEquals(KBPack.PackType.BUNDLE, dto.toPack().packType)
    }

    @Test
    fun `KBPackDTO toPack parses case-insensitive pack type`() {
        val dto =
            KBPackDTO(
                id = "dto-6",
                name = "Case",
                packType = "SYSTEM",
            )

        assertEquals(KBPack.PackType.SYSTEM, dto.toPack().packType)
    }

    @Test
    fun `KBPackDTO toPack defaults to SYSTEM for unknown pack type`() {
        val dto =
            KBPackDTO(
                id = "dto-7",
                name = "Unknown",
                packType = "unknown_type",
            )

        assertEquals(KBPack.PackType.SYSTEM, dto.toPack().packType)
    }

    @Test
    fun `KBPackDTO toPack defaults to SYSTEM for null pack type`() {
        val dto =
            KBPackDTO(
                id = "dto-8",
                name = "Null Type",
                packType = null,
            )

        assertEquals(KBPack.PackType.SYSTEM, dto.toPack().packType)
    }

    @Test
    fun `KBPackDTO toPack sets isLocal to false`() {
        val dto =
            KBPackDTO(
                id = "dto-9",
                name = "Server Pack",
            )

        assertFalse(dto.toPack().isLocal)
    }

    @Test
    fun `KBPackDTO toPack sets timestamps to null`() {
        val dto =
            KBPackDTO(
                id = "dto-10",
                name = "Timestamps",
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2024-06-01T00:00:00Z",
            )

        val pack = dto.toPack()
        assertNull(pack.createdAtMillis)
        assertNull(pack.updatedAtMillis)
    }

    // MARK: - KBPackDTO Serialization Tests

    @Test
    fun `KBPackDTO serialization roundtrip works`() {
        val original =
            KBPackDTO(
                id = "dto-roundtrip",
                name = "Roundtrip",
                description = "Test",
                packType = "system",
                questionIds = listOf("q1"),
                stats = KBPackStats(questionCount = 1),
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2024-06-01T00:00:00Z",
            )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<KBPackDTO>(encoded)

        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(original.description, decoded.description)
        assertEquals(original.packType, decoded.packType)
    }

    // MARK: - KBPackStats Tests

    @Test
    fun `KBPackStats has correct defaults`() {
        val stats = KBPackStats(questionCount = 50)

        assertEquals(50, stats.questionCount)
        assertNull(stats.domainCount)
        assertEquals(emptyMap<String, Int>(), stats.domainDistribution)
        assertEquals(emptyMap<Int, Int>(), stats.difficultyDistribution)
        assertNull(stats.audioCoveragePercent)
        assertNull(stats.missingAudioCount)
    }

    @Test
    fun `KBPackStats serialization roundtrip works`() {
        val original =
            KBPackStats(
                questionCount = 100,
                domainCount = 5,
                domainDistribution = mapOf("science" to 40, "mathematics" to 60),
                difficultyDistribution = mapOf(3 to 30, 4 to 40, 5 to 30),
                audioCoveragePercent = 85.5,
                missingAudioCount = 15,
            )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<KBPackStats>(encoded)

        assertEquals(original.questionCount, decoded.questionCount)
        assertEquals(original.domainCount, decoded.domainCount)
        assertEquals(original.domainDistribution, decoded.domainDistribution)
        assertEquals(original.difficultyDistribution, decoded.difficultyDistribution)
        assertEquals(original.audioCoveragePercent, decoded.audioCoveragePercent)
        assertEquals(original.missingAudioCount, decoded.missingAudioCount)
    }

    // MARK: - KBPacksResponse Tests

    @Test
    fun `KBPacksResponse serialization roundtrip works`() {
        val response =
            KBPacksResponse(
                packs =
                    listOf(
                        KBPackDTO(id = "p1", name = "Pack 1"),
                        KBPackDTO(id = "p2", name = "Pack 2"),
                    ),
                total = 2,
            )

        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<KBPacksResponse>(encoded)

        assertEquals(2, decoded.packs.size)
        assertEquals("p1", decoded.packs[0].id)
        assertEquals("p2", decoded.packs[1].id)
        assertEquals(2, decoded.total)
    }

    @Test
    fun `KBPacksResponse total defaults to null`() {
        val response = KBPacksResponse(packs = emptyList())

        assertNull(response.total)
    }
}
