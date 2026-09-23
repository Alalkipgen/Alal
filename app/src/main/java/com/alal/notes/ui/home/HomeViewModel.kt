package com.alal.notes.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.notes.data.entity.Category
import com.alal.notes.data.entity.Note
import com.alal.notes.data.entity.Template
import com.alal.notes.data.prefs.UserPreferences
import com.alal.notes.data.repository.NoteRepository
import com.alal.notes.domain.model.AppThemeKey
import com.alal.notes.domain.model.NoteStatus
import com.alal.notes.domain.model.SortMode
import com.alal.notes.domain.model.ViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val pinned: List<Note> = emptyList(),
    val others: List<Note> = emptyList(),
    val categories: List<Category> = emptyList(),
    val categoryFilter: Long = -1L,
    val selection: Set<Long> = emptySet(),
    val templates: List<Template> = emptyList(),
    val loaded: Boolean = false,
) {
    val selecting: Boolean get() = selection.isNotEmpty()
    val isEmpty: Boolean get() = loaded && pinned.isEmpty() && others.isEmpty() && categoryFilter == -1L
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val categoryFilter = MutableStateFlow(-1L)
    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<HomeUiState> = combine(
        repository.observeActive().onEach(repository::warmOpenCache),
        repository.observeCategories(),
        categoryFilter,
        selection,
        combine(prefs.settings, repository.observeTemplates()) { s, t -> s.sortMode to t },
    ) { notes, categories, filter, sel, (sort, templates) ->
        val filtered = if (filter == -1L) notes else notes.filter { it.categoryId == filter }
        val sorted = when (sort) {
            SortMode.MODIFIED -> filtered.sortedByDescending { it.updatedAt }
            SortMode.CREATED -> filtered.sortedByDescending { it.createdAt }
            SortMode.TITLE -> filtered.sortedBy { it.title.ifBlank { it.body }.lowercase() }
            SortMode.WORDS -> filtered.sortedByDescending { it.wordCount }
        }
        HomeUiState(
            pinned = sorted.filter { it.isPinned },
            others = sorted.filter { !it.isPinned },
            categories = categories,
            categoryFilter = filter,
            selection = sel,
            templates = templates,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setCategoryFilter(id: Long) { categoryFilter.value = id }

    fun toggleSelect(id: Long) {
        selection.value = selection.value.toMutableSet().also { if (!it.add(id)) it.remove(id) }
    }
    fun clearSelection() { selection.value = emptySet() }
    fun selectAll() { selection.value = (state.value.pinned + state.value.others).map { it.id }.toSet() }

    fun setViewMode(mode: ViewMode) = viewModelScope.launch { prefs.setViewMode(mode) }
    fun setSortMode(mode: SortMode) = viewModelScope.launch { prefs.setSortMode(mode) }
    fun toggleDarkTheme(currentDark: Boolean) = viewModelScope.launch {
        prefs.setTheme(if (currentDark) AppThemeKey.MATERIAL else AppThemeKey.MIDNIGHT)
    }

    fun pin(ids: Collection<Long>, pinned: Boolean) = viewModelScope.launch { repository.setPinned(ids.toList(), pinned); clearSelection() }
    fun archive(ids: Collection<Long>) = viewModelScope.launch { repository.setArchived(ids.toList(), true); clearSelection() }
    fun trash(ids: Collection<Long>) = viewModelScope.launch { repository.trash(ids.toList()); clearSelection() }
    fun favorite(ids: Collection<Long>, fav: Boolean) = viewModelScope.launch { repository.setFavorite(ids.toList(), fav); clearSelection() }
    fun setStatus(ids: Collection<Long>, status: NoteStatus) = viewModelScope.launch { repository.setStatus(ids.toList(), status); clearSelection() }
    fun setCategory(ids: Collection<Long>, categoryId: Long?) = viewModelScope.launch { repository.setCategory(ids.toList(), categoryId); clearSelection() }
    fun duplicate(id: Long) = viewModelScope.launch { repository.duplicate(id); clearSelection() }

    fun undoArchive(ids: Collection<Long>) = viewModelScope.launch { repository.setArchived(ids.toList(), false) }
    fun undoTrash(ids: Collection<Long>) = viewModelScope.launch { repository.restore(ids.toList()) }

    suspend fun createNote(template: Template?): Long {
        val filter = categoryFilter.value.takeIf { it != -1L }
        return repository.createNote(template?.body ?: "", filter, prefs.settings.first().wordCountMethod)
    }

    fun addCategory(name: String, color: Int) = viewModelScope.launch { repository.addCategory(name, color) }
    fun updateCategory(category: Category) = viewModelScope.launch { repository.updateCategory(category) }
    fun deleteCategory(id: Long) = viewModelScope.launch {
        repository.deleteCategory(id)
        if (categoryFilter.value == id) categoryFilter.value = -1L
    }
}
