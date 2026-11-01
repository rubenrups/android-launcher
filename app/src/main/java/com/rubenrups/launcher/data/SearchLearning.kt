package com.rubenrups.launcher.data

/**
 * What the drawer learns from searching: for every string typed, which apps were chosen and how often. A result's
 * score is its picks for exactly that string, plus a smaller share of the picks made after typing something longer
 * that starts with it (typing "spo" benefits from the times "spotify" was typed in full).
 */
object SearchLearning {
    const val MAX_QUERIES = 300
    const val MAX_APPS_PER_QUERY = 8
    private const val EXACT_WEIGHT = 10
    private const val LONGER_WEIGHT = 3

    fun normalise(query: String): String = query.trim().lowercase()

    /** The picks with one more choice of [appKey] for [query]. Old, rarely used entries fall away to keep it small. */
    fun record(picks: Map<String, Map<String, Int>>, query: String, appKey: String): Map<String, Map<String, Int>> {
        val q = normalise(query)
        if (q.isEmpty()) return picks
        val forQuery = (picks[q].orEmpty() + (appKey to ((picks[q]?.get(appKey) ?: 0) + 1)))
            .entries.sortedByDescending { it.value }.take(MAX_APPS_PER_QUERY).associate { it.toPair() }
        // The newest query goes last, so trimming from the front drops the one not used for longest.
        val all = LinkedHashMap(picks)
        all.remove(q)
        all[q] = forQuery
        while (all.size > MAX_QUERIES) all.remove(all.keys.first())
        return all
    }

    fun score(picks: Map<String, Map<String, Int>>, query: String, appKey: String): Int {
        val q = normalise(query)
        if (q.isEmpty()) return 0
        var total = (picks[q]?.get(appKey) ?: 0) * EXACT_WEIGHT
        for ((recorded, apps) in picks) {
            if (recorded != q && recorded.startsWith(q)) total += (apps[appKey] ?: 0) * LONGER_WEIGHT
        }
        return total
    }

    /** [items] with the most chosen ones first; ones with the same score keep their order. */
    fun <T> rank(items: List<T>, key: (T) -> String, picks: Map<String, Map<String, Int>>, query: String): List<T> {
        if (picks.isEmpty()) return items
        return items.sortedByDescending { score(picks, query, key(it)) }
    }
}
