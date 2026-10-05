package app.pastille.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pastille.data.SnippetRepository
import app.pastille.model.CategoryRecord
import app.pastille.model.SnippetRecord
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinScreen(repository: SnippetRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val binned by remember { repository.observeBin() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by remember { repository.observeCategories() }
        .collectAsStateWithLifecycle(initialValue = emptyList<CategoryRecord>())
    val folderNames = remember(categories) { categories.associate { it.id to it.name } }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<SnippetRecord?>(null) }
    var previewing by remember { mutableStateOf<SnippetRecord?>(null) }
    val now = remember(binned) { System.currentTimeMillis() }

    BackHandler(onBack = onBack)

    fun restore(snippet: SnippetRecord) {
        scope.launch {
            repository.restoreFromBin(snippet.id)
            val result = snackbarHostState.showSnackbar(
                message = "Restored",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                // Back into the bin with its original countdown.
                repository.delete(snippet.id, now = snippet.deletedAt ?: System.currentTimeMillis())
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Bin") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Empty bin") },
                            leadingIcon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
                            enabled = binned.isNotEmpty(),
                            onClick = {
                                menuOpen = false
                                confirmEmpty = true
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "explainer") {
                Text(
                    text = "Snippets are deleted forever after 30 days.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            if (binned.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "The bin is empty",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
            itemsIndexed(binned, key = { _, snippet -> snippet.id }) { index, snippet ->
                Column(modifier = Modifier.animateItem()) {
                    if (index > 0) HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    BinRow(
                        snippet = snippet,
                        folderName = snippet.categoryId?.let(folderNames::get),
                        now = now,
                        onOpen = { previewing = snippet },
                        onRestore = { restore(snippet) },
                        onDeleteForever = { confirmDelete = snippet },
                    )
                }
            }
        }
    }

    if (confirmEmpty) {
        val count = binned.size
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(if (count == 1) "Delete 1 snippet forever?" else "Delete $count snippets forever?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmEmpty = false
                        scope.launch { repository.emptyBin() }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Empty bin") }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
        )
    }

    confirmDelete?.let { snippet ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete forever?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = null
                        scope.launch { repository.deleteForever(snippet.id) }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete forever") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    previewing?.let { snippet ->
        AlertDialog(
            onDismissRequest = { previewing = null },
            title = { Text(rowTitle(snippet.title, snippet.text).ifBlank { "Image" }) },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(
                            text = if (snippet.isImage) "Image snippet" else snippet.text,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    previewing = null
                    restore(snippet)
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { previewing = null }) { Text("Close") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun BinRow(
    snippet: SnippetRecord,
    folderName: String?,
    now: Long,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    val currentOnRestore by rememberUpdatedState(onRestore)
    val currentOnDeleteForever by rememberUpdatedState(onDeleteForever)
    var width by remember { mutableFloatStateOf(0f) }
    val stateHolder = remember { arrayOfNulls<androidx.compose.material3.SwipeToDismissBoxState>(1) }
    fun past(direction: SwipeToDismissBoxValue): Boolean {
        val offset = stateHolder[0]?.let { runCatching { it.requireOffset() }.getOrNull() } ?: 0f
        return swipePastThreshold(direction, offset, width)
    }
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled && past(value)) {
                if (value == SwipeToDismissBoxValue.StartToEnd) currentOnRestore() else currentOnDeleteForever()
            }
            // Both actions snap back: restore removes the row through the flow, delete asks first.
            value == SwipeToDismissBoxValue.Settled
        },
        positionalThreshold = { distance ->
            distance * swipeFraction(stateHolder[0]?.dismissDirection ?: SwipeToDismissBoxValue.Settled)
        },
    )
    stateHolder[0] = swipeState
    SwipeToDismissBox(
        state = swipeState,
        modifier = Modifier.onSizeChanged { width = it.width.toFloat() },
        backgroundContent = {
            val direction = swipeState.dismissDirection
            val restoring = direction == SwipeToDismissBoxValue.StartToEnd
            Box(
                modifier = Modifier.fillMaxSize().background(
                    when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.secondaryContainer
                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                        SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                ).padding(horizontal = 24.dp),
                contentAlignment = if (restoring) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        imageVector = if (restoring) Icons.Rounded.Restore else Icons.Rounded.DeleteForever,
                        contentDescription = null,
                        tint = if (restoring) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                    )
                }
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .heightIn(min = 72.dp)
                .combinedClickable(onClick = onOpen, onLongClick = onDeleteForever)
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rowTitle(snippet.title, snippet.text).ifBlank { "Image" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!snippet.isImage && snippet.text.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = snippet.text.replace('\n', ' '),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                BinMetaRow(deletedAt = snippet.deletedAt ?: now, now = now, folderName = folderName)
            }
            IconButton(onClick = onRestore) {
                Icon(
                    Icons.Rounded.Restore,
                    contentDescription = "Restore",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BinMetaRow(deletedAt: Long, now: Long, folderName: String?) {
    val days = binDaysLeft(deletedAt, now)
    val countdownColour = if (binExpiresSoon(days)) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.HourglassEmpty,
            contentDescription = null,
            tint = countdownColour,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(binDaysLeftLabel(days), style = MaterialTheme.typography.labelSmall, color = countdownColour)
        if (folderName != null) {
            Text(
                " · from ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(2.dp))
            Text(folderName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
