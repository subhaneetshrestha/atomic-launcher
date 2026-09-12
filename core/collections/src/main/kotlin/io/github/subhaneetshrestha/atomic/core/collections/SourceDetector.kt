package io.github.subhaneetshrestha.atomic.core.collections

/** What a collection address turned out to be pointing at. */
enum class SourceKind {
    /** The address is one image, which then never changes. */
    IMAGE,

    /** A JSON document with image addresses somewhere inside it. */
    JSON,

    /** RSS, Media RSS or Atom, with images in enclosures. */
    FEED,

    /** One address per line. */
    TEXT,
}

/**
 * Decides what a collection address holds. A server's word is taken first (its `Content-Type`),
 * then what the bytes themselves say, then the first character of the text; anything else is read
 * as a list of addresses, which is the format a person is most likely to have typed by hand.
 *
 * The one host with a rule of its own is Wallhaven, and that is settled before the fetch: the
 * address is rewritten to its API, which answers in JSON. See [Wallhaven].
 */
object SourceDetector {
    /** How many bytes of the body [detect] needs. */
    const val SNIFF_BYTES = 64

    /** What can be told from the address alone, so a plain image needs no index fetch at all. */
    fun byUrl(url: String): SourceKind? = if (UrlRules.looksLikeImage(url)) SourceKind.IMAGE else null

    fun detect(
        contentType: String?,
        head: ByteArray,
    ): SourceKind {
        byContentType(contentType)?.let { return it }
        if (isImageMagic(head)) return SourceKind.IMAGE
        return when (head.firstPrintable()) {
            '{', '[' -> SourceKind.JSON
            '<' -> SourceKind.FEED
            else -> SourceKind.TEXT
        }
    }

    private fun byContentType(contentType: String?): SourceKind? {
        val type =
            contentType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                ?: return null
        return when {
            type.startsWith("image/") -> SourceKind.IMAGE
            type.endsWith("/json") || type.endsWith("+json") -> SourceKind.JSON
            type.endsWith("/xml") || type.endsWith("+xml") -> SourceKind.FEED
            type == "text/plain" -> SourceKind.TEXT
            else -> null
        }
    }

    /** The signatures of the formats Android can decode, read from the first bytes of the body. */
    private fun isImageMagic(head: ByteArray): Boolean =
        when {
            head.startsWith(0xFF, 0xD8, 0xFF) -> true

            head.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> true

            head.startsWithText("GIF87a") || head.startsWithText("GIF89a") -> true

            head.startsWithText("RIFF") && head.textAt(8, 4) == "WEBP" -> true

            // HEIF, HEIC and AVIF are ISO base media files: the box type follows its length.
            head.textAt(4, 4) == "ftyp" -> true

            head.startsWithText("BM") -> true

            else -> false
        }

    private fun ByteArray.startsWith(vararg bytes: Int): Boolean {
        if (size < bytes.size) return false
        return bytes.withIndex().all { (index, value) -> this[index] == value.toByte() }
    }

    private fun ByteArray.startsWithText(text: String): Boolean = textAt(0, text.length) == text

    private fun ByteArray.textAt(
        offset: Int,
        length: Int,
    ): String? {
        if (size < offset + length) return null
        return String(this, offset, length, Charsets.US_ASCII)
    }

    /** The first character that is not whitespace or a byte-order mark. */
    private fun ByteArray.firstPrintable(): Char? {
        for (byte in this) {
            val char = byte.toInt().toChar()
            if (char.isWhitespace() || byte == 0xEF.toByte() || byte == 0xBB.toByte() || byte == 0xBF.toByte()) continue
            return char
        }
        return null
    }
}
