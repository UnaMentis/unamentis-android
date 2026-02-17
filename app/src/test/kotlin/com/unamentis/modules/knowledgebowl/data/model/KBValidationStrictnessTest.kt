package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [KBValidationStrictness].
 *
 * Tests level ordering, comparison methods, serialization, and
 * default values.
 */
class KBValidationStrictnessTest {
    private val json = Json { ignoreUnknownKeys = true }

    // MARK: - Level Ordering Tests

    @Test
    fun `STRICT has lowest level`() {
        assertEquals(1, KBValidationStrictness.STRICT.level)
    }

    @Test
    fun `STANDARD has middle level`() {
        assertEquals(2, KBValidationStrictness.STANDARD.level)
    }

    @Test
    fun `LENIENT has highest level`() {
        assertEquals(3, KBValidationStrictness.LENIENT.level)
    }

    @Test
    fun `levels are in ascending order`() {
        assertTrue(KBValidationStrictness.STRICT.level < KBValidationStrictness.STANDARD.level)
        assertTrue(KBValidationStrictness.STANDARD.level < KBValidationStrictness.LENIENT.level)
    }

    // MARK: - Display Name Tests

    @Test
    fun `STRICT has correct display name`() {
        assertEquals("Strict", KBValidationStrictness.STRICT.displayName)
    }

    @Test
    fun `STANDARD has correct display name`() {
        assertEquals("Standard", KBValidationStrictness.STANDARD.displayName)
    }

    @Test
    fun `LENIENT has correct display name`() {
        assertEquals("Lenient", KBValidationStrictness.LENIENT.displayName)
    }

    // MARK: - isStricterThan Tests

    @Test
    fun `STRICT is stricter than STANDARD`() {
        assertTrue(KBValidationStrictness.STRICT.isStricterThan(KBValidationStrictness.STANDARD))
    }

    @Test
    fun `STRICT is stricter than LENIENT`() {
        assertTrue(KBValidationStrictness.STRICT.isStricterThan(KBValidationStrictness.LENIENT))
    }

    @Test
    fun `STANDARD is stricter than LENIENT`() {
        assertTrue(KBValidationStrictness.STANDARD.isStricterThan(KBValidationStrictness.LENIENT))
    }

    @Test
    fun `STANDARD is not stricter than STRICT`() {
        assertFalse(KBValidationStrictness.STANDARD.isStricterThan(KBValidationStrictness.STRICT))
    }

    @Test
    fun `LENIENT is not stricter than STANDARD`() {
        assertFalse(KBValidationStrictness.LENIENT.isStricterThan(KBValidationStrictness.STANDARD))
    }

    @Test
    fun `STRICT is not stricter than itself`() {
        assertFalse(KBValidationStrictness.STRICT.isStricterThan(KBValidationStrictness.STRICT))
    }

    // MARK: - isMoreLenientThan Tests

    @Test
    fun `LENIENT is more lenient than STANDARD`() {
        assertTrue(KBValidationStrictness.LENIENT.isMoreLenientThan(KBValidationStrictness.STANDARD))
    }

    @Test
    fun `LENIENT is more lenient than STRICT`() {
        assertTrue(KBValidationStrictness.LENIENT.isMoreLenientThan(KBValidationStrictness.STRICT))
    }

    @Test
    fun `STANDARD is more lenient than STRICT`() {
        assertTrue(KBValidationStrictness.STANDARD.isMoreLenientThan(KBValidationStrictness.STRICT))
    }

    @Test
    fun `STRICT is not more lenient than STANDARD`() {
        assertFalse(KBValidationStrictness.STRICT.isMoreLenientThan(KBValidationStrictness.STANDARD))
    }

    @Test
    fun `STANDARD is not more lenient than LENIENT`() {
        assertFalse(KBValidationStrictness.STANDARD.isMoreLenientThan(KBValidationStrictness.LENIENT))
    }

    @Test
    fun `LENIENT is not more lenient than itself`() {
        assertFalse(KBValidationStrictness.LENIENT.isMoreLenientThan(KBValidationStrictness.LENIENT))
    }

    // MARK: - Relationship Consistency Tests

    @Test
    fun `isStricterThan and isMoreLenientThan are inverses`() {
        for (a in KBValidationStrictness.entries) {
            for (b in KBValidationStrictness.entries) {
                if (a != b) {
                    assertEquals(
                        "isStricterThan and isMoreLenientThan should be inverses for $a vs $b",
                        a.isStricterThan(b),
                        b.isMoreLenientThan(a),
                    )
                }
            }
        }
    }

    // MARK: - Default Tests

    @Test
    fun `DEFAULT is STANDARD`() {
        assertEquals(KBValidationStrictness.STANDARD, KBValidationStrictness.DEFAULT)
    }

    // MARK: - Enum Completeness Tests

    @Test
    fun `there are exactly 3 strictness levels`() {
        assertEquals(3, KBValidationStrictness.entries.size)
    }

    @Test
    fun `all entries have unique levels`() {
        val levels = KBValidationStrictness.entries.map { it.level }.toSet()
        assertEquals(KBValidationStrictness.entries.size, levels.size)
    }

    @Test
    fun `all entries have non-empty display names`() {
        for (strictness in KBValidationStrictness.entries) {
            assertTrue(
                "Display name for ${strictness.name} should not be empty",
                strictness.displayName.isNotEmpty(),
            )
        }
    }

    // MARK: - Serialization Tests

    @Test
    fun `STRICT serializes to strict`() {
        val encoded = json.encodeToString(KBValidationStrictness.STRICT)
        assertEquals("\"strict\"", encoded)
    }

    @Test
    fun `STANDARD serializes to standard`() {
        val encoded = json.encodeToString(KBValidationStrictness.STANDARD)
        assertEquals("\"standard\"", encoded)
    }

    @Test
    fun `LENIENT serializes to lenient`() {
        val encoded = json.encodeToString(KBValidationStrictness.LENIENT)
        assertEquals("\"lenient\"", encoded)
    }

    @Test
    fun `deserialization roundtrip works for all values`() {
        for (strictness in KBValidationStrictness.entries) {
            val encoded = json.encodeToString(strictness)
            val decoded = json.decodeFromString<KBValidationStrictness>(encoded)
            assertEquals(strictness, decoded)
        }
    }
}
