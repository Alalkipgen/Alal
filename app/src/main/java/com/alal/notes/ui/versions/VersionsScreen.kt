package com.alal.notes.ui.versions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.alal.notes.R
import com.alal.notes.data.entity.Note
import com.alal.notes.data.entity.NoteVersion
import com.alal.notes.data.prefs.UserPreferences
import com.alal.notes.data.repository.NoteRepository
import com.alal.notes.ui.home.EmptyState
import com.alal.notes.ui.more.SubScreenTopBar
import com.alal.notes.ui.navigation.Route
import com.alal.notes.ui.theme.Alal
import com.alal.notes.ui.util.Format
import com.alal.notes.ui.util.rememberIs24Hour
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VersionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NoteRepository,
    private val prefs: UserPreferences,
) : ViewModel() {
    val noteId: Long = savedStateHandle.toRoute<Route.Versions>().noteId

    val note: StateFlow<Note?> = repository.observeNote(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val versions: StateFlow<List<NoteVersion>> = repository.observeVersions(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message
    fun consumeMessage() { _message.value = null }

    fun snapshotNow() = viewModelScope.launch { repository.snapshotNow(noteId); _message.value = R.string.version_saved }
    fun delete(v: NoteVersion) = viewModelScope.launch { repository.deleteVersion(v.id) }
    fun clearAll() = viewModelScope.launch { repository.clearVersions(noteId); _message.value = R.string.versions_cleared }

    /** Snapshot the current text first, then write the old version back as the live note. */
    fun restore(v: NoteVersion) = viewModelScope.launch {
        val current = repository.getNote(noteId) ?: return@launch
        repository.snapshotNow(noteId)
        val method = prefs.settings.first().wordCountMethod
        repository.saveContent(current, v.title, v.body, method)
        _message.value = R.string.version_restored
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionsScreen(onBack: () -> Unit, vm: VersionsViewModel = hiltViewModel()) {
    val note by vm.note.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val is24h = rememberIs24Hour()
    val snackbar = remember { SnackbarHostState() }
    var preview by remember { mutableStateOf<NoteVersion?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(context.getString(it)); vm.consumeMessage() } }

    Scaffold(
        containerColor = cs.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            SubScreenTopBar(stringResource(R.string.version_history), onBack) {
                IconButton(onClick = { vm.snapshotNow() }) { Icon(Icons.Rounded.AddCircleOutline, stringResource(R.string.version_save_now)) }
                if (versions.isNotEmpty()) {
                    IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.versions_clear)) }
                }
            }
        },
    ) { padding ->
        if (versions.isEmpty()) {
            EmptyState(
                modifier = Modifier.fillMaxSize().padding(padding),
                title = stringResource(R.string.versions_empty_title),
                body = stringResource(R.string.versions_empty_body),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.versions_intro, NoteRepository.MAX_VERSIONS_PER_NOTE),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                items(versions, key = { it.id }) { v ->
                    VersionRow(v, currentWords = note?.wordCount ?: 0, is24h = is24h) { preview = v }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    preview?.let { v ->
        ModalBottomSheet(onDismissRequest = { preview = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, null, tint = cs.primary)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(Format.full(v.createdAt), style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    stringResource(R.string.meta_words, Format.number(v.wordCount)),
                    style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                )
                HorizontalDivider()
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        v.title.ifBlank { stringResource(R.string.untitled) },
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = Alal.extras.type.title),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(v.body, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp))
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.delete(v); preview = null }) { Text(stringResource(R.string.delete), color = cs.error) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { vm.restore(v); preview = null }) { Text(stringResource(R.string.version_restore)) }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.versions_clear)) },
            text = { Text(stringResource(R.string.versions_clear_confirm)) },
            confirmButton = { TextButton(onClick = { vm.clearAll(); confirmClear = false }) { Text(stringResource(R.string.delete), color = cs.error) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun VersionRow(v: NoteVersion, currentWords: Int, is24h: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val delta = v.wordCount - currentWords
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Format.relative(v.createdAt, is24h), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    when {
                        delta > 0 -> "+${Format.number(delta)}"
                        delta < 0 -> "\u2212${Format.number(-delta)}"
                        else -> "\u00B10"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when { delta > 0 -> cs.primary; delta < 0 -> cs.error; else -> cs.onSurfaceVariant },
                )
            }
            Text(
                v.title.ifBlank { stringResource(R.string.untitled) },
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.meta_words, Format.number(v.wordCount)) + "  ·  " + Format.full(v.createdAt),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
        }
    }
}
