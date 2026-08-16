package org.matrix.vector.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One item of the navigation container: its stable identity, its label, and its icon(s).
 *
 * Shared by both apps' nav bars. Each app builds its own list of these — the panels differ (Vector's
 * Modules vs LSPatch's Manage) — and maps a [key] back to its own route type for the actual
 * navigation, which is the one thing the two apps do differently enough that it stays app-side.
 *
 * [key] is the only stable identity, and so the only thing ever written to preferences: R8 rewrites
 * class names in a release build, and an ordinal would quietly name a different panel the day a
 * fifth one is declared. See [NavPanels] for what is stored.
 *
 * [labelRes] is a string resource id — no hard-coded English — resolved by the bar at draw time.
 * [selectedIcon] defaults to [icon], so a host with a single glyph per item need not repeat it.
 */
@Immutable
class TopLevelDestination(
    val key: String,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)
