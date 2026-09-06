package io.github.subhaneetshrestha.atomic.core.search

import kotlin.test.Test
import kotlin.test.assertEquals

/** Proves the module compiles and its tests run on the JUnit Platform. */
class ModuleWiringTest {
    @Test
    fun `code points survive a lowercase round-trip`() {
        val label = "Café"
        assertEquals("café", label.lowercase())
    }
}
