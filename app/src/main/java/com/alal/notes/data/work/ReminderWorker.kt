package com.alal.notes.data.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.alal.notes.MainActivity
import com.alal.notes.R
import com.alal.notes.data.repository.NoteRepository
import com.alal.notes.domain.markdown.MarkdownStripper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** Fires a local notification for a note reminder, then clears the reminder on the note. */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: NoteRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val noteId = inputData.getLong(KEY_NOTE_ID, -1L)
        if (noteId <= 0) return Result.failure()
        val note = repository.getNote(noteId) ?: return Result.success()
        val at = note.reminderAt ?: return Result.success()
        if (note.isTrashed) { repository.setReminder(noteId, null); return Result.success() }
        // Woken up early (e.g. reminder was moved later): reschedule instead of firing.
        val now = System.currentTimeMillis()
        if (at - now > 60_000L) { ReminderScheduler.enqueue(applicationContext, noteId, at); return Result.success() }

        val ctx = applicationContext
        val nm = NotificationManagerCompat.from(ctx)
        if (nm.areNotificationsEnabled()) {
            val open = Intent(ctx, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_NOTE)
                .putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(
                ctx, noteId.toInt(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val title = note.title.ifBlank { ctx.getString(R.string.untitled) }
            val preview = if (note.isLocked) ctx.getString(R.string.locked_note) else MarkdownStripper.strip(note.body).take(160)
            val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(preview.ifBlank { ctx.getString(R.string.reminder) })
                .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
            try {
                nm.notify(noteId.toInt(), n)
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS revoked on Android 13+
            }
        }
        repository.setReminder(noteId, null)
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "reminders"
        const val KEY_NOTE_ID = "noteId"
    }
}

/** Schedules/cancels reminder work. WorkManager survives reboots, so no BOOT_COMPLETED receiver is needed. */
@Singleton
class ReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    fun schedule(noteId: Long, at: Long) = enqueue(context, noteId, at)

    fun cancel(noteId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(noteId))
    }

    companion object {
        fun uniqueName(noteId: Long) = "reminder_$noteId"

        fun enqueue(context: Context, noteId: Long, at: Long) {
            val delay = max(0L, at - System.currentTimeMillis())
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(ReminderWorker.KEY_NOTE_ID to noteId))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(uniqueName(noteId), ExistingWorkPolicy.REPLACE, request)
        }
    }
}
