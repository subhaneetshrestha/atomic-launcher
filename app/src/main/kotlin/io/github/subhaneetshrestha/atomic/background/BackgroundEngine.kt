package io.github.subhaneetshrestha.atomic.background

import io.github.subhaneetshrestha.atomic.core.collections.BackgroundState
import io.github.subhaneetshrestha.atomic.core.collections.CollectionParser
import io.github.subhaneetshrestha.atomic.core.collections.Rotation
import io.github.subhaneetshrestha.atomic.core.collections.SourceDetector
import io.github.subhaneetshrestha.atomic.core.collections.SourceKind
import io.github.subhaneetshrestha.atomic.core.collections.Wallhaven
import io.github.subhaneetshrestha.atomic.core.theme.CollectionConfig
import io.github.subhaneetshrestha.atomic.util.Logs
import kotlin.random.Random

/**
 * One turn of the background: decide whether it is time, read the collection if the list has gone
 * stale, choose the next image, fetch it and put it in place. Runs on a background thread, hours
 * after anyone asked for anything, so every step either succeeds or is written down for the
 * settings screen to explain later.
 *
 * The decisions themselves live in [Rotation] and are unit-tested there; this is the wiring.
 */
class BackgroundEngine(
    private val store: ImageStore,
    private val fetcher: ImageFetcher,
    private val policy: NetworkPolicy,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random,
) {
    /** Who asked. The user's own tap ignores both the clock and any backoff, but not the network rules. */
    enum class Trigger { SCHEDULED, OPPORTUNISTIC, USER }

    sealed class Outcome {
        data class Changed(
            val url: String,
            val thumbnail: Thumbnail,
        ) : Outcome()

        data object NotConfigured : Outcome()

        data object NotDue : Outcome()

        /** Waiting out the backoff after earlier failures. */
        data object Waiting : Outcome()

        data class Skipped(
            val reason: SkipReason,
        ) : Outcome()

        data class Failed(
            val reason: String,
        ) : Outcome()
    }

    fun run(
        config: CollectionConfig,
        trigger: Trigger,
    ): Outcome {
        if (!config.isConfigured) return Outcome.NotConfigured
        val now = clock()
        val intervalMs = config.intervalMinutes * MINUTE_MS
        var state = store.readState()
        if (trigger != Trigger.USER) {
            if (!Rotation.isDue(state, now, intervalMs)) return Outcome.NotDue
            if (!Rotation.mayTry(state, now)) return Outcome.Waiting
        }
        policy.skipReason(config.unmeteredOnly, trigger == Trigger.OPPORTUNISTIC)?.let { reason ->
            store.writeState(Rotation.skipped(state, reason.name))
            return Outcome.Skipped(reason)
        }

        if (!Rotation.indexIsFresh(state, config.url, now)) {
            state =
                when (val read = readCollection(state, config.url, now)) {
                    is Read.Ok -> read.state
                    is Read.Failed -> return fail(state, null, now, read.reason, intervalMs, read.permanent)
                }
        }
        val url = Rotation.next(state, config.shuffle, random)
        if (url == null) {
            return fail(state, null, now, "the collection has no images left to show", intervalMs, permanent = false)
        }

        val target = store.newIncoming() ?: return fail(state, null, now, "no room to download", intervalMs, false)
        when (val fetched = fetcher.fetchImage(url, target)) {
            is ImageFetcher.Outcome.Failed -> {
                return fail(state, url, now, fetched.reason, intervalMs, fetched.permanent)
            }

            is ImageFetcher.Outcome.Image -> {
                Unit
            }

            else -> {
                return fail(state, url, now, "the server answered with nothing", intervalMs, false)
            }
        }
        // Decoding the small copy proves the bytes are an image before they replace what is on
        // screen, and it is the same copy the legibility of the text is judged from.
        val thumbnail = ImageDecoders.thumbnail(target)
        if (thumbnail == null) {
            target.delete()
            return fail(state, url, now, "what arrived is not an image this phone can show", intervalMs, true)
        }
        if (!store.commit()) return fail(state, url, now, "the image could not be put in place", intervalMs, false)
        state = Rotation.succeeded(state, url, now)
        store.writeState(state)
        Logs.d(TAG) { "background changed to $url" }
        return Outcome.Changed(url, thumbnail)
    }

    private sealed class Read {
        class Ok(
            val state: BackgroundState,
        ) : Read()

        class Failed(
            val reason: String,
            val permanent: Boolean,
        ) : Read()
    }

    /** Reads the collection into a list of image addresses, or says why it could not. */
    private fun readCollection(
        state: BackgroundState,
        sourceUrl: String,
        now: Long,
    ): Read {
        if (SourceDetector.byUrl(sourceUrl) == SourceKind.IMAGE) {
            return Read.Ok(Rotation.withIndex(state, sourceUrl, listOf(sourceUrl), now))
        }
        val requestUrl =
            if (Wallhaven.isWallhavenPage(sourceUrl)) {
                Wallhaven.apiUrlFor(sourceUrl)
                    ?: return Read.Failed("this Wallhaven address cannot be read without an account", true)
            } else {
                sourceUrl
            }
        val cached = state.index?.takeIf { it.sourceUrl == sourceUrl }
        return when (val fetched = fetcher.fetchIndex(requestUrl, cached?.etag, cached?.lastModified)) {
            is ImageFetcher.Outcome.Failed -> {
                Read.Failed(fetched.reason, fetched.permanent)
            }

            ImageFetcher.Outcome.Unchanged -> {
                Read.Ok(Rotation.indexRevalidated(state, now))
            }

            is ImageFetcher.Outcome.Index -> {
                val kind = SourceDetector.detect(fetched.contentType, fetched.body.take(SNIFF).toByteArray())
                val parsed = CollectionParser.parse(kind, fetched.body, fetched.url)
                if (parsed.warnings.isNotEmpty()) Logs.d(TAG) { "collection: ${parsed.warnings}" }
                if (parsed.isEmpty) {
                    Read.Failed(parsed.warnings.firstOrNull() ?: "the collection holds no images", permanent = false)
                } else {
                    Read.Ok(
                        Rotation.withIndex(
                            state,
                            sourceUrl,
                            parsed.urls,
                            now,
                            fetched.etag,
                            fetched.lastModified,
                        ),
                    )
                }
            }

            else -> {
                Read.Failed("the collection could not be read", permanent = false)
            }
        }
    }

    private fun fail(
        state: BackgroundState,
        url: String?,
        now: Long,
        reason: String,
        intervalMs: Long,
        permanent: Boolean,
    ): Outcome.Failed {
        store.writeState(Rotation.failed(state, url, now, reason, intervalMs, permanent))
        Logs.w(TAG, "background not changed: $reason")
        return Outcome.Failed(reason)
    }

    private companion object {
        const val MINUTE_MS = 60_000L

        /** Characters of the body [SourceDetector] needs; a signature is in the first few bytes. */
        const val SNIFF = SourceDetector.SNIFF_BYTES

        const val TAG = "BackgroundEngine"
    }
}
