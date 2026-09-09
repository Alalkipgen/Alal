package com.alal.notes.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.alal.notes.BuildConfig
import com.alal.notes.data.dao.CategoryDao
import com.alal.notes.data.dao.DailyStatDao
import com.alal.notes.data.dao.NoteDao
import com.alal.notes.data.dao.TagDao
import com.alal.notes.data.dao.TemplateDao
import com.alal.notes.data.dao.VersionDao
import com.alal.notes.data.db.AlalDatabase
import com.alal.notes.data.entity.NoteTagCrossRef
import com.alal.notes.data.prefs.UserPreferences
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates and restores plain-JSON backups of the whole database + settings.
 * No network is involved: the user picks a file/folder (SAF) or shares the file to any app
 * (Gmail, Drive, Telegram...) through the system share sheet.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AlalDatabase,
    private val noteDao: NoteDao,
    private val categoryDao: CategoryDao,
    private val tagDao: TagDao,
    private val templateDao: TemplateDao,
    private val versionDao: VersionDao,
    private val dailyStatDao: DailyStatDao,
    private val prefs: UserPreferences,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        isLenient = true
    }

    // ------------------------------------------------------------------ export

    suspend fun buildBackup(): BackupFile = withContext(Dispatchers.IO) {
        BackupFile(
            versionName = BuildConfig.VERSION_NAME,
            exportedAt = System.currentTimeMillis(),
            notes = noteDao.getAll().map(NoteDto::from),
            categories = categoryDao.getAll().map(CategoryDto::from),
            tags = tagDao.getAll().map(TagDto::from),
            noteTags = noteDao.getAllNoteTags().map(NoteTagDto::from),
            templates = templateDao.getAll().filter { !it.isBuiltIn }.map(TemplateDto::from),
            versions = versionDao.getAll().map(VersionDto::from),
            dailyStats = dailyStatDao.getAll().map(DailyStatDto::from),
            settings = prefs.exportAll(),
        )
    }

    suspend fun buildJson(): String = json.encodeToString(BackupFile.serializer(), buildBackup())

    fun suggestedFileName(now: Long = System.currentTimeMillis()): String =
        "alal-backup-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date(now)) + ".json"

    /** Writes a backup to a user-chosen document URI (from CreateDocument). */
    suspend fun writeTo(uri: Uri): Long = withContext(Dispatchers.IO) {
        val text = buildJson()
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IOException("Cannot open $uri")
        prefs.setLastBackupAt(System.currentTimeMillis())
        text.length.toLong()
    }

    /** Writes a backup into the app cache and returns a shareable content:// URI. */
    suspend fun writeForShare(): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, suggestedFileName())
        file.writeText(buildJson(), Charsets.UTF_8)
        prefs.setLastBackupAt(System.currentTimeMillis())
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /**
     * Writes a dated backup into the SAF folder chosen for auto-backup and keeps only the newest
     * [keep] files created by Alal. Returns false if the folder is no longer accessible.
     */
    suspend fun writeToFolder(treeUri: Uri, keep: Int = 7): Boolean = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val parent = try {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        } catch (e: Exception) {
            return@withContext false
        }
        val text = buildJson()
        val doc = try {
            DocumentsContract.createDocument(resolver, parent, "application/json", suggestedFileName())
        } catch (e: Exception) {
            null
        } ?: return@withContext false
        resolver.openOutputStream(doc, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: return@withContext false
        prefs.setLastBackupAt(System.currentTimeMillis())

        // Prune old alal-backup-*.json files.
        try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
            val ours = ArrayList<Pair<String, Uri>>()
            resolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    if (name.startsWith("alal-backup-") && name.endsWith(".json")) {
                        ours += name to DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0))
                    }
                }
            }
            ours.sortedByDescending { it.first }.drop(keep).forEach { (_, u) ->
                try { DocumentsContract.deleteDocument(resolver, u) } catch (_: Exception) { }
            }
        } catch (_: Exception) {
            // pruning is best-effort
        }
        true
    }

    // ------------------------------------------------------------------ import

    suspend fun read(uri: Uri): BackupFile = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IOException("Cannot open $uri")
        val file = json.decodeFromString(BackupFile.serializer(), text)
        if (file.app != "Alal") throw IllegalArgumentException("Not an Alal backup")
        if (file.format > BackupFile.FORMAT) throw IllegalArgumentException("Backup is from a newer Alal version")
        file
    }

    fun describe(file: BackupFile) = BackupInfo(
        format = file.format,
        versionName = file.versionName,
        exportedAt = file.exportedAt,
        notes = file.notes.count { !it.isTrashed },
        trashedNotes = file.notes.count { it.isTrashed },
        categories = file.categories.size,
        tags = file.tags.size,
        templates = file.templates.size,
        versions = file.versions.size,
        hasSettings = file.settings.isNotEmpty(),
    )

    /**
     * MERGE: adds everything from the file as *new* rows (ids are remapped; categories/tags are
     * matched by name so you don't get duplicates). REPLACE: wipes the current database first and
     * keeps original ids. Settings are applied in both modes when [applySettings] is true.
     */
    suspend fun restore(file: BackupFile, mode: RestoreMode, applySettings: Boolean): RestoreResult = withContext(Dispatchers.IO) {
        var notesAdded = 0
        var categoriesAdded = 0
        var tagsAdded = 0
        var templatesAdded = 0
        db.withTransaction {
            if (mode == RestoreMode.REPLACE) {
                noteDao.deleteAll() // cascades note_tags + note_versions
                versionDao.deleteAll()
                categoryDao.deleteAll()
                tagDao.deleteAll()
                templateDao.deleteCustom()
                dailyStatDao.deleteAll()
            }
            val replace = mode == RestoreMode.REPLACE

            // categories
            val catMap = HashMap<Long, Long>()
            for (c in file.categories) {
                val existing = if (replace) null else categoryDao.findByName(c.name)
                val newId = when {
                    existing != null -> existing.id
                    replace -> { categoryDao.insert(c.toEntity()); c.id }
                    else -> categoryDao.insert(c.toEntity(id = 0))
                }
                if (existing == null) categoriesAdded++
                catMap[c.id] = newId
            }

            // tags
            val tagMap = HashMap<Long, Long>()
            for (t in file.tags) {
                val existing = if (replace) null else tagDao.findByName(t.name)
                val newId = when {
                    existing != null -> existing.id
                    replace -> { tagDao.insert(t.toEntity()); t.id }
                    else -> tagDao.getOrCreate(t.name).id
                }
                if (existing == null) tagsAdded++
                tagMap[t.id] = newId
            }

            // notes
            val noteMap = HashMap<Long, Long>()
            for (n in file.notes) {
                val cat = n.categoryId?.let { catMap[it] }
                val newId = if (replace) {
                    noteDao.insert(n.toEntity(categoryId = cat)); n.id
                } else {
                    noteDao.insert(n.toEntity(id = 0, categoryId = cat))
                }
                noteMap[n.id] = newId
                notesAdded++
            }

            // note <-> tag
            for (r in file.noteTags) {
                val nid = noteMap[r.noteId] ?: continue
                val tid = tagMap[r.tagId] ?: continue
                noteDao.addTag(NoteTagCrossRef(nid, tid))
            }

            // custom templates (matched by key when merging)
            for (t in file.templates) {
                if (t.isBuiltIn) continue
                val existing = if (replace) null else templateDao.findByKey(t.key)
                if (existing != null) continue
                templateDao.upsert(t.toEntity(id = if (replace) t.id else 0))
                templatesAdded++
            }

            // versions
            for (v in file.versions) {
                val nid = noteMap[v.noteId] ?: continue
                versionDao.insert(v.toEntity(id = if (replace) v.id else 0, noteId = nid))
            }

            // daily stats (merge = add up)
            for (s in file.dailyStats) {
                if (replace) dailyStatDao.upsert(s.toEntity()) else dailyStatDao.add(s.day, s.wordsAdded, s.saves)
            }
        }
        if (applySettings && file.settings.isNotEmpty()) prefs.importAll(file.settings)
        RestoreResult(notesAdded, categoriesAdded, tagsAdded, templatesAdded)
    }
}
