package com.alal.notes.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alal.notes.R
import com.alal.notes.data.entity.Category
import com.alal.notes.data.entity.Note
import com.alal.notes.data.prefs.Settings
import com.alal.notes.domain.model.NoteStatus
import com.alal.notes.domain.model.SortMode
import com.alal.notes.domain.model.ViewMode
import com.alal.notes.ui.components.ColorSwatch
import com.alal.notes.ui.components.PillChip
import com.alal.notes.ui.components.WritingPenAnimation
import com.alal.notes.ui.components.label
import com.alal.notes.ui.theme.Accents
import com.alal.notes.ui.theme.ActionColors
import com.alal.notes.ui.theme.Alal
import com.alal.notes.ui.util.rememberHaptics
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    settings: Settings,
    onOpenNote: (Long) -> Unit,
    onSearch: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberHaptics()
    val dark = Alal.extras.dark
    val cs = MaterialTheme.colorScheme

    var showTemplates by rememberSaveable { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var categoryDialog by remember { mutableStateOf<Category?>(null) }
    var showAddCategory by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }

    val gridState = rememberLazyStaggeredGridState()
    val listState = rememberLazyListState()
    val fabExpanded by remember(settings.viewMode) {
        derivedStateOf {
            if (settings.viewMode == ViewMode.GRID) !gridState.canScrollBackward else !listState.canScrollBackward
        }
    }

    BackHandler(enabled = state.selecting) { vm.clearSelection() }

    val archivedMsg = stringResource(R.string.snackbar_archived)
    val trashedMsg = stringResource(R.string.snackbar_trashed)
    val pinnedMsg = stringResource(R.string.snackbar_pinned)
    val unpinnedMsg = stringResource(R.string.snackbar_unpinned)
    val undoLabel = stringResource(R.string.undo)

    fun archiveWithUndo(ids: Set<Long>) {
        vm.archive(ids)
        scope.launch {
            val r = snackbar.showSnackbar(archivedMsg, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.undoArchive(ids)
        }
    }
    fun trashWithUndo(ids: Set<Long>) {
        vm.trash(ids)
        scope.launch {
            val r = snackbar.showSnackbar(trashedMsg, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.undoTrash(ids)
        }
    }
    fun togglePin(note: Note) {
        vm.pin(listOf(note.id), !note.isPinned)
        scope.launch { snackbar.showSnackbar(if (note.isPinned) unpinnedMsg else pinnedMsg, duration = SnackbarDuration.Short) }
    }

    Scaffold(
        containerColor = cs.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (state.selecting) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, state.selection.size)) },
                    navigationIcon = {
                        IconButton(onClick = { vm.clearSelection() }) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
                    },
                    actions = {
                        val allPinned = (state.pinned + state.others).filter { it.id in state.selection }.all { it.isPinned }
                        IconButton(onClick = { vm.pin(state.selection, !allPinned) }) {
                            Icon(Icons.Rounded.PushPin, stringResource(if (allPinned) R.string.unpin else R.string.pin), tint = ActionColors.pin)
                        }
                        IconButton(onClick = { archiveWithUndo(state.selection) }) {
                            Icon(Icons.Rounded.Archive, stringResource(R.string.archive), tint = ActionColors.archive)
                        }
                        IconButton(onClick = { trashWithUndo(state.selection) }) {
                            Icon(Icons.Rounded.Delete, stringResource(R.string.trash), tint = ActionColors.trash)
                        }
                        IconButton(onClick = { vm.selectAll() }) { Icon(Icons.Rounded.SelectAll, stringResource(R.string.all)) }
                        Box {
                            IconButton(onClick = { statusMenu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
                            DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                                Text(stringResource(R.string.change_status), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                for (s in NoteStatus.entries) {
                                    DropdownMenuItem(text = { Text(s.label()) }, onClick = { statusMenu = false; vm.setStatus(state.selection, s) })
                                }
                                Text(stringResource(R.string.move_to), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                DropdownMenuItem(text = { Text(stringResource(R.string.no_category)) }, onClick = { statusMenu = false; vm.setCategory(state.selection, null) })
                                for (c in state.categories) {
                                    DropdownMenuItem(text = { Text(c.name) }, onClick = { statusMenu = false; vm.setCategory(state.selection, c.id) })
                                }
                                if (state.selection.size == 1) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, onClick = { statusMenu = false; vm.duplicate(state.selection.first()) })
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surfaceContainer),
                )
            } else {
                TopAppBar(
                    title = { HomeSearchField(onSearch) },
                    actions = {
                        FilledTonalIconButton(
                            onClick = { haptics.tick(); vm.toggleDarkTheme(dark) },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = cs.surfaceContainerHigh,
                                contentColor = cs.onSurfaceVariant,
                            ),
                        ) {
                            Icon(if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, stringResource(R.string.theme_toggle))
                        }
                        Spacer(Modifier.width(8.dp))
                        Box {
                            FilledTonalIconButton(
                                onClick = { overflow = true },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = cs.surfaceContainerHigh,
                                    contentColor = cs.onSurfaceVariant,
                                ),
                            ) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
                            DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                                Text(stringResource(R.string.view_mode), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.view_grid)) },
                                    leadingIcon = { Icon(Icons.Rounded.GridView, null) },
                                    onClick = { overflow = false; vm.setViewMode(ViewMode.GRID) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.view_list)) },
                                    leadingIcon = { Icon(Icons.Rounded.ViewAgenda, null) },
                                    onClick = { overflow = false; vm.setViewMode(ViewMode.LIST) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.view_compact)) },
                                    leadingIcon = { Icon(Icons.Rounded.ViewList, null) },
                                    onClick = { overflow = false; vm.setViewMode(ViewMode.COMPACT) },
                                )
                                Text(stringResource(R.string.sort), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                val sortLabels = listOf(
                                    SortMode.MODIFIED to R.string.sort_modified,
                                    SortMode.CREATED to R.string.sort_created,
                                    SortMode.TITLE to R.string.sort_title,
                                    SortMode.WORDS to R.string.sort_words,
                                )
                                for ((mode, label) in sortLabels) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(label)) },
                                        leadingIcon = { if (settings.sortMode == mode) Icon(Icons.Rounded.Sort, null) else Spacer(Modifier.width(24.dp)) },
                                        onClick = { overflow = false; vm.setSortMode(mode) },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
                )
            }
        },
        floatingActionButton = {
            if (!state.selecting) {
                ExtendedFloatingActionButton(
                    onClick = { haptics.tick(); showTemplates = true },
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.new_note)) },
                    containerColor = cs.primary,
                    contentColor = cs.onPrimary,
                    shape = RoundedCornerShape(18.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp, pressedElevation = 6.dp),
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            CategoryChips(
                categories = state.categories,
                selected = state.categoryFilter,
                onSelect = { haptics.tick(); vm.setCategoryFilter(it) },
                onLongPress = { categoryDialog = it },
                onAdd = { showAddCategory = true },
            )
            if (state.isEmpty) {
                EmptyState(Modifier.fillMaxSize(), onWrite = { haptics.tick(); showTemplates = true })
            } else {
                NoteCollection(
                    state = state,
                    settings = settings,
                    gridState = gridState,
                    listState = listState,
                    onOpen = { note -> if (state.selecting) vm.toggleSelect(note.id) else onOpenNote(note.id) },
                    onLongPress = { note -> haptics.confirm(); vm.toggleSelect(note.id) },
                )
            }
        }
    }

    if (showTemplates) {
        TemplatePickerSheet(
            templates = state.templates,
            onDismiss = { showTemplates = false },
            onPick = { template ->
                showTemplates = false
                scope.launch { onOpenNote(vm.createNote(template)) }
            },
        )
    }

    if (showAddCategory) {
        CategoryDialog(
            initial = null,
            onDismiss = { showAddCategory = false },
            onSave = { name, color -> vm.addCategory(name, color); showAddCategory = false },
            onDelete = null,
        )
    }
    categoryDialog?.let { cat ->
        CategoryDialog(
            initial = cat,
            onDismiss = { categoryDialog = null },
            onSave = { name, color -> vm.updateCategory(cat.copy(name = name, color = color)); categoryDialog = null },
            onDelete = { vm.deleteCategory(cat.id); categoryDialog = null },
        )
    }
}

/** Filled pill search field. It lives in the app bar row so the note grid keeps its height. */
@Composable
private fun HomeSearchField(onSearch: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(CircleShape)
            .background(cs.surfaceContainerHigh)
            .clickable(onClick = onSearch)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.search_hint),
            color = cs.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CategoryChips(
    categories: List<Category>,
    selected: Long,
    onSelect: (Long) -> Unit,
    onLongPress: (Category) -> Unit,
    onAdd: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PillChip(stringResource(R.string.all), selected == -1L, onClick = { onSelect(-1L) }) }
        items(categories, key = { it.id }) { c ->
            Box {
                PillChip(
                    c.name, selected == c.id,
                    onClick = { if (selected == c.id) onLongPress(c) else onSelect(c.id) },
                    dotColor = Color(c.color),
                )
            }
        }
        item {
            IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, stringResource(R.string.add_category), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun NoteCollection(
    state: HomeUiState,
    settings: Settings,
    gridState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpen: (Note) -> Unit,
    onLongPress: (Note) -> Unit,
) {
    val categoriesById = remember(state.categories) { state.categories.associateBy { it.id } }
    val bottomPad = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)

    @Composable
    fun card(note: Note) {
        NoteCard(
            note = note,
            category = note.categoryId?.let { categoriesById[it] },
            settings = settings,
            viewMode = settings.viewMode,
            selected = note.id in state.selection,
            selecting = state.selecting,
            onClick = { onOpen(note) },
            onLongClick = { onLongPress(note) },
        )
    }

    @Composable
    fun sectionHeader(text: String) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
        )
    }

    val showSections = state.pinned.isNotEmpty() && state.others.isNotEmpty()

    if (settings.viewMode == ViewMode.GRID) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            state = gridState,
            contentPadding = bottomPad,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (showSections) item(span = StaggeredGridItemSpan.FullLine) { sectionHeader(stringResource(R.string.pinned)) }
            items(state.pinned, key = { "p${it.id}" }) { card(it) }
            if (showSections) item(span = StaggeredGridItemSpan.FullLine) { sectionHeader(stringResource(R.string.recent)) }
            items(state.others, key = { "n${it.id}" }) { card(it) }
        }
    } else {
        LazyColumn(
            state = listState,
            contentPadding = bottomPad,
            verticalArrangement = Arrangement.spacedBy(if (settings.viewMode == ViewMode.COMPACT) 8.dp else 12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (showSections) item { sectionHeader(stringResource(R.string.pinned)) }
            items(state.pinned, key = { "p${it.id}" }) { card(it) }
            if (showSections) item { sectionHeader(stringResource(R.string.recent)) }
            items(state.others, key = { "n${it.id}" }) { card(it) }
        }
    }
}

@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.empty_title),
    body: String = stringResource(R.string.empty_body),
    onWrite: (() -> Unit)? = null,
) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        WritingPenAnimation()
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (onWrite != null) {
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onWrite) {
                Icon(Icons.Rounded.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.write))
            }
        }
    }
}

@Composable
fun CategoryDialog(
    initial: Category?,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var color by rememberSaveable { mutableStateOf(initial?.color ?: Accents.presets.first().color.toArgbInt()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_category else R.string.edit_category)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.category_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.color), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in Accents.presets) {
                        val argb = p.color.toArgbInt()
                        ColorSwatch(p.color, selected = color == argb, onClick = { color = argb }, size = 32)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), color) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

fun Color.toArgbInt(): Int = toArgb()
