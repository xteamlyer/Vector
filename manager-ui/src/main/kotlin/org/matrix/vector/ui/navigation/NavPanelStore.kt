package org.matrix.vector.ui.navigation

import kotlinx.coroutines.flow.StateFlow

/**
 * Where the panel arrangement is kept, injected so the shared [Navigator] does not reach into
 * either app's settings store.
 *
 * One encoded string, the form [encodeNavPanels] produces, rather than a decoded [NavPanels]: the
 * arrangement has exactly one home, and a host that stored the decoded form would have to know the
 * encoding rules to write it — which is how a bar and a sheet come to disagree about which panels
 * exist while the reader is rearranging them.
 *
 * A flow, unlike [FloatingNavSettings]: this is read by everything that draws the container, and a
 * change has to reach all of it in the same frame.
 */
interface NavPanelStore {

    val encoded: StateFlow<String>

    fun setEncoded(value: String)
}
