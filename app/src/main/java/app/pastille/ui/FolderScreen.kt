package app.pastille.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pastille.data.SnippetRepository
import app.pastille.images.ImageStore
import app.pastille.model.CategoryRecord
import app.pastille.model.SnippetRecord
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    folderId: Long,
    repository: SnippetRepository,
    onBack: () -> Unit,
    onCreate: (Long?) -> Unit,
    onEdit: (SnippetRecord) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val categories: List<CategoryRecord>? by remember { repository.observeCategories() }
        .collectAsStateWithLifecycle(initialValue = null)
    val snippets by remember { repository.observeSnippets() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val loaded = categories ?: return
    val folder = loaded.firstOrNull { it.id == folderId }
    LaunchedEffect(folder) {
        if (folder == null) onBack()
    }
    if (folder == null) return

    fun copySnippet(snippet: SnippetRecord) {
        val imageFile = snippet.imageFile
        if (snippet.isImage && imageFile != null) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                ImageStore.forContext(context).fileFor(imageFile),
            )
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(
                    ClipData.newUri(context.contentResolver, snippet.title.ifBlank { "Image" }, uri),
                )
        } else {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(
                    ClipData.newPlainText(snippet.title.ifBlank { "Snippet" }, snippet.text),
                )
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            scope.launch { snackbarHostState.showSnackbar("Copied") }
        }
    }

    fun togglePin(snippet: SnippetRecord) {
        scope.launch {
            repository.upsert(snippet.copy(pinned = !snippet.pinned))
        }
    }

    fun deleteSnippet(snippet: SnippetRecord) {
        scope.launch {
            repository.delete(snippet.id)
            val result = snackbarHostState.showSnackbar(
                message = "Deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                repository.restore(snippet)
            }
        }
    }

    if (showRename) {
        CategoryNameDialog(
            initialName = folder.name,
            title = "Rename folder",
            confirmLabel = "Rename",
            onConfirm = { name ->
                try {
                    repository.renameCategory(folder.id, name)
                    null
                } catch (e: IllegalArgumentException) {
                    e.message?.replace("category", "folder")
                }
            },
            onDismiss = { showRename = false },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteCategory(folder.id)
                            showDelete = false
                            onBack()
                        }
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Delete folder?") },
            text = { Text("Snippets in ${folder.name} move to the top level.") },
        )
    }

    val folderSnippets = remember(snippets, folderId) {
        snippets.filter { it.categoryId == folderId }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename folder") },
                            onClick = {
                                showMenu = false
                                showRename = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete folder") },
                            onClick = {
                                showMenu = false
                                showDelete = true
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onCreate(folderId) },
                text = { Text("New snippet") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (folderSnippets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nothing in ${folder.name} yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                itemsIndexed(folderSnippets, key = { _, snippet -> snippet.id }) { index, snippet ->
                    if (index > 0) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    SnippetRow(
                        snippet = snippet,
                        categoryName = null,
                        onEdit = { onEdit(snippet) },
                        onCopy = { copySnippet(snippet) },
                        onTogglePin = { togglePin(snippet) },
                        onDelete = { deleteSnippet(snippet) },
                    )
                }
            }
        }
    }
}
