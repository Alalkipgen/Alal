package com.alal.notes.ui.util

import android.os.Build
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Format {
    private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)

    // Date formatting happens once per visible note card, so the formatters (and the
    // Calendar used to pick a pattern) are cached instead of re-allocated every frame.
    private val formatters = HashMap<String, SimpleDateFormat>(8)
    private val calendarA = Calendar.getInstance()
    private val calendarB = Calendar.getInstance()

    @Synchronized
    private fun formatter(pattern: String, locale: Locale): SimpleDateFormat {
        val key = pattern + '|' + locale.toLanguageTag()
        return formatters.getOrPut(key) { SimpleDateFormat(pattern, locale) }
    }

    /** 2340 -> "2,340" */
    fun number(n: Int): String = numberFormat.format(n)

    /** 12800 -> "12.8k" */
    fun compact(n: Int): String = when {
        n < 1000 -> n.toString()
        n < 100_000 -> String.format(Locale.US, "%.1fk", n / 1000.0).replace(".0k", "k")
        else -> "${n / 1000}k"
    }

    /** "13:30" for today, "Sep 1" for this year, "Sep 1, 2025" otherwise. */
    @Synchronized
    fun relative(timestamp: Long, is24h: Boolean, locale: Locale = Locale.getDefault()): String {
        val now = calendarA.apply { timeInMillis = System.currentTimeMillis() }
        val then = calendarB.apply { timeInMillis = timestamp }
        val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        val sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        val pattern = when {
            sameDay -> if (is24h) "HH:mm" else "h:mm a"
            sameYear -> "MMM d"
            else -> "MMM d, yyyy"
        }
        return formatter(pattern, locale).format(Date(timestamp))
    }

    fun full(timestamp: Long, locale: Locale = Locale.getDefault()): String =
        formatter("MMM d, yyyy · HH:mm", locale).format(Date(timestamp))
}

@Composable
fun rememberIs24Hour(): Boolean = DateFormat.is24HourFormat(LocalContext.current)

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

/**
 * Thin wrapper over the platform haptic constants. Using the View API (instead of the Compose
 * one) lets us pick the richer feedback types that Android 11+ exposes and degrade gracefully
 * on older devices. Every call is ignored by the system when the user turned haptics off.
 *
 * Feedback is graded: [light] for high-frequency taps (toolbar, keys), [tick] for chips and
 * steps, [select] for toggles, [confirm] for gestures that change state, [success] / [warn]
 * for outcomes. Rapid repeats are throttled so a burst of taps or a pinch never turns into a
 * continuous buzz - that "muddy" feel was the main complaint about the old implementation.
 */
class Haptics(private val view: View) {

    private var lastAt = 0L
    private var lastConstant = -1

    private fun play(constant: Int, minGapMs: Long) {
        val now = SystemClock.uptimeMillis()
        if (minGapMs > 0 && constant == lastConstant && now - lastAt < minGapMs) return
        lastAt = now
        lastConstant = constant
        view.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    }

    /** Very light key-press feel: formatting toolbar, keyboard-like buttons. */
    fun light() = play(HapticFeedbackConstants.KEYBOARD_TAP, 35L)

    /** Light tick: chip taps, moving through a list, discrete steps. */
    fun tick() = play(HapticFeedbackConstants.CLOCK_TICK, 35L)

    /** Pinch-zoom / slider step. Uses the fine "segment" tick where available. */
    fun step() = play(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        else HapticFeedbackConstants.CLOCK_TICK,
        60L,
    )

    /** Selection changed: switching tab, toggling an option. */
    fun select() = play(HapticFeedbackConstants.CONTEXT_CLICK, 50L)

    /** A destructive or long-press gesture became active. */
    fun confirm() = play(HapticFeedbackConstants.LONG_PRESS, 80L)

    /** An action completed successfully (save, export, restore). */
    fun success() = play(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.LONG_PRESS,
        120L,
    )

    /** An action was refused (empty field, locked note, failed import). */
    fun warn() = play(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS,
        120L,
    )
}
