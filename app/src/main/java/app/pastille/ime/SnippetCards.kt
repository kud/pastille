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
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.automirrored.rounded.ShortText
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pastille.images.ImageThumbnail
import app.pastille.images.StickerThumbnail
import app.pastille.model.CategoryRecord
import app.pastille.model.SnippetRecord
import app.pastille.settings.KeyboardMode
import app.pastille.settings.KeyboardStyle
import app.pastille.settings.PanelHeight
import app.pastille.settings.TOOLBAR_HEIGHT_DP
import app.pastille.settings.tileColumns
import app.pastille.settings.stickerColumns
import app.pastille.share.isMeaningfulImageName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
internal fun modeIcon(mode: KeyboardMode): ImageVector = when (mode) {
    KeyboardMode.Snippets -> Icons.AutoMirrored.Rounded.ShortText
    KeyboardMode.Stickers -> Icons.Rounded.EmojiEmotions
    KeyboardMode.Images -> Icons.Rounded.Image
}

// Icon pills in bar order; only the selected one shows its label, and under 400dp none do.
@Composable
internal fun ModeSwitch(
    mode: KeyboardMode,
    enabled: Set<KeyboardMode>,
    onChange: (KeyboardMode) -> Unit,
    compact: Boolean = false,
) {
    Row(
        modifier = Modifier.padding(start = 8.dp).selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyboardMode.entries.filter { it in enabled }.forEach { tab ->
            ModeTab(
                icon = modeIcon(tab),
                label = tab.label,
                selected = mode == tab,
                showLabel = mode == tab && !compact,
            ) { onChange(tab) }
        }
    }
}

@Composable
private fun ModeTab(icon: ImageVector, label: String, selected: Boolean, showLabel: Boolean, onClick: () -> Unit) {
    val palette = LocalKeyboardPalette.current
    val reduceMotion = LocalReduceMotion.current
    val background by animateColorAsState(
        targetValue = if (selected) palette.stripButton else Color.Transparent,
        animationSpec = tween(PastilleMotion.EXIT_MS),
        label = "modePill",
    )
    val tint = if (selected) palette.onStripButton else palette.icon
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label }
            .animateContentSize(
                if (reduceMotion) snap() else tween(PastilleMotion.SHORT_MS, easing = PastilleMotion.Standard),
            )
            .padding(horizontal = if (showLabel) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        if (showLabel) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

// Snippet folders and image folders share one chip row, so both modes read the same way.
@Composable
internal fun FolderChipRow(
    entries: List<ChipEntry>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onReorder: ((List<Long>) -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val rowState = rememberLazyListState()
    var order by remember(entries) { mutableStateOf(entries) }
    val reorderState = rememberReorderableLazyListState(rowState) { from, to ->
        val fromIndex = order.indexOfFirst { it.id == from.key }
        val toIndex = order.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }
    LaunchedEffect(selectedId) {
        val index = entries.indexOfFirst { it.id == selectedId }
        if (index >= 0) rowState.animateScrollToItem(index)
    }
    LazyRow(
        state = rowState,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(order, key = { it.id ?: Long.MIN_VALUE }) { entry ->
            val chip = @Composable { modifier: Modifier ->
                Box(modifier = modifier) {
                    if (onReorder != null) {
                        DragChip(label = entry.label, icon = entry.icon, movable = entry.id != null)
                    } else {
                        FilterToggleChip(
                            label = entry.label,
                            icon = entry.icon,
                            selected = entry.id == selectedId,
                            onClick = { onSelect(entry.id) },
                        )
                    }
                }
            }
            if (onReorder != null && entry.id != null) {
                ReorderableItem(reorderState, key = entry.id) { dragging ->
                    val scale by animateFloatAsState(if (dragging) 1.08f else 1f, label = "chipDrag")
                    chip(
                        Modifier
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .draggableHandle(
                                onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onDragStopped = { onReorder(order.mapNotNull { it.id }) },
                            ),
                    )
                }
            } else {
                chip(Modifier)
            }
        }
    }
}

internal val TileShape = RoundedCornerShape(10.dp)
internal val TileHeight = 88.dp

// Gboard clipboard tile: one fixed size for text, folders and images; flat, a colour change on press.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalKeyboardPalette.current
    val reduceMotion = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val color by animateColorAsState(
        targetValue = if (pressed) palette.keyPressed else palette.key,
        animationSpec = tween(60),
        label = "tileColour",
    )
    val flash by animateFloatAsState(
        targetValue = if (highlighted) 0.25f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(600),
        label = "tileFlash",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TileHeight)
            .clip(TileShape)
            .background(color)
            .background(palette.accent.copy(alpha = flash))
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onLongClick = onLongClick,
                        onClick = onClick,
                    )
                },
            ),
        content = content,
    )
}

@Composable
internal fun SnippetTile(
    snippet: SnippetRecord,
    highlighted: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    actions: KeyboardActions,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    tapVerb: String = "Insert",
) {
    val palette = LocalKeyboardPalette.current
    val title = displayTitle(snippet.title, snippet.text).ifBlank { if (snippet.isImage) "Image" else "" }
    val imageFile = snippet.imageFile
    Tile(
        onClick = if (interactive) onTap else null,
        onLongClick = if (interactive) onLongPress else null,
        highlighted = highlighted,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$tapVerb $title"
            customActions = listOf(
                CustomAccessibilityAction("Edit") {
                    actions.onEditSnippet(snippet)
                    true
                },
                CustomAccessibilityAction("Delete") {
                    actions.onDeleteSnippet(snippet)
                    true
                },
            )
        },
    ) {
        if (snippet.isImage && imageFile != null) {
            ImageThumbnail(
                fileName = imageFile,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                targetPx = 512,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp),
                )
            }
        } else {
            val preview = snippet.text.trim()
            val previewShown = preview.isNotEmpty() && preview != title
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.label,
                    maxLines = if (previewShown) 1 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (previewShown) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = preview,
                        fontSize = 14.sp,
                        color = palette.label.copy(alpha = 0.72f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal sealed interface ThumbState {
    data object Loading : ThumbState
    data object Failed : ThumbState
    data class Loaded(val bitmap: Bitmap) : ThumbState
}

@Composable
internal fun rememberThumb(image: ImageItem, sizePx: Int): ThumbState {
    val resolver = LocalContext.current.contentResolver
    var state by remember(image.uri, sizePx) { mutableStateOf<ThumbState>(ThumbState.Loading) }
    LaunchedEffect(image.uri, sizePx) {
        val bitmap = withContext(Dispatchers.IO) {
            ImageSourceReader.loadThumbnail(resolver, image.uri, sizePx)
        }
        state = if (bitmap != null) ThumbState.Loaded(bitmap) else ThumbState.Failed
    }
    return state
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ImageTile(image: ImageItem, onTap: () -> Unit, onLongPress: () -> Unit, tapVerb: String = "Insert") {
    val state = rememberThumb(image, 512)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TileHeight)
            .clip(TileShape)
            .combinedClickable(
                onClickLabel = "$tapVerb image",
                onLongClickLabel = "Preview",
                onLongClick = onLongPress,
                onClick = onTap,
            ),
    ) {
        Crossfade(targetState = state, animationSpec = tween(150), label = "thumb") { current ->
            when (current) {
                is ThumbState.Loading -> ThumbPlaceholder(shimmer = true)
                is ThumbState.Loaded -> Image(
                    bitmap = current.bitmap.asImageBitmap(),
                    contentDescription = image.displayName,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
                is ThumbState.Failed -> ThumbPlaceholder(shimmer = false) {
                    Icon(
                        Icons.Rounded.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = LocalKeyboardPalette.current.icon,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ThumbPlaceholder(shimmer: Boolean, content: @Composable () -> Unit = {}) {
    val alpha = if (shimmer && !LocalReduceMotion.current) {
        val pulse by rememberInfiniteTransition(label = "shimmer").animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
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
            .background(LocalKeyboardPalette.current.key),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
internal fun FilterToggleChip(label: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    val palette = LocalKeyboardPalette.current
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (icon != null) {
            {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = palette.key,
            labelColor = palette.label,
            selectedContainerColor = palette.stripButton,
            selectedLabelColor = palette.onStripButton,
            selectedLeadingIconColor = palette.onStripButton,
        ),
        border = null,
    )
}

internal data class ChipEntry(val id: Long?, val label: String, val icon: ImageVector)

internal fun sourceIcon(source: ImageSource): ImageVector = when {
    source.isScreenshots -> Icons.Rounded.Screenshot
    source.name.equals("Camera", ignoreCase = true) -> Icons.Rounded.PhotoCamera
    source.name.startsWith("Download", ignoreCase = true) -> Icons.Rounded.Download
    else -> Icons.Rounded.PhotoLibrary
}

@Composable
internal fun DragChip(label: String, icon: ImageVector, movable: Boolean) {
    val palette = LocalKeyboardPalette.current
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(palette.key)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (movable) Icons.Rounded.DragIndicator else icon, contentDescription = null, tint = palette.icon, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = palette.label, maxLines = 1)
    }
}

// The snippet grid shared by the keyboard panel and the Quick Settings picker sheet.
@Composable
internal fun SnippetGrid(
    items: List<SnippetRecord>,
    actions: KeyboardActions,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    highlightedSnippetId: Long? = null,
    tapVerb: String = "Insert",
) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(tileColumns(maxWidth.value.toInt())),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.id }) { snippet ->
                SnippetTile(
                    snippet = snippet,
                    highlighted = snippet.id == highlightedSnippetId,
                    onTap = { actions.onSnippetTap(snippet) },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions.onSnippetLongPress(snippet)
                    },
                    actions = actions,
                    modifier = Modifier.animateItem(),
                    tapVerb = tapVerb,
                )
            }
        }
    }
}

// The recent-images grid shared by the keyboard panel and the Quick Settings picker sheet.
@Composable
internal fun ImageGrid(
    images: List<ImageItem>,
    actions: KeyboardActions,
    modifier: Modifier = Modifier,
    tapVerb: String = "Insert",
) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(modifier = modifier) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(tileColumns(maxWidth.value.toInt())),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (images.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "No images here yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalKeyboardPalette.current.icon,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }
            }
            items(images, key = { it.uri.toString() }) { image ->
                ImageTile(
                    image = image,
                    onTap = { actions.onImageTap(image) },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions.onImageLongPress(image)
                    },
                    tapVerb = tapVerb,
                )
            }
        }
    }
}

// One flat grid of stickers in manual order, shared by the keyboard and the picker sheet. Columns
// come from the width, so a sticker keeps its size and a taller panel shows more rows.
@Composable
internal fun StickerGrid(
    stickers: List<SnippetRecord>,
    actions: KeyboardActions,
    modifier: Modifier = Modifier,
    tapVerb: String = "Insert",
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthDp = maxWidth.value.toInt()
        val columns = stickerColumns(widthDp)
        val cellDp = (widthDp - 12 - 4 * (columns - 1)).toFloat() / columns
        val targetPx = with(density) { cellDp.dp.roundToPx() }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(stickers, key = { it.id }) { sticker ->
                StickerCell(
                    sticker = sticker,
                    targetPx = targetPx,
                    tapVerb = tapVerb,
                    onTap = { actions.onSnippetTap(sticker) },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions.onSnippetLongPress(sticker)
                    },
                    actions = actions,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

// No chrome: the sticker alone, at a 6dp inset. A pressed square fades in under the finger, and nothing else moves.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerCell(
    sticker: SnippetRecord,
    targetPx: Int,
    tapVerb: String,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    actions: KeyboardActions,
    modifier: Modifier = Modifier,
) {
    val palette = LocalKeyboardPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(PastilleMotion.PRESS_MS),
        label = "stickerPress",
    )
    val title = sticker.title.ifBlank { "Sticker" }
    val imageFile = sticker.imageFile ?: return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(TileShape)
            .background(palette.keyPressed.copy(alpha = palette.keyPressed.alpha * pressAlpha))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = onLongPress,
                onClick = onTap,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "$tapVerb sticker, $title"
                customActions = listOf(
                    CustomAccessibilityAction("Edit") {
                        actions.onEditSnippet(sticker)
                        true
                    },
                    CustomAccessibilityAction("Delete") {
                        actions.onDeleteSnippet(sticker)
                        true
                    },
                )
            }
            .padding(6.dp),
    ) {
        StickerThumbnail(fileName = imageFile, targetPx = targetPx, modifier = Modifier.fillMaxSize())
    }
}
