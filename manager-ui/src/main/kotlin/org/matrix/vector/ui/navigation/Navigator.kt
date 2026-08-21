package org.matrix.vector.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/**
 * The back stack, as an object with intent-revealing operations.
 *
 * Navigation 3 hands you the stack as a plain observable list, so this is a handful of operations
 * over that list rather than a graph definition.
 *
 * Switching tabs truncates to a single root entry, so the stack can never grow without bound and
 * system back leaves the app instead of retracing every tab that was visited.
 *
 * It owns the panel arrangement too, which is why every surface reaches for it as
 * `LocalNavigator.current.panels`, and why there is no second CompositionLocal and no
 * `rememberNavPanels` anywhere: a second source of truth is exactly what would let the bar, the
 * ball and the appearance sheet disagree about which panels exist while the reader is rearranging
 * them. The two writes go through [NavPanelStore] for the same reason — the encoding has one home.
 */
@Stable
class Navigator(
    val backStack: NavBackStack<NavKey>,
    private val panelsState: State<NavPanels>,
    private val store: NavPanelStore,
) {

    /** The reader's panels. A snapshot read, so a composable that touches it recomposes. */
    val panels: NavPanels
        get() = panelsState.value

    /**
     * Whether the navigation container is being rearranged.
     *
     * Transient by construction: a plain `mutableStateOf`, deliberately neither a
     * `rememberSaveable` nor a preference. Coming back to the app days later to find it still in
     * edit mode would be a puzzle, and the arrangement itself is already written the instant it
     * changes, so there is nothing here worth restoring.
     */
    var editingPanels: Boolean by mutableStateOf(false)

    val current: NavKey?
        get() = backStack.lastOrNull()

    /**
     * Which item is highlighted — the root of the stack, or the first visible panel.
     *
     * The visibility test is not made redundant by [reconcilePanels]: hiding the panel you are
     * standing on recomposes the container before the effect that corrects the stack has run, and
     * for that frame the root names something the container is no longer drawing. A bar that
     * highlights nothing is worse than one highlighting the panel you are about to be moved to.
     */
    val currentTopLevel: NavKey
        get() {
            val root = backStack.firstOrNull() ?: return panels.start.route
            return if (panels.isVisible(root)) root else panels.start.route
        }

    val canGoBack: Boolean
        get() = backStack.size > 1

    /** Push a detail destination on top of the current tab. */
    fun go(route: NavKey) {
        if (backStack.lastOrNull() != route) backStack.add(route)
    }

    /**
     * Put [route] where the current screen is, so back from it returns past this one.
     *
     * For a screen whose job ends the moment it is answered -- a picker that has been picked from.
     * Leaving it on the stack would make Back from the screen it opened return to the question the
     * reader has already answered.
     */
    fun replace(route: NavKey) {
        if (backStack.isEmpty()) backStack.add(route) else backStack[backStack.lastIndex] = route
    }

    /** Select a bar item, discarding whatever detail screens were open. */
    fun switchTo(tab: NavKey) {
        if (backStack.size == 1 && backStack.firstOrNull() == tab) return
        backStack.clear()
        backStack.add(tab)
    }

    /** Returns false when there is nothing left to pop, so the caller can let the system exit. */
    fun back(): Boolean {
        if (!canGoBack) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    /** Hide or restore a panel, and persist it. [key] is a TopLevelDestination.key. */
    fun setPanelHidden(key: String, hidden: Boolean) {
        store.setEncoded(encodeNavPanels(panels.withHidden(key, hidden)))
    }

    /** Reorder, and persist it. Both indices are into [NavPanels.all]. */
    fun movePanel(from: Int, to: Int) {
        store.setEncoded(encodeNavPanels(panels.withMoved(from, to)))
    }

    /**
     * Replace a root that names a panel which is no longer shown.
     *
     * This is one mechanism serving two stories that look unrelated: hiding the panel you are on
     * moves you to the first visible one, and a stack restored from before a panel was hidden — a
     * real state, since the stack survives process death both through SavedStateRegistry and
     * through the hooker's per-activity Bundle cache — is corrected instead of leaving a container
     * that highlights nothing.
     *
     * `backStack[0] = …` rather than clear() then add(): NavBackStack supports set(index, value),
     * and emptying the list even for an instant hands NavDisplay a stack with no entries.
     */
    fun reconcilePanels() {
        val root = backStack.firstOrNull() ?: return
        // Only a root that names a panel is this method's business. The type test this replaces
        // said the same thing; asking the catalogue says it without the shared code having to know
        // the host's route types. `all`, not `visible`: a hidden panel is still a panel, and it is
        // exactly the root this exists to correct.
        if (panels.all.none { it.route == root }) return
        if (!panels.isVisible(root)) backStack[0] = panels.start.route
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator in composition") }

@Composable
fun rememberNavigator(store: NavPanelStore, catalogue: List<TopLevelDestination>): Navigator {
    val stored = store.encoded.collectAsStateWithLifecycle()
    // Derived rather than decoded on every recomposition: the string changes when a panel is
    // dragged or hidden and at no other time, while everything that reads the panels reads them
    // once per frame.
    val panels = remember(stored, catalogue) { derivedStateOf { decodeNavPanels(stored.value, catalogue) } }
    // rememberNavBackStack persists across process death via SavedState, which matters here:
    // parasitically the manager's activity state is hand-managed by the zygisk hooker, so
    // anything that relies on the system restoring it needs to survive that path too. The first
    // visible panel is the seed and only the seed — a restored stack skips it entirely, which is
    // why the correction below is an effect that runs on every arrangement rather than a one-off.
    val backStack = rememberNavBackStack(panels.value.start.route)
    val navigator = remember(backStack, panels, store) { Navigator(backStack, panels, store) }
    LaunchedEffect(navigator, panels.value) { navigator.reconcilePanels() }
    return navigator
}
