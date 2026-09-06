package io.github.subhaneetshrestha.atomic.apps

/** One launchable activity as the user sees it. [label] is the trimmed launcher label, cached per snapshot. */
data class AppEntry(
    val key: AppKey,
    val label: String,
    val isSuspended: Boolean = false,
    /** Part of the system image: cannot be uninstalled, only disabled. */
    val isSystem: Boolean = false,
)
