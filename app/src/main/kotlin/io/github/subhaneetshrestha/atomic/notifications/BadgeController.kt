package io.github.subhaneetshrestha.atomic.notifications

import android.content.Context
import io.github.subhaneetshrestha.atomic.core.theme.NotificationConfig
import io.github.subhaneetshrestha.atomic.settings.SettingsRepository
import io.github.subhaneetshrestha.atomic.util.Logs
import io.github.subhaneetshrestha.atomic.util.Threads
import java.lang.ref.WeakReference

/**
 * Badges, from the process's point of view: the store the home screen reads, the hub the listener
 * feeds, and the settings that decide whether any of it runs. Created once per process.
 */
class BadgeController(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val coalescer =
        Coalescer { delayMs, action ->
            val runnable = Runnable(action)
            Threads.main.postDelayed(runnable, delayMs)
            val cancel = { Threads.main.removeCallbacks(runnable) }
            cancel
        }

    val store = BadgeStore(coalescer)
    val hub = BadgeHub(store)

    /** The connected listener, weakly: the system owns its lifetime, not us. */
    private var listener = WeakReference<BadgeNotificationListener>(null)

    /** Whether the user has asked for badges at all. Access is a separate question. */
    val wanted: Boolean get() = settings.settings.notifications.enabled

    /**
     * Set when the user is handed to Settings to grant notification access, and spent the first
     * time we see that they did. It lives here rather than on the screen that set it because that
     * screen may be gone by the time they come back: leaving Settings by the Home key finishes
     * it, and the launcher's own onResume is then the only thing left to notice the grant.
     */
    var awaitingGrant: Boolean = false

    fun start() {
        apply(settings.settings.notifications)
        settings.addDocumentListener { old, new ->
            if (old.notifications != new.notifications) apply(new.notifications)
        }
        // The system binds a granted listener on its own at boot; this covers the app being
        // started while the binding has been dropped (a crash, a force-stop, a fresh grant).
        if (wanted) NotificationAccess.rebind(context)
    }

    fun attach(service: BadgeNotificationListener) {
        listener = WeakReference(service)
    }

    fun detach(service: BadgeNotificationListener) {
        if (listener.get() === service) listener.clear()
    }

    /** Called from either activity's onResume: the user may have been to Settings meanwhile. */
    fun onAccessChanged(granted: Boolean) {
        if (granted && awaitingGrant) {
            // They went to Settings to turn badges on, and they did. Honour that once, and never
            // against a later decision to turn badges off.
            awaitingGrant = false
            if (!wanted) settings.update { it.copy(notifications = it.notifications.copy(enabled = true)) }
        }
        if (granted && wanted) {
            NotificationAccess.rebind(context)
        } else if (!granted) {
            hub.clear()
        }
    }

    private fun apply(config: NotificationConfig) {
        hub.includeOngoing = config.includeOngoing
        store.disabled = config.perAppDisabled.map { BadgeKey(it.pkg, it.user) }.toSet()
        if (config.enabled) {
            NotificationAccess.rebind(context)
        } else {
            // Turning badges off stops the listening, not just the drawing.
            hub.clear()
            listener.get()?.let {
                Logs.d(TAG) { "badges off; asking to unbind" }
                it.requestUnbind()
            }
        }
    }

    /** Whether a badge should be drawn for this app right now. */
    fun countFor(
        packageName: String,
        userSerial: Long,
    ): Int = if (wanted) store.countFor(packageName, userSerial) else 0

    private companion object {
        const val TAG = "BadgeController"
    }
}
