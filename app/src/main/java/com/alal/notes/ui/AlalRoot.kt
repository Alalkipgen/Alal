package com.alal.notes.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
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
import com.alal.notes.ui.more.TagsScreen
import com.alal.notes.ui.more.TemplatesScreen
import com.alal.notes.ui.navigation.ListKind
import com.alal.notes.ui.navigation.Route
import com.alal.notes.ui.search.SearchScreen
import com.alal.notes.ui.settings.AppearanceScreen
import com.alal.notes.ui.settings.EditorSettingsScreen
import com.alal.notes.ui.settings.SettingsScreen
import com.alal.notes.ui.stats.StatsScreen
import com.alal.notes.ui.versions.VersionsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private data class Tab(val route: Route, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * Keep Home completely still while the already-composed editor gets its first text layout.
 * The editor is off-screen during this hold, so its empty/default frame cannot flash.
 */
private const val OPEN_RENDER_HOLD_MS = 250
private const val SLIDE_MS = 240
private const val OPEN_GATE_RELEASE_MARGIN_MS = 80

private fun openSlideSpec() = tween<IntOffset>(
    durationMillis = SLIDE_MS,
    delayMillis = OPEN_RENDER_HOLD_MS,
    easing = FastOutSlowInEasing,
)

private fun closeSlideSpec() = tween<IntOffset>(SLIDE_MS, easing = FastOutSlowInEasing)

private fun NavBackStackEntry?.isEditor(): Boolean =
    this?.destination?.hasRoute(Route.Editor::class) == true

@Composable
fun AlalRoot(settings: Settings, pendingAction: String?, onActionConsumed: () -> Unit) {
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
                if (mainVm.prepareNote(id)) {
                    navController.navigate(Route.Editor(id)) { launchSingleTop = true }
                }
                onActionConsumed()
            }
            action == MainActivity.ACTION_SEARCH -> {
                navController.navigate(Route.Search)
                onActionConsumed()
            }
            action.startsWith(openPrefix) -> {
                action.removePrefix(openPrefix).toLongOrNull()?.let { id ->
                    if (mainVm.prepareNote(id) && !navController.currentBackStackEntry.isEditor()) {
                        navController.navigate(Route.Editor(id)) { launchSingleTop = true }
                    }
                }
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

    Box(Modifier.fillMaxSize()) {
        AlalNavHost(navController, settings, mainVm)
        if (showBar) {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
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
    }
}

@Composable
private fun TabSurface(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(bottom = 80.dp),
    ) {
        content()
    }
}

@Composable
private fun AlalNavHost(navController: NavHostController, settings: Settings, mainVm: MainViewModel) {
    val scope = rememberCoroutineScope()
    // Home remains tappable during the render hold, so an atomic gate admits one request only.
    // launchSingleTop and the destination check provide a second line of defence.
    val openGate = remember { AtomicBoolean(false) }
    val openNote: (Long) -> Unit = openNote@{ id ->
        if (navController.currentBackStackEntry.isEditor()) return@openNote
        if (!openGate.compareAndSet(false, true)) return@openNote
        scope.launch {
            try {
                if (mainVm.prepareNote(id) && !navController.currentBackStackEntry.isEditor()) {
                    navController.navigate(Route.Editor(id)) { launchSingleTop = true }
                    delay((OPEN_RENDER_HOLD_MS + SLIDE_MS + OPEN_GATE_RELEASE_MARGIN_MS).toLong())
                }
            } finally {
                openGate.set(false)
            }
        }
    }
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(
        navController = navController,
        startDestination = Route.Home,
        // Compose/load the editor off-screen, keep Home fixed for 250 ms, then slide once.
        enterTransition = {
            if (targetState.isEditor()) {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, openSlideSpec())
            } else {
                fadeIn(tween(150))
            }
        },
        exitTransition = {
            if (targetState.isEditor()) {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, openSlideSpec())
            } else {
                fadeOut(tween(110))
            }
        },
        popEnterTransition = {
            if (initialState.isEditor()) {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, closeSlideSpec())
            } else {
                fadeIn(tween(150))
            }
        },
        popExitTransition = {
            if (initialState.isEditor()) {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, closeSlideSpec())
            } else {
                fadeOut(tween(110))
            }
        },
    ) {
        composable<Route.Home> {
            TabSurface {
                HomeScreen(
                    settings = settings,
                    onOpenNote = openNote,
                    onSearch = { navController.navigate(Route.Search) },
                )
            }
        }
        composable<Route.Stats> { TabSurface { StatsScreen(settings = settings) } }
        composable<Route.More> {
            TabSurface { MoreScreen(onNavigate = { route -> navController.navigate(route) }) }
        }
        composable<Route.Editor> { entry ->
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
            NoteListScreen(
                kind = route.kind,
                tagId = route.tagId,
                settings = settings,
                onOpenNote = openNote,
                onBack = back,
            )
        }
        composable<Route.Tags> {
            TagsScreen(
                onOpenTag = { id -> navController.navigate(Route.NoteList(ListKind.TAG, id)) },
                onBack = back,
            )
        }
        composable<Route.Templates> {
            TemplatesScreen(onBack = back, onCreated = openNote)
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
