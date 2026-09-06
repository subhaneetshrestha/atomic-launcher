package io.github.subhaneetshrestha.atomic

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.StrictMode
import io.github.subhaneetshrestha.atomic.apps.AppRepository
import io.github.subhaneetshrestha.atomic.settings.DefaultSettingsSource
import io.github.subhaneetshrestha.atomic.settings.SettingsSource
import io.github.subhaneetshrestha.atomic.util.Logs

/** Process singletons. Keeps onCreate to a few milliseconds: the home app starts at every boot. */
class AtomicApp : Application() {

    lateinit var appRepository: AppRepository
        private set

    lateinit var settingsSource: SettingsSource
        private set

    override fun onCreate() {
        super.onCreate()
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Logs.enabled = debuggable
        if (debuggable) installStrictMode()
        // A later phase skips all of this when running in the accessibility service's own process.
        settingsSource = DefaultSettingsSource()
        appRepository = AppRepository(this).also { it.start() }
    }

    /** Debug builds only: surfaces main-thread I/O, leaks and hidden-API use in logcat. */
    private fun installStrictMode() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .apply { if (Build.VERSION.SDK_INT >= 28) detectNonSdkApiUsage() }
                .penaltyLog()
                .build(),
        )
    }
}
