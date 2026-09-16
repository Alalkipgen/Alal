package com.alal.notes.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.notes.data.prefs.Settings
import com.alal.notes.data.prefs.UserPreferences
import com.alal.notes.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val repository: NoteRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    fun applyLocale(tag: String) {
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val target = if (tag.isBlank()) "" else tag
        if (current != target) {
            AppCompatDelegate.setApplicationLocales(
                if (target.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(target),
            )
        }
    }

    /** Warms the selected note and reports whether navigation may safely start. */
    suspend fun prepareNote(noteId: Long): Boolean = repository.prepareForOpen(noteId) != null

    /** Creates an empty note (optionally from a template) and returns its id. */
    suspend fun createNote(templateBody: String = "", categoryId: Long? = null): Long =
        repository.createNote(templateBody, categoryId, settings.value.wordCountMethod)

    fun setViewModePersist(block: suspend UserPreferences.() -> Unit) {
        viewModelScope.launch { prefs.block() }
    }
}
