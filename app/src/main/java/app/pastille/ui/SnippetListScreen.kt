package app.pastille.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pastille.crash.CrashLog
import app.pastille.data.SnippetRepository
import app.pastille.ime.ScreenshotReader
import app.pastille.model.SnippetRecord
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetListScreen(
    repository: SnippetRepository,
    onCreate: () -> Unit,
    onEdit: (SnippetRecord) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember { repository.observeSnippets() }
    val snippets by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by remember { mutableStateOf("") }
    var refreshTick by remember { mutableStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showCrashDialog by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshTick++ }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(repository.exportJson().toByteArray())
                    } ?: error("Could not open $uri")
                    "Exported ${snippets.size} snippets"
                }.onSuccess { toast(context, it) }
                    .onFailure { toast(context, "Export failed: ${it.message}") }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val raw = context.contentResolver.openInputStream(uri)?.use {
                        it.bufferedReader().readText()
                    } ?: error("Could not open $uri")
                    repository.importJson(raw)
                }.onSuccess { toast(context, "Imported $it snippets") }
                    .onFailure { toast(context, "Import failed: ${it.message}") }
            }
        }
    }

    val visible = remember(snippets, query) {
        if (query.isBlank()) {
            snippets
        } else {
            snippets.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.text.contains(query, ignoreCase = true)
            }
        }
    }

    if (showCrashDialog) {
        val entries = remember(showCrashDialog) { CrashLog.forContext(context).entries() }
        val clipboard = LocalClipboardManager.current
        val crashText = remember(entries) { entries.joinToString("\n\n———\n\n") }
        AlertDialog(
            onDismissRequest = { showCrashDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(crashText))
                        showCrashDialog = false
                    },
                    enabled = entries.isNotEmpty(),
                ) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCrashDialog = false }) {
                    Text("Close")
                }
            },
            title = { Text("Last crash") },
            text = {
                if (entries.isEmpty()) {
                    Text("No crashes recorded.")
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    ) {
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

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Pastille") },
                actions = {
                    IconButton(onClick = { exportLauncher.launch("pastille-snippets.json") }) {
                        Icon(Icons.Filled.Upload, contentDescription = "Export snippets")
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                        Icon(Icons.Filled.Download, contentDescription = "Import snippets")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Last crash") },
                            onClick = {
                                showMenu = false
                                showCrashDialog = true
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                text = { Text("New snippet") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "onboarding") {
                OnboardingCard(key = refreshTick)
                Spacer(Modifier.height(4.dp))
            }
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search snippets") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                )
            }
            if (snippets.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "No snippets yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = onCreate) {
                            Text("Add your first snippet")
                        }
                    }
                }
            } else if (visible.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "No snippets match “$query”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(visible, key = { it.id }) { snippet ->
                SnippetRow(
                    snippet = snippet,
                    onEdit = { onEdit(snippet) },
                    onTogglePin = {
                        scope.launch {
                            repository.upsert(snippet.copy(pinned = !snippet.pinned))
                        }
                    },
                    onDelete = {
                        scope.launch { repository.delete(snippet.id) }
                    },
                )
            }
        }
    }
}

@Composable
private fun OnboardingCard(key: Int) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    val imeEnabled = remember(key) { isPastilleEnabled(context) }
    val current = remember(key) { isPastilleCurrent(context) }
    val photosGranted = remember(key) { ScreenshotReader.hasPermission(context) }
    val partialOnly = remember(key) { ScreenshotReader.hasOnlyPartialAccess(context) }

    if (imeEnabled && current && photosGranted) return

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Get set up", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (!imeEnabled) {
                SetupRow(
                    text = "1. Enable Pastille in system settings",
                    button = "Enable",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                )
            }
            if (!current) {
                SetupRow(
                    text = "2. Switch to the Pastille keyboard",
                    button = "Switch",
                    onClick = {
                        context.getSystemService(InputMethodManager::class.java)
                            ?.showInputMethodPicker()
                    },
                )
            }
            if (!photosGranted || partialOnly) {
                if (partialOnly) {
                    Text(
                        text = "You granted access to selected photos only, so Pastille " +
                            "cannot see your latest screenshot. Grant full photo access " +
                            "to fix this.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                SetupRow(
                    text = "3. Allow photo access for screenshots",
                    button = "Allow",
                    onClick = {
                        permissionLauncher.launch(photoPermission())
                    },
                )
            }
        }
    }
}

@Composable
private fun SetupRow(text: String, button: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onClick) { Text(button) }
    }
}

@Composable
private fun SnippetRow(
    snippet: SnippetRecord,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (snippet.title.isNotBlank()) {
                    Text(
                        text = snippet.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = snippet.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onTogglePin) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = if (snippet.pinned) "Unpin" else "Pin",
                    tint = if (snippet.pinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete snippet")
            }
        }
    }
}

private fun isPastilleEnabled(context: Context): Boolean {
    val manager = context.getSystemService(InputMethodManager::class.java) ?: return false
    return manager.enabledInputMethodList.any { it.packageName == context.packageName }
}

private fun isPastilleCurrent(context: Context): Boolean {
    val current = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.DEFAULT_INPUT_METHOD,
    ) ?: return false
    return current.contains(context.packageName)
}

private fun photoPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
