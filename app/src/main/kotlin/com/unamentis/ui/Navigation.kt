package com.unamentis.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.unamentis.R
import com.unamentis.core.network.ConnectivityMonitor
import com.unamentis.navigation.DeepLinkDestination
import com.unamentis.navigation.DeepLinkRoutes
import com.unamentis.ui.analytics.AnalyticsScreen
import com.unamentis.ui.components.OfflineBanner
import com.unamentis.ui.curriculum.CurriculumScreen
import com.unamentis.ui.history.HistoryScreen
import com.unamentis.ui.session.SessionScreen
import com.unamentis.ui.settings.SettingsScreen
import com.unamentis.ui.todo.TodoScreen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Navigation destinations for the main bottom navigation.
 *
 * @property route The navigation route string
 * @property titleResId String resource ID for the tab title
 * @property icon The icon to display in the navigation bar
 */
sealed class Screen(
    val route: String,
    @StringRes val titleResId: Int,
    val icon: ImageVector,
) {
    data object Session : Screen("session", R.string.tab_session, Icons.Default.Mic)

    data object Curriculum : Screen("curriculum", R.string.tab_curriculum, Icons.Default.Book)

    data object Todo : Screen("todo", R.string.tab_todo, Icons.Default.Checklist)

    data object History : Screen("history", R.string.tab_history, Icons.Default.History)

    data object Analytics : Screen("analytics", R.string.tab_analytics, Icons.Default.Analytics)

    data object Settings : Screen("settings", R.string.tab_settings, Icons.Default.Settings)
}

/**
 * Extended routes for deep linking with parameters.
 */
object Routes {
    const val SESSION = "session"
    const val SESSION_START = "session/start?curriculum_id={curriculum_id}&topic_id={topic_id}"
    const val CURRICULUM = "curriculum"
    const val CURRICULUM_DETAIL = "curriculum/{id}"
    const val TODO = "todo"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{id}"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings?section={section}"
}

/**
 * Handler for scroll-to-top events when tapping the current tab.
 *
 * Each screen can subscribe to `scrollToTopEvents` to receive notifications
 * when the user taps on an already-selected tab, triggering a scroll to top.
 */
class ScrollToTopHandler {
    private val _scrollToTopEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /**
     * Flow of scroll-to-top events. Emits the route of the tab that was tapped.
     */
    val scrollToTopEvents: SharedFlow<String> = _scrollToTopEvents.asSharedFlow()

    /**
     * Trigger a scroll-to-top event for the given route.
     */
    fun triggerScrollToTop(route: String) {
        _scrollToTopEvents.tryEmit(route)
    }
}

/**
 * CompositionLocal providing access to the scroll-to-top handler.
 */
val LocalScrollToTopHandler = compositionLocalOf { ScrollToTopHandler() }

/**
 * Main navigation host for UnaMentis with bottom navigation and deep link support.
 *
 * @param connectivityMonitor Monitor for network connectivity state
 * @param initialDeepLink Optional deep link destination to navigate to on launch
 * @param onDeepLinkConsumed Callback when the deep link has been handled
 */
@Composable
fun UnaMentisNavHost(
    connectivityMonitor: ConnectivityMonitor,
    initialDeepLink: DeepLinkDestination? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val scrollToTopHandler = remember { ScrollToTopHandler() }

    // Primary tabs shown in the bottom navigation
    val primaryTabs =
        listOf(
            Screen.Session,
            Screen.Curriculum,
            Screen.Todo,
            Screen.History,
        )

    // Items hidden in the "More" menu
    val moreItems =
        listOf(
            Screen.Analytics,
            Screen.Settings,
        )

    // Handle deep link navigation
    LaunchedEffect(initialDeepLink) {
        if (initialDeepLink != null && initialDeepLink !is DeepLinkDestination.Unknown) {
            val route = initialDeepLink.toNavigationRoute()
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
            onDeepLinkConsumed()
        }
    }

    CompositionLocalProvider(LocalScrollToTopHandler provides scrollToTopHandler) {
        Scaffold(
            bottomBar = {
                UnaMentisBottomBar(
                    navController = navController,
                    primaryTabs = primaryTabs,
                    moreItems = moreItems,
                    scrollToTopHandler = scrollToTopHandler,
                )
            },
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                // Offline banner at the top
                OfflineBanner(connectivityMonitor = connectivityMonitor)

                NavHost(
                    navController = navController,
                    startDestination = Screen.Session.route,
                    modifier = Modifier.weight(1f),
                ) {
                    // Session tab with deep link support
                    composable(
                        route = Routes.SESSION,
                        deepLinks =
                            listOf(
                                navDeepLink { uriPattern = DeepLinkRoutes.URI_SESSION },
                            ),
                    ) {
                        SessionScreen()
                    }

                    // Session start with curriculum/topic context
                    composable(
                        route = Routes.SESSION_START,
                        arguments =
                            listOf(
                                navArgument("curriculum_id") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                },
                                navArgument("topic_id") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                },
                            ),
                        deepLinks =
                            listOf(
                                navDeepLink {
                                    uriPattern = "${DeepLinkRoutes.URI_SESSION_START}?" +
                                        "${DeepLinkRoutes.PARAM_CURRICULUM_ID}={curriculum_id}&" +
                                        "${DeepLinkRoutes.PARAM_TOPIC_ID}={topic_id}"
                                },
                            ),
                    ) { backStackEntry ->
                        val curriculumId = backStackEntry.arguments?.getString("curriculum_id")
                        val topicId = backStackEntry.arguments?.getString("topic_id")
                        SessionScreen(
                            initialCurriculumId = curriculumId,
                            initialTopicId = topicId,
                        )
                    }

                    // Curriculum tab with deep link support
                    composable(
                        route = Routes.CURRICULUM,
                        deepLinks =
                            listOf(
                                navDeepLink { uriPattern = DeepLinkRoutes.URI_CURRICULUM },
                            ),
                    ) {
                        CurriculumScreen()
                    }

                    // Curriculum detail
                    composable(
                        route = Routes.CURRICULUM_DETAIL,
                        arguments =
                            listOf(
                                navArgument("id") { type = NavType.StringType },
                            ),
                        deepLinks =
                            listOf(
                                navDeepLink { uriPattern = DeepLinkRoutes.URI_CURRICULUM_DETAIL },
                            ),
                    ) { backStackEntry ->
                        val curriculumId = backStackEntry.arguments?.getString("id")
                        CurriculumScreen(initialCurriculumId = curriculumId)
                    }

                    // To-Do tab with deep link support
                    composable(
                        route = Routes.TODO,
                        deepLinks =
                            listOf(
                                navDeepLink { uriPattern = DeepLinkRoutes.URI_TODO },
                            ),
                    ) {
                        TodoScreen()
                    }

                    // History tab with deep link support
                    composable(
                        route = Routes.HISTORY,
                        deepLinks =
                            listOf(
                                navDeepLink { uriPattern = DeepLinkRoutes.URI_HISTORY },
                            ),
                    ) {
                        HistoryScreen()
                    }

                    // History detail
                    composable(
                        route = Routes.HISTORY_DETAIL,
                        arguments =
                            listOf(
                                navArgument("id") { type = NavType.StringType },
                            ),
                        deepLinks =
                            listOf(
                                navDeepLink { uriPattern = DeepLinkRoutes.URI_HISTORY_DETAIL },
                            ),
                    ) { backStackEntry ->
                        val sessionId = backStackEntry.arguments?.getString("id")
                        HistoryScreen(initialSessionId = sessionId)
                    }

                    // Analytics tab with deep link support
                    composable(
                        route = Routes.ANALYTICS,
                        deepLinks =
                            listOf(
                                navDeepLink { uriPattern = DeepLinkRoutes.URI_ANALYTICS },
                            ),
                    ) {
                        AnalyticsScreen()
                    }

                    // Settings tab with deep link support
                    composable(
                        route = Routes.SETTINGS,
                        arguments =
                            listOf(
                                navArgument("section") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                },
                            ),
                        deepLinks =
                            listOf(
                                navDeepLink { uriPattern = DeepLinkRoutes.URI_SETTINGS },
                                navDeepLink {
                                    uriPattern = "${DeepLinkRoutes.URI_SETTINGS}?" +
                                        "${DeepLinkRoutes.PARAM_SECTION}={section}"
                                },
                            ),
                    ) { backStackEntry ->
                        val section = backStackEntry.arguments?.getString("section")
                        SettingsScreen(initialSection = section)
                    }
                }
            }
        }
    }
}

/**
 * Bottom navigation bar with 4 primary tabs and a "More" menu.
 * Triggers scroll-to-top when tapping the currently selected tab.
 */
@Composable
private fun UnaMentisBottomBar(
    navController: NavHostController,
    primaryTabs: List<Screen>,
    moreItems: List<Screen>,
    scrollToTopHandler: ScrollToTopHandler,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    var showMoreMenu by remember { mutableStateOf(false) }

    // Check if a "More" menu item is currently selected
    val isMoreItemSelected =
        moreItems.any { screen ->
            currentDestination?.hierarchy?.any {
                it.route?.startsWith(screen.route) == true
            } == true
        }

    NavigationBar {
        // Primary tabs
        primaryTabs.forEach { screen ->
            val isSelected =
                currentDestination?.hierarchy?.any {
                    it.route?.startsWith(screen.route) == true
                } == true
            val title = stringResource(screen.titleResId)
            val tabContentDescription = stringResource(R.string.nav_tab_content_description, title)

            NavigationBarItem(
                modifier =
                    Modifier
                        .testTag("nav_${screen.route}")
                        .semantics { contentDescription = tabContentDescription },
                icon = { Icon(screen.icon, contentDescription = null) },
                label = { Text(title) },
                selected = isSelected,
                onClick = {
                    if (isSelected) {
                        // Already on this tab - trigger scroll to top
                        scrollToTopHandler.triggerScrollToTop(screen.route)
                    } else {
                        // Navigate to the tab
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        }

        // More menu item
        val moreLabel = stringResource(R.string.nav_more_label)
        val moreContentDescription = stringResource(R.string.nav_tab_content_description, moreLabel)
        NavigationBarItem(
            modifier =
                Modifier
                    .testTag("nav_more")
                    .semantics { contentDescription = moreContentDescription },
            icon = {
                Icon(
                    Icons.Default.MoreHoriz,
                    contentDescription = null,
                )
            },
            label = { Text(moreLabel) },
            selected = isMoreItemSelected,
            onClick = { showMoreMenu = true },
        )

        // More dropdown menu
        DropdownMenu(
            expanded = showMoreMenu,
            onDismissRequest = { showMoreMenu = false },
        ) {
            moreItems.forEach { screen ->
                val menuTitle = stringResource(screen.titleResId)
                val menuContentDescription =
                    stringResource(R.string.nav_tab_content_description, menuTitle)
                DropdownMenuItem(
                    modifier =
                        Modifier
                            .testTag("menu_${screen.route}")
                            .semantics { contentDescription = menuContentDescription },
                    text = { Text(menuTitle) },
                    onClick = {
                        showMoreMenu = false
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    leadingIcon = {
                        Icon(screen.icon, contentDescription = null)
                    },
                )
            }
        }
    }
}
