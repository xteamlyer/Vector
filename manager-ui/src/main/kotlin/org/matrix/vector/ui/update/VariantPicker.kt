package org.matrix.vector.ui.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.matrix.vector.ui.R

/**
 * One build a release published: which variant it is, how big it is, and the name to fall back on
 * when the variant is neither of the two the picker knows how to label.
 *
 * [key] is the stable identity the caller works in -- the same string it persists and resolves a
 * remembered choice against. The two keys the picker gives its own label and rationale to are
 * [RELEASE] and [DEBUG]; anything else keeps [fallbackLabel] (its file name, usually) and offers no
 * rationale line, because the picker cannot honestly say what an unfamiliar build is for.
 */
data class VariantChoice(
    val key: String,
    val sizeInBytes: Long,
    val fallbackLabel: String = "",
) {
    companion object {
        const val RELEASE = "release"
        const val DEBUG = "debug"
    }
}

/**
 * Which of a release's builds to install: Release or Debug, one of two.
 *
 * A segmented row rather than checkboxes, because this is one-of-two rather than on-and-off: two
 * checkboxes permit neither and both, and a single "install the debug build" makes the ordinary
 * choice an unlabelled absence. It is the same control the theme selector uses, for the same reason
 * -- the shared outline says "it is one of these".
 *
 * The size sits on each segment because it is the part of the decision that is otherwise a surprise,
 * and it is the size the release itself reports rather than an assumption about which build is
 * bigger. The line beneath says what the choice means and changes with it, so the answer arrives at
 * the moment the question is asked.
 *
 * Fewer than two builds means no control at all -- nobody should be asked to choose between one
 * thing -- so the picker renders nothing. Release is always shown first, whatever order the caller
 * passed, so the default sits under the reader's thumb.
 *
 * Shared by the framework updater and LSPatch's manager updater: both publish a Release and a Debug
 * build per release and both must let the reader pick, since a debug build is what maintainers ask
 * for in a bug report.
 */
@Composable
fun VariantPicker(
    choices: List<VariantChoice>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (choices.size < 2) return
    val colors = MaterialTheme.colorScheme
    // Release first, then Debug, then anything else in the order it arrived, so the default sits
    // under the reader's thumb rather than wherever the source happened to list its assets.
    val ordered = choices.sortedBy { rank(it.key) }

    Column(modifier = modifier) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ordered.forEachIndexed { index, choice ->
                SegmentedButton(
                    selected = choice.key == selectedKey,
                    onClick = { onSelect(choice.key) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ordered.size),
                    icon = {},
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = label(choice),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatSize(choice.sizeInBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        val why =
            when (selectedKey) {
                VariantChoice.DEBUG -> stringResource(R.string.update_variant_debug_why)
                VariantChoice.RELEASE -> stringResource(R.string.update_variant_release_why)
                else -> null
            }
        if (why != null) {
            Spacer(Modifier.height(6.dp))
            // Crossfaded so the sentence reads as the answer to the tap that just happened rather
            // than as text that was always there.
            AnimatedContent(targetState = why, label = "variant") { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun label(choice: VariantChoice): String =
    when (choice.key) {
        VariantChoice.RELEASE -> stringResource(R.string.update_variant_release)
        VariantChoice.DEBUG -> stringResource(R.string.update_variant_debug)
        else -> choice.fallbackLabel
    }

private fun rank(key: String): Int =
    when (key) {
        VariantChoice.RELEASE -> 0
        VariantChoice.DEBUG -> 1
        else -> 2
    }

/** Human-readable byte size, the same rendering the update UI has always used. */
fun formatSize(bytes: Long): String =
    when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.0f kB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
