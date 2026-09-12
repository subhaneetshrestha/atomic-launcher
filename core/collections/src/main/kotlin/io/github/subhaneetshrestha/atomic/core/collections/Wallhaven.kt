package io.github.subhaneetshrestha.atomic.core.collections

import java.net.URI
import java.net.URISyntaxException

/**
 * Wallhaven is the one host with a rule of its own: its pages are HTML, but the same search is
 * available keyless as JSON. A pasted page address is rewritten to that API before it is fetched,
 * and the rewrite forces `purity=100`, so the launcher can only ever be handed the tame images —
 * this is a home screen, and nobody is watching what it downloads.
 *
 * An API key is never carried over. Keyless callers are allowed 45 requests a minute; the fetcher
 * keeps [MIN_SPACING_MS] between two requests to the host, which stays well inside that.
 */
object Wallhaven {
    const val HOST = "wallhaven.cc"

    const val MIN_SPACING_MS = 1_400L

    private const val API = "https://wallhaven.cc/api/v1"

    /** Search parameters worth carrying over; `apikey` and `purity` are deliberately not among them. */
    private val CARRIED =
        setOf("q", "categories", "sorting", "order", "toprange", "atleast", "resolutions", "ratios", "colors", "seed")

    /** What a path means when the address carries no `sorting` of its own. */
    private val SORTING_BY_PATH =
        mapOf(
            "/latest" to "date_added",
            "/hot" to "hot",
            "/toplist" to "toplist",
            "/random" to "random",
        )

    fun isWallhavenPage(url: String): Boolean {
        val host = UrlRules.hostOf(url)?.lowercase() ?: return false
        return host == HOST || host == "www.$HOST"
    }

    /**
     * The keyless API address for a Wallhaven page, or null when the address is not one this
     * knows how to ask for (a user's collection, say, which needs a key).
     */
    fun apiUrlFor(url: String): String? {
        if (!isWallhavenPage(url)) return null
        val uri =
            try {
                URI(url.trim())
            } catch (e: URISyntaxException) {
                return null
            }
        val path =
            uri.path
                .orEmpty()
                .trimEnd('/')
                .lowercase()
                .ifEmpty { "/" }
        val id = path.removePrefix("/w/")
        if (path.startsWith("/w/")) {
            return if (id.matches(WALLPAPER_ID)) "$API/w/$id" else null
        }
        if (path != "/" && path != "/search" && path !in SORTING_BY_PATH) return null
        val carried = carriedParams(uri.rawQuery) ?: return null
        if (path in SORTING_BY_PATH && "sorting" !in carried) carried["sorting"] = SORTING_BY_PATH.getValue(path)
        carried["purity"] = "100"
        return "$API/search?" + carried.entries.joinToString("&") { "${it.key}=${it.value}" }
    }

    /**
     * Values are passed on exactly as they were written: they are already escaped for a URL.
     * Parameters this does not carry are simply dropped, but a carried one that could change the
     * shape of the request refuses the whole address — silently searching for something other
     * than what was pasted would be worse than saying no.
     */
    private fun carriedParams(rawQuery: String?): LinkedHashMap<String, String>? {
        val result = LinkedHashMap<String, String>()
        for (pair in rawQuery.orEmpty().split('&')) {
            if (pair.isEmpty()) continue
            val name = pair.substringBefore('=').lowercase()
            if (name !in CARRIED) continue
            val value = pair.substringAfter('=', "")
            if (value.isEmpty()) continue
            if (value.length > MAX_VALUE || !SAFE_VALUE.matches(value)) return null
            result[if (name == "toprange") "topRange" else name] = value
        }
        return result
    }

    private const val MAX_VALUE = 200

    /** Anything that could end the value and begin another parameter, or walk out of the path. */
    private val SAFE_VALUE = Regex("^[^\\s&#?/]+$")

    private val WALLPAPER_ID = Regex("^[a-z0-9]{1,16}$")
}
