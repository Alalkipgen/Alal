package com.alal.notes

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.alal.notes.data.work.AutoBackupWorker
import com.alal.notes.data.work.ReminderWorker
import com.alal.notes.data.work.TrashPurgeWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AlalApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        TrashPurgeWorker.schedule(this)
        AutoBackupWorker.schedule(this)
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannelCompat.Builder(ReminderWorker.CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(getString(R.string.notif_channel_reminders))
            .setDescription(getString(R.string.notif_channel_reminders_desc))
            .setVibrationEnabled(true)
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }
}
