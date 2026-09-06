package io.github.subhaneetshrestha.atomic.settings.grant

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * From Android 13, an app installed from a file rather than a store cannot be given notification
 * access or accessibility until the user allows restricted settings for it, and the switch simply
 * does nothing until they do. Knowing when that is likely is what lets the launcher explain the
 * dead switch instead of leaving the user to work it out.
 */
class InstallSourceTest {
    @Test
    fun `before Android 13 there is no such thing to warn about`() {
        assertEquals(
            Restriction.NO,
            InstallSource.restriction(sdkInt = 30, packageSource = InstallSource.LOCAL_FILE, installer = null),
        )
        assertEquals(
            Restriction.NO,
            InstallSource.restriction(
                sdkInt = 32,
                packageSource = InstallSource.DOWNLOADED_FILE,
                installer = "org.fdroid.fdroid",
            ),
        )
    }

    @Test
    fun `an app installed from a file is the case the warning is for`() {
        assertEquals(
            Restriction.LIKELY,
            InstallSource.restriction(
                sdkInt = 33,
                packageSource = InstallSource.LOCAL_FILE,
                installer = "com.android.shell",
            ),
        )
        assertEquals(
            Restriction.LIKELY,
            InstallSource.restriction(
                sdkInt = 34,
                packageSource = InstallSource.DOWNLOADED_FILE,
                installer = "com.android.chrome",
            ),
        )
    }

    @Test
    fun `an app from the Play Store is never restricted`() {
        for (level in listOf(33, 34, 35, 36)) {
            assertEquals(
                Restriction.NO,
                InstallSource.restriction(
                    sdkInt = level,
                    packageSource = InstallSource.STORE,
                    installer = "com.android.vending",
                ),
                "API $level",
            )
        }
        assertEquals(
            Restriction.NO,
            InstallSource.restriction(
                sdkInt = 33,
                packageSource = InstallSource.UNSPECIFIED,
                installer = "com.android.vending",
            ),
            "the installer alone settles it",
        )
    }

    @Test
    fun `another store, such as F-Droid, might be restricted and cannot be told apart`() {
        assertEquals(
            Restriction.POSSIBLE,
            InstallSource.restriction(
                sdkInt = 33,
                packageSource = InstallSource.STORE,
                installer = "org.fdroid.fdroid",
            ),
        )
        assertEquals(
            Restriction.POSSIBLE,
            InstallSource.restriction(sdkInt = 35, packageSource = InstallSource.OTHER, installer = null),
            "from Android 15 an installer the phone does not vouch for is guarded as well",
        )
        assertEquals(
            Restriction.POSSIBLE,
            InstallSource.restriction(sdkInt = 33, packageSource = null, installer = null),
            "and when the phone will not say, assume it might be",
        )
    }
}
