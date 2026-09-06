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
    val appearance: Appearance = Appearance(),
)

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
)

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
