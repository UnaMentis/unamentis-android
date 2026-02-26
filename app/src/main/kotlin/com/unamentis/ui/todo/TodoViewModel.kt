package com.unamentis.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.data.local.AppDatabase
import com.unamentis.data.model.Todo
import com.unamentis.data.model.TodoPriority
import com.unamentis.data.model.TodoStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the Todo screen.
 *
 * Responsibilities:
 * - Manage todo list with CRUD operations
 * - Filter todos by status (Active, Completed, Archived)
 * - Update todo status and priority
 * - Link todos to session context
 * - Support AI-suggested todos with due dates
 *
 * @property database App database
 */
@HiltViewModel
class TodoViewModel
    @Inject
    constructor(
        private val database: AppDatabase,
    ) : ViewModel() {
        /**
         * Selected filter tab.
         */
        private val _selectedTab = MutableStateFlow(TodoFilter.ACTIVE)
        val selectedTab: StateFlow<TodoFilter> = _selectedTab.asStateFlow()

        /**
         * Multi-select mode state.
         */
        private val _isSelectionMode = MutableStateFlow(false)
        val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

        /**
         * Set of selected todo IDs for batch operations.
         */
        private val _selectedTodoIds = MutableStateFlow<Set<String>>(emptySet())
        val selectedTodoIds: StateFlow<Set<String>> = _selectedTodoIds.asStateFlow()

        /**
         * All todos from database.
         */
        private val allTodos: Flow<List<Todo>> = database.todoDao().getAllTodos()

        /**
         * Filtered todos based on selected tab with smart sorting:
         * - Overdue items first
         * - Then by due date (soonest first)
         * - Then by priority
         * - Then by creation date
         */
        private val filteredTodos: StateFlow<List<Todo>> =
            combine(
                selectedTab,
                allTodos,
            ) { filter, todos ->
                val now = System.currentTimeMillis()
                when (filter) {
                    TodoFilter.ACTIVE -> todos.filter { it.status.isActive }
                    TodoFilter.COMPLETED -> todos.filter { it.status == TodoStatus.COMPLETED }
                    TodoFilter.ARCHIVED -> todos.filter { it.status == TodoStatus.ARCHIVED }
                    TodoFilter.AI_SUGGESTED -> todos.filter { it.isAISuggested && it.status.isActive }
                }.sortedWith(
                    // Overdue items first
                    compareBy<Todo> { todo ->
                        when {
                            todo.dueDate != null && todo.dueDate < now -> 0
                            todo.dueDate != null -> 1
                            else -> 2
                        }
                    }
                        // Then by due date (soonest first)
                        .thenBy { it.dueDate ?: Long.MAX_VALUE }
                        // Then by priority (high first)
                        .thenByDescending { it.priority.ordinal }
                        // Then by creation date (newest first)
                        .thenByDescending { it.createdAt },
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

        /**
         * Count of AI-suggested todos.
         */
        private val aiSuggestedCount: Flow<Int> =
            allTodos.map { todos ->
                todos.count { it.isAISuggested && it.status.isActive }
            }

        /**
         * Count of overdue todos.
         */
        private val overdueCount: Flow<Int> =
            allTodos.map { todos ->
                val now = System.currentTimeMillis()
                todos.count {
                    it.status.isActive &&
                        it.dueDate != null &&
                        it.dueDate < now
                }
            }

        /**
         * Combined UI state.
         */
        val uiState: StateFlow<TodoUiState> =
            combine(
                selectedTab,
                filteredTodos,
                combine(aiSuggestedCount, overdueCount) { ai, overdue -> ai to overdue },
                combine(isSelectionMode, selectedTodoIds) { mode, ids -> mode to ids },
            ) { tab, todos, (aiCount, overdue), (selectionMode, selectedIds) ->
                TodoUiState(
                    selectedTab = tab,
                    todos = todos,
                    aiSuggestedCount = aiCount,
                    overdueCount = overdue,
                    isSelectionMode = selectionMode,
                    selectedTodoIds = selectedIds,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = TodoUiState(),
            )

        /**
         * Select filter tab.
         */
        fun selectTab(tab: TodoFilter) {
            _selectedTab.value = tab
            // Clear selection when switching tabs
            exitSelectionMode()
        }

        // ==================== Selection Mode Methods ====================

        /**
         * Enter selection mode for batch operations.
         */
        fun enterSelectionMode() {
            _isSelectionMode.value = true
        }

        /**
         * Exit selection mode and clear all selections.
         */
        fun exitSelectionMode() {
            _isSelectionMode.value = false
            _selectedTodoIds.value = emptySet()
        }

        /**
         * Toggle selection of a todo.
         */
        fun toggleTodoSelection(todoId: String) {
            val currentSelection = _selectedTodoIds.value.toMutableSet()
            if (todoId in currentSelection) {
                currentSelection.remove(todoId)
            } else {
                currentSelection.add(todoId)
            }
            _selectedTodoIds.value = currentSelection

            // Exit selection mode if no items selected
            if (currentSelection.isEmpty()) {
                _isSelectionMode.value = false
            }
        }

        /**
         * Select all currently visible todos.
         */
        fun selectAll() {
            val visibleTodos = filteredTodos.value
            _selectedTodoIds.value = visibleTodos.map { it.id }.toSet()
        }

        /**
         * Clear all selections.
         */
        fun clearSelection() {
            _selectedTodoIds.value = emptySet()
        }

        // ==================== Batch Operation Methods ====================

        /**
         * Complete all selected todos.
         */
        fun batchComplete() {
            val selectedIds = _selectedTodoIds.value.toList()
            if (selectedIds.isEmpty()) return

            viewModelScope.launch {
                val now = System.currentTimeMillis()
                database.todoDao().updateStatusForIds(
                    ids = selectedIds,
                    status = TodoStatus.COMPLETED.name,
                    updatedAt = now,
                )
                exitSelectionMode()
            }
        }

        /**
         * Archive all selected todos.
         */
        fun batchArchive() {
            val selectedIds = _selectedTodoIds.value.toList()
            if (selectedIds.isEmpty()) return

            viewModelScope.launch {
                val now = System.currentTimeMillis()
                database.todoDao().updateStatusForIds(
                    ids = selectedIds,
                    status = TodoStatus.ARCHIVED.name,
                    updatedAt = now,
                )
                exitSelectionMode()
            }
        }

        /**
         * Delete all selected todos.
         */
        fun batchDelete() {
            val selectedIds = _selectedTodoIds.value.toList()
            if (selectedIds.isEmpty()) return

            viewModelScope.launch {
                database.todoDao().deleteByIds(selectedIds)
                exitSelectionMode()
            }
        }

        /**
         * Restore all selected todos to active status.
         */
        fun batchRestore() {
            val selectedIds = _selectedTodoIds.value.toList()
            if (selectedIds.isEmpty()) return

            viewModelScope.launch {
                val now = System.currentTimeMillis()
                database.todoDao().updateStatusForIds(
                    ids = selectedIds,
                    status = TodoStatus.ACTIVE.name,
                    updatedAt = now,
                )
                exitSelectionMode()
            }
        }

        /**
         * Update priority for all selected todos.
         */
        fun batchUpdatePriority(priority: TodoPriority) {
            val selectedIds = _selectedTodoIds.value.toList()
            if (selectedIds.isEmpty()) return

            viewModelScope.launch {
                selectedIds.forEach { id ->
                    val todo = database.todoDao().getById(id)
                    if (todo != null) {
                        database.todoDao().update(
                            todo.copy(
                                priority = priority,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
                exitSelectionMode()
            }
        }

        // ==================== Single Item CRUD Methods ====================

        /**
         * Create new todo.
         */
        fun createTodo(
            title: String,
            notes: String? = null,
            priority: TodoPriority = TodoPriority.MEDIUM,
            sessionId: String? = null,
            topicId: String? = null,
            dueDate: Long? = null,
        ) {
            viewModelScope.launch {
                val todo =
                    Todo(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        notes = notes,
                        priority = priority,
                        sessionId = sessionId,
                        topicId = topicId,
                        dueDate = dueDate,
                    )
                database.todoDao().insert(todo)
            }
        }

        /**
         * Update todo due date.
         */
        fun updateDueDate(
            todoId: String,
            dueDate: Long?,
        ) {
            viewModelScope.launch {
                val todo = database.todoDao().getById(todoId)
                if (todo != null) {
                    database.todoDao().update(
                        todo.copy(
                            dueDate = dueDate,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }

        /**
         * Accept an AI-suggested todo (remove AI flag, keep the todo).
         */
        fun acceptAISuggestion(todoId: String) {
            viewModelScope.launch {
                val todo = database.todoDao().getById(todoId)
                if (todo != null && todo.isAISuggested) {
                    database.todoDao().update(
                        todo.copy(
                            isAISuggested = false,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }

        /**
         * Dismiss an AI-suggested todo (delete it).
         */
        fun dismissAISuggestion(todoId: String) {
            viewModelScope.launch {
                val todo = database.todoDao().getById(todoId)
                if (todo != null && todo.isAISuggested) {
                    database.todoDao().delete(todo)
                }
            }
        }

        /**
         * Dismiss all AI-suggested todos.
         */
        fun dismissAllAISuggestions() {
            viewModelScope.launch {
                database.todoDao().deleteUnacceptedSuggestions()
            }
        }

        /**
         * Update existing todo.
         */
        fun updateTodo(todo: Todo) {
            viewModelScope.launch {
                database.todoDao().update(
                    todo.copy(updatedAt = System.currentTimeMillis()),
                )
            }
        }

        /**
         * Mark todo as completed.
         */
        fun completeTodo(todoId: String) {
            viewModelScope.launch {
                val todo = database.todoDao().getById(todoId)
                if (todo != null) {
                    database.todoDao().update(
                        todo.copy(
                            status = TodoStatus.COMPLETED,
                            completedAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }

        /**
         * Mark todo as active (uncomplete).
         */
        fun activateTodo(todoId: String) {
            viewModelScope.launch {
                val todo = database.todoDao().getById(todoId)
                if (todo != null) {
                    database.todoDao().update(
                        todo.copy(
                            status = TodoStatus.ACTIVE,
                            completedAt = null,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }

        /**
         * Archive todo.
         */
        fun archiveTodo(todoId: String) {
            viewModelScope.launch {
                val todo = database.todoDao().getById(todoId)
                if (todo != null) {
                    database.todoDao().update(
                        todo.copy(
                            status = TodoStatus.ARCHIVED,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }

        /**
         * Delete todo.
         */
        fun deleteTodo(todoId: String) {
            viewModelScope.launch {
                val todo = database.todoDao().getById(todoId)
                if (todo != null) {
                    database.todoDao().delete(todo)
                }
            }
        }

        /**
         * Update todo priority.
         */
        fun updatePriority(
            todoId: String,
            priority: TodoPriority,
        ) {
            viewModelScope.launch {
                val todo = database.todoDao().getById(todoId)
                if (todo != null) {
                    database.todoDao().update(
                        todo.copy(
                            priority = priority,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
    }

/**
 * Todo filter tabs.
 */
enum class TodoFilter(val displayName: String) {
    ACTIVE("Active"),
    COMPLETED("Completed"),
    ARCHIVED("Archived"),
    AI_SUGGESTED("AI Suggested"),
}

/**
 * UI state for Todo screen.
 */
data class TodoUiState(
    val selectedTab: TodoFilter = TodoFilter.ACTIVE,
    val todos: List<Todo> = emptyList(),
    val aiSuggestedCount: Int = 0,
    val overdueCount: Int = 0,
    val isSelectionMode: Boolean = false,
    val selectedTodoIds: Set<String> = emptySet(),
)
