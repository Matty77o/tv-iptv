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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

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
        val startEpoch = intent.getLongExtra(EXTRA_START_EPOCH, -1L)
        val channelIconUrl = intent.getStringExtra(EXTRA_CHANNEL_ICON_URL)
        val programmeIconUrl = intent.getStringExtra(EXTRA_PROGRAMME_ICON_URL)

        val pendingResult = goAsync()

        thread(name = "UmayTVGuideNotification") {
            try {
                val largeIcon = channelIconUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::downloadBitmap)
                val programmeArtwork = programmeIconUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::downloadBitmap)

                showNotification(
                    context = context,
                    title = title,
                    channel = channel,
                    kind = kind,
                    startTime = startTime,
                    startEpoch = startEpoch,
                    largeIcon = largeIcon,
                    programmeArtwork = programmeArtwork,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(
        context: Context,
        title: String,
        channel: String,
        kind: String,
        startTime: String,
        startEpoch: Long,
        largeIcon: Bitmap?,
        programmeArtwork: Bitmap?,
    ) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_PROGRAMME, true)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_CHANNEL, channel)
            putExtra(EXTRA_START_EPOCH, startEpoch)
        }

        val openApp = PendingIntent.getActivity(
            context,
            (title + channel + startEpoch).hashCode(),
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
            body = if (channel.isNotBlank()) {
                "Now on $channel"
            } else {
                "Your favourite show is starting now"
            }
        }

        val builder = Notification.Builder(context, CHANNEL_ID)
            // Android requires the status-bar small icon to be a normal app resource.
            // The colourful channel logo is shown as the large notification icon.
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(heading)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }
        if (programmeArtwork != null) {
            builder.setStyle(
                Notification.BigPictureStyle()
                    .bigPicture(programmeArtwork)
                    .bigLargeIcon(largeIcon)
            )
        }

        val notificationId = (title + startEpoch + kind).hashCode()
        manager.notify(notificationId, builder.build())
    }

    private fun downloadBitmap(url: String): Bitmap? {
        var connection: HttpURLConnection? = null

        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 7_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "UmayTVGuide/2.0")
            connection.connect()

            if (connection.responseCode !in 200..299) {
                return null
            }

            connection.inputStream.use(BitmapFactory::decodeStream)
        } catch (_: Throwable) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Favourite show reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description =
                    "Alerts 10 minutes before favourite shows and when they start"
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_KIND = "kind"
        const val EXTRA_START_TIME = "start_time"
        const val EXTRA_START_EPOCH = "start_epoch"
        const val EXTRA_CHANNEL_ICON_URL = "channel_icon_url"
        const val EXTRA_PROGRAMME_ICON_URL = "programme_icon_url"
        const val EXTRA_OPEN_PROGRAMME = "open_programme"

        const val KIND_SOON = "soon"
        const val KIND_NOW = "now"

        private const val CHANNEL_ID = "favourite_show_reminders"
    }
}
