package app.pastille.ui

import android.Manifest
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
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.pastille.ime.ImageSourceReader
import app.pastille.model.CategoryRecord
import app.pastille.model.SnippetRecord
import app.pastille.settings.PastilleSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetListScreen(
    repository: SnippetRepository,
    onCreate: (Long?) -> Unit,
    onEdit: (SnippetRecord) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFolder: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val flow = remember { repository.observeSnippets() }
    val snippets by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showCrashDialog by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(true) }
    var prevIndex by remember { mutableIntStateOf(0) }
    var prevOffset by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hasCrashReport = remember(showMenu) {
        CrashLog.forContext(context).entries().isNotEmpty()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshTick++ }

    val categories by remember { repository.observeCategories() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var showCreateFolder by remember { mutableStateOf(false) }

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

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                fabExpanded = index == 0 && offset == 0 ||
                    index < prevIndex ||
                    (index == prevIndex && offset < prevOffset)
                prevIndex = index
                prevOffset = offset
            }
    }

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

    fun moveSnippet(snippet: SnippetRecord, categoryId: Long?) {
        scope.launch {
            repository.setCategory(listOf(snippet.id), categoryId)
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
    val sortedFolders = remember(categories) {
        categories.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
    val snippetCounts = remember(snippets) {
        snippets.groupingBy { it.categoryId }.eachCount()
    }
    val topLevel = remember(snippets) {
        snippets.filter { it.categoryId == null }
    }
    val categoryNames = remember(categories) { categories.associate { it.id to it.name } }

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
                LargeTopAppBar(
                    title = { Wordmark(collapsedFraction = scrollBehavior.state.collapsedFraction) },
                    actions = {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search snippets")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("New folder") },
                                onClick = {
                                    showMenu = false
                                    showCreateFolder = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Import snippets") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.FileDownload, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    importLauncher.launch(arrayOf("application/json"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export snippets") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.FileUpload, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    exportLauncher.launch("pastille-snippets.json")
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Keyboard setup") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Keyboard, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Settings, contentDescription = null)
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
                                        Icon(Icons.Outlined.BugReport, contentDescription = null)
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
                ExtendedFloatingActionButton(
                    onClick = { onCreate(null) },
                    expanded = fabExpanded,
                    text = { Text("New snippet") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                item(key = "onboarding") {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        OnboardingCard(key = refreshTick, onOpenSettings = onOpenSettings)
                    }
                }
                item(key = "try-it") {
                    TryItCard()
                }
                if (snippets.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "No snippets yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            FilledTonalButton(onClick = { onCreate(null) }) {
                                Text("Add your first snippet")
                            }
                        }
                    }
                }
                itemsIndexed(sortedFolders, key = { _, folder -> "folder-${folder.id}" }) { index, folder ->
                    if (index > 0) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    FolderRow(
                        name = folder.name,
                        count = snippetCounts[folder.id] ?: 0,
                        onClick = { onOpenFolder(folder.id) },
                    )
                }
                itemsIndexed(topLevel, key = { _, snippet -> snippet.id }) { index, snippet ->
                    if (index > 0 || sortedFolders.isNotEmpty()) {
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
                        folders = sortedFolders,
                        onMove = { moveSnippet(snippet, it) },
                    )
                }
                }
            }
        }
        if (searching) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = {},
                        expanded = true,
                        onExpandedChange = {
                            if (!it) {
                                searching = false
                                query = ""
                            }
                        },
                        placeholder = { Text("Search snippets") },
                        leadingIcon = {
                            IconButton(
                                onClick = {
                                    searching = false
                                    query = ""
                                },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Close search",
                                )
                            }
                        },
                        modifier = Modifier.focusRequester(focusRequester),
                    )
                },
                expanded = true,
                onExpandedChange = {
                    if (!it) {
                        searching = false
                        query = ""
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(visible, key = { _, snippet -> snippet.id }) { index, snippet ->
                        if (index > 0) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        SnippetRow(
                            snippet = snippet,
                            categoryName = snippet.categoryId?.let { categoryNames[it] },
                            onEdit = { onEdit(snippet) },
                            onCopy = { copySnippet(snippet) },
                            onTogglePin = { togglePin(snippet) },
                            onDelete = { deleteSnippet(snippet) },
                            folders = sortedFolders,
                            onMove = { moveSnippet(snippet, it) },
                            containerColor = SearchBarDefaults.colors().containerColor,
                        )
                    }
                    if (query.isNotBlank() && visible.isEmpty()) {
                        item(key = "no-match") {
                            Text(
                                text = "No snippets match “$query”",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
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
            tint = MaterialTheme.colorScheme.primary,
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
private fun OnboardingCard(key: Int, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    val imeEnabled = remember(key) { isPastilleEnabled(context) }
    val photosGranted = remember(key) { ImageSourceReader.hasPermission(context) }
    val partialOnly = remember(key) { ImageSourceReader.hasOnlyPartialAccess(context) }
    val keyboardStyleChosen = remember(key) { PastilleSettings.forContext(context).keyboardStyleChosen }

    if (imeEnabled && photosGranted && !partialOnly && keyboardStyleChosen) return

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Get set up", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (!imeEnabled) {
                SetupRow(
                    text = "1. Enable Pastille in system settings",
                    button = "Enable",
                    onClick = {
                        context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS))
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
                    text = "2. Allow photo access for screenshots",
                    button = "Allow",
                    onClick = {
                        permissionLauncher.launch(photoPermission())
                    },
                )
            }
            if (!keyboardStyleChosen) {
                SetupRow(
                    text = "3. Pick a keyboard style",
                    button = "Choose",
                    onClick = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun TryItCard() {
    val context = LocalContext.current
    val show = remember { isPastilleEnabled(context) && !isPastilleCurrent(context) }
    if (!show) return
    TryItField(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
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
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    folders: List<CategoryRecord> = emptyList(),
    onMove: ((Long?) -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var moveMenuOpen by remember { mutableStateOf(false) }
    var expanded by rememberSaveable(snippet.id) { mutableStateOf(false) }
    var overflows by remember(snippet.id) { mutableStateOf(false) }
    var deleting by remember(snippet.id) { mutableStateOf(false) }
    val currentOnEdit by rememberUpdatedState(onEdit)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val imageFile = snippet.imageFile
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    currentOnEdit()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    if (!deleting) {
                        deleting = true
                        currentOnDelete()
                    }
                    true
                }
                SwipeToDismissBoxValue.Settled -> true
            }
        },
    )
    SwipeToDismissBox(
        state = swipeState,
        backgroundContent = { SwipeBackground(swipeState) },
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
                            imageVector = Icons.Outlined.ContentCopy,
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
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy ${rowTitle(snippet.title, snippet.text).ifBlank { "snippet" }}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (snippet.pinned) "Unpin" else "Pin") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (snippet.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onTogglePin()
                    },
                )
                if (onMove != null && (folders.isNotEmpty() || snippet.categoryId != null)) {
                    DropdownMenuItem(
                        text = { Text("Move to folder") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Folder, contentDescription = null)
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
                        Icon(Icons.Outlined.Delete, contentDescription = null)
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
                Icon(Icons.Outlined.Check, contentDescription = "Current folder")
            } else {
                Spacer(Modifier.size(24.dp))
            }
        },
        enabled = !selected,
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(state: SwipeToDismissBoxState) {
    val colors = MaterialTheme.colorScheme
    val direction = state.dismissDirection
    val armed = state.targetValue != SwipeToDismissBoxValue.Settled
    val deleting = direction == SwipeToDismissBoxValue.EndToStart
    val container by animateColorAsState(
        targetValue = when {
            !armed -> colors.surfaceContainerHigh
            deleting -> colors.errorContainer
            else -> colors.secondaryContainer
        },
        label = "swipeContainer",
    )
    val content = when {
        !armed -> colors.onSurfaceVariant
        deleting -> colors.onErrorContainer
        else -> colors.onSecondaryContainer
    }
    Box(
        modifier = Modifier.fillMaxSize().background(container).padding(horizontal = 24.dp),
        contentAlignment = if (deleting) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (direction != SwipeToDismissBoxValue.Settled) {
            Icon(
                imageVector = if (deleting) Icons.Outlined.Delete else Icons.Outlined.Edit,
                contentDescription = if (deleting) "Delete" else "Edit",
                tint = content,
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
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
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
                SnippetKind.Text -> Icons.AutoMirrored.Outlined.ShortText
                SnippetKind.Link -> Icons.Outlined.Link
                SnippetKind.Image -> Icons.Outlined.Image
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
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
        if (categoryName != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = categoryName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (snippet.pinned) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = "Pinned",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
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

private fun isPastilleCurrent(context: Context): Boolean {
    val current = AndroidSettings.Secure.getString(
        context.contentResolver,
        AndroidSettings.Secure.DEFAULT_INPUT_METHOD,
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
