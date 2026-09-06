package io.github.subhaneetshrestha.atomic.core.search

/**
 * One thing that can be found. [aliases] are other names it answers to, such as the name an app
 * came with before the user renamed it. A [hidden] entry is kept out of the way: it is left out of
 * the list of everything and out of ordinary matches, and only surfaces when its whole name is
 * typed.
 */
data class SearchEntry<K>(
    val key: K,
    val label: String,
    val aliases: List<String> = emptyList(),
    val hidden: Boolean = false,
)

data class SearchResult<K>(
    val key: K,
    val label: String,
    val score: Int,
)

/**
 * The searchable set of apps, with every name reduced once when the index is built so that typing
 * only costs the matching itself. Rebuild it when the apps or their names change.
 *
 * Entries are expected in the order they should be shown, which the caller has already sorted the
 * way the phone's language sorts names; with nothing typed they come back in exactly that order.
 */
class SearchIndex<K>(
    entries: List<SearchEntry<K>>,
) {
    private class Prepared<K>(
        val entry: SearchEntry<K>,
        val names: List<NormalizedText>,
        val order: Int,
    ) {
        val length: Int get() = names.first().length
    }

    private val prepared: List<Prepared<K>> =
        entries.mapIndexed { order, entry ->
            Prepared(entry, (listOf(entry.label) + entry.aliases).map(Normalizer::normalize), order)
        }

    fun query(
        text: String,
        limit: Int = DEFAULT_LIMIT,
    ): List<SearchResult<K>> {
        val query = Normalizer.normalize(text)
        if (query.length == 0) {
            return prepared
                .asSequence()
                .filterNot { it.entry.hidden }
                .take(limit)
                .map { SearchResult(it.entry.key, it.entry.label, 0) }
                .toList()
        }
        val found = ArrayList<Pair<Prepared<K>, Int>>()
        for (candidate in prepared) {
            val score = if (candidate.entry.hidden) exactly(candidate, query) else bestOf(candidate, query)
            if (score != null) found += candidate to score
        }
        found.sortWith(byBestMatch())
        return found.take(limit).map { (candidate, score) ->
            SearchResult(candidate.entry.key, candidate.entry.label, score)
        }
    }

    /** The best any of its names can do, or null if none of them match. */
    private fun bestOf(
        candidate: Prepared<K>,
        query: NormalizedText,
    ): Int? {
        var best: Int? = null
        for (name in candidate.names) {
            val score = Scorer.score(name, query)
            if (score != Scorer.NO_MATCH && (best == null || score > best)) best = score
        }
        return best
    }

    /** A hidden app answers to its whole name and nothing less. */
    private fun exactly(
        candidate: Prepared<K>,
        query: NormalizedText,
    ): Int? = if (candidate.names.any { it.codePoints.contentEquals(query.codePoints) }) EXACT else null

    /**
     * Best score first; then the shorter name, because it is the more complete match of the two;
     * then the order they came in, so the list never reshuffles between keystrokes.
     */
    private fun byBestMatch(): Comparator<Pair<Prepared<K>, Int>> =
        compareByDescending<Pair<Prepared<K>, Int>> { it.second }
            .thenBy { it.first.length }
            .thenBy { it.first.order }

    private companion object {
        const val DEFAULT_LIMIT = 30

        /** A hidden app that was named in full is as good a match as there is. */
        const val EXACT = Int.MAX_VALUE
    }
}
