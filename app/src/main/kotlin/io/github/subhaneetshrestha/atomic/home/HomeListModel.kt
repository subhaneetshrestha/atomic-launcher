package io.github.subhaneetshrestha.atomic.home

import io.github.subhaneetshrestha.atomic.apps.AppEntry
import io.github.subhaneetshrestha.atomic.settings.HomeSettings

/** One row of the home list; [label] is the override if there is one, else the system label. */
data class HomeRow(
    val entry: AppEntry,
    val label: String,
)

/** Pure mapping from the app snapshot plus settings to the rows on screen. */
object HomeListModel {
    const val MAX_ROWS = 16

    fun build(
        entries: List<AppEntry>,
        settings: HomeSettings,
    ): List<HomeRow> {
        val chosen =
            if (settings.homeApps.isNotEmpty()) {
                val byKey = entries.associateBy { it.key }
                settings.homeApps
                    .distinct()
                    .mapNotNull { byKey[it] }
                    .take(MAX_ROWS)
            } else {
                // Nothing configured yet: the first visible apps alphabetically, so the screen is never blank.
                entries.filterNot { it.key in settings.hidden }.take(settings.homeAppCount.coerceIn(0, MAX_ROWS))
            }
        return chosen.map { HomeRow(it, settings.labelOverrides[it.key] ?: it.label) }
    }
}
