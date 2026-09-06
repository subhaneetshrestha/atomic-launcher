package io.github.subhaneetshrestha.atomic.search

import io.github.subhaneetshrestha.atomic.apps.AppEntry
import io.github.subhaneetshrestha.atomic.apps.AppKey
import io.github.subhaneetshrestha.atomic.core.search.SearchEntry
import io.github.subhaneetshrestha.atomic.core.search.SearchIndex
import io.github.subhaneetshrestha.atomic.settings.HomeSettings

/** Builds the searchable set from the apps on the device and what the user has done to their names. */
object AppSearchIndex {
    fun build(
        entries: List<AppEntry>,
        settings: HomeSettings,
    ): SearchIndex<AppKey> =
        SearchIndex(
            entries.map { entry ->
                val renamed = settings.labelOverrides[entry.key]
                SearchEntry(
                    key = entry.key,
                    label = renamed ?: entry.label,
                    // A renamed app still answers to the name it came with.
                    aliases = if (renamed == null) emptyList() else listOf(entry.label),
                    hidden = entry.key in settings.hidden,
                )
            },
        )
}
