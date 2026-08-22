package org.matrix.vector.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey

/**
 * One item of the navigation container: its stable identity, where it goes, its label and its
 * icon(s).
 *
 * Shared by both apps' nav bars. Each app builds its own list of these — the panels differ — and
 * declares its own route types; what is common is that a panel is a key, a destination and a glyph,
 * and that every surface drawing the container has to agree about all three.
 *
 * [key] is the only stable identity, and so the only thing ever written to preferences: a shrinker
 * rewrites class names in a release build, and an ordinal would quietly name a different panel the
 * day another one is declared. See [NavPanels] for what is stored.
 *
 * [route] is the back stack's own type, so a panel carries its destination rather than leaving each
 * host to map keys back to routes — which is the mapping that has to be re-derived, and can be got
 * wrong, everywhere the container is drawn.
 *
 * [labelRes] is a string resource id — no hard-coded English — resolved by the bar at draw time.
 * [selectedIcon] defaults to [icon], so a host with a single glyph per item need not repeat it.
 */
@Immutable
class TopLevelDestination(
    val key: String,
    val route: NavKey,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)
