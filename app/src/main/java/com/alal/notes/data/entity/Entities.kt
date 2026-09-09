package com.alal.notes.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.alal.notes.domain.model.NoteStatus
import com.alal.notes.domain.model.PaperTexture
import com.alal.notes.domain.model.enumOrDefault

@Entity(
    tableName = "notes",
    indices = [
        Index("categoryId"),
        Index("isTrashed", "isArchived", "isPinned", "updatedAt"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val categoryId: Long? = null,
    val status: NoteStatus = NoteStatus.DRAFT,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null,
    val wordGoal: Int? = null,
    /** ARGB int, or null = theme default. */
    val backgroundColor: Int? = null,
    /** Index into the gradient preset list, or null. */
    val backgroundGradient: Int? = null,
    val showBackgroundOnCard: Boolean = true,
    val paperTexture: PaperTexture? = null,
    val fontSizeOverride: Int? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val wordCount: Int = 0,
    val charCount: Int = 0,
    /** Phase 2: per-note lock (requires app PIN/biometric). */
    val isLocked: Boolean = false,
    /** Phase 2: epoch millis of a pending reminder, or null. */
    val reminderAt: Long? = null,
)

/** Phase 2: a snapshot of a note's text, taken automatically on meaningful saves. */
@Entity(
    tableName = "note_versions",
    indices = [Index("noteId")],
    foreignKeys = [
        ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class NoteVersion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val title: String,
    val body: String,
    val wordCount: Int,
    val createdAt: Long,
)

/** Phase 2: one row per calendar day (ISO yyyy-MM-dd) for the Stats screen. */
@Entity(tableName = "daily_stats")
data class DailyStat(
    @PrimaryKey val day: String,
    val wordsAdded: Int = 0,
    val saves: Int = 0,
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ARGB int. */
    val color: Int,
    val icon: String = "label",
    val sortOrder: Int = 0,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    indices = [Index("tagId")],
    foreignKeys = [
        ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class NoteTagCrossRef(
    val noteId: Long,
    val tagId: Long,
)

/** External-content FTS4 table mirroring [Note] (title + body). */
@Fts4(contentEntity = Note::class)
@Entity(tableName = "notes_fts")
data class NoteFts(
    val title: String,
    val body: String,
)

@Entity(tableName = "templates", indices = [Index(value = ["key"], unique = true)])
data class Template(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "key") val key: String,
    val name: String,
    val icon: String = "description",
    val body: String = "",
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0,
)

class Converters {
    @TypeConverter fun statusToString(s: NoteStatus?): String? = s?.name
    @TypeConverter fun stringToStatus(s: String?): NoteStatus? = s?.let { enumOrDefault(it, NoteStatus.DRAFT) }
    @TypeConverter fun textureToString(t: PaperTexture?): String? = t?.name
    @TypeConverter fun stringToTexture(s: String?): PaperTexture? = s?.let { enumOrDefault(it, PaperTexture.DOTTED) }
}
