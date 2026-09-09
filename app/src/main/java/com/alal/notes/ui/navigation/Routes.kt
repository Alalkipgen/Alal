package com.alal.notes.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose destinations. */
sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data object Stats : Route
    @Serializable data object More : Route
    @Serializable data class Editor(val noteId: Long, val templateId: Long = 0L, val focus: Boolean = false) : Route
    @Serializable data object Search : Route
    @Serializable data class NoteList(val kind: String, val tagId: Long = 0L) : Route
    @Serializable data object Tags : Route
    @Serializable data object Templates : Route
    @Serializable data object Appearance : Route
    @Serializable data object EditorSettings : Route
    @Serializable data object Settings : Route
    @Serializable data object About : Route
    // Phase 2
    @Serializable data object Backup : Route
    @Serializable data object AppLock : Route
    @Serializable data class Versions(val noteId: Long) : Route
}

object ListKind {
    const val PINNED = "pinned"
    const val FAVORITES = "favorites"
    const val ARCHIVE = "archive"
    const val TRASH = "trash"
    const val TAG = "tag"
}
