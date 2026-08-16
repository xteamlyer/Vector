package org.matrix.vector.ui.store

import org.matrix.vector.ui.R
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * The consent gate — and parasitically it is the *only* one.
 *
 * Where the manager inherits `INSTALL_PACKAGES` (shell-mode), the commit that follows installs a
 * third-party APK with no system confirmation at all; standalone, the platform asks as usual. The
 * dialog therefore names the module, the version, the file and its size before anything is
 * downloaded, and says which of the two is about to happen. [silent] — whether the host installs
 * without a further system prompt — is passed in rather than read here, because only the app knows
 * which mode its installer runs in.
 */
@Composable
fun ConfirmInstall(
    module: OnlineModule?,
    packageName: String,
    asset: ReleaseAsset,
    silent: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, asset.size)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.store_confirm_title, module?.title ?: packageName)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.store_confirm_body,
                        asset.name.orEmpty(),
                        size,
                        packageName,
                    )
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.store_confirm_trust),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (silent) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.store_confirm_silent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.store_install)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.store_cancel)) }
        },
    )
}
