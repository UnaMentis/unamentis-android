package com.unamentis.ui.todo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AutoAwesome
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
import com.unamentis.ui.LocalScrollToTopHandler
import com.unamentis.ui.Routes
import com.unamentis.ui.util.safeProgress
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Todo screen - Task management.
 *
 * Features:
 * - Filter tabs (Active, Completed, Archived, AI Suggested)
 * - CRUD operations for todos
 * - Priority indicators
 * - Due date support with overdue indicators
 * - AI-suggested todos with confidence and reasoning
 * - Resume from context (linked to sessions/topics)
 * - Completion tracking
 *
 * Layout:
 * - Scrollable tab row for filters
 * - Todo list with cards
 * - FAB for adding new todos
 * - Bottom sheet for editing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(viewModel: TodoViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<Todo?>(null) }
    var showSuggestionInfo by remember { mutableStateOf<Todo?>(null) }
    var showBatchPriorityMenu by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Handle scroll-to-top events
    val scrollToTopHandler = LocalScrollToTopHandler.current
    LaunchedEffect(scrollToTopHandler) {
        scrollToTopHandler.scrollToTopEvents.collect { route ->
            if (route == Routes.TODO) {
                coroutineScope.launch {
                    listState.animateScrollToItem(0)
                }
            }
        }
    }

    // Handle back press to exit selection mode
    if (uiState.isSelectionMode) {
        BackHandler {
            viewModel.exitSelectionMode()
        }
    }

    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                // Selection mode top bar
                TopAppBar(
                    title = { Text("${uiState.selectedTodoIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit selection")
                        }
                    },
                    actions = {
                        // Select all
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                        }

                        // Batch complete (only for Active tab)
                        if (uiState.selectedTab == TodoFilter.ACTIVE) {
                            IconButton(
                                onClick = { viewModel.batchComplete() },
                                enabled = uiState.selectedTodoIds.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Complete all")
                            }
                        }

                        // Batch restore (only for Completed/Archived tabs)
                        if (uiState.selectedTab == TodoFilter.COMPLETED ||
                            uiState.selectedTab == TodoFilter.ARCHIVED
                        ) {
                            IconButton(
                                onClick = { viewModel.batchRestore() },
                                enabled = uiState.selectedTodoIds.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = "Restore all")
                            }
                        }

                        // Batch archive (only for Active tab)
                        if (uiState.selectedTab == TodoFilter.ACTIVE) {
                            IconButton(
                                onClick = { viewModel.batchArchive() },
                                enabled = uiState.selectedTodoIds.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.Archive, contentDescription = "Archive all")
                            }
                        }

                        // Priority menu
                        Box {
                            IconButton(
                                onClick = { showBatchPriorityMenu = true },
                                enabled = uiState.selectedTodoIds.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.Flag, contentDescription = "Change priority")
                            }
                            DropdownMenu(
                                expanded = showBatchPriorityMenu,
                                onDismissRequest = { showBatchPriorityMenu = false },
                            ) {
                                TodoPriority.entries.forEach { priority ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                when (priority) {
                                                    TodoPriority.HIGH -> "High Priority"
                                                    TodoPriority.MEDIUM -> "Medium Priority"
                                                    TodoPriority.LOW -> "Low Priority"
                                                },
                                            )
                                        },
                                        onClick = {
                                            viewModel.batchUpdatePriority(priority)
                                            showBatchPriorityMenu = false
                                        },
                                    )
                                }
                            }
                        }

                        // Delete
                        IconButton(
                            onClick = { showBatchDeleteConfirm = true },
                            enabled = uiState.selectedTodoIds.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete all",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                )
            } else {
                // Normal top bar
                TopAppBar(
                    title = { Text("To-Do") },
                    actions = {
                        // Overdue indicator badge
                        if (uiState.overdueCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                            ) {
                                Text("${uiState.overdueCount}")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (uiState.selectedTab == TodoFilter.ACTIVE && !uiState.isSelectionMode) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add todo")
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // Filter tabs - using ScrollableTabRow for more tabs
            ScrollableTabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                edgePadding = 16.dp,
            ) {
                TodoFilter.entries.forEach { filter ->
                    val badgeCount =
                        if (filter == TodoFilter.AI_SUGGESTED) uiState.aiSuggestedCount else null

                    Tab(
                        selected = uiState.selectedTab == filter,
                        onClick = { viewModel.selectTab(filter) },
                        text = {
                            if (badgeCount != null && badgeCount > 0) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(filter.displayName)
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    ) {
                                        Text("$badgeCount")
                                    }
                                }
                            } else {
                                Text(filter.displayName)
                            }
                        },
                        icon =
                            if (filter == TodoFilter.AI_SUGGESTED) {
                                { Icon(Icons.Outlined.AutoAwesome, contentDescription = "AI suggested") }
                            } else {
                                null
                            },
                    )
                }
            }

            // Todo list
            TodoList(
                todos = uiState.todos,
                listState = listState,
                isAISuggestedTab = uiState.selectedTab == TodoFilter.AI_SUGGESTED,
                isSelectionMode = uiState.isSelectionMode,
                selectedTodoIds = uiState.selectedTodoIds,
                onTodoClick = { editingTodo = it },
                onTodoLongClick = { todo ->
                    if (!uiState.isSelectionMode) {
                        viewModel.enterSelectionMode()
                    }
                    viewModel.toggleTodoSelection(todo.id)
                },
                onToggleSelection = { viewModel.toggleTodoSelection(it.id) },
                onToggleComplete = { todo ->
                    if (todo.status == TodoStatus.COMPLETED) {
                        viewModel.activateTodo(todo.id)
                    } else {
                        viewModel.completeTodo(todo.id)
                    }
                },
                onArchive = { viewModel.archiveTodo(it.id) },
                onDelete = { viewModel.deleteTodo(it.id) },
                onAcceptSuggestion = { viewModel.acceptAISuggestion(it.id) },
                onDismissSuggestion = { viewModel.dismissAISuggestion(it.id) },
                onShowSuggestionInfo = { showSuggestionInfo = it },
                onUpdateDueDate = { todo, dueDate -> viewModel.updateDueDate(todo.id, dueDate) },
            )
        }
    }

    // Batch delete confirmation dialog
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("Delete ${uiState.selectedTodoIds.size} items?") },
            text = {
                Text("This will permanently delete the selected to-do items. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.batchDelete()
                        showBatchDeleteConfirm = false
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Create dialog
    if (showCreateDialog) {
        TodoEditDialog(
            todo = null,
            onDismiss = { showCreateDialog = false },
            onSave = { title, notes, priority, dueDate ->
                viewModel.createTodo(title, notes, priority, dueDate = dueDate)
                showCreateDialog = false
            },
        )
    }

    // Edit dialog
    if (editingTodo != null) {
        TodoEditDialog(
            todo = editingTodo,
            onDismiss = { editingTodo = null },
            onSave = { title, notes, priority, dueDate ->
                viewModel.updateTodo(
                    editingTodo!!.copy(
                        title = title,
                        notes = notes,
                        priority = priority,
                        dueDate = dueDate,
                    ),
                )
                editingTodo = null
            },
        )
    }

    // AI suggestion info dialog
    if (showSuggestionInfo != null) {
        SuggestionInfoDialog(
            todo = showSuggestionInfo!!,
            onDismiss = { showSuggestionInfo = null },
        )
    }
}

/**
 * Todo list display.
 */
@Composable
private fun TodoList(
    todos: List<Todo>,
    listState: LazyListState,
    isAISuggestedTab: Boolean,
    isSelectionMode: Boolean,
    selectedTodoIds: Set<String>,
    onTodoClick: (Todo) -> Unit,
    onTodoLongClick: (Todo) -> Unit,
    onToggleSelection: (Todo) -> Unit,
    onToggleComplete: (Todo) -> Unit,
    onArchive: (Todo) -> Unit,
    onDelete: (Todo) -> Unit,
    onAcceptSuggestion: (Todo) -> Unit,
    onDismissSuggestion: (Todo) -> Unit,
    onShowSuggestionInfo: (Todo) -> Unit,
    onUpdateDueDate: (Todo, Long?) -> Unit,
) {
    if (todos.isEmpty()) {
        // Empty state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector =
                        if (isAISuggestedTab) {
                            Icons.Outlined.AutoAwesome
                        } else {
                            Icons.Default.CheckCircle
                        },
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Text(
                    text = if (isAISuggestedTab) "No AI suggestions" else "No tasks",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isAISuggestedTab) {
                    Text(
                        text = "AI will suggest tasks based on your learning sessions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = todos,
                key = { it.id },
            ) { todo ->
                TodoCard(
                    todo = todo,
                    isAISuggestedTab = isAISuggestedTab,
                    isSelectionMode = isSelectionMode,
                    isSelected = todo.id in selectedTodoIds,
                    onClick = {
                        if (isSelectionMode) {
                            onToggleSelection(todo)
                        } else {
                            onTodoClick(todo)
                        }
                    },
                    onLongClick = { onTodoLongClick(todo) },
                    onToggleComplete = { onToggleComplete(todo) },
                    onArchive = { onArchive(todo) },
                    onDelete = { onDelete(todo) },
                    onAcceptSuggestion = { onAcceptSuggestion(todo) },
                    onDismissSuggestion = { onDismissSuggestion(todo) },
                    onShowSuggestionInfo = { onShowSuggestionInfo(todo) },
                    onUpdateDueDate = { dueDate -> onUpdateDueDate(todo, dueDate) },
                )
            }
        }
    }
}

/**
 * Individual todo card.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TodoCard(
    todo: Todo,
    isAISuggestedTab: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleComplete: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onAcceptSuggestion: () -> Unit,
    onDismissSuggestion: () -> Unit,
    onShowSuggestionInfo: () -> Unit,
    onUpdateDueDate: (Long?) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val isOverdue = todo.dueDate != null && todo.dueDate < now && todo.status == TodoStatus.ACTIVE

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    when {
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        isOverdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        todo.isAISuggested -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // AI suggestion indicator
            if (todo.isAISuggested) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "AI Suggested",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (todo.suggestionConfidence != null) {
                        Text(
                            text = "(${(todo.suggestionConfidence * 100).toInt()}% confidence)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = onShowSuggestionInfo,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "View suggestion reason",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Selection checkbox or completion checkbox
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onClick() },
                        )
                    } else {
                        Checkbox(
                            checked = todo.status == TodoStatus.COMPLETED,
                            onCheckedChange = { onToggleComplete() },
                        )
                    }

                    // Priority indicator
                    PriorityBadge(priority = todo.priority)

                    // Title
                    Text(
                        text = todo.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textDecoration =
                            if (todo.status == TodoStatus.COMPLETED) {
                                TextDecoration.LineThrough
                            } else {
                                null
                            },
                        color =
                            if (todo.status == TodoStatus.COMPLETED) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                }

                // More menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (todo.dueDate != null) "Change due date" else "Set due date") },
                            onClick = {
                                showDatePicker = true
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.CalendarMonth, null) },
                        )
                        if (todo.dueDate != null) {
                            DropdownMenuItem(
                                text = { Text("Remove due date") },
                                onClick = {
                                    onUpdateDueDate(null)
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.EventBusy, null) },
                            )
                        }
                        if (todo.status != TodoStatus.ARCHIVED) {
                            DropdownMenuItem(
                                text = { Text("Archive") },
                                onClick = {
                                    onArchive()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Archive, null) },
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
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                        )
                    }
                }
            }

            // Notes
            if (!todo.notes.isNullOrBlank()) {
                Text(
                    text = todo.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Due date
                if (todo.dueDate != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint =
                                if (isOverdue) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                        Text(
                            text =
                                if (isOverdue) {
                                    "Overdue: ${formatDate(todo.dueDate)}"
                                } else {
                                    "Due: ${formatDate(todo.dueDate)}"
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                if (isOverdue) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }

                Text(
                    text = "Created ${formatDate(todo.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (todo.completedAt != null) {
                    Text(
                        text = "Completed ${formatDate(todo.completedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // AI suggestion action buttons (only in AI suggested tab)
            if (isAISuggestedTab && todo.isAISuggested) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismissSuggestion,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dismiss")
                    }
                    Button(
                        onClick = onAcceptSuggestion,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Accept")
                    }
                }
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        DatePickerDialog(
            initialDate = todo.dueDate,
            onDismiss = { showDatePicker = false },
            onDateSelected = { selectedDate ->
                onUpdateDueDate(selectedDate)
                showDatePicker = false
            },
        )
    }
}

/**
 * Priority badge indicator.
 */
@Composable
private fun PriorityBadge(priority: TodoPriority) {
    val (color, text) =
        when (priority) {
            TodoPriority.HIGH -> MaterialTheme.colorScheme.error to "H"
            TodoPriority.MEDIUM -> MaterialTheme.colorScheme.secondary to "M"
            TodoPriority.LOW -> MaterialTheme.colorScheme.outline to "L"
        }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = color,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onError,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Todo edit/create dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoEditDialog(
    todo: Todo?,
    onDismiss: () -> Unit,
    onSave: (String, String?, TodoPriority, Long?) -> Unit,
) {
    var title by remember { mutableStateOf(todo?.title ?: "") }
    var notes by remember { mutableStateOf(todo?.notes ?: "") }
    var priority by remember { mutableStateOf(todo?.priority ?: TodoPriority.MEDIUM) }
    var dueDate by remember { mutableStateOf(todo?.dueDate) }
    var showDatePicker by remember { mutableStateOf(false) }

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
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Priority selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Priority",
                        style = MaterialTheme.typography.labelMedium,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                        },
                                    )
                                },
                            )
                        }
                    }
                }

                // Due date selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Due Date (optional)",
                        style = MaterialTheme.typography.labelMedium,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (dueDate != null) formatDate(dueDate!!) else "Set due date",
                            )
                        }
                        if (dueDate != null) {
                            IconButton(
                                onClick = { dueDate = null },
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear due date",
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title, notes.takeIf { it.isNotBlank() }, priority, dueDate)
                    }
                },
                enabled = title.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )

    if (showDatePicker) {
        DatePickerDialog(
            initialDate = dueDate,
            onDismiss = { showDatePicker = false },
            onDateSelected = { selectedDate ->
                dueDate = selectedDate
                showDatePicker = false
            },
        )
    }
}

/**
 * Date picker dialog for selecting due dates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(
    initialDate: Long?,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit,
) {
    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initialDate ?: System.currentTimeMillis(),
        )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { onDateSelected(it) }
                },
                enabled = datePickerState.selectedDateMillis != null,
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

/**
 * Dialog showing AI suggestion reasoning.
 */
@Composable
private fun SuggestionInfoDialog(
    todo: Todo,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("AI Suggestion") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                if (todo.suggestionReason != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Why this was suggested:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = todo.suggestionReason,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (todo.suggestionConfidence != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Confidence:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(
                            progress = { safeProgress(todo.suggestionConfidence) },
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(todo.suggestionConfidence * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

/**
 * Format timestamp for display.
 */
private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
