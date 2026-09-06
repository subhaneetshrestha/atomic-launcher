package io.github.subhaneetshrestha.atomic.core.theme

/** Pure edits the settings screens and the app menu apply through SettingsStore.update. */
object SettingsEdits {
    /**
     * Binds [action] to [surface]. [Action.None] is stored explicitly, so an unbound gesture stays
     * unbound; a null [action] removes the entry, which brings back the shipped default.
     */
    fun bind(
        settings: Settings,
        surface: BindingSurface,
        action: Action?,
    ): Settings =
        when (surface) {
            is BindingSurface.Gesture -> {
                val current = settings.gestures.bindings
                val next = LinkedHashMap(current)
                if (action == null) next.remove(surface.id.key) else next[surface.id.key] = action
                if (next == current) settings else settings.copy(gestures = settings.gestures.copy(bindings = next))
            }

            is BindingSurface.InfoTap -> {
                val line = settings.homeInfo.line(surface.id)
                if (line.onTap ==
                    action
                ) {
                    settings
                } else {
                    settings.copy(homeInfo = settings.homeInfo.withLine(surface.id, line.copy(onTap = action)))
                }
            }

            is BindingSurface.InfoLongPress -> {
                val line = settings.homeInfo.line(surface.id)
                if (line.onLongPress == action) {
                    settings
                } else {
                    settings.copy(homeInfo = settings.homeInfo.withLine(surface.id, line.copy(onLongPress = action)))
                }
            }
        }

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

    /** Whether [app] shows a badge. Storing the exceptions means a new app badges by default. */
    fun setBadges(
        settings: Settings,
        app: PackageRef,
        shown: Boolean,
    ): Settings {
        val current = settings.notifications.perAppDisabled
        val next =
            if (shown) {
                current.filterNot { it == app }
            } else if (app in current) {
                current
            } else {
                current + app
            }
        if (next == current) return settings
        return settings.copy(notifications = settings.notifications.copy(perAppDisabled = next))
    }

    /**
     * Writes down when the user agreed to a special access, the first time they did. Kept so the
     * app can say what it was given and when, and never used to decide whether it still has it:
     * that is always read from the system.
     */
    fun recordConsent(
        settings: Settings,
        kind: ConsentKind,
        atMillis: Long,
    ): Settings {
        if (settings.consents.containsKey(kind.key)) return settings
        return settings.copy(consents = settings.consents + (kind.key to atMillis))
    }

    private fun HomeEntry.matches(ref: AppRef) = component == ref.component && user == ref.user
}
