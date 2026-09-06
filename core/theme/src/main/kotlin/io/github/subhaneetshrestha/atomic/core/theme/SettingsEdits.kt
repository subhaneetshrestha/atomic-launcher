package io.github.subhaneetshrestha.atomic.core.theme

/** Pure edits the settings screens and the app menu apply through SettingsStore.update. */
object SettingsEdits {
    /**
     * Before the first edit, an empty home list means "the first apps alphabetically". Editing must
     * not silently replace those rows with just the edited one, so they become explicit entries first.
     */
    fun materializeHome(
        settings: Settings,
        visible: List<AppRef>,
    ): Settings {
        if (settings.home.entries.isNotEmpty()) return settings
        val entries = visible.take(HomeLimits.MAX_ROWS).map { HomeEntry(it.component, it.user) }
        return settings.copy(home = settings.home.copy(entries = entries))
    }

    /** Appends [ref] to the home list unless it is already there or the list is full. */
    fun addToHome(
        settings: Settings,
        ref: AppRef,
    ): Settings {
        val entries = settings.home.entries
        if (entries.any { it.matches(ref) } || entries.size >= HomeLimits.MAX_ROWS) return settings
        return settings.copy(home = settings.home.copy(entries = entries + HomeEntry(ref.component, ref.user)))
    }

    fun removeFromHome(
        settings: Settings,
        ref: AppRef,
    ): Settings {
        val entries = settings.home.entries
        val remaining = entries.filterNot { it.matches(ref) }
        return if (remaining.size ==
            entries.size
        ) {
            settings
        } else {
            settings.copy(home = settings.home.copy(entries = remaining))
        }
    }

    /** Moves the entry at [from] so that it sits at [to]; positions outside the list are ignored. */
    fun moveHomeEntry(
        settings: Settings,
        from: Int,
        to: Int,
    ): Settings {
        val entries = settings.home.entries
        if (from == to || from !in entries.indices || to !in entries.indices) return settings
        val reordered = entries.toMutableList()
        val entry = reordered.removeAt(from)
        reordered.add(to, entry)
        return settings.copy(home = settings.home.copy(entries = reordered))
    }

    /** Sets the launcher-local label of [ref]; a blank or unreadable [label] removes any existing rename. */
    fun rename(
        settings: Settings,
        ref: AppRef,
        label: String?,
    ): Settings {
        val others = settings.renames.filterNot { it.component == ref.component && it.user == ref.user }
        val cleaned = label?.let(Labels::clean)
        val renames = if (cleaned == null) others else others + Rename(ref.component, ref.user, cleaned)
        return if (renames == settings.renames) settings else settings.copy(renames = renames)
    }

    fun labelOverride(
        settings: Settings,
        ref: AppRef,
    ): String? = settings.renames.firstOrNull { it.component == ref.component && it.user == ref.user }?.label

    fun setHidden(
        settings: Settings,
        ref: AppRef,
        hidden: Boolean,
    ): Settings {
        val without = settings.hidden.filterNot { it == ref }
        val updated = if (hidden) without + ref else without
        return if (updated == settings.hidden) settings else settings.copy(hidden = updated)
    }

    fun isHidden(
        settings: Settings,
        ref: AppRef,
    ): Boolean = ref in settings.hidden

    private fun HomeEntry.matches(ref: AppRef) = component == ref.component && user == ref.user
}
