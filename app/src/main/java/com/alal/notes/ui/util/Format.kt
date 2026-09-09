package com.alal.notes.ui.util

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Format {
    private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)

    /** 2340 -> "2,340" */
    fun number(n: Int): String = numberFormat.format(n)

    /** 12800 -> "12.8k" */
    fun compact(n: Int): String = when {
        n < 1000 -> n.toString()
        n < 100_000 -> String.format(Locale.US, "%.1fk", n / 1000.0).replace(".0k", "k")
        else -> "${n / 1000}k"
    }

    /** "13:30" for today, "Sep 1" for this year, "Sep 1, 2025" otherwise. */
    fun relative(timestamp: Long, is24h: Boolean, locale: Locale = Locale.getDefault()): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = timestamp }
        val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        val pattern = when {
            sameDay -> if (is24h) "HH:mm" else "h:mm a"
            sameYear -> "MMM d"
            else -> "MMM d, yyyy"
        }
        return java.text.SimpleDateFormat(pattern, locale).format(Date(timestamp))
    }

    fun full(timestamp: Long, locale: Locale = Locale.getDefault()): String =
        java.text.SimpleDateFormat("MMM d, yyyy · HH:mm", locale).format(Date(timestamp))
}

@Composable
fun rememberIs24Hour(): Boolean = DateFormat.is24HourFormat(LocalContext.current)

@Composable
fun rememberHaptics(): Haptics = Haptics(LocalHapticFeedback.current)

class Haptics(private val feedback: HapticFeedback) {
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
}
