package com.unamentis.ui.todo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unamentis.data.model.Todo
import com.unamentis.data.model.TodoPriority
import com.unamentis.data.model.TodoStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * Todo screen - Task management.
 *
 * Features:
 * - Filter tabs (Active, Completed, Archived)
 * - CRUD operations for todos
 * - Priority indicators
 * - Resume from context (linked to sessions/topics)
 * - Completion tracking
 *
 * Layout:
 * - Tab row for filters
 * - Todo list with cards
 * - FAB for adding new todos
 * - Bottom sheet for editing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    viewModel: TodoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<Todo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("To-Do") }
            )
        },
        floatingActionButton = {
            if (uiState.selectedTab == TodoFilter.ACTIVE) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add todo")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter tabs
            TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                Tab(
                    selected = uiState.selectedTab == TodoFilter.ACTIVE,
                    onClick = { viewModel.selectTab(TodoFilter.ACTIVE) },
                    text = { Text("Active") }
                )
                Tab(
                    selected = uiState.selectedTab == TodoFilter.COMPLETED,
                    onClick = { viewModel.selectTab(TodoFilter.COMPLETED) },
                    text = { Text("Completed") }
                )
                Tab(
                    selected = uiState.selectedTab == TodoFilter.ARCHIVED,
                    onClick = { viewModel.selectTab(TodoFilter.ARCHIVED) },
                    text = { Text("Archived") }
                )
            }

            // Todo list
            TodoList(
                todos = uiState.todos,
                onTodoClick = { editingTodo = it },
                onToggleComplete = { todo ->
                    if (todo.status == TodoStatus.COMPLETED) {
                        viewModel.activateTodo(todo.id)
                    } else {
                        viewModel.completeTodo(todo.id)
                    }
                },
                onArchive = { viewModel.archiveTodo(it.id) },
                onDelete = { viewModel.deleteTodo(it.id) }
            )
        }
    }

    // Create dialog
    if (showCreateDialog) {
        TodoEditDialog(
            todo = null,
            onDismiss = { showCreateDialog = false },
            onSave = { title, notes, priority ->
                viewModel.createTodo(title, notes, priority)
                showCreateDialog = false
            }
        )
    }

    // Edit dialog
    if (editingTodo != null) {
        TodoEditDialog(
            todo = editingTodo,
            onDismiss = { editingTodo = null },
            onSave = { title, notes, priority ->
                viewModel.updateTodo(
                    editingTodo!!.copy(
                        title = title,
                        notes = notes,
                        priority = priority
                    )
                )
                editingTodo = null
            }
        )
    }
}

/**
 * Todo list display.
 */
@Composable
private fun TodoList(
    todos: List<Todo>,
    onTodoClick: (Todo) -> Unit,
    onToggleComplete: (Todo) -> Unit,
    onArchive: (Todo) -> Unit,
    onDelete: (Todo) -> Unit
) {
    if (todos.isEmpty()) {
        // Empty state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = "No tasks",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = todos,
                key = { it.id }
            ) { todo ->
                TodoCard(
                    todo = todo,
                    onClick = { onTodoClick(todo) },
                    onToggleComplete = { onToggleComplete(todo) },
                    onArchive = { onArchive(todo) },
                    onDelete = { onDelete(todo) }
                )
            }
        }
    }
}

/**
 * Individual todo card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoCard(
    todo: Todo,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Checkbox
                    Checkbox(
                        checked = todo.status == TodoStatus.COMPLETED,
                        onCheckedChange = { onToggleComplete() }
                    )

                    // Priority indicator
                    PriorityBadge(priority = todo.priority)

                    // Title
                    Text(
                        text = todo.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (todo.status == TodoStatus.COMPLETED) {
                            TextDecoration.LineThrough
                        } else null,
                        color = if (todo.status == TodoStatus.COMPLETED) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else MaterialTheme.colorScheme.onSurface
                    )
                }

                // More menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (todo.status != TodoStatus.ARCHIVED) {
                            DropdownMenuItem(
                                text = { Text("Archive") },
                                onClick = {
                                    onArchive()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Archive, null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                onDelete()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }

            // Notes
            if (!todo.notes.isNullOrBlank()) {
                Text(
                    text = todo.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Metadata
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Created ${formatDate(todo.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (todo.completedAt != null) {
                    Text(
                        text = "Completed ${formatDate(todo.completedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Priority badge indicator.
 */
@Composable
private fun PriorityBadge(priority: TodoPriority) {
    val (color, text) = when (priority) {
        TodoPriority.HIGH -> MaterialTheme.colorScheme.error to "H"
        TodoPriority.MEDIUM -> MaterialTheme.colorScheme.secondary to "M"
        TodoPriority.LOW -> MaterialTheme.colorScheme.outline to "L"
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = color
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onError,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Todo edit/create dialog.
 */
@Composable
private fun TodoEditDialog(
    todo: Todo?,
    onDismiss: () -> Unit,
    onSave: (String, String?, TodoPriority) -> Unit
) {
    var title by remember { mutableStateOf(todo?.title ?: "") }
    var notes by remember { mutableStateOf(todo?.notes ?: "") }
    var priority by remember { mutableStateOf(todo?.priority ?: TodoPriority.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (todo == null) "Create Todo" else "Edit Todo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                // Priority selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Priority",
                        style = MaterialTheme.typography.labelMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TodoPriority.entries.forEach { p ->
                            FilterChip(
                                selected = priority == p,
                                onClick = { priority = p },
                                label = {
                                    Text(
                                        when (p) {
                                            TodoPriority.LOW -> "Low"
                                            TodoPriority.MEDIUM -> "Medium"
                                            TodoPriority.HIGH -> "High"
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title, notes.takeIf { it.isNotBlank() }, priority)
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Format timestamp for display.
 */
private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
