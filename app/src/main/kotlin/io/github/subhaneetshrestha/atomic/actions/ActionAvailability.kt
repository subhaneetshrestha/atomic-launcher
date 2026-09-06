package io.github.subhaneetshrestha.atomic.actions

import io.github.subhaneetshrestha.atomic.core.theme.Action
import io.github.subhaneetshrestha.atomic.core.theme.ActionGroup
import io.github.subhaneetshrestha.atomic.core.theme.BuiltinId

/**
 * Decides whether an action can run, so the picker can grey out what this device cannot do and
 * the dispatcher can offer a way forward instead of failing silently. Plain Kotlin over
 * [ActionEnvironment], so every rule is unit-tested.
 */
class ActionAvailability(
    private val env: ActionEnvironment,
) {
    fun of(action: Action): Availability =
        when (action) {
            Action.None -> {
                Availability.Available
            }

            is Action.Unknown -> {
                Availability.Unsupported(UnsupportedReason.UNKNOWN_ACTION)
            }

            is Action.Builtin -> {
                ofBuiltin(action.id)
            }

            is Action.OpenApp -> {
                ifInstalled(env.appExists(action.component, action.user))
            }

            is Action.AppInfo -> {
                ifInstalled(env.appExists(action.component, action.user))
            }

            is Action.Uninstall -> {
                ifInstalled(env.packageExists(action.pkg, action.user))
            }

            is Action.Shortcut -> {
                if (env.isDefaultLauncher) {
                    ifInstalled(env.packageExists(action.pkg, action.user))
                } else {
                    Availability.NotDefaultLauncher
                }
            }

            // A link cannot be resolved in advance without asking who can open it, which package
            // visibility does not allow; a failure to open is learned when it happens.
            is Action.OpenUrl -> {
                Availability.Available
            }
        }

    private fun ifInstalled(installed: Boolean): Availability =
        if (installed) Availability.Available else Availability.Missing

    private fun ofBuiltin(id: BuiltinId): Availability =
        when {
            // A surface this build does not have cannot be offered, nor can the user be asked to
            // allow something that is not there to allow.
            !env.isBuilt(id) -> {
                Availability.Unsupported(UnsupportedReason.NOT_IN_THIS_VERSION)
            }

            env.sdkInt < BuiltinActions.minSdk(id) -> {
                Availability.Unsupported(UnsupportedReason.NEEDS_NEWER_ANDROID)
            }

            id == BuiltinId.FLASHLIGHT_TOGGLE && !env.hasTorch -> {
                Availability.Unsupported(UnsupportedReason.NO_HARDWARE)
            }

            id == BuiltinId.LOCK_SCREEN -> {
                lockScreen()
            }

            id.group == ActionGroup.SYSTEM && !env.accessibilityEnabled -> {
                Availability.NeedsGrant(ActionGrant.ACCESSIBILITY)
            }

            id.group == ActionGroup.INTENT && env.hasNoHandler(id) -> {
                Availability.NoHandler
            }

            else -> {
                Availability.Available
            }
        }

    /** Two routes to the same thing: whichever the user has allowed, and whichever this Android has. */
    private fun lockScreen(): Availability =
        when {
            env.deviceAdminActive -> Availability.Available
            env.sdkInt < BuiltinActions.ACCESSIBILITY_LOCK_SDK -> Availability.NeedsGrant(ActionGrant.DEVICE_ADMIN)
            env.accessibilityEnabled -> Availability.Available
            else -> Availability.NeedsGrant(ActionGrant.ACCESSIBILITY)
        }
}
