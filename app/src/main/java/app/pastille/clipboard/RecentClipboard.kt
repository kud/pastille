package app.pastille.clipboard

import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Holds the recent clips for the process (the keyboard and the app share it). Never persisted.
class RecentClipboard(
    private val scope: CoroutineScope,
    private val enabled: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxChars: Int = Int.MAX_VALUE,
) {
    private val mutableState = MutableStateFlow(RecentClipsState())
    val state: StateFlow<RecentClipsState> = mutableState.asStateFlow()
    private var expiryJob: Job? = null

    fun record(candidate: ClipCandidate?, sensitiveField: Boolean = false) {
        if (candidate == null) return
        if (!enabled()) {
            clear()
            return
        }
        change { it.record(candidate, now(), sensitiveField, maxChars) }
    }

    // Reads the clipboard only when the feature is on, and never the content from a password field.
    fun recordCurrent(context: Context, sensitiveField: Boolean = false) {
        if (!enabled()) {
            clear()
            return
        }
        record(readClipCandidate(context, state.value.lastSeenStamp, sensitiveField), sensitiveField)
    }

    fun remove(id: Long) = change { it.remove(id) }

    fun clear() = change { it.cleared() }

    fun prune() = change { it.pruned(now()) }

    private fun change(transform: (RecentClipsState) -> RecentClipsState) {
        mutableState.update(transform)
        scheduleExpiry()
    }

    @Synchronized
    private fun scheduleExpiry() {
        expiryJob?.cancel()
        val at = mutableState.value.nextExpiry() ?: return
        expiryJob = scope.launch {
            delay((at - now()).coerceAtLeast(0))
            mutableState.update { it.pruned(now()) }
            scheduleExpiry()
        }
    }
}

private const val EXTRA_IS_SENSITIVE_KEY = "android.content.extra.IS_SENSITIVE"

// One read of the primary clip. The description (label, sensitive flag, stamp) is checked before the
// content, so a clip already seen, Pastille's own, or a sensitive one is never read. Only items that
// carry text count: a URI or intent clip (an image, a file) isn't recorded.
fun readClipCandidate(context: Context, lastSeenStamp: Long?, sensitiveField: Boolean = false): ClipCandidate? = try {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val description = clipboard?.primaryClipDescription
    if (description == null) {
        null
    } else {
        val stamp = description.timestamp
        // ClipDescription.EXTRA_IS_SENSITIVE is API 33, but password managers set the same key on older
        // versions too, so it is read by name everywhere.
        val sensitive = description.extras?.getBoolean(EXTRA_IS_SENSITIVE_KEY) == true
        val own = description.label?.toString()?.startsWith(CLIP_LABEL_PREFIX) == true
        val skipContent = sensitiveField || sensitive || own || (stamp != 0L && stamp == lastSeenStamp)
        val text = if (skipContent) {
            null
        } else {
            clipboard.primaryClip?.let { clip ->
                (0 until clip.itemCount)
                    .mapNotNull { clip.getItemAt(it)?.text?.toString() }
                    .firstOrNull { it.isNotBlank() }
            }
        }
        ClipCandidate(text = text, label = description.label, sensitive = sensitive, stamp = stamp)
    }
} catch (_: Exception) {
    null
}
