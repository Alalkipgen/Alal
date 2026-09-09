package com.alal.notes.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.notes.data.dao.TagCount
import com.alal.notes.data.entity.Category
import com.alal.notes.data.entity.Note
import com.alal.notes.data.prefs.UserPreferences
import com.alal.notes.data.repository.NoteRepository
import com.alal.notes.domain.model.NoteStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val status: NoteStatus? = null,
    val categoryId: Long? = null,
    val results: List<Note> = emptyList(),
    val searching: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val status = MutableStateFlow<NoteStatus?>(null)
    private val categoryId = MutableStateFlow<Long?>(null)
    private val searching = MutableStateFlow(false)

    val tagCounts: StateFlow<List<TagCount>> = repository.observeTagCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<Category>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rawResults = combine(query.debounce(200).distinctUntilChanged(), repository.observeActive()) { q, _ -> q }
        .mapLatest { q ->
            if (q.isBlank()) return@mapLatest emptyList()
            searching.value = true
            val tag = q.trim().takeIf { it.startsWith("#") }?.removePrefix("#")
            val res: List<Note> = if (!tag.isNullOrBlank()) {
                val id = repository.observeTagCounts().first().firstOrNull { it.name.equals(tag, ignoreCase = true) }?.id
                if (id != null) repository.observeByTag(id).first() else repository.search(tag)
            } else {
                repository.search(q)
            }
            searching.value = false
            res
        }

    val state: StateFlow<SearchUiState> = combine(query, status, categoryId, rawResults, searching) { q, s, c, r, busy ->
        SearchUiState(
            query = q, status = s, categoryId = c, searching = busy,
            results = r.filter { n -> (s == null || n.status == s) && (c == null || n.categoryId == c) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(q: String) { query.value = q }
    fun setStatus(s: NoteStatus?) { status.value = s }
    fun setCategory(id: Long?) { categoryId.value = id }
    fun commitSearch() = viewModelScope.launch { val q = query.value.trim(); if (q.isNotEmpty()) prefs.addRecentSearch(q) }
    fun clearRecent() = viewModelScope.launch { prefs.clearRecentSearches() }
}
