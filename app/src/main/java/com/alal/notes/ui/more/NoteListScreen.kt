package com.alal.notes.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alal.notes.R
import com.alal.notes.data.entity.Category
import com.alal.notes.data.entity.Note
import com.alal.notes.data.prefs.Settings
import com.alal.notes.data.repository.NoteRepository
import com.alal.notes.domain.model.ViewMode
import com.alal.notes.ui.components.PillChip
import com.alal.notes.ui.home.EmptyState
import com.alal.notes.ui.home.NoteCard
import com.alal.notes.ui.navigation.ListKind
import com.alal.notes.ui.theme.ActionColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteListViewModel @Inject constructor(
    private val repository: NoteRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    val kind: String = savedState.get<String>("kind") ?: ListKind.PINNED
    val tagId: Long = savedState.get<Long>("tagId") ?: 0L

    val notes: StateFlow<List<Note>> = when (kind) {
        ListKind.PINNED -> repository.observePinned()
        ListKind.FAVORITES -> repository.observeFavorites()
        ListKind.ARCHIVE -> repository.observeArchived()
        ListKind.TRASH -> repository.observeTrashed()
        else -> repository.observeByTag(tagId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tagName: StateFlow<String> = repository.observeTags()
        .map { tags -> tags.firstOrNull { it.id == tagId }?.name ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val categories: StateFlow<List<Category>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(id: Long) = viewModelScope.launch { repository.restore(listOf(id)) }
    fun deleteForever(id: Long) = viewModelScope.launch { repository.deleteForever(listOf(id)) }
    fun emptyTrash() = viewModelScope.launch { repository.emptyTrash() }
    fun unarchive(id: Long) = viewModelScope.launch { repository.setArchived(listOf(id), false) }
    fun unpin(id: Long) = viewModelScope.launch { repository.setPinned(listOf(id), false) }
    fun unfavorite(id: Long) = viewModelScope.launch { repository.setFavorite(listOf(id), false) }
}

@Composable
fun NoteListScreen(
    kind: String,
    tagId: Long,
    settings: Settings,
    onOpenNote: (Long) -> Unit,
    onBack: () -> Unit,
    vm: NoteListViewModel = hiltViewModel(),
) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val tagName by vm.tagName.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Long?>(null) }

    val title = when (kind) {
        ListKind.PINNED -> stringResource(R.string.pinned)
        ListKind.FAVORITES -> stringResource(R.string.favorites)
        ListKind.ARCHIVE -> stringResource(R.string.archive)
        ListKind.TRASH -> stringResource(R.string.trash)
        else -> "#$tagName"
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            SubScreenTopBar(title, onBack) {
                if (kind == ListKind.TRASH && notes.isNotEmpty()) {
                    IconButton(onClick = { confirmEmpty = true }) { Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.empty_trash), tint = ActionColors.trash) }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (kind == ListKind.TRASH) {
                Text(
                    stringResource(R.string.trash_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            if (notes.isEmpty()) {
                EmptyState(Modifier.fillMaxSize(), title = stringResource(R.string.empty_list), body = "")
            } else {
                val byId = remember(categories) { categories.associateBy { it.id } }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(notes, key = { it.id }) { note ->
                        Column {
                            NoteCard(
                                note = note,
                                category = note.categoryId?.let { byId[it] },
                                settings = settings,
                                viewMode = ViewMode.LIST,
                                selected = false,
                                selecting = false,
                                onClick = { if (kind != ListKind.TRASH) onOpenNote(note.id) },
                                onLongClick = { },
                            )
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                when (kind) {
                                    ListKind.TRASH -> {
                                        TextButton(onClick = { vm.restore(note.id) }) { Icon(Icons.Rounded.Restore, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.restore)) }
                                        TextButton(onClick = { confirmDelete = note.id }) { Icon(Icons.Rounded.DeleteForever, null, tint = ActionColors.trash); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.delete_forever), color = ActionColors.trash) }
                                    }
                                    ListKind.ARCHIVE -> TextButton(onClick = { vm.unarchive(note.id) }) { Icon(Icons.Rounded.Unarchive, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.unarchive)) }
                                    ListKind.PINNED -> PillChip(stringResource(R.string.unpin), selected = false, onClick = { vm.unpin(note.id) })
                                    ListKind.FAVORITES -> PillChip(stringResource(R.string.unfavorite), selected = false, onClick = { vm.unfavorite(note.id) })
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.empty_trash)) },
            text = { Text(stringResource(R.string.empty_trash_confirm)) },
            confirmButton = { TextButton(onClick = { vm.emptyTrash(); confirmEmpty = false }) { Text(stringResource(R.string.empty_trash), color = ActionColors.trash) } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_forever)) },
            text = { Text(stringResource(R.string.delete_forever_confirm)) },
            confirmButton = { TextButton(onClick = { vm.deleteForever(id); confirmDelete = null }) { Text(stringResource(R.string.delete_forever), color = ActionColors.trash) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
