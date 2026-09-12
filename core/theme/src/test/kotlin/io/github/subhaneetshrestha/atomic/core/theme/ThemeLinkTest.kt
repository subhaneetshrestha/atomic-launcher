package io.github.subhaneetshrestha.atomic.core.theme

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Themes as links: what survives being pasted into a chat, and what a link may ask of us. */
class ThemeLinkTest {
    private fun packed(bytes: ByteArray): String {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(bytes)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    @Test
    fun `a theme goes into a link and comes back out of it`() {
        val theme = BuiltinThemes.terminal.copy(meta = ThemeMeta(id = "mine", name = "Mine", author = "someone"))
        val link = ThemeLink.write(theme)!!
        assertTrue(link.startsWith("atomic://theme?d="))
        val content = assertIs<LinkContent.Document>(ThemeLink.read(link))
        val decoded = assertIs<DecodeResult.Ok<Theme>>(SettingsCodec.decodeTheme(content.json, 1))
        assertEquals(theme, decoded.value)
    }

    @Test
    fun `a link is shorter than the theme it carries`() {
        val theme = BuiltinThemes.paper
        val link = ThemeLink.write(theme)!!
        assertTrue(
            link.length < SettingsCodec.encodeTheme(theme).length,
            "packing is the point: ${link.length} against ${SettingsCodec.encodeTheme(theme).length}",
        )
    }

    @Test
    fun `an address in a link is carried, not followed`() {
        val content =
            assertIs<LinkContent.Address>(ThemeLink.read("atomic://theme?url=https://example.org/a.atomictheme"))
        assertEquals("https://example.org/a.atomictheme", content.url)
    }

    @Test
    fun `an escaped address is unescaped once`() {
        val content =
            assertIs<LinkContent.Address>(ThemeLink.read("atomic://theme?url=https%3A%2F%2Fexample.org%2Fa%3Fv%3D2"))
        assertEquals("https://example.org/a?v=2", content.url)
    }

    @Test
    fun `a plaintext address in a link is refused`() {
        val content = assertIs<LinkContent.Problem>(ThemeLink.read("atomic://theme?url=http://example.org/a"))
        assertTrue(content.reason.contains("https"), content.reason)
    }

    @Test
    fun `a link that is not ours is not ours`() {
        assertNull(ThemeLink.read("https://example.org/theme"))
        assertNull(ThemeLink.read("atomic://settings?d=abc"))
        assertNull(ThemeLink.read("not a link at all"))
    }

    @Test
    fun `a link with nothing in it says so`() {
        val content = assertIs<LinkContent.Problem>(ThemeLink.read("atomic://theme"))
        assertTrue(content.reason.contains("no theme"), content.reason)
    }

    @Test
    fun `a link that is not base64, or not deflated, is refused`() {
        assertIs<LinkContent.Problem>(ThemeLink.unpack("!!!not base64!!!"))
        assertIs<LinkContent.Problem>(ThemeLink.unpack(Base64.getUrlEncoder().encodeToString("plain".toByteArray())))
    }

    @Test
    fun `a small link that would inflate to megabytes is refused`() {
        // Fifty compressed bytes that become a megabyte: the reason there is a ceiling at all.
        val bomb = packed(ByteArray(1024 * 1024) { 'a'.code.toByte() })
        assertTrue(bomb.length < 2048, "the bomb is small: ${bomb.length} characters")
        val content = assertIs<LinkContent.Problem>(ThemeLink.unpack(bomb))
        assertTrue(content.reason.contains("64 KB"), content.reason)
    }
}
