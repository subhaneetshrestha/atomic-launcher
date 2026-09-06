package io.github.subhaneetshrestha.atomic.actions

import io.github.subhaneetshrestha.atomic.core.theme.BuiltinId

/** Facts about the built-in actions that do not depend on the device. */
object BuiltinActions {
    /** The Android version an action first works on; anything older is offered but greyed out. */
    fun minSdk(id: BuiltinId): Int =
        when (id) {
            // Settings panels arrived in Android 10.
            BuiltinId.PANEL_INTERNET, BuiltinId.PANEL_WIFI, BuiltinId.PANEL_VOLUME, BuiltinId.PANEL_NFC -> 29

            // The accessibility service can only take a screenshot from Android 9.
            BuiltinId.SCREENSHOT -> 28

            else -> OLDEST
        }

    /** minSdk of the app itself: an action gated at this level is gated nowhere. */
    const val OLDEST = 26

    /** GLOBAL_ACTION_LOCK_SCREEN arrived in Android 9; below that only device admin can lock. */
    const val ACCESSIBILITY_LOCK_SDK = 28
}
