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

fun hashName(digest: ByteArray, ext: String): String {
    val hex = digest.joinToString("") { "%02x".format(it) }.take(32)
    return "$hex.$ext"
}

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
