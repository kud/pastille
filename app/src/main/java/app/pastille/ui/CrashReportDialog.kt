package app.pastille.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.pastille.crash.CrashLog

/** The recorded crashes, newest first, selectable and copyable whole for a bug report. */
@Composable
internal fun CrashReportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val entries = remember { CrashLog.forContext(context).entries() }
    val clipboard = LocalClipboardManager.current
    val crashText = remember(entries) { entries.joinToString("\n\n———\n\n") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(crashText))
                    onDismiss()
                },
                enabled = entries.isNotEmpty(),
            ) {
                Text("Copy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Last crash report") },
        text = {
            if (entries.isEmpty()) {
                Text("No crashes recorded.")
            } else {
                Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(
                            text = crashText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        },
    )
}
