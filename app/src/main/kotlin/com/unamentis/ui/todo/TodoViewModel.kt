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
 *
 * @property database App database
 */
@HiltViewModel
class TodoViewModel @Inject constructor(
    private val database: AppDatabase
) : ViewModel() {

    /**
     * Selected filter tab.
     */
    private val _selectedTab = MutableStateFlow(TodoFilter.ACTIVE)
    val selectedTab: StateFlow<TodoFilter> = _selectedTab.asStateFlow()

    /**
     * All todos from database.
     */
    private val allTodos: Flow<List<Todo>> = database.todoDao().getAllTodos()

    /**
     * Filtered todos based on selected tab.
     */
    private val filteredTodos: StateFlow<List<Todo>> = combine(
        selectedTab,
        allTodos
    ) { filter, todos ->
        when (filter) {
            TodoFilter.ACTIVE -> todos.filter { it.status == TodoStatus.ACTIVE }
            TodoFilter.COMPLETED -> todos.filter { it.status == TodoStatus.COMPLETED }
            TodoFilter.ARCHIVED -> todos.filter { it.status == TodoStatus.ARCHIVED }
        }.sortedWith(
            compareByDescending<Todo> { it.priority.ordinal }
                .thenByDescending { it.createdAt }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /**
     * Combined UI state.
     */
    val uiState: StateFlow<TodoUiState> = combine(
        selectedTab,
        filteredTodos
    ) { tab, todos ->
        TodoUiState(
            selectedTab = tab,
            todos = todos
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TodoUiState()
    )

    /**
     * Select filter tab.
     */
    fun selectTab(tab: TodoFilter) {
        _selectedTab.value = tab
    }

    /**
     * Create new todo.
     */
    fun createTodo(
        title: String,
        notes: String? = null,
        priority: TodoPriority = TodoPriority.MEDIUM,
        sessionId: String? = null,
        topicId: String? = null
    ) {
        viewModelScope.launch {
            val todo = Todo(
                id = UUID.randomUUID().toString(),
                title = title,
                notes = notes,
                priority = priority,
                sessionId = sessionId,
                topicId = topicId
            )
            database.todoDao().insert(todo)
        }
    }

    /**
     * Update existing todo.
     */
    fun updateTodo(todo: Todo) {
        viewModelScope.launch {
            database.todoDao().update(
                todo.copy(updatedAt = System.currentTimeMillis())
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
                        updatedAt = System.currentTimeMillis()
                    )
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
                        updatedAt = System.currentTimeMillis()
                    )
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
                        updatedAt = System.currentTimeMillis()
                    )
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
    fun updatePriority(todoId: String, priority: TodoPriority) {
        viewModelScope.launch {
            val todo = database.todoDao().getById(todoId)
            if (todo != null) {
                database.todoDao().update(
                    todo.copy(
                        priority = priority,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}

/**
 * Todo filter tabs.
 */
enum class TodoFilter {
    ACTIVE,
    COMPLETED,
    ARCHIVED
}

/**
 * UI state for Todo screen.
 */
data class TodoUiState(
    val selectedTab: TodoFilter = TodoFilter.ACTIVE,
    val todos: List<Todo> = emptyList()
)
