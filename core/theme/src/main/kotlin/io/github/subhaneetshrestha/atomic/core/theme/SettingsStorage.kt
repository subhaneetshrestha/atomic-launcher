package io.github.subhaneetshrestha.atomic.core.theme

/** Where the settings document lives. The Android implementation is an AtomicFile in filesDir. */
interface SettingsStorage {
    /** The stored document, or null when there is none yet. */
    fun read(): String?

    fun write(text: String)

    /** Keeps a copy of a document that is about to be overwritten, e.g. a corrupt one or one from a newer app. */
    fun preserve(
        text: String,
        tag: String,
    )
}

/** Runs a write later. The Android implementation posts to a background Handler. */
fun interface WriteScheduler {
    /** Schedules [action] after [delayMs] and returns a handle that cancels it. */
    fun schedule(
        delayMs: Long,
        action: () -> Unit,
    ): () -> Unit
}
