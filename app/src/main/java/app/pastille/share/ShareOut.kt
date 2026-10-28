package app.pastille.share

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.pastille.backup.BackupArchive
import app.pastille.backup.ImportBackupActivity
import app.pastille.images.ImageStore
import app.pastille.images.fileProviderUri
import app.pastille.images.mimeTypeForFile
import app.pastille.model.SnippetRecord
import java.io.File

// Sharing out of Pastille goes through Android's own share sheet, where Quick Share, Bluetooth,
// messaging apps and the rest appear by themselves. Pastille sends nothing over the network itself.

/** Opens the share sheet for one snippet: its text, or its image file with a read grant. False if the image is gone. */
fun shareSnippet(context: Context, snippet: SnippetRecord): Boolean {
    val image = snippet.imageFile
    if (image == null) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, snippet.text)
            if (snippet.title.isNotBlank()) putExtra(Intent.EXTRA_TITLE, snippet.title)
        }
        startChooser(context, send)
        return true
    }
    val file = ImageStore.forContext(context).fileFor(image)
    if (!file.isFile) return false
    shareFile(context, fileProviderUri(context, file), mimeTypeForFile(image), snippet.title)
    return true
}

/** Opens the share sheet for a backup archive built in cache/shared/. */
fun shareBackup(context: Context, file: File) {
    shareFile(context, fileProviderUri(context, file), BackupArchive.MIME, file.name)
}

private fun shareFile(context: Context, uri: Uri, mime: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        if (title.isNotBlank()) putExtra(Intent.EXTRA_TITLE, title)
        // The ClipData carries the grant to the chooser's preview and on to the chosen target.
        clipData = ClipData.newUri(context.contentResolver, title.ifBlank { "Pastille" }, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startChooser(context, send)
}

private fun startChooser(context: Context, send: Intent) {
    val chooser = Intent.createChooser(send, null).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Pastille's own targets would only save the thing back where it came from.
        putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(
                ComponentName(context, ShareActivity::class.java),
                ComponentName(context, ImportBackupActivity::class.java),
            ),
        )
    }
    context.startActivity(chooser)
}
