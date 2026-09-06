package io.github.subhaneetshrestha.atomic.actions

/** Whether an action can be carried out here and now, and what stands in the way if it cannot. */
sealed class Availability {
    data object Available : Availability()

    /** The user has to allow something first; the settings screen offers the disclosure. */
    data class NeedsGrant(
        val grant: ActionGrant,
    ) : Availability()

    data class Unsupported(
        val reason: UnsupportedReason,
    ) : Availability()

    /** An implicit intent that nothing on this device answered when it was last tried. */
    data object NoHandler : Availability()

    /** Shortcuts are only readable while we hold the home role. */
    data object NotDefaultLauncher : Availability()

    /** The app or shortcut it points at is gone. */
    data object Missing : Availability()
}

enum class ActionGrant { ACCESSIBILITY, DEVICE_ADMIN }

enum class UnsupportedReason {
    NEEDS_NEWER_ANDROID,
    NO_HARDWARE,

    /** A launcher surface a later version brings; the action exists so a shared backup keeps it. */
    NOT_IN_THIS_VERSION,
    UNKNOWN_ACTION,
}
