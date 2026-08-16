package org.matrix.vector.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * One installed package, as a row: its icon, its label, its package name, and whatever a screen
 * needs to add underneath (a version, a scope, a patch mode).
 *
 * Shared by every list of packages — the app and module tabs of Manage, the app picker. The icon is
 * a slot rather than a bitmap so each host draws it however it already loads icons, which is the one
 * thing that differs between them. A [checked] box or a [trailing] control may sit at the end, but
 * not both — one row cannot be a checkbox target and carry its own action at once.
 */
@Composable
fun PackageRow(
    icon: @Composable () -> Unit,
    label: String,
    packageName: String,
    modifier: Modifier = Modifier,
    labelColor: Color = Color.Unspecified,
    checked: Boolean? = null,
    trailing: (@Composable () -> Unit)? = null,
    additionalContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    require(checked == null || trailing == null) { "checked and trailing must not both be set" }
    Row(
        modifier = modifier.fillMaxWidth().padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = label,
                color = if (labelColor == Color.Unspecified) LocalContentColor.current else labelColor,
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
            additionalContent?.invoke(this)
        }
        if (checked != null) {
            Checkbox(checked = checked, onCheckedChange = null)
        }
        trailing?.invoke()
    }
}

/** Convenience for hosts that already hold the icon as a bitmap (LSPatch loads icons that way). */
@Composable
fun PackageRow(
    icon: ImageBitmap,
    label: String,
    packageName: String,
    modifier: Modifier = Modifier,
    labelColor: Color = Color.Unspecified,
    checked: Boolean? = null,
    trailing: (@Composable () -> Unit)? = null,
    additionalContent: (@Composable ColumnScope.() -> Unit)? = null,
) =
    PackageRow(
        icon = { Icon(bitmap = icon, contentDescription = label, tint = Color.Unspecified) },
        label = label,
        packageName = packageName,
        modifier = modifier,
        labelColor = labelColor,
        checked = checked,
        trailing = trailing,
        additionalContent = additionalContent,
    )
