package io.github.subhaneetshrestha.atomic.settings.grant

/** Whether the switch the user is about to look for will do anything when they tap it. */
enum class Restriction {
    /** It will work. */
    NO,

    /** It will not: this app was installed from a file, which Android 13 and later guard. */
    LIKELY,

    /** It might not, and there is no way to tell from here, so say so gently. */
    POSSIBLE,
}

/**
 * Where this copy of the app came from, and what that means for the switches Android guards.
 *
 * From Android 13, notification access and accessibility cannot be granted to an app installed
 * from a file until the user opens App info and allows restricted settings; the switch is simply
 * inert until then, with nothing on screen to say why. From Android 15 the same guard extends to
 * installers the phone does not vouch for. An app from the Play Store is never affected.
 *
 * The numbers mirror PackageInstaller.SessionParams, whose values the Android side passes through.
 */
object InstallSource {
    const val UNSPECIFIED = 0
    const val STORE = 1
    const val LOCAL_FILE = 2
    const val DOWNLOADED_FILE = 3
    const val OTHER = 4

    /** The Play Store's own installer, the one case that is certainly unrestricted. */
    const val PLAY_STORE = "com.android.vending"

    private const val FIRST_GUARDED_SDK = 33

    fun restriction(
        sdkInt: Int,
        packageSource: Int?,
        installer: String?,
    ): Restriction =
        when {
            sdkInt < FIRST_GUARDED_SDK -> Restriction.NO
            installer == PLAY_STORE -> Restriction.NO
            packageSource == LOCAL_FILE || packageSource == DOWNLOADED_FILE -> Restriction.LIKELY
            else -> Restriction.POSSIBLE
        }
}
