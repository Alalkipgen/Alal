package com.alal.notes.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alal.notes.BuildConfig
import com.alal.notes.R
import com.alal.notes.data.dao.TagCount
import com.alal.notes.data.entity.Template
import com.alal.notes.data.prefs.UserPreferences
import com.alal.notes.data.repository.NoteRepository
import com.alal.notes.ui.home.EmptyState
import com.alal.notes.ui.home.TemplateRow
import com.alal.notes.ui.theme.ActionColors
import com.alal.notes.ui.theme.Alal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ------------------------------------------------------------------ Tags

@HiltViewModel
class TagsViewModel @Inject constructor(private val repository: NoteRepository) : ViewModel() {
    val tags: StateFlow<List<TagCount>> = repository.observeTagCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun delete(id: Long) = viewModelScope.launch { repository.deleteTag(id) }
}

@Composable
fun TagsScreen(onOpenTag: (Long) -> Unit, onBack: () -> Unit, vm: TagsViewModel = hiltViewModel()) {
    val tags by vm.tags.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    var confirm by remember { mutableStateOf<TagCount?>(null) }
    Scaffold(containerColor = cs.background, topBar = { SubScreenTopBar(stringResource(R.string.tags), onBack) }) { padding ->
        if (tags.isEmpty()) {
            EmptyState(Modifier.fillMaxSize().padding(padding), title = stringResource(R.string.empty_list), body = "")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(tags, key = { it.id }) { t ->
                    ListItem(
                        headlineContent = { Text("#${t.name}") },
                        supportingContent = { Text(stringResource(R.string.notes_count, t.noteCount)) },
                        leadingContent = { Icon(Icons.Rounded.Label, null, tint = cs.primary) },
                        trailingContent = { IconButton(onClick = { confirm = t }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = cs.onSurfaceVariant) } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onOpenTag(t.id) },
                    )
                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
    confirm?.let { t ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text("#${t.name}") },
            confirmButton = { TextButton(onClick = { vm.delete(t.id); confirm = null }) { Text(stringResource(R.string.delete), color = ActionColors.trash) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// ------------------------------------------------------------------ Templates

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val prefs: UserPreferences,
) : ViewModel() {
    val templates: StateFlow<List<Template>> = repository.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun createNoteFrom(t: Template): Long {
        val method = prefs.settings.first().wordCountMethod
        return repository.createNote(body = t.body, method = method)
    }
    fun save(t: Template) = viewModelScope.launch { repository.saveTemplate(t) }
    fun delete(t: Template) = viewModelScope.launch { if (!t.isBuiltIn) repository.deleteTemplate(t.id) }
}

@Composable
fun TemplatesScreen(onBack: () -> Unit, onCreated: (Long) -> Unit, vm: TemplatesViewModel = hiltViewModel()) {
    val templates by vm.templates.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var editing by remember { mutableStateOf<Template?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = cs.background,
        topBar = { SubScreenTopBar(stringResource(R.string.templates), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.custom)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(templates, key = { it.id }) { t ->
                val actions: (@Composable () -> Unit)? = if (t.isBuiltIn) null else {
                    {
                        Row {
                            IconButton(onClick = { editing = t }) { Icon(Icons.Rounded.Edit, stringResource(R.string.save), tint = cs.onSurfaceVariant) }
                            IconButton(onClick = { vm.delete(t) }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = ActionColors.trash) }
                        }
                    }
                }
                TemplateRow(t = t, onClick = { scope.launch { onCreated(vm.createNoteFrom(t)) } }, trailing = actions)
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (creating || editing != null) {
        val base = editing
        TemplateEditDialog(
            initial = base,
            onDismiss = { creating = false; editing = null },
            onSave = { name, body ->
                vm.save(base?.copy(name = name, body = body) ?: Template(key = "custom_${System.currentTimeMillis()}", name = name, icon = "edit", body = body, isBuiltIn = false, sortOrder = 100))
                creating = false; editing = null
            },
        )
    }
}

@Composable
private fun TemplateEditDialog(initial: Template?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var body by remember { mutableStateOf(initial?.body ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.templates)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.category_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = body, onValueChange = { body = it },
                    label = { Text(stringResource(R.string.body_hint)) },
                    minLines = 6, maxLines = 12,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), body) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ------------------------------------------------------------------ About

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Scaffold(containerColor = cs.background, topBar = { SubScreenTopBar(stringResource(R.string.about), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall.copy(fontFamily = Alal.extras.type.title), color = cs.onSurface)
            Text(stringResource(R.string.version) + " " + BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            HorizontalDivider()
            Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
            HorizontalDivider()
            Text(stringResource(R.string.licenses), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.licenses_body), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("Alal · Phase 1", style = MaterialTheme.typography.labelSmall, color = cs.outline)
            }
        }
    }
}
