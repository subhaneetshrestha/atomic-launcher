package io.github.subhaneetshrestha.atomic.core.collections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rules an address must pass before the launcher will fetch it unattended. */
class UrlRulesTest {
    @Test
    fun `only https is fetched`() {
        assertTrue(UrlRules.isUsable("https://example.org/a.jpg"))
        assertFalse(UrlRules.isUsable("http://example.org/a.jpg"), "plaintext lets the path choose the picture")
        assertFalse(UrlRules.isUsable("file:///sdcard/a.jpg"))
        assertFalse(UrlRules.isUsable("javascript:alert(1)"))
        assertFalse(UrlRules.isUsable("/relative/a.jpg"))
        assertFalse(UrlRules.isUsable(""))
    }

    @Test
    fun `credentials and overlong addresses are refused`() {
        assertFalse(UrlRules.isUsable("https://user:secret@example.org/a.jpg"))
        val long = "https://example.org/" + "a".repeat(UrlRules.MAX_LENGTH)
        assertFalse(UrlRules.isUsable(long))
        assertTrue(UrlRules.problemWith(long)!!.contains("${UrlRules.MAX_LENGTH}"))
    }

    @Test
    fun `cleaning trims, drops, dedupes and caps`() {
        val cleaned =
            UrlRules.clean(
                listOf(
                    "  https://example.org/a.jpg  ",
                    "https://example.org/a.jpg",
                    "http://example.org/b.jpg",
                    "not a url at all",
                    "https://example.org/c.jpg",
                ),
            )
        assertEquals(listOf("https://example.org/a.jpg", "https://example.org/c.jpg"), cleaned)
        val many = (1..UrlRules.MAX_ENTRIES + 40).map { "https://example.org/$it.jpg" }
        assertEquals(UrlRules.MAX_ENTRIES, UrlRules.clean(many).size)
    }

    @Test
    fun `relative addresses resolve against the document they were found in`() {
        assertEquals(
            "https://example.org/images/a.jpg",
            UrlRules.resolve("https://example.org/feed/index.xml", "/images/a.jpg"),
        )
        assertEquals(
            "https://example.org/feed/a.jpg",
            UrlRules.resolve("https://example.org/feed/index.xml", "a.jpg"),
        )
        assertEquals(
            "https://cdn.example.org/a.jpg",
            UrlRules.resolve("https://example.org/feed/index.xml", "https://cdn.example.org/a.jpg"),
        )
        assertNull(
            UrlRules.resolve("https://example.org/feed/index.xml", "http://example.org/a.jpg"),
            "resolving must not smuggle in a plaintext hop",
        )
    }

    @Test
    fun `an image is recognised by its extension, not by its query string`() {
        assertTrue(UrlRules.looksLikeImage("https://example.org/a.JPG"))
        assertTrue(UrlRules.looksLikeImage("https://example.org/a.webp?width=1080"))
        assertFalse(UrlRules.looksLikeImage("https://example.org/photo?format=jpg"))
        assertFalse(UrlRules.looksLikeImage("https://example.org/index.html"))
    }

    @Test
    fun `the host is available for the confirmation an import asks for`() {
        assertEquals("images.example.org", UrlRules.hostOf("https://images.example.org/list.txt"))
    }
}
