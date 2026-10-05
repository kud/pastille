package app.pastille.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.pastille.ime.ImageSourceReader
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import app.pastille.ime.PastilleMotion
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pastille.R
import app.pastille.crash.CrashLog
import app.pastille.data.SnippetRepository
import app.pastille.images.ImageStore
import app.pastille.images.ImageThumbnail
import app.pastille.model.CategoryRecord
import app.pastille.model.SnippetRecord
import app.pastille.model.isMissingFolder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetListScreen(
    repository: SnippetRepository,
    onCreate: (Long?) -> Unit,
    onEdit: (SnippetRecord) -> Unit,
    onOpenSettings: () -> Unit,
    deletedSnippet: SnippetRecord? = null,
    onDeletedShown: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val flow = remember { repository.observeSnippets() }
    val snippets by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showCrashDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hasCrashReport = remember(showMenu) {
        CrashLog.forContext(context).entries().isNotEmpty()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshTick++ }

    val loadedCategories by remember { repository.observeCategories() }
        .collectAsStateWithLifecycle(initialValue = null as List<CategoryRecord>?)
    val categories = loadedCategories.orEmpty()
    var showCreateFolder by remember { mutableStateOf(false) }
    var showTryIt by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val result = repository.exportJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(result.json.toByteArray())
                    } ?: error("Could not open $uri")
                    if (result.skippedImages > 0) {
                        "Exported ${result.exported} snippets (${result.skippedImages} image snippets aren't included)"
                    } else {
                        "Exported ${result.exported} snippets"
                    }
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

    fun copySnippet(snippet: SnippetRecord) {
        val content = app.pastille.clipboard.clipContentFor(context, snippet)
        if (content == null) {
            scope.launch { snackbarHostState.showSnackbar("That image is gone") }
            return
        }
        val copied = app.pastille.clipboard.copySnippet(context, content) ?: return
        val clearAfter = copied.clearAfterSeconds
        if (clearAfter != null) {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Copied · clears in ${app.pastille.settings.shortDuration(clearAfter)}",
                    actionLabel = "Clear now",
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    app.pastille.clipboard.clearCopiedClip(context, copied)
                }
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            scope.launch { snackbarHostState.showSnackbar("Copied") }
        }
    }

    fun moveSnippet(snippet: SnippetRecord, categoryId: Long?) {
        scope.launch {
            repository.setCategory(listOf(snippet.id), categoryId)
        }
    }

    suspend fun showDeletedSnackbar(snippet: SnippetRecord) {
        val result = snackbarHostState.showSnackbar(
            message = "Deleted",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            repository.restore(snippet)
        }
    }

    fun deleteSnippet(snippet: SnippetRecord) {
        scope.launch {
            repository.delete(snippet.id)
            showDeletedSnackbar(snippet)
        }
    }

    LaunchedEffect(deletedSnippet) {
        if (deletedSnippet != null) {
            // Clearing the request restarts this effect, so the snackbar runs on the screen's scope.
            scope.launch { showDeletedSnackbar(deletedSnippet) }
            onDeletedShown()
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
    val haptics = LocalHapticFeedback.current
    var selectedTab by rememberSaveable { mutableLongStateOf(ALL_TAB) }
    var reordering by rememberSaveable { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<CategoryRecord?>(null) }
    var deletingFolder by remember { mutableStateOf<CategoryRecord?>(null) }
    val orderedFolders = remember(categories) { categories.sortedBy { it.position } }
    LaunchedEffect(loadedCategories) {
        if (isMissingFolder(loadedCategories, selectedTab.takeIf { it != ALL_TAB })) selectedTab = ALL_TAB
    }
    val selectedFolderId = selectedTab.takeIf { it != ALL_TAB }
    val shown = remember(visible, selectedTab) {
        if (selectedTab == ALL_TAB) visible else visible.filter { it.categoryId == selectedTab }
    }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) searchFocus.requestFocus() }
    var snippetDragOrder by remember(shown, reordering) { mutableStateOf(shown) }
    var folderDragOrder by remember(orderedFolders, reordering) { mutableStateOf(orderedFolders) }
    val snippetReorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = snippetDragOrder.indexOfFirst { it.id == from.key }
        val toIndex = snippetDragOrder.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            snippetDragOrder = snippetDragOrder.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }
    val categoryNames = remember(categories) { categories.associate { it.id to it.name } }

    if (showCrashDialog) {
        val entries = remember(showCrashDialog) { CrashLog.forContext(context).entries() }
        val clipboard = LocalClipboardManager.current
        val crashText = remember(entries) { entries.joinToString("\n\nâââ\n\n") }
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
            title = { Text("Last crash report") },
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

    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }

    BackHandler(enabled = !searching && (reordering || selectedTab != ALL_TAB)) {
        if (reordering) reordering = false else selectedTab = ALL_TAB
    }

    renamingFolder?.let { folder ->
        CategoryNameDialog(
            initialName = folder.name,
            title = "Rename folder",
            confirmLabel = "Rename",
            onConfirm = { name ->
                runCatching { repository.renameCategory(folder.id, name) }.exceptionOrNull()?.message
            },
            onDismiss = { renamingFolder = null },
        )
    }

    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.deleteCategory(folder.id) }
                        deletingFolder = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text("Cancel") } },
            title = { Text("Delete ?") },
            text = { Text("Snippets in  move to the top level.") },
        )
    }

    if (showTryIt) {
        TryItDialog(onDismiss = { showTryIt = false })
    }

    if (showCreateFolder) {
        CategoryNameDialog(
            initialName = "",
            title = "New folder",
            confirmLabel = "Create",
            onConfirm = { name ->
                repository.createCategory(name)
                null
            },
            onDismiss = { showCreateFolder = false },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (searching) {
                    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.statusBars)) {
                        SearchField(
                            query = query,
                            onQueryChange = { query = it },
                            onClose = {
                                searching = false
                                query = ""
                            },
                            modifier = Modifier.focusRequester(searchFocus),
                        )
                    }
                } else LargeTopAppBar(
                    title = { Wordmark(collapsedFraction = scrollBehavior.state.collapsedFraction) },
                    actions = {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search snippets")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("New folder") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    showCreateFolder = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Import snippets") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.FileDownload, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    importLauncher.launch(arrayOf("application/json"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export snippets") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.FileUpload, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    exportLauncher.launch("pastille-snippets.json")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Reorder") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.SwapVert, contentDescription = null)
                                },
                                enabled = snippets.size > 1 || orderedFolders.size > 1,
                                onClick = {
                                    showMenu = false
                                    searching = false
                                    reordering = true
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Try the keyboard") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Edit, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    showTryIt = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Keyboard setup") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Keyboard, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Settings, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onOpenSettings()
                                },
                            )
                            if (hasCrashReport) {
                                DropdownMenuItem(
                                    text = { Text("Last crash report") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.BugReport, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        showCrashDialog = true
                                    },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                if (!reordering) {
                    FloatingActionButton(onClick = { onCreate(selectedFolderId) }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "New snippet")
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (orderedFolders.isNotEmpty()) {
                    FolderTabs(
                        folders = if (reordering) folderDragOrder else orderedFolders,
                        selectedId = selectedTab,
                        reordering = reordering,
                        onSelect = { selectedTab = it },
                        onMove = { fromId, toId ->
                            val from = folderDragOrder.indexOfFirst { it.id == fromId }
                            val to = folderDragOrder.indexOfFirst { it.id == toId }
                            if (from >= 0 && to >= 0) {
                                folderDragOrder = folderDragOrder.toMutableList().apply { add(to, removeAt(from)) }
                            }
                        },
                        onDropped = {
                            scope.launch { repository.setCategoryOrder(folderDragOrder.map { it.id }) }
                        },
                        onRename = { renamingFolder = it },
                        onDelete = { deletingFolder = it },
                    )
                }
                if (reordering) {
                    ReorderBanner(onDone = { reordering = false })
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    item(key = "onboarding") {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            OnboardingCard(key = refreshTick)
                        }
                    }
                    if (shown.isEmpty()) {
                        item(key = "empty") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = when {
                                        query.isNotBlank() -> "No snippets match “$query”"
                                        snippets.isEmpty() -> "No snippets yet"
                                        else -> "Nothing in this folder yet"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(16.dp))
                                if (query.isBlank()) FilledTonalButton(onClick = { onCreate(selectedFolderId) }) {
                                    Text(if (snippets.isEmpty()) "Add your first snippet" else "Add a snippet here")
                                }
                            }
                        }
                    }
                    if (reordering) {
                        itemsIndexed(snippetDragOrder, key = { _, snippet -> snippet.id }) { index, snippet ->
                            ReorderableItem(snippetReorderState, key = snippet.id) { dragging ->
                                val elevation by animateDpAsState(if (dragging) 6.dp else 0.dp, label = "dragElevation")
                                Surface(shadowElevation = elevation, color = MaterialTheme.colorScheme.surface) {
                                    Column {
                                        if (index > 0) {
                                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                        }
                                        ReorderRow(
                                            snippet = snippet,
                                            handle = Modifier.draggableHandle(
                                                onDragStarted = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDragStopped = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    scope.launch {
                                                        repository.setSnippetOrder(shown, snippetDragOrder.map { it.id })
                                                    }
                                                },
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        itemsIndexed(shown, key = { _, snippet -> snippet.id }) { index, snippet ->
                            Column(modifier = Modifier.animateItem()) {
                                if (index > 0) {
                                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                }
                                SnippetRow(
                                    snippet = snippet,
                                    categoryName = if (selectedTab == ALL_TAB) snippet.categoryId?.let { categoryNames[it] } else null,
                                    onEdit = { onEdit(snippet) },
                                    onCopy = { copySnippet(snippet) },
                                    onDelete = { deleteSnippet(snippet) },
                                    onSwipeStartToEnd = { onEdit(snippet) },
                                    folders = orderedFolders,
                                    onMove = { moveSnippet(snippet, it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Wordmark(collapsedFraction: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_pastille_mark),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(lerp(36.dp, 26.dp, collapsedFraction)),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Pastille",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                fontSize = lerp(32.sp, 22.sp, collapsedFraction),
            ),
        )
    }
}

@Composable
private fun OnboardingCard(key: Int) {
    val context = LocalContext.current
    var photosAsked by remember { mutableStateOf(0) }
    val imeEnabled = remember(key) { isPastilleEnabled(context) }
    val photosGranted = remember(key, photosAsked) {
        ImageSourceReader.hasPermission(context) && !ImageSourceReader.hasOnlyPartialAccess(context)
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        photosAsked++
    }
    if (imeEnabled && photosGranted) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Set up Pastille", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (!imeEnabled) {
                SetupRow(
                    text = "Enable Pastille in system settings",
                    button = "Enable",
                    onClick = {
                        context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                )
            }
            if (!photosGranted) {
                SetupRow(
                    text = "Allow photo access so your images show in the keyboard",
                    button = "Allow",
                    onClick = { photoLauncher.launch(photoPermission()) },
                )
            }
        }
    }
}

@Composable
private fun TryItDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        title = { Text("Try the keyboard") },
        text = { TryItField() },
    )
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SnippetRow(
    snippet: SnippetRecord,
    categoryName: String? = null,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onSwipeStartToEnd: () -> Unit,
    folders: List<CategoryRecord> = emptyList(),
    onMove: ((Long?) -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var moveMenuOpen by remember { mutableStateOf(false) }
    var expanded by rememberSaveable(snippet.id) { mutableStateOf(false) }
    var overflows by remember(snippet.id) { mutableStateOf(false) }
    var deleting by remember(snippet.id) { mutableStateOf(false) }
    val currentOnSwipeStartToEnd by rememberUpdatedState(onSwipeStartToEnd)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val imageFile = snippet.imageFile
    val swipe = remember { SwipeGeometry() }
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (swipe.pastThreshold(value)) {
                        swipe.committed = true
                        currentOnSwipeStartToEnd()
                    }
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    val confirmed = deleting || swipe.pastThreshold(value)
                    if (confirmed && !deleting) {
                        deleting = true
                        swipe.committed = true
                        currentOnDelete()
                    }
                    confirmed
                }
                SwipeToDismissBoxValue.Settled -> true
            }
        },
        positionalThreshold = { distance -> distance * swipeFraction(swipe.direction()) },
    )
    swipe.state = swipeState
    SwipeToDismissBox(
        state = swipeState,
        modifier = Modifier.onSizeChanged { swipe.width = it.width.toFloat() },
        backgroundContent = { SwipeBackground(swipeState, swipe) },
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(containerColor)) {
            if (snippet.isImage && imageFile != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
                        .combinedClickable(onClick = onEdit, onLongClick = { menuOpen = true })
                        .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ImageThumbnail(
                        fileName = imageFile,
                        contentDescription = null,
                        modifier = Modifier.height(56.dp).width(imageThumbWidthDp(snippet).dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = snippet.title.ifBlank { "Image" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SnippetMetaRow(snippet = snippet, categoryName = categoryName)
                    }
                    IconButton(onClick = onCopy) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy ${snippet.title.ifBlank { "Image" }}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
                        .combinedClickable(onClick = onEdit, onLongClick = { menuOpen = true })
                        .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                Column(modifier = Modifier.weight(1f).animateContentSize()) {
                    val title = remember(snippet.title, snippet.text) { rowTitle(snippet.title, snippet.text) }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (snippet.text.trim() != title) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = snippet.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
                        )
                    }
                    if (overflows || expanded) {
                        Text(
                            text = if (expanded) "Show less" else "Show more",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.heightIn(min = 32.dp).clickable { expanded = !expanded }
                                .wrapContentHeight(Alignment.CenterVertically),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    SnippetMetaRow(snippet = snippet, categoryName = categoryName)
                }
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy ${rowTitle(snippet.title, snippet.text).ifBlank { "snippet" }}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (onMove != null && (folders.isNotEmpty() || snippet.categoryId != null)) {
                    DropdownMenuItem(
                        text = { Text("Move to folder") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Folder, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            moveMenuOpen = true
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
            if (onMove != null) {
                DropdownMenu(expanded = moveMenuOpen, onDismissRequest = { moveMenuOpen = false }) {
                    FolderChoice(
                        name = "None (top level)",
                        selected = snippet.categoryId == null,
                        onClick = {
                            moveMenuOpen = false
                            onMove(null)
                        },
                    )
                    folders.forEach { folder ->
                        FolderChoice(
                            name = folder.name,
                            selected = snippet.categoryId == folder.id,
                            onClick = {
                                moveMenuOpen = false
                                onMove(folder.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderChoice(name: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(name) },
        leadingIcon = {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = "Current folder")
            } else {
                Spacer(Modifier.size(24.dp))
            }
        },
        enabled = !selected,
        onClick = onClick,
    )
}

// The swipe-right action lives here and in the row's onSwipeStartToEnd, nowhere else.
private val StartToEndIcon: ImageVector get() = Icons.Rounded.Edit
private const val START_TO_END_LABEL = "Edit"

@OptIn(ExperimentalMaterial3Api::class)
private class SwipeGeometry {
    var state: SwipeToDismissBoxState? = null
    var width by mutableFloatStateOf(0f)

    // Set when an action fires, so the snap-back doesn't play the "below threshold" haptic.
    var committed = false

    fun direction(): SwipeToDismissBoxValue = state?.dismissDirection ?: SwipeToDismissBoxValue.Settled

    fun offset(): Float = state?.let { runCatching { it.requireOffset() }.getOrNull() } ?: 0f

    fun pastThreshold(direction: SwipeToDismissBoxValue): Boolean =
        swipePastThreshold(direction, offset(), width)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(state: SwipeToDismissBoxState, swipe: SwipeGeometry) {
    val colors = MaterialTheme.colorScheme
    val direction = state.dismissDirection
    val armed by remember(state, swipe) { derivedStateOf { swipe.pastThreshold(state.dismissDirection) } }
    val deleting = direction == SwipeToDismissBoxValue.EndToStart
    val view = LocalView.current
    var wasArmed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed && !wasArmed) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
                } else {
                    HapticFeedbackConstants.CONTEXT_CLICK
                },
            )
        } else if (!armed && wasArmed && !swipe.committed &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE)
        }
        wasArmed = armed
    }
    LaunchedEffect(direction) {
        if (direction == SwipeToDismissBoxValue.Settled) swipe.committed = false
    }
    val feedbackSpec = tween<Color>(PastilleMotion.SHORT_MS, easing = PastilleMotion.Standard)
    val container by animateColorAsState(
        targetValue = when {
            !armed -> colors.surfaceContainerHigh
            deleting -> colors.errorContainer
            else -> colors.secondaryContainer
        },
        animationSpec = feedbackSpec,
        label = "swipeContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            !armed -> colors.onSurfaceVariant
            deleting -> colors.onErrorContainer
            else -> colors.onSecondaryContainer
        },
        animationSpec = feedbackSpec,
        label = "swipeContent",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (armed) 1f else 0.85f,
        animationSpec = tween(PastilleMotion.SHORT_MS, easing = PastilleMotion.Standard),
        label = "swipeIconScale",
    )
    Box(
        modifier = Modifier.fillMaxSize().background(container).padding(horizontal = 24.dp),
        contentAlignment = if (deleting) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (direction != SwipeToDismissBoxValue.Settled) {
            Icon(
                imageVector = if (deleting) Icons.Rounded.Delete else StartToEndIcon,
                contentDescription = if (deleting) "Delete" else START_TO_END_LABEL,
                tint = content,
                modifier = Modifier.graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
            )
        }
    }
}

@Composable
private fun FolderRow(
    name: String,
    count: Int,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = {
            Text(if (count == 1) "1 snippet" else "$count snippets")
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SnippetMetaRow(snippet: SnippetRecord, categoryName: String?) {
    val kind = remember(snippet.isImage, snippet.text) { snippetKind(snippet.isImage, snippet.text) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = when (kind) {
                SnippetKind.Text -> Icons.AutoMirrored.Rounded.ShortText
                SnippetKind.Link -> Icons.Rounded.Link
                SnippetKind.Image -> Icons.Rounded.Image
            },
            contentDescription = when (kind) {
                SnippetKind.Text -> "Text"
                SnippetKind.Link -> "Link"
                SnippetKind.Image -> "Image"
            },
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = relativeTime(
                then = snippet.updatedAt.takeIf { it > 0 } ?: snippet.createdAt,
                now = System.currentTimeMillis(),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (categoryName != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = categoryName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun imageThumbWidthDp(snippet: SnippetRecord): Int {
    val width = snippet.imageWidth
    val height = snippet.imageHeight
    if (width == null || height == null || width <= 0 || height <= 0) return 56
    return (56f * width / height).toInt().coerceIn(56, 120)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryNameDialog(
    initialName: String,
    title: String,
    confirmLabel: String,
    onConfirm: suspend (String) -> String?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember(initialName) { mutableStateOf(initialName) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        saving = true
                        val failure = onConfirm(name)
                        saving = false
                        if (failure == null) {
                            onDismiss()
                        } else {
                            error = failure
                        }
                    }
                },
                enabled = name.isNotBlank() && !saving,
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text("Folder name") },
                singleLine = true,
                isError = error != null,
                supportingText = {
                    if (error != null) {
                        Text(error ?: "")
                    }
                },
            )
        },
    )
}

private fun isPastilleEnabled(context: Context): Boolean {
    val manager = context.getSystemService(InputMethodManager::class.java) ?: return false
    return manager.enabledInputMethodList.any { it.packageName == context.packageName }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private const val ALL_TAB = -1L

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTabs(
    folders: List<CategoryRecord>,
    selectedId: Long,
    reordering: Boolean,
    onSelect: (Long) -> Unit,
    onMove: (Long, Long) -> Unit,
    onDropped: () -> Unit,
    onRename: (CategoryRecord) -> Unit,
    onDelete: (CategoryRecord) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val rowState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(rowState) { from, to ->
        val fromId = from.key as? Long ?: return@rememberReorderableLazyListState
        val toId = to.key as? Long ?: return@rememberReorderableLazyListState
        onMove(fromId, toId)
    }
    LaunchedEffect(selectedId, folders) {
        val index = if (selectedId == ALL_TAB) 0 else folders.indexOfFirst { it.id == selectedId } + 1
        if (index >= 0) rowState.animateScrollToItem(index)
    }
    LazyRow(
        state = rowState,
        modifier = Modifier.fillMaxWidth().height(48.dp).selectableGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "all") {
            FolderTab(label = "All", icon = Icons.Rounded.GridView, selected = selectedId == ALL_TAB, onClick = { onSelect(ALL_TAB) })
        }
        items(folders, key = { it.id }) { folder ->
            ReorderableItem(reorderState, key = folder.id) { dragging ->
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    FolderTab(
                        label = folder.name,
                        icon = Icons.Rounded.Folder,
                        selected = selectedId == folder.id || dragging,
                        onClick = { onSelect(folder.id) },
                        onLongClick = if (reordering) null else ({ menuOpen = true }),
                        modifier = if (reordering) {
                            Modifier.longPressDraggableHandle(
                                onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onDragStopped = { onDropped() },
                            )
                        } else {
                            Modifier
                        },
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRename(folder)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onDelete(folder)
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) colors.secondaryContainer else colors.surfaceContainerHigh,
        label = "tabContainer",
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(32.dp)
            .clip(CircleShape)
            .background(container)
            .semantics { this.selected = selected }
            .combinedClickable(role = Role.Tab, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ReorderBanner(onDone: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Drag the handle to reorder. Hold a folder to move it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDone) { Text("Done") }
    }
}

@Composable
private fun ReorderRow(snippet: SnippetRecord, handle: Modifier) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rowTitle(snippet.title, snippet.text).ifBlank { "Image" },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = handle.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close search")
        }
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search snippets") },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = modifier.weight(1f),
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
            }
        }
    }
}
