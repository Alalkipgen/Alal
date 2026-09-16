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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private data class Tab(val route: Route, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * Opening a note starts moving immediately. The old 400 ms "preparation" hold (added so the
 * editor's first empty frame never slid in before Room delivered the text) made every open feel
 * like a ~600 ms lag. The editor now fades its content in once the note is loaded instead
 * (see EditorScreen), so the surfaces can move right away and the total open time is just the
 * slide itself.
 */
private const val SLIDE_MS = 240

/**
 * Longest the UI waits for the tapped note to be read before it starts the slide anyway. It sits
 * well under the ~100 ms that still reads as "instant", so a cold database can never turn the
 * open into a visible stall.
 */
private const val OPEN_PREFETCH_CAP_MS = 120L

private fun openSlideSpec() = tween<IntOffset>(SLIDE_MS, easing = FastOutSlowInEasing)

private fun closeSlideSpec() = tween<IntOffset>(SLIDE_MS, easing = FastOutSlowInEasing)

private fun NavBackStackEntry?.isEditor(): Boolean =
    this?.destination?.hasRoute(Route.Editor::class) == true

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

    // Keep navigation outside the screen-measuring path. The old root Scaffold changed its
    // content height as soon as the tab bar disappeared, so the outgoing Home FAB first dropped
    // vertically and then slid left. Tabs now reserve their own fixed bar space; the complete
    // Home screen (including Write) therefore travels right-to-left as one stable surface.
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
    // Material 3's NavigationBar is 80dp tall; navigationBarsPadding adds the gesture/button
    // inset below it. Because this lives inside each tab destination it remains unchanged while
    // that destination exits, avoiding any vertical relayout during the horizontal transition.
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
    // Read the note first (normally a few milliseconds), then slide. The tapped card stays
    // pressed while that happens, so the wait is covered by touch feedback instead of an empty
    // editor frame that has to be filled in afterwards.
    val openNote: (Long) -> Unit = { id ->
        scope.launch {
            withTimeoutOrNull(OPEN_PREFETCH_CAP_MS) { mainVm.prefetch(id) }
            navController.navigate(Route.Editor(id))
        }
    }
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(
        navController = navController,
        startDestination = Route.Home,
        // Both complete surfaces move together as soon as the note is tapped; a short fade on the
        // incoming editor softens the first frame. Back navigation mirrors it.
        enterTransition = {
            if (targetState.isEditor()) slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, openSlideSpec()) + fadeIn(tween(SLIDE_MS / 2))
            else fadeIn(tween(150))
        },
        exitTransition = {
            if (targetState.isEditor()) slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, openSlideSpec())
            else fadeOut(tween(110))
        },
        popEnterTransition = {
            if (initialState.isEditor()) slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, closeSlideSpec())
            else fadeIn(tween(150))
        },
        popExitTransition = {
            if (initialState.isEditor()) slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, closeSlideSpec())
            else fadeOut(tween(110))
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
