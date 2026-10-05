package app.pastille.clipboard

import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

const val CLIP_LABEL_PREFIX = "pastille:"

fun newClipLabel(): String = CLIP_LABEL_PREFIX + UUID.randomUUID()

// Only the label is compared, never the content, so Android shows no "pasted from clipboard" notice.
// A null label means the clipboard is empty or unreadable (Android 10+ in the background): not ours to clear.
fun isOwnClip(currentLabel: CharSequence?, expectedLabel: String): Boolean =
    currentLabel != null && currentLabel.toString() == expectedLabel

interface ClipboardAccess {
    fun primaryClipLabel(): CharSequence?
    fun clear()
}

class SystemClipboardAccess(context: Context) : ClipboardAccess {
    private val clipboard = context.applicationContext.getSystemService(ClipboardManager::class.java)

    override fun primaryClipLabel(): CharSequence? = clipboard?.primaryClipDescription?.label

    override fun clear() {
        clipboard?.clearPrimaryClip()
    }
}

// Lives in the Application's scope, so a timer started from the picker sheet outlives it.
// A clear missed because the process died is acceptable; there is no WorkManager fallback.
class ClipboardClearer(
    private val scope: CoroutineScope,
    private val access: ClipboardAccess,
) {
    private var pending: Job? = null

    fun schedule(label: String, delayMillis: Long) {
        pending?.cancel()
        pending = scope.launch {
            delay(delayMillis)
            clearIfOwn(label)
        }
    }

    fun cancel() {
        pending?.cancel()
        pending = null
    }

    fun clearNow(label: String): Boolean {
        cancel()
        return clearIfOwn(label)
    }

    private fun clearIfOwn(label: String): Boolean {
        val current = runCatching { access.primaryClipLabel() }.getOrNull()
        if (!isOwnClip(current, label)) return false
        return runCatching { access.clear() }.isSuccess
    }
}
