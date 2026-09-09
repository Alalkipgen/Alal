package com.alal.notes.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alal.notes.R
import com.alal.notes.ui.navigation.ListKind
import com.alal.notes.ui.navigation.Route
import com.alal.notes.ui.theme.ActionColors

@Composable
fun MoreScreen(onNavigate: (Route) -> Unit) {
    val cs = MaterialTheme.colorScheme

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.more), style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            MoreItem(Icons.Rounded.PushPin, R.string.pinned, ActionColors.pin) { onNavigate(Route.NoteList(ListKind.PINNED)) }
            MoreItem(Icons.Rounded.Star, R.string.favorites, ActionColors.pin) { onNavigate(Route.NoteList(ListKind.FAVORITES)) }
            MoreItem(Icons.Rounded.Label, R.string.tags, cs.primary) { onNavigate(Route.Tags) }
            MoreItem(Icons.Rounded.Archive, R.string.archive, ActionColors.archive) { onNavigate(Route.NoteList(ListKind.ARCHIVE)) }
            MoreItem(Icons.Rounded.Delete, R.string.trash, ActionColors.trash) { onNavigate(Route.NoteList(ListKind.TRASH)) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            MoreItem(Icons.Rounded.Backup, R.string.backup_restore, cs.onSurfaceVariant) { onNavigate(Route.Backup) }
            MoreItem(Icons.Rounded.Lock, R.string.app_lock, ActionColors.lock) { onNavigate(Route.AppLock) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            MoreItem(Icons.Rounded.Palette, R.string.appearance, cs.primary) { onNavigate(Route.Appearance) }
            MoreItem(Icons.Rounded.EditNote, R.string.editor_settings, cs.primary) { onNavigate(Route.EditorSettings) }
            MoreItem(Icons.Rounded.Dashboard, R.string.templates, cs.primary) { onNavigate(Route.Templates) }
            MoreItem(Icons.Outlined.Settings, R.string.settings, cs.onSurfaceVariant) { onNavigate(Route.Settings) }
            MoreItem(Icons.Outlined.Info, R.string.about, cs.onSurfaceVariant) { onNavigate(Route.About) }
        }
    }
}

@Composable
private fun MoreItem(icon: ImageVector, label: Int, tint: Color, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        leadingContent = { Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

/** Shared simple top bar with a back arrow, used by all sub-screens. */
@Composable
fun SubScreenTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}
