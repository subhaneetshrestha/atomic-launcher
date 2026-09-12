package io.github.subhaneetshrestha.atomic.core.theme

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What is behind the app names. Part of the theme, because it is how the launcher looks and
 * travels with a shared theme — which is also why an imported theme has to say out loud that it
 * would fetch images, and from where.
 */
@Serializable
data class Background(
    val mode: BackgroundMode = BackgroundMode.COLOR,
    val gradient: Gradient = Gradient(),
    val collection: CollectionConfig = CollectionConfig(),
    /** Black laid over the image, 0 to 1, so names stay readable on a busy photograph. */
    val dim: Float = 0.35f,
    /** Let the image behind the text decide the text colour, rather than the theme's. */
    val autoTextColor: Boolean = true,
)

@Serializable
enum class BackgroundMode {
    /** The theme's background colour, and nothing else drawn. */
    @SerialName("color")
    COLOR,

    @SerialName("gradient")
    GRADIENT,

    /** The device wallpaper, shown through the launcher window. No permission, no copy of the image. */
    @SerialName("wallpaper")
    WALLPAPER,

    /** Images fetched from a collection address and changed on a schedule. */
    @SerialName("collection")
    COLLECTION,
}

@Serializable
data class Gradient(
    val from: String = "#FF101820",
    val to: String = "#FF000000",
    /** Degrees clockwise from straight down: 0 paints top to bottom, 90 left to right. */
    val angle: Int = 0,
)

/**
 * Where the images come from and how often they change. [url] is whatever the user pasted — a
 * list, a feed, a JSON document, a single image or a Wallhaven search; what it turns out to be is
 * worked out when it is fetched.
 */
@Serializable
data class CollectionConfig(
    val url: String = "",
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    /** Fetch only on wi-fi. On by default: nobody expects their home screen to spend mobile data. */
    val unmeteredOnly: Boolean = true,
    /** Pick at random rather than walking the list in order. */
    val shuffle: Boolean = true,
) {
    val isConfigured: Boolean get() = url.isNotBlank()

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 360

        /** JobScheduler will not run a periodic job more often than this, so neither will we. */
        const val MIN_INTERVAL_MINUTES = 15

        const val MAX_INTERVAL_MINUTES = 7 * 24 * 60
    }
}
