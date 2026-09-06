package io.github.subhaneetshrestha.atomic.settings.grant

import android.content.Context
import android.content.Intent
import io.github.subhaneetshrestha.atomic.R
import io.github.subhaneetshrestha.atomic.core.theme.ConsentKind
import io.github.subhaneetshrestha.atomic.notifications.NotificationAccess

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
            isGranted = NotificationAccess::isGranted,
            isSupported = NotificationAccess::isSupported,
            intents = { context ->
                val component = NotificationAccess.component(context)
                NotificationAccess.routes(android.os.Build.VERSION.SDK_INT).map {
                    NotificationAccess.intent(it, component)
                }
            },
        )

    /** The later phases add the accessibility service, usage access and device admin here. */
    fun of(kind: ConsentKind): GrantSpec =
        when (kind) {
            ConsentKind.NOTIFICATION_ACCESS -> notificationAccess
            else -> notificationAccess
        }
}
