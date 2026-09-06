package io.github.subhaneetshrestha.atomic.core.search

/**
 * Scores how well a query matches a label. Every letter typed must appear, in order, but not
 * necessarily together, and the best way of lining them up is the one that counts.
 *
 * What the weights are for, strongest first: a label the query begins, the initials of its words
 * taken in order, and letters buried inside words. Runs of letters typed together beat scattered
 * ones, and every letter skipped costs a little more than the last. A short query that only
 * matches inside words scores below the floor and is not a match at all, which is what stops two
 * letters from dragging in half the phone.
 */
object Scorer {
    const val NO_MATCH: Int = Int.MIN_VALUE

    private const val UNREACHED = -1_000_000

    /** Every letter that lines up is worth this much, and a match must average at least it. */
    private const val MATCH = 16

    /** The first letter of a word, which is how people abbreviate: "gpm" for Google Play Music. */
    private const val WORD_INITIAL = 16

    /** The very first letter typed counts double when it opens a word. */
    private const val FIRST_LETTER_WEIGHT = 2

    /** Straight after the previous letter: the sign the user is typing the name itself. */
    private const val CONSECUTIVE = 12

    /** The label begins with what was typed. */
    private const val PREFIX = 6

    private const val GAP_START = -3
    private const val GAP_EXTEND = -1

    fun score(
        label: NormalizedText,
        query: NormalizedText,
    ): Int {
        val letters = query.length
        val length = label.length
        if (letters == 0) return 0
        if (letters > length || !isSubsequence(label, query)) return NO_MATCH

        // best[i] = the best score for the letters typed so far, with the last one landing at i.
        var best = IntArray(length) { UNREACHED }
        for (index in 0 until length) {
            if (label.codePoints[index] == query.codePoints[0]) {
                best[index] = letterScore(label, index, firstLetter = true)
            }
        }
        for (letter in 1 until letters) {
            val next = IntArray(length) { UNREACHED }
            // The best way to arrive from a letter at least two places back, gaps already paid for.
            var afterGap = UNREACHED
            for (index in 1 until length) {
                afterGap =
                    if (index < 2) {
                        UNREACHED
                    } else {
                        maxOf(afterGap + GAP_EXTEND, best[index - 2] + GAP_START)
                    }
                if (label.codePoints[index] != query.codePoints[letter]) continue
                val arrival = maxOf(best[index - 1] + CONSECUTIVE, afterGap)
                if (arrival <= UNREACHED) continue
                next[index] = arrival + letterScore(label, index, firstLetter = false)
            }
            best = next
        }
        val top = best.max()
        return if (top <= UNREACHED || top < MATCH * letters) NO_MATCH else top
    }

    private fun letterScore(
        label: NormalizedText,
        index: Int,
        firstLetter: Boolean,
    ): Int {
        var score = MATCH
        if (label.startsWord[index]) score += if (firstLetter) WORD_INITIAL * FIRST_LETTER_WEIGHT else WORD_INITIAL
        if (index == 0) score += PREFIX
        return score
    }

    /** A cheap look before the real work: most labels fail here and cost nothing more. */
    private fun isSubsequence(
        label: NormalizedText,
        query: NormalizedText,
    ): Boolean {
        var wanted = 0
        for (index in 0 until label.length) {
            if (label.codePoints[index] == query.codePoints[wanted]) {
                wanted++
                if (wanted == query.length) return true
            }
        }
        return false
    }
}
