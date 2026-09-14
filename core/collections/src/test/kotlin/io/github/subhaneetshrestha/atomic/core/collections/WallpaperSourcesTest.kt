package io.github.subhaneetshrestha.atomic.core.collections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WallpaperSourcesTest {
    @Test
    fun `every source has a unique id`() {
        val ids = WallpaperSources.all.map { it.id }
        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun `a source with no input carries the one address it fetches`() {
        val search = WallpaperSources.WALLHAVEN_SEARCH
        assertEquals(SourceInput.NONE, search.input)
        assertTrue(search.fixedUrl!!.startsWith("https://wallhaven.cc/search?"))
    }

    @Test
    fun `the fixed wallhaven search rewrites keylessly to the tame, phone-shaped images`() {
        // The source table's whole job is to hand Wallhaven.apiUrlFor an address it already knows
        // how to rewrite; this pins that the two stay in step.
        val api = Wallhaven.apiUrlFor(WallpaperSources.WALLHAVEN_SEARCH.fixedUrl!!)!!
        assertTrue(api.contains("categories=100"))
        assertTrue(api.contains("purity=100"))
        assertTrue(api.contains("ratios=9x16,10x16,9x18"))
        assertTrue(api.contains("atleast=1080x1920"))
        assertTrue(api.contains("sorting=random"))
    }

    @Test
    fun `an address source carries no fixed url`() {
        for (source in WallpaperSources.all - WallpaperSources.WALLHAVEN_SEARCH) {
            assertEquals(SourceInput.ADDRESS, source.input, source.id)
            assertNull(source.fixedUrl, source.id)
        }
    }

    @Test
    fun `byId answers a known source and nothing else`() {
        assertEquals(WallpaperSources.CUSTOM, WallpaperSources.byId("custom"))
        assertNull(WallpaperSources.byId("something a future build invented"))
    }
}
