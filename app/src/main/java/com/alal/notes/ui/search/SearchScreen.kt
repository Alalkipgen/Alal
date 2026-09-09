package com.alal.notes.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alal.notes.R
import com.alal.notes.data.entity.Note
import com.alal.notes.data.prefs.Settings
import com.alal.notes.domain.markdown.AutoTitle
import com.alal.notes.domain.markdown.MarkdownStripper
import com.alal.notes.domain.model.NoteStatus
import com.alal.notes.ui.components.PillChip
import com.alal.notes.ui.components.StatusChip
import com.alal.notes.ui.components.label
import com.alal.notes.ui.home.EmptyState
import com.alal.notes.ui.util.Format
import com.alal.notes.ui.util.rememberIs24Hour
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    settings: Settings,
    onOpenNote: (Long) -> Unit,
    onBack: () -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val tagCounts by vm.tagCounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { focus.requestFocus() } }

    Scaffold(containerColor = cs.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = state.query,
                        onQueryChange = vm::setQuery,
                        onSearch = { vm.commitSearch() },
                        expanded = false,
                        onExpandedChange = { },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                        },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear)) }
                            } else {
                                Icon(Icons.Rounded.Search, null)
                            }
                        },
                        modifier = Modifier.focusRequester(focus),
                    )
                },
                expanded = false,
                onExpandedChange = { },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) { }

            // Filters
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { PillChip(stringResource(R.string.all), state.status == null && state.categoryId == null, onClick = { vm.setStatus(null); vm.setCategory(null) }) }
                items(categories, key = { "c${it.id}" }) { c ->
                    PillChip(c.name, state.categoryId == c.id, onClick = { vm.setCategory(if (state.categoryId == c.id) null else c.id) }, dotColor = androidx.compose.ui.graphics.Color(c.color))
                }
                items(NoteStatus.entries, key = { "s${it.name}" }) { s ->
                    PillChip(s.label(), state.status == s, onClick = { vm.setStatus(if (state.status == s) null else s) })
                }
            }
            Spacer(Modifier.height(8.dp))

            if (state.query.isBlank()) {
                // Recent searches + tag cloud
                if (settings.recentSearches.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.recent_searches), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::clearRecent) { Text(stringResource(R.string.clear)) }
                    }
                    for (q in settings.recentSearches.take(8)) {
                        ListItem(
                            headlineContent = { Text(q) },
                            leadingContent = { Icon(Icons.Rounded.History, null, tint = cs.onSurfaceVariant) },
                            modifier = Modifier.clickable { vm.setQuery(q); vm.commitSearch() },
                        )
                    }
                }
                if (tagCounts.isNotEmpty()) {
                    Text(stringResource(R.string.tags), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (t in tagCounts) {
                            PillChip("#${t.name} · ${t.noteCount}", selected = false, onClick = { vm.setQuery("#${t.name}") })
                        }
                    }
                }
            } else if (state.results.isEmpty() && !state.searching) {
                EmptyState(Modifier.fillMaxSize(), title = stringResource(R.string.no_results), body = "")
            } else {
                val is24 = rememberIs24Hour()
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.results, key = { it.id }) { note ->
                        SearchResultRow(note, state.query, is24, settings) { onOpenNote(note.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(note: Note, query: String, is24: Boolean, settings: Settings, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val title = remember(note) { AutoTitle.from(note.title, note.body) }
    // Locked notes still match, but their body is never shown in results.
    val snippet = remember(note, query) { if (note.isLocked) "" else snippetFor(MarkdownStripper.strip(note.body), query) }
    Surface(shape = MaterialTheme.shapes.medium, color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(note.status, compact = true)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.meta_words, Format.number(note.wordCount)) + " · " + Format.relative(note.updatedAt, is24),
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(highlight(title.ifBlank { stringResource(R.string.untitled) }, query, cs.primary), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (snippet.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(highlight(snippet, query, cs.primary), fontSize = settings.cardPreviewSize.sp, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun snippetFor(text: String, query: String, radius: Int = 60): String {
    val q = query.trim().removePrefix("#")
    if (q.isEmpty()) return text.take(radius * 2)
    val idx = text.indexOf(q, ignoreCase = true)
    if (idx < 0) return text.take(radius * 2)
    val start = (idx - radius).coerceAtLeast(0)
    val end = (idx + q.length + radius).coerceAtMost(text.length)
    return (if (start > 0) "…" else "") + text.substring(start, end).replace('\n', ' ') + (if (end < text.length) "…" else "")
}

private fun highlight(text: String, query: String, color: androidx.compose.ui.graphics.Color) = buildAnnotatedString {
    val q = query.trim().removePrefix("#")
    if (q.isEmpty()) { append(text); return@buildAnnotatedString }
    var i = 0
    while (i < text.length) {
        val idx = text.indexOf(q, i, ignoreCase = true)
        if (idx < 0) { append(text.substring(i)); break }
        append(text.substring(i, idx))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color, background = color.copy(alpha = 0.15f))) { append(text.substring(idx, idx + q.length)) }
        i = idx + q.length
    }
}
