package com.matty77o.umaytvguide

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class ProgrammeReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val channel = intent.getStringExtra(EXTRA_CHANNEL).orEmpty()
        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_NOW
        val startTime = intent.getStringExtra(EXTRA_START_TIME).orEmpty()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openApp = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val heading: String
        val body: String
        if (kind == KIND_SOON) {
            heading = "$title starts in 10 minutes"
            body = if (channel.isNotBlank()) "$channel • $startTime" else startTime
        } else {
            heading = "$title is on now"
            body = if (channel.isNotBlank()) "Now on $channel" else "Your favourite show is starting now"
        }

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(heading)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()

        val notificationId = (title + startTime + kind).hashCode()
        manager.notify(notificationId, notification)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Favourite show reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts 10 minutes before favourite shows and when they start"
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_KIND = "kind"
        const val EXTRA_START_TIME = "start_time"

        const val KIND_SOON = "soon"
        const val KIND_NOW = "now"

        private const val CHANNEL_ID = "favourite_show_reminders"
    }
}
