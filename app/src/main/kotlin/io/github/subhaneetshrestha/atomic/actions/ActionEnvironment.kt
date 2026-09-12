package io.github.subhaneetshrestha.atomic.actions

import io.github.subhaneetshrestha.atomic.core.theme.BuiltinId

/**
 * What the availability decision needs to know about this device and this build. Kept as an
 * interface so the decision itself is plain Kotlin and can be unit-tested without a device.
 */
interface ActionEnvironment {
    val sdkInt: Int

    val hasTorch: Boolean

    val isDefaultLauncher: Boolean

    val accessibilityEnabled: Boolean

    /** Whether the notification shade can be opened with nothing granted. */
    val shadeWithoutAccessibility: Boolean

    val deviceAdminActive: Boolean

    fun appExists(
        component: String,
        user: Long,
    ): Boolean

    fun packageExists(
        pkg: String,
        user: Long,
    ): Boolean

    /** Whether this version of the launcher has the surface the action needs. */
    fun isBuilt(id: BuiltinId): Boolean

    /** Whether an implicit intent went unanswered when it was last tried in this session. */
    fun hasNoHandler(id: BuiltinId): Boolean
}
