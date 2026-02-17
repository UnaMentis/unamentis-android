package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [KBDomainMix].
 *
 * Tests weight management, normalization, linked-slider behavior,
 * serialization, and factory methods.
 */
class KBDomainMixTest {
    private val json = Json { ignoreUnknownKeys = true }

    // MARK: - Factory Method Tests

    @Test
    fun `default mix weights sum to 1`() {
        val mix = KBDomainMix.default()
        val total = mix.weights.values.sum()

        assertEquals(1.0, total, 0.001)
    }

    @Test
    fun `default mix has all domains`() {
        val mix = KBDomainMix.default()

        for (domain in KBDomain.entries) {
            assertTrue(
                "Domain ${domain.name} should be in default mix",
                mix.weights.containsKey(domain),
            )
        }
    }

    @Test
    fun `default mix weights are non-negative`() {
        val mix = KBDomainMix.default()

        for ((domain, weight) in mix.weights) {
            assertTrue(
                "Weight for ${domain.name} should be non-negative, was $weight",
                weight >= 0.0,
            )
        }
    }

    @Test
    fun `equal mix has equal weights for all domains`() {
        val mix = KBDomainMix.equal()
        val expectedWeight = 1.0 / KBDomain.entries.size

        for (domain in KBDomain.entries) {
            assertEquals(
                "Weight for ${domain.name}",
                expectedWeight,
                mix.weight(domain),
                0.001,
            )
        }
    }

    @Test
    fun `equal mix weights sum to 1`() {
        val mix = KBDomainMix.equal()
        val total = mix.weights.values.sum()

        assertEquals(1.0, total, 0.001)
    }

    @Test
    fun `fromWeights normalizes weights to sum to 1`() {
        val weights =
            mapOf(
                KBDomain.SCIENCE to 2.0,
                KBDomain.MATHEMATICS to 3.0,
                KBDomain.HISTORY to 5.0,
            )
        val mix = KBDomainMix.fromWeights(weights)
        val total = mix.weights.values.sum()

        assertEquals(1.0, total, 0.001)
        assertEquals(0.2, mix.weight(KBDomain.SCIENCE), 0.001)
        assertEquals(0.3, mix.weight(KBDomain.MATHEMATICS), 0.001)
        assertEquals(0.5, mix.weight(KBDomain.HISTORY), 0.001)
    }

    @Test
    fun `fromWeights with all zero weights falls back to equal distribution`() {
        val weights =
            mapOf(
                KBDomain.SCIENCE to 0.0,
                KBDomain.MATHEMATICS to 0.0,
            )
        val mix = KBDomainMix.fromWeights(weights)
        val total = mix.weights.values.sum()

        assertEquals(1.0, total, 0.001)
    }

    // MARK: - weight() and percentage() Tests

    @Test
    fun `weight returns 0 for missing domain`() {
        val mix =
            KBDomainMix(
                weights = mapOf(KBDomain.SCIENCE to 1.0),
            )

        assertEquals(0.0, mix.weight(KBDomain.MATHEMATICS), 0.001)
    }

    @Test
    fun `percentage returns weight times 100`() {
        val mix = KBDomainMix.equal()
        val expectedPercentage = 100.0 / KBDomain.entries.size

        assertEquals(expectedPercentage, mix.percentage(KBDomain.SCIENCE), 0.01)
    }

    @Test
    fun `percentage returns 0 for missing domain`() {
        val mix =
            KBDomainMix(
                weights = mapOf(KBDomain.SCIENCE to 1.0),
            )

        assertEquals(0.0, mix.percentage(KBDomain.HISTORY), 0.001)
    }

    // MARK: - withWeight Tests (Linked Slider)

    @Test
    fun `withWeight maintains sum of 1 after adjustment`() {
        val mix = KBDomainMix.equal()
        val adjusted = mix.withWeight(KBDomain.SCIENCE, 0.5)
        val total = adjusted.weights.values.sum()

        assertEquals(1.0, total, 0.001)
    }

    @Test
    fun `withWeight sets target domain to requested weight`() {
        val mix = KBDomainMix.equal()
        val adjusted = mix.withWeight(KBDomain.SCIENCE, 0.5)

        assertEquals(0.5, adjusted.weight(KBDomain.SCIENCE), 0.001)
    }

    @Test
    fun `withWeight reduces other domains proportionally`() {
        val mix = KBDomainMix.equal()
        val adjusted = mix.withWeight(KBDomain.SCIENCE, 0.5)

        // All other domains should still have equal weights among themselves
        val otherDomains = KBDomain.entries.filter { it != KBDomain.SCIENCE }
        val otherWeights = otherDomains.map { adjusted.weight(it) }

        // They should all be approximately equal
        val firstOther = otherWeights.first()
        for (w in otherWeights) {
            assertEquals(firstOther, w, 0.01)
        }
    }

    @Test
    fun `withWeight clamps value to 0 minimum`() {
        val mix = KBDomainMix.equal()
        val adjusted = mix.withWeight(KBDomain.SCIENCE, -0.5)
        val total = adjusted.weights.values.sum()

        assertTrue(adjusted.weight(KBDomain.SCIENCE) >= 0.0)
        assertEquals(1.0, total, 0.001)
    }

    @Test
    fun `withWeight clamps value to 1 maximum`() {
        val mix = KBDomainMix.equal()
        val adjusted = mix.withWeight(KBDomain.SCIENCE, 1.5)
        val total = adjusted.weights.values.sum()

        assertTrue(adjusted.weight(KBDomain.SCIENCE) <= 1.0)
        assertEquals(1.0, total, 0.001)
    }

    @Test
    fun `withWeight returns same mix for negligible change`() {
        val mix = KBDomainMix.equal()
        val adjusted = mix.withWeight(KBDomain.SCIENCE, mix.weight(KBDomain.SCIENCE))

        // Should return the same object (no change needed)
        assertEquals(mix, adjusted)
    }

    @Test
    fun `withWeight to 1 pushes all others to 0`() {
        val mix = KBDomainMix.equal()
        val adjusted = mix.withWeight(KBDomain.SCIENCE, 1.0)

        assertEquals(1.0, adjusted.weight(KBDomain.SCIENCE), 0.001)
        for (domain in KBDomain.entries) {
            if (domain != KBDomain.SCIENCE) {
                assertEquals(
                    "Domain ${domain.name} should be 0",
                    0.0,
                    adjusted.weight(domain),
                    0.001,
                )
            }
        }
    }

    @Test
    fun `withWeight successive adjustments maintain sum of 1`() {
        var mix = KBDomainMix.equal()
        mix = mix.withWeight(KBDomain.SCIENCE, 0.3)
        mix = mix.withWeight(KBDomain.MATHEMATICS, 0.2)
        mix = mix.withWeight(KBDomain.HISTORY, 0.15)

        val total = mix.weights.values.sum()
        assertEquals(1.0, total, 0.001)
    }

    // MARK: - resetToDefault Tests

    @Test
    fun `resetToDefault returns default distribution`() {
        val mix = KBDomainMix.equal()
        val reset = mix.resetToDefault()
        val defaultMix = KBDomainMix.default()

        for (domain in KBDomain.entries) {
            assertEquals(
                "Domain ${domain.name}",
                defaultMix.weight(domain),
                reset.weight(domain),
                0.001,
            )
        }
    }

    // MARK: - sortedByWeight Tests

    @Test
    fun `sortedByWeight returns domains in descending order`() {
        val mix = KBDomainMix.default()
        val sorted = mix.sortedByWeight

        for (i in 0 until sorted.size - 1) {
            assertTrue(
                "Sorted weights should be descending",
                sorted[i].second >= sorted[i + 1].second,
            )
        }
    }

    @Test
    fun `sortedByWeight has correct size`() {
        val mix = KBDomainMix.default()
        assertEquals(KBDomain.entries.size, mix.sortedByWeight.size)
    }

    // MARK: - activeDomains Tests

    @Test
    fun `activeDomains returns only domains above threshold`() {
        val mix = KBDomainMix.default()
        val active = mix.activeDomains

        assertTrue(active.isNotEmpty())
        for (domain in active) {
            assertTrue(mix.weight(domain) > KBDomainMix.MIN_THRESHOLD)
        }
    }

    @Test
    fun `activeDomains excludes zero-weight domains`() {
        var mix = KBDomainMix.equal()
        mix = mix.withWeight(KBDomain.SCIENCE, 1.0)

        val active = mix.activeDomains
        assertEquals(1, active.size)
        assertEquals(KBDomain.SCIENCE, active.first())
    }

    // MARK: - selectionWeights Tests

    @Test
    fun `selectionWeights excludes zero-weight domains`() {
        var mix = KBDomainMix.equal()
        mix = mix.withWeight(KBDomain.SCIENCE, 1.0)

        val selection = mix.selectionWeights
        assertEquals(1, selection.size)
        assertTrue(selection.containsKey(KBDomain.SCIENCE))
    }

    @Test
    fun `selectionWeights values are above threshold`() {
        val mix = KBDomainMix.default()

        for ((_, weight) in mix.selectionWeights) {
            assertTrue(weight > KBDomainMix.MIN_THRESHOLD)
        }
    }

    // MARK: - toString Tests

    @Test
    fun `toString contains domain names and percentages`() {
        val mix = KBDomainMix.default()
        val str = mix.toString()

        assertTrue(str.contains("Science"))
        assertTrue(str.contains("%"))
    }

    // MARK: - Serialization Tests

    @Test
    fun `serializes and deserializes correctly`() {
        val original = KBDomainMix.default()
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<KBDomainMix>(encoded)

        for (domain in KBDomain.entries) {
            assertEquals(
                "Domain ${domain.name}",
                original.weight(domain),
                decoded.weight(domain),
                0.001,
            )
        }
    }

    @Test
    fun `equal mix serializes and deserializes correctly`() {
        val original = KBDomainMix.equal()
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<KBDomainMix>(encoded)

        for (domain in KBDomain.entries) {
            assertEquals(
                original.weight(domain),
                decoded.weight(domain),
                0.001,
            )
        }
    }

    // MARK: - Validation Tests

    @Test(expected = IllegalArgumentException::class)
    fun `constructor rejects negative weights`() {
        KBDomainMix(
            weights =
                mapOf(
                    KBDomain.SCIENCE to -0.5,
                    KBDomain.MATHEMATICS to 1.5,
                ),
        )
    }

    @Test
    fun `constructor accepts zero weights`() {
        val mix =
            KBDomainMix(
                weights =
                    mapOf(
                        KBDomain.SCIENCE to 0.0,
                        KBDomain.MATHEMATICS to 1.0,
                    ),
            )

        assertEquals(0.0, mix.weight(KBDomain.SCIENCE), 0.001)
        assertEquals(1.0, mix.weight(KBDomain.MATHEMATICS), 0.001)
    }

    // MARK: - Data Class Equality Tests

    @Test
    fun `equal mixes are structurally equal`() {
        val mix1 = KBDomainMix.equal()
        val mix2 = KBDomainMix.equal()

        assertEquals(mix1, mix2)
    }

    @Test
    fun `different mixes are not equal`() {
        val equal = KBDomainMix.equal()
        val adjusted = equal.withWeight(KBDomain.SCIENCE, 0.5)

        assertNotEquals(equal, adjusted)
    }
}
