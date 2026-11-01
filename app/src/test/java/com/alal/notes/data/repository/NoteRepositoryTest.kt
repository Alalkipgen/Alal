package com.alal.notes.data.repository

import com.alal.notes.domain.model.NoteStatus
import com.alal.notes.domain.model.WordCountMethod
import com.alal.notes.testing.TestEnvironment
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

@RunWith(RobolectricTestRunner::class)
class NoteRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: TestEnvironment
    private val repository get() = env.repository
    private val method = WordCountMethod.SPACE_SPLIT

    @Before fun setUp() { env = TestEnvironment(tmp.root) }

    @After fun tearDown() { env.close() }

    @Test
    fun contentSaveFromAStaleCopyKeepsMetadata() = runBlocking<Unit> {
        val id = repository.createNote("Draft", null, method)
        val stale = repository.getNote(id)!!
        repository.setLocked(listOf(id), true)
        repository.setPinned(listOf(id), true)
        repository.setStatus(listOf(id), NoteStatus.EDITING)
        repository.setWordGoal(id, 800)

        repository.saveContent(stale, "Title", "Draft, longer now", method)

        val stored = stored(id)
        assertEquals("Draft, longer now", stored.body)
        assertEquals(3, stored.wordCount)
        assertTrue(stored.isLocked)
        assertTrue(stored.isPinned)
        assertEquals(NoteStatus.EDITING, stored.status)
        assertEquals(800, stored.wordGoal)
    }

    @Test
    fun ftsSearchFindsTextAfterContentSave() = runBlocking<Unit> {
        val id = repository.createNote("", null, method)
        repository.saveContent(repository.getNote(id)!!, "Budget meeting", "Quarterly numbers for the newsroom", method)

        // The FTS index itself (not the LIKE fallback) was updated by the targeted UPDATE.
        assertEquals(listOf(id), env.db.noteDao().searchFts("\"newsroom\"*").map { it.id })
        assertEquals(listOf(id), repository.search("quarterly").map { it.id })
        assertEquals(listOf(id), repository.search("budget").map { it.id })

        repository.saveContent(repository.getNote(id)!!, "Budget meeting", "Something else entirely", method)
        assertTrue(env.db.noteDao().searchFts("\"newsroom\"*").isEmpty())
        assertEquals(listOf(id), repository.search("entirely").map { it.id })
    }

    @Test
    fun myanmarSearchFindsTextAfterContentSave() = runBlocking<Unit> {
        val id = repository.createNote("", null, method)
        repository.saveContent(repository.getNote(id)!!, "", "မြန်မာစာ စမ်းသပ်ချက်", method)

        assertEquals(listOf(id), repository.search("စမ်းသပ်").map { it.id })
    }

    @Test
    fun writesEvictTheWarmedCopy() = runBlocking<Unit> {
        val id = repository.createNote("Draft", null, method)
        repository.prepareForOpen(id) // warmed

        repository.setLocked(listOf(id), true)
        assertTrue(repository.prepareForOpen(id)!!.isLocked)

        repository.saveContent(repository.getNote(id)!!, "", "Draft two", method)
        assertEquals("Draft two", repository.prepareForOpen(id)!!.body)

        repository.trash(listOf(id))
        assertTrue(repository.prepareForOpen(id)!!.isTrashed)
    }

    @Test
    fun getNoteReadsTheDatabaseNotTheWarmUpCache() = runBlocking<Unit> {
        val id = repository.createNote("Stored text", null, method)
        repository.setLocked(listOf(id), true)
        // e.g. a Home list emission that was already in flight when the lock was written
        repository.warmOpenCache(listOf(repository.getNote(id)!!.copy(body = "stale", isLocked = false)))

        val stored = repository.getNote(id)!!
        assertEquals("Stored text", stored.body)
        assertTrue(stored.isLocked)
    }

    /** The row in the database, read directly. */
    private suspend fun stored(id: Long) = env.db.noteDao().getById(id)!!
}
