package io.github.subhaneetshrestha.atomic.background

import io.github.subhaneetshrestha.atomic.core.collections.SourceDetector
import io.github.subhaneetshrestha.atomic.core.collections.UrlRules
import io.github.subhaneetshrestha.atomic.core.collections.Wallhaven
import io.github.subhaneetshrestha.atomic.util.Logs
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import javax.net.ssl.SSLException

/**
 * Fetches a collection document or an image over https. Everything here is a rule about somebody
 * else's server: how long to wait, how big a body may be, how many times it may redirect, and
 * whether what came back is worth asking for again.
 *
 * Every hop is checked against [UrlRules], so a redirect cannot walk the launcher off https or on
 * to a file it is not allowed to read.
 */
class ImageFetcher(
    private val userAgent: String,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    sealed class Outcome {
        data class Index(
            val body: String,
            /** The first bytes as they arrived: a signature does not survive being read as text. */
            val head: ByteArray,
            val contentType: String?,
            val url: String,
            val etag: String?,
            val lastModified: String?,
        ) : Outcome() {
            override fun equals(other: Any?): Boolean = this === other

            override fun hashCode(): Int = System.identityHashCode(this)
        }

        /** The document has not changed since the validators that were sent with the request. */
        data object Unchanged : Outcome()

        data class Image(
            val bytes: Long,
            val contentType: String?,
        ) : Outcome()

        /** [permanent] means the address itself is the problem and will not come right by waiting. */
        data class Failed(
            val reason: String,
            val permanent: Boolean,
        ) : Outcome()
    }

    /** When each rationed host was last asked, so a second API source never shares one clock with Wallhaven's. */
    private val lastRequestAt = HashMap<String, Long>()

    fun fetchIndex(
        url: String,
        etag: String? = null,
        lastModified: String? = null,
        maxBytes: Int = MAX_INDEX_BYTES,
    ): Outcome {
        val opened =
            open(url) { connection ->
                connection.setRequestProperty("Accept", "application/json,*/*;q=0.8")
                etag?.let { connection.setRequestProperty("If-None-Match", it) }
                lastModified?.let { connection.setRequestProperty("If-Modified-Since", it) }
            }
        val ok = opened as? Opened.Ok ?: return (opened as Opened.Failed).outcome
        return try {
            if (ok.connection.responseCode == HTTP_NOT_MODIFIED) return Outcome.Unchanged
            val bytes =
                ok.connection.inputStream.use { read(it, maxBytes.toLong()) }
                    ?: return Outcome.Failed("the document is larger than ${maxBytes / 1024} KiB", permanent = true)
            Outcome.Index(
                body = String(bytes, Charsets.UTF_8),
                head = bytes.copyOf(minOf(bytes.size, SourceDetector.SNIFF_BYTES)),
                contentType = ok.connection.contentType,
                url = ok.url,
                etag = ok.connection.getHeaderField("ETag"),
                lastModified = ok.connection.getHeaderField("Last-Modified"),
            )
        } catch (e: IOException) {
            failed(e)
        } finally {
            ok.connection.disconnect()
        }
    }

    /** Downloads to [target], which the caller then moves into place; a failed download leaves nothing. */
    fun fetchImage(
        url: String,
        target: File,
    ): Outcome {
        val opened = open(url) { it.setRequestProperty("Accept", "image/*,*/*;q=0.8") }
        val ok = opened as? Opened.Ok ?: return (opened as Opened.Failed).outcome
        val declared = ok.connection.contentLengthLong
        if (declared > MAX_IMAGE_BYTES) {
            ok.connection.disconnect()
            return tooLarge(target)
        }
        return try {
            val written = ok.connection.inputStream.use { input -> copy(input, target) }
            when {
                written < 0 -> {
                    tooLarge(target)
                }

                written == 0L -> {
                    target.delete()
                    Outcome.Failed("the image was empty", permanent = true)
                }

                // Stopping short of the length the server promised is a broken connection, and
                // will come right; half a file must never be marked as a bad address.
                declared > 0 && written != declared -> {
                    target.delete()
                    Outcome.Failed("only $written bytes of $declared arrived", permanent = false)
                }

                else -> {
                    Outcome.Image(written, ok.connection.contentType)
                }
            }
        } catch (e: IOException) {
            target.delete()
            failed(e)
        } finally {
            ok.connection.disconnect()
        }
    }

    private fun tooLarge(target: File): Outcome {
        target.delete()
        return Outcome.Failed("the image is larger than ${MAX_IMAGE_BYTES / (1024 * 1024)} MiB", permanent = true)
    }

    private sealed class Opened {
        class Ok(
            val connection: HttpURLConnection,
            val url: String,
        ) : Opened()

        class Failed(
            val outcome: Outcome.Failed,
        ) : Opened()
    }

    /**
     * Redirects are followed by hand rather than by HttpURLConnection, which would happily leave
     * https on the way and would not tell us where it ended up.
     */
    private fun open(
        url: String,
        prepare: (HttpURLConnection) -> Unit,
    ): Opened {
        var current = url
        var rateLimitRetries = 0
        for (hop in 0..MAX_REDIRECTS) {
            UrlRules.problemWith(current)?.let {
                return Opened.Failed(Outcome.Failed("the address $it", permanent = true))
            }
            spaceOut(current)
            val connection =
                try {
                    (URL(current).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        connectTimeout = CONNECT_TIMEOUT_MS
                        readTimeout = READ_TIMEOUT_MS
                        setRequestProperty("User-Agent", userAgent)
                        // Without this a server may gzip the body, and then the length it declares
                        // is not the length of what we would have to store.
                        setRequestProperty("Accept-Encoding", "identity")
                        prepare(this)
                    }
                } catch (e: MalformedURLException) {
                    return Opened.Failed(Outcome.Failed("the address is not a URL", permanent = true))
                } catch (e: IOException) {
                    return Opened.Failed(failed(e))
                }
            val code =
                try {
                    connection.responseCode
                } catch (e: IOException) {
                    connection.disconnect()
                    return Opened.Failed(failed(e))
                }
            when {
                code == HTTP_NOT_MODIFIED || code in 200..299 -> {
                    return Opened.Ok(connection, current)
                }

                code in REDIRECTS -> {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    current =
                        location?.let { UrlRules.resolve(current, it) }
                            ?: return Opened.Failed(
                                Outcome.Failed("redirected somewhere the launcher may not follow", permanent = true),
                            )
                }

                code == HTTP_TOO_MANY_REQUESTS -> {
                    val wait = retryAfterMillis(connection.getHeaderField("Retry-After"))
                    connection.disconnect()
                    if (rateLimitRetries >= MAX_RATE_LIMIT_RETRIES) {
                        return Opened.Failed(Outcome.Failed("the server is rate limiting requests", permanent = false))
                    }
                    rateLimitRetries++
                    sleep(wait)
                    // `current` is unchanged: the next iteration asks the same address again.
                }

                else -> {
                    connection.disconnect()
                    return Opened.Failed(
                        Outcome.Failed("the server answered $code", permanent = code in PERMANENT_CODES),
                    )
                }
            }
        }
        return Opened.Failed(Outcome.Failed("redirected more than $MAX_REDIRECTS times", permanent = true))
    }

    /**
     * A keyless request budget is rationed by host, and only the exact API host it was measured
     * against — `endsWith` also caught `w.wallhaven.cc` and `th.wallhaven.cc`, so every image
     * download was sleeping 1.4s for a limit that only ever applied to the search API and sends no
     * rate-limit headers of its own.
     */
    private fun spaceOut(url: String) {
        val host = UrlRules.hostOf(url)?.lowercase() ?: return
        val minSpacing = SPACED_HOSTS[host] ?: return
        val since = System.currentTimeMillis() - (lastRequestAt[host] ?: 0L)
        if (since in 0 until minSpacing) sleep(minSpacing - since)
        lastRequestAt[host] = System.currentTimeMillis()
    }

    /**
     * `Retry-After` in seconds, the only form a keyless API here has been seen to send. Wikimedia's
     * own policy is explicit that an absent header still means a wait: "if no such header is
     * present, clients should wait at least five seconds."
     */
    private fun retryAfterMillis(header: String?): Long {
        val seconds = header?.trim()?.toLongOrNull()
        return if (seconds != null && seconds > 0) seconds * 1000 else RATE_LIMIT_FALLBACK_MS
    }

    /** The whole body, or null when it is longer than [limit]. */
    private fun read(
        input: InputStream,
        limit: Long,
    ): ByteArray? {
        val buffer = ByteArray(BUFFER)
        val collected = java.io.ByteArrayOutputStream()
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return collected.toByteArray()
            if (collected.size() + count > limit) return null
            collected.write(buffer, 0, count)
        }
    }

    /** Bytes written, or -1 when the image turned out to be larger than the cap allows. */
    private fun copy(
        input: InputStream,
        target: File,
    ): Long {
        var written = 0L
        val buffer = ByteArray(BUFFER)
        FileOutputStream(target).use { output ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                written += count
                if (written > MAX_IMAGE_BYTES) {
                    output.flush()
                    return -1
                }
                output.write(buffer, 0, count)
            }
            output.flush()
            output.fd.sync()
        }
        return written
    }

    private fun failed(e: IOException): Outcome.Failed {
        Logs.w(TAG, "fetch failed", e)
        val reason =
            when (e) {
                is SSLException -> "the connection could not be secured"
                is java.net.SocketTimeoutException -> "the server did not answer in time"
                is java.net.UnknownHostException -> "the server could not be found"
                else -> e.message ?: e.javaClass.simpleName
            }
        return Outcome.Failed(reason, permanent = false)
    }

    companion object {
        const val MAX_INDEX_BYTES = 1024 * 1024

        const val MAX_IMAGE_BYTES = 15L * 1024 * 1024

        const val MAX_REDIRECTS = 5

        private const val CONNECT_TIMEOUT_MS = 10_000

        private const val READ_TIMEOUT_MS = 20_000

        private const val BUFFER = 16 * 1024

        private const val HTTP_NOT_MODIFIED = 304

        private const val HTTP_TOO_MANY_REQUESTS = 429

        /** A 429 is retried this many times before it is reported like any other failure. */
        private const val MAX_RATE_LIMIT_RETRIES = 2

        private const val RATE_LIMIT_FALLBACK_MS = 5_000L

        private val REDIRECTS = setOf(301, 302, 303, 307, 308)

        /** Answers that say the address is wrong, rather than that the server is having a bad day. */
        private val PERMANENT_CODES = setOf(400, 401, 403, 404, 405, 410, 414, 451)

        /** Hosts with a keyless rate limit worth respecting, and the gap the launcher holds to it. */
        private val SPACED_HOSTS = mapOf(Wallhaven.HOST to Wallhaven.MIN_SPACING_MS)

        private const val TAG = "ImageFetcher"
    }
}

/**
 * What the launcher calls itself to a server it fetches from. The repository URL is the contact
 * information Wikimedia's user-agent policy asks for, and the documented line between its 10/min
 * and 200/min anonymous bands.
 */
fun launcherUserAgent(context: android.content.Context): String =
    "atomic-launcher/${context.packageName} (Android ${android.os.Build.VERSION.RELEASE}; " +
        "+https://github.com/subhaneetshrestha/atomic-launcher)"
