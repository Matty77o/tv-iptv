package com.matty77o.umaytvguide

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class GuideRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val guide = XmlTvRepository.loadCached(
                applicationContext,
                "https://raw.githubusercontent.com/Matty77o/tv-iptv/main/guide.xml",
                "guide-cache.xml"
            )
            ChannelConfigRepository.loadCached(
                applicationContext,
                "https://raw.githubusercontent.com/Matty77o/tv-iptv/main/channels.json",
                "channels-cache.json"
            )

            val favourites = applicationContext
                .getSharedPreferences("umay_tv_guide", Context.MODE_PRIVATE)
                .getStringSet("favourite_show_titles", emptySet())
                ?.toSet()
                .orEmpty()

            ProgrammeReminderScheduler.scheduleAll(
                context = applicationContext,
                favouriteTitles = favourites,
                programmes = guide.programmes,
                channels = guide.channels,
            )
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}

object BackgroundRefreshManager {
    private const val WORK_NAME = "umay-tv-guide-background-refresh"

    fun configure(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<GuideRefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        manager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}

class HouseholdScheduleSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("umay_tv_guide", Context.MODE_PRIVATE)
        val household = prefs.getString("household_pairing_code", "").orEmpty()
            .uppercase().filter { it.isLetterOrDigit() }.take(12)
        if (household.length < 6) return Result.success()

        return try {
            val connection = java.net.URL("https://umay-tv-ai.matthewwood406.workers.dev/api/schedule?household=$household")
                .openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val entries = org.json.JSONObject(text).optJSONArray("entries") ?: org.json.JSONArray()
            prefs.edit().putString("shared_tv_schedule", entries.toString()).apply()

            val member = prefs.getString("household_member_name", "Someone").orEmpty().ifBlank { "Someone" }
            val zone = java.time.ZoneId.systemDefault()
            val today = java.time.LocalDate.now(zone)
            val now = java.time.ZonedDateTime.now(zone)
            for (i in 0 until entries.length()) {
                val e = entries.optJSONObject(i) ?: continue
                val stopText = e.optString("stop")
                if (!Regex("\\d{2}:\\d{2}").matches(stopText)) continue
                val stop = java.time.ZonedDateTime.of(today, java.time.LocalTime.parse(stopText), zone)
                if (!stop.isAfter(now)) continue
                ScheduleFollowUpScheduler.schedule(
                    context = applicationContext,
                    household = household,
                    memberName = member,
                    title = e.optString("title"),
                    channel = e.optString("channel"),
                    language = e.optString("language", "English"),
                    stop = stop,
                    nextTitle = e.optString("nextTitle"),
                    nextStart = e.optString("nextStart"),
                    nextStop = e.optString("nextStop"),
                )
            }
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}

object HouseholdScheduleSyncManager {
    private const val WORK_NAME = "umay-tv-household-schedule-sync"

    fun configure(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<HouseholdScheduleSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
