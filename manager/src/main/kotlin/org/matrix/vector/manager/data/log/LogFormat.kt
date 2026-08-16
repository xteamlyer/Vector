package org.matrix.vector.manager.data.log

import org.matrix.vector.ui.logs.LogLevel
import org.matrix.vector.ui.logs.LogRow

/**
 * The daemon's own log framing, and the scanner that recovers it.
 *
 * The row types this produces — [LogRow], [LogLevel] — are shared with LSPatch through the manager
 * UI library, but the *parsing* is Vector's alone: the authority for this format is not a sample of
 * the output, it is the writer, `daemon/src/main/jni/logcat.cpp`, which emits every entry as a
 * single `writev` of
 *
 * ```
 * "[ " %Y-%m-%dT%H:%M:%S ".%03ld %8d:%6d:%6d %c/%-15.*s ] " <message> "\n"
 * ```
 *
 * Three properties of that format decide how this parser is written:
 *
 * 1. **The widths are `printf` minimums, not columns.** A uid of `1010324` is seven digits and a
 *    future pid can exceed six, so the prefix has to be *scanned*. Slicing at constant offsets
 *    works right up until the day it silently does not.
 * 2. **A message containing newlines is still one `writev`.** Its continuation lines therefore
 *    carry no prefix at all, and *nothing* marks them as continuations — a stack trace's frames
 *    happen to be indented, but a `Caused by:` and `zygisk-core64`'s mount-argument report are
 *    flush left. They belong to the entry above them, not to nothing. See [isContinuationLine].
 *    The exception is a message past the logger's payload, which the *writer* splits into several
 *    entries before liblog ever sees it; [isSplitChunk] puts those back together.
 * 3. **Not every line is an entry.** `----part N start----` / `-----part N end----` mark the
 *    daemon rotating to a fresh file, and the watchdog writes its own banners. Those are real
 *    information about the log rather than noise, so they survive as [LogRow.Marker] instead of
 *    being dropped. They are also the *only* unprefixed lines that are not continuations, which
 *    is what makes rule 2 decidable.
 *
 * Anything the scanner cannot make sense of degrades to a marker carrying the raw text. A log
 * viewer that hides a line it failed to understand is worse than useless during a diagnosis.
 */

/** The three-character delimiter that ends the prefix. See [parseLogLine]. */
private const val DELIMITER = " ] "

/** `"[ "` plus the 23-character timestamp; below this the fixed-position checks run off the end. */
private const val MIN_PREFIX = 26

/**
 * Whether a line belongs to the entry above it rather than standing on its own.
 *
 * The answer follows from the writer, not from the look of the line. `logcat.cpp` emits an entry as
 * a single `writev` whose first `iovec` is the prefix, so **every** line of a multi-line message
 * after the first arrives without one. An unprefixed line under an entry is therefore a
 * continuation by construction, whatever it looks like.
 *
 * So the rule is inverted: a line is a continuation unless it is one of the four things the daemon
 * writes raw, which [isRawBanner] names.
 *
 * **Two things the caller must have established first**, neither of which this can see:
 * 1. That [text] is not itself an entry. This answers only "does this *unprefixed* line belong to
 *    the entry above", and a prefixed line is not unprefixed. [parseLogLine] is the check.
 * 2. That there is an entry above at all. Before the first one of a file, and after a banner, there
 *    is nothing to continue.
 */
fun isContinuationLine(text: String): Boolean = !isRawBanner(text)

/**
 * Whether [row] is the tail of a message the writer had to cut in two, rather than an entry.
 *
 * Rule 2 holds only up to a point. `android.util.Log.e(tag, msg, tr)` does not hand liblog a
 * message longer than the logger's payload — `printlns` in AOSP's `Log.java` walks the string and
 * emits it as *several* entries, breaking at the last newline that fits. Each of those is a real
 * entry with its own prefix, written by its own `writev`, and logd stores them separately.
 *
 * So this is not a guess at structure — it is the *undo* of a split the writer did not choose. It
 * is kept narrow to earn that: same tag, same process, same thread, same level, immediately
 * adjacent, and a message that reads as a tail rather than a beginning.
 *
 * Being wrong costs a divider between two rows that stay legible either way; nothing is discarded.
 */
fun isSplitChunk(previous: LogRow.Entry, row: LogRow.Entry): Boolean =
    previous.tag == row.tag &&
        previous.pid == row.pid &&
        previous.tid == row.tid &&
        previous.level == row.level &&
        looksLikeSplitChunk(row.message)

/**
 * Whether a message opens the way a continuation does rather than the way a message does.
 *
 * `printlns` breaks on a newline, so a tail begins with whatever line followed the break: inside a
 * stack trace that is an indented frame, or a `Caused by:`/`Suppressed:` written flush left. A
 * writer starting its *own* message with whitespace is close enough to unheard of to be worth
 * trading against joining the traces this exists for.
 */
fun looksLikeSplitChunk(message: String): Boolean =
    message.isNotEmpty() &&
        (message[0].isWhitespace() ||
            message.startsWith("Caused by: ") ||
            message.startsWith("Suppressed: "))

/**
 * The lines the daemon writes to the file itself, outside any entry.
 *
 * `Logcat::LogRaw` has exactly two callers and the rotation code exactly two more, so this list is
 * closed and can be enumerated rather than guessed at.
 */
private fun isRawBanner(text: String): Boolean =
    text.isEmpty() ||
        PART_BANNER.matches(text) ||
        text.startsWith("Logd crashed too many times") ||
        text.startsWith("Logd maybe crashed (err=")

private val PART_BANNER = Regex("""-{4,}part \d+ (start|end)-{4,}""")

/** Parses one raw line, degrading to [LogRow.Marker] rather than failing. */
fun parseLogLine(index: Int, text: String, truncated: Boolean = false): LogRow =
    parseEntry(index, text, truncated) ?: LogRow.Marker(index, text)

private fun parseEntry(index: Int, line: String, truncated: Boolean): LogRow.Entry? {
    val n = line.length
    if (n < MIN_PREFIX || line[0] != '[' || line[1] != ' ') return null

    // The timestamp is fixed-width, so it is the one part worth checking by position: cheap
    // separators to reject in six comparisons before any digit scanning happens.
    if (
        line[6] != '-' ||
            line[9] != '-' ||
            line[12] != 'T' ||
            line[15] != ':' ||
            line[18] != ':' ||
            line[21] != '.'
    )
        return null

    var i = 25 // "[ " + 23 characters of timestamp

    val uidField = readInt(line, skipSpaces(line, i))
    if (uidField == NO_INT) return null
    i = endOf(uidField)
    if (i >= n || line[i] != ':') return null

    val pidField = readInt(line, skipSpaces(line, i + 1))
    if (pidField == NO_INT) return null
    i = endOf(pidField)
    if (i >= n || line[i] != ':') return null

    val tidField = readInt(line, skipSpaces(line, i + 1))
    if (tidField == NO_INT) return null
    i = endOf(tidField)

    if (i + 2 >= n || line[i] != ' ' || line[i + 2] != '/') return null
    val level = LogLevel.of(line[i + 1])
    val tagStart = i + 3

    // The delimiter is the three-character sequence, not a bare ']'. A message that contains a
    // bracket — "[TX_ID: 773] Intercept…" — has no space before its ']', and the tag is padded
    // with spaces to fifteen columns, so the first " ] " is always the real end of the prefix.
    val delimiter = line.indexOf(DELIMITER, tagStart)
    if (delimiter < 0) return null

    return LogRow.Entry(
        index = index,
        date = line.substring(2, 12),
        time = line.substring(13, 25),
        uid = valueOf(uidField),
        pid = valueOf(pidField),
        tid = valueOf(tidField),
        level = level,
        tag = line.substring(tagStart, delimiter).trimEnd(),
        message = line.substring(delimiter + DELIMITER.length),
        truncated = truncated,
    )
}

private fun skipSpaces(s: String, from: Int): Int {
    var i = from
    while (i < s.length && s[i] == ' ') i++
    return i
}

/**
 * [readInt] has to return both the value and where it stopped.
 *
 * The two are packed into one `Long` rather than returned as a `Pair`, because a `Pair` would
 * allocate three times per parsed line — and a window of a full 4 MB log part is thirty thousand
 * lines, re-parsed every time the window moves.
 */
private const val NO_INT = -1L

private fun valueOf(field: Long): Int = (field ushr 32).toInt()

private fun endOf(field: Long): Int = (field and 0xFFFFFFFFL).toInt()

/** Reads an unsigned decimal, refusing anything long enough to overflow. */
private fun readInt(s: String, from: Int): Long {
    var i = from
    var value = 0L
    while (i < s.length && s[i] in '0'..'9') {
        value = value * 10 + (s[i] - '0')
        if (value > Int.MAX_VALUE) return NO_INT
        i++
    }
    if (i == from) return NO_INT
    return (value shl 32) or i.toLong()
}
