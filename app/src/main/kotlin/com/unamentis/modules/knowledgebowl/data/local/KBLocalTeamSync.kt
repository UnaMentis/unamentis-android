package com.unamentis.modules.knowledgebowl.data.local

import android.util.Log
import com.unamentis.modules.knowledgebowl.data.model.KBMemberStats
import com.unamentis.modules.knowledgebowl.data.model.KBTeamMember
import com.unamentis.modules.knowledgebowl.data.model.KBTeamProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Synchronization mode for team data.
 */
sealed class KBTeamSyncMode {
    /**
     * Sync with a remote server.
     *
     * @property baseUrl The server URL to sync against
     */
    data class Server(val baseUrl: String) : KBTeamSyncMode()

    /** Local-only storage with no remote sync. */
    data object Local : KBTeamSyncMode()

    /** Offline mode; changes are queued for later sync. */
    data object Offline : KBTeamSyncMode()
}

/**
 * Current status of team data synchronization.
 *
 * @property mode The active sync mode
 * @property isSyncing Whether a sync operation is currently in progress
 * @property lastSyncTime Timestamp of the last successful sync in milliseconds
 * @property pendingChanges Number of local changes awaiting sync
 * @property lastError Description of the most recent sync error, if any
 */
data class KBTeamSyncStatus(
    val mode: KBTeamSyncMode,
    val isSyncing: Boolean = false,
    val lastSyncTime: Long? = null,
    val pendingChanges: Int = 0,
    val lastError: String? = null,
)

/**
 * Local sync provider for Knowledge Bowl team data.
 *
 * Delegates all persistence operations to [KBTeamStore] and exposes
 * sync status as a [StateFlow]. Currently operates in [KBTeamSyncMode.Local]
 * mode; remote sync modes can be added in the future.
 *
 * @property teamStore The underlying local persistence store
 */
@Singleton
class KBLocalTeamSync
    @Inject
    constructor(
        private val teamStore: KBTeamStore,
    ) {
        /** The current synchronization mode. */
        val syncMode: KBTeamSyncMode = KBTeamSyncMode.Local

        private val _status =
            MutableStateFlow(
                KBTeamSyncStatus(mode = syncMode),
            )

        /** Observable synchronization status. */
        val status: StateFlow<KBTeamSyncStatus> = _status.asStateFlow()

        /**
         * Fetch the team profile from local storage.
         *
         * @return The stored team profile, or null if none exists
         */
        suspend fun fetchTeam(): KBTeamProfile? {
            Log.d(TAG, "Fetching team profile")
            return teamStore.loadProfile()
        }

        /**
         * Save a team profile to local storage.
         *
         * @param team The team profile to persist
         */
        suspend fun saveTeam(team: KBTeamProfile) {
            Log.d(TAG, "Saving team profile '${team.name}'")
            teamStore.saveProfile(team)
        }

        /**
         * Delete the team profile from local storage.
         */
        suspend fun deleteTeam() {
            Log.d(TAG, "Deleting team profile")
            teamStore.deleteProfile()
        }

        /**
         * Fetch all team members from the stored profile.
         *
         * @return List of team members, or empty list if no profile exists
         */
        suspend fun fetchMembers(): List<KBTeamMember> {
            val profile = teamStore.loadProfile() ?: return emptyList()
            return profile.members
        }

        /**
         * Add a member to the team.
         *
         * Loads the current profile, appends the member, and saves.
         *
         * @param member The team member to add
         */
        suspend fun addMember(member: KBTeamMember) {
            val profile =
                teamStore.loadProfile() ?: run {
                    Log.w(TAG, "Cannot add member: no team profile exists")
                    return
                }
            val updatedProfile =
                profile.copy(
                    members = profile.members + member,
                    lastUpdatedAt = System.currentTimeMillis(),
                )
            teamStore.saveProfile(updatedProfile)
            Log.i(TAG, "Added member '${member.name}' to team")
        }

        /**
         * Update an existing team member.
         *
         * Replaces the member with a matching ID in the stored profile.
         *
         * @param member The updated team member data
         */
        suspend fun updateMember(member: KBTeamMember) {
            val profile =
                teamStore.loadProfile() ?: run {
                    Log.w(TAG, "Cannot update member: no team profile exists")
                    return
                }
            val updatedMembers =
                profile.members.map { existing ->
                    if (existing.id == member.id) member else existing
                }
            val updatedProfile =
                profile.copy(
                    members = updatedMembers,
                    lastUpdatedAt = System.currentTimeMillis(),
                )
            teamStore.saveProfile(updatedProfile)
            Log.i(TAG, "Updated member '${member.name}'")
        }

        /**
         * Remove a member from the team by ID.
         *
         * Also deletes the member's statistics.
         *
         * @param memberId The unique identifier of the member to remove
         */
        suspend fun deleteMember(memberId: String) {
            val profile =
                teamStore.loadProfile() ?: run {
                    Log.w(TAG, "Cannot delete member: no team profile exists")
                    return
                }
            val updatedProfile =
                profile.copy(
                    members = profile.members.filter { it.id != memberId },
                    domainAssignments = profile.domainAssignments.filter { it.memberId != memberId },
                    lastUpdatedAt = System.currentTimeMillis(),
                )
            teamStore.saveProfile(updatedProfile)
            teamStore.deleteStats(memberId)
            Log.i(TAG, "Deleted member $memberId")
        }

        /**
         * Fetch statistics for a specific team member.
         *
         * @param memberId The member's unique identifier
         * @return The stored statistics, or null if none exist
         */
        suspend fun fetchStats(memberId: String): KBMemberStats? {
            return teamStore.loadStats(memberId)
        }

        /**
         * Fetch statistics for all team members.
         *
         * @return List of all stored member statistics
         */
        suspend fun fetchAllStats(): List<KBMemberStats> {
            return teamStore.loadAllStats()
        }

        /**
         * Save (push) member statistics to local storage.
         *
         * @param stats The member statistics to persist
         */
        suspend fun pushStats(stats: KBMemberStats) {
            teamStore.saveStats(stats)
        }

        companion object {
            private const val TAG = "KBLocalTeamSync"
        }
    }
