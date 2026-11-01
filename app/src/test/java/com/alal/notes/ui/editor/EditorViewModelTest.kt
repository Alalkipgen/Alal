package com.alal.notes.ui.editor

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.alal.notes.data.entity.Note
import com.alal.notes.data.export.NoteExporter
import com.alal.notes.data.work.ReminderScheduler
import com.alal.notes.domain.model.WordCountMethod
import com.alal.notes.domain.wordcount.WordCounter
import com.alal.notes.testing.MainThreadRule
import com.alal.notes.testing.TestEnvironment
import com.alal.notes.testing.waitUntil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EditorViewModelTest {

    @get:Rule val tmp = TemporaryFolder()
    @get:Rule val main = MainThreadRule()
    private lateinit var env: TestEnvironment
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun setUp() {
        env = TestEnvironment(tmp.root)
        runBlocking { env.prefs.setAutoSaveDelayMs(300) }
    }

    @After fun tearDown() {
        main.run { stores.forEach { it.clear() } }
        env.close()
    }

    // ------------------------------------------------------------- metadata survives content saves

    @Test
    fun lockSetInEditorSurvivesAutoSave() {
        val id = newNote("First draft")
        val editor = openEditor(id).first

        main.run { editor.toggleLock() }
        waitUntil("lock to be stored") { stored(id)!!.isLocked }

        main.run { editor.type("First draft, ten seconds of typing later") }
        waitUntil("auto-save") { stored(id)!!.body == "First draft, ten seconds of typing later" }

        assertTrue("content save must not unlock the note", stored(id)!!.isLocked)
    }

    @Test
    fun pinSetInEditorSurvivesAutoSave() {
        val id = newNote("First draft")
        val editor = openEditor(id).first

        main.run { editor.togglePin() }
        waitUntil("pin to be stored") { stored(id)!!.isPinned }
        // Pinning bumps updatedAt; let the open editor observe that row before the next keystroke,
        // as it would on a device long before the user reaches the keyboard.
        Thread.sleep(200)

        main.run { editor.type("First draft, ten seconds of typing later") }
        waitUntil("auto-save") { stored(id)!!.body == "First draft, ten seconds of typing later" }

        assertTrue("content save must not unpin the note", stored(id)!!.isPinned)
    }

    // ------------------------------------------------------------- leaving with an empty editor

    @Test
    fun emptiedExistingNoteGoesToTrashWithVersions() {
        val id = newNote("Interview notes")
        runBlocking { env.repository.snapshotNow(id) }
        val editor = openEditor(id).first

        main.run { editor.type("") }
        waitUntil("blank auto-save") { stored(id)!!.body.isEmpty() }
        main.run { editor.finishEditing() }

        waitUntil("note to be in Trash") { stored(id)?.isTrashed == true }
        assertEquals(listOf("Interview notes"), versionBodies(id))
    }

    @Test
    fun emptiedExistingNoteGoesToTrashWhenEditorIsCleared() {
        val id = newNote("Interview notes")
        runBlocking { env.repository.snapshotNow(id) }
        val (editor, store) = openEditor(id)

        main.run {
            editor.type("")
            store.clear() // leaves without finishEditing(): onCleared() applies the same rule
        }

        waitUntil("note to be in Trash") { stored(id)?.isTrashed == true }
        assertEquals("Interview notes", stored(id)!!.body)
        assertEquals(listOf("Interview notes"), versionBodies(id))
    }

    @Test
    fun brandNewBlankNoteIsDeleted() {
        val id = newNote("")
        val editor = openEditor(id).first

        main.run { editor.finishEditing() }

        waitUntil("blank note to be deleted") { stored(id) == null }
    }

    @Test
    fun brandNewBlankNoteIsDeletedWhenEditorIsCleared() {
        val id = newNote("")
        val store = openEditor(id).second

        main.run { store.clear() }

        waitUntil("blank note to be deleted") { stored(id) == null }
    }

    @Test
    fun newNoteThatHadSavedTextGoesToTrashWhenEmptied() {
        val id = newNote("")
        val editor = openEditor(id).first

        main.run { editor.type("An idea worth keeping") }
        waitUntil("auto-save") { stored(id)!!.body == "An idea worth keeping" }
        main.run {
            editor.type("")
            editor.finishEditing()
        }

        waitUntil("note to be in Trash") { stored(id)?.isTrashed == true }
        assertEquals("An idea worth keeping", stored(id)!!.body)
    }

    // ------------------------------------------------------------- helpers

    private fun newNote(body: String): Long =
        runBlocking { env.repository.createNote(body, null, WordCountMethod.SPACE_SPLIT) }

    /** The row in the database, read directly. */
    private fun stored(id: Long): Note? = runBlocking { env.db.noteDao().getById(id) }

    private fun versionBodies(id: Long): List<String> =
        runBlocking { env.repository.observeVersions(id).first().map { it.body } }

    private fun openEditor(id: Long): Pair<EditorViewModel, ViewModelStore> {
        val store = ViewModelStore().also(stores::add)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = RuntimeEnvironment.getApplication()
                return EditorViewModel(env.repository, env.prefs, ReminderScheduler(app), NoteExporter(app, env.prefs), WordCounter()) as T
            }
        }
        val editor = main.run { ViewModelProvider(store, factory)[EditorViewModel::class.java].also { it.load(id) } }
        waitUntil("note $id to open") { editor.loaded.value }
        // Publish the loaded text the way Compose does after each frame, so later typing is a change.
        main.run { Snapshot.sendApplyNotifications() }
        return editor to store
    }

    /** What the keyboard does on the UI thread: change the buffer, then publish the snapshot change. */
    private fun EditorViewModel.type(text: String) {
        bodyState.edit { replace(0, length, text) }
        Snapshot.sendApplyNotifications()
    }
}
