package com.alal.notes.data.repository

import com.alal.notes.data.dao.CategoryDao
import com.alal.notes.data.dao.DailyStatDao
import com.alal.notes.data.dao.GroupCount
import com.alal.notes.data.dao.NoteDao
import com.alal.notes.data.dao.TagCount
import com.alal.notes.data.dao.TagDao
import com.alal.notes.data.dao.TemplateDao
import com.alal.notes.data.dao.VersionDao
import com.alal.notes.data.entity.Category
import com.alal.notes.data.entity.DailyStat
import com.alal.notes.data.entity.Note
import com.alal.notes.data.entity.NoteTagCrossRef
import com.alal.notes.data.entity.NoteVersion
import com.alal.notes.data.entity.Tag
import com.alal.notes.data.entity.Template
import com.alal.notes.domain.model.NoteStatus
import com.alal.notes.domain.model.WordCountMethod
import com.alal.notes.domain.wordcount.WordCounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val categoryDao: CategoryDao,
    private val tagDao: TagDao,
    private val templateDao: TemplateDao,
    private val versionDao: VersionDao,
    private val dailyStatDao: DailyStatDao,
    private val wordCounter: WordCounter,
) {
    // ---- observe ----
    fun observeActive(): Flow<List<Note>> = noteDao.observeActive()
    fun observePinned(): Flow<List<Note>> = noteDao.observePinned()
    fun observeFavorites(): Flow<List<Note>> = noteDao.observeFavorites()
    fun observeArchived(): Flow<List<Note>> = noteDao.observeArchived()
    fun observeTrashed(): Flow<List<Note>> = noteDao.observeTrashed()
    fun observeByTag(tagId: Long): Flow<List<Note>> = noteDao.observeByTag(tagId)
    fun observeNote(id: Long): Flow<Note?> = noteDao.observeById(id)
    fun observeActiveCount(): Flow<Int> = noteDao.observeActiveCount()
    fun observeTotalWords(): Flow<Int> = noteDao.observeTotalWords()
    fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll()
    fun observeTags(): Flow<List<Tag>> = tagDao.observeAll()
    fun observeTagCounts(): Flow<List<TagCount>> = tagDao.observeWithCounts()
    fun observeTagsForNote(noteId: Long): Flow<List<Tag>> = noteDao.observeTagsForNote(noteId)
    fun observeAllNoteTags(): Flow<List<NoteTagCrossRef>> = noteDao.observeAllNoteTags()
    fun observeTemplates(): Flow<List<Template>> = templateDao.observeAll()

    suspend fun getNote(id: Long): Note? = noteDao.getById(id)
    suspend fun getTemplate(id: Long): Template? = templateDao.getById(id)

    // ---- create / save ----
    suspend fun createNote(body: String = "", categoryId: Long? = null, method: WordCountMethod): Long {
        val now = System.currentTimeMillis()
        val stats = wordCounter.count(body, method)
        return noteDao.insert(
            Note(body = body, categoryId = categoryId, createdAt = now, updatedAt = now, wordCount = stats.words, charCount = stats.chars),
        )
    }

    /** Saves content; recounts words off the main thread. Returns the stored note. */
    suspend fun saveContent(note: Note, title: String, body: String, method: WordCountMethod): Note = withContext(Dispatchers.Default) {
        val stats = wordCounter.count(body, method)
        val updated = note.copy(
            title = title,
            body = body,
            wordCount = stats.words,
            charCount = stats.chars,
            updatedAt = System.currentTimeMillis(),
        )
        noteDao.update(updated)
        updated
    }

    /**
     * Phase 2 bookkeeping after a successful [saveContent]: logs words written today and takes a
     * version snapshot when the text changed meaningfully. Safe to call from any dispatcher.
     */
    suspend fun afterSave(before: Note, after: Note) = withContext(Dispatchers.IO) {
        val delta = after.wordCount - before.wordCount
        dailyStatDao.add(today(), if (delta > 0) delta else 0, 1)
        val last = versionDao.latestForNote(after.id)
        val shouldSnapshot = when {
            before.body.isBlank() && before.title.isBlank() -> false // first save of a fresh note: nothing to keep
            last == null -> true
            last.body == before.body && last.title == before.title -> false // already have this exact state
            abs(before.body.length - last.body.length) >= SNAPSHOT_MIN_CHARS -> true
            before.updatedAt - last.createdAt >= SNAPSHOT_MIN_GAP_MS -> true
            else -> false
        }
        if (shouldSnapshot) {
            // We store the *previous* state, so a snapshot is always something you can go back to.
            versionDao.insert(
                NoteVersion(
                    noteId = after.id, title = before.title, body = before.body,
                    wordCount = before.wordCount, createdAt = before.updatedAt,
                ),
            )
            versionDao.trim(after.id, MAX_VERSIONS_PER_NOTE)
        }
    }

    suspend fun update(note: Note) = noteDao.update(note.copy(updatedAt = System.currentTimeMillis()))

    /** Update metadata (pin, status, colour...) without bumping updatedAt. */
    suspend fun updateMeta(note: Note) = noteDao.update(note)

    suspend fun setPinned(ids: List<Long>, pinned: Boolean) = noteDao.setPinned(ids, pinned, System.currentTimeMillis())
    suspend fun setFavorite(ids: List<Long>, fav: Boolean) = noteDao.setFavorite(ids, fav)
    suspend fun setArchived(ids: List<Long>, archived: Boolean) = noteDao.setArchived(ids, archived)
    suspend fun trash(ids: List<Long>) = noteDao.trash(ids, System.currentTimeMillis())
    suspend fun restore(ids: List<Long>) = noteDao.restore(ids)
    suspend fun deleteForever(ids: List<Long>) = noteDao.deleteByIds(ids)
    suspend fun emptyTrash() = noteDao.emptyTrash()
    suspend fun purgeTrashOlderThan(millis: Long): Int = noteDao.purgeTrashedBefore(System.currentTimeMillis() - millis)
    suspend fun setStatus(ids: List<Long>, status: NoteStatus) = noteDao.setStatus(ids, status.name)
    suspend fun setCategory(ids: List<Long>, categoryId: Long?) = noteDao.setCategory(ids, categoryId)
    suspend fun duplicate(id: Long): Long? = noteDao.duplicate(id, System.currentTimeMillis())

    // ---- search ----
    suspend fun search(raw: String): List<Note> = withContext(Dispatchers.IO) {
        val q = raw.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val hasMyanmar = q.any { it in '\u1000'..'\u109F' }
        if (hasMyanmar) return@withContext noteDao.searchLike(q)
        val ftsQuery = q.split(Regex("\\s+")).filter { it.isNotBlank() }
            .joinToString(" ") { "\"" + it.replace("\"", "") + "\"*" }
        val fts = try { noteDao.searchFts(ftsQuery) } catch (_: Exception) { emptyList() }
        if (fts.isNotEmpty()) fts else noteDao.searchLike(q)
    }

    // ---- categories ----
    suspend fun addCategory(name: String, color: Int, icon: String = "label"): Long {
        val count = categoryDao.count()
        return categoryDao.insert(Category(name = name, color = color, icon = icon, sortOrder = count))
    }
    suspend fun updateCategory(category: Category) = categoryDao.update(category)
    suspend fun deleteCategory(id: Long) = categoryDao.delete(id)

    // ---- tags ----
    suspend fun addTagToNote(noteId: Long, name: String) {
        val clean = name.trim().removePrefix("#").trim()
        if (clean.isEmpty()) return
        val tag = tagDao.getOrCreate(clean)
        noteDao.addTag(NoteTagCrossRef(noteId, tag.id))
    }
    suspend fun removeTagFromNote(noteId: Long, tagId: Long) = noteDao.removeTag(noteId, tagId)
    suspend fun deleteTag(id: Long) = tagDao.delete(id)

    // ---- templates ----
    suspend fun saveTemplate(template: Template): Long = templateDao.upsert(template)
    suspend fun deleteTemplate(id: Long) = templateDao.delete(id)

    // ---- Phase 2: lock / reminder ----
    suspend fun setLocked(ids: List<Long>, locked: Boolean) = noteDao.setLocked(ids, locked)
    suspend fun unlockAllNotes() = noteDao.unlockAll()
    suspend fun setReminder(id: Long, at: Long?) = noteDao.setReminder(id, at)
    suspend fun notesWithReminders(): List<Note> = noteDao.getWithReminders()

    // ---- Phase 2: versions ----
    fun observeVersions(noteId: Long): Flow<List<NoteVersion>> = versionDao.observeForNote(noteId)
    suspend fun getVersion(id: Long): NoteVersion? = versionDao.getById(id)
    suspend fun deleteVersion(id: Long) = versionDao.delete(id)
    suspend fun clearVersions(noteId: Long) = versionDao.deleteForNote(noteId)

    /** Manually snapshot the current stored text of a note (used before a restore). */
    suspend fun snapshotNow(noteId: Long) {
        val n = noteDao.getById(noteId) ?: return
        versionDao.insert(NoteVersion(noteId = n.id, title = n.title, body = n.body, wordCount = n.wordCount, createdAt = System.currentTimeMillis()))
        versionDao.trim(noteId, MAX_VERSIONS_PER_NOTE)
    }

    // ---- Phase 2: stats ----
    fun observeNoteCount(): Flow<Int> = noteDao.observeCountNotTrashed()
    fun observeTotalChars(): Flow<Int> = noteDao.observeTotalChars()
    fun observeByStatus(): Flow<List<GroupCount>> = noteDao.observeByStatus()
    fun observeByCategory(): Flow<List<GroupCount>> = noteDao.observeByCategory()
    fun observeLongest(): Flow<Note?> = noteDao.observeLongest()
    fun observeDailyStats(): Flow<List<DailyStat>> = dailyStatDao.observeRecent()

    companion object {
        const val MAX_VERSIONS_PER_NOTE = 50
        /** A big paste/delete always earns a snapshot... */
        const val SNAPSHOT_MIN_CHARS = 1000
        /** ...otherwise at most one snapshot per 10 minutes of editing. */
        const val SNAPSHOT_MIN_GAP_MS = 10 * 60 * 1000L

        /** ISO day key (device local date), e.g. 2026-09-09. */
        fun today(): String = LocalDate.now().toString()
    }
}
