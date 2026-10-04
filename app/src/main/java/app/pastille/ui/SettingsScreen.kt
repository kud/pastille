package app.pastille.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pastille.settings.PastilleSettings
import app.pastille.tile.ImeSwitcher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { PastilleSettings.forContext(context) }
    var enabled by remember { mutableStateOf(settings.returnToPreviousKeyboard) }
    val clipboard = LocalClipboardManager.current
    val command = "adb shell pm grant app.pastille android.permission.WRITE_SECURE_SETTINGS"
    var granted by remember { mutableStateOf(ImeSwitcher.hasWriteSecureSettings(context)) }
    LifecycleResumeEffect(Unit) {
        granted = ImeSwitcher.hasWriteSecureSettings(context)
        onPauseOrDispose { }
    }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text("Return to previous keyboard after inserting") },
                supportingContent = {
                    Text("After you insert a snippet or a screenshot, switch back to the keyboard you were using.")
                },
                trailingContent = { Switch(checked = enabled, onCheckedChange = null) },
                modifier = Modifier.toggleable(
                    value = enabled,
                    role = Role.Switch,
                    onValueChange = {
                        enabled = it
                        settings.returnToPreviousKeyboard = it
                    },
                ),
            )
            Text(
                text = "Quick Settings tile",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            )
            Text(
                text = "Add the Pastille tile to Quick Settings to change keyboard in one tap. Without extra permission it opens the keyboard picker. To switch straight to Pastille and back, grant one permission over adb, once:",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ListItem(
                headlineContent = { Text(command, fontFamily = FontFamily.Monospace) },
                trailingContent = {
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(command)) },
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy command")
                    }
                },
            )
            ListItem(
                headlineContent = { Text("One-tap switching") },
                supportingContent = {
                    Text(
                        if (granted) {
                            "Granted"
                        } else {
                            "Not granted — the tile opens the keyboard picker"
                        },
                    )
                },
            )
        }
    }
}
