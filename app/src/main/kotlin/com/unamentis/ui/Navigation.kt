package com.unamentis.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.unamentis.ui.analytics.AnalyticsScreen
import com.unamentis.ui.curriculum.CurriculumScreen
import com.unamentis.ui.history.HistoryScreen
import com.unamentis.ui.session.SessionScreen
import com.unamentis.ui.settings.SettingsScreen
import com.unamentis.ui.todo.TodoScreen

/**
 * Navigation destinations for the main bottom navigation.
 */
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Session : Screen("session", "Session", Icons.Default.Mic)
    data object Curriculum : Screen("curriculum", "Curriculum", Icons.Default.Book)
    data object Todo : Screen("todo", "To-Do", Icons.Default.Checklist)
    data object History : Screen("history", "History", Icons.Default.History)
    data object Analytics : Screen("analytics", "Analytics", Icons.Default.Analytics)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

/**
 * Main navigation host for UnaMentis with bottom navigation.
 */
@Composable
fun UnaMentisNavHost() {
    val navController = rememberNavController()
    val items = listOf(
        Screen.Session,
        Screen.Curriculum,
        Screen.Todo,
        Screen.History,
        Screen.Analytics,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                // Pop up to the start destination to avoid building up a large stack
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Session.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Session.route) { SessionScreen() }
            composable(Screen.Curriculum.route) { CurriculumScreen() }
            composable(Screen.Todo.route) { TodoScreen() }
            composable(Screen.History.route) { HistoryScreen() }
            composable(Screen.Analytics.route) { AnalyticsScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
