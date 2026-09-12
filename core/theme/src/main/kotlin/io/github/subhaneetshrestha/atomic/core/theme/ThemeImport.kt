package io.github.subhaneetshrestha.atomic.core.theme

import io.github.subhaneetshrestha.atomic.core.collections.UrlRules

/**
 * What a theme says about itself, shown before it is applied. A theme changes every colour on the
 * home screen and can bring an address the launcher would fetch images from, so it is never
 * applied on arrival: this is what the screen asking about it has to say.
 */
data class ThemePreview(
    val name: String,
    val author: String?,
    val license: String?,
    val description: String?,
    /** Values the document had to have corrected; worth showing, because the sender may not know. */
    val warnings: List<Warning>,
    /** The host images would be downloaded from, when the theme brings a collection with it. */
    val imageHost: String?,
)

/** Reading a theme somebody else wrote: from a file, a share, or a link. */
object ThemeImport {
    sealed class Outcome {
        data class Ready(
            val theme: Theme,
            val preview: ThemePreview,
        ) : Outcome()

        /** Nothing usable came out of the document, in words for the screen that asked. */
        data class Refused(
            val reason: String,
        ) : Outcome()
    }

    fun read(
        json: String,
        appVersionCode: Int,
    ): Outcome =
        when (val result = SettingsCodec.decodeTheme(json, appVersionCode)) {
            is DecodeResult.Ok -> {
                Outcome.Ready(result.value, preview(result.value, result.warnings))
            }

            is DecodeResult.Unsupported -> {
                Outcome.Refused(
                    "this theme was made for a newer version of atomic (theme format ${result.schema}, " +
                        "this version reads ${result.supported})",
                )
            }

            is DecodeResult.Corrupt -> {
                Outcome.Refused("this file is not a theme: ${result.reason}")
            }
        }

    fun preview(
        theme: Theme,
        warnings: List<Warning>,
    ): ThemePreview {
        val collection = theme.background.collection
        return ThemePreview(
            name = theme.meta.name,
            author = theme.meta.author?.takeIf { it.isNotBlank() },
            license = theme.meta.license?.takeIf { it.isNotBlank() },
            description = theme.meta.description?.takeIf { it.isNotBlank() },
            warnings = warnings,
            imageHost =
                if (theme.background.mode == BackgroundMode.COLLECTION && collection.isConfigured) {
                    UrlRules.hostOf(collection.url)
                } else {
                    null
                },
        )
    }
}
