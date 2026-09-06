package io.github.subhaneetshrestha.atomic.settings.grant

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.subhaneetshrestha.atomic.util.Logs

/** Asks the package manager where this copy of the app came from; the rule itself is [InstallSource]. */
object InstallSourceProbe {
    fun restriction(context: Context): Restriction {
        // Nothing is restricted before Android 13, and nothing before it can be asked either:
        // getInstallSourceInfo is Android 11 and its packageSource is Android 13.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return Restriction.NO
        val info =
            try {
                context.packageManager.getInstallSourceInfo(context.packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                Logs.w(TAG, "install source unknown", e)
                null
            }
        return InstallSource.restriction(
            sdkInt = Build.VERSION.SDK_INT,
            packageSource = info?.packageSource,
            installer = info?.installingPackageName,
        )
    }

    private const val TAG = "InstallSource"
}
