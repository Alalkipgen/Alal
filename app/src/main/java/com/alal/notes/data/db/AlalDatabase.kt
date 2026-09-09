package com.alal.notes.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alal.notes.data.dao.CategoryDao
import com.alal.notes.data.dao.DailyStatDao
import com.alal.notes.data.dao.NoteDao
import com.alal.notes.data.dao.TagDao
import com.alal.notes.data.dao.TemplateDao
import com.alal.notes.data.dao.VersionDao
import com.alal.notes.data.entity.Category
import com.alal.notes.data.entity.Converters
import com.alal.notes.data.entity.DailyStat
import com.alal.notes.data.entity.Note
import com.alal.notes.data.entity.NoteFts
import com.alal.notes.data.entity.NoteVersion
import com.alal.notes.data.entity.NoteTagCrossRef
import com.alal.notes.data.entity.Tag
import com.alal.notes.data.entity.Template
import com.alal.notes.domain.template.BuiltInTemplates

@Database(
    entities = [
        Note::class, Category::class, Tag::class, NoteTagCrossRef::class, NoteFts::class, Template::class,
        NoteVersion::class, DailyStat::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AlalDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun templateDao(): TemplateDao
    abstract fun versionDao(): VersionDao
    abstract fun dailyStatDao(): DailyStatDao

    companion object {
        const val NAME = "alal.db"

        fun build(context: Context): AlalDatabase =
            Room.databaseBuilder(context, AlalDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .addCallback(Seed)
                .build()

        /** Phase 1 -> Phase 2: lock + reminder columns, version history, daily writing stats. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isLocked INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN reminderAt INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS note_versions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "noteId INTEGER NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, " +
                        "wordCount INTEGER NOT NULL, createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(noteId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_versions_noteId ON note_versions (noteId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS daily_stats (" +
                        "day TEXT NOT NULL, wordsAdded INTEGER NOT NULL, saves INTEGER NOT NULL, PRIMARY KEY(day))",
                )
            }
        }

        /** Seeds built-in templates and default categories with plain SQL (runs once on create). */
        private object Seed : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                for (t in BuiltInTemplates.all) {
                    db.execSQL(
                        "INSERT OR IGNORE INTO templates (id, `key`, name, icon, body, isBuiltIn, sortOrder) VALUES (?, ?, ?, ?, ?, 1, ?)",
                        arrayOf<Any>(t.id, t.key, t.name, t.icon, t.body, t.sortOrder),
                    )
                }
                val defaults = listOf(
                    Triple("Features", 0xFF2A7F7F.toInt(), "article"),
                    Triple("Interviews", 0xFF4F5BD5.toInt(), "mic"),
                    Triple("Research", 0xFF7B4FA3.toInt(), "science"),
                    Triple("Ideas", 0xFFD98E04.toInt(), "lightbulb"),
                )
                defaults.forEachIndexed { i, (name, color, icon) ->
                    db.execSQL(
                        "INSERT INTO categories (name, color, icon, sortOrder) VALUES (?, ?, ?, ?)",
                        arrayOf<Any>(name, color, icon, i),
                    )
                }
            }
        }
    }
}
