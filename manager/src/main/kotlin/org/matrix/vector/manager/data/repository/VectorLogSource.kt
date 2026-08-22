package org.matrix.vector.manager.data.repository

import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.flow.StateFlow
import org.matrix.vector.manager.data.log.LogFile
import org.matrix.vector.manager.data.log.logArchiveName
import org.matrix.vector.manager.di.ServiceLocator
import org.matrix.vector.ui.logs.LogContent
import org.matrix.vector.ui.logs.LogResetKind
import org.matrix.vector.ui.logs.LogSource
import org.matrix.vector.ui.logs.WriterLabeler

/**
 * Vector's daemon-backed implementation of the shared Logs screen's [LogSource].
 *
 * Every stream is a real file the daemon hands over as a seekable descriptor, wrapped in a
 * [LogFile] — the byte-offset windowed reader that keeps the pane from ever holding the log. The
 * two streams are the daemon's module log and its verbose log; the rotated parts, the persistent
 * verbose-logging preference, the bug-report export and the part rotation are all things the daemon
 * offers, so every capability here is on.
 */
class VectorLogSource : LogSource {

    private val daemon = ServiceLocator.daemon
    private val settings = ServiceLocator.settings

    private val writers = WriterLabeler(ServiceLocator.context.packageManager)

    override fun writerLabel(uid: Int): String? = writers.label(uid)

    override suspend fun parts(verbose: Boolean): List<String> =
        daemon.getLogParts(verbose).getOrDefault(emptyList())

    override suspend fun open(verbose: Boolean, part: String?): Result<LogContent?> {
        // The old descriptor points at an inode, not at "the current log", so the live tail is
        // re-asked for by name rather than re-indexed; a pinned part is fetched by its name.
        val result =
            if (part == null) daemon.getLiveLogPart(verbose) else daemon.getLogPart(verbose, part)
        return result.map { pfd -> pfd?.let { LogFile(it) } }
    }

    override val canConfigureVerbose: Boolean = true

    override suspend fun isVerboseEnabled(): Boolean =
        daemon.isVerboseLogEnabled().getOrDefault(false)

    override suspend fun setVerboseEnabled(enabled: Boolean): Boolean {
        daemon.setVerboseLogEnabled(enabled)
        // Read back what the daemon reports: an older one OR'd the preference with its own build
        // type and would refuse to move, which the screen then explains rather than hides.
        return daemon.isVerboseLogEnabled().getOrDefault(enabled)
    }

    override val canSaveArchive: Boolean = true
    override val archiveMimeType: String = "application/zip"

    override fun archiveName(): String = logArchiveName("zip")

    /**
     * Writes the daemon's bug report — far more than the logs: `FileSystem.getLogs` walks
     * tombstones and ANRs, shells out to `logcat -b all -d` and `dmesg`, sweeps the modules folder
     * and deflates the lot. Seconds, synchronous, and off the main thread by the caller's dispatch.
     *
     * Each side owns its copy of the descriptor and closes it: `use` closes this one, and the
     * daemon closes the copy it received. The [verbose] flag is ignored — the report is whole.
     */
    override suspend fun saveArchive(uri: Uri, verbose: Boolean): Result<Unit> =
        runCatching {
            ServiceLocator.context.contentResolver.openFileDescriptor(uri, "wt").use { fd ->
                if (fd == null) throw IOException("could not open the document to write")
                daemon.writeBugReportTo(fd).getOrThrow()
            }
        }

    override val resetKind: LogResetKind = LogResetKind.ROTATE

    override suspend fun reset(verbose: Boolean): Boolean =
        // The daemon's startNewLogPart() opens a fresh part and leaves the closed one on disk under
        // a ten-part LRU; nothing is truncated. It answers only that the request was taken.
        daemon.startNewLogPart(verbose).isSuccess

    override val wordWrap: StateFlow<Boolean> = settings.logWordWrap
    override val tracesInline: StateFlow<Boolean> = settings.logTracesInline

    override fun setWordWrap(enabled: Boolean) = settings.setLogWordWrap(enabled)

    override fun setTracesInline(inline: Boolean) = settings.setLogTracesInline(inline)
}
