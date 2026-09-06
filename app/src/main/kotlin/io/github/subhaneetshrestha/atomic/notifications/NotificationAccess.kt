package io.github.subhaneetshrestha.atomic.notifications

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService

/** Ways to the screen where notification access is granted, newest first. */
enum class AccessRoute {
    /** Android 11 and later: the page for this one app, with the switch on it. */
    LISTENER_DETAIL,

    /** The list of every app that has asked; the user finds ours in it. */
    LISTENER_LIST,

    /** Settings itself, when the phone has neither of the above. */
    ALL_SETTINGS,
}

/**
 * Whether the user has given the launcher notification access, and how to take them to the switch.
 *
 * The grant is the user's to make in Settings: there is no permission to request and no dialog we
 * can raise. All this does is read the state and open the right page.
 */
object NotificationAccess {
    /** The secure setting Android keeps the granted listeners in; the only way to read it on API 26. */
    private const val ENABLED_LISTENERS = "enabled_notification_listeners"

    /** Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, which is API 30 and later. */
    private const val ACTION_LISTENER_DETAIL = "android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS"
    private const val EXTRA_LISTENER_COMPONENT = "android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME"

    /** Notification listeners are never bound on a low-RAM phone before Android 11. */
    private const val LOW_RAM_SUPPORTED_SDK = 30

    /** Android 11 is where a single app's page can be opened directly. */
    private const val DETAIL_PAGE_SDK = 30

    /** NotificationManager.isNotificationListenerAccessGranted arrived in Android 8.1. */
    private const val QUERYABLE_SDK = 27

    fun component(context: Context): ComponentName = ComponentName(context, BadgeNotificationListener::class.java)

    fun isGranted(context: Context): Boolean {
        val component = component(context)
        if (Build.VERSION.SDK_INT >= QUERYABLE_SDK) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            return manager.isNotificationListenerAccessGranted(component)
        }
        val listed = Settings.Secure.getString(context.contentResolver, ENABLED_LISTENERS)
        return isListed(listed, forms(component))
    }

    fun isSupported(context: Context): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java)
        return isSupported(Build.VERSION.SDK_INT, lowRam = manager?.isLowRamDevice == true)
    }

    fun isSupported(
        sdkInt: Int,
        lowRam: Boolean,
    ): Boolean = !lowRam || sdkInt >= LOW_RAM_SUPPORTED_SDK

    /** Both spellings of our component: the system writes whichever the app used. */
    fun forms(component: ComponentName): Set<String> =
        setOf(component.flattenToString(), component.flattenToShortString())

    fun isListed(
        enabled: String?,
        forms: Set<String>,
    ): Boolean = enabled?.split(':')?.any { it.trim() in forms } == true

    fun routes(sdkInt: Int): List<AccessRoute> =
        if (sdkInt >= DETAIL_PAGE_SDK) {
            listOf(AccessRoute.LISTENER_DETAIL, AccessRoute.LISTENER_LIST, AccessRoute.ALL_SETTINGS)
        } else {
            listOf(AccessRoute.LISTENER_LIST, AccessRoute.ALL_SETTINGS)
        }

    fun intent(
        route: AccessRoute,
        component: ComponentName,
    ): Intent =
        when (route) {
            AccessRoute.LISTENER_DETAIL -> {
                Intent(ACTION_LISTENER_DETAIL)
                    .putExtra(EXTRA_LISTENER_COMPONENT, component.flattenToString())
            }

            AccessRoute.LISTENER_LIST -> {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }

            AccessRoute.ALL_SETTINGS -> {
                Intent(Settings.ACTION_SETTINGS)
            }
        }

    /**
     * Ask the system to bind the listener again. Needed after the user grants access while the
     * app is running, and after the listener has asked to be unbound because badges were off.
     */
    fun rebind(context: Context) {
        runCatching { NotificationListenerService.requestRebind(component(context)) }
    }
}
