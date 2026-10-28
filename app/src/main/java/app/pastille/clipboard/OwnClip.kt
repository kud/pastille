package app.pastille.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Copies text Pastille wrote itself (the About version, a crash report) under a `pastille:` label,
 * so the recent clipboard skips it like any of Pastille's own copies. Compose's clipboard gives no
 * label, which is how these two once showed up in the keyboard's Recent row.
 */
fun copyOwnText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(newClipLabel(), text))
}
