package io.github.subhaneetshrestha.atomic.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The background section of a theme: what survives being read from a document someone else wrote. */
class BackgroundThemeTest {
    private fun read(json: String): DecodeResult.Ok<Theme> =
        SettingsCodec.decodeTheme(json, appVersionCode = 1) as DecodeResult.Ok<Theme>

    @Test
    fun `a theme with no background section gets the plain colour one`() {
        val theme = read("""{"meta":{"id":"x"}}""").value
        assertEquals(BackgroundMode.COLOR, theme.background.mode)
        assertEquals(CollectionConfig.DEFAULT_INTERVAL_MINUTES, theme.background.collection.intervalMinutes)
        assertTrue(!theme.background.collection.isConfigured)
    }

    @Test
    fun `a collection round-trips through the document`() {
        val theme =
            BuiltinThemes.ink.copy(
                background =
                    Background(
                        mode = BackgroundMode.COLLECTION,
                        collection =
                            CollectionConfig(
                                url = "https://example.org/walls.txt",
                                intervalMinutes = 60,
                                unmeteredOnly = false,
                                shuffle = false,
                            ),
                        dim = 0.5f,
                    ),
            )
        val decoded = read(SettingsCodec.encodeTheme(theme)).value
        assertEquals(theme.background, decoded.background)
    }

    @Test
    fun `an address the fetcher would refuse is cleared, and says so`() {
        val result =
            read(
                """{"background":{"mode":"collection","collection":{"url":"http://example.org/walls.txt"}}}""",
            )
        assertEquals("", result.value.background.collection.url)
        assertEquals(BackgroundMode.COLLECTION, result.value.background.mode)
        val warning = result.warnings.single { it.path == "background.collection.url" }
        assertTrue(warning.message.contains("https"), warning.message)
    }

    @Test
    fun `an interval below what Android will schedule is pulled up to it`() {
        val result = read("""{"background":{"collection":{"url":"","intervalMinutes":1}}}""")
        assertEquals(
            CollectionConfig.MIN_INTERVAL_MINUTES,
            result.value.background.collection.intervalMinutes,
        )
        assertTrue(result.warnings.any { it.path == "background.collection.intervalMinutes" })
        val long = read("""{"background":{"collection":{"intervalMinutes":100000}}}""")
        assertEquals(CollectionConfig.MAX_INTERVAL_MINUTES, long.value.background.collection.intervalMinutes)
    }

    @Test
    fun `dim stays between none and black, and an angle is turned back into a circle`() {
        val result =
            read("""{"background":{"mode":"gradient","dim":4.5,"gradient":{"angle":-90,"from":"puce"}}}""")
        assertEquals(1f, result.value.background.dim)
        assertEquals(270, result.value.background.gradient.angle)
        assertEquals(Gradient().from, result.value.background.gradient.from)
        assertTrue(result.warnings.any { it.path == "background.gradient.from" })
        assertTrue(result.warnings.any { it.path == "background.dim" })
    }

    @Test
    fun `an unknown mode falls back to the plain colour rather than failing the document`() {
        val result = read("""{"background":{"mode":"lava-lamp"}}""")
        assertEquals(BackgroundMode.COLOR, result.value.background.mode)
    }
}
