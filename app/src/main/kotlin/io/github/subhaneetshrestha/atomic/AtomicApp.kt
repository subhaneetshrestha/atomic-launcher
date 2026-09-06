package io.github.subhaneetshrestha.atomic

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.StrictMode
import androidx.core.content.pm.PackageInfoCompat
import io.github.subhaneetshrestha.atomic.apps.AppRepository
import io.github.subhaneetshrestha.atomic.core.theme.ResolvedColors
import io.github.subhaneetshrestha.atomic.core.theme.ThemeResolver
import io.github.subhaneetshrestha.atomic.diagnostics.CrashEnvironment
import io.github.subhaneetshrestha.atomic.diagnostics.CrashRecorder
import io.github.subhaneetshrestha.atomic.settings.NightModes
import io.github.subhaneetshrestha.atomic.settings.SettingsRepository
import io.github.subhaneetshrestha.atomic.settings.TokenColors
import io.github.subhaneetshrestha.atomic.util.Logs
import java.time.Instant

/** Process singletons. Keeps onCreate to a few milliseconds: the home app starts at every boot. */
class AtomicApp : Application() {
    lateinit var appRepository: AppRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var themeResolver: ThemeResolver
        private set

    lateinit var crashRecorder: CrashRecorder
        private set

    lateinit var crashEnvironment: CrashEnvironment
        private set

    /** The first-run setup is offered once per process; the persisted flag decides across processes. */
    var setupOffered: Boolean = false

    override fun onCreate() {
        super.onCreate()
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Logs.enabled = debuggable
        if (debuggable) installStrictMode()
        crashEnvironment = crashEnvironment()
        crashRecorder = CrashRecorder(filesDir, { Instant.now() }, crashEnvironment).also { it.install() }
        // One small file, read once, so the home screen never shows a loading state.
        val policy = StrictMode.allowThreadDiskReads()
        try {
            settingsRepository = SettingsRepository(this)
        } finally {
            StrictMode.setThreadPolicy(policy)
        }
        themeResolver = ThemeResolver(TokenColors(this))
        val versionCode = crashEnvironment.versionCode
        if (settingsRepository.settings.app.lastVersionCode != versionCode) {
            settingsRepository.update { it.copy(app = it.app.copy(lastVersionCode = versionCode)) }
        }
        // A later phase skips all of this when running in the accessibility service's own process.
        appRepository = AppRepository(this).also { it.start() }
    }

    /** The current theme's colours for [context]'s day/night state. */
    fun resolvedColors(context: Context): ResolvedColors =
        themeResolver.resolve(settingsRepository.settings.theme, NightModes.isNight(context))

    private fun crashEnvironment(): CrashEnvironment {
        val info: PackageInfo =
            if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
        return CrashEnvironment(
            appVersion = info.versionName ?: "?",
            versionCode = PackageInfoCompat.getLongVersionCode(info).toInt(),
            androidRelease = Build.VERSION.RELEASE ?: "?",
            sdkInt = Build.VERSION.SDK_INT,
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
        )
    }

    /** Debug builds only: surfaces main-thread I/O, leaks and hidden-API use in logcat. */
    private fun installStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy
                .Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy
                .Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .apply { if (Build.VERSION.SDK_INT >= 28) detectNonSdkApiUsage() }
                .penaltyLog()
                .build(),
        )
    }
}
