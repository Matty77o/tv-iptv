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
