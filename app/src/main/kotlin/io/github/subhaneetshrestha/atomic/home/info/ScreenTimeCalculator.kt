package io.github.subhaneetshrestha.atomic.home.info

/** An app coming to the front or leaving it, as Android's usage events report it. */
data class ForegroundEvent(
    val packageName: String,
    val atMillis: Long,
    val resumed: Boolean,
)

/**
 * How long the phone has been in use today: the union of the stretches an app spent in front,
 * rather than the sum of them, because two apps can overlap across a handover and counting both
 * would say more time has passed than has.
 *
 * The launcher's own time is left out: a home screen is what is showing between two apps, and
 * counting it would make screen time meaningless.
 *
 * Pure. The awkward parts — an app that was already in front at midnight, one still in front now,
 * a pause with no resume before it — are settled here rather than on a phone at 23:59.
 */
object ScreenTimeCalculator {
    fun total(
        events: List<ForegroundEvent>,
        from: Long,
        now: Long,
        exclude: Set<String> = emptySet(),
    ): Long {
        if (now <= from) return 0
        val started = HashMap<String, Long>()
        val intervals = ArrayList<LongRange>()
        for (event in events.sortedBy { it.atMillis }) {
            if (event.packageName in exclude) continue
            val at = event.atMillis.coerceIn(from, now)
            if (event.resumed) {
                started.putIfAbsent(event.packageName, at)
            } else {
                // A pause with nothing before it means the app was already in front at midnight.
                val begun = started.remove(event.packageName) ?: from
                if (at > begun) intervals += begun..at
            }
        }
        for (begun in started.values) if (now > begun) intervals += begun..now
        return merged(intervals)
    }

    /** Overlapping stretches count once. */
    private fun merged(intervals: List<LongRange>): Long {
        if (intervals.isEmpty()) return 0
        val ordered = intervals.sortedBy { it.first }
        var total = 0L
        var start = ordered.first().first
        var end = ordered.first().last
        for (interval in ordered.drop(1)) {
            if (interval.first > end) {
                total += end - start
                start = interval.first
                end = interval.last
            } else if (interval.last > end) {
                end = interval.last
            }
        }
        return total + (end - start)
    }
}
