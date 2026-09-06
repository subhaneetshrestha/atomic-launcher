package io.github.subhaneetshrestha.atomic.core.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the ranking is meant to feel like, in order of strength: a label the query begins,
 * then the initials of its words in order, then letters buried inside words, which for a very
 * short query do not count at all.
 */
class ScorerTest {
    private fun score(
        label: String,
        query: String,
    ): Int = Scorer.score(Normalizer.normalize(label), Normalizer.normalize(query))

    private fun matches(
        query: String,
        vararg labels: String,
    ): List<String> = labels.filter { score(it, query) != Scorer.NO_MATCH }.sortedByDescending { score(it, query) }

    @Test
    fun `the letters must all be there, in order`() {
        assertEquals(Scorer.NO_MATCH, score("Gmail", "xyz"))
        assertEquals(Scorer.NO_MATCH, score("Gmail", "liamg"), "the same letters backwards are not a match")
        assertEquals(Scorer.NO_MATCH, score("Maps", "mapsx"), "a query longer than the label cannot match")
        assertTrue(score("Gmail", "gml") != Scorer.NO_MATCH, "letters may be skipped between the ones typed")
    }

    @Test
    fun `two letters find the app they begin before the ones they are buried in`() {
        assertEquals(
            listOf("Gmail", "GroupMe", "Google Maps"),
            matches("gm", "Instagram", "Gmail", "Telegram", "Google Maps", "GroupMe"),
            "Gmail begins with the query; the other two are the initials of their words",
        )
        assertEquals(
            emptyList(),
            matches("gm", "Instagram", "Telegram"),
            "two letters from inside a word are not enough",
        )
    }

    @Test
    fun `more letters reach inside words`() {
        assertTrue(score("Instagram", "gram") != Scorer.NO_MATCH, "a longer run inside a word does count")
        assertTrue(
            score("Instagram", "insta") > score("Instagram", "gram"),
            "but the start of the label counts for more",
        )
    }

    @Test
    fun `the same match earlier in the label wins`() {
        assertEquals(listOf("Calendar", "Google Calendar"), matches("cal", "Google Calendar", "Calendar"))
        assertEquals(listOf("Files", "Google Files"), matches("fil", "Google Files", "Files"))
    }

    @Test
    fun `the initials of three words are a strong match`() {
        assertTrue(score("Google Play Music", "gpm") != Scorer.NO_MATCH)
        assertTrue(
            score("Google Play Music", "gpm") > score("Google Play Music", "gam"),
            "initials beat the same number of letters taken from the middle",
        )
    }

    @Test
    fun `everything matches an empty query, and nothing is ranked by it`() {
        assertEquals(0, score("Gmail", ""))
        assertEquals(0, score("", ""))
        assertEquals(Scorer.NO_MATCH, score("", "a"))
    }

    @Test
    fun `accents and case do not matter`() {
        assertTrue(score("Café Wi-Fi", "cafe") != Scorer.NO_MATCH)
        assertTrue(score("Café Wi-Fi", "CAFEWIFI") != Scorer.NO_MATCH)
        assertEquals(score("Café", "cafe"), score("cafe", "café"), "both sides are reduced the same way")
    }

    @Test
    fun `a run of letters beats scattered initials even when the first word is one letter long`() {
        assertEquals(
            listOf("Tmall", "T-Mobile"),
            matches("tm", "T-Mobile", "Tmall"),
            "typing tm means the app called Tmall, not the initials of a two-word name",
        )
        assertEquals(listOf("Shazam", "S Health"), matches("sh", "S Health", "Shazam"))
        assertTrue(
            score("X-plore", "xp") < score("Xperia Lounge", "xp"),
            "one letter, a separator and another letter must not outrank the name the query begins",
        )
    }
}
