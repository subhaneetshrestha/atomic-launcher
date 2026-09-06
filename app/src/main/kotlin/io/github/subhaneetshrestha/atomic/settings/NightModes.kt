package io.github.subhaneetshrestha.atomic.settings

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import io.github.subhaneetshrestha.atomic.core.theme.NightMode

/** Forces light or dark resources per activity through an override configuration; SYSTEM leaves the device's choice. */
object NightModes {
    fun applyTo(
        activity: Activity,
        base: Context,
        mode: NightMode,
    ) {
        if (mode == NightMode.SYSTEM) return
        val override = Configuration()
        val nightBits = if (mode == NightMode.DARK) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        override.uiMode = (base.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
        activity.applyOverrideConfiguration(override)
    }

    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}
