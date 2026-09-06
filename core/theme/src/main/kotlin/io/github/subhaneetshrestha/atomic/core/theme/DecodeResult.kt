package io.github.subhaneetshrestha.atomic.core.theme

/** Outcome of reading a settings or theme document. Decoding never throws. */
sealed class DecodeResult<out T> {
    /** The document was read; [warnings] list values that were corrected or ignored. */
    data class Ok<T>(
        val value: T,
        val warnings: List<Warning>,
    ) : DecodeResult<T>()

    /** Not JSON, not an object, or fields of the wrong type: the caller falls back to defaults. */
    data class Corrupt(
        val reason: String,
    ) : DecodeResult<Nothing>()

    /** Written by a newer app than this one understands. */
    data class Unsupported(
        val schema: Int,
        val supported: Int,
    ) : DecodeResult<Nothing>()
}

data class Warning(
    val path: String,
    val message: String,
)
