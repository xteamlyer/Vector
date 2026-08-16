package org.matrix.vector.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * A hook a host installs to re-apply its in-composition language override inside a new window.
 *
 * A dialog or a bottom sheet is its own window, and Compose gives every window a fresh set of
 * Android composition locals taken from that window's context — which undoes a host's language
 * override on the way in. A shared sheet therefore speaks the *phone's* language while the screen
 * behind it speaks the reader's, unless the override is re-applied *inside* the sheet content.
 *
 * The override cannot be re-applied around the call, because the crossing happens inside it. So the
 * shared sheets wrap their content in `LocalDialogLocalizer.current { … }`; a host that localises
 * (Vector) provides a wrapper that re-applies its override, and a host that does not (LSPatch)
 * leaves the identity default. Read inside the sheet, the local still carries the host's value —
 * Compose's own composition locals propagate into the subcomposition even though the Android
 * context ones were reset.
 */
val LocalDialogLocalizer = staticCompositionLocalOf<@Composable (@Composable () -> Unit) -> Unit> {
    { content -> content() }
}

/**
 * Material's dialog, localised through [LocalDialogLocalizer].
 *
 * The shared replacement for each host's own localised-dialog wrapper. Every slot is wrapped so the
 * override lands inside the dialog's window rather than being lost at its boundary.
 */
@Composable
fun SharedAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val localize = LocalDialogLocalizer.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { localize(confirmButton) },
        modifier = modifier,
        dismissButton = dismissButton?.let { { localize(it) } },
        icon = icon?.let { { localize(it) } },
        title = title?.let { { localize(it) } },
        text = text?.let { { localize(it) } },
    )
}
