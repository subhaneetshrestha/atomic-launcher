package io.github.subhaneetshrestha.atomic.home

import io.github.subhaneetshrestha.atomic.apps.AppEntry
import io.github.subhaneetshrestha.atomic.apps.AppKey
import io.github.subhaneetshrestha.atomic.settings.HomeSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeListModelTest {

    private fun key(pkg: String) = AppKey(pkg, "$pkg.Main", 0L)

    private fun entry(pkg: String, label: String) = AppEntry(key(pkg), label)

    private val alphabetical = listOf(
        entry("a.calc", "Calculator"),
        entry("b.cam", "Camera"),
        entry("c.maps", "Maps"),
        entry("d.msg", "Messages"),
    )

    @Test
    fun `configured apps keep their order and missing ones are dropped`() {
        val settings = HomeSettings(homeApps = listOf(key("c.maps"), key("zz.gone"), key("a.calc")))
        assertEquals(listOf("Maps", "Calculator"), HomeListModel.build(alphabetical, settings).map { it.entry.label })
    }

    @Test
    fun `a key listed twice produces one row`() {
        val settings = HomeSettings(homeApps = listOf(key("b.cam"), key("b.cam")))
        assertEquals(listOf("Camera"), HomeListModel.build(alphabetical, settings).map { it.entry.label })
    }

    @Test
    fun `empty configuration falls back to the first apps alphabetically`() {
        val rows = HomeListModel.build(alphabetical, HomeSettings(homeAppCount = 2))
        assertEquals(listOf("Calculator", "Camera"), rows.map { it.entry.label })
    }

    @Test
    fun `row count is capped`() {
        val many = (1..40).map { entry("p$it", "App $it") }
        assertEquals(HomeListModel.MAX_ROWS, HomeListModel.build(many, HomeSettings(homeAppCount = 99)).size)
        val configured = HomeSettings(homeApps = many.map { it.key })
        assertEquals(HomeListModel.MAX_ROWS, HomeListModel.build(many, configured).size)
    }

    @Test
    fun `no apps yields no rows`() {
        assertEquals(emptyList(), HomeListModel.build(emptyList(), HomeSettings()))
    }
}
