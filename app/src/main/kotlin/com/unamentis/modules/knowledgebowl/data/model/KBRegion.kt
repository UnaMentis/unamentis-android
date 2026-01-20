package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supported Knowledge Bowl regions with distinct rule sets.
 *
 * Each region has different rules for conferring, scoring, and timing.
 */
@Serializable
enum class KBRegion(
    val displayName: String,
    val abbreviation: String,
) {
    /** Colorado state rules - NO verbal conferring */
    @SerialName("colorado")
    COLORADO("Colorado", "CO"),

    /** Colorado Springs sub-region - stricter hand signal rules */
    @SerialName("colorado_springs")
    COLORADO_SPRINGS("Colorado Springs", "CO"),

    /** Minnesota state rules - verbal conferring allowed, SOS bonus */
    @SerialName("minnesota")
    MINNESOTA("Minnesota", "MN"),

    /** Washington state rules - longer written round (45 min) */
    @SerialName("washington")
    WASHINGTON("Washington", "WA"),
    ;

    /**
     * Get the configuration for this region.
     */
    val config: KBRegionalConfig
        get() = KBRegionalConfig.forRegion(this)

    companion object {
        /**
         * Default region (Colorado).
         */
        val DEFAULT = COLORADO
    }
}
