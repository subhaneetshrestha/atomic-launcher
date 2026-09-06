package io.github.subhaneetshrestha.atomic.apps

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.view.View
import android.widget.Toast
import io.github.subhaneetshrestha.atomic.R
import io.github.subhaneetshrestha.atomic.util.Logs

/** Launches apps through LauncherApps in the entry's own profile. A failed launch never crashes Home. */
class AppLauncher(
    private val context: Context,
    private val repository: AppRepository,
) {
    private val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)

    fun launch(
        entry: AppEntry,
        sourceView: View?,
    ) {
        val component = ComponentName(entry.key.packageName, entry.key.activityName)
        val bounds = sourceView?.let { view -> Rect().takeIf { view.getGlobalVisibleRect(it) } }
        val options =
            sourceView?.let { view ->
                ActivityOptions.makeClipRevealAnimation(view, 0, 0, view.width, view.height).toBundle()
            }
        try {
            val user =
                repository.userFor(entry.key.userSerial)
                    ?: throw IllegalStateException("unknown user serial ${entry.key.userSerial}")
            launcherApps.startMainActivity(component, user, bounds, options)
        } catch (e: RuntimeException) {
            // ActivityNotFoundException, SecurityException, IllegalStateException: the app may have
            // been removed or disabled between the snapshot and the tap.
            Logs.w(TAG, "launch failed for ${entry.key.flattenedComponent}", e)
            Toast.makeText(context, context.getString(R.string.launch_failed, entry.label), Toast.LENGTH_SHORT).show()
            repository.refresh()
        }
    }

    private companion object {
        const val TAG = "AppLauncher"
    }
}
