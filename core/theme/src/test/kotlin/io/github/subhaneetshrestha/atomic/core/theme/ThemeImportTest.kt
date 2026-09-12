package io.github.subhaneetshrestha.atomic.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a theme has to say for itself before it is allowed to change every colour on the screen. */
class ThemeImportTest {
    private fun read(
        json: String,
        version: Int = 1,
    ) = ThemeImport.read(json, version)

    @Test
    fun `a theme introduces itself`() {
        val outcome = assertIs<ThemeImport.Outcome.Ready>(read(SettingsCodec.encodeTheme(BuiltinThemes.paper)))
        assertEquals("Paper", outcome.preview.name)
        assertEquals("atomic", outcome.preview.author)
        assertEquals("CC0-1.0", outcome.preview.license)
        assertTrue(outcome.preview.warnings.isEmpty())
        assertNull(outcome.preview.imageHost, "this one downloads nothing")
    }

    @Test
    fun `a theme that would download images names the host it would download from`() {
        val theme =
            BuiltinThemes.ink.copy(
                background =
                    Background(
                        mode = BackgroundMode.COLLECTION,
                        collection = CollectionConfig(url = "https://images.example.org/list.txt"),
                    ),
            )
        val outcome = assertIs<ThemeImport.Outcome.Ready>(read(SettingsCodec.encodeTheme(theme)))
        assertEquals("images.example.org", outcome.preview.imageHost)
    }

    @Test
    fun `a collection the fetcher would refuse is cleared, and the reason is shown`() {
        val outcome =
            assertIs<ThemeImport.Outcome.Ready>(
                read("""{"background":{"mode":"collection","collection":{"url":"http://images.example.org/list"}}}"""),
            )
        assertNull(outcome.preview.imageHost)
        assertTrue(outcome.preview.warnings.any { it.path.endsWith("collection.url") })
    }

    @Test
    fun `a theme from a newer app is refused rather than guessed at`() {
        val outcome = assertIs<ThemeImport.Outcome.Refused>(read("""{"schema":99,"meta":{"name":"Future"}}"""))
        assertTrue(outcome.reason.contains("newer version"), outcome.reason)
    }

    @Test
    fun `a theme made for a newer app version is applied, with a warning`() {
        val outcome =
            assertIs<ThemeImport.Outcome.Ready>(read("""{"minAppVersion":40,"meta":{"name":"Later"}}""", version = 7))
        assertEquals("Later", outcome.preview.name)
        assertTrue(outcome.preview.warnings.any { it.path == "minAppVersion" })
    }

    @Test
    fun `something that is not a theme at all is refused`() {
        assertIs<ThemeImport.Outcome.Refused>(read("this is not JSON"))
        assertIs<ThemeImport.Outcome.Refused>(read(""))
        assertIs<ThemeImport.Outcome.Refused>(read("[1,2,3]"))
    }
}
