package io.github.subhaneetshrestha.atomic.core.collections

/** How a source's one blank, if it has one, is filled in. */
enum class SourceInput {
    /** The source needs nothing typed; picking it is the whole interaction. */
    NONE,

    /** An address: reuses the same paste-and-validate dialog every collection URL already goes through. */
    ADDRESS,
}

/**
 * A named wallpaper source the picker offers. This is deliberately thin: [Wallhaven] already does the
 * one rewrite a source needs (page address to keyless API, at fetch time in `BackgroundEngine`), so a
 * source here is only ever a label and an input shape — for Wallhaven's own two entries the "custom
 * address" flow already produces exactly the URL `Wallhaven.apiUrlFor` expects, and for everything
 * else the pasted address is used exactly as it is today. No source-specific request-building lives
 * here; that is why adding one costs a picker row, not a parser change (see the design note).
 */
data class WallpaperSource(
    val id: String,
    val input: SourceInput,
    /** Set only when [input] is [SourceInput.NONE]: the one address this source ever fetches. */
    val fixedUrl: String? = null,
) {
    init {
        require((input == SourceInput.NONE) == (fixedUrl != null)) {
            "$id: fixedUrl is set exactly when input is NONE, never otherwise"
        }
    }
}

/**
 * The picker's rows, and the source of truth for what a stored `source` id means. Every entry here
 * reuses the collection engine as it already ships: an address source still ends up as a plain
 * `CollectionConfig.url`, so a build that has never heard of a source id still fetches from the same
 * address a build that knows the id would.
 */
object WallpaperSources {
    /**
     * `categories=100` (general only, no faces behind app names), forced to true phone ratios and
     * a minimum size — `atleast=` alone is orientation-blind, and the broad `ratios=portrait` bucket
     * includes letterboxed images, so both are named explicitly. `sorting=random` makes the six-hour
     * index refresh the pagination: a fresh batch of 24, never the same search twice in a row.
     */
    val WALLHAVEN_SEARCH =
        WallpaperSource(
            id = "wallhaven_search",
            input = SourceInput.NONE,
            fixedUrl =
                "https://wallhaven.cc/search?categories=100&purity=100" +
                    "&ratios=9x16,10x16,9x18&atleast=1080x1920&sorting=random",
        )

    /** A person's own public favourites or collection; [Wallhaven.apiUrlFor] reads it keyless. */
    val WALLHAVEN_COLLECTION = WallpaperSource("wallhaven_collection", SourceInput.ADDRESS)

    /** An nginx/Caddy/Apache directory of images the user (or anyone) hosts. */
    val OWN_SERVER = WallpaperSource("own_server", SourceInput.ADDRESS)

    /** A plain `.txt`, one address per line — the universal bridge to a source with no adapter here. */
    val LIST_FILE = WallpaperSource("list_file", SourceInput.ADDRESS)

    /** A Mastodon or Pixelfed account's public RSS/Atom feed. */
    val MASTODON = WallpaperSource("mastodon", SourceInput.ADDRESS)

    /** A Nextcloud public share link to a folder. */
    val NEXTCLOUD = WallpaperSource("nextcloud", SourceInput.ADDRESS)

    /** Anything else: the row that existed before there was a picker. */
    val CUSTOM = WallpaperSource("custom", SourceInput.ADDRESS)

    /** Every source, in the order the picker shows them. */
    val all: List<WallpaperSource> =
        listOf(WALLHAVEN_SEARCH, WALLHAVEN_COLLECTION, OWN_SERVER, LIST_FILE, MASTODON, NEXTCLOUD, CUSTOM)

    fun byId(id: String): WallpaperSource? = all.firstOrNull { it.id == id }
}
