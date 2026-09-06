package io.github.subhaneetshrestha.atomic.core.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class SettingsCodecTest {
    @Test
    fun `default settings survive an encode-decode round-trip`() {
        val text = SettingsCodec.encodeSettings(Settings())

        assertTrue(text.contains("\"schema\": 1"), "document declares its schema version:\n$text")
        assertEquals(DecodeResult.Ok(Settings(), emptyList()), SettingsCodec.decodeSettings(text))
    }

    @Test
    fun `unknown keys are ignored and missing fields take their defaults`() {
        val text = """{ "schema": 1, "extra": true, "home": { "fallbackCount": 3, "later": [1, 2] } }"""

        assertEquals(
            DecodeResult.Ok(Settings(home = HomeConfig(fallbackCount = 3)), emptyList()),
            SettingsCodec.decodeSettings(text),
        )
    }

    @Test
    fun `corrupt documents are reported, never thrown`() {
        assertIs<DecodeResult.Corrupt>(SettingsCodec.decodeSettings(""))
        assertIs<DecodeResult.Corrupt>(SettingsCodec.decodeSettings("""{ "schema": 1, "home": """))
        assertIs<DecodeResult.Corrupt>(SettingsCodec.decodeSettings("[1, 2, 3]"))
        assertIs<DecodeResult.Corrupt>(SettingsCodec.decodeSettings("""{ "home": { "fallbackCount": "six" } }"""))
    }

    @Test
    fun `out-of-range numbers are clamped and reported`() {
        val text = """
            { "home": { "fallbackCount": 99 },
              "theme": { "typography": { "weight": 450, "sizes": { "homeSp": 500, "clockSp": 1 } },
                         "layout": { "rowGapDp": -5, "paddingDp": { "h": 999 } } } }
        """

        val result = assertIs<DecodeResult.Ok<Settings>>(SettingsCodec.decodeSettings(text))

        assertEquals(16, result.value.home.fallbackCount)
        assertEquals(500, result.value.theme.typography.weight, "weights snap to the nearest hundred")
        assertEquals(64f, result.value.theme.typography.sizes.homeSp)
        assertEquals(12f, result.value.theme.typography.sizes.clockSp)
        assertEquals(0, result.value.theme.layout.rowGapDp)
        assertEquals(200, result.value.theme.layout.paddingDp.h)
        assertEquals(
            listOf(
                "home.fallbackCount",
                "theme.typography.weight",
                "theme.typography.sizes.homeSp",
                "theme.typography.sizes.clockSp",
                "theme.layout.rowGapDp",
                "theme.layout.paddingDp.h",
            ).sorted(),
            result.warnings.map { it.path }.sorted(),
        )
    }

    @Test
    fun `colours are normalised, tokens allowed, and invalid values fall back with a warning`() {
        val text = """
            { "theme": {
                "colors": { "background": "#102030", "text": "auto", "textSecondary": "red",
                            "accent": "@android:color/system_accent1_500" },
                "darkColors": { "background": "auto", "text": "#80ffffff", "accent": "@android:color/holo_blue" } } }
        """

        val result = assertIs<DecodeResult.Ok<Settings>>(SettingsCodec.decodeSettings(text))

        val colors = result.value.theme.colors
        assertEquals("#FF102030", colors.background, "six-digit hex gains an opaque alpha")
        assertEquals("auto", colors.text, "auto is legal for text roles")
        assertEquals(
            Colors().textSecondary,
            colors.textSecondary,
            "an unknown colour name falls back to the role default",
        )
        assertEquals("@android:color/system_accent1_500", colors.accent)
        val dark = result.value.theme.darkColors!!
        assertEquals(null, dark.background, "auto is not legal for the background")
        assertEquals("#80FFFFFF", dark.text, "hex is upper-cased")
        assertEquals(null, dark.accent, "only system_* tokens are allowed")
        assertEquals(
            listOf("theme.colors.textSecondary", "theme.darkColors.accent", "theme.darkColors.background"),
            result.warnings.map { it.path }.sorted(),
        )
    }

    @Test
    fun `a built-in theme survives an encode-decode round-trip`() {
        val text = SettingsCodec.encodeTheme(BuiltinThemes.ink)

        assertEquals(
            DecodeResult.Ok(BuiltinThemes.ink, emptyList()),
            SettingsCodec.decodeTheme(text, appVersionCode = 1),
        )
    }

    @Test
    fun `themes written by a newer app are refused, settings are loaded best-effort`() {
        assertEquals(
            DecodeResult.Unsupported(schema = 99, supported = SettingsCodec.THEME_SCHEMA),
            SettingsCodec.decodeTheme("""{ "schema": 99, "meta": { "id": "future" } }""", appVersionCode = 1),
        )

        val result =
            assertIs<DecodeResult.Ok<Settings>>(
                SettingsCodec.decodeSettings("""{ "schema": 99, "home": { "fallbackCount": 2 } }"""),
            )
        assertEquals(2, result.value.home.fallbackCount, "known fields are still read")
        assertEquals(
            SettingsCodec.SCHEMA,
            result.value.schema,
            "the document is re-stamped with the schema this app writes",
        )
        assertEquals(listOf("schema"), result.warnings.map { it.path })
    }

    @Test
    fun `a theme that asks for a newer app loads with a warning`() {
        val text = """{ "schema": 1, "minAppVersion": 20500, "meta": { "id": "fancy", "name": "Fancy" } }"""

        val result = assertIs<DecodeResult.Ok<Theme>>(SettingsCodec.decodeTheme(text, appVersionCode = 10000))

        assertEquals("fancy", result.value.meta.id)
        assertEquals(listOf("minAppVersion"), result.warnings.map { it.path })
    }

    @Test
    fun `app lists are validated, de-duplicated and capped`() {
        val filler = (1..20).joinToString(",") { """{ "component": "com.filler.app$it/.Main" }""" }
        val longLabel = "x".repeat(60)
        val text = """
            { "home": { "entries": [
                { "component": "com.a/.Main", "user": 0, "label": "  Mail  " },
                { "component": "com.a/.Main" },
                { "component": "not a component" },
                { "component": "com.b/.Main", "user": -3 },
                { "component": "com.c/.Main", "label": "$longLabel" },
                $filler ] },
              "hidden": [ { "component": "com.a/.Main" }, { "component": "com.a/.Main" }, { "component": "junk" } ],
              "renames": [ { "component": "com.a/.Main", "label": "Tab\u0007Mail" }, { "component": "com.z/.Main", "label": "   " } ] }
        """

        val result = assertIs<DecodeResult.Ok<Settings>>(SettingsCodec.decodeSettings(text))

        val home = result.value.home.entries
        assertEquals(HomeLimits.MAX_ROWS, home.size, "the home list is capped")
        assertEquals("Mail", home[0].label, "labels are trimmed")
        assertEquals(1, home.count { it.component == "com.a/.Main" }, "a component appears once")
        assertTrue(home.none { it.component == "not a component" || it.user < 0 }, "malformed entries are dropped")
        assertEquals(40, home.first { it.component == "com.c/.Main" }.label!!.length, "labels are cut at 40 characters")
        assertEquals(listOf(AppRef("com.a/.Main")), result.value.hidden)
        assertEquals(
            listOf(Rename("com.a/.Main", label = "TabMail")),
            result.value.renames,
            "control characters go; blank renames go entirely",
        )
        val paths = result.warnings.map { it.path }
        assertTrue(paths.any { it.startsWith("home.entries") }, "dropped or shortened entries are reported: $paths")
        assertTrue("hidden[2]" in paths && "renames[1]" in paths, "dropped list items are reported by index: $paths")
    }

    @Test
    fun `every built-in theme is valid without corrections`() {
        assertEquals(listOf("ink", "paper", "terminal", "you"), BuiltinThemes.all.map { it.meta.id })
        for (theme in BuiltinThemes.all) {
            val result =
                assertIs<DecodeResult.Ok<Theme>>(
                    SettingsCodec.decodeTheme(SettingsCodec.encodeTheme(theme), appVersionCode = 1),
                )
            assertEquals(emptyList(), result.warnings, theme.meta.id)
            assertEquals(theme, result.value, theme.meta.id)
        }
    }

    // The golden files are the published example themes for theme authors and a regression guard
    // for the document format: any change here must be deliberate and shows up as a diff.
    @Test
    fun `built-in themes match the published example files`() {
        val dir = File("../../docs/themes")
        val created = mutableListOf<String>()
        for (theme in BuiltinThemes.all) {
            val golden = File(dir, "${theme.meta.id}.atomictheme")
            val actual = SettingsCodec.encodeTheme(theme)
            if (!golden.exists()) {
                golden.writeText(actual)
                created += golden.name
            } else {
                assertEquals(golden.readText(), actual, "${golden.name} is out of date with BuiltinThemes")
            }
        }
        if (created.isNotEmpty()) fail("created from the code, review and rerun: ${created.joinToString()}")
    }
}
