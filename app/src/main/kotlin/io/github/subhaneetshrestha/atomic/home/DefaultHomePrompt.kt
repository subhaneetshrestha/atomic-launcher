package io.github.subhaneetshrestha.atomic.home

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import io.github.subhaneetshrestha.atomic.util.Logs

/** Knows whether we hold the home role and opens the system UI that grants it. */
class DefaultHomePrompt(private val activity: Activity) {

    fun isDefaultHome(): Boolean = try {
        val byRole = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) heldByRole() else null
        byRole ?: resolvesToUs()
    } catch (e: RuntimeException) {
        Logs.w(TAG, "default-home check failed; assuming default to avoid nagging", e)
        true
    }

    /** Role request dialog on API 29+ (result delivered through [roleLauncher]); Home settings otherwise. */
    fun request(roleLauncher: ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && requestRole(roleLauncher)) return
        for (action in SETTINGS_FALLBACKS) {
            try {
                activity.startActivity(Intent(action))
                return
            } catch (e: ActivityNotFoundException) {
                Logs.w(TAG, "$action not available")
            }
        }
    }

    /** null when the role system is unavailable on this device, so the caller falls back to resolution. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun heldByRole(): Boolean? {
        val roles = activity.getSystemService(RoleManager::class.java) ?: return null
        if (!roles.isRoleAvailable(RoleManager.ROLE_HOME)) return null
        return roles.isRoleHeld(RoleManager.ROLE_HOME)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestRole(roleLauncher: ActivityResultLauncher<Intent>): Boolean {
        val roles = activity.getSystemService(RoleManager::class.java) ?: return false
        if (!roles.isRoleAvailable(RoleManager.ROLE_HOME)) return false
        return try {
            roleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
            true
        } catch (e: ActivityNotFoundException) {
            Logs.w(TAG, "role request UI unavailable, falling back to Settings", e)
            false
        }
    }

    private fun resolvesToUs(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = activity.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == activity.packageName
    }

    private companion object {
        const val TAG = "DefaultHomePrompt"
        val SETTINGS_FALLBACKS = listOf(
            Settings.ACTION_HOME_SETTINGS,
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            Settings.ACTION_SETTINGS,
        )
    }
}
