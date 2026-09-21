package com.example.perfectoutfit.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.perfectoutfit.MainActivity
import com.example.perfectoutfit.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBackupFailureNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) : BackupFailureNotifier {
    companion object {
        const val CHANNEL_ID = "backup_failures"
        const val CHANNEL_NAME = "Backup Failures"
        private const val NOTIFICATION_ID = 1001
    }

    override fun showFailure() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Alerts when the automatic Google Drive backup fails"
            }
        )

        val openSettings = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_SETTINGS
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openSettings,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.backup_failed_title))
            .setContentText(context.getString(R.string.backup_failed_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun clear() {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
