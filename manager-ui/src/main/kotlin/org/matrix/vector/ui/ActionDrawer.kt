package org.matrix.vector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The header at the top of a package action drawer: the package's icon, its label, its package
 * name, and an optional slot for extra facts (a description, an API level).
 *
 * Mirrors the row it was opened from, so the reader can see which package the actions below act on.
 * The icon is a slot rather than a bitmap so each host draws it however it already draws app icons.
 */
@Composable
fun ActionDrawerHeader(
    label: String,
    packageName: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        icon?.invoke()
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = packageName,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            extraContent?.invoke(this)
        }
    }
}

/**
 * One full-width action inside a package action drawer: its glyph in a tinted disc, the action's
 * title, and — when it has one — the line under it saying what the action does.
 *
 * The disc is what lets a destructive action look destructive: an error-red glyph on a bare row is
 * easy to miss, the same glyph on a red disc is not. Once one row carries it they all have to, or
 * the bare one reads as a different kind of thing sitting in the same list. The measurements keep a
 * single column running down the whole drawer: 24dp of margin, a 40dp disc and 20dp of gap put
 * every title where [ActionDrawerHeader] puts the package's name.
 *
 * [tint] colours a destructive or emphasised action; a title inherits it only when it is the error
 * colour, so a merely emphasised row keeps a readable heading. [trailing] is for a row that carries
 * state as well as an action — a switch, a badge. [onClick] is nullable because a row can be a
 * statement rather than an action ("not in the store"), and a caller that needs a non-button role
 * (a toggle announced as a switch) supplies its own behaviour through [modifier] and leaves this
 * null.
 */
@Composable
fun ActionDrawerItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val accent = tint ?: colors.onSurfaceVariant
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .then(modifier)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (tint == colors.error) colors.error else colors.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/**
 * The centred icon-and-line a list panel shows when it has nothing to list — a still-loading set, a
 * genuinely empty one, or a search that matched nothing. The same shape on every panel, so an empty
 * screen always reads as an empty screen rather than as something broken.
 */
@Composable
fun PanelEmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
