package org.matrix.vector.ui.net

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import org.matrix.vector.ui.R
import org.matrix.vector.ui.SheetHeading
import org.matrix.vector.ui.StatusNote
import org.matrix.vector.ui.ToggleRow

/**
 * The Network section of a settings sheet: the DoH switch and, beneath it, what the last lookup
 * actually did.
 *
 * Shared so Vector and LSPatch present name resolution identically. A host supplies its
 * [NetworkSettings] and the [DohStatus] flow of its own resolver (each app owns one client, one
 * resolver); this renders the switch and the status.
 *
 * The switch says what was asked for; the status line says what happened, and they come apart more
 * often than the switch admits — a proxy takes the decision away, and one unreachable lookup
 * latches the fallback for the session. The status is shown only while the switch is on: off, the
 * switch has already said so, and a second line repeating it would carry no information.
 */
@Composable
fun ColumnScope.DohSettingSection(
    settings: NetworkSettings,
    status: StateFlow<DohStatus>,
    onRetry: () -> Unit,
) {
    val doh by settings.dohEnabled.collectAsStateWithLifecycle()
    val dohStatus by status.collectAsStateWithLifecycle()

    SheetHeading(stringResource(R.string.settings_network), Icons.Rounded.Dns)
    ToggleRow(
        title = stringResource(R.string.settings_doh),
        icon = Icons.Rounded.Dns,
        subtitle = stringResource(R.string.settings_doh_summary),
        checked = doh,
        onCheckedChange = settings::setDohEnabled,
    )
    if (doh) {
        when (val state = dohStatus) {
            is DohStatus.Untested -> StatusNote(stringResource(R.string.settings_doh_untested))

            is DohStatus.Bypassed -> StatusNote(stringResource(R.string.settings_doh_bypassed))

            is DohStatus.Working ->
                StatusNote(
                    stringResource(R.string.settings_doh_working, state.host),
                    tone = MaterialTheme.colorScheme.primary,
                )

            // The one state with something to offer. Not an error colour: falling back is the
            // designed behaviour and the app is working, so this is the shade the rest of the app
            // uses for "worth knowing", not for "something is broken".
            is DohStatus.FellBack ->
                StatusNote(
                    stringResource(R.string.settings_doh_fell_back, state.reason),
                    tone = MaterialTheme.colorScheme.tertiary,
                    actionLabel = stringResource(R.string.settings_doh_retry),
                    onAction = onRetry,
                )

            // Reachable only in the gap between flipping the switch on and the next lookup
            // recording something newer.
            is DohStatus.Disabled -> StatusNote(stringResource(R.string.settings_doh_untested))
        }
    }
}
