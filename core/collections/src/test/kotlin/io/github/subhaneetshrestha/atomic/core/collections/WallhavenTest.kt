package io.github.subhaneetshrestha.atomic.core.collections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Rewriting a pasted Wallhaven page into the keyless API, tame images only. */
class WallhavenTest {
    @Test
    fun `a search keeps its query and is forced to the tame images`() {
        val api = Wallhaven.apiUrlFor("https://wallhaven.cc/search?q=mountains&categories=100&sorting=views")!!
        assertTrue(api.startsWith("https://wallhaven.cc/api/v1/search?"))
        assertTrue(api.contains("q=mountains"))
        assertTrue(api.contains("categories=100"))
        assertTrue(api.contains("sorting=views"))
        assertTrue(api.contains("purity=100"))
    }

    @Test
    fun `an api key in the pasted address is never carried over`() {
        val api = Wallhaven.apiUrlFor("https://wallhaven.cc/search?q=x&apikey=SECRET&purity=111")!!
        assertTrue(!api.contains("SECRET"), "a key would make the request identifiable, and is not ours to send")
        assertTrue(!api.contains("purity=111"), "the pasted purity is replaced, not merged")
        assertEquals(1, Regex("purity=").findAll(api).count())
    }

    @Test
    fun `the listing pages carry their own sorting`() {
        assertTrue(Wallhaven.apiUrlFor("https://wallhaven.cc/toplist")!!.contains("sorting=toplist"))
        assertTrue(Wallhaven.apiUrlFor("https://www.wallhaven.cc/latest")!!.contains("sorting=date_added"))
        assertTrue(Wallhaven.apiUrlFor("https://wallhaven.cc/hot/")!!.contains("sorting=hot"))
        assertTrue(Wallhaven.apiUrlFor("https://wallhaven.cc/")!!.contains("purity=100"))
    }

    @Test
    fun `a single wallpaper page becomes the request for that wallpaper`() {
        assertEquals("https://wallhaven.cc/api/v1/w/85dz39", Wallhaven.apiUrlFor("https://wallhaven.cc/w/85dz39"))
        assertNull(Wallhaven.apiUrlFor("https://wallhaven.cc/w/../../etc"))
    }

    @Test
    fun `addresses this cannot ask for keylessly are refused rather than guessed at`() {
        assertNull(Wallhaven.apiUrlFor("https://wallhaven.cc/user/someone/favorites"))
        assertNull(Wallhaven.apiUrlFor("https://example.org/search?q=x"))
        assertNull(Wallhaven.apiUrlFor("https://w.wallhaven.cc/full/ab/wallhaven-abc.jpg"))
    }

    @Test
    fun `a parameter that could reshape the request refuses the whole address`() {
        assertNull(Wallhaven.apiUrlFor("https://wallhaven.cc/search?q=a/b"))
        val api = Wallhaven.apiUrlFor("https://wallhaven.cc/search?q=cats&page=3&colors=663399")!!
        assertTrue(api.contains("q=cats"))
        assertTrue(api.contains("colors=663399"))
        assertTrue(!api.contains("page="), "a parameter it does not carry is simply left out")
    }
}
