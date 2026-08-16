package org.matrix.vector.ui.logs

import java.io.Closeable

/**
 * A random-access, windowed reader over one log stream, in the host's own format.
 *
 * The shared view model never holds a whole log: it asks the reader to index line offsets, then to
 * materialise at most a window's worth of rows around wherever the reader is looking, and to build a
 * filter in one streaming pass. A log of any size therefore opens at the same speed and the pane
 * never holds the file.
 *
 * Vector satisfies this over a seekable descriptor the daemon hands it, decoding the daemon's own
 * framing without ever allocating a `String` per line. LSPatch satisfies it over a rotated
 * `threadtime` part read whole into memory (or a one-shot logcat snapshot before the collector has
 * written a part), where the "window" is the whole part. Both are [Closeable] so the
 * descriptor-backed one can release its fd; the in-memory one closes to nothing.
 */
interface LogContent : Closeable {

    /** Pass one: where every line starts, plus how many fell off the front of an over-long file. */
    suspend fun index(): LogIndex

    /**
     * Pass two: turn an ascending, absolute selection of line numbers into rows.
     *
     * The unfiltered case passes a contiguous run; a filtered case passes the sparse set of
     * matching line numbers. Either way the rows returned carry their absolute [LogRow.index] so
     * the lazy list can key on it, and day breaks are inserted where the calendar day changes.
     */
    suspend fun readRows(index: LogIndex, lines: IntArray): List<LogRow>

    /**
     * Walks a window's start back to the entry that owns it, so a boundary landing inside a
     * multi-line message opens the page on the whole message rather than on its tail. A format
     * where every physical line is its own entry returns [line] unchanged.
     */
    fun entryStart(index: LogIndex, line: Int): Int

    /** Builds the filter and the facets in one streaming pass, reporting a real fraction of bytes. */
    suspend fun scan(index: LogIndex, query: LogQuery, onProgress: (Float) -> Unit): LogScanResult
}
