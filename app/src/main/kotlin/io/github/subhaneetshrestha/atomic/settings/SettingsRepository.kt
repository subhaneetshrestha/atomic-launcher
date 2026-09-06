package io.github.subhaneetshrestha.atomic.settings

import android.content.Context
import android.os.StrictMode
import io.github.subhaneetshrestha.atomic.core.theme.Settings
import io.github.subhaneetshrestha.atomic.core.theme.SettingsStore
import io.github.subhaneetshrestha.atomic.util.Logs
import io.github.subhaneetshrestha.atomic.util.Threads
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The app's settings: the persisted document ([settings]) plus the home screen's projection of it
 * ([current]). Loaded synchronously when created (one small file) so the first frame needs no
 * loading state. Main thread only, except the write that the store schedules on [Threads.io].
 */
class SettingsRepository(
    context: Context,
) : SettingsSource {
    private val store =
        SettingsStore(AtomicFileStorage(context.applicationContext.filesDir), HandlerScheduler(Threads.io))
    private val viewListeners = CopyOnWriteArrayList<SettingsSource.Listener>()
    private val documentListeners = CopyOnWriteArrayList<(old: Settings, new: Settings) -> Unit>()

    val settings: Settings get() = store.current

    override var current: HomeSettings = store.current.toHomeSettings()
        private set

    init {
        if (store.loadWarnings.isNotEmpty()) Logs.w(TAG, "settings loaded with corrections: ${store.loadWarnings}")
        store.addListener { old, new ->
            current = new.toHomeSettings()
            for (listener in documentListeners) listener(old, new)
            for (listener in viewListeners) listener.onSettingsChanged(current)
        }
    }

    fun update(transform: (Settings) -> Settings) = store.update(transform)

    /** Writes pending changes now (onStop, setup done). A few kilobytes, so the main-thread write is deliberate. */
    fun flush() {
        val policy = StrictMode.allowThreadDiskWrites()
        try {
            store.flush()
        } finally {
            StrictMode.setThreadPolicy(policy)
        }
    }

    fun addDocumentListener(listener: (old: Settings, new: Settings) -> Unit) {
        documentListeners.addIfAbsent(listener)
    }

    fun removeDocumentListener(listener: (old: Settings, new: Settings) -> Unit) {
        documentListeners.remove(listener)
    }

    override fun addListener(listener: SettingsSource.Listener) {
        viewListeners.addIfAbsent(listener)
    }

    override fun removeListener(listener: SettingsSource.Listener) {
        viewListeners.remove(listener)
    }

    private companion object {
        const val TAG = "SettingsRepository"
    }
}
