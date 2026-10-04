package app.pastille.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

sealed interface HostRead {
    data class Text(val text: String, val fromSelection: Boolean) : HostRead
    data object Empty : HostRead
    data object Sensitive : HostRead
    data object Unavailable : HostRead
}

sealed interface ClipRead {
    data class Text(val text: String, val sensitive: Boolean) : ClipRead
    data object Empty : ClipRead
}

enum class AddSource {
    Field,
    Clipboard,
}

data class AddSources(
    val field: HostRead,
    val clip: ClipRead,
)

data class StatusStrip(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val key: Long = System.nanoTime(),
)

fun isSensitiveField(inputType: Int, imeOptions: Int): Boolean {
    if (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return true
    return when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_TEXT -> {
            when (inputType and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                -> true
                else -> false
            }
        }
        InputType.TYPE_CLASS_NUMBER ->
            inputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else -> false
    }
}

fun previewOf(text: String, maxLines: Int = 2): String {
    val joined = text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(maxLines)
        .joinToString("\n")
    return if (joined.length > 200) joined.take(200) + "…" else joined
}

fun displayTitle(title: String, text: String): String {
    if (title.isNotBlank()) return title
    val first = text.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (first.isEmpty()) return ""
    return if (first.length > 40) first.take(40) + "…" else first
}
