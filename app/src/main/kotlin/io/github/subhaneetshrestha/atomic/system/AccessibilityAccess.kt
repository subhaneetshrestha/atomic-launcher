package io.github.subhaneetshrestha.atomic.system

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import io.github.subhaneetshrestha.atomic.util.ComponentList

/**
 * Whether the user has turned the launcher's accessibility service on, and the way to the switch.
 * There is no permission to request: it is a decision made in Settings, and all this does is read
 * it and open the right page.
 */
object AccessibilityAccess {
    fun component(context: Context): ComponentName = ComponentName(context, SystemActionsService::class.java)

    fun isEnabled(context: Context): Boolean {
        val forms = ComponentList.forms(component(context))
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val bound =
            manager
                ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.any { it.id in forms } == true
        if (bound) return true
        // The list above is what is bound; the setting is what the user asked for, and the two
        // differ for a moment after the switch is flipped.
        return ComponentList.isListed(
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            forms,
        )
    }

    fun intents(): List<Intent> =
        listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))
}
