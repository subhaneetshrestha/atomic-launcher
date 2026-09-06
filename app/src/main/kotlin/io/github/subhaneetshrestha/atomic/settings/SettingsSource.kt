package io.github.subhaneetshrestha.atomic.settings

/** Where the home screen reads its settings from and how it learns about changes. */
interface SettingsSource {
    val current: HomeSettings

    fun addListener(listener: Listener)

    fun removeListener(listener: Listener)

    fun interface Listener {
        fun onSettingsChanged(settings: HomeSettings)
    }
}

/** Phase 1: constants only, never changes. Replaced by the JSON-backed store in Phase 2. */
class DefaultSettingsSource(override val current: HomeSettings = HomeSettings()) : SettingsSource {
    override fun addListener(listener: SettingsSource.Listener) = Unit

    override fun removeListener(listener: SettingsSource.Listener) = Unit
}
