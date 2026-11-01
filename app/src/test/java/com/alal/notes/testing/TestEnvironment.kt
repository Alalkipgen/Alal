package com.alal.notes.testing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.alal.notes.data.db.AlalDatabase
import com.alal.notes.data.prefs.UserPreferences
import com.alal.notes.data.repository.NoteRepository
import com.alal.notes.domain.wordcount.WordCounter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.concurrent.Executors

/** Real Room (in memory, with the FTS triggers), real DataStore in a temp file, real repository. */
class TestEnvironment(dir: File) {
    val db: AlalDatabase = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AlalDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val prefs = UserPreferences(
        PreferenceDataStoreFactory.create(scope = storeScope) { File(dir, "test_settings.preferences_pb") },
    )

    val repository = NoteRepository(
        db.noteDao(), db.categoryDao(), db.tagDao(), db.templateDao(), db.versionDao(), db.dailyStatDao(), WordCounter(),
    )

    fun close() {
        db.close()
        storeScope.cancel()
    }
}

/**
 * Gives viewModelScope one real "main" thread, like the app's UI thread, so ViewModel coroutines
 * never interleave with each other. Use [run] for calls the UI would make on that thread.
 */
class MainThreadRule : TestWatcher() {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "test-main") }
    private val dispatcher = executor.asCoroutineDispatcher()

    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) {
        Dispatchers.resetMain()
        executor.shutdown()
    }

    fun <T> run(block: () -> T): T = runBlocking(dispatcher) { block() }
}

/** Polls [condition] until it holds; Room, DataStore and the editor's exit work run on other threads. */
fun waitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for $what")
        Thread.sleep(10)
    }
}
