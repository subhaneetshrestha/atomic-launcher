package io.github.subhaneetshrestha.atomic.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Editing the theme that is showing. */
class ThemeEditsTest {
    @Test
    fun `a value typed out of range is pulled into it as it is typed`() {
        val edited =
            ThemeEdits.edit(BuiltinThemes.ink) {
                it.copy(typography = it.typography.copy(sizes = it.typography.sizes.copy(homeSp = 999f)))
            }
        assertEquals(64f, edited.typography.sizes.homeSp)
    }

    @Test
    fun `changing a built-in makes the theme the user's own`() {
        val edited = ThemeEdits.edit(BuiltinThemes.ink) { it.copy(colors = it.colors.copy(accent = "#FF00FF00")) }
        assertEquals(ThemeEdits.CUSTOM_ID, edited.meta.id)
        assertEquals("Ink", edited.meta.name, "the name it was given is still its name")
        assertNotEquals(BuiltinThemes.ink, edited)
    }

    @Test
    fun `an edit that changes nothing leaves the built-in alone`() {
        val same = ThemeEdits.edit(BuiltinThemes.terminal) { it.copy(colors = it.colors.copy(accent = "#ff39ff14")) }
        assertEquals(BuiltinThemes.terminal, same, "the same colour written another way is the same colour")
        assertEquals("terminal", same.meta.id)
    }

    @Test
    fun `a colour that is not a colour is refused rather than stored`() {
        val edited = ThemeEdits.edit(BuiltinThemes.ink) { it.copy(colors = it.colors.copy(text = "puce")) }
        assertEquals(BuiltinThemes.ink.colors.text, edited.colors.text)
    }

    @Test
    fun `which built-in is showing, if any`() {
        assertEquals(BuiltinThemes.paper, ThemeEdits.builtinBehind(BuiltinThemes.paper))
        assertNull(ThemeEdits.builtinBehind(BuiltinThemes.paper.copy(layout = Layout(rowGapDp = 12))))
        assertNull(ThemeEdits.builtinBehind(Theme()))
    }

    @Test
    fun `cleaning says what it corrected`() {
        val (cleaned, warnings) = ThemeEdits.clean(BuiltinThemes.ink.copy(layout = Layout(paddingDp = Padding(h = -4))))
        assertEquals(0, cleaned.layout.paddingDp.h)
        assertTrue(warnings.any { it.path.contains("paddingDp.h") })
    }
}
