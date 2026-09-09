package com.alal.notes.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.alal.notes.MainActivity
import com.alal.notes.R
import com.alal.notes.data.prefs.Settings
import com.alal.notes.ui.backup.BackupScreen
import com.alal.notes.ui.editor.EditorScreen
import com.alal.notes.ui.home.HomeScreen
import com.alal.notes.ui.lock.AppLockScreen
import com.alal.notes.ui.lock.LockGate
import com.alal.notes.ui.more.AboutScreen
import com.alal.notes.ui.more.MoreScreen
import com.alal.notes.ui.more.NoteListScreen
import com.alal.notes.ui.stats.StatsScreen
import com.alal.notes.ui.more.TagsScreen
import com.alal.notes.ui.more.TemplatesScreen
import com.alal.notes.ui.navigation.ListKind
import com.alal.notes.ui.navigation.Route
import com.alal.notes.ui.search.SearchScreen
import com.alal.notes.ui.settings.AppearanceScreen
import com.alal.notes.ui.settings.EditorSettingsScreen
import com.alal.notes.ui.settings.SettingsScreen
import com.alal.notes.ui.versions.VersionsScreen

private data class Tab(val route: Route, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
fun AlalRoot(settings: Settings, pendingAction: String?, onActionConsumed: () -> Unit) {
    // App lock wraps everything, including shortcut / notification deep links.
    LockGate(settings) {
        AlalShell(settings, pendingAction, onActionConsumed)
    }
}

@Composable
private fun AlalShell(settings: Settings, pendingAction: String?, onActionConsumed: () -> Unit) {
    val navController = rememberNavController()
    val mainVm: MainViewModel = hiltViewModel()

    LaunchedEffect(pendingAction) {
        val action = pendingAction ?: return@LaunchedEffect
        val openPrefix = "${MainActivity.ACTION_OPEN_NOTE}:"
        when {
            action == MainActivity.ACTION_NEW_NOTE -> {
                val id = mainVm.createNote()
                navController.navigate(Route.Editor(id))
                onActionConsumed()
            }
            action == MainActivity.ACTION_SEARCH -> {
                navController.navigate(Route.Search)
                onActionConsumed()
            }
            action.startsWith(openPrefix) -> {
                action.removePrefix(openPrefix).toLongOrNull()?.let { id -> navController.navigate(Route.Editor(id)) }
                onActionConsumed()
            }
        }
    }

    val tabs = listOf(
        Tab(Route.Home, R.string.tab_notes, Icons.Outlined.Description, Icons.Rounded.Description),
        Tab(Route.Stats, R.string.tab_stats, Icons.Outlined.Insights, Icons.Rounded.Insights),
        Tab(Route.More, R.string.tab_more, Icons.Outlined.MoreHoriz, Icons.Rounded.MoreHoriz),
    )
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = tabs.any { tab -> destination?.hasRoute(tab.route::class) == true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Screens handle their own system-bar insets; the root only reserves room for the tab bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically(tween(220)) { it } + fadeIn(),
                exit = slideOutVertically(tween(180)) { it } + fadeOut(),
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                    for (tab in tabs) {
                        val selected = destination?.hasRoute(tab.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Reserve space for the bottom tab bar so FABs / lists are never hidden behind it.
        // consumeWindowInsets prevents nested Scaffolds from adding the navigation-bar inset twice.
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            AlalNavHost(navController, settings)
        }
    }
}

@Composable
private fun AlalNavHost(navController: NavHostController, settings: Settings) {
    val openNote: (Long) -> Unit = { id -> navController.navigate(Route.Editor(id)) }
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(
        navController = navController,
        startDestination = Route.Home,
        enterTransition = { fadeIn(tween(200)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220)) },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(150)) + scaleOut(targetScale = 0.96f, animationSpec = tween(200)) },
    ) {
        composable<Route.Home> {
            HomeScreen(
                settings = settings,
                onOpenNote = openNote,
                onSearch = { navController.navigate(Route.Search) },
            )
        }
        composable<Route.Stats> { StatsScreen(settings = settings) }
        composable<Route.More> {
            MoreScreen(onNavigate = { route -> navController.navigate(route) })
        }
        composable<Route.Editor>(
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, tween(260)) + fadeIn(tween(200))
            },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, tween(220)) + fadeOut(tween(180))
            },
        ) { entry ->
            val route = entry.toRoute<Route.Editor>()
            EditorScreen(
                noteId = route.noteId,
                settings = settings,
                onBack = back,
                onVersions = { id -> navController.navigate(Route.Versions(id)) },
            )
        }
        composable<Route.Search> {
            SearchScreen(settings = settings, onOpenNote = openNote, onBack = back)
        }
        composable<Route.NoteList> { entry ->
            val route = entry.toRoute<Route.NoteList>()
            NoteListScreen(kind = route.kind, tagId = route.tagId, settings = settings, onOpenNote = openNote, onBack = back)
        }
        composable<Route.Tags> {
            TagsScreen(onOpenTag = { id -> navController.navigate(Route.NoteList(ListKind.TAG, id)) }, onBack = back)
        }
        composable<Route.Templates> {
            TemplatesScreen(onBack = back, onCreated = { id -> navController.navigate(Route.Editor(id)) })
        }
        composable<Route.Appearance> { AppearanceScreen(settings = settings, onBack = back) }
        composable<Route.EditorSettings> { EditorSettingsScreen(settings = settings, onBack = back) }
        composable<Route.Settings> { SettingsScreen(settings = settings, onBack = back) }
        composable<Route.About> { AboutScreen(onBack = back) }
        composable<Route.Backup> { BackupScreen(onBack = back) }
        composable<Route.AppLock> { AppLockScreen(onBack = back) }
        composable<Route.Versions> { VersionsScreen(onBack = back) }
    }
}
