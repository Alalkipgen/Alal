package com.alal.notes.ui.lock

import com.alal.notes.data.prefs.Settings

/**
 * The app-lock decision as plain Kotlin, so it can be unit tested without Compose.
 *
 * [unlocked] is null until the first loaded [Settings] are seen in this process. With the lock
 * on, that means a cold start (or a restored process) always begins locked. If the lock was off
 * when settings arrived, the user is let in, and turning the lock on later in Settings keeps
 * them in until they leave the app for longer than the timeout.
 */
data class LockGateState(
    val unlocked: Boolean? = null,
    /** Wall-clock millis when the app went to the background, or 0 when it has not. */
    val leftAt: Long = 0L,
) {
    /** True while the unlock surface must cover the app. */
    fun isLocked(settings: Settings): Boolean = settings.appLockOn && unlocked != true

    /** Call whenever loaded settings are (re)applied. */
    fun onSettings(settings: Settings): LockGateState = when {
        !settings.appLockOn -> copy(unlocked = true)
        unlocked == null -> copy(unlocked = false)
        else -> this
    }

    /** The app left the screen. A configuration change (activity recreation) is not leaving. */
    fun onStop(now: Long, changingConfigurations: Boolean): LockGateState =
        if (changingConfigurations) this else copy(leftAt = now)

    /** The app is visible again: re-lock if it was away for at least `lockTimeoutSec`. */
    fun onStart(settings: Settings, now: Long): LockGateState {
        if (leftAt <= 0L) return this
        val timedOut = now - leftAt >= settings.lockTimeoutSec * 1000L
        val relock = unlocked == true && settings.appLockOn && timedOut
        return copy(unlocked = if (relock) false else unlocked, leftAt = 0L)
    }

    fun onUnlocked(): LockGateState = copy(unlocked = true)
}

private val Settings.appLockOn: Boolean
    get() = lockEnabled && lockPinHash.isNotBlank()
