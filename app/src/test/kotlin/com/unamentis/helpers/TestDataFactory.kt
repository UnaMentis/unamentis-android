package com.unamentis.helpers

import com.unamentis.data.local.entity.CurriculumEntity
import com.unamentis.data.local.entity.SessionEntity
import com.unamentis.data.local.entity.TopicProgressEntity
import com.unamentis.data.local.entity.TranscriptEntryEntity
import com.unamentis.data.model.Curriculum
import com.unamentis.data.model.LLMMessage
import com.unamentis.data.model.Session
import com.unamentis.data.model.Topic
import com.unamentis.data.model.TranscriptSegment
import java.util.UUID

/**
 * Helper to create test data for unit and integration tests.
 *
 * NOTE: This is NOT a mock. It creates real data objects and Room entities
 * for use with in-memory databases.
 *
 * Usage:
 * ```kotlin
 * // Create test curriculum
 * val curriculum = TestDataFactory.createCurriculum(name = "Test Course")
 *
 * // Create curriculum with topics
 * val curriculumWithTopics = TestDataFactory.createCurriculum(topicCount = 5)
 *
 * // Create entity for Room database
 * val entity = TestDataFactory.createCurriculumEntity()
 * ```
 */
object TestDataFactory {
    // region Curriculum

    /**
     * Create a test [Curriculum] domain object.
     *
     * @param id Curriculum ID (auto-generated if null)
     * @param name Curriculum name
     * @param description Curriculum description
     * @param topicCount Number of topics to auto-create (0 for none)
     * @param version Curriculum version
     */
    fun createCurriculum(
        id: String = UUID.randomUUID().toString(),
        name: String = "Test Curriculum",
        description: String = "A test curriculum for unit testing",
        topicCount: Int = 0,
        version: String = "1.0.0",
    ): Curriculum {
        val topics =
            if (topicCount > 0) {
                (0 until topicCount).map { index ->
                    createTopic(
                        title = "Topic ${index + 1}",
                        orderIndex = index,
                    )
                }
            } else {
                emptyList()
            }

        return Curriculum(
            id = id,
            title = name,
            description = description,
            version = version,
            topics = topics,
        )
    }

    /**
     * Create a test [CurriculumEntity] for Room database.
     */
    fun createCurriculumEntity(
        id: String = UUID.randomUUID().toString(),
        title: String = "Test Curriculum",
        description: String = "A test curriculum for unit testing",
        version: String = "1.0.0",
        contentJson: String = "{}",
        downloadedAt: Long = System.currentTimeMillis(),
        lastAccessedAt: Long = System.currentTimeMillis(),
        topicCount: Int = 0,
    ): CurriculumEntity =
        CurriculumEntity(
            id = id,
            title = title,
            description = description,
            version = version,
            contentJson = contentJson,
            downloadedAt = downloadedAt,
            lastAccessedAt = lastAccessedAt,
            topicCount = topicCount,
        )

    // endregion

    // region Topic

    /**
     * Create a test [Topic] domain object.
     *
     * @param id Topic ID (auto-generated if null)
     * @param title Topic title
     * @param orderIndex Order within curriculum
     * @param description Topic description
     * @param objectives Learning objectives
     * @param transcript Transcript segments
     */
    fun createTopic(
        id: String = UUID.randomUUID().toString(),
        title: String = "Test Topic",
        orderIndex: Int = 0,
        description: String = "Test description for $title",
        objectives: List<String> = listOf("Objective 1", "Objective 2"),
        transcript: List<TranscriptSegment> =
            listOf(
                TranscriptSegment(
                    id = UUID.randomUUID().toString(),
                    type = "content",
                    content = "Test transcript content for $title",
                ),
            ),
    ): Topic =
        Topic(
            id = id,
            title = title,
            orderIndex = orderIndex,
            transcript = transcript,
            learningObjectives = objectives,
            description = description,
        )

    // endregion

    // region Session

    /**
     * Create a test [Session] domain object.
     *
     * @param id Session ID (auto-generated if null)
     * @param topicId Optional topic being studied
     * @param curriculumId Optional curriculum this session belongs to
     * @param startTime Session start time
     * @param durationSeconds Session duration
     * @param turnCount Number of conversation turns
     */
    fun createSession(
        id: String = UUID.randomUUID().toString(),
        topicId: String? = null,
        curriculumId: String? = null,
        startTime: Long = System.currentTimeMillis(),
        durationSeconds: Long = 300,
        turnCount: Int = 10,
    ): Session =
        Session(
            id = id,
            topicId = topicId,
            curriculumId = curriculumId,
            startTime = startTime,
            durationSeconds = durationSeconds,
            turnCount = turnCount,
        )

    /**
     * Create a test [SessionEntity] for Room database.
     */
    fun createSessionEntity(
        id: String = UUID.randomUUID().toString(),
        topicId: String? = null,
        curriculumId: String? = null,
        startTime: Long = System.currentTimeMillis(),
        endTime: Long? = null,
        durationSeconds: Long = 0,
        turnCount: Int = 0,
        interruptionCount: Int = 0,
        totalCost: Double = 0.0,
    ): SessionEntity =
        SessionEntity(
            id = id,
            topicId = topicId,
            curriculumId = curriculumId,
            startTime = startTime,
            endTime = endTime,
            durationSeconds = durationSeconds,
            turnCount = turnCount,
            interruptionCount = interruptionCount,
            totalCost = totalCost,
        )

    // endregion

    // region Transcript

    /**
     * Create a test [TranscriptEntryEntity] for Room database.
     *
     * @param id Entry ID (auto-generated if null)
     * @param sessionId Parent session ID
     * @param role Who spoke ("user" or "assistant")
     * @param text Content of the utterance
     * @param timestamp When the utterance occurred
     */
    fun createTranscriptEntry(
        id: String = UUID.randomUUID().toString(),
        sessionId: String = UUID.randomUUID().toString(),
        role: String = "user",
        text: String = "Test transcript entry",
        timestamp: Long = System.currentTimeMillis(),
    ): TranscriptEntryEntity =
        TranscriptEntryEntity(
            id = id,
            sessionId = sessionId,
            role = role,
            text = text,
            timestamp = timestamp,
        )

    /**
     * Create a conversation transcript with alternating user/assistant messages.
     *
     * @param sessionId Session these entries belong to
     * @param turnCount Number of conversation turns (user + assistant = 1 turn)
     */
    fun createConversation(
        sessionId: String = UUID.randomUUID().toString(),
        turnCount: Int = 3,
    ): List<TranscriptEntryEntity> {
        val entries = mutableListOf<TranscriptEntryEntity>()
        var timestamp = System.currentTimeMillis()

        repeat(turnCount) { turn ->
            // User message
            entries.add(
                createTranscriptEntry(
                    sessionId = sessionId,
                    role = "user",
                    text = "User message ${turn + 1}",
                    timestamp = timestamp,
                ),
            )
            timestamp += 5000 // 5 seconds later

            // Assistant response
            entries.add(
                createTranscriptEntry(
                    sessionId = sessionId,
                    role = "assistant",
                    text = "Assistant response ${turn + 1}",
                    timestamp = timestamp,
                ),
            )
            timestamp += 10000 // 10 seconds later
        }

        return entries
    }

    // endregion

    // region Topic Progress

    /**
     * Create a test [TopicProgressEntity] for Room database.
     *
     * @param topicId Topic being tracked
     * @param curriculumId Curriculum this progress belongs to
     * @param timeSpentSeconds Time spent on topic
     * @param masteryLevel Mastery level (0.0 - 1.0)
     * @param lastAccessedAt Last time topic was accessed
     * @param completedSegments List of completed segment IDs
     * @param currentSegmentId ID of current segment (null if completed)
     */
    fun createTopicProgress(
        topicId: String = UUID.randomUUID().toString(),
        curriculumId: String = UUID.randomUUID().toString(),
        timeSpentSeconds: Long = 0,
        masteryLevel: Float = 0.0f,
        lastAccessedAt: Long = System.currentTimeMillis(),
        completedSegments: List<String> = emptyList(),
        currentSegmentId: String? = null,
    ): TopicProgressEntity =
        TopicProgressEntity(
            topicId = topicId,
            curriculumId = curriculumId,
            timeSpentSeconds = timeSpentSeconds,
            masteryLevel = masteryLevel,
            lastAccessedAt = lastAccessedAt,
            completedSegments = completedSegments,
            currentSegmentId = currentSegmentId,
        )

    // endregion

    // region LLM Messages

    /**
     * Create test LLM conversation messages.
     *
     * @param userMessages User messages to include
     * @param includeSystemPrompt Whether to include a system prompt
     */
    fun createLLMMessages(
        vararg userMessages: String,
        includeSystemPrompt: Boolean = true,
    ): List<LLMMessage> {
        val messages = mutableListOf<LLMMessage>()

        if (includeSystemPrompt) {
            messages.add(
                LLMMessage(
                    role = "system",
                    content = "You are a helpful AI tutor. Be concise and clear.",
                ),
            )
        }

        userMessages.forEachIndexed { index, message ->
            messages.add(LLMMessage(role = "user", content = message))

            // Add assistant response for all but the last message
            if (index < userMessages.lastIndex) {
                messages.add(
                    LLMMessage(
                        role = "assistant",
                        content = "Response to: $message",
                    ),
                )
            }
        }

        return messages
    }

    /**
     * Create a simple user-assistant conversation.
     */
    fun createSimpleConversation(): List<LLMMessage> =
        listOf(
            LLMMessage(role = "system", content = "You are a helpful AI tutor."),
            LLMMessage(role = "user", content = "What is machine learning?"),
            LLMMessage(
                role = "assistant",
                content = "Machine learning is a subset of AI that enables systems to learn from data.",
            ),
            LLMMessage(role = "user", content = "Can you give an example?"),
        )

    // endregion
}
