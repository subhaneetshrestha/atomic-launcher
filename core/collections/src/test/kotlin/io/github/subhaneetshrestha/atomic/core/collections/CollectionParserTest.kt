package io.github.subhaneetshrestha.atomic.core.collections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fixtures for the four document kinds, including the ones written by something careless. */
class CollectionParserTest {
    private val base = "https://example.org/list"

    private fun parse(
        kind: SourceKind,
        body: String,
    ) = CollectionParser.parse(kind, body, base)

    @Test
    fun `a text list takes one address a line and ignores comments`() {
        val parsed =
            parse(
                SourceKind.TEXT,
                """
                # my wallpapers
                https://example.org/a.jpg

                  https://example.org/b.jpg   # the good one
                http://example.org/insecure.jpg
                /relative/c.jpg
                """.trimIndent(),
            )
        assertEquals(
            listOf("https://example.org/a.jpg", "https://example.org/b.jpg", "https://example.org/relative/c.jpg"),
            parsed.urls,
        )
    }

    @Test
    fun `a JSON array of addresses is read as it stands`() {
        val parsed = parse(SourceKind.JSON, """["https://example.org/a.jpg", "https://example.org/b.png"]""")
        assertEquals(listOf("https://example.org/a.jpg", "https://example.org/b.png"), parsed.urls)
    }

    @Test
    fun `the wallhaven answer is read without a parser of its own`() {
        val parsed =
            parse(
                SourceKind.JSON,
                """
                {"data":[
                  {"id":"abc","path":"https://w.wallhaven.cc/full/ab/wallhaven-abc.jpg",
                   "thumbs":{"small":"https://th.wallhaven.cc/small/ab/abc.jpg"}},
                  {"id":"def","path":"https://w.wallhaven.cc/full/de/wallhaven-def.png"}
                ],"meta":{"current_page":1}}
                """.trimIndent(),
            )
        assertTrue(parsed.urls.contains("https://w.wallhaven.cc/full/ab/wallhaven-abc.jpg"))
        assertTrue(parsed.urls.contains("https://w.wallhaven.cc/full/de/wallhaven-def.png"))
    }

    @Test
    fun `a wallpaper's thumbnails never join its own full-size image`() {
        // The bug this pins: thumbs.small, .large and .original are all image-extension URLs too,
        // so a walk that does not know it is inside a thumbnail container collects all four per
        // wallpaper — three of every four "images" a Wallhaven rotation picked were 300px thumbs.
        val parsed =
            parse(
                SourceKind.JSON,
                """
                {"data":[
                  {"id":"abc","path":"https://w.wallhaven.cc/full/ab/wallhaven-abc.jpg",
                   "thumbs":{"small":"https://th.wallhaven.cc/small/ab/abc.jpg",
                             "large":"https://th.wallhaven.cc/large/ab/abc.jpg",
                             "original":"https://th.wallhaven.cc/original/ab/abc.jpg"}},
                  {"id":"def","path":"https://w.wallhaven.cc/full/de/wallhaven-def.png",
                   "thumbs":{"small":"https://th.wallhaven.cc/small/de/def.jpg"}}
                ],"meta":{"current_page":1}}
                """.trimIndent(),
            )
        assertEquals(
            listOf(
                "https://w.wallhaven.cc/full/ab/wallhaven-abc.jpg",
                "https://w.wallhaven.cc/full/de/wallhaven-def.png",
            ),
            parsed.urls,
        )
    }

    @Test
    fun `a source with only thumbnails falls back to them rather than finding nothing`() {
        val parsed =
            parse(
                SourceKind.JSON,
                """{"thumbs":{"small":"https://example.org/only-thumb.jpg"}}""",
            )
        assertEquals(listOf("https://example.org/only-thumb.jpg"), parsed.urls)
        assertEquals(
            listOf("no full-size image found; used a thumbnail or the url, path and src fields instead"),
            parsed.warnings,
        )
    }

    @Test
    fun `addresses with no extension are taken from the fields that name addresses`() {
        val parsed =
            parse(
                SourceKind.JSON,
                """[{"url":"https://images.example.org/photo-1?w=1080","title":"one"}]""",
            )
        assertEquals(listOf("https://images.example.org/photo-1?w=1080"), parsed.urls)
        assertTrue(parsed.warnings.single().contains("no full-size image found"))
    }

    @Test
    fun `JSON that is not JSON comes back empty with a reason`() {
        val parsed = parse(SourceKind.JSON, "{ not json")
        assertTrue(parsed.isEmpty)
        assertTrue(parsed.warnings.single().contains("could not be read"))
    }

    @Test
    fun `RSS enclosures, media items and atom links are all found`() {
        val parsed =
            parse(
                SourceKind.FEED,
                """
                <?xml version="1.0" encoding="utf-8"?>
                <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
                  <channel>
                    <item>
                      <link>https://example.org/post/1</link>
                      <enclosure url="https://example.org/a.jpg" type="image/jpeg" length="1234"/>
                    </item>
                    <item>
                      <media:content url="https://example.org/b.png?s=1&amp;v=2" medium="image"/>
                      <media:thumbnail url="https://example.org/thumb.jpg"/>
                    </item>
                    <item>
                      <enclosure url="https://example.org/podcast.mp3" type="audio/mpeg"/>
                      <link rel="enclosure" type="image/webp" href="/c.webp"/>
                    </item>
                  </channel>
                </rss>
                """.trimIndent(),
            )
        assertEquals(
            listOf(
                "https://example.org/a.jpg",
                "https://example.org/b.png?s=1&v=2",
                "https://example.org/thumb.jpg",
                "https://example.org/c.webp",
            ),
            parsed.urls,
            "the article link and the audio enclosure are not images",
        )
    }

    @Test
    fun `a feed that declares a document type is not read at all`() {
        val bomb =
            """
            <?xml version="1.0"?>
            <!DOCTYPE rss [<!ENTITY a "aaaaaaaaaa"><!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">]>
            <rss><channel><item><enclosure url="https://example.org/a.jpg" type="image/jpeg"/></item></channel></rss>
            """.trimIndent()
        val parsed = parse(SourceKind.FEED, bomb)
        assertTrue(parsed.isEmpty)
        assertTrue(parsed.warnings.single().contains("document type"))
    }

    @Test
    fun `a broken feed keeps what it read before it broke`() {
        val parsed =
            parse(
                SourceKind.FEED,
                """<rss><channel><item><enclosure url="https://example.org/a.jpg" type="image/jpeg"/>""",
            )
        assertEquals(listOf("https://example.org/a.jpg"), parsed.urls)
        assertTrue(parsed.warnings.single().contains("well-formed"))
    }

    @Test
    fun `a direct image is a collection of one`() {
        val parsed = CollectionParser.parse(SourceKind.IMAGE, "", "https://example.org/only.jpg")
        assertEquals(listOf("https://example.org/only.jpg"), parsed.urls)
    }
}
