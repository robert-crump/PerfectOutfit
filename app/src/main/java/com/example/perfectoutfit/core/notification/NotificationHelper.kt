package com.example.perfectoutfit.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.example.perfectoutfit.MainActivity
import com.example.perfectoutfit.R
import com.example.perfectoutfit.core.model.Sport
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "rating_reminders"
        const val CHANNEL_NAME = "Rating Reminders"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reminders to rate your outfit after exercise"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showRatingNotification(
        outfitEntryId: Long,
        sport: Sport,
        dateMs: Long,
        durationHours: Int
    ) {
        val deepLinkIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("perfectoutfit://rate?outfitEntryId=$outfitEntryId"),
            context,
            MainActivity::class.java
        )

        val pendingIntent = PendingIntent.getActivity(
            context,
            outfitEntryId.toInt(),
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sportName = sport.name.lowercase().replaceFirstChar { it.uppercase() }
        val dateStr = SimpleDateFormat("MMMM d", Locale.getDefault()).format(Date(dateMs))

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Rate your outfit ($sportName)")
            .setContentText("Tap to rate your outfit for $dateStr. Workout duration: ${durationHours}h.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(outfitEntryId.toInt(), notification)
    }
}
