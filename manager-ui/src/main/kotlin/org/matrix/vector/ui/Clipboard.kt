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
 *
 * [label] names the clip in whatever clipboard UI the phone shows. It defaults to the context's own
 * package, which is right for a host installed under its own name and wrong for one running inside
 * someone else's process -- so a host that is not what its context says it is passes its own.
 */
fun copyToClipboard(context: Context, text: String, label: String = context.packageName) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}
