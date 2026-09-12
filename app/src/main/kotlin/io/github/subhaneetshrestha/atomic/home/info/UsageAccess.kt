package io.github.subhaneetshrestha.atomic.home.info

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import io.github.subhaneetshrestha.atomic.util.Logs
import java.time.LocalDate
import java.time.ZoneId

/**
 * Today's screen time, and the access it needs. Usage access is not a permission an app may ask
 * for: the user grants it on a page of its own in Settings, and this reads whether they have.
 *
 * Nothing is stored. The total is worked out again from Android's own events each time it is
 * shown, and the launcher's own time is left out of it.
 */
object UsageAccess {
    fun isGranted(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode =
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    ops.unsafeCheckOpNoThrow(OP, Process.myUid(), context.packageName)
                } else {
                    @Suppress("DEPRECATION")
                    ops.checkOpNoThrow(OP, Process.myUid(), context.packageName)
                }
            } catch (e: SecurityException) {
                return false
            }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun intents(): List<Intent> =
        listOf(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    /** Milliseconds the phone has been in an app today, or null when access has not been given. */
    fun todayMillis(
        context: Context,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        if (!isGranted(context)) return null
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val midnight =
            LocalDate
                .now(zone)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        val events =
            try {
                manager.queryEvents(midnight, now)
            } catch (e: RuntimeException) {
                Logs.w(TAG, "usage events would not come", e)
                return null
            } ?: return null
        val collected = ArrayList<ForegroundEvent>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val resumed =
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> true
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> false
                    else -> continue
                }
            collected += ForegroundEvent(event.packageName, event.timeStamp, resumed)
        }
        return ScreenTimeCalculator.total(collected, midnight, now, exclude = setOf(context.packageName))
    }

    /** `android:get_usage_stats`, which has no constant below Android 10. */
    private const val OP = "android:get_usage_stats"

    private const val TAG = "UsageAccess"
}
