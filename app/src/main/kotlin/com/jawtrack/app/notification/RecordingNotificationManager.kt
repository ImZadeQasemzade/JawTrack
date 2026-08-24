package com.jawtrack.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jawtrack.app.MainActivity
import com.jawtrack.app.R
import com.jawtrack.app.recording.RecordingService
import java.util.concurrent.TimeUnit

/**
 * The persistent notification doubles as an "is it still running?" check (§4.2): it shows
 * elapsed time and live episode count so the user can glance at the lock screen and know
 * the night is actually being captured.
 */
class RecordingNotificationManager(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_recording),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_recording_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(elapsedMillis: Long, episodeCount: Int): Notification {
        val elapsedText = formatElapsed(elapsedMillis)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notification_title_recording))
            .setContentText(context.getString(R.string.notification_text_recording_template, elapsedText, episodeCount))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.notification_stop_action), stopIntent)
            .build()
    }

    fun notify(elapsedMillis: Long, episodeCount: Int) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, build(elapsedMillis, episodeCount))
    }

    private fun formatElapsed(elapsedMillis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(elapsedMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis) % 60
        return "%dh %02dm".format(hours, minutes)
    }

    companion object {
        const val CHANNEL_ID = "jawtrack_recording"
        const val NOTIFICATION_ID = 1001
    }
}
