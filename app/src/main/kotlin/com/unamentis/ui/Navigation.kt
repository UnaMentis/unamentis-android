package com.unamentis.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.unamentis.core.accessibility.AccessibilityChecker
import com.unamentis.core.network.ConnectivityMonitor
import com.unamentis.data.local.OnboardingPreferences
import com.unamentis.navigation.DeepLinkDestination
import com.unamentis.navigation.DeepLinkRoutes
import com.unamentis.ui.analytics.AnalyticsScreen
import com.unamentis.ui.components.OfflineBanner
import com.unamentis.ui.curriculum.CurriculumScreen
import com.unamentis.ui.history.HistoryScreen
import com.unamentis.ui.onboarding.OnboardingScreen
import com.unamentis.ui.session.SessionActivityState
import com.unamentis.ui.session.SessionScreen
import com.unamentis.ui.settings.AboutScreen
import com.unamentis.ui.settings.DebugScreen
import com.unamentis.ui.settings.ServerSettingsScreen
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
    data object Session : Screen("session", R.string.tab_session, Icons.Default.GraphicEq)

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
    const val ONBOARDING = "onboarding"
    const val SESSION = "session"
    const val SESSION_START = "session/start?curriculum_id={curriculum_id}&topic_id={topic_id}"
    const val CURRICULUM = "curriculum"
    const val CURRICULUM_DETAIL = "curriculum/{id}"
    const val TODO = "todo"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{id}"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings?section={section}"
    const val SERVER_SETTINGS = "settings/servers"
    const val ABOUT = "settings/about"
    const val DEBUG = "settings/debug"
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
 * Shows all 6 tabs in the bottom navigation, matching iOS layout.
 * Tab bar hides during active sessions (when not paused) for immersive experience.
 * On first launch, shows onboarding screens before the main app.
 *
 * @param connectivityMonitor Monitor for network connectivity state
 * @param sessionActivityState State tracking session activity for tab bar visibility
 * @param onboardingPreferences Preferences to track onboarding completion state
 * @param accessibilityChecker Checker for accessibility preferences like reduce motion
 * @param initialDeepLink Optional deep link destination to navigate to on launch
 * @param onDeepLinkConsumed Callback when the deep link has been handled
 */
@Composable
fun UnaMentisNavHost(
    connectivityMonitor: ConnectivityMonitor,
    sessionActivityState: SessionActivityState,
    onboardingPreferences: OnboardingPreferences,
    accessibilityChecker: AccessibilityChecker,
    initialDeepLink: DeepLinkDestination? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val scrollToTopHandler = remember { ScrollToTopHandler() }
    val shouldHideTabBar by sessionActivityState.shouldHideTabBar.collectAsState()

    // Determine start destination based on onboarding completion
    val startDestination =
        remember {
            if (onboardingPreferences.hasCompletedOnboarding()) {
                Screen.Session.route
            } else {
                Routes.ONBOARDING
            }
        }

    // All 6 tabs shown in the bottom navigation (matching iOS)
    val allTabs =
        listOf(
            Screen.Session,
            Screen.Curriculum,
            Screen.Todo,
            Screen.History,
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

    // Track current route to hide tab bar during onboarding
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val isOnOnboarding = navBackStackEntry?.destination?.route == Routes.ONBOARDING

    CompositionLocalProvider(LocalScrollToTopHandler provides scrollToTopHandler) {
        Scaffold(
            bottomBar = {
                // Tab bar hides during onboarding and active sessions (matching iOS behavior)
                AnimatedVisibility(
                    visible = !shouldHideTabBar && !isOnOnboarding,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                ) {
                    UnaMentisBottomBar(
                        navController = navController,
                        tabs = allTabs,
                        scrollToTopHandler = scrollToTopHandler,
                    )
                }
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
                    startDestination = startDestination,
                    modifier = Modifier.weight(1f),
                ) {
                    // Onboarding (shown on first launch)
                    composable(route = Routes.ONBOARDING) {
                        OnboardingScreen(
                            onComplete = {
                                onboardingPreferences.setOnboardingCompleted()
                                navController.navigate(Screen.Session.route) {
                                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                                }
                            },
                            reduceMotion = accessibilityChecker.shouldReduceMotion(),
                        )
                    }

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
                        CurriculumScreen(
                            onNavigateToSession = { curriculumId, topicId ->
                                val route =
                                    "session/start?curriculum_id=$curriculumId" +
                                        (topicId?.let { "&topic_id=$it" } ?: "")
                                navController.navigate(route)
                            },
                        )
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
                        CurriculumScreen(
                            initialCurriculumId = curriculumId,
                            onNavigateToSession = { navCurriculumId, topicId ->
                                val route =
                                    "session/start?curriculum_id=$navCurriculumId" +
                                        (topicId?.let { "&topic_id=$it" } ?: "")
                                navController.navigate(route)
                            },
                        )
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
                        SettingsScreen(
                            initialSection = section,
                            onNavigateToServerSettings = {
                                navController.navigate(Routes.SERVER_SETTINGS)
                            },
                            onNavigateToAbout = {
                                navController.navigate(Routes.ABOUT)
                            },
                            onNavigateToDebug = {
                                navController.navigate(Routes.DEBUG)
                            },
                        )
                    }

                    // Server Settings (sub-screen of Settings)
                    composable(route = Routes.SERVER_SETTINGS) {
                        ServerSettingsScreen(
                            onNavigateBack = { navController.popBackStack() },
                        )
                    }

                    // About (sub-screen of Settings)
                    composable(route = Routes.ABOUT) {
                        AboutScreen(
                            onNavigateBack = { navController.popBackStack() },
                        )
                    }

                    // Debug Tools (sub-screen of Settings, debug builds only)
                    composable(route = Routes.DEBUG) {
                        DebugScreen(
                            onNavigateBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bottom navigation bar with all 6 tabs (matching iOS layout).
 * Triggers scroll-to-top when tapping the currently selected tab.
 */
@Composable
private fun UnaMentisBottomBar(
    navController: NavHostController,
    tabs: List<Screen>,
    scrollToTopHandler: ScrollToTopHandler,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        tabs.forEach { screen ->
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
    }
}
