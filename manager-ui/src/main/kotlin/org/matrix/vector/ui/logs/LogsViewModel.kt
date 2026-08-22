package org.matrix.vector.ui.logs

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "LogsViewModel"

/** The two log streams a host keeps. They are read independently and never both at once. */
enum class LogTab {
    MODULES,
    VERBOSE,
}

/**
 * What a pane is showing.
 *
 * Each of these is a genuinely different situation and the screen renders each as its own thing, so
 * "the source is unreachable" never arrives looking like a line the source wrote.
 */
sealed interface LogStatus {
    /** Opening the descriptor and indexing. Short enough that no progress is worth reporting. */
    data object Loading : LogStatus

    /** Filtering, which reads the whole file and therefore reports a real fraction. */
    data class Scanning(val progress: Float) : LogStatus

    data object Ready : LogStatus

    /** The source is not reachable. Nothing about the log is known. */
    data object DaemonUnavailable : LogStatus

    /** The source answered, and has not opened a log file yet. */
    data object NoLogFile : LogStatus

    /** The file exists and is empty — for the modules log, the normal state of a quiet system. */
    data object Empty : LogStatus

    /** The file has content; the current filter excludes all of it. */
    data object NoMatches : LogStatus

    data class ReadFailed(val message: String?) : LogStatus
}

/** A one-shot instruction to move the list, delivered as state so it survives recomposition. */
data class ScrollCommand(val token: Long, val position: Int)

data class LogPaneState(
    val status: LogStatus = LogStatus.Loading,
    /** At most [LogsViewModel.WINDOW] lines' worth of rows, never the file. */
    val rows: List<LogRow> = emptyList(),
    val totalLines: Int = 0,
    /** Lines the current filter admits; equals [totalLines] when nothing is filtered. */
    val visibleLines: Int = 0,
    val windowFirst: Int = 0,
    val windowLast: Int = 0,
    /**
     * The lines actually on screen, as positions within the current view.
     *
     * Distinct from the loaded window: the reader sees a screenful, the window is a few thousand
     * lines around it. The line counter reports this pair, because "which lines am I looking at" is
     * the question it is read to answer; the window's bounds answer a question about paging.
     */
    val visibleFirst: Int = 0,
    val visibleLast: Int = 0,
    val droppedLeading: Int = 0,
    val query: LogQuery = LogQuery(),
    val facets: LogFacets? = null,
    /** A name for each writer the facets offer, resolved with them and off the main thread. */
    val writerNames: Map<Int, String> = emptyMap(),
    val refreshing: Boolean = false,
    val scroll: ScrollCommand? = null,
    /** Rotated parts the host holds, oldest first. The last one is the live file. */
    val parts: List<String> = emptyList(),
    /** Which of [parts] is on screen. A load with no part pinned selects the last, the live one. */
    val partIndex: Int = 0,
) {
    val filtered: Boolean
        get() = query.isActive

    /**
     * Whether the reader is actually looking at the newest line -- the viewport, not the window.
     *
     * The live tail follows only while this is true, and the comment on it promises it "goes false
     * the instant they scroll up to read history". That only holds when it tracks what is *on
     * screen*: the loaded window is a few thousand lines around the viewport, so keying off
     * [windowLast] left it true while the reader scrolled up *within* that window, and the tail
     * yanked them back to the bottom on the next poll. [visibleLast] is the last line actually
     * visible, so scrolling up even a little drops it below [visibleLines] and pauses the follow.
     */
    val atNewest: Boolean
        get() = visibleLines == 0 || visibleLast >= visibleLines
}

/** Progress of the export, which is slow enough that the UI must say so. */
sealed interface LogSaveState {
    data object Idle : LogSaveState

    data object Saving : LogSaveState

    data class Saved(val uri: Uri) : LogSaveState

    data class Failed(val message: String?) : LogSaveState
}

/**
 * One state machine per log stream, over a windowed reader.
 *
 * The `StateFlow` only ever carries a window of rows; nothing here holds the file. Every read runs
 * on `Dispatchers.IO` behind the pane's own mutex, so a scroll that extends the window cannot race
 * the refresh that replaced the file under it. Both hosts' [LogSource] implementations keep their
 * own transport off the main thread.
 */
class LogsViewModel(private val source: LogSource) : ViewModel() {

    private class Pane {
        var content: LogContent? = null
        var index: LogIndex? = null

        /** Line numbers the filter admits, or `null` when nothing is filtered. */
        var matches: IntArray? = null

        var first = 0
        var last = 0
        var opened = false

        /** The part being read, or null for the live one. */
        var part: String? = null
        val mutex = Mutex()
        var loadJob: Job? = null
        var scanJob: Job? = null
        var pageJob: Job? = null
        val state = MutableStateFlow(LogPaneState())

        /** Lines addressable in the current view: filtered count, or the whole file. */
        fun viewCount(): Int = matches?.size ?: (index?.lineCount ?: 0)
    }

    private val panes = LogTab.entries.associateWith { Pane() }

    private var scrollToken = 0L

    private val _saveState = MutableStateFlow<LogSaveState>(LogSaveState.Idle)
    val saveState: StateFlow<LogSaveState> = _saveState.asStateFlow()

    private val _verboseEnabled = MutableStateFlow(false)
    val verboseEnabled: StateFlow<Boolean> = _verboseEnabled.asStateFlow()

    /**
     * True when the user asked for verbose logging off and the host kept it on.
     *
     * A host that reports the stored preference unmodified keeps this false. One that OR's the
     * preference with its own build type would snap the switch straight back; rather than let a
     * control refuse to move with no explanation, the screen reads the value the host reports
     * *after* the write and says who is overriding whom.
     */
    private val _verboseEnforced = MutableStateFlow(false)
    val verboseEnforced: StateFlow<Boolean> = _verboseEnforced.asStateFlow()

    val wordWrap: StateFlow<Boolean> = source.wordWrap

    val tracesInline: StateFlow<Boolean> = source.tracesInline

    /** Whether the host offers a persistent verbose-logging preference to toggle. */
    val canConfigureVerbose: Boolean = source.canConfigureVerbose

    /** Whether the host can export the log, and how the save document should be created. */
    val canSaveArchive: Boolean = source.canSaveArchive
    val archiveMimeType: String = source.archiveMimeType

    fun archiveName(): String = source.archiveName()

    /** Which reset the host offers, if any. */
    val resetKind: LogResetKind? = source.resetKind

    init {
        if (source.canConfigureVerbose) {
            viewModelScope.launch { _verboseEnabled.value = source.isVerboseEnabled() }
        }
    }

    fun state(tab: LogTab): StateFlow<LogPaneState> = panes.getValue(tab).state

    fun setWordWrap(enabled: Boolean) = source.setWordWrap(enabled)

    fun setTracesInline(inline: Boolean) = source.setTracesInline(inline)

    /** Called when a stream comes on screen. Only the stream on screen is ever read. */
    fun open(tab: LogTab) {
        val pane = panes.getValue(tab)
        if (pane.opened) return
        pane.opened = true
        reload(tab, jumpTo = Jump.NEWEST)
    }

    /**
     * Moves to another rotated part.
     *
     * Selecting the newest clears the pin rather than naming it, so the pane goes back to following
     * the live descriptor — the one the host keeps appending to — instead of a fixed inode that
     * stops growing the moment the log rotates.
     */
    fun selectPart(tab: LogTab, index: Int) {
        val pane = panes.getValue(tab)
        val parts = pane.state.value.parts
        if (parts.isEmpty()) return
        val target = index.coerceIn(0, parts.lastIndex)
        pane.part = if (target == parts.lastIndex) null else parts[target]
        pane.state.update { it.copy(partIndex = target) }
        reload(tab, jumpTo = if (target == parts.lastIndex) Jump.NEWEST else Jump.OLDEST)
    }

    fun refresh(tab: LogTab) {
        val pane = panes.getValue(tab)
        pane.opened = true
        // Keep the reader where it was unless it was already following the tail, in which case
        // following it is the whole point of pressing refresh.
        reload(tab, jumpTo = if (pane.state.value.atNewest) Jump.NEWEST else Jump.KEEP)
    }

    /**
     * A quiet re-read for the live tail, driven on a timer while the reader is following the end.
     *
     * Unlike [refresh] it never raises the pull-to-refresh flag — a spinner flashing every couple of
     * seconds would be worse than the stale log it is trying to fix — and it only ever jumps to the
     * newest line, because that is the one situation it runs in. The screen calls it only when the
     * pane is [LogPaneState.atNewest] on the live part, so it cannot yank a reader who has scrolled
     * up to read history.
     */
    fun tail(tab: LogTab) {
        val pane = panes.getValue(tab)
        if (!pane.opened) return
        reload(tab, jumpTo = Jump.NEWEST, refreshingFlag = false)
    }

    private enum class Jump {
        NEWEST,
        OLDEST,
        KEEP,
    }

    private fun reload(tab: LogTab, jumpTo: Jump, refreshingFlag: Boolean = true) {
        val pane = panes.getValue(tab)
        pane.loadJob?.cancel()
        pane.loadJob =
            viewModelScope.launch(Dispatchers.IO) {
                pane.mutex.withLock {
                    pane.state.update {
                        it.copy(
                            status = if (it.rows.isEmpty()) LogStatus.Loading else it.status,
                            refreshing = refreshingFlag,
                        )
                    }
                    val keptFirst = pane.first

                    // The old reader points at an inode, not at "the current log": once the host
                    // rotates, it keeps resolving to the part that has been retired. So a refresh
                    // re-opens rather than re-indexing the one we hold.
                    pane.content?.close()
                    pane.content = null
                    pane.index = null
                    pane.matches = null

                    val verbose = tab == LogTab.VERBOSE
                    val parts = source.parts(verbose)
                    // A part that has since been rotated away stops existing; falling back to the
                    // live file beats showing an empty screen with no explanation.
                    val chosen = pane.part?.takeIf { it in parts }
                    pane.part = chosen
                    pane.state.update {
                        it.copy(
                            parts = parts,
                            partIndex =
                                if (chosen == null) (parts.size - 1).coerceAtLeast(0)
                                else parts.indexOf(chosen),
                        )
                    }

                    val content =
                        source.open(verbose, chosen).getOrElse {
                            Log.w(TAG, "logs: ${tab.name.lowercase()} log (${chosen ?: "live"}) unavailable", it)
                            pane.state.value =
                                pane.state.value.copy(
                                    status = LogStatus.DaemonUnavailable,
                                    rows = emptyList(),
                                    refreshing = false,
                                )
                            return@withLock
                        }
                    if (content == null) {
                        pane.state.value =
                            pane.state.value.copy(
                                status = LogStatus.NoLogFile,
                                rows = emptyList(),
                                refreshing = false,
                            )
                        return@withLock
                    }

                    val index =
                        try {
                            pane.content = content
                            content.index().also { pane.index = it }
                        } catch (e: Exception) {
                            runCatching { content.close() }
                            pane.content = null
                            pane.state.value =
                                pane.state.value.copy(
                                    status = LogStatus.ReadFailed(e.message),
                                    rows = emptyList(),
                                    refreshing = false,
                                )
                            return@withLock
                        }

                    pane.state.update {
                        it.copy(
                            totalLines = index.lineCount,
                            visibleLines = index.lineCount,
                            droppedLeading = index.droppedLeading,
                            refreshing = false,
                        )
                    }

                    if (index.lineCount == 0) {
                        pane.state.update {
                            it.copy(status = LogStatus.Empty, rows = emptyList(), windowLast = 0)
                        }
                        return@withLock
                    }

                    // A filter set before the refresh still applies to the file that replaced it.
                    if (pane.state.value.query.isActive) {
                        runScan(pane, jumpTo)
                    } else {
                        applyJump(pane, jumpTo, keptFirst)
                    }
                }
            }
    }

    private suspend fun applyJump(pane: Pane, jumpTo: Jump, keptFirst: Int) {
        val count = pane.viewCount()
        when (jumpTo) {
            // A log is read from the end: that is where the crash is.
            Jump.NEWEST -> loadWindow(pane, count - WINDOW, count, ScrollTo.END)
            Jump.OLDEST -> loadWindow(pane, 0, WINDOW, ScrollTo.START)
            Jump.KEEP -> loadWindow(pane, keptFirst, keptFirst + WINDOW, ScrollTo.NONE)
        }
    }

    private enum class ScrollTo {
        START,
        END,
        NONE,
    }

    /**
     * Materialises `[first, last)` of the current view.
     *
     * The window size is invariant, so extending one edge trims the other and peak memory is a
     * function of [WINDOW] alone — completely independent of how large the file turned out to be.
     */
    private suspend fun loadWindow(pane: Pane, first: Int, last: Int, scrollTo: ScrollTo) {
        val index = pane.index ?: return
        val content = pane.content ?: return
        val selection = pane.matches
        val count = selection?.size ?: index.lineCount
        if (count == 0) return

        var from = first.coerceIn(0, max(0, count - 1))
        val to = min(max(last, from + 1), count)
        from = max(0, min(from, to - 1))

        // Unfiltered, a view position *is* a line number, so the window start can be walked back
        // to the entry that owns any stack frames it landed in the middle of. Filtered, the frames
        // already travel with their entry.
        if (selection == null) from = content.entryStart(index, from)

        val lines = IntArray(to - from) { selection?.get(from + it) ?: (from + it) }
        val rows =
            try {
                content.readRows(index, lines)
            } catch (e: Exception) {
                pane.state.update { it.copy(status = LogStatus.ReadFailed(e.message)) }
                return
            }

        pane.first = from
        pane.last = to
        val command =
            when (scrollTo) {
                ScrollTo.START -> ScrollCommand(++scrollToken, 0)
                ScrollTo.END -> ScrollCommand(++scrollToken, max(0, rows.size - 1))
                ScrollTo.NONE -> null
            }
        pane.state.update {
            it.copy(
                status = LogStatus.Ready,
                rows = rows,
                windowFirst = from,
                windowLast = to,
                visibleLines = count,
                scroll = command ?: it.scroll,
            )
        }
    }

    /**
     * Extends the window as the list approaches an edge.
     *
     * The list is keyed by absolute line number, so inserting rows above the viewport re-anchors on
     * the first visible key instead of shifting it.
     */
    fun onVisibleRows(tab: LogTab, firstVisible: Int, lastVisible: Int, rowCount: Int) {
        val pane = panes.getValue(tab)

        // Reported first and unconditionally: the counter has to follow the scroll even while a
        // page is being loaded, which is exactly when the reader is moving.
        if (rowCount > 0) {
            val rows = pane.state.value.rows
            val from = rows.getOrNull(firstVisible)?.index?.plus(1) ?: 0
            val to = rows.getOrNull(lastVisible)?.index?.plus(1) ?: 0
            if (from != pane.state.value.visibleFirst || to != pane.state.value.visibleLast) {
                pane.state.update { it.copy(visibleFirst = from, visibleLast = to) }
            }
        }

        if (pane.pageJob?.isActive == true || pane.loadJob?.isActive == true) return
        if (pane.state.value.status !is LogStatus.Ready) return
        val count = pane.viewCount()

        val extendUp = firstVisible < THRESHOLD && pane.first > 0
        val extendDown = lastVisible > rowCount - THRESHOLD && pane.last < count
        if (!extendUp && !extendDown) return

        pane.pageJob =
            viewModelScope.launch(Dispatchers.IO) {
                pane.mutex.withLock {
                    if (extendUp) {
                        val first = max(0, pane.first - PAGE)
                        loadWindow(pane, first, first + WINDOW, ScrollTo.NONE)
                    } else {
                        val last = min(count, pane.last + PAGE)
                        loadWindow(pane, last - WINDOW, last, ScrollTo.NONE)
                    }
                }
            }
    }

    fun jumpToOldest(tab: LogTab) = jump(tab, Jump.OLDEST)

    fun jumpToNewest(tab: LogTab) = jump(tab, Jump.NEWEST)

    private fun jump(tab: LogTab, to: Jump) {
        val pane = panes.getValue(tab)
        pane.pageJob?.cancel()
        pane.pageJob =
            viewModelScope.launch(Dispatchers.IO) {
                pane.mutex.withLock { applyJump(pane, to, pane.first) }
            }
    }

    // --- Filtering ---------------------------------------------------------------------------

    fun setQuery(tab: LogTab, text: String) =
        updateQuery(tab, debounce = true) { it.copy(text = text) }

    fun toggleLevel(tab: LogTab, level: LogLevel) =
        updateQuery(tab, debounce = false) {
            it.copy(levels = if (level in it.levels) it.levels - level else it.levels + level)
        }

    fun toggleWriter(tab: LogTab, uid: Int) =
        updateQuery(tab, debounce = false) {
            it.copy(uids = if (uid in it.uids) it.uids - uid else it.uids + uid)
        }

    fun toggleTag(tab: LogTab, tag: String) =
        updateQuery(tab, debounce = false) {
            it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag)
        }

    fun clearFilter(tab: LogTab) = updateQuery(tab, debounce = false) { LogQuery() }

    /**
     * Computes the facet counts without narrowing anything, for the filter sheet.
     *
     * The sheet lists the tags the file actually contains with their counts rather than a
     * hardcoded set that goes stale, and that list is a by-product of the same pass that builds a
     * filter — so opening the sheet runs the scan with an unchanged query and keeps the window
     * exactly where the reader left it.
     */
    fun loadFacets(tab: LogTab) {
        val pane = panes.getValue(tab)
        if (pane.state.value.facets != null || pane.index == null) return
        updateQuery(tab, debounce = false, jumpTo = Jump.KEEP) { it }
    }

    private fun updateQuery(
        tab: LogTab,
        debounce: Boolean,
        jumpTo: Jump = Jump.NEWEST,
        transform: (LogQuery) -> LogQuery,
    ) {
        val pane = panes.getValue(tab)
        pane.state.update { it.copy(query = transform(it.query)) }
        pane.scanJob?.cancel()
        pane.scanJob =
            viewModelScope.launch(Dispatchers.IO) {
                // Typing should not launch a full-file scan per keystroke; the in-flight one is
                // cancelled above and the reader's `yield()` per block lets it stop promptly.
                if (debounce) delay(QUERY_DEBOUNCE_MS)
                pane.mutex.withLock { runScan(pane, jumpTo) }
            }
    }

    private suspend fun runScan(pane: Pane, jumpTo: Jump) {
        val index = pane.index ?: return
        val content = pane.content ?: return
        val query = pane.state.value.query

        pane.state.update { it.copy(status = LogStatus.Scanning(0f)) }
        var reported = 0f
        val scan =
            try {
                content.scan(index, query) { progress ->
                    // A repaint per 256 KB block would be pure churn on a small file.
                    if (progress - reported >= PROGRESS_STEP) {
                        reported = progress
                        pane.state.update { it.copy(status = LogStatus.Scanning(progress)) }
                    }
                }
            } catch (e: Exception) {
                pane.state.update { it.copy(status = LogStatus.ReadFailed(e.message)) }
                return
            }

        pane.matches = scan.matches
        val count = scan.matches?.size ?: index.lineCount
        // Named here, on the scan's own dispatcher: a host resolves a uid through the package
        // manager, and asking it once per chip while the filter sheet animates in would put those
        // round trips on the main thread.
        val names = scan.facets.writers.mapNotNull { w -> source.writerLabel(w.uid)?.let { w.uid to it } }.toMap()
        pane.state.update { it.copy(facets = scan.facets, writerNames = names, visibleLines = count) }

        if (count == 0) {
            pane.first = 0
            pane.last = 0
            pane.state.update {
                it.copy(
                    status = if (query.isActive) LogStatus.NoMatches else LogStatus.Empty,
                    rows = emptyList(),
                    windowFirst = 0,
                    windowLast = 0,
                )
            }
            return
        }
        applyJump(pane, jumpTo, pane.first)
    }

    // --- Destructive and export actions --------------------------------------------------------

    /**
     * Resets the current log — a rotation or a clear, depending on the host.
     *
     * [onResult] reports whether the host took the request, which is as much as there is to know
     * about an operation whose effect the host does not report back.
     */
    fun reset(tab: LogTab, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok =
                runCatching { source.reset(tab == LogTab.VERBOSE) }
                    .getOrElse {
                        Log.e(TAG, "logs: resetting the ${tab.name.lowercase()} log failed", it)
                        false
                    }
            if (ok) reload(tab, Jump.NEWEST)
            onResult(ok)
        }
    }

    /**
     * Writes the host's export into [uri].
     *
     * This is the slowest transaction on the screen by a wide margin for a host that assembles a
     * bug report, so it is seconds and synchronous, and the host's guarantee that its transport
     * never touches the main thread is load-bearing here more than anywhere else.
     */
    fun saveTo(uri: Uri, verbose: Boolean) {
        if (_saveState.value == LogSaveState.Saving) return
        viewModelScope.launch(Dispatchers.IO) {
            _saveState.value = LogSaveState.Saving
            _saveState.value =
                source
                    .saveArchive(uri, verbose)
                    .fold(
                        onSuccess = { LogSaveState.Saved(uri) },
                        onFailure = { LogSaveState.Failed(it.message) },
                    )
        }
    }

    fun consumeSaveState() {
        _saveState.value = LogSaveState.Idle
    }

    fun setVerbose(enabled: Boolean) {
        viewModelScope.launch {
            val actual =
                runCatching {
                        source.setVerboseEnabled(enabled)
                    }
                    .getOrElse {
                        Log.e(TAG, "logs: setting verbose logging to $enabled failed", it)
                        enabled
                    }
            _verboseEnabled.value = actual
            _verboseEnforced.value = !enabled && actual
            if (actual) refresh(LogTab.VERBOSE)
        }
    }

    override fun onCleared() {
        super.onCleared()
        panes.values.forEach { pane ->
            pane.loadJob?.cancel()
            pane.scanJob?.cancel()
            pane.pageJob?.cancel()
            pane.content?.close()
            pane.content = null
        }
    }

    companion object {
        /** Rows held at once. At ~150 bytes a line this is a third of a megabyte of text. */
        const val WINDOW = 2_000

        /**
         * How far the window's near edge moves per extension. The far edge follows it, so a step
         * re-reads a whole [WINDOW] either way; this only sets how often that happens.
         */
        private const val PAGE = 500

        /** How close to an edge the viewport gets before the window is extended. */
        private const val THRESHOLD = 60

        /**
         * How many writers are named per scan.
         *
         * The filter lists them by weight, so the ones past this wrote a handful of lines each. They still appear, as
         * their uid, and are still selectable; what they no longer do is make every scan pay to name them.
         */
        private const val MAX_NAMED_WRITERS = 48

        private const val QUERY_DEBOUNCE_MS = 250L

        private const val PROGRESS_STEP = 0.02f
    }
}
