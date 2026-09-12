package io.github.subhaneetshrestha.atomic.settings.grant

import android.content.Context
import android.content.Intent
import io.github.subhaneetshrestha.atomic.R
import io.github.subhaneetshrestha.atomic.core.theme.ConsentKind
import io.github.subhaneetshrestha.atomic.home.info.UsageAccess
import io.github.subhaneetshrestha.atomic.notifications.NotificationAccess
import io.github.subhaneetshrestha.atomic.system.AccessibilityAccess
import io.github.subhaneetshrestha.atomic.system.DeviceAdminLock

/**
 * One special access, and everything the disclosure screen needs to ask for it: what is accessed,
 * what it is used for, what the limits are, how to read whether it has been granted, and the ways
 * to the Settings page that grants it.
 *
 * The three texts are shown together, in the app, immediately before Settings is opened, and the
 * user has to tap Continue: that is what Google's user-data policy requires of a prominent
 * disclosure, and it is also the least surprising thing to do.
 */
data class GrantSpec(
    val kind: ConsentKind,
    val titleRes: Int,
    val whatRes: Int,
    val whyRes: Int,
    val limitsRes: Int,
    /** What to say when the user comes back having granted it. */
    val grantedRes: Int,
    val isGranted: (Context) -> Boolean,
    val isSupported: (Context) -> Boolean,
    val intents: (Context) -> List<Intent>,
)

object Grants {
    val notificationAccess =
        GrantSpec(
            kind = ConsentKind.NOTIFICATION_ACCESS,
            titleRes = R.string.grant_notification_title,
            whatRes = R.string.grant_notification_what,
            whyRes = R.string.grant_notification_why,
            limitsRes = R.string.grant_notification_limits,
            grantedRes = R.string.grant_granted,
            isGranted = NotificationAccess::isGranted,
            isSupported = NotificationAccess::isSupported,
            intents = { context ->
                val component = NotificationAccess.component(context)
                NotificationAccess.routes(android.os.Build.VERSION.SDK_INT).map {
                    NotificationAccess.intent(it, component)
                }
            },
        )

    /**
     * The six actions Android reserves for an accessibility service. The service is the launcher's
     * own, runs in its own process, and is configured to be told nothing.
     */
    val accessibility =
        GrantSpec(
            kind = ConsentKind.ACCESSIBILITY,
            titleRes = R.string.grant_accessibility_title,
            whatRes = R.string.grant_accessibility_what,
            whyRes = R.string.grant_accessibility_why,
            limitsRes = R.string.grant_accessibility_limits,
            grantedRes = R.string.grant_accessibility_granted,
            isGranted = AccessibilityAccess::isEnabled,
            isSupported = { true },
            intents = { AccessibilityAccess.intents() },
        )

    /** Today's screen time, worked out from Android's own record of what came to the front. */
    val usageAccess =
        GrantSpec(
            kind = ConsentKind.USAGE_ACCESS,
            titleRes = R.string.grant_usage_title,
            whatRes = R.string.grant_usage_what,
            whyRes = R.string.grant_usage_why,
            limitsRes = R.string.grant_usage_limits,
            grantedRes = R.string.grant_usage_granted,
            isGranted = UsageAccess::isGranted,
            isSupported = { true },
            intents = { UsageAccess.intents() },
        )

    /** One power, force-lock, and the only way to lock the screen at all below Android 9. */
    val deviceAdmin =
        GrantSpec(
            kind = ConsentKind.DEVICE_ADMIN,
            titleRes = R.string.grant_admin_title,
            whatRes = R.string.grant_admin_what,
            whyRes = R.string.grant_admin_why,
            limitsRes = R.string.grant_admin_limits,
            grantedRes = R.string.grant_admin_granted,
            isGranted = DeviceAdminLock::isActive,
            isSupported = { true },
            intents = { context -> listOf(DeviceAdminLock.addIntent(context)) },
        )

    fun of(kind: ConsentKind): GrantSpec =
        when (kind) {
            ConsentKind.NOTIFICATION_ACCESS -> notificationAccess
            ConsentKind.ACCESSIBILITY -> accessibility
            ConsentKind.USAGE_ACCESS -> usageAccess
            ConsentKind.DEVICE_ADMIN -> deviceAdmin
        }
}
