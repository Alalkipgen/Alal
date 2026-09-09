package com.alal.notes.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.alal.notes.data.entity.Category
import com.alal.notes.data.entity.DailyStat
import com.alal.notes.data.entity.Note
import com.alal.notes.data.entity.NoteTagCrossRef
import com.alal.notes.data.entity.NoteVersion
import com.alal.notes.data.entity.Tag
import com.alal.notes.data.entity.Template
import kotlinx.coroutines.flow.Flow

data class TagCount(val id: Long, val name: String, val noteCount: Int)

/** Stats: notes + words grouped by a text key (status name or category name). */
data class GroupCount(val key: String?, val n: Int, val words: Int)

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): Note?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: Long): Flow<Note?>

    /** Active notes (not archived, not trashed). Pinned first, then by updatedAt desc. */
    @Query("SELECT * FROM notes WHERE isTrashed = 0 AND isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun observeActive(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isTrashed = 0 AND isArchived = 0 AND isPinned = 1 ORDER BY updatedAt DESC")
    fun observePinned(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isTrashed = 0 AND isFavorite = 1 ORDER BY updatedAt DESC")
    fun observeFavorites(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isTrashed = 0 AND isArchived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    fun observeTrashed(): Flow<List<Note>>

    @Query("SELECT notes.* FROM notes JOIN note_tags ON notes.id = note_tags.noteId WHERE note_tags.tagId = :tagId AND notes.isTrashed = 0 ORDER BY notes.updatedAt DESC")
    fun observeByTag(tagId: Long): Flow<List<Note>>

    @Query("SELECT COUNT(*) FROM notes WHERE isTrashed = 0 AND isArchived = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(wordCount), 0) FROM notes WHERE isTrashed = 0")
    fun observeTotalWords(): Flow<Int>

    @Query(
        "SELECT notes.* FROM notes JOIN notes_fts ON notes.id = notes_fts.rowid " +
            "WHERE notes_fts MATCH :query AND notes.isTrashed = 0 ORDER BY notes.updatedAt DESC LIMIT 200",
    )
    suspend fun searchFts(query: String): List<Note>

    /** Fallback for scripts FTS4's simple tokenizer handles poorly (e.g. Myanmar without spaces). */
    @Query(
        "SELECT * FROM notes WHERE isTrashed = 0 AND (title LIKE '%' || :needle || '%' OR body LIKE '%' || :needle || '%') " +
            "ORDER BY updatedAt DESC LIMIT 200",
    )
    suspend fun searchLike(needle: String): List<Note>

    @Query("UPDATE notes SET isPinned = :pinned, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setPinned(ids: List<Long>, pinned: Boolean, now: Long)

    @Query("UPDATE notes SET isFavorite = :fav WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<Long>, fav: Boolean)

    @Query("UPDATE notes SET isArchived = :archived, isPinned = CASE WHEN :archived THEN 0 ELSE isPinned END WHERE id IN (:ids)")
    suspend fun setArchived(ids: List<Long>, archived: Boolean)

    @Query("UPDATE notes SET isTrashed = 1, trashedAt = :now, isPinned = 0 WHERE id IN (:ids)")
    suspend fun trash(ids: List<Long>, now: Long)

    @Query("UPDATE notes SET isTrashed = 0, trashedAt = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<Long>)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM notes WHERE isTrashed = 1")
    suspend fun emptyTrash()

    @Query("DELETE FROM notes WHERE isTrashed = 1 AND trashedAt IS NOT NULL AND trashedAt < :before")
    suspend fun purgeTrashedBefore(before: Long): Int

    @Query("UPDATE notes SET status = :status WHERE id IN (:ids)")
    suspend fun setStatus(ids: List<Long>, status: String)

    @Query("UPDATE notes SET categoryId = :categoryId WHERE id IN (:ids)")
    suspend fun setCategory(ids: List<Long>, categoryId: Long?)

    // Tags
    @Query("SELECT tags.* FROM tags JOIN note_tags ON tags.id = note_tags.tagId WHERE note_tags.noteId = :noteId ORDER BY tags.name")
    fun observeTagsForNote(noteId: Long): Flow<List<Tag>>

    @Query("SELECT tags.* FROM tags JOIN note_tags ON tags.id = note_tags.tagId WHERE note_tags.noteId = :noteId ORDER BY tags.name")
    suspend fun getTagsForNote(noteId: Long): List<Tag>

    @Query("SELECT * FROM note_tags")
    fun observeAllNoteTags(): Flow<List<NoteTagCrossRef>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTag(ref: NoteTagCrossRef)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId AND tagId = :tagId")
    suspend fun removeTag(noteId: Long, tagId: Long)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun clearTags(noteId: Long)

    @Transaction
    suspend fun duplicate(id: Long, now: Long): Long? {
        val src = getById(id) ?: return null
        val copy = src.copy(id = 0, isPinned = false, createdAt = now, updatedAt = now, title = src.title, reminderAt = null)
        val newId = insert(copy)
        getTagsForNote(id).forEach { addTag(NoteTagCrossRef(newId, it.id)) }
        return newId
    }

    // ---- Phase 2: lock / reminder ----
    @Query("UPDATE notes SET isLocked = :locked WHERE id IN (:ids)")
    suspend fun setLocked(ids: List<Long>, locked: Boolean)

    @Query("UPDATE notes SET isLocked = 0 WHERE isLocked = 1")
    suspend fun unlockAll()

    @Query("UPDATE notes SET reminderAt = :at WHERE id = :id")
    suspend fun setReminder(id: Long, at: Long?)

    @Query("SELECT * FROM notes WHERE reminderAt IS NOT NULL AND isTrashed = 0")
    suspend fun getWithReminders(): List<Note>

    // ---- Phase 2: backup ----
    @Query("SELECT * FROM notes ORDER BY id")
    suspend fun getAll(): List<Note>

    @Query("SELECT * FROM note_tags")
    suspend fun getAllNoteTags(): List<NoteTagCrossRef>

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    // ---- Phase 2: stats ----
    @Query("SELECT COUNT(*) FROM notes WHERE isTrashed = 0")
    fun observeCountNotTrashed(): Flow<Int>

    @Query("SELECT COALESCE(SUM(charCount), 0) FROM notes WHERE isTrashed = 0")
    fun observeTotalChars(): Flow<Int>

    @Query("SELECT status AS `key`, COUNT(*) AS n, COALESCE(SUM(wordCount), 0) AS words FROM notes WHERE isTrashed = 0 GROUP BY status")
    fun observeByStatus(): Flow<List<GroupCount>>

    @Query(
        "SELECT categories.name AS `key`, COUNT(notes.id) AS n, COALESCE(SUM(notes.wordCount), 0) AS words " +
            "FROM notes LEFT JOIN categories ON categories.id = notes.categoryId " +
            "WHERE notes.isTrashed = 0 GROUP BY notes.categoryId ORDER BY n DESC",
    )
    fun observeByCategory(): Flow<List<GroupCount>>

    @Query("SELECT * FROM notes WHERE isTrashed = 0 ORDER BY wordCount DESC LIMIT 1")
    fun observeLongest(): Flow<Note?>
}

@Dao
interface VersionDao {
    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY createdAt DESC")
    fun observeForNote(noteId: Long): Flow<List<NoteVersion>>

    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestForNote(noteId: Long): NoteVersion?

    @Query("SELECT * FROM note_versions WHERE id = :id")
    suspend fun getById(id: Long): NoteVersion?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(version: NoteVersion): Long

    @Query("DELETE FROM note_versions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM note_versions WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)

    @Query(
        "DELETE FROM note_versions WHERE noteId = :noteId AND id NOT IN " +
            "(SELECT id FROM note_versions WHERE noteId = :noteId ORDER BY createdAt DESC LIMIT :keep)",
    )
    suspend fun trim(noteId: Long, keep: Int)

    @Query("SELECT * FROM note_versions ORDER BY id")
    suspend fun getAll(): List<NoteVersion>

    @Query("DELETE FROM note_versions")
    suspend fun deleteAll()
}

@Dao
interface DailyStatDao {
    @Query("SELECT * FROM daily_stats WHERE day = :day")
    suspend fun get(day: String): DailyStat?

    @Query("SELECT * FROM daily_stats WHERE day = :day")
    fun observe(day: String): Flow<DailyStat?>

    @Query("SELECT * FROM daily_stats WHERE day >= :fromDay ORDER BY day")
    fun observeSince(fromDay: String): Flow<List<DailyStat>>

    @Query("SELECT * FROM daily_stats ORDER BY day DESC LIMIT 400")
    fun observeRecent(): Flow<List<DailyStat>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stat: DailyStat)

    @Transaction
    suspend fun add(day: String, words: Int, saves: Int) {
        val cur = get(day) ?: DailyStat(day)
        upsert(cur.copy(wordsAdded = cur.wordsAdded + words, saves = cur.saves + saves))
    }

    @Query("SELECT * FROM daily_stats ORDER BY day")
    suspend fun getAll(): List<DailyStat>

    @Query("DELETE FROM daily_stats")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    suspend fun getAll(): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Category?

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<Tag>>

    @Query(
        "SELECT tags.id AS id, tags.name AS name, COUNT(note_tags.noteId) AS noteCount FROM tags " +
            "LEFT JOIN note_tags ON tags.id = note_tags.tagId GROUP BY tags.id ORDER BY noteCount DESC, tags.name",
    )
    fun observeWithCounts(): Flow<List<TagCount>>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Tag?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: Tag): Long

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM tags ORDER BY id")
    suspend fun getAll(): List<Tag>

    @Query("DELETE FROM tags")
    suspend fun deleteAll()

    @Transaction
    suspend fun getOrCreate(name: String): Tag {
        findByName(name)?.let { return it }
        val id = insert(Tag(name = name))
        return if (id > 0) Tag(id, name) else findByName(name) ?: Tag(name = name)
    }
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<Template>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getById(id: Long): Template?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(templates: List<Template>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: Template): Long

    @Query("DELETE FROM templates WHERE id = :id AND isBuiltIn = 0")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM templates")
    suspend fun count(): Int

    @Query("SELECT * FROM templates ORDER BY id")
    suspend fun getAll(): List<Template>

    @Query("SELECT * FROM templates WHERE `key` = :key LIMIT 1")
    suspend fun findByKey(key: String): Template?

    @Query("DELETE FROM templates WHERE isBuiltIn = 0")
    suspend fun deleteCustom()
}
