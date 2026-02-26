package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A member of a Knowledge Bowl team.
 *
 * @property id Unique member identifier
 * @property name Full display name
 * @property initials Computed initials from the name (up to 2 characters)
 * @property avatarColorHex Hex color string for avatar display
 * @property primaryDomain Optional primary area of expertise
 * @property secondaryDomain Optional secondary area of expertise
 * @property isActive Whether the member is currently active on the team
 * @property createdAt Creation timestamp in milliseconds
 * @property deviceId Optional device identifier for multi-device sync
 */
@Serializable
data class KBTeamMember(
    val id: String,
    val name: String,
    val initials: String =
        name
            .split(" ")
            .mapNotNull { it.firstOrNull()?.uppercase() }
            .take(MAX_INITIALS_LENGTH)
            .joinToString(""),
    @SerialName("avatar_color_hex")
    val avatarColorHex: String = AVATAR_COLORS.random(),
    @SerialName("primary_domain")
    val primaryDomain: KBDomain? = null,
    @SerialName("secondary_domain")
    val secondaryDomain: KBDomain? = null,
    @SerialName("is_active")
    val isActive: Boolean = true,
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @SerialName("device_id")
    val deviceId: String? = null,
) {
    companion object {
        /** Maximum number of characters in computed initials. */
        const val MAX_INITIALS_LENGTH = 2

        /** Preset avatar colors for team members. */
        val AVATAR_COLORS =
            listOf(
                "#3B82F6",
                "#EF4444",
                "#10B981",
                "#F59E0B",
                "#8B5CF6",
                "#EC4899",
                "#14B8A6",
                "#F97316",
                "#6366F1",
                "#84CC16",
            )
    }
}

/**
 * Maps a team member to a Knowledge Bowl domain.
 *
 * @property memberId ID of the team member
 * @property domain The assigned domain
 * @property isPrimary Whether this is the member's primary assignment
 */
@Serializable
data class KBDomainAssignment(
    @SerialName("member_id")
    val memberId: String,
    val domain: KBDomain,
    @SerialName("is_primary")
    val isPrimary: Boolean = true,
)

/**
 * A Knowledge Bowl team profile containing members and their domain assignments.
 *
 * @property id Unique team identifier
 * @property teamCode Human-readable code for team sharing/joining
 * @property name Team display name
 * @property region Competition region with associated rules
 * @property members List of team members
 * @property domainAssignments Mapping of members to domains
 * @property createdAt Creation timestamp in milliseconds
 * @property lastUpdatedAt Last modification timestamp in milliseconds
 * @property isCaptain Whether the current device user is the team captain
 */
@Serializable
data class KBTeamProfile(
    val id: String,
    @SerialName("team_code")
    val teamCode: String,
    val name: String,
    val region: KBRegion,
    val members: List<KBTeamMember> = emptyList(),
    @SerialName("domain_assignments")
    val domainAssignments: List<KBDomainAssignment> = emptyList(),
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @SerialName("last_updated_at")
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    @SerialName("is_captain")
    val isCaptain: Boolean = true,
) {
    /** Members that are currently active on the team. */
    val activeMembers: List<KBTeamMember>
        get() = members.filter { it.isActive }

    /** Set of domains covered by primary assignments. */
    val coveredDomains: Set<KBDomain>
        get() =
            domainAssignments
                .filter { it.isPrimary }
                .map { it.domain }
                .toSet()

    /** Domains that have no primary assignment. */
    val uncoveredDomains: List<KBDomain>
        get() = KBDomain.entries.filter { it !in coveredDomains }

    /** Percentage of all domains covered by primary assignments (0.0 to 100.0). */
    val coveragePercentage: Double
        get() =
            if (KBDomain.entries.isEmpty()) {
                0.0
            } else {
                coveredDomains.size.toDouble() / KBDomain.entries.size * PERCENTAGE_MULTIPLIER
            }

    companion object {
        private const val PERCENTAGE_MULTIPLIER = 100.0
        private const val TEAM_CODE_LENGTH = 6
        private val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toList()

        /**
         * Generate a random team code for sharing.
         *
         * Uses alphanumeric characters excluding ambiguous ones (0, O, 1, I).
         */
        fun generateTeamCode(): String =
            (1..TEAM_CODE_LENGTH)
                .map { CODE_CHARS.random() }
                .joinToString("")
    }
}

/**
 * Performance statistics for a single team member.
 *
 * @property memberId ID of the team member
 * @property totalQuestions Total number of questions attempted
 * @property correctAnswers Number of correct answers
 * @property domainScores Accuracy scores per domain (0.0 to 1.0)
 * @property lastPractice Timestamp of last practice session in milliseconds
 */
@Serializable
data class KBMemberStats(
    @SerialName("member_id")
    val memberId: String,
    @SerialName("total_questions")
    val totalQuestions: Int = 0,
    @SerialName("correct_answers")
    val correctAnswers: Int = 0,
    @SerialName("domain_scores")
    val domainScores: Map<KBDomain, Double> = emptyMap(),
    @SerialName("last_practice")
    val lastPractice: Long? = null,
)
