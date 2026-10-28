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
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import app.pastille.PastilleApplication
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
 * what's already here. Matched by the standard ZIP types in the manifest; whether it is really a
 * Pastille backup is decided by its contents, never its name.
 */
class ImportBackupActivity : ComponentActivity() {

    private var pending by mutableStateOf<Pending?>(null)

    private class Pending(val file: File, val counts: BackupArchive.Counts)

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
                    ImportConfirmDialog(
                        counts = found.counts,
                        onImport = {
                            pending = null
                            importInBackground(this, found.file)
                            finish()
                        },
                        onCancel = {
                            found.file.delete()
                            finish()
                        },
                    )
                }
            }
        }
        lifecycleScope.launch {
            val file = runCatching { BackupFiles.copyIn(this@ImportBackupActivity, uri) }.getOrNull()
            if (file == null) {
                finishWith(IMPORT_UNREADABLE)
                return@launch
            }
            val counts = runCatching { BackupFiles.peek(file) }.getOrNull()
            if (counts == null) {
                file.delete()
                finishWith(IMPORT_NOT_A_BACKUP)
                return@launch
            }
            pending = Pending(file, counts)
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
fun importInBackground(context: Context, file: File) {
    val app = context.applicationContext as PastilleApplication
    app.backgroundScope.launch {
        val message = runCatching { BackupFiles.importFile(app, file) }
            .fold(onSuccess = { importMessage(it) }, onFailure = { IMPORT_NOT_A_BACKUP })
        withContext(Dispatchers.Main) { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
    }
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
