package org.matrix.vector.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Puts text on the clipboard, or does nothing.
 *
 * Shared by the reusable screens — every one of them copies for the same reason, the text is on its
 * way into a bug report, so they want the same silence when there is no clipboard service to hand.
 * There may not be: a host may be running inside `com.android.shell`, and a failure to copy is not
 * worth a crash on a screen someone opened *because* something had already gone wrong.
 */
fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(context.packageName, text))
}
