package com.unamentis.ui.learning

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.unamentis.R
import com.unamentis.ui.curriculum.CurriculumScreen

/**
 * Main Learning tab screen with Curriculum and Modules sub-tabs.
 *
 * Matches iOS LearningView pattern: segmented tab control switching
 * between CurriculumScreen (existing) and ModulesScreen.
 *
 * @param initialCurriculumId Optional curriculum ID for deep linking
 * @param onNavigateToSession Callback to start a session with curriculum/topic context
 * @param onNavigateToModule Callback to open a module by ID
 */
@Composable
fun LearningScreen(
    initialCurriculumId: String? = null,
    onNavigateToSession: (String, String?) -> Unit = { _, _ -> },
    onNavigateToModule: (String) -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val curriculumTabLabel = stringResource(R.string.learning_tab_curriculum)
    val modulesTabLabel = stringResource(R.string.learning_tab_modules)

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.testTag("learning_tab_row"),
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(curriculumTabLabel) },
                modifier =
                    Modifier.semantics {
                        contentDescription = curriculumTabLabel
                    },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(modulesTabLabel) },
                modifier =
                    Modifier.semantics {
                        contentDescription = modulesTabLabel
                    },
            )
        }

        when (selectedTab) {
            0 ->
                CurriculumScreen(
                    initialCurriculumId = initialCurriculumId,
                    onNavigateToSession = onNavigateToSession,
                )
            1 ->
                ModulesScreen(
                    onNavigateToModule = onNavigateToModule,
                )
        }
    }
}
