package io.github.subhaneetshrestha.atomic.util

import android.content.ComponentName

/**
 * The colon-separated lists Android keeps its granted services in, for notification access and for
 * accessibility. A component appears in whichever spelling the app that registered it used, so
 * both are checked.
 */
object ComponentList {
    fun forms(component: ComponentName): Set<String> =
        setOf(component.flattenToString(), component.flattenToShortString())

    fun isListed(
        value: String?,
        forms: Set<String>,
    ): Boolean = value?.split(':')?.any { it.trim() in forms } == true
}
