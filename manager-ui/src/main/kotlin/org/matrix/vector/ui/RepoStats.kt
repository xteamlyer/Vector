package org.matrix.vector.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.matrix.vector.ui.locale.currentLocale
import java.util.Locale

/**
 * Compact counts for the project footer: 11905 becomes "11.9k".
 *
 * The locale is passed in rather than read from `Locale.getDefault()`, which is the *process* default
 * and stays the host app's: a reader on a French phone who has set the app to English would otherwise
 * be shown "11,9k".
 */
fun compactCount(value: Int, locale: Locale): String =
    when {
        value < 1_000 -> value.toString()
        value < 1_000_000 -> String.format(locale, "%.1fk", value / 1000f)
        else -> String.format(locale, "%.1fM", value / 1_000_000f)
    }

/**
 * The shared one-line project footer: stars / forks / open issues / licence, muted and centred.
 *
 * A standing fact about the project rather than a list item, so it is centred — that reads as a
 * footer rather than one more left-aligned row. Both hosts' Home screens show the same thing from the
 * same GitHub repository over the same layout: this is Vector's footer, and LSPatch renders it too.
 * Counts format in the in-app locale via [currentLocale], so the language override reaches them.
 */
@Composable
fun RepoStatsRow(
    stars: Int,
    forks: Int,
    openIssues: Int,
    license: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        val locale = currentLocale()
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FooterStat(Icons.Rounded.Star, compactCount(stars, locale))
            FooterStat(Icons.AutoMirrored.Rounded.CallSplit, compactCount(forks, locale))
            FooterStat(Icons.Rounded.BugReport, openIssues.toString())
            if (!license.isNullOrBlank()) {
                Text(
                    text = license,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FooterStat(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.height(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
