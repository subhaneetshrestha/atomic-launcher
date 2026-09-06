package io.github.subhaneetshrestha.atomic

import android.content.Context
import androidx.activity.ComponentActivity
import io.github.subhaneetshrestha.atomic.settings.NightModes

/** Base for every activity: applies the user's night-mode choice before resources are read. */
abstract class ThemedActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        val app = newBase.applicationContext as? AtomicApp ?: return
        NightModes.applyTo(this, newBase, app.settingsRepository.settings.appearance.nightMode)
    }

    protected val atomicApp: AtomicApp get() = application as AtomicApp
}
