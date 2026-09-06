package io.github.subhaneetshrestha.atomic.core.theme

/**
 * The in-memory source of truth for settings. Reads the document once when created, applies
 * updates synchronously (listeners are told at once), and writes the whole document back after a
 * short debounce so bursts of edits cost one write. Call [flush] when the app goes to the
 * background so nothing is lost.
 *
 * Threading: [update], [flush] and the listeners belong to one thread (the main thread in the
 * app); the scheduled write may run on another thread and only reads the latest snapshot.
 */
class SettingsStore(
    private val storage: SettingsStorage,
    private val scheduler: WriteScheduler,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
) {
    fun interface Listener {
        fun onSettingsChanged(
            old: Settings,
            new: Settings,
        )
    }

    @Volatile
    var current: Settings = Settings()
        private set

    /** Corrections made while reading the stored document; empty for a fresh install. */
    val loadWarnings: List<Warning>

    private val listeners = mutableListOf<Listener>()
    private var cancelPendingWrite: (() -> Unit)? = null

    /** A document from a newer app is copied aside only when we are about to overwrite it. */
    @Volatile
    private var preserveBeforeWrite: Pair<String, String>? = null

    @Volatile
    private var dirty = false

    init {
        val text = storage.read()
        loadWarnings =
            when (val result = text?.let { SettingsCodec.decodeSettings(it) }) {
                null -> {
                    emptyList()
                }

                is DecodeResult.Ok -> {
                    current = result.value
                    if (result.warnings.any { it.path == "schema" }) {
                        preserveBeforeWrite = text to "v${SettingsCodec.documentSchema(text) ?: "unknown"}"
                    }
                    result.warnings
                }

                is DecodeResult.Corrupt -> {
                    storage.preserve(text, "corrupt")
                    listOf(Warning("document", "unreadable (${result.reason}); using defaults, original kept aside"))
                }

                is DecodeResult.Unsupported -> {
                    storage.preserve(text, "v${result.schema}")
                    listOf(
                        Warning(
                            "document",
                            "written by a newer app (schema ${result.schema}); using defaults, original kept aside",
                        ),
                    )
                }
            }
    }

    fun update(transform: (Settings) -> Settings) {
        val old = current
        val new = transform(old)
        if (new == old) return
        current = new
        dirty = true
        for (listener in listeners.toList()) listener.onSettingsChanged(old, new)
        cancelPendingWrite?.invoke()
        cancelPendingWrite = scheduler.schedule(debounceMs) { writeNow() }
    }

    /** Writes any pending change right away. */
    fun flush() {
        cancelPendingWrite?.invoke()
        cancelPendingWrite = null
        if (dirty) writeNow()
    }

    fun addListener(listener: Listener) {
        if (listener !in listeners) listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    private fun writeNow() {
        dirty = false
        preserveBeforeWrite?.let { (text, tag) ->
            storage.preserve(text, tag)
            preserveBeforeWrite = null
        }
        storage.write(SettingsCodec.encodeSettings(current))
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 300L
    }
}
