package org.matrix.vector.manager.data.log

import org.matrix.vector.ui.logs.CrashFrame
import org.matrix.vector.ui.logs.CrashSection
import org.matrix.vector.ui.logs.parseStackTrace

/**
 * A recorded crash, in the shape the screens ask questions of.
 *
 * The file [CrashRecorder] writes is the record; this is that record read back. Parsing it here
 * rather than rendering the text means the UI can answer "what threw", "where", and "is this frame
 * ours" without a reader having to find those things in a wall of monospace — and it means the one
 * frame that names our own code can be pulled to the front of a summary, which is the single fact a
 * bug report is usually missing.
 *
 * Parsing never decides what is kept. A line the parser does not recognise contributes no frame and
 * nothing more; the file on disk is untouched, [CrashRecorder.read] still returns every byte of it,
 * and the copy action on the trace screen reads from there rather than from anything here. A trace
 * is evidence, and failing to understand it is not a reason to be unable to hand it over.
 */
data class CrashReport(
    /** The recorded timestamp, in the fixed format the file was written with. */
    val at: String,
    /**
     * The thread that threw, or empty for a record written before the header carried one — the
     * cache outlives an update, so the first run after one reads the old shape.
     */
    val thread: String,
    /** Build, host and platform, as one line. Restated from "What is running" on that screen. */
    val build: String,
    /** The throwable, then what caused it, in the order `printStackTrace` prints them. */
    val sections: List<CrashSection>,
) {
    /**
     * The innermost cause, which is the thing that actually went wrong.
     *
     * `RuntimeException: Unable to start activity` is the platform restating where it noticed; the
     * end of the chain is the sentence worth putting in a summary.
     */
    val root: CrashSection?
        get() = sections.lastOrNull()

    /**
     * The first frame in code we ship, anywhere in the chain.
     *
     * A crash inside `ActivityThread` is not a report anyone can act on until it says which of our
     * frames led there, and that frame is rarely near the top — the platform's own frames sit above
     * it. Null when nothing in the trace is ours, which happens and is itself worth seeing.
     */
    val ours: CrashFrame?
        get() = sections.firstNotNullOfOrNull { section -> section.frames.firstOrNull { it.ours } }
}

/**
 * Reads back a record written by [CrashRecorder].
 *
 * The two header lines are ours; everything after them is a stack trace, handed to
 * [parseStackTrace].
 *
 * Returns null only when there is no header to read, never on a trace it cannot make sense of.
 */
fun parseCrashReport(record: String): CrashReport? {
    val lines = record.trimEnd().lines()
    if (lines.size < 2) return null
    val (at, thread) =
        lines[0].split(" · thread ", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    return CrashReport(
        at = at,
        thread = thread,
        build = lines[1],
        sections = parseStackTrace(lines.drop(2)),
    )
}
