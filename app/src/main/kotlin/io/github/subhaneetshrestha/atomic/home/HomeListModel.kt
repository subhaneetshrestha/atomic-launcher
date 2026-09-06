package io.github.subhaneetshrestha.atomic.home

import io.github.subhaneetshrestha.atomic.apps.AppEntry
import io.github.subhaneetshrestha.atomic.settings.HomeSettings

/** One row of the home list. */
data class HomeRow(
    val entry: AppEntry,
)

/** Pure mapping from the app snapshot plus settings to the rows on screen. */
object HomeListModel {
    const val MAX_ROWS = 16

    fun build(
        entries: List<AppEntry>,
        settings: HomeSettings,
    ): List<HomeRow> {
        if (settings.homeApps.isNotEmpty()) {
            val byKey = entries.associateBy { it.key }
            return settings.homeApps
                .distinct()
                .mapNotNull { byKey[it] }
                .take(MAX_ROWS)
                .map(::HomeRow)
        }
        // Nothing configured yet: the first apps alphabetically, so the screen is never blank.
        return entries.take(settings.homeAppCount.coerceIn(0, MAX_ROWS)).map(::HomeRow)
    }
}
