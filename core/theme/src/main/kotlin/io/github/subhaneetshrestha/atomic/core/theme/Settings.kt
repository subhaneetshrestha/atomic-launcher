package io.github.subhaneetshrestha.atomic.core.theme

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The device-local settings document (`filesDir/settings.json`). Rule: theme = how it looks
 * (shareable, embedded as [theme]); settings = what is shown and what it does (this document).
 * Every field has a default so any older or partial document decodes.
 */
@Serializable
data class Settings(
    val schema: Int = SettingsCodec.SCHEMA,
    val app: AppState = AppState(),
    val theme: Theme = BuiltinThemes.ink,
    val home: HomeConfig = HomeConfig(),
    /** Excluded from the drawer and search; may still sit on the home list. */
    val hidden: List<AppRef> = emptyList(),
    val renames: List<Rename> = emptyList(),
    val homeInfo: HomeInfoConfig = HomeInfoConfig(),
    val gestures: GestureConfig = GestureConfig(),
    val appearance: Appearance = Appearance(),
) {
    /** What this surface does: the stored binding, else what the launcher ships, else nothing. */
    fun binding(surface: BindingSurface): Action =
        when (surface) {
            is BindingSurface.Gesture -> gestures.binding(surface.id)
            is BindingSurface.InfoTap -> homeInfo.tap(surface.id)
            is BindingSurface.InfoLongPress -> homeInfo.longPress(surface.id)
        }
}

@Serializable
data class AppState(
    val lastVersionCode: Int = 0,
    val setupDone: Boolean = false,
)

/** A launchable activity by flattened component name and user serial (0 = the primary user). */
@Serializable
data class AppRef(
    val component: String,
    val user: Long = 0,
)

@Serializable
data class HomeConfig(
    /** Ordered rows of the home list; empty means "the first [fallbackCount] apps alphabetically". */
    val entries: List<HomeEntry> = emptyList(),
    val fallbackCount: Int = 6,
)

@Serializable
data class HomeEntry(
    val component: String,
    val user: Long = 0,
    /** Per-row label override; wins over [Rename] and the system label. */
    val label: String? = null,
)

@Serializable
data class Rename(
    val component: String,
    val user: Long = 0,
    val label: String,
)

@Serializable
data class HomeInfoConfig(
    val position: InfoPosition = InfoPosition.ABOVE,
    val clock: InfoLine = InfoLine(enabled = true),
    val date: InfoLine = InfoLine(enabled = true),
    val battery: InfoLine = InfoLine(enabled = true),
    val screenTime: InfoLine = InfoLine(enabled = false),
) {
    fun line(id: InfoLineId): InfoLine =
        when (id) {
            InfoLineId.CLOCK -> clock
            InfoLineId.DATE -> date
            InfoLineId.BATTERY -> battery
            InfoLineId.SCREEN_TIME -> screenTime
        }

    fun withLine(
        id: InfoLineId,
        line: InfoLine,
    ): HomeInfoConfig =
        when (id) {
            InfoLineId.CLOCK -> copy(clock = line)
            InfoLineId.DATE -> copy(date = line)
            InfoLineId.BATTERY -> copy(battery = line)
            InfoLineId.SCREEN_TIME -> copy(screenTime = line)
        }

    /** The stored tap binding, else the one the line ships with. */
    fun tap(id: InfoLineId): Action = line(id).onTap ?: id.defaultTap

    fun longPress(id: InfoLineId): Action = line(id).onLongPress ?: Action.None
}

/** The four lines that can sit above or below the app list. [key] is the stored form. */
enum class InfoLineId(
    val key: String,
    val defaultTap: Action,
) {
    CLOCK("clock", Action.Builtin(BuiltinId.ALARMS)),
    DATE("date", Action.Builtin(BuiltinId.CALENDAR_TODAY)),
    BATTERY("battery", Action.Builtin(BuiltinId.BATTERY_SETTINGS)),
    SCREEN_TIME("screen_time", Action.None),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun fromKey(key: String): InfoLineId? = byKey[key]
    }
}

@Serializable
enum class InfoPosition {
    @SerialName("above")
    ABOVE,

    @SerialName("below")
    BELOW,
}

@Serializable
data class InfoLine(
    val enabled: Boolean = false,
    /** Line-specific format; null means the locale default. */
    val format: String? = null,
    /** null means the action the line ships with; [Action.None] means the user unbound it. */
    val onTap: Action? = null,
    val onLongPress: Action? = null,
)

@Serializable
data class Appearance(
    val nightMode: NightMode = NightMode.SYSTEM,
)

@Serializable
enum class NightMode {
    @SerialName("system")
    SYSTEM,

    @SerialName("light")
    LIGHT,

    @SerialName("dark")
    DARK,
}
