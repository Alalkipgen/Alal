package com.alal.notes.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.alal.notes.data.prefs.Settings
import com.alal.notes.data.prefs.UserPreferences
import com.alal.notes.testing.MainThreadRule
import com.alal.notes.testing.TestEnvironment
import com.alal.notes.testing.waitUntil
import com.alal.notes.ui.lock.LockGateViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Cold start: nothing is drawn from default settings, and an enabled lock always starts locked. */
@RunWith(RobolectricTestRunner::class)
class AppLockStartupTest {

    @get:Rule val tmp = TemporaryFolder()
    @get:Rule val main = MainThreadRule()
    private lateinit var env: TestEnvironment

    private val lockOn = Settings(lockEnabled = true, lockPinHash = "hash", lockSalt = "salt", lockTimeoutSec = 0)

    @Before fun setUp() { env = TestEnvironment(tmp.root) }

    @After fun tearDown() { env.close() }

    @Test
    fun settingsNotLoadedYetMeansNothingToRender() {
        val store = SlowDataStore()
        val vm = MainViewModel(UserPreferences(store), env.repository)

        // MainActivity keeps the splash up and composes no content while this is null.
        assertNull(vm.settings.value)

        store.deliver(preferencesOf(booleanPreferencesKey("lockEnabled") to true, stringPreferencesKey("lockPinHash") to "hash"))
        waitUntil("settings to load") { vm.settings.value != null }
        assertTrue(vm.settings.value!!.lockEnabled)
    }

    @Test
    fun loadedSettingsWithLockEnabledStartLocked() {
        runBlocking {
            env.prefs.setLockPin(hash = "hash", salt = "salt")
            env.prefs.setLockEnabled(true)
        }
        val vm = MainViewModel(env.prefs, env.repository)
        waitUntil("settings to load") { vm.settings.value != null }
        val settings = vm.settings.value!!

        val gate = gateIn(ViewModelStore())
        gate.onSettings(settings)
        assertTrue(gate.state.isLocked(settings))
    }

    @Test
    fun rotationKeepsTheSameGateUnlocked() {
        val activityStore = ViewModelStore() // survives configuration changes
        val gate = gateIn(activityStore)
        gate.onSettings(lockOn)
        gate.onUnlocked()

        gate.onStop(now = 1_000, changingConfigurations = true)
        val recreated = gateIn(activityStore)
        recreated.onSettings(lockOn)
        recreated.onStart(lockOn, now = 61_000)

        assertSame(gate, recreated)
        assertFalse(recreated.state.isLocked(lockOn))
    }

    @Test
    fun newProcessStartsLockedAgain() {
        val oldProcess = ViewModelStore()
        val gate = gateIn(oldProcess)
        gate.onSettings(lockOn)
        gate.onUnlocked()
        assertFalse(gate.state.isLocked(lockOn))

        // Process death: no ViewModel survives (unlike rememberSaveable state, which is restored).
        oldProcess.clear()
        val fresh = gateIn(ViewModelStore())
        fresh.onSettings(lockOn)

        assertNotSame(gate, fresh)
        assertTrue(fresh.state.isLocked(lockOn))
    }

    private fun gateIn(store: ViewModelStore): LockGateViewModel =
        ViewModelProvider(store, ViewModelProvider.NewInstanceFactory())[LockGateViewModel::class.java]

    /** A DataStore whose first read has not finished yet (cold start on a slow device). */
    private class SlowDataStore : DataStore<Preferences> {
        private val current = MutableStateFlow<Preferences?>(null)
        override val data: Flow<Preferences> = current.filterNotNull()

        fun deliver(prefs: Preferences) { current.value = prefs }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(current.filterNotNull().first()).also { current.value = it }
    }
}
