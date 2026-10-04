package app.pastille.ime

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pastille.images.ImageThumbnail
import app.pastille.model.CategoryRecord
import app.pastille.model.SnippetRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val PANEL_CONTENT_HEIGHT = 320.dp

@Composable
fun KeyboardPanel(
    snippets: List<SnippetRecord>,
    categories: List<CategoryRecord>,
    selectedCategoryId: Long?,
    openCount: Int,
    screenshots: List<ScreenshotItem>,
    hasScreenshotPermission: Boolean,
    panelState: PanelState,
    addSources: AddSources?,
    strip: StatusStrip?,
    highlightedSnippetId: Long?,
    onBack: () -> Unit,
    onOpenAdd: () -> Unit,
    onSelectCategory: (Long?) -> Unit,
    onOpenApp: () -> Unit,
    onOpenPermissions: () -> Unit,
    onSwitchKeyboard: () -> Unit,
    onSnippetTap: (SnippetRecord) -> Unit,
    onSnippetLongPress: (SnippetRecord) -> Unit,
    onScreenshotTap: (ScreenshotItem) -> Unit,
    onAddFrom: (AddSource, Long?) -> Unit,
    onWriteInApp: (Long?) -> Unit,
    onPinToggle: (SnippetRecord) -> Unit,
    onEditSnippet: (SnippetRecord) -> Unit,
    onDeleteSnippet: (SnippetRecord) -> Unit,
    onMoveToCategory: (Long, Long?) -> Unit,
    onStripDismiss: (Long) -> Unit,
    onHighlightShown: () -> Unit,
    onHighlightNotInFilter: () -> Unit,
) {
    val selectedCategory = categories.find { it.id == selectedCategoryId }
    val visible = if (selectedCategory == null) snippets else snippets.filter { it.categoryId == selectedCategory.id }
    val chipState = rememberLazyListState()
    val actionsSnippet = (panelState as? PanelState.Actions)?.let { action ->
        snippets.find { it.id == action.snippetId }
    }
    val toolbarTitle = when (panelState) {
        PanelState.Browse -> null
        PanelState.Add -> "New snippet"
        is PanelState.Actions -> actionsTitle(actionsSnippet)
        PanelState.ChooseAlbum -> "Choose album"
    }

    LaunchedEffect(openCount, selectedCategoryId, categories) {
        if (categories.isEmpty()) return@LaunchedEffect
        val index = if (selectedCategoryId == null) {
            0
        } else {
            categories.indexOfFirst { it.id == selectedCategoryId } + 1
        }
        chipState.scrollToItem(index.coerceAtLeast(0))
    }

    LaunchedEffect(strip?.key) {
        val current = strip ?: return@LaunchedEffect
        delay(if (current.actionLabel != null) 6_000 else 4_000)
        onStripDismiss(current.key)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .padding(top = 8.dp, bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (panelState == PanelState.Browse) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (categories.isNotEmpty()) {
                            LazyRow(
                                state = chipState,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                item(key = "all") {
                                    FilterToggleChip(
                                        label = "All",
                                        selected = selectedCategoryId == null,
                                        onClick = { onSelectCategory(null) },
                                    )
                                }
                                items(categories, key = { it.id }) { category ->
                                    FilterToggleChip(
                                        label = category.name,
                                        selected = selectedCategoryId == category.id,
                                        onClick = { onSelectCategory(category.id) },
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(16.dp)
                                    .fillMaxHeight()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.surfaceContainer,
                                            ),
                                        ),
                                    ),
                            )
                        }
                    }
                    AssistChip(
                        onClick = onOpenAdd,
                        label = { Text("New") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            labelColor = MaterialTheme.colorScheme.onPrimary,
                            leadingIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        border = null,
                    )
                } else {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = toolbarTitle.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                ToolbarAction(
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    label = "Open Pastille",
                    onClick = onOpenApp,
                )
                ToolbarAction(
                    icon = Icons.Outlined.Keyboard,
                    label = "Switch keyboard",
                    onClick = onSwitchKeyboard,
                )
            }

            Column(modifier = Modifier.height(PANEL_CONTENT_HEIGHT)) {
                if (strip != null) {
                    StatusStripRow(strip = strip, onDismiss = onStripDismiss)
                }
                val slideOffset = with(LocalDensity.current) { 8.dp.roundToPx() }
                AnimatedContent(
                    targetState = panelState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        (fadeIn(tween(150)) + slideInVertically(tween(150)) { slideOffset }) togetherWith
                            fadeOut(tween(150))
                    },
                    label = "panel",
                ) { state ->
                    when (state) {
                        PanelState.Browse -> BrowseContent(
                            snippets = snippets,
                            visible = visible,
                            selectedCategory = selectedCategory,
                            selectedCategoryId = selectedCategoryId,
                            screenshots = screenshots,
                            hasScreenshotPermission = hasScreenshotPermission,
                            highlightedSnippetId = highlightedSnippetId,
                            onOpenAdd = onOpenAdd,
                            onOpenPermissions = onOpenPermissions,
                            onSnippetTap = onSnippetTap,
                            onSnippetLongPress = onSnippetLongPress,
                            onPinToggle = onPinToggle,
                            onEditSnippet = onEditSnippet,
                            onDeleteSnippet = onDeleteSnippet,
                            onScreenshotTap = onScreenshotTap,
                            onHighlightShown = onHighlightShown,
                            onHighlightNotInFilter = onHighlightNotInFilter,
                        )
                        PanelState.Add -> AddContent(
                            sources = addSources,
                            categories = categories,
                            initialCategoryId = selectedCategoryId,
                            onAddFrom = onAddFrom,
                            onWriteInApp = onWriteInApp,
                            onOpenApp = onOpenApp,
                        )
                        is PanelState.Actions -> ActionsContent(
                            snippet = snippets.find { it.id == state.snippetId },
                            categories = categories,
                            onBack = onBack,
                            onPinToggle = onPinToggle,
                            onEditSnippet = onEditSnippet,
                            onDeleteSnippet = onDeleteSnippet,
                            onMoveToCategory = { categoryId ->
                                onMoveToCategory(state.snippetId, categoryId)
                            },
                        )
                        PanelState.ChooseAlbum -> Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

private fun actionsTitle(snippet: SnippetRecord?): String {
    if (snippet == null) return ""
    val base = displayTitle(snippet.title, snippet.text)
    if (base.isNotBlank()) return base
    return if (snippet.isImage) "Image" else ""
}

@Composable
private fun StatusStripRow(strip: StatusStrip, onDismiss: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strip.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
        )
        if (strip.actionLabel != null) {
            TextButton(
                onClick = {
                    strip.onAction?.invoke()
                    onDismiss(strip.key)
                },
            ) {
                Text(strip.actionLabel, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun BrowseContent(
    snippets: List<SnippetRecord>,
    visible: List<SnippetRecord>,
    selectedCategory: CategoryRecord?,
    selectedCategoryId: Long?,
    screenshots: List<ScreenshotItem>,
    hasScreenshotPermission: Boolean,
    highlightedSnippetId: Long?,
    onOpenAdd: () -> Unit,
    onOpenPermissions: () -> Unit,
    onSnippetTap: (SnippetRecord) -> Unit,
    onSnippetLongPress: (SnippetRecord) -> Unit,
    onPinToggle: (SnippetRecord) -> Unit,
    onEditSnippet: (SnippetRecord) -> Unit,
    onDeleteSnippet: (SnippetRecord) -> Unit,
    onScreenshotTap: (ScreenshotItem) -> Unit,
    onHighlightShown: () -> Unit,
    onHighlightNotInFilter: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(highlightedSnippetId, snippets, selectedCategoryId) {
        val id = highlightedSnippetId ?: return@LaunchedEffect
        val current = snippets.find { it.id == id } ?: return@LaunchedEffect
        if (selectedCategoryId != null && current.categoryId != selectedCategoryId) {
            onHighlightNotInFilter()
            return@LaunchedEffect
        }
        val index = visible.indexOfFirst { it.id == id }
        if (index == -1) return@LaunchedEffect
        gridState.animateScrollToItem(index)
        delay(650)
        onHighlightShown()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (hasScreenshotPermission) {
            if (screenshots.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(screenshots, key = { it.uri.toString() }) { item ->
                        ScreenshotThumb(item = item, onTap = { onScreenshotTap(item) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        } else {
            TextButton(
                onClick = onOpenPermissions,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Text("Allow photo access for screenshots")
            }
        }

        if (snippets.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No snippets yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = onOpenAdd) {
                    Text("Add a snippet")
                }
            }
        } else if (visible.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Nothing in ${selectedCategory?.name.orEmpty()} yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onOpenAdd) {
                    Text("New snippet")
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.id }) { snippet ->
                    SnippetCard(
                        snippet = snippet,
                        highlighted = snippet.id == highlightedSnippetId,
                        onTap = { onSnippetTap(snippet) },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSnippetLongPress(snippet)
                        },
                        onPinToggle = { onPinToggle(snippet) },
                        onEdit = { onEditSnippet(snippet) },
                        onDelete = { onDeleteSnippet(snippet) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddContent(
    sources: AddSources?,
    categories: List<CategoryRecord>,
    initialCategoryId: Long?,
    onAddFrom: (AddSource, Long?) -> Unit,
    onWriteInApp: (Long?) -> Unit,
    onOpenApp: () -> Unit,
) {
    var addCategoryId by remember { mutableStateOf(initialCategoryId) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val field = sources?.field
        val fieldEnabled = field is HostRead.Text
        SourceRow(
            icon = Icons.Outlined.TextFields,
            headline = if ((field as? HostRead.Text)?.fromSelection == true) "From selection" else "From text field",
            supporting = when (field) {
                is HostRead.Text -> previewOf(field.text)
                HostRead.Empty -> "Text field is empty"
                HostRead.Sensitive -> "Not available in this field"
                HostRead.Unavailable, null -> "This app doesn't share its text"
            },
            enabled = fieldEnabled,
            onClick = { onAddFrom(AddSource.Field, addCategoryId) },
        )
        val clip = sources?.clip
        val clipEnabled = clip is ClipRead.Text
        SourceRow(
            icon = Icons.Outlined.ContentPaste,
            headline = "From clipboard",
            supporting = when (clip) {
                is ClipRead.Text -> if (clip.sensitive) "Sensitive content" else previewOf(clip.text)
                ClipRead.Empty, null -> "Clipboard is empty"
            },
            enabled = clipEnabled,
            onClick = { onAddFrom(AddSource.Clipboard, addCategoryId) },
        )
        SourceRow(
            icon = Icons.Outlined.EditNote,
            headline = "Write in Pastille app",
            supporting = null,
            enabled = true,
            onClick = { onWriteInApp(addCategoryId) },
            trailing = {
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        if (categories.isNotEmpty()) {
            CategoryChipsRow(
                categories = categories,
                selectedId = addCategoryId,
                onSelect = { addCategoryId = it },
                trailingNewInApp = true,
                onOpenApp = onOpenApp,
            )
        }
    }
}

@Composable
private fun SourceRow(
    icon: ImageVector,
    headline: String,
    supporting: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    val disabled = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ListItem(
        headlineContent = {
            Text(
                headline,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else disabled,
            )
        },
        supportingContent = if (supporting != null) {
            {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else disabled,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            null
        },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else disabled,
            )
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.heightIn(min = 64.dp).clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun CategoryChipsRow(
    categories: List<CategoryRecord>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    trailingNewInApp: Boolean = false,
    onOpenApp: () -> Unit = {},
) {
    Column {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 16.dp),
            )
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item(key = "none") {
                    FilterToggleChip(
                        label = "None",
                        selected = selectedId == null,
                        onClick = { onSelect(null) },
                    )
                }
                items(categories, key = { it.id }) { category ->
                    FilterToggleChip(
                        label = category.name,
                        selected = selectedId == category.id,
                        onClick = { onSelect(category.id) },
                    )
                }
                if (trailingNewInApp) {
                    item(key = "new") {
                        AssistChip(
                            onClick = onOpenApp,
                            label = { Text("New in app") },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionsContent(
    snippet: SnippetRecord?,
    categories: List<CategoryRecord>,
    onBack: () -> Unit,
    onPinToggle: (SnippetRecord) -> Unit,
    onEditSnippet: (SnippetRecord) -> Unit,
    onDeleteSnippet: (SnippetRecord) -> Unit,
    onMoveToCategory: (Long?) -> Unit,
) {
    if (snippet == null) {
        LaunchedEffect(Unit) { onBack() }
        Box(modifier = Modifier.fillMaxSize())
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (snippet.isImage) {
                snippet.imageFile?.let { fileName ->
                    ImageThumbnail(
                        fileName = fileName,
                        contentDescription = snippet.title.ifBlank { "Image snippet" },
                        modifier = Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(8.dp)),
                        targetPx = 256,
                        contentScale = ContentScale.Crop,
                    )
                }
            } else {
                Text(
                    text = snippet.text.ifBlank { snippet.title },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PanelActionButton(
                icon = if (snippet.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                label = if (snippet.pinned) "Unpin" else "Pin",
                onClick = { onPinToggle(snippet) },
                modifier = Modifier.weight(1f),
            )
            PanelActionButton(
                icon = Icons.Outlined.Edit,
                label = "Edit",
                onClick = { onEditSnippet(snippet) },
                modifier = Modifier.weight(1f),
            )
            PanelActionButton(
                icon = Icons.Outlined.Delete,
                label = "Delete",
                onClick = { onDeleteSnippet(snippet) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            )
        }
        if (categories.isNotEmpty()) {
            CategoryChipsRow(
                categories = categories,
                selectedId = snippet.categoryId,
                onSelect = onMoveToCategory,
            )
        }
    }
}

@Composable
private fun PanelActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors = ButtonDefaults.filledTonalButtonColors(),
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        contentPadding = PaddingValues(8.dp),
        colors = colors,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun FilterToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolbarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SnippetCard(
    snippet: SnippetRecord,
    highlighted: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onPinToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (highlighted) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = tween(600),
        label = "highlight",
    )
    Card(
        colors = CardDefaults.cardColors(
            containerColor = container,
        ),
        modifier = Modifier.heightIn(min = 56.dp).combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(if (snippet.pinned) "Unpin" else "Pin") {
                        onPinToggle()
                        true
                    },
                    CustomAccessibilityAction("Edit") {
                        onEdit()
                        true
                    },
                    CustomAccessibilityAction("Delete") {
                        onDelete()
                        true
                    },
                )
            },
    ) {
        Column(Modifier.padding(12.dp)) {
            if (snippet.isImage) {
                snippet.imageFile?.let { fileName ->
                    ImageThumbnail(
                        fileName = fileName,
                        contentDescription = snippet.title.ifBlank { "Image snippet" },
                        modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(8.dp)),
                        targetPx = 256,
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (snippet.pinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = if (snippet.isImage) snippet.title.ifBlank { "Image" } else snippet.title.ifBlank { snippet.text },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!snippet.isImage && snippet.title.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = snippet.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private sealed interface ThumbState {
    data object Loading : ThumbState
    data object Failed : ThumbState
    data class Loaded(val bitmap: Bitmap) : ThumbState
}

@Composable
private fun ScreenshotThumb(item: ScreenshotItem, onTap: () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    var state by remember(item.uri) { mutableStateOf<ThumbState>(ThumbState.Loading) }
    LaunchedEffect(item.uri) {
        val bitmap = withContext(Dispatchers.IO) {
            ScreenshotReader.loadThumbnail(resolver, item.uri)
        }
        state = if (bitmap != null) ThumbState.Loaded(bitmap) else ThumbState.Failed
    }
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = "Insert screenshot", onClick = onTap),
    ) {
        Crossfade(targetState = state, animationSpec = tween(150), label = "thumb") { current ->
            when (current) {
                is ThumbState.Loading -> ThumbPlaceholder(shimmer = true)
                is ThumbState.Loaded -> {
                    Image(
                        bitmap = current.bitmap.asImageBitmap(),
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is ThumbState.Failed -> {
                    ThumbPlaceholder(shimmer = false) {
                        Icon(
                            Icons.Outlined.BrokenImage,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbPlaceholder(shimmer: Boolean, content: @Composable () -> Unit = {}) {
    val alpha = if (shimmer) {
        val pulse by rememberInfiniteTransition(label = "shimmer").animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "alpha",
        )
        pulse
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
