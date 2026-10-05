package app.pastille.picker

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pastille.data.SnippetRepository
import app.pastille.ime.ChipEntry
import app.pastille.ime.FilterToggleChip
import app.pastille.ime.FolderChipRow
import app.pastille.ime.ImageGrid
import app.pastille.ime.ImageItem
import app.pastille.ime.ImageSource
import app.pastille.ime.ImageSourceReader
import app.pastille.ime.KeyboardActions
import app.pastille.ime.LocalKeyboardPalette
import app.pastille.ime.ModeSwitch
import app.pastille.ime.SnippetGrid
import app.pastille.ime.ThumbState
import app.pastille.ime.displayTitle
import app.pastille.ime.rememberThumb
import app.pastille.ime.resolveSource
import app.pastille.ime.sourceIcon
import app.pastille.ime.visibleSources
import app.pastille.images.ImageThumbnail
import app.pastille.model.CategoryRecord
import app.pastille.model.SnippetRecord
import app.pastille.settings.KeyboardMode
import app.pastille.settings.PastilleSettings
import app.pastille.settings.effectiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class PickerState(
    val mode: KeyboardMode,
    val folderId: Long?,
    val sourceId: Long?,
    val target: PickerTarget?,
    val notice: PickerNotice?,
    val closing: Boolean,
)

sealed interface PickerTarget {
    data class Snippet(val id: Long) : PickerTarget
    data class Image(val image: ImageItem) : PickerTarget
}

class PickerNotice(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
) {
    val key: Long = System.nanoTime()
}

// Search matches the title or the text, ignoring case; a blank query matches everything.
fun snippetMatches(snippet: SnippetRecord, query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return snippet.title.contains(needle, ignoreCase = true) || snippet.text.contains(needle, ignoreCase = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerSheet(
    state: PickerState,
    actions: KeyboardActions,
    onSourcesResolved: (Long?) -> Unit,
    onCloseTarget: () -> Unit,
    onCopyImage: (ImageItem) -> Unit,
    onShareSnippet: (SnippetRecord) -> Unit,
    onShareImage: (ImageItem) -> Unit,
    onNoticeDismiss: (Long) -> Unit,
    onDismissed: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalKeyboardPalette.current
    val settings = remember { PastilleSettings.forContext(context) }
    val repository = remember { SnippetRepository.forContext(context) }
    val snippets by remember { repository.observeSnippets() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by remember { repository.observeCategories() }
        .collectAsStateWithLifecycle(initialValue = emptyList<CategoryRecord>())
    val snippetsOn = remember { settings.snippetsEnabled }
    val imagesOn = remember { settings.imagesEnabled }
    val enabledSourceIds = remember { settings.enabledImageSources }
    val mode = effectiveMode(state.mode, snippetsOn, imagesOn)

    var query by rememberSaveable { mutableStateOf("") }
    var hasImagePermission by remember { mutableStateOf(ImageSourceReader.hasPermission(context)) }
    var sources by remember { mutableStateOf(emptyList<ImageSource>()) }
    var images by remember { mutableStateOf(emptyList<ImageItem>()) }

    LaunchedEffect(mode) {
        if (mode != KeyboardMode.Images) return@LaunchedEffect
        hasImagePermission = ImageSourceReader.hasPermission(context)
        sources = withContext(Dispatchers.IO) {
            runCatching { ImageSourceReader.listSources(context) }.getOrDefault(emptyList())
        }
        onSourcesResolved(resolveSource(visibleSources(sources, enabledSourceIds), state.sourceId)?.bucketId)
    }
    LaunchedEffect(mode, state.sourceId) {
        if (mode != KeyboardMode.Images) return@LaunchedEffect
        val bucket = state.sourceId ?: return@LaunchedEffect
        images = withContext(Dispatchers.IO) {
            runCatching { ImageSourceReader.readRecent(context, bucket) }.getOrDefault(emptyList())
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    LaunchedEffect(state.closing) {
        if (!state.closing) return@LaunchedEffect
        runCatching { sheetState.hide() }
        onDismissed()
    }
    LaunchedEffect(state.notice?.key) {
        val notice = state.notice ?: return@LaunchedEffect
        delay(if (notice.actionLabel != null) 6_000 else 3_000)
        onNoticeDismiss(notice.key)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissed,
        sheetState = sheetState,
        containerColor = palette.tray,
        contentColor = palette.label,
        dragHandle = { SheetHeader(onSwitchKeyboard = actions::onSwitchKeyboard) },
    ) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val target = state.target) {
                    null -> Column(modifier = Modifier.fillMaxSize()) {
                        SearchField(
                            query = query,
                            onQueryChange = { query = it },
                            placeholder = if (mode == KeyboardMode.Images) "Search images" else "Search snippets",
                        )
                        if (snippetsOn && imagesOn) {
                            Box(modifier = Modifier.padding(vertical = 4.dp)) {
                                ModeSwitch(mode = mode, onChange = actions::onModeChange)
                            }
                        }
                        when (mode) {
                            KeyboardMode.Snippets -> SnippetsBrowse(
                                snippets = snippets,
                                categories = categories,
                                folderId = state.folderId,
                                query = query,
                                actions = actions,
                            )
                            KeyboardMode.Images -> ImagesBrowse(
                                hasPermission = hasImagePermission,
                                sources = visibleSources(sources, enabledSourceIds),
                                sourceId = state.sourceId,
                                images = images.filter { query.isBlank() || it.displayName.contains(query.trim(), ignoreCase = true) },
                                actions = actions,
                            )
                        }
                    }
                    is PickerTarget.Snippet -> {
                        val snippet = snippets.find { it.id == target.id }
                        if (snippet == null) {
                            LaunchedEffect(Unit) { onCloseTarget() }
                        } else {
                            SnippetActions(
                                snippet = snippet,
                                categories = categories,
                                actions = actions,
                                onBack = onCloseTarget,
                                onShare = { onShareSnippet(snippet) },
                            )
                        }
                    }
                    is PickerTarget.Image -> ImageActions(
                        image = target.image,
                        onBack = onCloseTarget,
                        onCopy = { onCopyImage(target.image) },
                        onShare = { onShareImage(target.image) },
                    )
                }
            }
            state.notice?.let { NoticeRow(notice = it, onDismiss = onNoticeDismiss) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetHeader(onSwitchKeyboard: () -> Unit) {
    val palette = LocalKeyboardPalette.current
    Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
        BottomSheetDefaults.DragHandle(modifier = Modifier.align(Alignment.Center), color = palette.icon)
        IconButton(onClick = onSwitchKeyboard, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
            Icon(Icons.Rounded.Keyboard, contentDescription = "Switch keyboard", tint = palette.icon)
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String) {
    val palette = LocalKeyboardPalette.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(placeholder, color = palette.icon) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = palette.icon) },
        shape = RoundedCornerShape(50),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = palette.key,
            unfocusedContainerColor = palette.key,
            focusedTextColor = palette.label,
            unfocusedTextColor = palette.label,
            cursorColor = palette.accent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun ColumnScope.SnippetsBrowse(
    snippets: List<SnippetRecord>,
    categories: List<CategoryRecord>,
    folderId: Long?,
    query: String,
    actions: KeyboardActions,
) {
    val folders = categories.sortedBy { it.position }
    val folder = folders.find { it.id == folderId }
    val searching = query.isNotBlank()
    if (!searching && folders.isNotEmpty()) {
        FolderChipRow(
            entries = listOf(ChipEntry(null, "All", Icons.Rounded.GridView)) +
                folders.map { ChipEntry(it.id, it.name, Icons.Rounded.Folder) },
            selectedId = folder?.id,
            onSelect = actions::onOpenFolder,
        )
    }
    // Search looks through every folder.
    val items = when {
        searching -> snippets.filter { snippetMatches(it, query) }
        folder != null -> snippets.filter { it.categoryId == folder.id }
        else -> snippets
    }
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when {
            items.isNotEmpty() -> SnippetGrid(items = items, actions = actions, tapVerb = "Copy")
            searching -> Message("No snippets match \"${query.trim()}\"")
            folder != null -> Message("Nothing in ${folder.name} yet")
            else -> Message("No snippets yet", button = "Open Pastille", onClick = actions::onOpenApp)
        }
    }
}

@Composable
private fun ColumnScope.ImagesBrowse(
    hasPermission: Boolean,
    sources: List<ImageSource>,
    sourceId: Long?,
    images: List<ImageItem>,
    actions: KeyboardActions,
) {
    if (!hasPermission) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Message("Allow photo access in Pastille to see your images here", button = "Open Pastille", onClick = actions::onOpenApp)
        }
        return
    }
    if (sources.isNotEmpty()) {
        FolderChipRow(
            entries = sources.map { ChipEntry(it.bucketId, it.name, sourceIcon(it)) },
            selectedId = sourceId,
            onSelect = { id -> id?.let(actions::onSelectSource) },
        )
    }
    ImageGrid(
        images = images,
        actions = actions,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        tapVerb = "Copy",
    )
}

@Composable
private fun Message(text: String, button: String? = null, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalKeyboardPalette.current.icon,
            textAlign = TextAlign.Center,
        )
        if (button != null) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
private fun BackTitle(title: String, onBack: () -> Unit) {
    val palette = LocalKeyboardPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = palette.icon)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = palette.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = onClick, modifier = modifier.height(56.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1)
    }
}

// The keyboard's long-press actions (design spec §7.2), plus Share for images.
@Composable
private fun SnippetActions(
    snippet: SnippetRecord,
    categories: List<CategoryRecord>,
    actions: KeyboardActions,
    onBack: () -> Unit,
    onShare: () -> Unit,
) {
    val palette = LocalKeyboardPalette.current
    val imageFile = snippet.imageFile
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BackTitle(displayTitle(snippet.title, snippet.text).ifBlank { "Image" }, onBack)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.key)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (imageFile != null) {
                ImageThumbnail(
                    fileName = imageFile,
                    contentDescription = snippet.title.ifBlank { "Image snippet" },
                    modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(8.dp)),
                    targetPx = 512,
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    text = snippet.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.icon,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(Icons.Rounded.Edit, "Edit in app", { actions.onEditSnippet(snippet) }, Modifier.weight(1f))
            ActionButton(Icons.Rounded.Delete, "Delete", { actions.onDeleteSnippet(snippet) }, Modifier.weight(1f))
            if (snippet.isImage) {
                ActionButton(Icons.Rounded.Share, "Share", onShare, Modifier.weight(1f))
            }
        }
        if (categories.isNotEmpty()) {
            Text(
                text = "Folder",
                style = MaterialTheme.typography.labelMedium,
                color = palette.icon,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "none") {
                    FilterToggleChip(
                        label = "No folder",
                        selected = snippet.categoryId == null,
                        onClick = { actions.onMoveToCategory(snippet.id, null) },
                        icon = Icons.Rounded.FolderOff,
                    )
                }
                items(categories.sortedBy { it.position }, key = { it.id }) { category ->
                    FilterToggleChip(
                        label = category.name,
                        selected = snippet.categoryId == category.id,
                        onClick = { actions.onMoveToCategory(snippet.id, category.id) },
                        icon = Icons.Rounded.Folder,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageActions(image: ImageItem, onBack: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit) {
    val palette = LocalKeyboardPalette.current
    val thumb = rememberThumb(image, 1024)
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BackTitle(image.displayName.substringBeforeLast('.').ifBlank { "Image" }, onBack)
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (thumb) {
                is ThumbState.Loaded -> Image(
                    bitmap = thumb.bitmap.asImageBitmap(),
                    contentDescription = image.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                ThumbState.Loading -> Unit
                ThumbState.Failed -> Icon(Icons.Rounded.BrokenImage, contentDescription = null, tint = palette.icon)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(Icons.Rounded.ContentCopy, "Copy", onCopy, Modifier.weight(1f))
            ActionButton(Icons.Rounded.Share, "Share", onShare, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NoticeRow(notice: PickerNotice, onDismiss: (Long) -> Unit) {
    val palette = LocalKeyboardPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.keyPressed)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = notice.message,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
        )
        if (notice.actionLabel != null) {
            TextButton(
                onClick = {
                    notice.onAction?.invoke()
                    onDismiss(notice.key)
                },
            ) {
                Text(notice.actionLabel, color = palette.accent)
            }
        }
    }
}
