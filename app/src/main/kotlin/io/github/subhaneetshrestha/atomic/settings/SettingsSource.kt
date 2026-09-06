package io.github.subhaneetshrestha.atomic.settings

/** Where the home screen reads its view of the settings from and how it learns about changes. */
interface SettingsSource {
    val current: HomeSettings

    fun addListener(listener: Listener)

    fun removeListener(listener: Listener)

    fun interface Listener {
        fun onSettingsChanged(settings: HomeSettings)
    }
}
