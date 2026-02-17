package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A collection of Knowledge Bowl questions grouped for practice or competition.
 *
 * Supports both server-side packs (fetched from management API) and
 * local packs (created on-device).
 *
 * @property id Unique pack identifier
 * @property name Human-readable pack name
 * @property description Optional description of the pack contents
 * @property questionCount Total number of questions in the pack
 * @property domainDistribution Count of questions per domain (key is domain serialized name)
 * @property difficultyDistribution Count of questions per difficulty level
 * @property packType Whether this is a system, custom, or bundle pack
 * @property isLocal Whether this pack was created locally on-device
 * @property questionIds UUIDs of questions (populated for local packs)
 * @property createdAtMillis Creation timestamp in milliseconds
 * @property updatedAtMillis Last update timestamp in milliseconds
 */
@Serializable
data class KBPack(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("question_count")
    val questionCount: Int,
    @SerialName("domain_distribution")
    val domainDistribution: Map<String, Int> = emptyMap(),
    @SerialName("difficulty_distribution")
    val difficultyDistribution: Map<Int, Int> = emptyMap(),
    @SerialName("pack_type")
    val packType: PackType = PackType.CUSTOM,
    @SerialName("is_local")
    val isLocal: Boolean = true,
    @SerialName("question_ids")
    val questionIds: List<String>? = null,
    @SerialName("created_at_millis")
    val createdAtMillis: Long? = System.currentTimeMillis(),
    @SerialName("updated_at_millis")
    val updatedAtMillis: Long? = null,
) {
    /**
     * Type of question pack.
     */
    @Serializable
    enum class PackType {
        /** Pre-made packs from the server */
        @SerialName("system")
        SYSTEM,

        /** User-created packs (local or synced) */
        @SerialName("custom")
        CUSTOM,

        /** Combined packs */
        @SerialName("bundle")
        BUNDLE,
    }

    /**
     * Top domains in this pack (up to 4), sorted by question count descending.
     */
    val topDomains: List<KBDomain>
        get() =
            domainDistribution
                .entries
                .sortedByDescending { it.value }
                .take(TOP_DOMAIN_LIMIT)
                .mapNotNull { KBDomain.fromSerialName(it.key) }

    companion object {
        private const val TOP_DOMAIN_LIMIT = 4
    }
}

/**
 * Response from GET /api/kb/packs.
 *
 * @property packs List of pack DTOs
 * @property total Optional total count for pagination
 */
@Serializable
data class KBPacksResponse(
    val packs: List<KBPackDTO>,
    val total: Int? = null,
)

/**
 * Data transfer object for a pack from the server.
 *
 * Maps from server JSON format to the domain [KBPack] model.
 *
 * @property id Pack identifier
 * @property name Pack name
 * @property description Optional description
 * @property packType Pack type string from server
 * @property questionIds Optional list of question IDs
 * @property stats Optional pack statistics
 * @property createdAt ISO8601 creation timestamp
 * @property updatedAt ISO8601 update timestamp
 */
@Serializable
data class KBPackDTO(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("pack_type")
    val packType: String? = null,
    @SerialName("question_ids")
    val questionIds: List<String>? = null,
    val stats: KBPackStats? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
) {
    /**
     * Convert to domain model [KBPack].
     */
    fun toPack(): KBPack {
        return KBPack(
            id = id,
            name = name,
            description = description,
            questionCount = stats?.questionCount ?: questionIds?.size ?: 0,
            domainDistribution = stats?.domainDistribution ?: emptyMap(),
            difficultyDistribution = stats?.difficultyDistribution ?: emptyMap(),
            packType = parsePackType(packType),
            isLocal = false,
            questionIds = questionIds,
            createdAtMillis = null,
            updatedAtMillis = null,
        )
    }

    private fun parsePackType(type: String?): KBPack.PackType {
        return when (type?.lowercase()) {
            "system" -> KBPack.PackType.SYSTEM
            "custom" -> KBPack.PackType.CUSTOM
            "bundle" -> KBPack.PackType.BUNDLE
            else -> KBPack.PackType.SYSTEM
        }
    }
}

/**
 * Pack statistics from the server.
 *
 * @property questionCount Total number of questions
 * @property domainCount Number of distinct domains
 * @property domainDistribution Question count per domain
 * @property difficultyDistribution Question count per difficulty level
 * @property audioCoveragePercent Percentage of questions with pre-recorded audio
 * @property missingAudioCount Number of questions without audio
 */
@Serializable
data class KBPackStats(
    @SerialName("question_count")
    val questionCount: Int,
    @SerialName("domain_count")
    val domainCount: Int? = null,
    @SerialName("domain_distribution")
    val domainDistribution: Map<String, Int> = emptyMap(),
    @SerialName("difficulty_distribution")
    val difficultyDistribution: Map<Int, Int> = emptyMap(),
    @SerialName("audio_coverage_percent")
    val audioCoveragePercent: Double? = null,
    @SerialName("missing_audio_count")
    val missingAudioCount: Int? = null,
)
