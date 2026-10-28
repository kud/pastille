package app.pastille.clipboard

// Recent clipboard: what was on the clipboard while Pastille could read it (its keyboard shown, or its
// app in front; Android 10+ lets no one else read). Memory only: never written to disk, so never in a
// backup, an export or the sync file, and gone with the process. These are the pure rules; the holder
// and the clipboard reads live in RecentClipboard.kt.

const val RECENT_CLIPS_MAX = 10
const val RECENT_CLIP_TTL_MS = 60 * 60 * 1000L

data class RecentClip(val id: Long, val text: String, val at: Long)

// What one read of the clipboard found. `stamp` is the clip's ClipDescription timestamp: the same
// stamp means the same copy, seen before, whatever its text.
data class ClipCandidate(
    val text: String?,
    val label: CharSequence?,
    val sensitive: Boolean,
    val stamp: Long,
)

data class RecentClipsState(
    val clips: List<RecentClip> = emptyList(),
    val lastSeenStamp: Long? = null,
    val nextId: Long = 1,
)

// Kept only when it is text, not marked sensitive (password managers set EXTRA_IS_SENSITIVE), not
// Pastille's own copy (labelled `pastille:<uuid>`: it is a snippet already), and not read while the
// keyboard sits in a password or incognito field.
fun isRecordable(candidate: ClipCandidate, sensitiveField: Boolean): Boolean {
    val text = candidate.text
    return !text.isNullOrBlank() &&
        !candidate.sensitive &&
        !sensitiveField &&
        candidate.label?.toString()?.startsWith(CLIP_LABEL_PREFIX) != true
}

// A clip lives an hour. One stamped in the future (the clock went back) goes too: privacy over keeping it.
fun RecentClipsState.pruned(now: Long): RecentClipsState {
    val kept = clips.filter { now >= it.at && now - it.at < RECENT_CLIP_TTL_MS }
    return if (kept.size == clips.size) this else copy(clips = kept)
}

// Each copy is looked at once, by its stamp, so a clip removed or cleared doesn't come back the next
// time the keyboard opens on the same clipboard. A stamp of 0 is unknown and always looked at.
fun RecentClipsState.record(
    candidate: ClipCandidate,
    now: Long,
    sensitiveField: Boolean = false,
    maxChars: Int = Int.MAX_VALUE,
): RecentClipsState {
    val base = pruned(now)
    if (candidate.stamp != 0L && candidate.stamp == base.lastSeenStamp) return base
    val seen = base.copy(lastSeenStamp = candidate.stamp.takeIf { it != 0L } ?: base.lastSeenStamp)
    if (!isRecordable(candidate, sensitiveField)) return seen
    val text = candidate.text!!.take(maxChars)
    val existing = seen.clips.firstOrNull { it.text == text }
    val clip = existing?.copy(at = now) ?: RecentClip(id = seen.nextId, text = text, at = now)
    val clips = (listOf(clip) + seen.clips.filter { it.text != text }).take(RECENT_CLIPS_MAX)
    return seen.copy(clips = clips, nextId = if (existing == null) seen.nextId + 1 else seen.nextId)
}

fun RecentClipsState.remove(id: Long): RecentClipsState = copy(clips = clips.filter { it.id != id })

// Clearing keeps lastSeenStamp, so what is on the clipboard now stays cleared.
fun RecentClipsState.cleared(): RecentClipsState = copy(clips = emptyList())

// When the oldest clip expires, so the holder can drop it on time rather than at the next read.
fun RecentClipsState.nextExpiry(): Long? = clips.minOfOrNull { it.at }?.plus(RECENT_CLIP_TTL_MS)
