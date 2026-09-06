package io.github.subhaneetshrestha.atomic.apps

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.view.View
import android.widget.Toast
import io.github.subhaneetshrestha.atomic.R
import io.github.subhaneetshrestha.atomic.util.Logs

/** System actions on an app that need no special permission: the app-info page and the uninstall dialog. */
class AppActions(
    private val context: Context,
    private val repository: AppRepository,
) {
    private val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)

    fun showAppInfo(
        entry: AppEntry,
        sourceView: View?,
    ) {
        val user = repository.userFor(entry.key.userSerial)
        val bounds = sourceView?.let { view -> Rect().takeIf { view.getGlobalVisibleRect(it) } }
        try {
            if (user == null) throw IllegalStateException("unknown user ${entry.key.userSerial}")
            launcherApps.startAppDetailsActivity(
                ComponentName(entry.key.packageName, entry.key.activityName),
                user,
                bounds,
                null,
            )
        } catch (e: RuntimeException) {
            Logs.w(TAG, "app info failed for ${entry.key.flattenedComponent}", e)
            Toast.makeText(context, R.string.action_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    /** Opens the system uninstall dialog; the package callbacks update the list when it completes. */
    fun uninstall(entry: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${entry.key.packageName}"))
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Logs.w(TAG, "no uninstall UI", e)
            Toast.makeText(context, R.string.action_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val TAG = "AppActions"
    }
}
