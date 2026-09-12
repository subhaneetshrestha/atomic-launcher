package io.github.subhaneetshrestha.atomic.core.collections

import java.net.URI
import java.net.URISyntaxException

/**
 * What a collection may point the launcher at. These images are fetched with nobody watching, so
 * the rules are narrow: https only (a plaintext hop lets anyone on the path choose the picture on
 * the home screen), no credentials smuggled in the authority, a length no server can use to bloat
 * the stored index, and a ceiling on how many images one collection may hold.
 */
object UrlRules {
    const val MAX_LENGTH = 2048

    const val MAX_ENTRIES = 500

    private val IMAGE_EXTENSIONS =
        setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")

    /** Why [url] may not be fetched, in words an import screen can show, or null when it may. */
    fun problemWith(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return "is empty"
        if (trimmed.length > MAX_LENGTH) return "is longer than $MAX_LENGTH characters"
        val uri = parse(trimmed) ?: return "is not a URL"
        return when {
            !uri.isAbsolute -> "does not start with https://"
            !uri.scheme.equals("https", ignoreCase = true) -> "uses ${uri.scheme}:, and only https: is fetched"
            uri.host.isNullOrBlank() -> "names no host"
            uri.userInfo != null -> "carries a user name or password in the address"
            else -> null
        }
    }

    fun isUsable(url: String): Boolean = problemWith(url) == null

    fun hostOf(url: String): String? = parse(url.trim())?.host

    /** Makes [href] absolute against the document it was found in; null when it cannot be. */
    fun resolve(
        base: String,
        href: String,
    ): String? {
        val trimmed = href.trim()
        if (trimmed.isEmpty()) return null
        val baseUri = parse(base) ?: return trimmed.takeIf { isUsable(it) }
        return try {
            baseUri.resolve(trimmed).toString().takeIf { isUsable(it) }
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** Trims, drops what may not be fetched, removes repeats and cuts the list to [MAX_ENTRIES]. */
    fun clean(urls: List<String>): List<String> =
        urls
            .asSequence()
            .map { it.trim() }
            .filter(::isUsable)
            .distinct()
            .take(MAX_ENTRIES)
            .toList()

    /** Whether the path ends in a file extension only an image uses; the query string is ignored. */
    fun looksLikeImage(url: String): Boolean {
        val path = parse(url.trim())?.path ?: return false
        return path.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
    }

    private fun parse(url: String): URI? =
        try {
            URI(url)
        } catch (e: URISyntaxException) {
            null
        }
}
