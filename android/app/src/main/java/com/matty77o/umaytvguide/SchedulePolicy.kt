package com.matty77o.umaytvguide

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

object SchedulePolicy {
    const val PREF_DATE = "shared_tv_schedule_date"
    const val PREF_END_TIME = "schedule_end_time"
    const val DEFAULT_END_TIME = "17:00"

    fun todayKey(): String = LocalDate.now().toString()

    fun endTime(context: Context): LocalTime {
        val prefs = context.getSharedPreferences("umay_tv_guide", Context.MODE_PRIVATE)
        return runCatching {
            LocalTime.parse(prefs.getString(PREF_END_TIME, DEFAULT_END_TIME) ?: DEFAULT_END_TIME)
        }.getOrDefault(LocalTime.of(17, 0))
    }

    fun endTimeText(context: Context): String = endTime(context).toString().take(5)

    fun isScheduleForToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences("umay_tv_guide", Context.MODE_PRIVATE)
        return prefs.getString(PREF_DATE, null) == todayKey()
    }

    fun markToday(context: Context) {
        context.getSharedPreferences("umay_tv_guide", Context.MODE_PRIVATE)
            .edit().putString(PREF_DATE, todayKey()).apply()
    }
}

object ScheduleDayScheduler {
    private const val REQUEST_END_TIME = 61001
    private const val REQUEST_NEW_DAY = 61002

    fun schedule(context: Context) {
        scheduleEndTime(context)
        scheduleNewDay(context)
    }

    fun scheduleEndTime(context: Context) {
        val now = ZonedDateTime.now()
        val end = SchedulePolicy.endTime(context)
        var whenAt = now.with(end).withSecond(0).withNano(0)
        if (!whenAt.isAfter(now)) whenAt = whenAt.plusDays(1)
        setAlarm(context, REQUEST_END_TIME, ScheduleFollowUpReceiver.ACTION_AUTO_TV_OFF, whenAt)
    }

    fun scheduleNewDay(context: Context) {
        val now = ZonedDateTime.now()
        val whenAt = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusSeconds(3)
        setAlarm(context, REQUEST_NEW_DAY, ScheduleFollowUpReceiver.ACTION_CLEAR_NEW_DAY, whenAt)
    }

    private fun setAlarm(context: Context, requestCode: Int, action: String, whenAt: ZonedDateTime) {
        val intent = Intent(context, ScheduleFollowUpReceiver::class.java).apply { this.action = action }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val millis = whenAt.toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }
}
