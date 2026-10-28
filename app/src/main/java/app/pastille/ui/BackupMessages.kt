package app.pastille.ui

import app.pastille.backup.BackupArchive

// The words for Export, Import, Share and automatic backup. Plain words, counted properly, and never
// an exception's own text: "Import failed: Unexpected JSON token at offset 0" told nobody what to do.

private fun count(n: Int, one: String, many: String): String = if (n == 1) "1 $one" else "$n $many"

/** "12 snippets, 3 stickers and 2 images"; parts at zero are left out, "nothing" when all are. */
fun contentsPhrase(snippets: Int, stickers: Int, images: Int): String {
    val parts = buildList {
        if (snippets > 0) add(count(snippets, "snippet", "snippets"))
        if (stickers > 0) add(count(stickers, "sticker", "stickers"))
        if (images > 0) add(count(images, "image", "images"))
    }
    return when (parts.size) {
        0 -> "nothing"
        1 -> parts[0]
        else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }
}

fun contentsPhrase(counts: BackupArchive.Counts): String =
    contentsPhrase(counts.snippets, counts.stickers, counts.images)

/** "Exported 12 snippets and 3 stickers". */
fun exportMessage(written: BackupArchive.Written): String =
    "Exported " + contentsPhrase(written.snippets, written.stickers, written.images)

/** "Imported 8 snippets"; what's already here is skipped, so nothing new says so. */
fun importMessage(imported: BackupArchive.Counts): String =
    if (imported.snippets + imported.images + imported.stickers == 0) {
        "Nothing new to import"
    } else {
        "Imported " + contentsPhrase(imported)
    }

/** The import confirmation's text, for a file shared to Pastille or the backup file. */
fun importQuestion(contents: BackupArchive.Counts): String =
    "This backup holds ${contentsPhrase(contents)}. Anything already in Pastille is skipped."

const val EXPORT_FAILED = "Couldn't save the backup. Try another place."
const val IMPORT_UNREADABLE = "Couldn't open that file."
const val IMPORT_NOT_A_BACKUP = "That file isn't a Pastille backup."
const val SHARE_FAILED = "Couldn't prepare the backup to share."

/**
 * Settings › Backup's status line: what file, and when this phone last synced with it, or what went
 * wrong and the one thing to do about it.
 */
fun backupStatus(
    name: String,
    lastAt: Long?,
    problem: app.pastille.settings.BackupProblem?,
    now: Long,
): String = when (problem) {
    app.pastille.settings.BackupProblem.Access -> "Can't reach $name. Tap to choose a file again."
    app.pastille.settings.BackupProblem.Write -> "The last sync with $name failed. Tap to choose a file again."
    app.pastille.settings.BackupProblem.Read -> "$name · Waiting for Drive"
    null -> if (lastAt == null) {
        "$name · Not synced yet"
    } else {
        val ago = relativeTime(lastAt, now)
        val phrase = if (ago == "Just now" || ago == "Yesterday") ago.lowercase() else ago
        "$name · Synced $phrase"
    }
}

const val SYNC_NOTE =
    "To sync two phones, both choose the same file: create it on one, then use the existing file on the other."

const val PRIVACY_NOTE =
    "Your snippets, stickers and images stay on your phone unless you export, share or back them up yourself."
