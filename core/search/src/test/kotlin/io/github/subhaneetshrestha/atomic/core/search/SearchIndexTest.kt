package io.github.subhaneetshrestha.atomic.core.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchIndexTest {
    private fun index(vararg entries: SearchEntry<String>) = SearchIndex(entries.toList())

    private fun labels(results: List<SearchResult<String>>) = results.map { it.label }

    private val apps =
        index(
            SearchEntry("calc", "Calculator"),
            SearchEntry("cam", "Camera"),
            SearchEntry("gmail", "Gmail"),
            SearchEntry("maps", "Google Maps"),
            SearchEntry("insta", "Instagram"),
        )

    @Test
    fun `no query lists everything in the order it was given`() {
        assertEquals(
            listOf("Calculator", "Camera", "Gmail", "Google Maps", "Instagram"),
            labels(apps.query("")),
            "the caller has already put them in the order the language sorts them",
        )
        assertEquals(listOf("Calculator", "Camera"), labels(apps.query("  ", limit = 2)), "blank counts as no query")
    }

    @Test
    fun `a query keeps what matches, best first, up to the limit`() {
        assertEquals(listOf("Gmail", "Google Maps"), labels(apps.query("gm")))
        assertEquals(listOf("Gmail"), labels(apps.query("gm", limit = 1)))
        assertEquals(listOf("Camera"), labels(apps.query("cam")), "Calculator has no m in it")
        assertEquals(
            listOf("Camera", "Calculator"),
            labels(apps.query("ca")),
            "both score the same, so the shorter name comes first",
        )
        assertEquals(emptyList(), labels(apps.query("zzz")))
    }

    @Test
    fun `a hidden app stays out of the way until its whole name is typed`() {
        val withHidden =
            index(
                SearchEntry("calc", "Calculator"),
                SearchEntry("vault", "Secret Vault", hidden = true),
            )

        assertEquals(listOf("Calculator"), labels(withHidden.query("")), "not in the list of everything")
        assertEquals(emptyList(), labels(withHidden.query("sec")), "nor part way through its name")
        assertEquals(emptyList(), labels(withHidden.query("vault")), "nor by one of its words")
        assertEquals(listOf("Secret Vault"), labels(withHidden.query("secret vault")), "only the whole name")
        assertEquals(listOf("Secret Vault"), labels(withHidden.query("Secret  VAULT")), "spacing and case aside")
    }

    @Test
    fun `a renamed app answers to both names and shows the one the user chose`() {
        val renamed = index(SearchEntry("mail", "Mail", aliases = listOf("Gmail")))

        assertEquals(listOf("Mail"), labels(renamed.query("mail")))
        assertEquals(listOf("Mail"), labels(renamed.query("gmail")), "the name it came with still finds it")
        assertEquals(listOf("Mail"), labels(renamed.query("gm")))
        assertEquals(1, renamed.query("mail").size, "and it is one result, not two")
    }

    @Test
    fun `equal matches come out in a settled order`() {
        val same = index(SearchEntry("b", "Notes"), SearchEntry("a", "Note"), SearchEntry("c", "Notes"))

        assertEquals(
            listOf("Note", "Notes", "Notes"),
            labels(same.query("note")),
            "the shorter name first, then as given",
        )
        assertEquals(listOf("a", "b", "c"), same.query("note").map { it.key })
        assertEquals(same.query("note"), same.query("note"), "and the same every time")
    }

    @Test
    fun `a thousand apps are searched without a pause`() {
        val many = SearchIndex((1..1000).map { SearchEntry("k$it", "Sample App Number $it") })
        val queries = listOf("sam", "app", "num", "san", "s1", "sa9", "sample", "appnum", "n42", "zzz")

        // Warm the code paths, then measure: the point is to catch a change that makes this slow,
        // not to be a benchmark, so the bound is generous.
        repeat(3) { queries.forEach { many.query(it) } }
        val started = System.nanoTime()
        repeat(10) { queries.forEach { many.query(it) } }
        val perPass = (System.nanoTime() - started) / 1_000_000.0 / 10

        assertTrue(perPass < 250, "ten queries over a thousand apps took $perPass ms")
        println("search: ten queries over a thousand apps in $perPass ms")
    }

    @Test
    fun `the list of every app is as long as it needs to be`() {
        val many = SearchIndex((1..45).map { SearchEntry("k$it", "App $it") })

        assertEquals(45, many.query("", limit = Int.MAX_VALUE).size, "a phone with 45 apps shows 45")
        assertEquals(30, many.query("").size, "the default cap is still there for whoever wants it")
        assertEquals(45, many.query("app", limit = Int.MAX_VALUE).size)
    }
}
