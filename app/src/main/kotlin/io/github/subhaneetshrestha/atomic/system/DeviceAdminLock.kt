package io.github.subhaneetshrestha.atomic.system

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import io.github.subhaneetshrestha.atomic.R
import io.github.subhaneetshrestha.atomic.util.Logs

/**
 * Device administrator, asked for one thing only: locking the screen. It is the only way to do
 * that below Android 9, and it stays as the alternative above it for anyone who would rather not
 * turn on an accessibility service.
 *
 * The policy file asks for `force-lock` and nothing else, and the disclosure says what that costs:
 * the phone asks for the PIN rather than the fingerprint afterwards, and the administrator has to
 * be turned off before the launcher can be uninstalled.
 */
class LockAdminReceiver : DeviceAdminReceiver()

object DeviceAdminLock {
    fun component(context: Context): ComponentName = ComponentName(context, LockAdminReceiver::class.java)

    fun isActive(context: Context): Boolean = manager(context)?.isAdminActive(component(context)) == true

    fun lock(context: Context): Boolean {
        val manager = manager(context) ?: return false
        if (!manager.isAdminActive(component(context))) return false
        return try {
            manager.lockNow()
            true
        } catch (e: SecurityException) {
            Logs.w(TAG, "not allowed to lock", e)
            false
        }
    }

    fun addIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.grant_admin_explanation),
            )

    /** Gives the administrator back. Android will not let the app be uninstalled until it is. */
    fun remove(context: Context) {
        val manager = manager(context) ?: return
        try {
            manager.removeActiveAdmin(component(context))
        } catch (e: SecurityException) {
            Logs.w(TAG, "could not give the administrator back", e)
        }
    }

    private fun manager(context: Context): DevicePolicyManager? =
        context.getSystemService(DevicePolicyManager::class.java)

    private const val TAG = "DeviceAdminLock"
}
