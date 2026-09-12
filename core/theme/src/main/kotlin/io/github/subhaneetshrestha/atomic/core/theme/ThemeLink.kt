package io.github.subhaneetshrestha.atomic.core.theme

import io.github.subhaneetshrestha.atomic.core.collections.UrlRules
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/** What an `atomic://theme` link turned out to be carrying. */
sealed class LinkContent {
    /** The theme document itself, packed into the link. */
    data class Document(
        val json: String,
    ) : LinkContent()

    /** An address the theme can be fetched from; the user is asked before anything is fetched. */
    data class Address(
        val url: String,
    ) : LinkContent()

    data class Problem(
        val reason: String,
    ) : LinkContent()
}

/**
 * Themes as links: `atomic://theme?d=…` carries the whole document, deflated and in base64 that
 * survives being pasted into a chat; `atomic://theme?url=https://…` carries the address of one.
 *
 * A link is somebody else's data, so both forms are bounded. The packed one is never inflated past
 * [MAX_JSON_BYTES], which a document of fifty compressed bytes could otherwise ask for, and the
 * fetched one is capped where it is fetched.
 */
object ThemeLink {
    const val SCHEME = "atomic"

    const val HOST = "theme"

    const val DATA_PARAMETER = "d"

    const val URL_PARAMETER = "url"

    /** A theme is a few kilobytes of JSON; a document larger than this is not one. */
    const val MAX_JSON_BYTES = 64 * 1024

    const val MAX_FETCH_BYTES = 256 * 1024

    /** The link that carries [theme], or null when the result would be too long to be usable. */
    fun write(theme: Theme): String? {
        val packed = deflate(SettingsCodec.encodeTheme(theme).toByteArray(Charsets.UTF_8))
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(packed)
        return if (encoded.length > MAX_LINK_LENGTH) null else "$SCHEME://$HOST?$DATA_PARAMETER=$encoded"
    }

    /** Reads a link, or returns null when it is not one of ours at all. */
    fun read(link: String): LinkContent? {
        val uri =
            try {
                URI(link.trim())
            } catch (e: URISyntaxException) {
                return null
            }
        if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        if (!uri.host.equals(HOST, ignoreCase = true) && uri.schemeSpecificPart?.startsWith("//$HOST") != true) {
            return null
        }
        val parameters = parameters(uri.rawQuery)
        parameters[URL_PARAMETER]?.let { address ->
            val problem = UrlRules.problemWith(address)
            return if (problem == null) LinkContent.Address(address) else LinkContent.Problem("the address $problem")
        }
        val data = parameters[DATA_PARAMETER] ?: return LinkContent.Problem("the link carries no theme")
        return unpack(data)
    }

    /** base64url, then deflate, with a ceiling on what the result may inflate to. */
    fun unpack(data: String): LinkContent {
        val bytes =
            try {
                Base64.getUrlDecoder().decode(data.trim())
            } catch (e: IllegalArgumentException) {
                return LinkContent.Problem("the theme in the link is not readable")
            }
        val json =
            inflate(bytes)
                ?: return LinkContent.Problem("the theme in the link is damaged or larger than 64 KB")
        return LinkContent.Document(json)
    }

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        return try {
            deflater.setInput(bytes)
            deflater.finish()
            val out = ByteArrayOutputStream(bytes.size / 2)
            val buffer = ByteArray(BUFFER)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    /** The inflated text, or null when it is not deflated data or would be too large. */
    private fun inflate(bytes: ByteArray): String? {
        val inflater = Inflater()
        return try {
            inflater.setInput(bytes)
            val out = ByteArrayOutputStream(bytes.size * 2)
            val buffer = ByteArray(BUFFER)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null
                if (out.size() + count > MAX_JSON_BYTES) return null
                out.write(buffer, 0, count)
            }
            String(out.toByteArray(), Charsets.UTF_8)
        } catch (e: DataFormatException) {
            null
        } finally {
            inflater.end()
        }
    }

    private fun parameters(rawQuery: String?): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (pair in rawQuery.orEmpty().split('&')) {
            if (pair.isEmpty()) continue
            val name = pair.substringBefore('=').lowercase()
            val value = pair.substringAfter('=', "")
            if (name.isEmpty() || value.isEmpty() || name in result) continue
            result[name] =
                try {
                    // Only the address is escaped; the packed theme is base64url and has nothing to undo.
                    if (name == URL_PARAMETER) URLDecoder.decode(value, "UTF-8") else value
                } catch (e: IllegalArgumentException) {
                    continue
                }
        }
        return result
    }

    /** Past this, a link stops surviving the places people paste links into. */
    private const val MAX_LINK_LENGTH = 8000

    private const val BUFFER = 4096
}
