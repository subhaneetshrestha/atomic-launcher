package io.github.subhaneetshrestha.atomic.actions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.subhaneetshrestha.atomic.apps.AppRepository
import io.github.subhaneetshrestha.atomic.core.theme.ActionGroup
import io.github.subhaneetshrestha.atomic.core.theme.BuiltinId
import io.github.subhaneetshrestha.atomic.system.SystemActionsBridge

/**
 * Answers the availability questions from the real device. [builtSurfaces] is what this version of
 * the launcher has built of its own; actions outside it are honestly reported as missing from this
 * version rather than appearing broken.
 */
class AndroidActionEnvironment(
    context: Context,
    private val apps: AppRepository,
    private val system: SystemActionsBridge,
    private val builtSurfaces: Set<BuiltinId>,
    private val defaultLauncher: () -> Boolean,
) : ActionEnvironment {
    private val packageManager = context.applicationContext.packageManager
    private val handlerless = mutableSetOf<BuiltinId>()

    override val sdkInt: Int = Build.VERSION.SDK_INT

    override val hasTorch: Boolean = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

    override val isDefaultLauncher: Boolean get() = defaultLauncher()

    override val accessibilityEnabled: Boolean get() = system.isEnabled

    override val deviceAdminActive: Boolean get() = system.isDeviceAdminActive

    override fun appExists(
        component: String,
        user: Long,
    ): Boolean = apps.contains(component, user)

    override fun packageExists(
        pkg: String,
        user: Long,
    ): Boolean = apps.containsPackage(pkg, user)

    override fun isBuilt(id: BuiltinId): Boolean =
        when (id.group) {
            ActionGroup.SYSTEM -> id in system.supported
            ActionGroup.LAUNCHER -> id in builtSurfaces
            ActionGroup.INTENT, ActionGroup.DEVICE -> true
        }

    override fun hasNoHandler(id: BuiltinId): Boolean = id in handlerless

    /** Remembered after a launch found nothing on this device, so the picker can grey it out. */
    fun rememberNoHandler(id: BuiltinId) {
        handlerless += id
    }
}
