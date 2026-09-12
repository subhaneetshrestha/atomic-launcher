package io.github.subhaneetshrestha.atomic.system

import io.github.subhaneetshrestha.atomic.core.theme.BuiltinId

/**
 * The way to the actions only a privileged component can perform: locking the screen, the
 * notification shade, quick settings, recents, a screenshot, the power menu. Most of them need the
 * optional accessibility service, which is off until the user turns it on; the shade sometimes
 * needs nothing at all, and the lock will take a device administrator instead.
 */
interface SystemActionsBridge {
    val isEnabled: Boolean

    val isDeviceAdminActive: Boolean

    /** The actions this build can actually perform. */
    val supported: Set<BuiltinId>

    /** Whether this Android still lets an app open the shade without anything being granted. */
    val shadeWithoutAccessibility: Boolean

    fun perform(id: BuiltinId): Boolean
}
