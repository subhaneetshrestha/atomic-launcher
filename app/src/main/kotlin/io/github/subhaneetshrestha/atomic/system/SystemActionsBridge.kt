package io.github.subhaneetshrestha.atomic.system

import io.github.subhaneetshrestha.atomic.core.theme.BuiltinId

/**
 * The way to the actions only a privileged component can perform: locking the screen, the
 * notification shade, quick settings, recents, a screenshot, the power menu. The optional
 * accessibility service that carries them out arrives in a later phase; until then this build
 * declares it supports none of them, so they are offered as "not in this version" rather than
 * asking the user to allow a service that is not there.
 */
interface SystemActionsBridge {
    val isEnabled: Boolean

    val isDeviceAdminActive: Boolean

    /** The actions this build can actually perform. */
    val supported: Set<BuiltinId>

    fun perform(id: BuiltinId): Boolean
}

object NoSystemActions : SystemActionsBridge {
    override val isEnabled: Boolean = false

    override val isDeviceAdminActive: Boolean = false

    override val supported: Set<BuiltinId> = emptySet()

    override fun perform(id: BuiltinId): Boolean = false
}
