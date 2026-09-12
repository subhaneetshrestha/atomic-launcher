package io.github.subhaneetshrestha.atomic.core.collections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What a collection address turns out to hold, decided from the server's word, then the bytes. */
class SourceDetectorTest {
    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `the declared content type is taken first`() {
        assertEquals(SourceKind.IMAGE, SourceDetector.detect("image/jpeg", ByteArray(0)))
        assertEquals(SourceKind.JSON, SourceDetector.detect("application/json; charset=utf-8", ByteArray(0)))
        assertEquals(SourceKind.FEED, SourceDetector.detect("application/rss+xml", ByteArray(0)))
        assertEquals(SourceKind.FEED, SourceDetector.detect("text/xml", ByteArray(0)))
        assertEquals(SourceKind.TEXT, SourceDetector.detect("text/plain", ByteArray(0)))
    }

    @Test
    fun `an unhelpful content type falls through to the bytes`() {
        assertEquals(
            SourceKind.IMAGE,
            SourceDetector.detect("application/octet-stream", bytes(0xFF, 0xD8, 0xFF, 0xE0)),
            "a JPEG announces itself whatever the server says",
        )
        assertEquals(
            SourceKind.IMAGE,
            SourceDetector.detect(null, bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
        )
        assertEquals(SourceKind.IMAGE, SourceDetector.detect(null, "GIF89a...".toByteArray()))
        assertEquals(SourceKind.IMAGE, SourceDetector.detect(null, "RIFF????WEBPVP8 ".toByteArray()))
        assertEquals(SourceKind.IMAGE, SourceDetector.detect(null, "....ftypavif".toByteArray()))
    }

    @Test
    fun `text documents are told apart by their first character`() {
        assertEquals(SourceKind.JSON, SourceDetector.detect(null, "  [\"https://a\"]".toByteArray()))
        assertEquals(SourceKind.JSON, SourceDetector.detect(null, "{\"data\":[]}".toByteArray()))
        assertEquals(SourceKind.FEED, SourceDetector.detect(null, "<?xml version=\"1.0\"?><rss/>".toByteArray()))
        assertEquals(SourceKind.TEXT, SourceDetector.detect(null, "https://example.org/a.jpg\n".toByteArray()))
    }

    @Test
    fun `a byte-order mark does not hide the first character`() {
        val bom = bytes(0xEF, 0xBB, 0xBF) + "[\"https://a\"]".toByteArray()
        assertEquals(SourceKind.JSON, SourceDetector.detect(null, bom))
    }

    @Test
    fun `an address ending in an image extension needs no fetch to be recognised`() {
        assertEquals(SourceKind.IMAGE, SourceDetector.byUrl("https://example.org/a.png"))
        assertNull(SourceDetector.byUrl("https://example.org/list.txt"))
    }
}
