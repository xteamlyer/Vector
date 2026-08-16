package org.matrix.vector.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How many relationship-preview icons a reach cluster shows before collapsing the rest into "+N".
 *
 * The single owner of this cap: a module's scope, an app's reach, and any other "who does this touch"
 * preview all count to the same length, so they read as the same kind of thing. [ModuleRow] applies
 * it for its built-in reach band; a host driving [IconCluster] directly caps its own list to match.
 */
const val REACH_PREVIEW_LIMIT = 3

/**
 * The size a reach icon is drawn at inside a row's reach band.
 *
 * A shared contract, not a free choice: the row reserves a band sized to fit exactly this, so a slot
 * a host hands to a row's reach must draw at this size. Standalone [IconCluster] use (outside a row)
 * is free to pick any size.
 */
val REACH_ICON_SIZE = 20.dp

/**
 * A row of small icons standing in for a set too large to name, with the tail collapsed to "+N".
 *
 * The shared "who does this touch" cluster: a module's scope on the Manage screen shows the apps it
 * reaches this way, and an app's row shows the modules reaching it the same way, so the two sides of
 * the same relationship read identically. A count alone answers a question nobody asked — three
 * recognisable icons answer "does this touch anything I care about" without opening anything, and the
 * remainder becomes a number after them.
 *
 * The caller owns how each icon is drawn ([icons] and the optional [leading] mark are slots). Drawn
 * inside a row's reach band the slots must draw at [REACH_ICON_SIZE], the size the band is sized to;
 * used standalone the size is free. This owns only the layout and the overflow count, which is what
 * has to match across call sites. Draw nothing when there is nothing to depict — the caller decides
 * that, since an empty cluster and an unknown one look the same from here.
 */
@Composable
fun IconCluster(
    icons: List<@Composable () -> Unit>,
    remainder: Int,
    modifier: Modifier = Modifier,
    /** An iconless member drawn first — the framework, which is a scope target with no launcher icon. */
    leading: (@Composable () -> Unit)? = null,
    spacing: Dp = 3.dp,
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        leading?.let { Box(Modifier.padding(start = spacing)) { it() } }
        icons.forEach { slot -> Box(Modifier.padding(start = spacing)) { slot() } }
        if (remainder > 0) {
            Spacer(Modifier.width(5.dp))
            Text(
                text = stringResource(R.string.modules_scope_more, remainder),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
