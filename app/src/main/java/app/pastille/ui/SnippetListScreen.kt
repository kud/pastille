package app.pastille.ui

import androidx.compose.foundation.layout.offset
import app.pastille.settings.KeyboardMode
import app.pastille.ime.modeIcon
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.ui.semantics.heading
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.foundation.Image
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.material3.TopAppBarScrollBehavior
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
import androidx.compose.ui.platform.testTag
import android.view.HapticFeedbackConstants
import app.pastille.ime.LocalReduceMotion
import app.pastille.ime.PastilleMotion
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pastille.R
import app.pastille.data.SnippetRepository
import app.pastille.images.ImageStore
import app.pastille.images.ImageThumbnail
import app.pastille.model.CategoryRecord
import app.pastille.model.SnippetRecord
import app.pastille.model.filterByTags
import app.pastille.model.isMissingFolder
import app.pastille.model.matchesQuery
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import app.pastille.settings.PastilleSettings
import app.pastille.settings.setupNeedsPhotos
import app.pastille.share.imageTitle
import app.pastille.backup.BackupArchive
import app.pastille.backup.BackupFiles
import app.pastille.share.shareBackup
import app.pastille.share.shareSnippet
import app.pastille.share.sendToPhone
import androidx.compose.material.icons.automirrored.rounded.SendToMobile
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.QrCode2
import app.pastille.qr.SnippetQr
import app.pastille.qr.SnippetQrDialog
import app.pastille.qr.QR_TOO_LONG
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetListScreen(
    repository: SnippetRepository,
    onCreate: (Long?) -> Unit,
    onEdit: (SnippetRecord) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBin: () -> Unit = {},
    deletedSnippet: SnippetRecord? = null,
    onDeletedShown: () -> Unit = {},
    addStickersRequested: Boolean = false,
    onAddStickersHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val flow = remember { repository.observeSnippets() }
    // null until the first read, so the screen never shows "No snippets yet" (or no tag row) for a
    // frame and then jumps to the real list: nothing below the folder row draws before it loads.
    val loadedSnippets by flow.collectAsStateWithLifecycle(initialValue = null as List<SnippetRecord>?)
    val snippets = loadedSnippets.orEmpty()
    val loadedStickers by remember { repository.observeStickers() }.collectAsStateWithLifecycle(initialValue = null as List<SnippetRecord>?)
    val stickers = loadedStickers.orEmpty()
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollBehavior = rememberListDrivenScrollBehavior()
    val binned by remember { repository.observeBin() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val binCount = binned.size
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshTick++ }
    val onboarding = rememberOnboarding(refreshTick)
    val settings = remember { PastilleSettings.forContext(context) }
    // Snippets | Stickers: two kinds of thing, never mixed in one chip row. The last one reopens.
    var stickersMode by rememberSaveable { mutableStateOf(settings.appStickersMode) }
    LaunchedEffect(stickersMode) { settings.appStickersMode = stickersMode }

    val loadedCategories by remember { repository.observeCategories() }
        .collectAsStateWithLifecycle(initialValue = null as List<CategoryRecord>?)
    val categories = loadedCategories.orEmpty()
    val loaded = loadedSnippets != null && loadedStickers != null && loadedCategories != null
    var showCreateFolder by remember { mutableStateOf(false) }
    var organisingId by rememberSaveable { mutableStateOf<Long?>(null) }
    // The folder the snippet had when the sheet opened, for the snackbar's Undo.
    var organisingFrom by rememberSaveable { mutableStateOf<Long?>(null) }
    val allTagNames by remember { repository.observeTagNames() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val organising = organisingId?.let { id -> snippets.firstOrNull { it.id == id } ?: stickers.firstOrNull { it.id == id } }

    // Export, Import and Share backup: one format for all three, the zip archive with images and
    // stickers (BackupArchive). Import still reads the old JSON exports.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupArchive.MIME),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val (file, written) = BackupFiles.build(context)
                    try {
                        BackupFiles.writeTo(context, uri, file)
                    } finally {
                        file.delete()
                    }
                    exportMessage(written)
                }.onSuccess { toast(context, it) }
                    .onFailure { toast(context, EXPORT_FAILED) }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val file = runCatching { BackupFiles.copyIn(context, uri) }.getOrNull()
                if (file == null) {
                    toast(context, IMPORT_UNREADABLE)
                } else {
                    runCatching { BackupFiles.importFile(context, file) }
                        .onSuccess { toast(context, importMessage(it)) }
                        .onFailure { toast(context, IMPORT_NOT_A_BACKUP) }
                }
            }
        }
    }
    fun shareWholeBackup() {
        scope.launch {
            runCatching { BackupFiles.buildForSharing(context).first }
                .onSuccess { shareBackup(context, it) }
                .onFailure { toast(context, SHARE_FAILED) }
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

    fun openOrganise(snippet: SnippetRecord) {
        organisingFrom = snippet.categoryId
        organisingId = snippet.id
    }

    // With tags in the sheet it stays open on a tap; the move is reported once it closes.
    fun closeOrganise(snippet: SnippetRecord?) {
        organisingId = null
        val previous = organisingFrom
        val target = snippet?.categoryId
        if (snippet == null || target == previous) return
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = movedMessage(target?.let { id -> categories.firstOrNull { it.id == id }?.name }),
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                repository.setCategory(listOf(snippet.id), previous)
            }
        }
    }

    suspend fun showDeletedSnackbar(snippet: SnippetRecord) {
        val result = snackbarHostState.showSnackbar(
            message = "Moved to bin",
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

    fun setSticker(snippet: SnippetRecord, on: Boolean) {
        scope.launch {
            val before = repository.get(snippet.id) ?: return@launch
            if (!repository.setSticker(snippet.id, on)) return@launch
            val result = snackbarHostState.showSnackbar(
                message = if (on) "Moved to stickers" else "Moved to snippets",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) repository.restore(before)
        }
    }

    fun importStickers(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            val adding = launch {
                snackbarHostState.showSnackbar(addingStickersMessage(uris.size), duration = SnackbarDuration.Indefinite)
            }
            val fallbackDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
                .format(LocalDate.now())
            val names = withContext(Dispatchers.IO) { uris.map { displayNameOf(context, it) } }
            val store = ImageStore.forContext(context)
            val imported = repository.importImages(
                uris = uris,
                categoryId = null,
                sticker = true,
                importer = store,
                titleFor = { index -> imageTitle(null, names.getOrNull(index), fallbackDate) },
            )
            adding.cancel()
            val result = snackbarHostState.showSnackbar(
                message = stickerImportMessage(imported),
                actionLabel = if (imported.savedCount > 0) "Undo" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) repository.undoImport(imported, store::delete)
        }
    }

    val stickerPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(SnippetRepository.MAX_IMPORT),
    ) { uris -> importStickers(uris) }

    fun pickStickers() {
        stickerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(deletedSnippet) {
        if (deletedSnippet != null) {
            // Clearing the request restarts this effect, so the snackbar runs on the screen's scope.
            scope.launch { showDeletedSnackbar(deletedSnippet) }
            onDeletedShown()
        }
    }

    val tagsInUse = remember(snippets) { snippets.flatMap { it.tags }.distinct().sorted() }
    var selectedTags by rememberSaveable { mutableStateOf(listOf<String>()) }
    LaunchedEffect(tagsInUse) {
        if (snippets.isEmpty()) return@LaunchedEffect
        if (selectedTags.any { it !in tagsInUse }) selectedTags = selectedTags.filter { it in tagsInUse }
    }
    val visible = remember(snippets, query, selectedTags) {
        filterByTags(snippets.filter { matchesQuery(it, query) }, selectedTags.toSet())
    }
    val haptics = LocalHapticFeedback.current
    var selectedTab by rememberSaveable { mutableLongStateOf(ALL_TAB) }
    var reordering by rememberSaveable { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<CategoryRecord?>(null) }
    var deletingFolder by remember { mutableStateOf<CategoryRecord?>(null) }
    val orderedFolders = remember(categories) { categories.sortedBy { it.position } }
    LaunchedEffect(loadedCategories) {
        if (isMissingFolder(loadedCategories, selectedTab.takeIf { it >= 0 })) selectedTab = ALL_TAB
    }
    LaunchedEffect(addStickersRequested) {
        if (!addStickersRequested) return@LaunchedEffect
        stickersMode = true
        searching = false
        query = ""
        reordering = false
        onAddStickersHandled()
        pickStickers()
    }
    val onStickers = stickersMode
    val selectedFolderId = selectedTab.takeIf { it >= 0 }
    // Search finds stickers by description too, as rows carrying a "Sticker" pill.
    val matchingStickers = remember(stickers, query) {
        if (query.isBlank()) emptyList() else stickers.filter { matchesQuery(it, query) }
    }
    // Search is global: from Stickers it finds snippets too, as from All it finds stickers.
    val shown = remember(snippets, visible, selectedTab, matchingStickers, stickersMode, query) {
        when {
            // The tag row is hidden under Stickers, so its filter must not narrow the results there.
            stickersMode -> snippets.filter { matchesQuery(it, query) } + matchingStickers
            selectedTab == ALL_TAB -> visible + matchingStickers
            else -> visible.filter { it.categoryId == selectedTab }
        }
    }
    val stickerGridShown = onStickers && query.isBlank()
    val reduceMotion = LocalReduceMotion.current
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

    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }

    // Back steps out one level at a time: Reorder, then Stickers to Snippets, then a folder to All.
    BackHandler(enabled = !searching && (reordering || stickersMode || selectedTab != ALL_TAB)) {
        when {
            reordering -> reordering = false
            stickersMode -> stickersMode = false
            else -> selectedTab = ALL_TAB
        }
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

    if (organising != null) {
        OrganiseSheet(
            snippet = organising,
            folders = orderedFolders,
            allTags = allTagNames,
            onMove = { target -> scope.launch { repository.setCategory(listOf(organising.id), target) } },
            onTagsChange = { tags -> scope.launch { repository.setTags(organising.id, tags) } },
            onDismiss = { closeOrganise(organising) },
            onSetSticker = { on ->
                organisingId = null
                setSticker(organising, on)
            },
            onRename = { title -> scope.launch { repository.setTitle(organising.id, title) } },
            onDelete = {
                organisingId = null
                deleteSnippet(organising)
            },
        )
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
                } else TopAppBar(
                    modifier = Modifier.testTag(TOP_BAR_TAG),
                    title = { Wordmark() },
                    actions = {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search snippets")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            // The menu follows the mode on screen (Erwann, 2026-10-28): Reorder for
                            // the mode shown, then what belongs to the whole app. The backup now
                            // carries stickers and images too, so it sits with the app-wide items.
                            // New folder lives at the end of the folder row ("+ Add").
                            DropdownMenuItem(
                                text = { Text("Reorder") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.SwapVert, contentDescription = null)
                                },
                                // Reorder applies to the mode on screen.
                                enabled = if (stickersMode) stickers.size > 1 else snippets.size > 1 || orderedFolders.size > 1,
                                onClick = {
                                    showMenu = false
                                    searching = false
                                    reordering = true
                                },
                            )
                            HorizontalDivider()
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
                                text = { Text("Import backup") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.FileDownload, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    // Any type: Drive and file managers label zips and old JSON
                                    // exports inconsistently, and the contents decide anyway.
                                    importLauncher.launch(arrayOf("*/*"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export backup") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.FileUpload, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    exportLauncher.launch(BackupArchive.SUGGESTED_NAME)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Share backup") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Share, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    shareWholeBackup()
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
                            DropdownMenuItem(
                                text = { Text(if (binCount > 0) "Bin ($binCount)" else "Bin") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Delete, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onOpenBin()
                                },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        // Flat, like Sacha's header: the cards scrolling under it are the depth.
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                if (!reordering && onStickers) {
                    ExtendedFloatingActionButton(
                        onClick = ::pickStickers,
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text("Add stickers") },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        elevation = FloatingActionButtonDefaults.loweredElevation(),
                    )
                } else if (!reordering) {
                    // Sacha: an extended "+ New snippet", light tonal fill with dark text.
                    ExtendedFloatingActionButton(
                        onClick = { onCreate(selectedFolderId) },
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text("New snippet") },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        elevation = FloatingActionButtonDefaults.loweredElevation(),
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                ModeSwitch(
                    stickers = stickersMode,
                    enabled = !reordering,
                    onSelect = { stickersMode = it },
                )
                // Folders hold snippets only, so the row (and its "+ Add") lives in Snippets mode.
                AnimatedVisibility(
                    visible = !stickersMode,
                    enter = PastilleMotion.rowEnter(reduceMotion),
                    exit = PastilleMotion.rowExit(reduceMotion),
                ) {
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
                        onAdd = { showCreateFolder = true },
                    )
                }
                if (loaded && tagsInUse.isNotEmpty() && !reordering && !onStickers) {
                    TagFilterRow(
                        tags = tagsInUse,
                        selected = selectedTags.toSet(),
                        onToggle = { tag ->
                            selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag
                        },
                    )
                }
                if (reordering) {
                    ReorderBanner(onDone = { reordering = false })
                }
                AnimatedContent(
                    targetState = stickerGridShown,
                    transitionSpec = { PastilleMotion.peerTransition(reduceMotion) },
                    label = "listMode",
                    modifier = Modifier.fillMaxSize(),
                ) { showGrid ->
                    if (!loaded) {
                        Box(modifier = Modifier.fillMaxSize())
                    } else if (showGrid) {
                        StickerManageGrid(
                            stickers = stickers,
                            reordering = reordering,
                            onOpen = ::openOrganise,
                            onMoveToSnippets = { setSticker(it, false) },
                            onDelete = ::deleteSnippet,
                            onReorder = { order -> scope.launch { repository.setSnippetOrder(stickers, order) } },
                            onAdd = ::pickStickers,
                        )
                    } else LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag(SNIPPET_LIST_TAG),
                        state = listState,
                        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                    ) {
                        // Only when there is something to set up: a zero-height first item leaves the list
                        // claiming it can still scroll back at its top (see `rememberOnboarding`).
                        if (onboarding.needed) {
                            item(key = "onboarding") {
                                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                    OnboardingCard(onboarding)
                                }
                            }
                        }
                        if (shown.isEmpty()) {
                            item(key = "empty") {
                                when {
                                    query.isNotBlank() -> EmptyState(
                                        icon = Icons.Rounded.SearchOff,
                                        title = "No snippets match “$query”",
                                        body = "Search looks in titles, text and #tags.",
                                    )
                                    snippets.isEmpty() -> EmptyState(
                                        icon = Icons.Rounded.Inbox,
                                        title = "No snippets yet",
                                        body = "Save the things you type again and again, then paste them from the keyboard.",
                                        action = "Add your first snippet",
                                        onAction = { onCreate(selectedFolderId) },
                                    )
                                    else -> EmptyState(
                                        icon = Icons.Rounded.FolderOpen,
                                        title = "Nothing in this folder yet",
                                        action = "Add a snippet here",
                                        onAction = { onCreate(selectedFolderId) },
                                    )
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
                                // Sacha: separate rounded cards with small gaps, no dividers.
                                Column(
                                    modifier = Modifier.animateItem().testTag(SNIPPET_ROW_TAG)
                                        .padding(horizontal = 12.dp, vertical = 3.dp),
                                ) {
                                    SnippetRow(
                                        snippet = snippet,
                                        categoryName = if (stickersMode || selectedTab == ALL_TAB) snippet.categoryId?.let { categoryNames[it] } else null,
                                        onEdit = { if (snippet.sticker) openOrganise(snippet) else onEdit(snippet) },
                                        onCopy = { copySnippet(snippet) },
                                        onDelete = { deleteSnippet(snippet) },
                                        onSwipeStartToEnd = { openOrganise(snippet) },
                                        folders = orderedFolders,
                                        onMove = if (snippet.sticker) null else ({ moveSnippet(snippet, it) }),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Wordmark() {
    // Sacha's lockup: the colour mark at 28dp beside a bold 26sp "Pastille". The mark is drawn, not
    // tinted, so it keeps its blue and folded corner in both themes; the name carries the meaning.
    Row(modifier = Modifier.testTag(WORDMARK_TAG), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_pastille_mark),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Pastille",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                fontSize = 26.sp,
            ),
        )
    }
}

/** What the setup card still asks for, read again on every resume and after the photo prompt. */
private class Onboarding(val imeEnabled: Boolean, val photosNeeded: Boolean, val onPhotosAnswered: () -> Unit) {
    val needed: Boolean get() = !imeEnabled || photosNeeded
}

/**
 * Decided here, not inside the card, so the list can leave the card's item out entirely once set
 * up. An item that composes to nothing is a zero-height first item, and LazyColumn (foundation
 * 1.7) then settles at its top on index 1, offset 0: it reports it can still scroll backward,
 * accepts the next downward drag, moves nothing, and returns the whole delta as consumed. The
 * header never gets that delta, and `animateItem()` reads it as a scroll and pushes every row down
 * by it, so a fling towards the top emptied the list until the rows sprang back.
 */
@Composable
private fun rememberOnboarding(refreshTick: Int): Onboarding {
    val context = LocalContext.current
    val settings = remember { PastilleSettings.forContext(context) }
    var photosAsked by remember { mutableStateOf(0) }
    val imeEnabled = remember(refreshTick) { isPastilleEnabled(context) }
    // Asked once, like Settings › Photo access words it: declining or "Select photos" is an answer,
    // and the card no longer asks; Settings is where it changes. It used to nag forever.
    val photosNeeded = remember(refreshTick, photosAsked) {
        setupNeedsPhotos(
            granted = ImageSourceReader.hasPermission(context),
            partialOnly = ImageSourceReader.hasOnlyPartialAccess(context),
            answered = settings.photoPromptAnswered,
        )
    }
    return Onboarding(
        imeEnabled = imeEnabled,
        photosNeeded = photosNeeded,
        onPhotosAnswered = {
            settings.photoPromptAnswered = true
            photosAsked++
        },
    )
}

@Composable
private fun OnboardingCard(onboarding: Onboarding) {
    val context = LocalContext.current
    val imeEnabled = onboarding.imeEnabled
    val photosNeeded = onboarding.photosNeeded
    val onPhotosAnswered by rememberUpdatedState(onboarding.onPhotosAnswered)
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onPhotosAnswered()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Set up Pastille",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(4.dp))
            if (!imeEnabled) {
                SetupRow(
                    icon = Icons.Rounded.Keyboard,
                    text = "Turn on Pastille in your keyboards",
                    button = "Enable",
                    onClick = {
                        context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                )
            }
            if (photosNeeded) {
                SetupRow(
                    icon = Icons.Rounded.PhotoLibrary,
                    text = "Allow photos, so your images show in the keyboard",
                    button = "Allow",
                    onClick = { photoLauncher.launch(photoPermission()) },
                )
            }
        }
    }
}

@Composable
private fun SetupRow(icon: ImageVector, text: String, button: String, onClick: () -> Unit) {
    // Settings' row language (§20): a plain 24dp glyph, the text, then the one action as a tonal
    // button, 48dp tall, so the step reads as something to do rather than a link.
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.FilledTonalButton(onClick = onClick) { Text(button) }
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
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    shape: Shape = RoundedCornerShape(16.dp),
) {
    val context = LocalContext.current
    val rowScope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var moveMenuOpen by remember { mutableStateOf(false) }
    var qrOpen by remember { mutableStateOf(false) }
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
        modifier = Modifier.clip(shape).onSizeChanged { swipe.width = it.width.toFloat() },
        backgroundContent = { SwipeBackground(swipeState, swipe) },
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(containerColor)) {
            if (snippet.isImage && imageFile != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
                        .combinedClickable(onClick = onEdit, onLongClick = { menuOpen = true })
                        .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.Top,
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
                        CardTitle(snippet.title.ifBlank { "Image" })
                        Spacer(modifier = Modifier.height(6.dp))
                        SnippetMetaRow(snippet = snippet, categoryName = categoryName)
                    }
                    TimeAndCopy(snippet = snippet, copyLabel = "Copy ${snippet.title.ifBlank { "Image" }}", onCopy = onCopy)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
                        .combinedClickable(onClick = onEdit, onLongClick = { menuOpen = true })
                        .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                Column(modifier = Modifier.weight(1f).animateContentSize()) {
                    val title = remember(snippet.title, snippet.text) { rowTitle(snippet.title, snippet.text) }
                    CardTitle(title)
                    if (snippet.text.trim() != title) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = snippet.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // Up to two lines, sized to the text: a reserved second line left a hole between a
                            // one-line preview and its folder chip. Time and copy sit at the top, so they line
                            // up whatever the height (Iris, 2026-10-28).
                            maxLines = if (expanded) Int.MAX_VALUE else 2,
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
                    if (hasMeta(snippet, categoryName)) {
                        Spacer(modifier = Modifier.height(6.dp))
                        SnippetMetaRow(snippet = snippet, categoryName = categoryName)
                    }
                }
                TimeAndCopy(
                    snippet = snippet,
                    copyLabel = "Copy ${rowTitle(snippet.title, snippet.text).ifBlank { "snippet" }}",
                    onCopy = onCopy,
                )
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
                    text = { Text("Share") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Share, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        if (!shareSnippet(context, snippet)) toast(context, "That image is gone")
                    },
                )
                // Text only: a camera reads it and shares it into Pastille. Too long for the
                // biggest code, the item says so and Send to another phone, just below, carries it.
                if (!snippet.isImage) {
                    val qrFits = remember(snippet.text) { SnippetQr.fits(snippet.text) }
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Show QR code")
                                if (!qrFits) {
                                    Text(
                                        text = QR_TOO_LONG,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.QrCode2, contentDescription = null)
                        },
                        enabled = qrFits,
                        onClick = {
                            menuOpen = false
                            qrOpen = true
                        },
                    )
                }
                // A file, not text: Quick Share's "Open" on the other phone lands in "Import to
                // Pastille", where plain text would only reach the clipboard.
                DropdownMenuItem(
                    text = { Text("Send to another phone") },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Rounded.SendToMobile, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        rowScope.launch {
                            runCatching { BackupFiles.buildToSend(context, snippet) }
                                .onSuccess { sendToPhone(context, it) }
                                .onFailure { toast(context, SEND_FAILED) }
                        }
                    },
                )
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
            if (qrOpen) {
                SnippetQrDialog(
                    title = rowTitle(snippet.title, snippet.text).ifBlank { "Snippet" },
                    text = snippet.text,
                    onDismiss = { qrOpen = false },
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
private val StartToEndIcon: ImageVector get() = Icons.AutoMirrored.Rounded.DriveFileMove
private const val START_TO_END_LABEL = "Organise"

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
    MetaLine(tags = snippet.tags) {
        // Plain text says nothing by a glyph; a link or an image does.
        if (kind != SnippetKind.Text) {
            Icon(
                imageVector = if (kind == SnippetKind.Link) Icons.Rounded.Link else Icons.Rounded.Image,
                contentDescription = if (kind == SnippetKind.Link) "Link" else "Image",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        if (snippet.sticker) {
            StickerPill()
            Spacer(modifier = Modifier.width(6.dp))
        } else if (categoryName != null) {
            FolderPill(categoryName)
            Spacer(modifier = Modifier.width(6.dp))
        }
    }
}

private fun hasMeta(snippet: SnippetRecord, categoryName: String?): Boolean =
    snippet.tags.isNotEmpty() || snippet.sticker || categoryName != null ||
        snippetKind(snippet.isImage, snippet.text) != SnippetKind.Text

/** The card's title, medium weight, one line. */
@Composable
private fun CardTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The card's right column, one unit on the title's line: the time ("5 min", "2 h"), then copy. The
 * 48dp button is pulled up by half the difference between it and the title's 24sp line, so the time,
 * the glyph and the title share one centre (Erwann, 2026-10-28: « Bizarre l'alignement », when the
 * time sat on the title and copy floated lower, centred on the card). The glyph sits 16dp from the
 * card's edge, the same as the title's start.
 */
@Composable
private fun TimeAndCopy(snippet: SnippetRecord, copyLabel: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.offset(y = (-12).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = relativeTime(
                then = snippet.updatedAt.takeIf { it > 0 } ?: snippet.createdAt,
                now = System.currentTimeMillis(),
            ).removeSuffix(" ago"),
            // Tabular figures, so "5 min" and "28 h" end on the same column edge down the list.
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp),
        )
        IconButton(onClick = onCopy) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = copyLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

private fun displayNameOf(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()
internal const val TOP_BAR_TAG = "top-bar"
internal const val SNIPPET_LIST_TAG = "snippet-list"
internal const val MODE_SWITCH_TAG = "mode-switch"
internal const val WORDMARK_TAG = "wordmark"
internal const val SNIPPET_ROW_TAG = "snippet-row"

/** Snippets | Stickers. Material's own check marks the selected side, so it never rests on colour. */
@Composable
private fun ModeSwitch(stickers: Boolean, enabled: Boolean, onSelect: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 4.dp)
            .testTag(MODE_SWITCH_TAG),
    ) {
        listOf(false to "Snippets", true to "Stickers").forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = stickers == value,
                onClick = { onSelect(value) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    activeBorderColor = MaterialTheme.colorScheme.outline,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                    inactiveBorderColor = MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.heightIn(min = 48.dp),
                // The keyboard tab's glyph; the selected half swaps it for the check, as in the share sheet.
                icon = {
                    SegmentedButtonDefaults.Icon(active = stickers == value) {
                        Icon(
                            modeIcon(if (value) KeyboardMode.Stickers else KeyboardMode.Snippets),
                            contentDescription = null,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                        )
                    }
                },
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

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
    onAdd: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val rowState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(rowState) { from, to ->
        val fromId = from.key as? Long ?: return@rememberReorderableLazyListState
        val toId = to.key as? Long ?: return@rememberReorderableLazyListState
        onMove(fromId, toId)
    }
    LaunchedEffect(selectedId, folders) {
        val index = when (selectedId) {
            ALL_TAB -> 0
            else -> folders.indexOfFirst { it.id == selectedId }.let { if (it < 0) -1 else it + 1 }
        }
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
            FolderChip(label = "All", icon = Icons.Rounded.GridView, alwaysIcon = true, selected = selectedId == ALL_TAB, onClick = { onSelect(ALL_TAB) })
        }
        items(folders, key = { it.id }) { folder ->
            ReorderableItem(reorderState, key = folder.id) { dragging ->
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    FolderChip(
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
        if (!reordering) {
            item(key = "add-folder") {
                AddFolderChip(onClick = onAdd)
            }
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
    // Jotter review: a full-width pill with ✕, in place of the bar.
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(56.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search snippets and #tags") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close search")
            }
        }
    }
}

/**
 * Issue #8, settled by the redesign: the header is a compact pinned bar (Sacha, 2026-10-28), so it
 * never collapses, never resizes the wordmark and can't be dragged. The list scrolls under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberListDrivenScrollBehavior(): TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

/**
 * The folder row ends with a "+" that creates a folder, built as one of the row's own chips: 36dp tall
 * in a 48dp target, the chips' stadium shape and unselected fill, so it shares their height, centre and
 * edges (Erwann, 2026-10-28: the outlined 36dp circle read taller and foreign beside them).
 */
@Composable
private fun AddFolderChip(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(36.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = "New folder", onClick = onClick)
            .semantics { contentDescription = "New folder" }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(20.dp))
    }
}
