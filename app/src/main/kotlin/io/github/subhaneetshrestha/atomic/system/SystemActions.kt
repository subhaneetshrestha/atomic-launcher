package io.github.subhaneetshrestha.atomic.system

import android.accessibilityservice.AccessibilityService
import io.github.subhaneetshrestha.atomic.core.theme.BuiltinId

/**
 * The actions only a privileged component can perform, and what Android calls each of them. Which
 * ones exist depends on the version: locking the screen and taking a screenshot arrived in Android
 * 9, the rest have always been there.
 *
 * Pure, so the gating is settled in tests rather than on six emulators.
 */
object SystemActions {
    /** An instruction older than this is stale: the user has moved on, and it is dropped. */
    const val ISSUE_WINDOW_MS = 2_000L

    private val ACTIONS: Map<BuiltinId, Int> =
        mapOf(
            BuiltinId.NOTIFICATION_SHADE to AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
            BuiltinId.QUICK_SETTINGS to AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
            BuiltinId.RECENTS to AccessibilityService.GLOBAL_ACTION_RECENTS,
            BuiltinId.POWER_MENU to AccessibilityService.GLOBAL_ACTION_POWER_DIALOG,
            BuiltinId.LOCK_SCREEN to AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN,
            BuiltinId.SCREENSHOT to AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT,
        )

    /** Android 9 is where an accessibility service could first lock the screen or take a screenshot. */
    private const val PIE = 28

    private val MIN_SDK: Map<BuiltinId, Int> =
        mapOf(BuiltinId.LOCK_SCREEN to PIE, BuiltinId.SCREENSHOT to PIE)

    fun globalAction(id: BuiltinId): Int? = ACTIONS[id]

    fun minSdk(id: BuiltinId): Int = MIN_SDK[id] ?: 0

    /** Everything this Android version can be asked of the service, once it is running. */
    fun supported(sdkInt: Int): Set<BuiltinId> = ACTIONS.keys.filter { sdkInt >= minSdk(it) }.toSet()

    /**
     * Everything the launcher can offer at all. Locking is here on every version, including the
     * ones whose accessibility service cannot do it: a device administrator can, and below
     * Android 9 that is the only way. Offering it as "not in this version" there would be wrong.
     */
    fun offered(sdkInt: Int): Set<BuiltinId> = supported(sdkInt) + BuiltinId.LOCK_SCREEN

    fun isFresh(
        issuedAt: Long,
        now: Long,
    ): Boolean = issuedAt in (now - ISSUE_WINDOW_MS)..now
}
