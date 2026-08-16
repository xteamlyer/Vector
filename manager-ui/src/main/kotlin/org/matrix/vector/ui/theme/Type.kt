package org.matrix.vector.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * Monospaced identifiers, for the shared UI.
 *
 * Commit SHAs, version codes, package names and log lines are things a user compares character by
 * character. Proportional digits make that harder, so [Mono] is used for them rather than bending
 * the body styles. A copy of Vector's `VectorMono`, kept here so the shared store screen does not
 * reach back into either app's theme.
 */
val Mono: TextStyle =
    TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        letterSpacing = 0.sp,
        // Technical text stays left-to-right even when the app is not. Version numbers, package
        // names and uid/pid triples are identifiers, not prose: dropped into an Arabic paragraph
        // the bidi algorithm reorders their segments, and "2.0 (3049) · API 101" comes out as
        // "API 101 · (3049) 2.0". The string is the same; what it says is not.
        textDirection = TextDirection.Ltr,
    )

/** Log bodies: monospace, tighter line height than the body scale, and small. */
val LogLine: TextStyle =
    TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.sp,
        // A log line is machine output; it is read in the order it was written, always.
        textDirection = TextDirection.Ltr,
    )
