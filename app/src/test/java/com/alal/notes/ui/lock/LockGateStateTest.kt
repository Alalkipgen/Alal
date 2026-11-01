package com.alal.notes.ui.lock

import com.alal.notes.data.prefs.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockGateStateTest {

    private val lockOff = Settings()
    private val lockOn = Settings(lockEnabled = true, lockPinHash = "hash", lockSalt = "salt", lockTimeoutSec = 30)

    @Test
    fun coldStartWithLockEnabledIsLocked() {
        // Before and after the first loaded settings: never a frame of content.
        assertTrue(LockGateState().isLocked(lockOn))
        assertTrue(LockGateState().onSettings(lockOn).isLocked(lockOn))
    }

    @Test
    fun lockEnabledWithoutPinDoesNotLock() {
        val noPin = lockOn.copy(lockPinHash = "")
        assertFalse(LockGateState().onSettings(noPin).isLocked(noPin))
    }

    @Test
    fun enteringThePinUnlocks() {
        val state = LockGateState().onSettings(lockOn).onUnlocked()
        assertFalse(state.isLocked(lockOn))
    }

    @Test
    fun turningTheLockOnInSettingsKeepsTheUserIn() {
        val state = LockGateState().onSettings(lockOff).onSettings(lockOn)
        assertFalse(state.isLocked(lockOn))
    }

    @Test
    fun turningTheLockOffLetsTheUserThrough() {
        val state = LockGateState().onSettings(lockOn).onSettings(lockOff)
        assertFalse(state.isLocked(lockOff))
    }

    @Test
    fun relocksOnlyAfterLockTimeoutInBackground() {
        val unlocked = LockGateState().onSettings(lockOn).onUnlocked()

        val shortTrip = unlocked.onStop(now = 1_000, changingConfigurations = false).onStart(lockOn, now = 1_000 + 29_999)
        assertFalse(shortTrip.isLocked(lockOn))

        val longTrip = unlocked.onStop(now = 1_000, changingConfigurations = false).onStart(lockOn, now = 1_000 + 30_000)
        assertTrue(longTrip.isLocked(lockOn))
    }

    @Test
    fun timeoutZeroRelocksOnEveryReturn() {
        val immediately = lockOn.copy(lockTimeoutSec = 0)
        val state = LockGateState().onSettings(immediately).onUnlocked()
            .onStop(now = 5_000, changingConfigurations = false)
            .onStart(immediately, now = 5_001)
        assertTrue(state.isLocked(immediately))
    }

    @Test
    fun configurationChangeIsNotLeavingTheApp() {
        val immediately = lockOn.copy(lockTimeoutSec = 0)
        val state = LockGateState().onSettings(immediately).onUnlocked()
            .onStop(now = 5_000, changingConfigurations = true)
            .onStart(immediately, now = 60_000)
        assertFalse(state.isLocked(immediately))
    }

    @Test
    fun startWithoutLeavingDoesNotRelock() {
        val state = LockGateState().onSettings(lockOn).onUnlocked().onStart(lockOn, now = 99_000)
        assertFalse(state.isLocked(lockOn))
    }
}
