package io.github.subhaneetshrestha.atomic.notifications

import java.util.concurrent.CopyOnWriteArrayList

/** Runs a publish a moment later, so a burst of arrivals costs one redraw rather than five. */
fun interface Coalescer {
    /** Schedules [action] after [delayMs], replacing anything already scheduled. Returns a cancel. */
    fun post(
        delayMs: Long,
        action: () -> Unit,
    ): () -> Unit
}

/**
 * What the home screen reads to draw the number beside an app's name. Counts are held as they
 * arrive, so they are there to read at once, while the screen is told about them a frame later:
 * five notifications landing together are one redraw.
 *
 * Nothing is persisted. The system rebinds a notification listener itself and hands over
 * everything outstanding, so the counts are rebuilt from scratch each time rather than kept
 * across runs where they would go stale unnoticed.
 */
class BadgeStore(
    private val coalescer: Coalescer,
    private val delayMs: Long = ONE_FRAME_MS,
) {
    fun interface Listener {
        fun onBadgesChanged()
    }

    /** Apps the user has turned badges off for. Their counts are kept, just not shown. */
    var disabled: Set<BadgeKey> = emptySet()
        set(value) {
            if (field == value) return
            field = value
            announce()
        }

    private val counts = LinkedHashMap<BadgeKey, Int>()
    private val listeners = CopyOnWriteArrayList<Listener>()
    private var cancelPending: (() -> Unit)? = null

    fun countFor(
        packageName: String,
        userSerial: Long,
    ): Int {
        val key = BadgeKey(packageName, userSerial)
        if (key in disabled) return 0
        return counts[key] ?: 0
    }

    /** Everything outstanding, as the listener reports it on connecting. */
    fun replaceAll(fresh: Map<BadgeKey, Int>) {
        if (counts == fresh) return
        counts.clear()
        counts.putAll(fresh)
        announce()
    }

    /** One app's count changed: a notification arrived, was dismissed, or was re-ranked. */
    fun update(
        key: BadgeKey,
        count: Int,
    ) {
        val previous = counts[key] ?: 0
        if (previous == count) return
        if (count <= 0) counts.remove(key) else counts[key] = count
        announce()
    }

    /** Notification access is gone: show nothing rather than something out of date. */
    fun clear() {
        if (counts.isEmpty()) return
        counts.clear()
        announce()
    }

    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private fun announce() {
        cancelPending?.invoke()
        cancelPending =
            coalescer.post(delayMs) {
                cancelPending = null
                for (listener in listeners) listener.onBadgesChanged()
            }
    }

    private companion object {
        /** About one frame at sixty a second. */
        const val ONE_FRAME_MS = 16L
    }
}
