package io.github.subhaneetshrestha.atomic.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.LocaleList
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat
import io.github.subhaneetshrestha.atomic.util.Logs
import io.github.subhaneetshrestha.atomic.util.Threads
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The list of launchable apps, built from LauncherApps and kept fresh through its callbacks.
 *
 * Threading: every LauncherApps call and every snapshot write happens on [Threads.apps] (single
 * writer, no locks); listeners are notified on the main thread. Every binder call is guarded,
 * because a crashing home app silently loses its default status.
 */
class AppRepository(
    context: Context,
    private val profileSource: ProfileSource = ProfileSource.CurrentUserOnly,
) {
    /** Immutable view of the launchable apps. [loaded] is false until the first enumeration finishes. */
    class Snapshot(
        val entries: List<AppEntry>,
        val generation: Long,
        val loaded: Boolean,
    ) {
        companion object {
            val EMPTY = Snapshot(emptyList(), 0L, loaded = false)
        }
    }

    fun interface Listener {
        fun onSnapshot(snapshot: Snapshot)
    }

    private val appContext = context.applicationContext
    private val launcherApps: LauncherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager: UserManager = appContext.getSystemService(UserManager::class.java)
    private val users = ConcurrentHashMap<Long, UserHandle>()
    private val listeners = CopyOnWriteArrayList<Listener>()
    private var started = false

    @Volatile
    var current: Snapshot = Snapshot.EMPTY
        private set

    @Volatile
    private var lastLocales: String = ""

    /** Registers for package and locale changes and schedules the first enumeration. Main thread. */
    fun start() {
        check(Threads.isMainThread) { "start() must be called on the main thread" }
        if (started) return
        started = true
        launcherApps.registerCallback(callback, Threads.apps)
        ContextCompat.registerReceiver(
            appContext,
            localeReceiver,
            IntentFilter(Intent.ACTION_LOCALE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refresh()
    }

    /** Re-enumerates everything on the apps thread. */
    fun refresh() {
        Threads.apps.post { loadAll() }
    }

    /** Reloads if the locale list changed since the last snapshot (labels are locale dependent). */
    fun ensureFresh(locales: LocaleList) {
        if (locales.toLanguageTags() != lastLocales) refresh()
    }

    /** The UserHandle behind a serial seen in the current snapshot, or null if that profile is gone. */
    fun userFor(serial: Long): UserHandle? = users[serial]

    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    // ---- apps thread -----------------------------------------------------------------------

    private fun loadAll() {
        val entries = ArrayList<AppEntry>()
        for (user in profileSource.profiles()) {
            val serial = serialOf(user) ?: continue
            users[serial] = user
            val infos =
                try {
                    launcherApps.getActivityList(null, user)
                } catch (e: RuntimeException) {
                    Logs.w(TAG, "getActivityList failed for user $serial", e)
                    emptyList()
                }
            infos.mapNotNullTo(entries) { toEntry(it, serial) }
        }
        publish(entries)
    }

    private fun reloadPackage(
        packageName: String,
        user: UserHandle,
    ) {
        val serial = serialOf(user) ?: return
        users[serial] = user
        val fresh =
            try {
                launcherApps.getActivityList(packageName, user)
            } catch (e: RuntimeException) {
                Logs.w(TAG, "getActivityList failed for $packageName", e)
                emptyList()
            }.mapNotNull { toEntry(it, serial) }
        val kept = current.entries.filterNot { it.key.packageName == packageName && it.key.userSerial == serial }
        publish(kept + fresh)
    }

    private fun removePackage(
        packageName: String,
        user: UserHandle,
    ) {
        val serial = serialOf(user) ?: return
        publish(current.entries.filterNot { it.key.packageName == packageName && it.key.userSerial == serial })
    }

    private fun toEntry(
        info: LauncherActivityInfo,
        serial: Long,
    ): AppEntry? {
        val component = info.componentName
        if (component.packageName == appContext.packageName) return null
        // Synthesized rows for apps without a launcher activity ("app details" entries).
        if (component.className == APP_DETAILS_ACTIVITY) return null
        val label =
            info.label
                ?.toString()
                ?.trim()
                .orEmpty()
                .ifEmpty { component.packageName }
        val flags = info.applicationInfo.flags
        val suspended = (flags and ApplicationInfo.FLAG_SUSPENDED) != 0
        val system = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
        return AppEntry(AppKey(component.packageName, component.className, serial), label, suspended, system)
    }

    private fun publish(entries: List<AppEntry>) {
        val collator = Collator.getInstance(Locale.getDefault())
        val sorted =
            entries.sortedWith(
                Comparator<AppEntry> { a, b -> collator.compare(a.label, b.label) }
                    .thenBy { it.key.packageName }
                    .thenBy { it.key.activityName }
                    .thenBy { it.key.userSerial },
            )
        lastLocales =
            appContext.resources.configuration.locales
                .toLanguageTags()
        val next = Snapshot(sorted, current.generation + 1, loaded = true)
        current = next
        Logs.d(TAG) { "snapshot #${next.generation}: ${sorted.size} apps" }
        Threads.main.post { for (listener in listeners) listener.onSnapshot(next) }
    }

    private fun serialOf(user: UserHandle): Long? = userManager.getSerialNumberForUser(user).takeIf { it >= 0 }

    private val callback =
        object : LauncherApps.Callback() {
            override fun onPackageRemoved(
                packageName: String,
                user: UserHandle,
            ) = removePackage(packageName, user)

            override fun onPackageAdded(
                packageName: String,
                user: UserHandle,
            ) = reloadPackage(packageName, user)

            override fun onPackageChanged(
                packageName: String,
                user: UserHandle,
            ) = reloadPackage(packageName, user)

            override fun onPackagesAvailable(
                packageNames: Array<String>,
                user: UserHandle,
                replacing: Boolean,
            ) = packageNames.forEach { reloadPackage(it, user) }

            override fun onPackagesUnavailable(
                packageNames: Array<String>,
                user: UserHandle,
                replacing: Boolean,
            ) = packageNames.forEach { removePackage(it, user) }

            override fun onPackagesSuspended(
                packageNames: Array<String>,
                user: UserHandle,
            ) = packageNames.forEach { reloadPackage(it, user) }

            override fun onPackagesUnsuspended(
                packageNames: Array<String>,
                user: UserHandle,
            ) = packageNames.forEach { reloadPackage(it, user) }
        }

    private val localeReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                Logs.d(TAG) { "locale changed" }
                refresh()
            }
        }

    private companion object {
        const val TAG = "AppRepository"
        const val APP_DETAILS_ACTIVITY = "android.app.AppDetailsActivity"
    }
}
