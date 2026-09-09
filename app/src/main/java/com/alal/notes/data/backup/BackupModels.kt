package com.alal.notes.data.backup

import com.alal.notes.data.entity.Category
import com.alal.notes.data.entity.DailyStat
import com.alal.notes.data.entity.Note
import com.alal.notes.data.entity.NoteTagCrossRef
import com.alal.notes.data.entity.NoteVersion
import com.alal.notes.data.entity.Tag
import com.alal.notes.data.entity.Template
import com.alal.notes.domain.model.NoteStatus
import com.alal.notes.domain.model.PaperTexture
import com.alal.notes.domain.model.enumOrDefault
import kotlinx.serialization.Serializable

/**
 * On-disk backup format (plain JSON, UTF-8). Format 1 = Alal 1.1.0.
 * Everything is optional-with-defaults so older/newer files still parse.
 */
@Serializable
data class BackupFile(
    val format: Int = FORMAT,
    val app: String = "Alal",
    val versionName: String = "",
    val exportedAt: Long = 0L,
    val notes: List<NoteDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    val noteTags: List<NoteTagDto> = emptyList(),
    val templates: List<TemplateDto> = emptyList(),
    val versions: List<VersionDto> = emptyList(),
    val dailyStats: List<DailyStatDto> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
) {
    companion object {
        const val FORMAT = 1
    }
}

@Serializable
data class NoteDto(
    val id: Long,
    val title: String = "",
    val body: String = "",
    val categoryId: Long? = null,
    val status: String = NoteStatus.DRAFT.name,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null,
    val wordGoal: Int? = null,
    val backgroundColor: Int? = null,
    val backgroundGradient: Int? = null,
    val showBackgroundOnCard: Boolean = true,
    val paperTexture: String? = null,
    val fontSizeOverride: Int? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val wordCount: Int = 0,
    val charCount: Int = 0,
    val isLocked: Boolean = false,
    val reminderAt: Long? = null,
) {
    fun toEntity(id: Long = this.id, categoryId: Long? = this.categoryId): Note = Note(
        id = id,
        title = title,
        body = body,
        categoryId = categoryId,
        status = enumOrDefault(status, NoteStatus.DRAFT),
        isPinned = isPinned,
        isFavorite = isFavorite,
        isArchived = isArchived,
        isTrashed = isTrashed,
        trashedAt = trashedAt,
        wordGoal = wordGoal,
        backgroundColor = backgroundColor,
        backgroundGradient = backgroundGradient,
        showBackgroundOnCard = showBackgroundOnCard,
        paperTexture = paperTexture?.let { enumOrDefault(it, PaperTexture.DOTTED) },
        fontSizeOverride = fontSizeOverride,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        wordCount = wordCount,
        charCount = charCount,
        isLocked = isLocked,
        reminderAt = reminderAt,
    )

    companion object {
        fun from(n: Note) = NoteDto(
            id = n.id, title = n.title, body = n.body, categoryId = n.categoryId, status = n.status.name,
            isPinned = n.isPinned, isFavorite = n.isFavorite, isArchived = n.isArchived, isTrashed = n.isTrashed,
            trashedAt = n.trashedAt, wordGoal = n.wordGoal, backgroundColor = n.backgroundColor,
            backgroundGradient = n.backgroundGradient, showBackgroundOnCard = n.showBackgroundOnCard,
            paperTexture = n.paperTexture?.name, fontSizeOverride = n.fontSizeOverride, sortOrder = n.sortOrder,
            createdAt = n.createdAt, updatedAt = n.updatedAt, wordCount = n.wordCount, charCount = n.charCount,
            isLocked = n.isLocked, reminderAt = n.reminderAt,
        )
    }
}

@Serializable
data class CategoryDto(val id: Long, val name: String, val color: Int, val icon: String = "label", val sortOrder: Int = 0) {
    fun toEntity(id: Long = this.id) = Category(id = id, name = name, color = color, icon = icon, sortOrder = sortOrder)
    companion object {
        fun from(c: Category) = CategoryDto(c.id, c.name, c.color, c.icon, c.sortOrder)
    }
}

@Serializable
data class TagDto(val id: Long, val name: String) {
    fun toEntity(id: Long = this.id) = Tag(id = id, name = name)
    companion object {
        fun from(t: Tag) = TagDto(t.id, t.name)
    }
}

@Serializable
data class NoteTagDto(val noteId: Long, val tagId: Long) {
    companion object {
        fun from(r: NoteTagCrossRef) = NoteTagDto(r.noteId, r.tagId)
    }
}

@Serializable
data class TemplateDto(
    val id: Long,
    val key: String,
    val name: String,
    val icon: String = "description",
    val body: String = "",
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0,
) {
    fun toEntity(id: Long = this.id) = Template(id = id, key = key, name = name, icon = icon, body = body, isBuiltIn = isBuiltIn, sortOrder = sortOrder)
    companion object {
        fun from(t: Template) = TemplateDto(t.id, t.key, t.name, t.icon, t.body, t.isBuiltIn, t.sortOrder)
    }
}

@Serializable
data class VersionDto(val id: Long, val noteId: Long, val title: String, val body: String, val wordCount: Int, val createdAt: Long) {
    fun toEntity(id: Long = this.id, noteId: Long = this.noteId) =
        NoteVersion(id = id, noteId = noteId, title = title, body = body, wordCount = wordCount, createdAt = createdAt)
    companion object {
        fun from(v: NoteVersion) = VersionDto(v.id, v.noteId, v.title, v.body, v.wordCount, v.createdAt)
    }
}

@Serializable
data class DailyStatDto(val day: String, val wordsAdded: Int = 0, val saves: Int = 0) {
    fun toEntity() = DailyStat(day, wordsAdded, saves)
    companion object {
        fun from(s: DailyStat) = DailyStatDto(s.day, s.wordsAdded, s.saves)
    }
}

/** Summary shown before the user confirms a restore. */
data class BackupInfo(
    val format: Int,
    val versionName: String,
    val exportedAt: Long,
    val notes: Int,
    val trashedNotes: Int,
    val categories: Int,
    val tags: Int,
    val templates: Int,
    val versions: Int,
    val hasSettings: Boolean,
)

enum class RestoreMode { MERGE, REPLACE }

data class RestoreResult(val notesAdded: Int, val categoriesAdded: Int, val tagsAdded: Int, val templatesAdded: Int)
