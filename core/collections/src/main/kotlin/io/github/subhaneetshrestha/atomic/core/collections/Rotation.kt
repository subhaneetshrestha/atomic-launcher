package io.github.subhaneetshrestha.atomic.core.collections

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** The list of images a collection held when it was last read, and when that was. */
@Serializable
data class IndexCache(
    val sourceUrl: String,
    val fetchedAt: Long,
    val urls: List<String>,
    /** Sent back on the next fetch so an unchanged list costs a header exchange, not a megabyte. */
    val etag: String? = null,
    val lastModified: String? = null,
)

/**
 * Everything the background engine remembers between runs (`filesDir/background/state.json`):
 * what is on screen, what has been shown lately, what is not worth asking for again, and how long
 * to wait after things went wrong.
 */
@Serializable
data class BackgroundState(
    val index: IndexCache? = null,
    val currentUrl: String? = null,
    val currentAt: Long = 0,
    /** Recently shown, newest first, so a small collection does not repeat itself straight away. */
    val history: List<String> = emptyList(),
    /** Addresses that failed in a way waiting will not fix: gone, forbidden, or not an image. */
    val bad: List<String> = emptyList(),
    val failures: Int = 0,
    /** No attempt before this moment; how the launcher backs off a host that is having a bad day. */
    val retryAfter: Long = 0,
    val lastError: String? = null,
    /** Why the last run did nothing, when it was a deliberate choice rather than a failure. */
    val lastSkip: String? = null,
)

/**
 * When to change the background and which image to change to. Pure: every decision is a function
 * of the stored state and the clock, so the awkward parts (an empty collection, a clock that went
 * backwards, a host that is down) are settled in tests rather than on a phone six hours from now.
 */
object Rotation {
    const val HISTORY_SIZE = 20

    const val BAD_SIZE = 50

    /** How long a fetched index is trusted before the collection is read again. */
    const val INDEX_TTL_MS = 6 * 60 * 60 * 1000L

    const val MAX_BACKOFF_MS = 24 * 60 * 60 * 1000L

    fun indexIsFresh(
        state: BackgroundState,
        sourceUrl: String,
        now: Long,
    ): Boolean {
        val index = state.index ?: return false
        if (index.sourceUrl != sourceUrl || index.urls.isEmpty()) return false
        val age = now - index.fetchedAt
        return age in 0 until INDEX_TTL_MS
    }

    /**
     * Whether enough time has passed for the next image. A clock that has gone backwards (a time
     * zone change, a user setting the date) counts as due: waiting for a moment that has already
     * happened would leave the background stuck for as long as the clock is wrong.
     */
    fun isDue(
        state: BackgroundState,
        now: Long,
        intervalMs: Long,
    ): Boolean {
        if (state.currentUrl == null) return true
        val since = now - state.currentAt
        return since < 0 || since >= intervalMs
    }

    /** Whether the backoff after a failure has run out; a wildly future moment is treated as over. */
    fun mayTry(
        state: BackgroundState,
        now: Long,
    ): Boolean = now >= state.retryAfter || state.retryAfter - now > MAX_BACKOFF_MS

    /**
     * The next image to show, or null when the collection has nothing left to offer. Shuffling
     * avoids what has been shown lately; in order, the list is simply walked, which is what
     * somebody who numbered their files meant to happen.
     */
    fun next(
        state: BackgroundState,
        shuffle: Boolean,
        random: Random = Random,
    ): String? {
        val urls =
            state.index
                ?.urls
                .orEmpty()
                .filterNot { it in state.bad }
        if (urls.isEmpty()) return null
        if (!shuffle) return urls[(urls.indexOf(state.currentUrl) + 1) % urls.size]
        val unseen = urls.filterNot { it == state.currentUrl || it in state.history }
        val pool = unseen.ifEmpty { urls.filterNot { it == state.currentUrl }.ifEmpty { urls } }
        return pool[random.nextInt(pool.size)]
    }

    fun withIndex(
        state: BackgroundState,
        sourceUrl: String,
        urls: List<String>,
        now: Long,
        etag: String? = null,
        lastModified: String? = null,
    ): BackgroundState = state.copy(index = IndexCache(sourceUrl, now, UrlRules.clean(urls), etag, lastModified))

    /** The collection answered "unchanged": the list it gave last time is good for another spell. */
    fun indexRevalidated(
        state: BackgroundState,
        now: Long,
    ): BackgroundState = state.copy(index = state.index?.copy(fetchedAt = now))

    fun succeeded(
        state: BackgroundState,
        url: String,
        now: Long,
    ): BackgroundState =
        state.copy(
            currentUrl = url,
            currentAt = now,
            history = (listOf(url) + state.history).distinct().take(HISTORY_SIZE),
            failures = 0,
            retryAfter = 0,
            lastError = null,
            lastSkip = null,
        )

    /**
     * A failure. [permanent] means the address itself is the problem, so it is remembered and
     * never asked for again; anything else is the network or the host, and only delays the next
     * attempt — doubling each time, up to a day, so a phone with no signal is not spending its
     * battery on a server that is not there.
     */
    fun failed(
        state: BackgroundState,
        url: String?,
        now: Long,
        error: String,
        intervalMs: Long,
        permanent: Boolean,
    ): BackgroundState {
        val failures = state.failures + 1
        val bad =
            if (permanent && url != null) {
                (listOf(url) + state.bad).distinct().take(BAD_SIZE)
            } else {
                state.bad
            }
        return state.copy(
            bad = bad,
            failures = failures,
            retryAfter = now + backoffMs(intervalMs, failures),
            lastError = error,
            lastSkip = null,
        )
    }

    /** A run that deliberately did nothing (metered network, Data Saver, low battery). */
    fun skipped(
        state: BackgroundState,
        reason: String,
    ): BackgroundState = state.copy(lastSkip = reason)

    /** The interval doubled once per consecutive failure, capped at a day. */
    fun backoffMs(
        intervalMs: Long,
        failures: Int,
    ): Long {
        if (failures <= 0) return 0
        val shift = (failures - 1).coerceAtMost(MAX_SHIFT)
        val grown = intervalMs.coerceAtLeast(0) shl shift
        return if (grown <= 0) MAX_BACKOFF_MS else grown.coerceAtMost(MAX_BACKOFF_MS)
    }

    /** Past this many doublings the cap has been reached whatever the interval, and the shift would overflow. */
    private const val MAX_SHIFT = 20
}
