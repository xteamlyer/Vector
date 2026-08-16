package org.matrix.vector.ui.logs

/**
 * The shape of a parsed log, format-agnostic.
 *
 * These are the types the shared Logs screen and its view model read; the *parsing* of raw bytes
 * into them is the host's job, because the two hosts read different formats. Vector reads the
 * daemon's own `logcat.cpp` framing (`[ ts uid:pid:tid L/tag ] message`) with its multi-line
 * `writev` continuations; LSPatch reads Android's `threadtime` logcat over a Shizuku shell. Both
 * produce [LogRow.Entry] values, and everything above the reader — windowing, filtering, the row
 * renderer — is written against these types alone.
 */
enum class LogLevel(val char: Char) {
    VERBOSE('V'),
    DEBUG('D'),
    INFO('I'),
    WARN('W'),
    ERROR('E'),
    FATAL('F'),
    SILENT('S'),
    UNKNOWN('?');

    companion object {
        fun of(c: Char): LogLevel =
            when (c) {
                'V' -> VERBOSE
                'D' -> DEBUG
                'I' -> INFO
                'W' -> WARN
                'E' -> ERROR
                'F',
                'A' -> FATAL // liblog folds ASSERT into fatal
                'S' -> SILENT
                else -> UNKNOWN
            }

        /**
         * The levels worth offering as a filter. Nothing writes at `SILENT`, and `UNKNOWN` is what
         * an unrecognised level character degrades to.
         */
        val selectable = listOf(VERBOSE, DEBUG, INFO, WARN, ERROR, FATAL)
    }
}

/** One row of the rendered log. [index] is the line's absolute position in the file. */
sealed interface LogRow {
    val index: Int

    /**
     * Stable identity for the lazy list.
     *
     * This is what lets the window be extended upwards without the viewport lurching: the list
     * re-resolves its first visible item by key after rows are inserted above it, so a prepend
     * re-anchors instead of shifting. A day break shares its line's index with the entry it
     * introduces, so its key is negated to keep the two distinct.
     */
    val key: Long
        get() = index.toLong()

    data class Entry(
        override val index: Int,
        /** `yyyy-MM-dd`, kept as written; only the day separator ever needs it. */
        val date: String,
        /** `HH:mm:ss.SSS`. The date is redundant on every row and moves to the separator. */
        val time: String,
        val uid: Int,
        val pid: Int,
        val tid: Int,
        val level: LogLevel,
        val tag: String,
        val message: String,
        /**
         * The rest of a multi-line message: every line of it after the first, which a writer emits
         * in one `writev` and so leaves without a prefix. Often a stack trace, often not. Empty for
         * formats where every physical line is its own entry.
         */
        val continuation: List<String> = emptyList(),
        /** Set when the line exceeded [MAX_LINE_BYTES] and was cut. */
        val truncated: Boolean = false,
    ) : LogRow

    /** A rotation banner, a watchdog line, or anything the scanner could not read. */
    data class Marker(override val index: Int, val text: String) : LogRow

    /** Synthetic: introduces the first entry of a calendar day. */
    data class DayBreak(override val index: Int, val date: String) : LogRow {
        override val key: Long
            get() = -(index.toLong() + 1)
    }
}

/**
 * Cut point for a single line, counted in bytes because that is what a byte-offset reader has: the
 * line is cut before it is decoded, from the byte offsets the index recorded.
 *
 * The longest line observed in a real device log is a little over 800 characters (an attestation
 * dump), so this only ever bites on pathological output — but without it one runaway line sets the
 * horizontal extent for the entire list and makes panning useless.
 */
const val MAX_LINE_BYTES = 4096

/**
 * Where every line of a file starts, plus the end sentinel.
 *
 * `bounds` has `lineCount + 1` entries; line `k` is the bytes in `[bounds[k], bounds[k + 1])`.
 * [droppedLeading] is how many lines fell off the front of an over-long file, and exists so the
 * header can say so rather than quietly misreport the file's length. A reader that holds its data
 * in memory rather than on disk can still satisfy this contract with a trivial `0..lineCount` map.
 */
class LogIndex(val bounds: LongArray, val droppedLeading: Int) {
    val lineCount: Int
        get() = bounds.size - 1
}

/** What [LogContent.scan] found: the filtered line numbers, and what the file contains. */
class LogScanResult(val matches: IntArray?, val facets: LogFacets)

/** The tags and levels actually present, with counts, so the filter sheet cannot go stale. */
data class LogFacets(
    val tags: List<Pair<String, Int>> = emptyList(),
    val levels: Map<LogLevel, Int> = emptyMap(),
)

/** Everything that narrows the view. All of it is applied in one pass over the file. */
data class LogQuery(
    val levels: Set<LogLevel> = emptySet(),
    val tag: String? = null,
    val text: String = "",
) {
    val isActive: Boolean
        get() = levels.isNotEmpty() || tag != null || text.isNotBlank()

    fun matches(row: LogRow): Boolean =
        when (row) {
            is LogRow.Entry ->
                (levels.isEmpty() || row.level in levels) &&
                    (tag == null || row.tag == tag) &&
                    (text.isBlank() ||
                        row.message.contains(text, ignoreCase = true) ||
                        row.tag.contains(text, ignoreCase = true))
            // A rotation banner has neither level nor tag, so it survives only a plain text
            // search. It marks where a writer restarted, which is worth keeping when it can be.
            is LogRow.Marker ->
                levels.isEmpty() &&
                    tag == null &&
                    (text.isBlank() || row.text.contains(text, ignoreCase = true))
            is LogRow.DayBreak -> false
        }
}
