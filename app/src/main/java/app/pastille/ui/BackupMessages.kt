package app.pastille.ui

// The toasts for Import / Export snippets. Plain words, counted properly, and never an exception's
// own text: "Import failed: Unexpected JSON token at offset 0" told nobody what to do next.

private fun snippets(n: Int): String = if (n == 1) "1 snippet" else "$n snippets"

/** "Exported 12 snippets", "Exported 12 snippets · 1 image isn't included". */
fun exportMessage(exported: Int, skippedImages: Int): String {
    val head = "Exported ${snippets(exported)}"
    return when (skippedImages) {
        0 -> head
        1 -> "$head · 1 image isn't included"
        else -> "$head · $skippedImages images aren't included"
    }
}

/** "Imported 1 snippet", "Imported 8 snippets"; duplicates are skipped, so 0 means nothing new. */
fun importMessage(imported: Int): String =
    if (imported == 0) "No new snippets to import" else "Imported ${snippets(imported)}"

const val EXPORT_FAILED = "Couldn't save the export. Try another place."
const val IMPORT_UNREADABLE = "Couldn't open that file."
const val IMPORT_NOT_A_BACKUP = "That file isn't a Pastille export."
