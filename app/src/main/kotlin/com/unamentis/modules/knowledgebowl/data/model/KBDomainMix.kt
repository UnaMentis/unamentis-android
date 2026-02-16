package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Represents a distribution of question weights across Knowledge Bowl domains.
 *
 * The weights always sum to 1.0 (100%). Provides a linked-slider algorithm
 * so that adjusting one domain's weight automatically adjusts all others
 * proportionally to maintain the sum constraint.
 *
 * ## Usage
 * ```
 * var mix = KBDomainMix.default()
 * mix = mix.withWeight(KBDomain.SCIENCE, 0.5) // Science to 50%, others adjust
 * val scienceWeight = mix.weight(KBDomain.SCIENCE)  // 0.5
 * ```
 *
 * @property weights Weight for each domain (0.0 to 1.0), sums to 1.0
 */
@Serializable
data class KBDomainMix(
    val weights: Map<KBDomain, Double>,
) {
    init {
        // Ensure all weights are non-negative
        require(weights.values.all { it >= 0.0 }) {
            "All weights must be non-negative"
        }
    }

    /**
     * Get weight for a domain (returns 0 if not found).
     *
     * @param domain The domain to query
     * @return Weight as a fraction (0.0 to 1.0)
     */
    fun weight(domain: KBDomain): Double {
        return weights[domain] ?: 0.0
    }

    /**
     * Get weight as a percentage (0-100).
     *
     * @param domain The domain to query
     * @return Weight as a percentage
     */
    fun percentage(domain: KBDomain): Double {
        return (weights[domain] ?: 0.0) * 100.0
    }

    /**
     * Set weight for a domain, adjusting all other domains proportionally
     * to maintain a sum of 1.0.
     *
     * Algorithm:
     * 1. Calculate delta = newWeight - currentWeight
     * 2. Get total weight of other domains
     * 3. Distribute delta inversely proportional across others
     * 4. Clamp each to [0, 1] range
     * 5. Normalize if clamping caused drift
     *
     * @param domain The domain to adjust
     * @param newWeight The new weight (0.0 to 1.0)
     * @return A new [KBDomainMix] with adjusted weights
     */
    fun withWeight(domain: KBDomain, newWeight: Double): KBDomainMix {
        val clampedNew = max(0.0, min(1.0, newWeight))
        val oldWeight = weights[domain] ?: 0.0
        val delta = clampedNew - oldWeight

        // Skip if change is negligible
        if (abs(delta) <= MIN_THRESHOLD) {
            return this
        }

        val otherDomains = KBDomain.entries.filter { it != domain }
        val otherTotal = otherDomains.sumOf { weights[it] ?: 0.0 }

        // Can't increase if all others are at 0
        if (delta > 0 && otherTotal < MIN_THRESHOLD) {
            return this
        }

        val newWeights = weights.toMutableMap()

        // Distribute delta proportionally across other domains
        if (otherTotal > MIN_THRESHOLD) {
            for (other in otherDomains) {
                val currentWeight = newWeights[other] ?: 0.0
                val proportion = currentWeight / otherTotal
                val adjustment = -delta * proportion
                newWeights[other] = max(0.0, min(1.0, currentWeight + adjustment))
            }
        } else {
            // All others are at 0, distribute delta equally
            val adjustment = -delta / otherDomains.size
            for (other in otherDomains) {
                val currentWeight = newWeights[other] ?: 0.0
                newWeights[other] = max(0.0, min(1.0, currentWeight + adjustment))
            }
        }

        newWeights[domain] = clampedNew

        // Normalize to ensure sum is exactly 1.0
        return KBDomainMix(weights = normalize(newWeights))
    }

    /**
     * Reset to default distribution.
     *
     * @return A new [KBDomainMix] using the natural domain weights
     */
    fun resetToDefault(): KBDomainMix = default()

    /**
     * Domains and weights sorted by weight descending.
     */
    val sortedByWeight: List<Pair<KBDomain, Double>>
        get() = weights.entries
            .map { it.key to it.value }
            .sortedByDescending { it.second }

    /**
     * Domains with non-zero weight.
     */
    val activeDomains: List<KBDomain>
        get() = weights.entries
            .filter { it.value > MIN_THRESHOLD }
            .map { it.key }

    /**
     * Weights for domains with non-zero weight (for question selection).
     */
    val selectionWeights: Map<KBDomain, Double>
        get() = weights.filter { it.value > MIN_THRESHOLD }

    override fun toString(): String {
        return sortedByWeight
            .joinToString(", ") { "${it.first.displayName}: ${(it.second * 100).toInt()}%" }
    }

    companion object {
        /** Minimum weight threshold (below this is considered 0). */
        internal const val MIN_THRESHOLD: Double = 0.001

        /**
         * Default distribution using natural domain weights.
         */
        fun default(): KBDomainMix {
            val weights = mutableMapOf<KBDomain, Double>()
            for (domain in KBDomain.entries) {
                weights[domain] = domain.weight.toDouble()
            }
            return KBDomainMix(weights = normalize(weights))
        }

        /**
         * Equal distribution across all domains.
         */
        fun equal(): KBDomainMix {
            val equalWeight = 1.0 / KBDomain.entries.size
            val weights = KBDomain.entries.associateWith { equalWeight }
            return KBDomainMix(weights = weights)
        }

        /**
         * Create from custom weights (will be normalized to sum to 1.0).
         *
         * @param weights Map of domain to weight
         * @return Normalized [KBDomainMix]
         */
        fun fromWeights(weights: Map<KBDomain, Double>): KBDomainMix {
            return KBDomainMix(weights = normalize(weights.toMutableMap()))
        }

        /**
         * Normalize weights to sum to exactly 1.0.
         */
        private fun normalize(weights: MutableMap<KBDomain, Double>): Map<KBDomain, Double> {
            val total = weights.values.sum()

            if (total <= MIN_THRESHOLD) {
                // Fallback to equal distribution
                val equalWeight = 1.0 / KBDomain.entries.size
                return KBDomain.entries.associateWith { equalWeight }
            }

            // Scale all weights proportionally
            return weights.mapValues { (_, weight) -> weight / total }
        }
    }
}
