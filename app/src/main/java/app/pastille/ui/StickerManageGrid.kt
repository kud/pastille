package app.pastille.ui

import app.pastille.share.shareSnippet
import androidx.compose.material.icons.rounded.Share
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pastille.images.StickerThumbnail
import app.pastille.model.SnippetRecord
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

// About 88dp cells between 16dp margins: four columns on a 411dp phone.
fun appStickerColumns(widthDp: Int): Int = ((widthDp - 32) / 88).coerceIn(3, 6)

private val CellShape = RoundedCornerShape(12.dp)

/**
 * The app's Stickers chip: a grid of faint tiles, since a tap here opens something to manage. Tap opens
 * the Organise sheet, a long-press offers Move to snippets and Delete, and the overflow's Reorder drags
 * cells. No swipes.
 */
@Composable
internal fun StickerManageGrid(
    stickers: List<SnippetRecord>,
    reordering: Boolean,
    onOpen: (SnippetRecord) -> Unit,
    onMoveToSnippets: (SnippetRecord) -> Unit,
    onDelete: (SnippetRecord) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onAdd: () -> Unit,
) {
    if (stickers.isEmpty()) {
        StickersEmpty(onAdd = onAdd)
        return
    }
    val haptics = app.pastille.ui.rememberHaptics()
    val context = LocalContext.current
    val density = LocalDensity.current
    val gridState = rememberLazyGridState()
    var order by remember(stickers, reordering) { mutableStateOf(stickers) }
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        val fromIndex = order.indexOfFirst { it.id == from.key }
        val toIndex = order.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthDp = maxWidth.value.toInt()
        val columns = appStickerColumns(widthDp)
        val targetPx = with(density) { ((widthDp - 32 - 8 * (columns - 1)) / columns).dp.roundToPx() }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(if (reordering) order else stickers, key = { it.id }) { sticker ->
                if (reordering) {
                    ReorderableItem(reorderState, key = sticker.id) { dragging ->
                        val scale by animateFloatAsState(if (dragging) 1.06f else 1f, label = "stickerDrag")
                        StickerTile(
                            sticker = sticker,
                            targetPx = targetPx,
                            modifier = Modifier
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .longPressDraggableHandle(
                                    onDragStarted = { haptics.pickUp() },
                                    onDragStopped = {
                                        haptics.drop()
                                        onReorder(order.map { it.id })
                                    },
                                ),
                        )
                    }
                } else {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.animateItem()) {
                        StickerTile(
                            sticker = sticker,
                            targetPx = targetPx,
                            onClick = { onOpen(sticker) },
                            onLongClick = {
                                haptics.pickUp()
                                menuOpen = true
                            },
                        )
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Move to snippets") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ShortText, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onMoveToSnippets(sticker)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    shareSnippet(context, sticker)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onDelete(sticker)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerTile(
    sticker: SnippetRecord,
    targetPx: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val imageFile = sticker.imageFile ?: return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = CellShape,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CellShape)
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(onClickLabel = "Organise", onLongClick = onLongClick, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) { contentDescription = sticker.title.ifBlank { "Sticker" } },
    ) {
        StickerThumbnail(fileName = imageFile, targetPx = targetPx, modifier = Modifier.fillMaxSize().padding(10.dp))
    }
}

@Composable
private fun StickersEmpty(@Suppress("UNUSED_PARAMETER") onAdd: () -> Unit) {
    // The shared empty state, like the list, folders, search and the bin (Iris, 2026-10-28).
    EmptyState(
        icon = Icons.Rounded.EmojiEmotions,
        title = "No stickers yet",
        // No button: "Add stickers" is already the extended FAB below, and one action is the rule.
        body = "Add them below, or share images to Pastille and choose Sticker.",
    )
}

/** In search results, a sticker carries this in place of the folder pill. */
@Composable
internal fun StickerPill() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.height(18.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.EmojiEmotions, contentDescription = null, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(text = "Sticker", style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}
