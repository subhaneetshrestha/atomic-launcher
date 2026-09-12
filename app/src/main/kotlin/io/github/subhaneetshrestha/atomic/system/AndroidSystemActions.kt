package io.github.subhaneetshrestha.atomic.system

import android.content.Context
import android.os.Build
import io.github.subhaneetshrestha.atomic.core.theme.BuiltinId
import io.github.subhaneetshrestha.atomic.util.Logs

/**
 * The real way to the privileged actions. Two of them can sometimes be done without asking the
 * user for anything: the shade, where Android still lets an app reach it, and the lock, where the
 * user has made the launcher a device administrator instead. Everything else goes through the
 * accessibility service, which is off until they turn it on.
 */
class AndroidSystemActions(
    context: Context,
) : SystemActionsBridge {
    private val appContext = context.applicationContext

    override val isEnabled: Boolean get() = AccessibilityAccess.isEnabled(appContext)

    override val isDeviceAdminActive: Boolean get() = DeviceAdminLock.isActive(appContext)

    override val supported: Set<BuiltinId> = SystemActions.offered(Build.VERSION.SDK_INT)

    /** Whether the shade can be opened with nothing granted at all. */
    override val shadeWithoutAccessibility: Boolean get() = StatusBarShade.isAvailable

    override fun perform(id: BuiltinId): Boolean {
        if (id == BuiltinId.LOCK_SCREEN && (!isEnabled || Build.VERSION.SDK_INT < LOCK_BY_SERVICE_SDK)) {
            return DeviceAdminLock.lock(appContext)
        }
        if (id == BuiltinId.NOTIFICATION_SHADE && !isEnabled) return StatusBarShade.expand(appContext)
        if (!isEnabled || id !in SystemActions.supported(Build.VERSION.SDK_INT)) return false
        return try {
            appContext.startService(SystemActionsService.intent(appContext, id)) != null
        } catch (e: IllegalStateException) {
            // Starting a service is refused when the app is not in the foreground, which is not a
            // state a gesture on the home screen is made in, but is one a stale gesture could be.
            Logs.w(TAG, "could not reach the service", e)
            false
        } catch (e: SecurityException) {
            Logs.w(TAG, "not allowed to reach the service", e)
            false
        }
    }

    private companion object {
        /** GLOBAL_ACTION_LOCK_SCREEN arrived in Android 9; below it only an administrator can lock. */
        const val LOCK_BY_SERVICE_SDK = 28

        const val TAG = "AndroidSystemActions"
    }
}
