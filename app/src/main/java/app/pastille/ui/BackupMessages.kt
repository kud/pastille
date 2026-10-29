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

/**
 * How one received item is named in a sentence: its title or first line in quotes, cut at 40
 * characters, or "this sticker" / "this image" when an image has no title.
 */
fun itemPhrase(title: String, text: String, isImage: Boolean, sticker: Boolean): String {
    val name = title.ifBlank { if (isImage) "" else text }.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    if (name.isEmpty()) return if (sticker) "this sticker" else if (isImage) "this image" else "this snippet"
    val cut = if (name.length > 40) name.take(39).trimEnd() + "…" else name
    return "“$cut”"
}

/** The one-item confirmation's title: Add “Wi-Fi at the cottage” to Pastille? */
fun addOneQuestion(phrase: String): String = "Add $phrase to Pastille?"

/** After a one-item import: “Wi-Fi at the cottage” added, or already here. */
fun addOneMessage(phrase: String, added: Boolean): String =
    if (added) "Added ${phrase.replaceFirstChar { it.uppercase() }}" else "${phrase.replaceFirstChar { it.uppercase() }} is already in Pastille"

const val SEND_FAILED = "Couldn't prepare it to send."

const val EXPORT_FAILED = "Couldn't save the backup. Try another place."
const val IMPORT_UNREADABLE = "Couldn't open that file."
const val IMPORT_NOT_A_BACKUP = "That file isn't a Pastille backup."
const val FOLDER_UNREADABLE = "Couldn't open that folder. Try another one."
const val SHARE_FAILED = "Couldn't prepare the backup to share."

/**
 * Settings › Backup's status line: what file, and when this phone last synced with it, or what went
 * wrong and the one thing to do about it. [provider] is the storage app's name
 * ([app.pastille.backup.SyncDiagnosis.providerLabel]), so a pCloud problem never says "Drive".
 */
fun backupStatus(
    name: String,
    lastAt: Long?,
    problem: app.pastille.settings.BackupProblem?,
    now: Long,
    provider: String = "the storage app",
    choose: String = "a file",
): String {
    // "The storage app" opens a sentence; a brand keeps its own case ("pCloud", never "PCloud").
    val who = if (provider.first().isLowerCase() && ' ' in provider) provider.replaceFirstChar { it.uppercase() } else provider
    return when (problem) {
        app.pastille.settings.BackupProblem.Access -> "Can't reach $name. Tap to choose $choose again."
        app.pastille.settings.BackupProblem.Write -> "The last sync with $name failed. Tap to choose $choose again."
        app.pastille.settings.BackupProblem.Read -> "$name · Waiting for $provider"
        app.pastille.settings.BackupProblem.NotKept ->
            "$who didn't keep permission to $name, so syncing stops when the phone restarts. Tap to choose it again."
        app.pastille.settings.BackupProblem.ReadOnly ->
            "$who only lets Pastille read $name. Tap to create a new file instead."
        app.pastille.settings.BackupProblem.CantOverwrite ->
            "$who can't overwrite $name, so Pastille left it as it was. Tap to choose a file somewhere else."
        app.pastille.settings.BackupProblem.Mismatch ->
            "$who didn't save $name as Pastille wrote it. Pastille will try again after the next change."
        null -> if (lastAt == null) {
            "$name · Not synced yet"
        } else {
            val ago = relativeTime(lastAt, now)
            val phrase = if (ago == "Just now" || ago == "Yesterday") ago.lowercase() else ago
            "$name · Synced $phrase"
        }
    }
}

const val SYNC_NOTE =
    "To sync two phones, choose the same folder on both. Pastille keeps one file in it, " +
        app.pastille.backup.SyncFolder.FILE_NAME + ", and never makes a second. " +
        "On Google Drive, export a backup once, then sync that single file on both."

/** The status line's name for the synced place: the folder, or the file marked as a single file. */
fun syncPlaceName(folderName: String?, fileName: String, inFolder: Boolean): String =
    if (inFolder) folderName ?: "Sync folder" else "$fileName · single file"

/**
 * When the folder holds more than one file with the sync name: how many, and which one is used, by
 * its modified time, so it can be checked in the storage app. Null when there is only one.
 */
fun duplicatesNote(
    matches: Int,
    modified: Long?,
    zone: java.util.TimeZone = java.util.TimeZone.getDefault(),
): String? {
    if (matches < 2) return null
    val which = modified?.let {
        val format = java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.ENGLISH)
        format.timeZone = zone
        "using the newest, " + format.format(java.util.Date(it))
    } ?: "using the newest"
    return "$matches sync files here · $which"
}

const val PRIVACY_NOTE =
    "Your snippets, stickers and images stay on your phone unless you export, share or back them up yourself."
