package io.github.subhaneetshrestha.atomic.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.os.UserHandle
import io.github.subhaneetshrestha.atomic.util.Logs

/** One of an app's own shortcuts: what it is called, and the id to start it by. */
data class AppShortcut(
    val id: String,
    val label: String,
)

/**
 * The shortcuts an app publishes — "New message", "Take a selfie" — which Android only lets the
 * default home app read. Asking while another launcher holds that place throws, so it is asked
 * first and answered honestly.
 */
class ShortcutProvider(
    context: Context,
    private val repository: AppRepository,
) {
    private val launcherApps: LauncherApps? = context.getSystemService(LauncherApps::class.java)

    /** Whether this launcher may read shortcuts at all, which means: whether it is the home app. */
    val isAllowed: Boolean
        get() =
            try {
                launcherApps?.hasShortcutHostPermission() == true
            } catch (e: SecurityException) {
                false
            }

    fun forPackage(
        pkg: String,
        user: Long,
    ): List<AppShortcut> {
        val apps = launcherApps ?: return emptyList()
        val handle: UserHandle = repository.userFor(user) ?: return emptyList()
        val query =
            LauncherApps
                .ShortcutQuery()
                .setPackage(pkg)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
        return try {
            apps
                .getShortcuts(query, handle)
                .orEmpty()
                .filter { it.isEnabled }
                .mapNotNull { shortcut -> label(shortcut)?.let { AppShortcut(shortcut.id, it) } }
        } catch (e: SecurityException) {
            Logs.w(TAG, "shortcuts are only readable by the home app", e)
            emptyList()
        } catch (e: IllegalStateException) {
            Logs.w(TAG, "shortcuts could not be read", e)
            emptyList()
        }
    }

    fun start(
        pkg: String,
        id: String,
        user: Long,
        bounds: Rect?,
    ): Boolean {
        val apps = launcherApps ?: return false
        val handle = repository.userFor(user) ?: return false
        return try {
            apps.startShortcut(pkg, id, bounds, null, handle)
            true
        } catch (e: RuntimeException) {
            Logs.w(TAG, "shortcut $pkg/$id would not start", e)
            false
        }
    }

    /** The short label is what a launcher shows; the long one is the fallback. */
    private fun label(shortcut: ShortcutInfo): String? =
        (shortcut.shortLabel ?: shortcut.longLabel)?.toString()?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val TAG = "ShortcutProvider"
    }
}
