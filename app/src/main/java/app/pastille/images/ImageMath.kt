package app.pastille.images

import kotlin.math.roundToInt

fun targetSize(width: Int, height: Int, maxEdge: Int = 2048): Pair<Int, Int> {
    require(width > 0 && height > 0) { "Dimensions must be positive" }
    val longEdge = maxOf(width, height)
    if (longEdge <= maxEdge) return width to height
    val scale = maxEdge.toDouble() / longEdge
    return maxOf(1, (width * scale).roundToInt()) to maxOf(1, (height * scale).roundToInt())
}

fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
    val (targetWidth, targetHeight) = targetSize(width, height, maxEdge)
    var sample = 1
    while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) {
        sample *= 2
    }
    return sample
}

fun extensionFor(mimeType: String): String {
    return when (mimeType.substringBefore(';').trim().lowercase()) {
        "image/gif" -> "gif"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
}

fun mimeTypeForFile(name: String): String {
    return when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "gif" -> "image/gif"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }
}

fun hashStem(digest: ByteArray): String = digest.joinToString("") { "%02x".format(it) }.take(32)

fun hashName(digest: ByteArray, ext: String): String = "${hashStem(digest)}.$ext"

// A stored file matches a source by its hash stem, whatever extension it was written with.
fun storedNameForStem(stem: String, present: List<String>): String? =
    present.firstOrNull { it.substringBeforeLast('.', "") == stem }

// Files younger than the grace period may belong to an import whose row isn't inserted yet.
fun orphans(
    present: List<Pair<String, Long>>,
    referenced: Set<String>,
    now: Long,
    graceMs: Long = 60 * 60 * 1000L,
): List<String> =
    present.filter { (name, lastModified) -> name !in referenced && lastModified < now - graceMs }
        .map { (name, _) -> name }

fun staleShared(
    files: List<Pair<String, Long>>,
    now: Long,
    maxAgeMs: Long = 24 * 60 * 60 * 1000L,
): List<String> =
    files.filter { (_, lastModified) -> lastModified < now - maxAgeMs }.map { (name, _) -> name }

sealed interface StoragePlan {
    data object Copy : StoragePlan
    data class Transcode(val maxEdge: Int) : StoragePlan
    data class Refuse(val reason: String) : StoragePlan
}

const val STORAGE_MAX_EDGE = 2048
const val MAX_ANIMATED_BYTES = 15L * 1024 * 1024

// Animation and transparency survive only when the bytes are kept, so GIFs, animated WebPs and
// small PNGs or static WebPs are copied. Everything else is re-encoded at most 2048px.
fun storagePlan(mime: String?, width: Int, height: Int, bytes: Long, animated: Boolean): StoragePlan {
    val type = mime?.substringBefore(';')?.trim()?.lowercase()
    val longEdge = maxOf(width, height)
    return when {
        type == "image/gif" || (type == "image/webp" && animated) ->
            if (bytes <= MAX_ANIMATED_BYTES) StoragePlan.Copy else StoragePlan.Refuse("Image too large")
        (type == "image/webp" || type == "image/png") && longEdge <= STORAGE_MAX_EDGE -> StoragePlan.Copy
        else -> StoragePlan.Transcode(STORAGE_MAX_EDGE)
    }
}

const val WEBP_HEADER_BYTES = 21

// RIFF....WEBPVP8X, then a flags byte whose bit 1 marks an animation.
fun isAnimatedWebp(header: ByteArray): Boolean {
    if (header.size < WEBP_HEADER_BYTES) return false
    fun ascii(from: Int, text: String) = text.indices.all { header[from + it] == text[it].code.toByte() }
    return ascii(0, "RIFF") && ascii(8, "WEBP") && ascii(12, "VP8X") &&
        (header[20].toInt() and 0x02) != 0
}

private const val MB = 1024 * 1024

// An eighth of the app's memory class, between 8 and 32 MB.
fun thumbnailCacheBytes(memoryClassMb: Int): Int =
    (memoryClassMb.toLong() * MB / 8).coerceIn(8L * MB, 32L * MB).toInt()
