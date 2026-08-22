package org.matrix.vector.ui.logs

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

/** Whether a host's destructive reset opens a fresh part or empties the buffer, or has neither. */
enum class LogResetKind {
    /** A non-destructive rotation: a new part opens and the old one stays reachable on disk. */
    ROTATE,

    /** A destructive clear: the log buffer is emptied. */
    CLEAR,
}

/**
 * Everything the shared Logs screen needs from its host, so the screen and its view model can be
 * identical across two very different backends.
 *
 * A host keeps two streams — the primary one (Vector's module log, LSPatch's framework-filtered
 * logcat) and the verbose one (Vector's full daemon log, LSPatch's unfiltered logcat) — selected by
 * the `verbose` flag throughout. Each opens as a windowed [LogContent] reader; the screen's paging,
 * filtering and rendering are written against that alone.
 *
 * The capability flags let one screen serve both hosts honestly: a host advertises only what it can
 * actually do, and the screen hides the controls for the rest rather than offering a button that
 * fails. Vector can configure verbose logging; LSPatch cannot. Both rotate a part (Vector's daemon
 * and LSPatch's shell collector each open a fresh part without deleting the old one) and both save a
 * zip bug-report archive.
 */
interface LogSource {

    /** The rotated parts of a stream, oldest first; the last is the live one. Empty when a host keeps no parts. */
    suspend fun parts(verbose: Boolean): List<String>

    /**
     * A name for the process behind a uid, or null to leave it as the number.
     *
     * The log records who wrote a line as a uid, which is not something a reader recognises. Asked only of the uids the
     * filter offers, so a host may resolve it however it likes -- LSPatch reads its package list -- without that cost
     * falling on the scan.
     */
    fun writerLabel(uid: Int): String? = null

    /**
     * Opens a stream for reading. [part] names a rotated part, or null for the live tail.
     *
     * The three outcomes map to three screen states: a failed [Result] is the source being
     * unreachable (Vector: daemon down; LSPatch: Shizuku unavailable); a success carrying null is
     * reachable-but-no-file (Vector's daemon has opened none yet); a success carrying a reader is
     * the normal case, whose emptiness is then the reader's own `lineCount == 0`.
     */
    suspend fun open(verbose: Boolean, part: String?): Result<LogContent?>

    /**
     * Whether the host actually has a second, verbose stream to unfold into.
     *
     * A host can be reduced to a single log — one that reads only its own process because the
     * privileged backend it normally reads the device through is unavailable — and then the two
     * streams would be the same lines under two names. Saying so drops the unfold control instead of
     * offering a choice that changes nothing.
     *
     * Read from composition on every frame that matters, because a backend can come and go while the screen is open and
     * the control has to follow it. A host whose answer can change must therefore back it with snapshot state; one
     * computing it from a plain field would keep the control on screen after the stream behind it was gone.
     */
    val hasVerboseStream: Boolean
        get() = true

    // --- Verbose-logging preference (distinct from which stream is on screen) ------------------

    /** Whether the host has a persistent "write verbose lines at all" preference to toggle. */
    val canConfigureVerbose: Boolean

    /** The preference's current value, read back from the host after any write. */
    suspend fun isVerboseEnabled(): Boolean

    /** Writes the preference and returns the value the host reports afterwards. */
    suspend fun setVerboseEnabled(enabled: Boolean): Boolean

    // --- Export --------------------------------------------------------------------------------

    val canSaveArchive: Boolean

    /** The MIME type the save dialog creates the document as — a zip bug-report archive for both hosts. */
    val archiveMimeType: String

    /** The default file name offered when saving. */
    fun archiveName(): String

    /** Writes the export into [uri]. [verbose] is the stream on screen, for a host that exports it. */
    suspend fun saveArchive(uri: Uri, verbose: Boolean): Result<Unit>

    // --- Reset ---------------------------------------------------------------------------------

    /** Which reset the host offers, or null for none. Chooses the settings-sheet row and its copy. */
    val resetKind: LogResetKind?

    /** Performs the reset on the given stream; returns whether the host took the request. */
    suspend fun reset(verbose: Boolean): Boolean

    // --- Reading preferences -------------------------------------------------------------------

    val wordWrap: StateFlow<Boolean>
    val tracesInline: StateFlow<Boolean>

    fun setWordWrap(enabled: Boolean)

    fun setTracesInline(inline: Boolean)
}
