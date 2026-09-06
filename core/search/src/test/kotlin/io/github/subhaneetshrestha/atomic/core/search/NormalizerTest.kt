package io.github.subhaneetshrestha.atomic.core.search

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NormalizerTest {
    private fun text(label: String): String {
        val normalized = Normalizer.normalize(label)
        return String(normalized.codePoints, 0, normalized.codePoints.size)
    }

    private fun boundaryWords(label: String): List<String> {
        val normalized = Normalizer.normalize(label)
        val words = mutableListOf<StringBuilder>()
        normalized.codePoints.forEachIndexed { index, codePoint ->
            if (normalized.startsWord[index]) words += StringBuilder()
            if (words.isNotEmpty()) words.last().appendCodePoint(codePoint)
        }
        return words.map { it.toString().trim() }.filter { it.isNotEmpty() }
    }

    @Test
    fun `accents come off, so cafe finds a Cafe with one`() {
        assertEquals("cafe", text("Café"))
        assertEquals("uber eats", text("Über Eats"))
        assertEquals("aao", text("åäö"))
    }

    @Test
    fun `lowercasing never depends on the language the phone is set to`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))

            assertEquals("i", text("I"), "in Turkish, lowercasing I the usual way gives a dotless one")
            assertEquals("istanbul", text("İstanbul"))
            assertEquals("ı", text("ı"), "a dotless i stays itself, so it is not the same letter as i")
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `other scripts are left as they are`() {
        assertEquals("地图", text("地图"))
        assertEquals("नेपाल", text("नेपाल"))
    }

    @Test
    fun `punctuation between words becomes one space`() {
        assertEquals("wi fi settings", text("Wi-Fi   Settings"))
        assertEquals("at t", text("AT&T"))
        assertEquals("google maps", text("  Google  Maps  "), "nothing dangling at either end")
    }

    @Test
    fun `words start after a space, at a capital and at a digit`() {
        assertEquals(listOf("google", "maps"), boundaryWords("Google Maps"))
        assertEquals(listOf("word", "press"), boundaryWords("WordPress"))
        assertEquals(listOf("player", "3"), boundaryWords("Player3"))
        assertEquals(listOf("k", "9", "mail"), boundaryWords("K-9 Mail"))
    }

    @Test
    fun `each character remembers where it came from, so a match can be shown`() {
        val normalized = Normalizer.normalize("Über Eats")

        assertEquals("uber eats", String(normalized.codePoints, 0, normalized.codePoints.size))
        assertEquals(0, normalized.source[0], "the u came from the U with the umlaut")
        assertEquals(5, normalized.source[5], "the e of Eats")
        assertTrue(
            normalized.source
                .toList()
                .zipWithNext()
                .all { (a, b) -> a <= b },
            "and they never point backwards",
        )
    }
}
