package com.unamentis.ui.assistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.unamentis.R
import com.unamentis.ui.readinglist.ReadingListScreen
import com.unamentis.ui.todo.TodoScreen

/**
 * Assistant tab combining To-Do and Reading List with a segmented tab selector.
 *
 * Matches iOS AssistantTabView with segmented picker switching between
 * TodoListView and ReadingListView.
 *
 * @param onNavigateToReader Callback when user taps a reading item to open reader
 * @param onNavigateToPlayback Callback when user taps listen on a reading item
 */
@Composable
fun AssistantScreen(
    onNavigateToReader: (String) -> Unit = {},
    onNavigateToPlayback: (String) -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val todoLabel = stringResource(R.string.assistant_tab_todo)
    val readingLabel = stringResource(R.string.assistant_tab_reading)
    val tabDesc = stringResource(R.string.cd_assistant_tab_selector)

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = tabDesc },
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(todoLabel) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(readingLabel) },
            )
        }

        when (selectedTab) {
            0 -> TodoScreen()
            1 ->
                ReadingListScreen(
                    onNavigateToReader = onNavigateToReader,
                    onNavigateToPlayback = onNavigateToPlayback,
                )
        }
    }
}
