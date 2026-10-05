package app.pastille.ui

import app.pastille.data.ImportResult
import app.pastille.data.SnippetRepository

private fun count(n: Int, one: String, many: String = "${one}s"): String = if (n == 1) "1 $one" else "$n $many"

/** The snackbar while the picker's images are saved: "Adding 6 stickers…". */
fun addingStickersMessage(requested: Int): String =
    "Adding ${count(minOf(requested, SnippetRepository.MAX_IMPORT), "sticker")}…"

/**
 * The snackbar once they are: "Added 6 stickers", "Added 4 stickers · 2 moved from snippets",
 * "Added 20 of 34 stickers", "Couldn't add 1 image".
 */
fun stickerImportMessage(result: ImportResult): String {
    val added = result.created.size
    val moved = result.converted.size
    val parts = buildList {
        when {
            result.requested > SnippetRepository.MAX_IMPORT && added > 0 ->
                add("Added $added of ${result.requested} stickers")
            added > 0 -> add("Added ${count(added, "sticker")}")
        }
        if (moved > 0) add("$moved moved from snippets")
        if (result.alreadyThere.isNotEmpty()) add("${result.alreadyThere.size} already in Pastille")
        if (result.failed.isNotEmpty()) add("Couldn't add ${count(result.failed.size, "image")}")
    }
    return parts.joinToString(" · ").ifEmpty { "No stickers added" }
}
