package app.pastille.share

const val MAX_TEXT_CHARS = 50_000
const val MAX_IMAGES = 20

private const val TITLE_CHARS = 40
private val URL_REGEX = Regex("https?://\\S+")
private val TRAILING_PUNCTUATION = setOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"')
private val UUID_REGEX =
    Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

fun autoTitle(text: String): String {
    if (!text.contains('\n') && text.length <= TITLE_CHARS) return ""
    val first = text.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (first.isEmpty()) return ""
    return if (first.length > TITLE_CHARS) first.take(TITLE_CHARS) + "…" else first
}

fun findSingleUrl(text: String): String? {
    val found = URL_REGEX.findAll(text)
        .map { it.value.trimEnd { char -> char in TRAILING_PUNCTUATION } }
        .toList()
    return if (found.size == 1) found[0] else null
}

fun linkTitle(subject: String?, text: String, url: String): String {
    if (!subject.isNullOrBlank()) return subject.trim()
    val remainder = text.replace(url, "").trim()
    if (remainder.isNotEmpty()) {
        return autoTitle(remainder).ifEmpty { remainder }
    }
    val withoutScheme = url.substringAfter("://", url)
    val host = withoutScheme.substringBefore("/").substringBefore("?").substringBefore("#")
        .substringAfterLast("@").substringBefore(":")
        .let { if (it.startsWith("www.", ignoreCase = true)) it.substring(4) else it }
        .lowercase()
    val path = withoutScheme.substringAfter("/", "").substringBefore("?").substringBefore("#")
    val firstSegment = path.split("/").firstOrNull { it.isNotEmpty() }
    return if (firstSegment != null) "$host/$firstSegment" else host
}

fun textTitle(subject: String?, text: String): String =
    if (!subject.isNullOrBlank()) subject.trim() else autoTitle(text)

fun capText(text: String): Pair<String, Boolean> =
    if (text.length > MAX_TEXT_CHARS) text.take(MAX_TEXT_CHARS) to true else text to false

fun isMeaningfulImageName(displayName: String?): Boolean {
    if (displayName.isNullOrBlank()) return false
    val base = displayName.trim().let { if (it.contains(".")) it.substringBeforeLast(".") else it }
    if (base.isBlank()) return false
    if (UUID_REGEX.matches(base)) return false
    val lower = base.lowercase()
    if (lower.startsWith("img_") && lower.length > 4 && lower[4].isDigit()) return false
    if (lower.startsWith("pxl_") && lower.length > 4 && lower[4].isDigit()) return false
    if (lower.startsWith("screenshot_") && lower.length > 11 && lower[11].isDigit()) return false
    return true
}

fun imageTitle(caption: String?, displayName: String?, fallbackDate: String): String {
    val line = caption?.lines()?.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (line.isNotEmpty()) return line
    if (!displayName.isNullOrBlank() && isMeaningfulImageName(displayName)) {
        val trimmed = displayName.trim()
        return if (trimmed.contains(".")) trimmed.substringBeforeLast(".") else trimmed
    }
    return "Image · $fallbackDate"
}

fun linkHost(url: String): String {
    val withoutScheme = url.substringAfter("://", url)
    return withoutScheme.substringBefore("/").substringBefore("?").substringBefore("#")
        .substringAfterLast("@").substringBefore(":")
        .let { if (it.startsWith("www.", ignoreCase = true)) it.substring(4) else it }
        .lowercase()
}
