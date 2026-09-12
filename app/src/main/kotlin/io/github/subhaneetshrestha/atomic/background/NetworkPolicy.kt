package io.github.subhaneetshrestha.atomic.background

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager

/** Why a run of the background engine did nothing. Each one is shown on the settings screen. */
enum class SkipReason {
    NO_NETWORK,
    METERED,
    DATA_SAVER,
    POWER_SAVE,
}

/**
 * Whether the launcher may go to the network for a wallpaper right now. Fetching a picture nobody
 * asked for is the least important thing the phone is doing, so it gives way to everything: a
 * metered connection, Data Saver, battery saver.
 *
 * The scheduled runs also carry `setRequiresBatteryNotLow`, so a low battery is refused by
 * JobScheduler before this is ever consulted.
 */
class NetworkPolicy(
    context: Context,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    /**
     * [opportunistic] is a run the launcher started itself because time had passed, as opposed to
     * a scheduled one or one the user asked for by tapping; those are the ones battery saver stops.
     */
    fun skipReason(
        unmeteredOnly: Boolean,
        opportunistic: Boolean,
    ): SkipReason? {
        if (!hasInternet()) return SkipReason.NO_NETWORK
        val metered = connectivity?.isActiveNetworkMetered ?: true
        if (metered && dataSaverOn()) return SkipReason.DATA_SAVER
        if (metered && unmeteredOnly) return SkipReason.METERED
        if (opportunistic && power?.isPowerSaveMode == true) return SkipReason.POWER_SAVE
        return null
    }

    private fun hasInternet(): Boolean {
        val manager = connectivity ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun dataSaverOn(): Boolean =
        connectivity?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
}
