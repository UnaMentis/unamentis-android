package com.unamentis.core.tools.handlers

import android.util.Log
import com.unamentis.core.tools.LLMToolCall
import com.unamentis.core.tools.LLMToolDefinition
import com.unamentis.core.tools.LLMToolResult
import com.unamentis.core.tools.ToolExecutionContext
import com.unamentis.core.tools.ToolHandler
import com.unamentis.core.tools.ToolParameters
import com.unamentis.core.tools.ToolProperty
import com.unamentis.data.local.dao.TodoDao
import com.unamentis.data.model.Todo
import com.unamentis.data.model.TodoItemSource
import com.unamentis.data.model.TodoItemType
import com.unamentis.data.model.TodoPriority
import com.unamentis.data.model.TodoStatus
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tool handler for creating and managing todo items via LLM.
 *
 * This handler enables the AI tutor to create learning-related todos
 * during conversations, such as:
 * - Study reminders for topics that need reinforcement
 * - Practice exercises for concepts the student struggled with
 * - Review items for material covered in the session
 *
 * ## Supported Tools
 *
 * ### add_todo
 * Creates a new todo item with AI-suggested metadata.
 * ```
 * Arguments:
 *   title: string (required) - Task description
 *   type: "learning_target" | "reinforcement" (required) - Todo type
 *   notes: string (optional) - Additional details
 *   priority: "low" | "medium" | "high" (optional) - Priority level
 * ```
 *
 * ### mark_for_review
 * Creates a reinforcement todo for the current topic.
 * ```
 * Arguments:
 *   reason: string (optional) - Why the topic needs review
 * ```
 */
@Singleton
class TodoToolHandler
    @Inject
    constructor(
        private val todoDao: TodoDao,
    ) : ToolHandler {
        override val toolName: String = TOOL_ADD_TODO

        override fun getDefinition(): LLMToolDefinition {
            return LLMToolDefinition(
                name = TOOL_ADD_TODO,
                description =
                    """
                    Create a todo item for the student to follow up on after the session.
                    Use this when the student should practice a concept, review material,
                    or complete a learning activity. The todo will appear in their task list
                    with context linking back to this session.
                    """.trimIndent(),
                parameters =
                    ToolParameters(
                        properties =
                            mapOf(
                                "title" to
                                    ToolProperty(
                                        type = "string",
                                        description =
                                            "Clear, actionable task description " +
                                                "(e.g., 'Practice quadratic equations')",
                                    ),
                                "type" to
                                    ToolProperty(
                                        type = "string",
                                        description =
                                            "Type of todo: 'learning_target' for new concepts, " +
                                                "'reinforcement' for topics needing review",
                                        enum = listOf("learning_target", "reinforcement"),
                                    ),
                                "notes" to
                                    ToolProperty(
                                        type = "string",
                                        description = "Additional context or instructions (optional)",
                                    ),
                                "priority" to
                                    ToolProperty(
                                        type = "string",
                                        description = "Task priority level",
                                        enum = listOf("low", "medium", "high"),
                                    ),
                            ),
                        required = listOf("title", "type"),
                    ),
            )
        }

        /**
         * Get the tool definition for mark_for_review.
         *
         * This is a separate tool that creates a reinforcement todo
         * specifically for the current topic.
         */
        fun getMarkForReviewDefinition(): LLMToolDefinition {
            return LLMToolDefinition(
                name = TOOL_MARK_FOR_REVIEW,
                description =
                    """
                    Mark the current topic for review. Creates a reinforcement todo
                    linked to this topic that will remind the student to revisit
                    the material later. Use when the student shows confusion or
                    uncertainty about the current topic.
                    """.trimIndent(),
                parameters =
                    ToolParameters(
                        properties =
                            mapOf(
                                "reason" to
                                    ToolProperty(
                                        type = "string",
                                        description =
                                            "Why this topic needs review " +
                                                "(e.g., 'Student confused about electron configuration')",
                                    ),
                            ),
                        required = emptyList(),
                    ),
            )
        }

        override suspend fun handle(
            call: LLMToolCall,
            context: ToolExecutionContext,
        ): LLMToolResult {
            return when (call.name) {
                TOOL_ADD_TODO -> handleAddTodo(call, context)
                TOOL_MARK_FOR_REVIEW -> handleMarkForReview(call, context)
                else ->
                    LLMToolResult.error(
                        call.id,
                        "Unknown tool: ${call.name}. This handler supports: $TOOL_ADD_TODO, $TOOL_MARK_FOR_REVIEW",
                    )
            }
        }

        private suspend fun handleAddTodo(
            call: LLMToolCall,
            context: ToolExecutionContext,
        ): LLMToolResult {
            // Parse required arguments
            val title = call.getStringArgument("title")
            val type = call.getStringArgument("type")
            val validTypes = listOf("learning_target", "reinforcement")

            // Validate required arguments
            val validationError =
                when {
                    title.isNullOrBlank() -> "Missing required argument: title"
                    type.isNullOrBlank() -> "Missing required argument: type"
                    type !in validTypes -> "Invalid type: $type. Must be 'learning_target' or 'reinforcement'"
                    else -> null
                }

            if (validationError != null) {
                return LLMToolResult.error(call.id, validationError)
            }

            // Parse optional arguments
            val notes = call.getStringArgument("notes")
            val priorityStr = call.getStringArgument("priority") ?: "medium"
            val priority =
                when (priorityStr.lowercase()) {
                    "low" -> TodoPriority.LOW
                    "high" -> TodoPriority.HIGH
                    else -> TodoPriority.MEDIUM
                }

            // Determine item type from the type string
            val itemType =
                when (type) {
                    "learning_target" -> TodoItemType.LEARNING_TARGET
                    "reinforcement" -> TodoItemType.REINFORCEMENT
                    else -> TodoItemType.LEARNING_TARGET
                }

            // Create the todo
            val todo =
                Todo(
                    id = UUID.randomUUID().toString(),
                    title = title!!,
                    notes = buildTodoNotes(type!!, notes, context),
                    priority = priority,
                    status = TodoStatus.ACTIVE,
                    itemType = itemType,
                    source = TodoItemSource.VOICE,
                    sessionId = context.sessionId,
                    topicId = context.topicId,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    isAISuggested = true,
                    suggestionReason = "AI tutor suggested this $type during session",
                    suggestionConfidence = 0.85f,
                )

            return try {
                todoDao.insert(todo)
                Log.d(TAG, "Created AI-suggested todo: ${todo.id} - ${todo.title}")

                LLMToolResult.success(
                    call.id,
                    "Created todo: \"$title\" (${type.replace("_", " ")}). " +
                        "The student will see this in their task list.",
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create todo", e)
                LLMToolResult.error(call.id, "Failed to create todo: ${e.message}")
            }
        }

        private suspend fun handleMarkForReview(
            call: LLMToolCall,
            context: ToolExecutionContext,
        ): LLMToolResult {
            // Build title based on context
            val topicTitle = context.topicTitle
            val title =
                if (topicTitle != null) {
                    "Review: $topicTitle"
                } else {
                    "Review session material"
                }

            val reason = call.getStringArgument("reason")

            // Create the reinforcement todo
            val todo =
                Todo(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    notes = buildReviewNotes(reason, context),
                    priority = TodoPriority.MEDIUM,
                    status = TodoStatus.ACTIVE,
                    itemType = TodoItemType.REINFORCEMENT,
                    source = TodoItemSource.VOICE,
                    sessionId = context.sessionId,
                    topicId = context.topicId,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    isAISuggested = true,
                    suggestionReason = reason ?: "Topic marked for review by AI tutor",
                    suggestionConfidence = 0.9f,
                )

            return try {
                todoDao.insert(todo)
                Log.d(TAG, "Created review todo: ${todo.id} - ${todo.title}")

                val response =
                    buildString {
                        append("Marked for review: \"$title\"")
                        if (reason != null) {
                            append(" (Reason: $reason)")
                        }
                        append(". The student will be reminded to revisit this topic.")
                    }

                LLMToolResult.success(call.id, response)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create review todo", e)
                LLMToolResult.error(call.id, "Failed to mark for review: ${e.message}")
            }
        }

        private fun buildTodoNotes(
            type: String,
            userNotes: String?,
            context: ToolExecutionContext,
        ): String {
            return buildString {
                if (!userNotes.isNullOrBlank()) {
                    appendLine(userNotes)
                    appendLine()
                }

                appendLine("---")
                appendLine("Type: ${type.replace("_", " ").replaceFirstChar { it.uppercase() }}")

                context.topicTitle?.let {
                    appendLine("Topic: $it")
                }

                context.sessionId?.let {
                    appendLine("From session: $it")
                }

                appendLine("Created by AI tutor")
            }
        }

        private fun buildReviewNotes(
            reason: String?,
            context: ToolExecutionContext,
        ): String {
            return buildString {
                appendLine("This topic was flagged for review during your learning session.")
                appendLine()

                if (!reason.isNullOrBlank()) {
                    appendLine("Reason: $reason")
                    appendLine()
                }

                appendLine("---")
                context.topicId?.let {
                    appendLine("Topic ID: $it")
                }

                context.sessionId?.let {
                    appendLine("Session ID: $it")
                }

                appendLine("Suggested by AI tutor")
            }
        }

        companion object {
            private const val TAG = "TodoToolHandler"

            /** Tool name for adding a todo */
            const val TOOL_ADD_TODO = "add_todo"

            /** Tool name for marking current topic for review */
            const val TOOL_MARK_FOR_REVIEW = "mark_for_review"
        }
    }

/**
 * Combined handler that exposes both todo tools.
 *
 * This wrapper allows the [ToolCallProcessor] to handle both
 * add_todo and mark_for_review through a single handler registration.
 */
@Singleton
class MarkForReviewToolHandler
    @Inject
    constructor(
        private val todoToolHandler: TodoToolHandler,
    ) : ToolHandler {
        override val toolName: String = TodoToolHandler.TOOL_MARK_FOR_REVIEW

        override fun getDefinition(): LLMToolDefinition {
            return todoToolHandler.getMarkForReviewDefinition()
        }

        override suspend fun handle(
            call: LLMToolCall,
            context: ToolExecutionContext,
        ): LLMToolResult {
            return todoToolHandler.handle(call, context)
        }
    }
