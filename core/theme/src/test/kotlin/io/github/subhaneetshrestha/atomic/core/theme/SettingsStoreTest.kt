package io.github.subhaneetshrestha.atomic.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsStoreTest {
    private val storage = FakeStorage()
    private val scheduler = FakeScheduler()

    private fun store() = SettingsStore(storage, scheduler, debounceMs = 300)

    @Test
    fun `starts from defaults when nothing is stored and persists an update after the debounce`() {
        val store = store()
        assertEquals(Settings(), store.current)
        val changes = mutableListOf<Pair<Settings, Settings>>()
        store.addListener { old, new -> changes += old to new }

        store.update { it.copy(home = it.home.copy(fallbackCount = 3)) }

        assertEquals(3, store.current.home.fallbackCount, "the change is visible at once")
        assertEquals(listOf(Settings() to store.current), changes, "listeners hear about it synchronously")
        assertEquals(emptyList(), storage.writes, "nothing is written before the debounce elapses")
        assertEquals(listOf(300L), scheduler.pendingDelays)

        scheduler.fire()

        assertEquals(DecodeResult.Ok(store.current, emptyList()), SettingsCodec.decodeSettings(storage.writes.single()))
    }

    @Test
    fun `a burst of updates costs one write with the last state`() {
        val store = store()

        store.update { it.copy(home = it.home.copy(fallbackCount = 1)) }
        store.update { it.copy(home = it.home.copy(fallbackCount = 2)) }
        store.update { it.copy(home = it.home.copy(fallbackCount = 3)) }
        assertEquals(1, scheduler.pendingDelays.size, "each update replaces the pending write")
        scheduler.fire()

        assertEquals(1, storage.writes.size)
        assertEquals(3, (SettingsCodec.decodeSettings(storage.stored!!) as DecodeResult.Ok).value.home.fallbackCount)
    }

    @Test
    fun `flush writes pending changes at once and cancels the scheduled write`() {
        val store = store()
        store.update { it.copy(home = it.home.copy(fallbackCount = 5)) }

        store.flush()

        assertEquals(1, storage.writes.size)
        assertEquals(emptyList(), scheduler.pendingDelays)
        store.flush()
        assertEquals(1, storage.writes.size, "a second flush with nothing pending writes nothing")
    }

    @Test
    fun `an update that changes nothing is a no-op`() {
        val store = store()
        var notified = 0
        store.addListener { _, _ -> notified++ }

        store.update { it }
        store.update { it.copy(home = it.home.copy(fallbackCount = it.home.fallbackCount)) }

        assertEquals(0, notified)
        assertEquals(emptyList(), scheduler.pendingDelays)
    }

    @Test
    fun `a corrupt file yields defaults, is kept aside, and is only replaced when something changes`() {
        storage.stored = """{ "schema": 1, "home": { "fallbackCount": "six" } }"""

        val store = store()

        assertEquals(Settings(), store.current)
        assertEquals(
            listOf("corrupt" to storage.stored),
            storage.preserved,
            "the unreadable document is kept for diagnosis",
        )
        assertTrue(store.loadWarnings.any { it.path == "document" }, "the load reports why defaults were used")
        assertEquals(
            emptyList(),
            storage.writes,
            "defaults are not written over the original until the user changes something",
        )
    }

    @Test
    fun `a file from a newer app is loaded best-effort and kept aside before the first write-back`() {
        storage.stored = """{ "schema": 7, "home": { "fallbackCount": 4 }, "hologram": true }"""
        val store = store()

        assertEquals(4, store.current.home.fallbackCount, "known fields are used")
        assertEquals(listOf("schema"), store.loadWarnings.map { it.path })
        assertEquals(emptyList(), storage.preserved, "nothing is copied while the file is left untouched")

        store.update { it.copy(home = it.home.copy(fallbackCount = 2)) }
        scheduler.fire()

        assertEquals(
            listOf("v7" to """{ "schema": 7, "home": { "fallbackCount": 4 }, "hologram": true }"""),
            storage.preserved,
        )
        assertEquals(1, storage.writes.size)
        assertEquals(
            SettingsCodec.SCHEMA,
            (SettingsCodec.decodeSettings(storage.stored!!) as DecodeResult.Ok).value.schema,
        )
    }
}

/** In-memory stand-in for the settings file. */
class FakeStorage(
    var stored: String? = null,
) : SettingsStorage {
    val writes = mutableListOf<String>()
    val preserved = mutableListOf<Pair<String, String>>()

    override fun read(): String? = stored

    override fun write(text: String) {
        stored = text
        writes += text
    }

    override fun preserve(
        text: String,
        tag: String,
    ) {
        preserved += tag to text
    }
}

/** Records scheduled writes; [fire] runs them, as the Android Handler would after the delay. */
class FakeScheduler : WriteScheduler {
    private val pending = mutableListOf<Pair<Long, () -> Unit>>()

    val pendingDelays: List<Long> get() = pending.map { it.first }

    override fun schedule(
        delayMs: Long,
        action: () -> Unit,
    ): () -> Unit {
        val entry = delayMs to action
        pending += entry
        return { pending.remove(entry) }
    }

    fun fire() {
        val due = pending.toList()
        pending.clear()
        due.forEach { it.second() }
    }
}
