package com.unamentis.core.todo

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Context needed for auto-resume item creation.
 *
 * Captures the state of a session when it is stopped mid-curriculum,
 * allowing the user to pick up where they left off.
 *
 * @property topicId ID of the topic being studied
 * @property topicTitle Display title of the topic
 * @property curriculumId Optional curriculum ID
 * @property segmentIndex Current segment index in the topic
 * @property totalSegments Total number of segments in the topic
 * @property sessionDurationMs Duration of the session in milliseconds
 * @property conversationMessages Recent conversation messages for context
 */
data class AutoResumeContext(
    val topicId: String,
    val topicTitle: String,
    val curriculumId: String? = null,
    val segmentIndex: Int,
    val totalSegments: Int,
    val sessionDurationMs: Long,
    val conversationMessages: List<ResumeConversationMessage> = emptyList(),
)

/**
 * Simplified conversation message for serialization in auto-resume context.
 *
 * @property role Message role (e.g., "user", "assistant")
 * @property content Message text content
 */
@Serializable
data class ResumeConversationMessage(
    val role: String,
    val content: String,
)

/**
 * Service for detecting and creating auto-resume to-do items.
 *
 * When a learning session is stopped mid-curriculum (i.e., the user hasn't
 * finished all segments), this service evaluates whether an auto-resume
 * todo should be created. Conditions:
 * - Session lasted at least [MINIMUM_SESSION_DURATION_MS]
 * - User progressed past the first segment
 * - User did not complete the last segment
 *
 * Auto-resume items are placed at the top of the todo list so the user
 * sees them immediately when they next open the app.
 *
 * @property todoManager Manager for creating/updating todo items
 */
@Singleton
class AutoResumeService
    @Inject
    constructor(
        private val todoManager: TodoManager,
    ) {
        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        /**
         * Check if an auto-resume item should be created and create it if conditions are met.
         *
         * @param context The context from the stopped session
         * @return True if an auto-resume item was created or updated
         */
        suspend fun handleSessionStop(context: AutoResumeContext): Boolean {
            Log.i(TAG, "Evaluating auto-resume for topic: ${context.topicTitle}")

            if (!shouldCreateAutoResume(context)) {
                Log.i(TAG, "Auto-resume not needed: conditions not met")
                return false
            }

            return try {
                createAutoResumeItem(context)
                Log.i(TAG, "Auto-resume item created/updated for topic: ${context.topicTitle}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create auto-resume item", e)
                false
            }
        }

        /**
         * Determine if an auto-resume item should be created.
         *
         * @param context Session context to evaluate
         * @return True if all conditions for auto-resume are met
         */
        internal fun shouldCreateAutoResume(context: AutoResumeContext): Boolean {
            // Condition 1: Session was substantive (> minimum duration)
            if (context.sessionDurationMs < MINIMUM_SESSION_DURATION_MS) {
                Log.d(
                    TAG,
                    "Session too short for auto-resume: " +
                        "${context.sessionDurationMs}ms < ${MINIMUM_SESSION_DURATION_MS}ms",
                )
                return false
            }

            // Condition 2: Not at the beginning (segmentIndex > 0)
            if (context.segmentIndex <= 0) {
                Log.d(TAG, "At beginning of topic, no auto-resume needed")
                return false
            }

            // Condition 3: Not at the end (not completed)
            if (context.segmentIndex >= context.totalSegments - 1) {
                Log.d(TAG, "Topic appears complete, no auto-resume needed")
                return false
            }

            Log.d(
                TAG,
                "Auto-resume conditions met: segment ${context.segmentIndex}/${context.totalSegments}, " +
                    "duration ${context.sessionDurationMs}ms",
            )
            return true
        }

        /**
         * Create or update the auto-resume to-do item.
         */
        private suspend fun createAutoResumeItem(context: AutoResumeContext) {
            val conversationContextJson = encodeConversationContext(context.conversationMessages)
            val title = "Continue: ${context.topicTitle}"

            todoManager.createAutoResumeItem(
                title = title,
                topicId = context.topicId,
                segmentIndex = context.segmentIndex,
                conversationContext = conversationContextJson,
            )
        }

        /**
         * Encode conversation messages to a JSON string for storage.
         *
         * @param messages Conversation messages to encode
         * @return JSON string of the last [MAX_CONVERSATION_CONTEXT] non-system messages
         */
        internal fun encodeConversationContext(messages: List<ResumeConversationMessage>): String {
            val recentMessages =
                messages
                    .filter { it.role != "system" }
                    .takeLast(MAX_CONVERSATION_CONTEXT)

            return json.encodeToString(recentMessages)
        }

        // region Resume Context Retrieval

        /**
         * Get decoded conversation context for a topic.
         *
         * @param topicId The topic ID to get context for
         * @return List of conversation messages or null if not found
         */
        suspend fun getConversationContext(topicId: String): List<ResumeConversationMessage>? {
            val resumeContext = todoManager.getResumeContext(topicId) ?: return null
            val contextData = resumeContext.conversationContext ?: return null

            return try {
                json.decodeFromString<List<ResumeConversationMessage>>(contextData)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode conversation context", e)
                null
            }
        }

        /**
         * Get the segment index for a topic resume.
         *
         * @param topicId The topic ID to get resume point for
         * @return Segment index or null if not found
         */
        suspend fun getResumeSegmentIndex(topicId: String): Int? {
            return todoManager.getResumeContext(topicId)?.segmentIndex
        }

        // endregion

        // region Clear Auto Resume

        /**
         * Clear auto-resume for a topic (call when topic is completed normally).
         *
         * @param topicId The topic ID to clear
         */
        suspend fun clearAutoResume(topicId: String) {
            todoManager.clearAutoResume(topicId)
            Log.i(TAG, "Cleared auto-resume for topic: $topicId")
        }

        // endregion

        companion object {
            private const val TAG = "AutoResumeService"

            /** Minimum session duration in milliseconds to consider for auto-resume (2 minutes). */
            const val MINIMUM_SESSION_DURATION_MS: Long = 120_000L

            /** Maximum number of conversation messages to store for context. */
            const val MAX_CONVERSATION_CONTEXT: Int = 10
        }
    }
