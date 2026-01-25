package com.unamentis.modules.knowledgebowl.data.model

import androidx.annotation.StringRes
import com.unamentis.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The 12 academic domains used in Knowledge Bowl competitions.
 *
 * Each domain has a weight representing its typical distribution
 * in competition question banks.
 *
 * ## Domain Weights
 * - Science: 20%
 * - Mathematics: 15%
 * - Literature: 12%
 * - History: 12%
 * - Social Studies: 10%
 * - Arts: 8%
 * - Current Events: 8%
 * - Language: 5%
 * - Technology: 4%
 * - Pop Culture: 3%
 * - Religion/Philosophy: 2%
 * - Miscellaneous: 1%
 */
@Serializable
enum class KBDomain(
    val displayName: String,
    val iconName: String,
    val weight: Float,
) {
    @SerialName("science")
    SCIENCE("Science", "science", 0.20f),

    @SerialName("mathematics")
    MATHEMATICS("Mathematics", "calculate", 0.15f),

    @SerialName("literature")
    LITERATURE("Literature", "menu_book", 0.12f),

    @SerialName("history")
    HISTORY("History", "history", 0.12f),

    @SerialName("social_studies")
    SOCIAL_STUDIES("Social Studies", "public", 0.10f),

    @SerialName("arts")
    ARTS("Arts", "palette", 0.08f),

    @SerialName("current_events")
    CURRENT_EVENTS("Current Events", "newspaper", 0.08f),

    @SerialName("language")
    LANGUAGE("Language", "translate", 0.05f),

    @SerialName("technology")
    TECHNOLOGY("Technology", "computer", 0.04f),

    @SerialName("pop_culture")
    POP_CULTURE("Pop Culture", "star", 0.03f),

    @SerialName("religion_philosophy")
    RELIGION_PHILOSOPHY("Religion/Philosophy", "auto_awesome", 0.02f),

    @SerialName("miscellaneous")
    MISCELLANEOUS("Miscellaneous", "help_outline", 0.01f),
    ;

    companion object {
        /**
         * Get a domain by its serialized name.
         */
        fun fromSerialName(name: String): KBDomain? {
            return entries.find {
                it.name.lowercase() == name.lowercase() ||
                    name.lowercase().replace("_", "") == it.name.lowercase().replace("_", "")
            }
        }

        /**
         * Get a domain by its ID (same as serialized name).
         *
         * @param id The domain ID (e.g., "science", "mathematics", "social_studies")
         * @return The matching KBDomain, or null if not found
         */
        fun fromId(id: String): KBDomain? = fromSerialName(id)

        /**
         * Get the total weight (should sum to 1.0).
         */
        val totalWeight: Float
            get() = entries.sumOf { it.weight.toDouble() }.toFloat()
    }

    /**
     * Get the string resource ID for this domain's display name.
     *
     * Use with stringResource() for localized display.
     */
    @get:StringRes
    val stringResId: Int
        get() =
            when (this) {
                SCIENCE -> R.string.kb_domain_science
                MATHEMATICS -> R.string.kb_domain_mathematics
                LITERATURE -> R.string.kb_domain_literature
                HISTORY -> R.string.kb_domain_history
                SOCIAL_STUDIES -> R.string.kb_domain_social_studies
                ARTS -> R.string.kb_domain_arts
                CURRENT_EVENTS -> R.string.kb_domain_current_events
                LANGUAGE -> R.string.kb_domain_language
                TECHNOLOGY -> R.string.kb_domain_technology
                POP_CULTURE -> R.string.kb_domain_pop_culture
                RELIGION_PHILOSOPHY -> R.string.kb_domain_religion_philosophy
                MISCELLANEOUS -> R.string.kb_domain_miscellaneous
            }
}
