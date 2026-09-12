package io.github.subhaneetshrestha.atomic.system

import android.content.Context
import io.github.subhaneetshrestha.atomic.util.Logs

/**
 * Pulling down the notification shade without an accessibility service. Android has never made
 * this public, but every launcher does it, and where the platform still allows it the shade is one
 * swipe away with nothing to grant.
 *
 * Where it does not, the lookup itself fails and the launcher says so: the action then needs the
 * accessibility service, and asks for it. Nothing here throws, whatever the platform does.
 */
object StatusBarShade {
    /** Whether this Android will let the launcher reach the shade on its own. Worked out once. */
    val isAvailable: Boolean by lazy { method() != null }

    fun expand(context: Context): Boolean {
        val expand = method() ?: return false
        val service = context.getSystemService(SERVICE) ?: return false
        return try {
            expand.invoke(service)
            Logs.d(TAG) { "shade opened without the service" }
            true
        } catch (e: Throwable) {
            // A hidden API can be withdrawn between one version and the next, and a launcher that
            // crashes loses its place as the home app. Anything at all here is a "no".
            Logs.w(TAG, "the shade would not open", e)
            false
        }
    }

    private fun method(): java.lang.reflect.Method? =
        try {
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel")
        } catch (e: Throwable) {
            Logs.d(TAG) { "this Android does not offer the shade to apps: $e" }
            null
        }

    private const val SERVICE = "statusbar"

    private const val TAG = "StatusBarShade"
}
