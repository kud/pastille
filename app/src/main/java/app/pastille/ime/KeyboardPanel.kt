package app.pastille.ime

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pastille.images.ImageThumbnail
import app.pastille.model.CategoryRecord
import app.pastille.model.SnippetRecord
import app.pastille.settings.KeyboardMode
import app.pastille.settings.KeyboardStyle
import app.pastille.settings.PanelHeight
import app.pastille.settings.TOOLBAR_HEIGHT_DP
import app.pastille.settings.tileColumns
import app.pastille.share.isMeaningfulImageName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class KeyboardUiState(
    val mode: KeyboardMode = KeyboardMode.Snippets,
    val snippets: List<SnippetRecord> = emptyList(),
    val categories: List<CategoryRecord> = emptyList(),
    val folderId: Long? = null,
    val imageSources: List<ImageSource> = emptyList(),
    val sourceId: Long? = null,
    val images: List<ImageItem> = emptyList(),
    val hasImagePermission: Boolean = false,
    val panelState: PanelState = PanelState.Browse,
    val addSources: AddSources? = null,
    val strip: StatusStrip? = null,
    val highlightedSnippetId: Long? = null,
    val pickerStyle: KeyboardStyle = KeyboardStyle.Auto,
    val darkTheme: Boolean = true,
    val savedStyle: KeyboardStyle = KeyboardStyle.Auto,
    val heightPreset: PanelHeight = PanelHeight.Default,
    val landscape: Boolean = false,
    val returnAfterSnippet: Boolean = true,
    val returnAfterImage: Boolean = false,
    val enabledSourceIds: Set<Long>? = null,
    val snippetsEnabled: Boolean = true,
    val imagesEnabled: Boolean = true,
)

interface KeyboardActions {
    fun onModeChange(mode: KeyboardMode) {}
    fun onOpenFolder(categoryId: Long?) {}
    fun onBack() {}
    fun onOpenAdd() {}
    fun onOpenApp() {}
    fun onSwitchKeyboard() {}
    fun onSnippetTap(snippet: SnippetRecord) {}
    fun onSnippetLongPress(snippet: SnippetRecord) {}
    fun onSelectSource(bucketId: Long) {}
    fun onImageTap(image: ImageItem) {}
    fun onImageLongPress(image: ImageItem) {}
    fun onAddFrom(source: AddSource, categoryId: Long?) {}
    fun onWriteInApp(categoryId: Long?) {}
    fun onEditSnippet(snippet: SnippetRecord) {}
    fun onDeleteSnippet(snippet: SnippetRecord) {}
    fun onMoveToCategory(snippetId: Long, categoryId: Long?) {}
    fun onStripDismiss(key: Long) {}
    fun onHighlightShown() {}
    fun onPickStyle(style: KeyboardStyle) {}
    fun onStyleDone() {}
    fun onStyleNotNow() {}
    fun onOpenSettings() {}
    fun onOpenAllSettings() {}
    fun onSetStyle(style: KeyboardStyle) {}
    fun onSetHeight(height: PanelHeight) {}
    fun onSetReturn(image: Boolean, enabled: Boolean) {}
    fun onOpenImageFolders() {}
    fun onSetModeEnabled(mode: KeyboardMode, enabled: Boolean) {}
    fun onOpenReorder() {}
    fun onReorderFolders(newOrder: List<Long>) {}
    fun onReorderSnippets(shown: List<SnippetRecord>, newOrder: List<Long>) {}
    fun onSetShownSources(bucketIds: Set<Long>) {}
}

object NoKeyboardActions : KeyboardActions

@Composable
fun KeyboardPanel(
    state: KeyboardUiState,
    actions: KeyboardActions,
    contentHeight: Dp,
    modifier: Modifier = Modifier,
    previewMode: Boolean = false,
) {
    val palette = LocalKeyboardPalette.current
    val actionsSnippet = (state.panelState as? PanelState.Actions)?.let { action ->
        state.snippets.find { it.id == action.snippetId }
    }
    val title = when (val panel = state.panelState) {
        PanelState.Browse -> null
        PanelState.Add -> "New snippet"
        is PanelState.Actions -> actionsSnippet?.let { displayTitle(it.title, it.text).ifBlank { "Image" } }.orEmpty()
        is PanelState.Preview -> previewTitle(panel.image, state.imageSources.find { it.bucketId == state.sourceId })
        PanelState.Style -> "Keyboard style"
        PanelState.Settings -> "Keyboard settings"
        PanelState.ImageFolders -> "Image folders"
        PanelState.Reorder -> "Hold and drag to reorder"
    }

    LaunchedEffect(state.strip?.key) {
        val current = state.strip ?: return@LaunchedEffect
        delay(if (current.actionLabel != null) 6_000 else 4_000)
        actions.onStripDismiss(current.key)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = palette.tray,
        contentColor = palette.label,
    ) {
        val insets = if (previewMode) {
            Modifier
        } else {
            Modifier.windowInsetsPadding(
                WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            )
        }
        Column(modifier = Modifier.fillMaxWidth().then(insets)) {
            Toolbar(state = state, actions = actions, title = title)
            Box(modifier = Modifier.fillMaxWidth().height(contentHeight).clipToBounds()) {
                PanelContent(state = state, actions = actions, actionsSnippet = actionsSnippet)
                Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    StatusStripArea(strip = state.strip, actions = actions)
                }
            }
        }
    }
}

private fun previewTitle(image: ImageItem, source: ImageSource?): String {
    if (isMeaningfulImageName(image.displayName)) return image.displayName.substringBeforeLast('.')
    val label = when {
        source == null -> "Image"
        source.isScreenshots -> "Screenshot"
        else -> source.name
    }
    if (image.dateAddedSeconds <= 0) return label
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(image.dateAddedSeconds * 1000))
    return "$label · $time"
}

@Composable
private fun Toolbar(state: KeyboardUiState, actions: KeyboardActions, title: String?) {
    val palette = LocalKeyboardPalette.current
    val reduceMotion = LocalReduceMotion.current
    Row(
        modifier = Modifier.fillMaxWidth().height(TOOLBAR_HEIGHT_DP.dp).background(palette.strip),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    if (reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(tween(150, delayMillis = 60)) togetherWith fadeOut(tween(90))
                    }
                },
                contentAlignment = Alignment.CenterStart,
                label = "toolbarLeading",
            ) { current ->
                if (current == null) {
                    if (state.snippetsEnabled && state.imagesEnabled) {
                        ModeSwitch(mode = state.mode, onChange = actions::onModeChange)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = actions::onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = palette.icon,
                            )
                        }
                        Text(
                            text = current,
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.padding(end = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (state.mode == KeyboardMode.Snippets) {
                    ToolbarAction(icon = Icons.Rounded.Add, label = "New snippet", onClick = actions::onOpenAdd)
                }
            }
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (state.mode == KeyboardMode.Snippets) {
                    ToolbarAction(icon = Icons.Rounded.SwapVert, label = "Reorder snippets", onClick = actions::onOpenReorder)
                }
            }
            ToolbarAction(icon = Icons.Rounded.Keyboard, label = "Switch keyboard", onClick = actions::onSwitchKeyboard)
            ToolbarAction(icon = Icons.Rounded.Settings, label = "Keyboard settings", onClick = actions::onOpenSettings)
        }
    }
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
            Icon(icon, contentDescription = label, tint = LocalKeyboardPalette.current.icon)
        }
    }
}

@Composable
private fun StatusStripArea(strip: StatusStrip?, actions: KeyboardActions) {
    val reduceMotion = LocalReduceMotion.current
    var shown by remember { mutableStateOf(strip) }
    if (strip != null) shown = strip
    AnimatedVisibility(
        visible = strip != null,
        enter = fadeIn(tween(if (reduceMotion) 0 else 150)),
        exit = fadeOut(tween(if (reduceMotion) 0 else 120)),
    ) {
        shown?.let { StatusStripRow(strip = it, onDismiss = actions::onStripDismiss) }
    }
}

@Composable
private fun StatusStripRow(strip: StatusStrip, onDismiss: (Long) -> Unit) {
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
            text = strip.message,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.label,
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
                Text(strip.actionLabel, color = palette.accent)
            }
        }
    }
}

// Every content swap stays inside the fixed content height: no size animation.
private fun <S> AnimatedContentTransitionScope<S>.noSizeChange(transform: ContentTransform): ContentTransform =
    transform using SizeTransform(clip = false) { _, _ -> snap() }

@Composable
private fun PanelContent(
    state: KeyboardUiState,
    actions: KeyboardActions,
    actionsSnippet: SnippetRecord?,
) {
    val reduceMotion = LocalReduceMotion.current
    val rise = with(LocalDensity.current) { 24.dp.roundToPx() }
    AnimatedContent(
        targetState = state.panelState,
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart,
        transitionSpec = {
            val enterFade = fadeIn(
                tween(PastilleMotion.ENTER_MS - PastilleMotion.FADE_IN_DELAY_MS, delayMillis = PastilleMotion.FADE_IN_DELAY_MS),
            )
            val transform = when {
                reduceMotion -> EnterTransition.None togetherWith ExitTransition.None
                targetState != PanelState.Browse ->
                    (slideInVertically(tween(PastilleMotion.ENTER_MS, easing = PastilleMotion.EmphasizedDecelerate)) { rise } + enterFade) togetherWith
                        fadeOut(tween(90))
                else ->
                    enterFade togetherWith (
                        slideOutVertically(tween(PastilleMotion.EXIT_MS + 30, easing = PastilleMotion.EmphasizedAccelerate)) { rise } +
                            fadeOut(tween(PastilleMotion.EXIT_MS + 30))
                        )
            }
            noSizeChange(transform)
        },
        label = "panel",
    ) { panel ->
        when (panel) {
            PanelState.Browse -> BrowseContent(state = state, actions = actions)
            PanelState.Add -> AddContent(
                sources = state.addSources,
                categories = state.categories,
                initialCategoryId = state.folderId,
                actions = actions,
            )
            is PanelState.Actions -> ActionsContent(
                snippet = actionsSnippet,
                categories = state.categories,
                actions = actions,
            )
            is PanelState.Preview -> PreviewContent(image = panel.image, actions = actions)
            PanelState.Style -> StylePickerContent(state = state, actions = actions)
            PanelState.Settings -> SettingsContent(state = state, actions = actions)
            PanelState.ImageFolders -> ImageFoldersContent(state = state, actions = actions)
            PanelState.Reorder -> ReorderContent(state = state, actions = actions)
        }
    }
}

@Composable
private fun BrowseContent(state: KeyboardUiState, actions: KeyboardActions) {
    val reduceMotion = LocalReduceMotion.current
    val offset = with(LocalDensity.current) { 32.dp.roundToPx() }
    AnimatedContent(
        targetState = state.mode,
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart,
        transitionSpec = {
            val dir = if (targetState == KeyboardMode.Images) 1 else -1
            val transform = if (reduceMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (
                    slideInHorizontally(tween(PastilleMotion.ENTER_MS, easing = PastilleMotion.EmphasizedDecelerate)) { dir * offset } +
                        fadeIn(tween(PastilleMotion.ENTER_MS - PastilleMotion.FADE_IN_DELAY_MS, delayMillis = PastilleMotion.FADE_IN_DELAY_MS))
                    ) togetherWith (
                    slideOutHorizontally(tween(PastilleMotion.EXIT_MS, easing = PastilleMotion.EmphasizedAccelerate)) { -dir * offset } +
                        fadeOut(tween(PastilleMotion.EXIT_MS))
                    )
            }
            noSizeChange(transform)
        },
        label = "mode",
    ) { mode ->
        when (mode) {
            KeyboardMode.Snippets -> FolderContent(state = state, actions = actions)
            KeyboardMode.Images -> ImagesContent(state = state, actions = actions)
        }
    }
}

@Composable
private fun FolderContent(state: KeyboardUiState, actions: KeyboardActions) {
    val reduceMotion = LocalReduceMotion.current
    val offset = with(LocalDensity.current) { 32.dp.roundToPx() }
    val folders = state.categories.sortedBy { it.position }
    val folderId = state.folderId?.takeIf { id -> folders.any { it.id == id } }
    val order = listOf<Long?>(null) + folders.map { it.id }
    Column(modifier = Modifier.fillMaxSize()) {
        if (folders.isNotEmpty()) {
            FolderChipRow(
                entries = listOf(ChipEntry(null, "All", Icons.Rounded.GridView)) +
                    folders.map { ChipEntry(it.id, it.name, Icons.Rounded.Folder) },
                selectedId = folderId,
                onSelect = actions::onOpenFolder,
            )
        }
        AnimatedContent(
            targetState = folderId,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.TopStart,
            transitionSpec = {
                val dir = if (order.indexOf(targetState) >= order.indexOf(initialState)) 1 else -1
                val transform = if (reduceMotion) {
                    fadeIn(tween(PastilleMotion.EXIT_MS)) togetherWith fadeOut(tween(PastilleMotion.EXIT_MS))
                } else {
                    (
                        slideInHorizontally(tween(PastilleMotion.ENTER_MS, easing = PastilleMotion.EmphasizedDecelerate)) { dir * offset } +
                            fadeIn(tween(PastilleMotion.ENTER_MS - PastilleMotion.FADE_IN_DELAY_MS, delayMillis = PastilleMotion.FADE_IN_DELAY_MS))
                        ) togetherWith (
                        slideOutHorizontally(tween(PastilleMotion.EXIT_MS, easing = PastilleMotion.EmphasizedAccelerate)) { -dir * offset } +
                            fadeOut(tween(PastilleMotion.EXIT_MS))
                        )
                }
                noSizeChange(transform)
            },
            label = "folder",
        ) { shownFolderId ->
            SnippetsPage(state = state, folderId = shownFolderId, actions = actions)
        }
    }
}

@Composable
private fun SnippetsPage(state: KeyboardUiState, folderId: Long?, actions: KeyboardActions) {
    val folder = state.categories.find { it.id == folderId }
    val items = if (folder == null) state.snippets else state.snippets.filter { it.categoryId == folder.id }
    val gridState = rememberLazyGridState()

    LaunchedEffect(state.highlightedSnippetId, items) {
        val id = state.highlightedSnippetId ?: return@LaunchedEffect
        val index = items.indexOfFirst { it.id == id }
        if (index == -1) return@LaunchedEffect
        // Let the return-to-Browse transition settle before the flash.
        delay(PastilleMotion.ENTER_MS.toLong())
        gridState.animateScrollToItem(index)
        delay(650)
        actions.onHighlightShown()
    }

    when {
        items.isEmpty() && folder != null -> EmptyState(
            message = "Nothing in ${folder.name} yet",
            button = "New snippet",
            onClick = actions::onOpenAdd,
        )
        items.isEmpty() -> EmptyState(
            message = "No snippets yet",
            button = "Add a snippet",
            onClick = actions::onOpenAdd,
        )
        else -> SnippetGrid(
            items = items,
            actions = actions,
            gridState = gridState,
            highlightedSnippetId = state.highlightedSnippetId,
        )
    }
}

@Composable
private fun EmptyState(message: String, button: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalKeyboardPalette.current.icon,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onClick) {
            Text(button)
        }
    }
}

// Gboard-style key: flat, no ripple, a colour change on press.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeyButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    container: Color? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val palette = LocalKeyboardPalette.current
    val reduceMotion = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val color by animateColorAsState(
        targetValue = container ?: if (pressed) palette.keyPressed else palette.key,
        animationSpec = tween(60),
        label = "keyColour",
    )
    val flash by animateFloatAsState(
        targetValue = if (highlighted) 0.25f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(600),
        label = "keyFlash",
    )
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(color)
            .background(palette.accent.copy(alpha = flash))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun KeyLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = LocalKeyboardPalette.current.label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun ImagesContent(state: KeyboardUiState, actions: KeyboardActions) {
    if (!state.hasImagePermission) {
        EmptyState(
            message = "Allow photo access in Pastille to see your images here",
            button = "Open Pastille",
            onClick = actions::onOpenApp,
        )
        return
    }
    val sources = visibleSources(state.imageSources, state.enabledSourceIds)
    if (sources.isEmpty() && state.imageSources.isNotEmpty()) {
        EmptyState(
            message = "No image folders chosen",
            button = "Choose folders",
            onClick = actions::onOpenImageFolders,
        )
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
    FolderChipRow(
        entries = sources.map { ChipEntry(it.bucketId, it.name, sourceIcon(it)) },
        selectedId = state.sourceId,
        onSelect = { id -> id?.let(actions::onSelectSource) },
    )
    ImageGrid(
        images = state.images,
        actions = actions,
        modifier = Modifier.weight(1f).fillMaxWidth(),
    )
    }
}

@Composable
private fun PreviewContent(image: ImageItem, actions: KeyboardActions) {
    val state = rememberThumb(image, 1024)
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (state) {
                is ThumbState.Loaded -> Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = image.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                is ThumbState.Loading -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))) {
                    ThumbPlaceholder(shimmer = true)
                }
                is ThumbState.Failed -> Icon(
                    Icons.Rounded.BrokenImage,
                    contentDescription = null,
                    tint = LocalKeyboardPalette.current.icon,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(
            onClick = { actions.onImageTap(image) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Insert")
        }
    }
}

@Composable
private fun AddContent(
    sources: AddSources?,
    categories: List<CategoryRecord>,
    initialCategoryId: Long?,
    actions: KeyboardActions,
) {
    var addCategoryId by remember { mutableStateOf(initialCategoryId) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val field = sources?.field
        SourceRow(
            icon = Icons.Rounded.TextFields,
            headline = if ((field as? HostRead.Text)?.fromSelection == true) "From selection" else "From text field",
            supporting = when (field) {
                is HostRead.Text -> previewOf(field.text)
                HostRead.Empty -> "Text field is empty"
                HostRead.Sensitive -> "Not available in this field"
                HostRead.Unavailable, null -> "This app doesn't share its text"
            },
            enabled = field is HostRead.Text,
            onClick = { actions.onAddFrom(AddSource.Field, addCategoryId) },
        )
        val clip = sources?.clip
        SourceRow(
            icon = Icons.Rounded.ContentPaste,
            headline = "From clipboard",
            supporting = when (clip) {
                is ClipRead.Text -> if (clip.sensitive) "Sensitive content" else previewOf(clip.text)
                ClipRead.Empty, null -> "Clipboard is empty"
            },
            enabled = clip is ClipRead.Text,
            onClick = { actions.onAddFrom(AddSource.Clipboard, addCategoryId) },
        )
        SourceRow(
            icon = Icons.Rounded.EditNote,
            headline = "Write in Pastille app",
            supporting = null,
            enabled = true,
            onClick = { actions.onWriteInApp(addCategoryId) },
            trailing = {
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = LocalKeyboardPalette.current.icon,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        if (categories.isNotEmpty()) {
            FolderChipsRow(
                categories = categories,
                selectedId = addCategoryId,
                onSelect = { addCategoryId = it },
                trailingNewInApp = true,
                onOpenApp = actions::onOpenApp,
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
    val palette = LocalKeyboardPalette.current
    val disabled = palette.label.copy(alpha = 0.38f)
    ListItem(
        headlineContent = {
            Text(headline, color = if (enabled) palette.label else disabled)
        },
        supportingContent = if (supporting != null) {
            {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) palette.icon else disabled,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            null
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = if (enabled) palette.icon else disabled)
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.heightIn(min = 64.dp).clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun FolderChipsRow(
    categories: List<CategoryRecord>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    trailingNewInApp: Boolean = false,
    onOpenApp: () -> Unit = {},
) {
    val palette = LocalKeyboardPalette.current
    Column {
        HorizontalDivider(color = palette.key)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Folder",
                style = MaterialTheme.typography.labelMedium,
                color = palette.icon,
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
                        label = "No folder",
                        selected = selectedId == null,
                        onClick = { onSelect(null) },
                        icon = Icons.Rounded.FolderOff,
                    )
                }
                items(categories.sortedBy { it.position }, key = { it.id }) { category ->
                    FilterToggleChip(
                        label = category.name,
                        selected = selectedId == category.id,
                        onClick = { onSelect(category.id) },
                        icon = Icons.Rounded.Folder,
                    )
                }
                if (trailingNewInApp) {
                    item(key = "new") {
                        AssistChip(
                            onClick = onOpenApp,
                            label = { Text("New in app") },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Rounded.OpenInNew,
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
    actions: KeyboardActions,
) {
    if (snippet == null) {
        LaunchedEffect(Unit) { actions.onBack() }
        Box(modifier = Modifier.fillMaxSize())
        return
    }
    val palette = LocalKeyboardPalette.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.key)
                .clickable(onClickLabel = "Edit in app") { actions.onEditSnippet(snippet) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val imageFile = snippet.imageFile
            if (imageFile != null) {
                ImageThumbnail(
                    fileName = imageFile,
                    contentDescription = snippet.title.ifBlank { "Image snippet" },
                    modifier = Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(8.dp)),
                    targetPx = 256,
                    contentScale = ContentScale.Crop,
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
            PanelActionButton(
                icon = Icons.Rounded.Edit,
                label = "Edit in app",
                onClick = { actions.onEditSnippet(snippet) },
                modifier = Modifier.weight(1f),
            )
            PanelActionButton(
                icon = Icons.Rounded.Delete,
                label = "Delete",
                onClick = { actions.onDeleteSnippet(snippet) },
                modifier = Modifier.weight(1f),
            )
        }
        if (categories.isNotEmpty()) {
            FolderChipsRow(
                categories = categories,
                selectedId = snippet.categoryId,
                onSelect = { actions.onMoveToCategory(snippet.id, it) },
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
) {
    val contentColor = LocalKeyboardPalette.current.label
    KeyButton(
        onClick = onClick,
        onLongClick = null,
        modifier = modifier.height(64.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor, maxLines = 1)
        }
    }
}

@Composable
private fun StylePickerContent(state: KeyboardUiState, actions: KeyboardActions) {
    val palette = LocalKeyboardPalette.current
    val available = materialYouAvailable()
    val choices = buildList {
        if (available) add(KeyboardStyle.MaterialYou)
        add(KeyboardStyle.GboardDark)
        add(KeyboardStyle.GboardLight)
    }
    val selected = resolveStyle(state.pickerStyle, available).let {
        if (it == KeyboardStyle.Auto) {
            if (state.darkTheme) KeyboardStyle.GboardDark else KeyboardStyle.GboardLight
        } else {
            it
        }
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
        Text(
            text = "Match your keyboard",
            style = MaterialTheme.typography.titleSmall,
            color = palette.label,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            text = "Pick the look closest to your usual keyboard. You can change it later in Pastille settings.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.labelSecondary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            choices.forEach { style ->
                StyleTile(
                    style = style,
                    selected = style == selected,
                    darkTheme = state.darkTheme,
                    onClick = { actions.onPickStyle(style) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = actions::onStyleNotNow) {
                Text("Not now", color = palette.label)
            }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = actions::onStyleDone) {
                Text("Done")
            }
        }
    }
}

private fun styleName(style: KeyboardStyle): String = when (style) {
    KeyboardStyle.MaterialYou -> "Material You"
    KeyboardStyle.GboardDark -> "Dark"
    KeyboardStyle.GboardLight -> "Light"
    KeyboardStyle.Auto -> "Auto"
}

@Composable
private fun StyleTile(
    style: KeyboardStyle,
    selected: Boolean,
    darkTheme: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val current = LocalKeyboardPalette.current
    val tile = remember(style, darkTheme) { keyboardPalette(context, style, darkTheme) }
    val name = styleName(style)
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .height(112.dp)
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) current.accent else current.key,
                shape = shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "$name style" },
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(tile.tray)) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().height(16.dp).background(tile.strip),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(3) {
                        Box(Modifier.size(4.dp).clip(CircleShape).background(tile.icon))
                    }
                }
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(2) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(50))
                                .background(tile.key),
                        )
                    }
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 20.dp, end = 4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(current.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = current.onAccent,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = current.label,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun SettingsContent(state: KeyboardUiState, actions: KeyboardActions) {
    val styles = buildList {
        add(KeyboardStyle.Auto)
        if (materialYouAvailable()) add(KeyboardStyle.MaterialYou)
        add(KeyboardStyle.GboardDark)
        add(KeyboardStyle.GboardLight)
    }
    val shownFolders = visibleSources(state.imageSources, state.enabledSourceIds)
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
        SettingsHeading("Look")
        ChipLine {
            styles.forEach { style ->
                FilterToggleChip(
                    label = styleName(style),
                    selected = style == state.savedStyle,
                    onClick = { actions.onSetStyle(style) },
                )
            }
        }
        SettingsHeading(if (state.landscape) "Height in landscape" else "Height")
        ChipLine {
            PanelHeight.entries.forEach { height ->
                FilterToggleChip(
                    label = height.label,
                    selected = height == state.heightPreset,
                    onClick = { actions.onSetHeight(height) },
                )
            }
        }
        SettingsHeading("Show in the keyboard")
        ChipLine {
            FilterToggleChip(
                label = "Snippets",
                selected = state.snippetsEnabled,
                onClick = { if (state.imagesEnabled) actions.onSetModeEnabled(KeyboardMode.Snippets, !state.snippetsEnabled) },
            )
            FilterToggleChip(
                label = "Images",
                selected = state.imagesEnabled,
                onClick = { if (state.snippetsEnabled) actions.onSetModeEnabled(KeyboardMode.Images, !state.imagesEnabled) },
            )
        }
        SettingsHeading("After inserting")
        SettingsSwitchRow(
            headline = "Return after a snippet",
            checked = state.returnAfterSnippet,
            onChange = { actions.onSetReturn(image = false, enabled = it) },
        )
        SettingsSwitchRow(
            headline = "Return after an image",
            checked = state.returnAfterImage,
            onChange = { actions.onSetReturn(image = true, enabled = it) },
        )
        SourceRow(
            icon = Icons.Rounded.PhotoLibrary,
            headline = "Image folders",
            supporting = shownFolders.joinToString { it.name }.ifEmpty { "None shown" },
            enabled = true,
            onClick = actions::onOpenImageFolders,
            trailing = { ChevronIcon() },
        )
        SourceRow(
            icon = Icons.Rounded.Settings,
            headline = "All settings",
            supporting = null,
            enabled = true,
            onClick = actions::onOpenAllSettings,
            trailing = {
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = LocalKeyboardPalette.current.icon,
                )
            },
        )
    }
}

@Composable
private fun ImageFoldersContent(state: KeyboardUiState, actions: KeyboardActions) {
    val palette = LocalKeyboardPalette.current
    if (!state.hasImagePermission) {
        EmptyState(
            message = "Allow photo access in Pastille to choose folders",
            button = "Open Pastille",
            onClick = actions::onOpenApp,
        )
        return
    }
    val shown = visibleSources(state.imageSources, state.enabledSourceIds).map { it.bucketId }.toSet()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(state.imageSources.sortedBy { it.name.lowercase() }, key = { it.bucketId }) { source ->
            val checked = source.bucketId in shown
            ListItem(
                headlineContent = { Text(source.name, color = palette.label) },
                supportingContent = {
                    Text(
                        if (source.count == 1) "1 image" else "${source.count} images",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.icon,
                    )
                },
                trailingContent = {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = palette.accent,
                            checkmarkColor = palette.onAccent,
                            uncheckedColor = palette.icon,
                        ),
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .animateItem()
                    .toggleable(
                        value = checked,
                        role = Role.Checkbox,
                        onValueChange = { on -> actions.onSetShownSources(if (on) shown + source.bucketId else shown - source.bucketId) },
                    ),
            )
        }
    }
}

@Composable
private fun SettingsHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = LocalKeyboardPalette.current.labelSecondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ChipLine(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun SettingsSwitchRow(headline: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val palette = LocalKeyboardPalette.current
    ListItem(
        headlineContent = { Text(headline, color = palette.label) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = palette.accent,
                    checkedThumbColor = palette.onAccent,
                    uncheckedTrackColor = palette.key,
                    uncheckedThumbColor = palette.icon,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun ChevronIcon() {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = LocalKeyboardPalette.current.icon,
    )
}

@Composable
private fun ReorderContent(state: KeyboardUiState, actions: KeyboardActions) {
    val haptics = LocalHapticFeedback.current
    val folderId = state.folderId?.takeIf { id -> state.categories.any { it.id == id } }
    val shown = if (folderId == null) state.snippets else state.snippets.filter { it.categoryId == folderId }
    var order by remember(shown) { mutableStateOf(shown) }
    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        val fromIndex = order.indexOfFirst { it.id == from.key }
        val toIndex = order.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }
    val folders = state.categories.sortedBy { it.position }
    Column(modifier = Modifier.fillMaxSize()) {
    if (folders.size > 1) {
        FolderChipRow(
            entries = folders.map { ChipEntry(it.id, it.name, Icons.Rounded.Folder) },
            selectedId = null,
            onSelect = {},
            onReorder = actions::onReorderFolders,
        )
    }
    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(tileColumns(maxWidth.value.toInt())),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(order, key = { it.id }) { snippet ->
                ReorderableItem(reorderState, key = snippet.id) { dragging ->
                    val scale by animateFloatAsState(if (dragging) 1.04f else 1f, label = "dragScale")
                    SnippetTile(
                        snippet = snippet,
                        highlighted = dragging,
                        onTap = {},
                        onLongPress = {},
                        actions = actions,
                        interactive = false,
                        modifier = Modifier
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .longPressDraggableHandle(
                                onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onDragStopped = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    actions.onReorderSnippets(shown, order.map { it.id })
                                },
                            ),
                    )
                }
            }
        }
    }
    }
}
