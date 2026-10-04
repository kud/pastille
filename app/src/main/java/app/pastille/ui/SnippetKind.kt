package app.pastille.ui

import app.pastille.ime.displayTitle
import app.pastille.share.autoTitle
import app.pastille.share.findSingleUrl
import app.pastille.share.linkTitle

enum class SnippetKind { Text, Link, Image }

fun linkOnly(text: String): String? =
    findSingleUrl(text)?.takeIf { url -> text.replace(url, "").isBlank() }

fun snippetKind(isImage: Boolean, text: String): SnippetKind = when {
    isImage -> SnippetKind.Image
    linkOnly(text) != null -> SnippetKind.Link
    else -> SnippetKind.Text
}

// A bare link whose title was generated from the URL reads better as its domain.
fun rowTitle(title: String, text: String): String {
    val url = linkOnly(text)
    if (url != null && (title.isBlank() || title.startsWith(autoTitle(text)))) {
        return linkTitle(subject = null, text = text, url = url)
    }
    return displayTitle(title, text)
}
