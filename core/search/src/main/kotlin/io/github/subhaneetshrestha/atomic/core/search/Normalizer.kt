package io.github.subhaneetshrestha.atomic.core.search

import java.text.Normalizer as Unicode

/**
 * A label reduced to what matching cares about: Latin accents off, one case, punctuation turned
 * into single spaces, and a note of where each character came from so a match can be shown in the
 * original text. Marks that are letters in their own right, such as the vowel signs of Devanagari,
 * are kept.
 */
class NormalizedText(
    val codePoints: IntArray,
    /** True where a word begins: at the start, after a space, at a capital, at a digit. */
    val startsWord: BooleanArray,
    /** The index in the original string that each code point came from. */
    val source: IntArray,
) {
    val length: Int get() = codePoints.size
}

/**
 * Prepares labels and queries for matching. Case is folded one code point at a time rather than
 * with the usual string call, because that call follows the phone's language: in Turkish it turns
 * I into a dotless one, which would stop "instagram" from finding Instagram on a Turkish phone.
 */
object Normalizer {
    private const val SPACE = ' '.code

    private enum class Previous { NONE, SEPARATOR, LOWER, UPPER, DIGIT }

    fun normalize(text: String): NormalizedText {
        val points = ArrayList<Int>(text.length)
        val starts = ArrayList<Boolean>(text.length)
        val sources = ArrayList<Int>(text.length)
        var previous = Previous.NONE
        var pendingBoundary = true
        var index = 0

        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val width = Character.charCount(codePoint)
            when {
                // A mark written as its own character is part of the word: the vowel signs of
                // Devanagari and its neighbours are letters, not decoration. Only the marks that
                // fall out of pulling a precomposed letter apart are dropped, which is what turns
                // an accented Latin letter into a plain one.
                isMark(codePoint) -> {
                    points += Character.toLowerCase(codePoint)
                    starts += false
                    sources += index
                }

                Character.isLetterOrDigit(codePoint) -> {
                    val digit = Character.isDigit(codePoint)
                    val upper = Character.isUpperCase(codePoint)
                    val boundary =
                        pendingBoundary ||
                            (upper && previous == Previous.LOWER) ||
                            (upper && previous == Previous.UPPER && nextIsLower(text, index + width)) ||
                            (upper && previous == Previous.DIGIT) ||
                            (digit && previous != Previous.DIGIT && previous != Previous.NONE)
                    var first = true
                    for (base in basesOf(codePoint)) {
                        points += Character.toLowerCase(base)
                        starts += boundary && first
                        sources += index
                        first = false
                    }
                    pendingBoundary = false
                    previous =
                        if (digit) {
                            Previous.DIGIT
                        } else if (upper) {
                            Previous.UPPER
                        } else {
                            Previous.LOWER
                        }
                }

                else -> {
                    // Anything else parts two words: one space, however many of them there were.
                    if (points.isNotEmpty() && points.last() != SPACE) {
                        points += SPACE
                        starts += false
                        sources += index
                    }
                    pendingBoundary = true
                    previous = Previous.SEPARATOR
                }
            }
            index += width
        }
        if (points.isNotEmpty() && points.last() == SPACE) {
            points.removeAt(points.lastIndex)
            starts.removeAt(starts.lastIndex)
            sources.removeAt(sources.lastIndex)
        }
        return NormalizedText(points.toIntArray(), starts.toBooleanArray(), sources.toIntArray())
    }

    private fun isMark(codePoint: Int): Boolean =
        when (Character.getType(codePoint).toByte()) {
            Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
            else -> false
        }

    private fun nextIsLower(
        text: String,
        index: Int,
    ): Boolean = index < text.length && Character.isLowerCase(text.codePointAt(index))

    /**
     * The letters left when a character is pulled apart and its marks dropped: é becomes e, and a
     * Korean syllable becomes the letters it is written from, which is what a Korean query will
     * also be reduced to.
     */
    private fun basesOf(codePoint: Int): IntArray {
        if (codePoint < 0x80) return intArrayOf(codePoint)
        val one = String(Character.toChars(codePoint))
        if (Unicode.isNormalized(one, Unicode.Form.NFD)) return intArrayOf(codePoint)
        val bases = ArrayList<Int>(2)
        var index = 0
        val decomposed = Unicode.normalize(one, Unicode.Form.NFD)
        while (index < decomposed.length) {
            val point = decomposed.codePointAt(index)
            if (!isMark(point)) bases += point
            index += Character.charCount(point)
        }
        return if (bases.isEmpty()) intArrayOf(codePoint) else bases.toIntArray()
    }
}
