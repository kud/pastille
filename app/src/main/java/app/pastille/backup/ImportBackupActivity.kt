package app.pastille.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import app.pastille.PastilleApplication
import app.pastille.data.SnippetRepository
import app.pastille.ui.addOneMessage
import app.pastille.ui.addOneQuestion
import app.pastille.ui.itemPhrase
import app.pastille.ui.IMPORT_NOT_A_BACKUP
import app.pastille.ui.IMPORT_UNREADABLE
import app.pastille.ui.importMessage
import app.pastille.ui.importQuestion
import app.pastille.ui.theme.PastilleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Opens a backup someone sent: Quick Share's "Open" on a received file, a file manager's open-with,
 * or a share of the file. One confirmation, then the same import as the app's menu, which skips
 * what's already here. A one-item archive ("Send to another phone") asks about that item by name,
 * and says so at once when it is already here. Matched by the standard ZIP types in the manifest; whether it is really a
 * Pastille backup is decided by its contents, never its name.
 */
class ImportBackupActivity : ComponentActivity() {

    private var pending by mutableStateOf<Pending?>(null)

    // [single] is set for a one-item archive ("Send to another phone"): its own question, its own toast.
    private class Pending(
        val file: File,
        val counts: BackupArchive.Counts,
        val single: String? = null,
        val preview: String? = null,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = incomingUri(intent)
        if (uri == null) {
            finishWith(IMPORT_UNREADABLE)
            return
        }
        setContent {
            PastilleTheme {
                pending?.let { found ->
                    val onImport = {
                        pending = null
                        importInBackground(this, found.file, found.single)
                        finish()
                    }
                    val onCancel = {
                        found.file.delete()
                        finish()
                    }
                    if (found.single != null) {
                        AddOneDialog(phrase = found.single, preview = found.preview, onAdd = onImport, onCancel = onCancel)
                    } else {
                        ImportConfirmDialog(counts = found.counts, onImport = onImport, onCancel = onCancel)
                    }
                }
            }
        }
        lifecycleScope.launch {
            val file = runCatching { BackupFiles.copyIn(this@ImportBackupActivity, uri) }.getOrNull()
            if (file == null) {
                finishWith(IMPORT_UNREADABLE)
                return@launch
            }
            val contents = runCatching { BackupFiles.read(file) }.getOrNull()
            if (contents == null) {
                file.delete()
                finishWith(IMPORT_NOT_A_BACKUP)
                return@launch
            }
            val single = BackupArchive.single(contents)
            if (single == null) {
                pending = Pending(file, BackupArchive.summary(contents))
                return@launch
            }
            val record = single.record
            val phrase = itemPhrase(record.title, record.text, record.isImage, record.sticker)
            val here = runCatching {
                withContext(Dispatchers.IO) { SnippetRepository.forContext(this@ImportBackupActivity).holds(record) }
            }.getOrDefault(false)
            if (here) {
                file.delete()
                finishWith(addOneMessage(phrase, added = false))
                return@launch
            }
            val preview = record.text.takeIf { !record.isImage && it.trim() != record.title.trim() }
            pending = Pending(file, BackupArchive.summary(contents), phrase, preview)
        }
    }

    private fun finishWith(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun incomingUri(intent: Intent): Uri? = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }
}

/** Imports on the application's scope, so closing the screen can't cut it short, then says what came in. */
fun importInBackground(context: Context, file: File, single: String? = null) {
    val app = context.applicationContext as PastilleApplication
    app.backgroundScope.launch {
        val message = runCatching { BackupFiles.importFile(app, file) }
            .fold(
                onSuccess = { counts ->
                    if (single == null) {
                        importMessage(counts)
                    } else {
                        addOneMessage(single, added = counts.snippets + counts.images + counts.stickers > 0)
                    }
                },
                onFailure = { IMPORT_NOT_A_BACKUP },
            )
        withContext(Dispatchers.Main) { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
    }
}

/** The one-item question: Add “Wi-Fi at the cottage” to Pastille? */
@Composable
fun AddOneDialog(phrase: String, preview: String?, onAdd: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(addOneQuestion(phrase)) },
        text = preview?.let { { Text(it, maxLines = 6, overflow = TextOverflow.Ellipsis) } },
        confirmButton = { TextButton(onClick = onAdd) { Text("Add") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
fun ImportConfirmDialog(
    counts: BackupArchive.Counts,
    onImport: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Import this backup?") },
        text = { Text(importQuestion(counts)) },
        confirmButton = { TextButton(onClick = onImport) { Text("Import") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
