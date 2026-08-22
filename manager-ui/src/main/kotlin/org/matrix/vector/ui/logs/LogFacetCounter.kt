package org.matrix.vector.ui.logs

/**
 * Counts what a log holds while deciding what the current query keeps, in the host's one scanning pass.
 *
 * A category is counted under the *other* categories, never under itself. Choosing an application and then opening the
 * filter should show the tags that application writes, with the counts it wrote them — not the tags of a log the reader
 * has already narrowed away, which would offer choices that select nothing, nor the counts of the whole file, which
 * would promise lines the filter will not show. Its own category is left out so that the alternatives beside a chosen
 * chip stay visible: picking one tag must not make every other tag disappear, or a second one could never be added.
 *
 * The rule falls out of that: for each facet, every condition applies except the facet's own.
 *
 * Both hosts scan their own format but ask the same question of it, so this is written once. [add] reports whether the
 * row survives the whole query, which is what the caller records as a match.
 */
class LogFacetCounter(private val query: LogQuery) {

    private val tags = HashMap<String, Int>()
    private val levels = HashMap<LogLevel, Int>()
    private val writers = HashMap<Int, Int>()

    fun add(row: LogRow): Boolean {
        if (row !is LogRow.Entry) return query.matches(row)

        val level = query.matchesLevel(row)
        val writer = query.matchesWriter(row)
        val tag = query.matchesTag(row)
        val text = query.matchesText(row)

        if (writer && tag && text) levels[row.level] = (levels[row.level] ?: 0) + 1
        if (level && tag && text && row.uid >= 0) writers[row.uid] = (writers[row.uid] ?: 0) + 1
        if (level && writer && text) tags[row.tag] = (tags[row.tag] ?: 0) + 1

        return level && writer && tag && text
    }

    /**
     * Ordered by weight: what a log is mostly made of is what a reader is mostly looking to include or exclude.
     *
     * Whatever the query already selects is listed even where nothing survives the rest of the filter -- at a count of
     * zero, which is the honest number. A chip counted out of existence would be a filter still narrowing the log with
     * no way left to switch it off.
     */
    fun facets(): LogFacets {
        query.tags.forEach { tags.putIfAbsent(it, 0) }
        query.uids.forEach { writers.putIfAbsent(it, 0) }
        return LogFacets(
            tags = tags.entries.sortedByDescending { it.value }.map { it.key to it.value },
            levels = levels,
            writers = writers.entries.sortedByDescending { it.value }.map { LogWriter(it.key, it.value) },
        )
    }
}
