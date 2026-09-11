package com.matty77o.umaytvguide

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val prefs = context.getSharedPreferences("umay_tv_guide", Context.MODE_PRIVATE)
        BackgroundRefreshManager.configure(context, prefs.getBoolean("auto_refresh", true))
        HouseholdScheduleSyncManager.configure(context)
        ScheduleDayScheduler.schedule(context)

        val pending = goAsync()
        thread {
            try {
                val guide = XmlTvRepository.loadCached(
                    context,
                    "https://raw.githubusercontent.com/Matty77o/tv-iptv/main/guide.xml",
                    "guide-cache.xml"
                )
                val favourites = prefs.getStringSet("favourite_show_titles", emptySet())
                    ?.toSet().orEmpty()
                ProgrammeReminderScheduler.scheduleAll(
                    context = context,
                    favouriteTitles = favourites,
                    programmes = guide.programmes,
                    channels = guide.channels,
                )
            } catch (_: Throwable) {
                // The app/worker will retry when connectivity is available.
            } finally {
                pending.finish()
            }
        }
    }
}
