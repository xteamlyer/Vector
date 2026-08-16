package org.matrix.vector.ui.logs

/** One throwable in the chain: what it was, what it said, and where it had been. */
data class CrashSection(
    /** The fully qualified type, e.g. `java.net.UnknownHostException`. */
    val type: String,
    val message: String?,
    val frames: List<CrashFrame>,
    /** The `... N more` count, which stands for frames identical to the ones already printed. */
    val elided: Int,
    /** False for the throwable that reached the handler, true for everything under `Caused by:`. */
    val isCause: Boolean,
) {
    /** The type without its package, which is what a heading has room for. */
    val simpleType: String
        get() = type.substringAfterLast('.')

    /**
     * What the heading shows: the simple name when it carries the identity, but the whole type when
     * dropping the package would leave nothing to recognise — an obfuscated type whose last segment is
     * a bare token is named by its package as much as by its class, so it is shown in full rather than
     * reduced to that token.
     */
    val displayType: String
        get() =
            if (type.endsWith("Exception") || type.endsWith("Error") || type.endsWith("Throwable")) {
                simpleType
            } else {
                type
            }
}

/** One `at ...` line, split at the point where it stops being a name and starts being a place. */
data class CrashFrame(
    /** `org.matrix.vector.manager.ui.MainActivity.onCreate` */
    val method: String,
    /** `MainActivity.kt:39`, or null for a native frame, which prints no source. */
    val location: String?,
    /** Whether the class belongs to something in this repository rather than to the platform. */
    val ours: Boolean,
) {
    /** `MainActivity.onCreate` — the part a reader recognises, without the package. */
    val shortMethod: String
        get() {
            val method = this.method.substringAfterLast('.', "")
            val type = this.method.substringBeforeLast('.').substringAfterLast('.')
            return if (method.isEmpty() || type.isEmpty()) this.method else "$type.$method"
        }

    /** The line as it was written, for copying a single frame. */
    val line: String
        get() = if (location == null) "at $method" else "at $method($location)"
}

/**
 * The packages this project ships, by prefix.
 *
 * Used only to decide emphasis, so being wrong costs a frame its highlight and nothing else. The
 * legacy Xposed prefixes are here because a module's crash goes through them and a reader chasing
 * one wants those frames to stand out for the same reason they want ours to.
 */
private val OUR_PACKAGES =
    listOf(
        "org.matrix.vector",
        "org.lsposed",
        "de.robv.android.xposed",
        "io.github.libxposed",
    )

private val FRAME = Regex("""^\s*at (.+?)(?:\(([^)]*)\))?$""")
private val ELIDED = Regex("""^\s*\.\.\. (\d+) more$""")
private const val CAUSED_BY = "Caused by: "
private const val SUPPRESSED = "Suppressed: "

/**
 * `Throwable.printStackTrace` output, as the chain of throwables it describes.
 *
 * The shape is fixed by the JDK: a header line naming the throwable, tab-indented `at` lines, an
 * optional `... N more`, and the same again after `Caused by:`. Suppressed exceptions print under
 * `Suppressed:` and are treated as another link, since for reading purposes they are one.
 *
 * Total by construction. A line it does not recognise contributes nothing and ends nothing; text
 * that is not a trace at all yields an empty list, which is how a caller asks "is there a trace
 * here" without a second parser to decide it first.
 */
fun parseStackTrace(trace: String): List<CrashSection> = parseStackTrace(trace.trimEnd().lines())

/**
 * The same, for a caller that already holds the lines.
 *
 * The log panel does: an entry's continuation lines *are* the trace, so joining them into a string
 * for this to split again would be work done twice on every visible row.
 */
fun parseStackTrace(lines: List<String>): List<CrashSection> {
    val sections = mutableListOf<CrashSection>()

    var type: String? = null
    var message: String? = null
    var isCause = false
    var open = false
    var frames = mutableListOf<CrashFrame>()
    var elided = 0

    fun flush() {
        if (!open) return
        sections += CrashSection(type.orEmpty(), message, frames.toList(), elided, isCause)
        frames = mutableListOf()
        elided = 0
        open = false
        type = null
        message = null
        isCause = false
    }

    for (line in lines) {
        val frame = FRAME.matchEntire(line)
        val skipped = ELIDED.matchEntire(line)
        when {
            frame != null -> {
                // A frame may arrive before any header, and does whenever the text handed here is
                // only the *continuation* of a log entry: `XposedBridge.log(Throwable)` writes the
                // whole trace as one message, so the header lands on the entry's own line and the
                // frames land under it. Such a trace opens an untyped section.
                open = true
                val method = frame.groupValues[1]
                val location = frame.groupValues[2].takeIf { it.isNotEmpty() }
                frames += CrashFrame(method, location, OUR_PACKAGES.any(method::startsWith))
            }
            skipped != null -> {
                open = true
                elided = skipped.groupValues[1].toIntOrNull() ?: 0
            }
            line.isBlank() -> Unit
            else -> {
                // A header: the throwable itself, or one introduced by Caused by:/Suppressed:.
                flush()
                open = true
                isCause = line.startsWith(CAUSED_BY) || line.startsWith(SUPPRESSED)
                val header = line.removePrefix(CAUSED_BY).removePrefix(SUPPRESSED).trim()
                // "type: message", where the type never contains a space and the message may.
                val split = header.indexOf(": ")
                type = if (split < 0) header else header.substring(0, split)
                message = if (split < 0) null else header.substring(split + 2)
            }
        }
    }
    flush()
    return sections
}

/**
 * A line that introduces a throwable, written flush left by `printStackTrace`.
 *
 * Either a labelled link in the chain, or a bare header: a dotted type name with no spaces in it,
 * which either ends in something that reads as a throwable, or — when obfuscation has reduced the name
 * to a bare token that no longer does — carries a `: ` message, the shape a thrown and printed
 * throwable has and a plain dotted identifier logged on its own does not. Kept narrow on the type's
 * shape, because callers use it to decide whether a line belongs to a trace at all — "store:
 * refreshing failed" is rejected for having no dot in its type.
 */
fun isThrowableHeader(text: String): Boolean {
    if (text.startsWith(CAUSED_BY) || text.startsWith(SUPPRESSED)) return true
    val type = text.substringBefore(": ")
    if (!type.contains('.') || type.any { it.isWhitespace() }) return false
    return type.endsWith("Exception") ||
        type.endsWith("Error") ||
        type.endsWith("Throwable") ||
        type != text
}
