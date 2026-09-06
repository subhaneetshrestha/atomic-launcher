package io.github.subhaneetshrestha.atomic.core.theme

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** Proves the serialization compiler plugin and the JUnit Platform are wired for this module. */
class ModuleWiringTest {

    @Serializable
    private data class Probe(val name: String, val size: Int = 1)

    @Test
    fun `serializable classes round-trip and tolerate unknown keys`() {
        val json = Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(Probe.serializer(), Probe("ink", 3))
        assertEquals(Probe("ink", 3), json.decodeFromString(Probe.serializer(), encoded))
        assertEquals(
            Probe("paper"),
            json.decodeFromString(Probe.serializer(), """{"name":"paper","unknown":true}"""),
        )
    }
}
