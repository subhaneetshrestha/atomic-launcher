package io.github.subhaneetshrestha.atomic.background

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import io.github.subhaneetshrestha.atomic.core.theme.CollectionConfig
import io.github.subhaneetshrestha.atomic.util.Logs

/**
 * Asks Android to run the background engine now and then. The job is not persisted across a
 * reboot — that would need a permission the launcher does not declare — so it is set up again
 * every time the process starts, which for a home app is every boot anyway.
 *
 * Android decides when a periodic job actually runs, and will not run one more often than
 * [JobScheduler.getMinPeriodMillis]; the launcher also refreshes on its own when the home screen
 * comes back and enough time has passed, which covers a phone that has decided the app is idle.
 */
object BackgroundScheduler {
    const val JOB_ID = 4201

    fun apply(
        context: Context,
        config: CollectionConfig,
        enabled: Boolean,
    ) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (!enabled || !config.isConfigured) {
            if (scheduler.pending() != null) Logs.d(TAG) { "background job cancelled" }
            scheduler.cancel(JOB_ID)
            return
        }
        val period = periodMs(config.intervalMinutes)
        val network = if (config.unmeteredOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY
        val existing = scheduler.pending()
        if (existing != null && existing.intervalMillis == period && existing.networkType == network) return
        val job =
            JobInfo
                .Builder(JOB_ID, ComponentName(context, BackgroundJobService::class.java))
                .setPeriodic(period, period / FLEX_DIVISOR)
                .setRequiredNetworkType(network)
                .setRequiresBatteryNotLow(true)
                .apply { if (Build.VERSION.SDK_INT >= 28) setPrefetch(true) }
                .build()
        val result = scheduler.schedule(job)
        Logs.d(TAG) { "background job every ${period / 60_000} min, network $network, result $result" }
    }

    private fun JobScheduler.pending(): JobInfo? = getPendingJob(JOB_ID)

    private fun periodMs(intervalMinutes: Int): Long {
        val wanted = intervalMinutes * 60_000L
        val floor = if (Build.VERSION.SDK_INT >= 30) JobInfo.getMinPeriodMillis() else LEGACY_MIN_PERIOD_MS
        return maxOf(wanted, floor)
    }

    /** Before Android 11 the floor was not public, but it has been fifteen minutes since Nougat. */
    private const val LEGACY_MIN_PERIOD_MS = 15 * 60_000L

    private const val FLEX_DIVISOR = 5

    private const val TAG = "BackgroundScheduler"
}
