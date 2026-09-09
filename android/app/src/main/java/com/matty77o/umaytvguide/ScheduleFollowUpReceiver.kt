package com.matty77o.umaytvguide

import android.Manifest
import android.app.AlarmManager
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime
import kotlin.concurrent.thread

object ScheduleFollowUpScheduler {
    fun schedule(
        context: Context,
        household: String,
        memberName: String,
        title: String,
        channel: String,
        language: String,
        stop: ZonedDateTime,
        nextTitle: String,
        nextStart: String,
        nextStop: String,
    ) {
        if (!stop.isAfter(ZonedDateTime.now())) return
        val promptId = "${stop.toInstant().toEpochMilli()}:${channel}:${title}".take(120)
        val intent = Intent(context, ScheduleFollowUpReceiver::class.java).apply {
            action = ScheduleFollowUpReceiver.ACTION_PROMPT
            putExtra(ScheduleFollowUpReceiver.EXTRA_HOUSEHOLD, household)
            putExtra(ScheduleFollowUpReceiver.EXTRA_MEMBER, memberName)
            putExtra(ScheduleFollowUpReceiver.EXTRA_PROMPT_ID, promptId)
            putExtra(ScheduleFollowUpReceiver.EXTRA_TITLE, title)
            putExtra(ScheduleFollowUpReceiver.EXTRA_CHANNEL, channel)
            putExtra(ScheduleFollowUpReceiver.EXTRA_LANGUAGE, language)
            putExtra(ScheduleFollowUpReceiver.EXTRA_NEXT_TITLE, nextTitle)
            putExtra(ScheduleFollowUpReceiver.EXTRA_NEXT_START, nextStart)
            putExtra(ScheduleFollowUpReceiver.EXTRA_NEXT_STOP, nextStop)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            promptId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val whenMillis = stop.toInstant().toEpochMilli() + 5_000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pi)
        }
    }
}

class ScheduleFollowUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        when (intent.action) {
            ACTION_PROMPT -> showPrompt(context, intent)
            ACTION_YES -> handleAnswer(context, intent, FollowUpAnswer.CHANGED)
            ACTION_NO -> handleAnswer(context, intent, FollowUpAnswer.STILL_WATCHING)
            ACTION_TV_OFF -> handleAnswer(context, intent, FollowUpAnswer.TV_OFF)
            ACTION_RECONCILE -> reconcilePrompt(context, intent)
        }
    }

    private fun showPrompt(context: Context, source: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)
        val title = source.getStringExtra(EXTRA_TITLE).orEmpty()
        val channel = source.getStringExtra(EXTRA_CHANNEL).orEmpty()
        val promptId = source.getStringExtra(EXTRA_PROMPT_ID).orEmpty()
        val household = source.getStringExtra(EXTRA_HOUSEHOLD).orEmpty()
        if (household.length >= 6) thread { runCatching { sendFollowUpState(household, promptId, "pending", source.getStringExtra(EXTRA_MEMBER).orEmpty(), title, channel) } }

        fun actionIntent(action: String): PendingIntent {
            val i = Intent(context, ScheduleFollowUpReceiver::class.java).apply {
                this.action = action
                source.extras?.let { putExtras(it) }
            }
            return PendingIntent.getBroadcast(context, (promptId + action).hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val body = if (channel.isBlank()) "What is the TV doing now?" else "$title has finished on $channel. Are you still watching $channel, did you change channel, or is the TV off?"
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("What happened after the programme?")
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setOngoing(false)
            .addAction(Notification.Action.Builder(null, "Changed channel", actionIntent(ACTION_YES)).build())
            .addAction(Notification.Action.Builder(null, "Still watching", actionIntent(ACTION_NO)).build())
            .addAction(Notification.Action.Builder(null, "TV is off", actionIntent(ACTION_TV_OFF)).build())
            .build()
        manager.notify(promptId.hashCode(), notification)
        scheduleReconcile(context, source, 1)
    }

    private fun scheduleReconcile(context: Context, source: Intent, attempt: Int) {
        if (attempt > 8) return
        val promptId = source.getStringExtra(EXTRA_PROMPT_ID).orEmpty()
        val intent = Intent(context, ScheduleFollowUpReceiver::class.java).apply {
            action = ACTION_RECONCILE
            source.extras?.let { putExtras(it) }
            putExtra(EXTRA_RECONCILE_ATTEMPT, attempt)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            (promptId + ":reconcile:" + attempt).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val whenMillis = System.currentTimeMillis() + 15_000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pi)
        }
    }

    private fun reconcilePrompt(context: Context, source: Intent) {
        val pendingResult = goAsync()
        thread(name = "UmayScheduleReconcile") {
            try {
                val household = source.getStringExtra(EXTRA_HOUSEHOLD).orEmpty()
                val promptId = source.getStringExtra(EXTRA_PROMPT_ID).orEmpty()
                val me = source.getStringExtra(EXTRA_MEMBER).orEmpty()
                val attempt = source.getIntExtra(EXTRA_RECONCILE_ATTEMPT, 1)
                if (household.length < 6 || promptId.isBlank()) return@thread
                val state = runCatching { getFollowUpState(household, promptId) }.getOrNull() ?: run {
                    scheduleReconcile(context, source, attempt + 1)
                    return@thread
                }
                val status = state.optString("status")
                if (status == "yes" || status == "no" || status == "off") {
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.cancel(promptId.hashCode())
                    val by = state.optString("by", "the other phone")
                    if (by.isNotBlank() && !by.equals(me, ignoreCase = true)) {
                        showInfo(context, "No need", "$by has already answered and the shared schedule was adjusted.")
                    }
                } else {
                    scheduleReconcile(context, source, attempt + 1)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private enum class FollowUpAnswer { CHANGED, STILL_WATCHING, TV_OFF }

    private fun handleAnswer(context: Context, source: Intent, answer: FollowUpAnswer) {
        val pendingResult = goAsync()
        thread(name = "UmayScheduleFollowUp") {
            try {
                val household = source.getStringExtra(EXTRA_HOUSEHOLD).orEmpty()
                val member = source.getStringExtra(EXTRA_MEMBER).orEmpty().ifBlank { "Someone" }
                val promptId = source.getStringExtra(EXTRA_PROMPT_ID).orEmpty()
                val title = source.getStringExtra(EXTRA_TITLE).orEmpty()
                val channel = source.getStringExtra(EXTRA_CHANNEL).orEmpty()
                val result = if (household.length >= 6) runCatching {
                    val status = when (answer) {
                        FollowUpAnswer.CHANGED -> "yes"
                        FollowUpAnswer.STILL_WATCHING -> "no"
                        FollowUpAnswer.TV_OFF -> "off"
                    }
                    sendFollowUpState(household, promptId, status, member, title, channel)
                }.getOrNull() else null

                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(promptId.hashCode())

                if (result?.optBoolean("alreadyHandled") == true) {
                    val by = result.optString("by", "the other phone")
                    showInfo(context, "No need", "$by has already answered and the shared schedule was adjusted.")
                    return@thread
                }

                if (answer == FollowUpAnswer.CHANGED) {
                    context.getSharedPreferences("umay_tv_guide", Context.MODE_PRIVATE).edit()
                        .putBoolean("open_schedule_once", true)
                        .putBoolean("schedule_choose_now", true).apply()
                    val open = PendingIntent.getActivity(
                        context, promptId.hashCode(),
                        Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    ensureChannel(manager)
                    manager.notify(
                        (promptId + "choose").hashCode(),
                        Notification.Builder(context, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle("What's playing now?")
                            .setContentText("Open Schedule and tap the programme that is currently on.")
                            .setContentIntent(open)
                            .setAutoCancel(true)
                            .build()
                    )
                } else if (answer == FollowUpAnswer.STILL_WATCHING) {
                    val nextTitle = source.getStringExtra(EXTRA_NEXT_TITLE).orEmpty()
                    if (nextTitle.isNotBlank()) {
                        appendNextProgramme(
                            context = context,
                            household = household,
                            time = source.getStringExtra(EXTRA_NEXT_START).orEmpty(),
                            stop = source.getStringExtra(EXTRA_NEXT_STOP).orEmpty(),
                            title = nextTitle,
                            channel = channel,
                            language = source.getStringExtra(EXTRA_LANGUAGE).orEmpty(),
                        )
                        showInfo(context, "Schedule updated", "$nextTitle was added next on $channel.")
                    } else {
                        showInfo(context, "Still watching $channel", "No next programme data was available, so nothing extra was added.")
                    }
                } else {
                    showInfo(context, "TV marked as off", "Nothing else was added to the schedule after $title.")
                }
            } finally { pendingResult.finish() }
        }
    }

    private fun appendNextProgramme(context: Context, household: String, time: String, stop: String, title: String, channel: String, language: String) {
        val prefs = context.getSharedPreferences("umay_tv_guide", Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(prefs.getString("shared_tv_schedule", "[]") ?: "[]") }.getOrDefault(JSONArray())
        val duplicate = (0 until arr.length()).any { i -> arr.optJSONObject(i)?.let { it.optString("title") == title && it.optString("time") == time } == true }
        if (!duplicate) arr.put(JSONObject().apply {
            put("time", time); put("stop", stop); put("title", title); put("channel", channel); put("language", language)
        })
        prefs.edit().putString("shared_tv_schedule", arr.toString()).apply()
        if (household.length >= 6) runCatching {
            val c = (URL("https://umay-tv-ai.matthewwood406.workers.dev/api/schedule?household=$household").openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"; doOutput = true; connectTimeout = 10_000; readTimeout = 15_000; setRequestProperty("Content-Type", "application/json")
            }
            val body = JSONObject().put("entries", arr).toString().toByteArray()
            c.outputStream.use { it.write(body) }
            c.inputStream.close(); c.disconnect()
        }
    }

    private fun getFollowUpState(household: String, promptId: String): JSONObject {
        val encodedPrompt = java.net.URLEncoder.encode(promptId, "UTF-8")
        val c = (URL("https://umay-tv-ai.matthewwood406.workers.dev/api/schedule-followup?household=$household&promptId=$encodedPrompt").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 8_000; readTimeout = 12_000
        }
        val text = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        return JSONObject(text)
    }

    private fun sendFollowUpState(household: String, promptId: String, status: String, member: String, title: String, channel: String): JSONObject {
        val c = (URL("https://umay-tv-ai.matthewwood406.workers.dev/api/schedule-followup?household=$household").openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"; doOutput = true; connectTimeout = 8_000; readTimeout = 12_000; setRequestProperty("Content-Type", "application/json")
        }
        val body = JSONObject().apply {
            put("promptId", promptId); put("status", status); put("by", member); put("title", title); put("channel", channel)
        }.toString().toByteArray()
        c.outputStream.use { it.write(body) }
        val text = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        return JSONObject(text)
    }

    private fun showInfo(context: Context, title: String, body: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)
        manager.notify((title + body).hashCode(), Notification.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(body).setAutoCancel(true).build())
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Shared TV schedule", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "One prompt when a scheduled programme finishes"
            })
        }
    }

    companion object {
        const val ACTION_PROMPT = "com.matty77o.umaytvguide.SCHEDULE_PROMPT"
        const val ACTION_YES = "com.matty77o.umaytvguide.SCHEDULE_YES"
        const val ACTION_NO = "com.matty77o.umaytvguide.SCHEDULE_NO"
        const val ACTION_TV_OFF = "com.matty77o.umaytvguide.SCHEDULE_TV_OFF"
        const val ACTION_RECONCILE = "com.matty77o.umaytvguide.SCHEDULE_RECONCILE"
        const val EXTRA_HOUSEHOLD = "household"
        const val EXTRA_MEMBER = "member"
        const val EXTRA_PROMPT_ID = "prompt_id"
        const val EXTRA_TITLE = "schedule_title"
        const val EXTRA_CHANNEL = "schedule_channel"
        const val EXTRA_LANGUAGE = "schedule_language"
        const val EXTRA_NEXT_TITLE = "next_title"
        const val EXTRA_NEXT_START = "next_start"
        const val EXTRA_NEXT_STOP = "next_stop"
        const val EXTRA_RECONCILE_ATTEMPT = "reconcile_attempt"
        private const val CHANNEL_ID = "shared_tv_schedule"
    }
}
