package app.pastille.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Image
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pastille.images.ImageThumbnail
import app.pastille.images.ThumbnailPlaceholder
import app.pastille.model.CategoryRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    state: ShareUiState,
    categories: List<CategoryRecord>,
    onSelectCategory: (Long?) -> Unit,
    onSelectSaveAs: (Boolean) -> Unit,
    onUndo: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    onRequestFinish: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val removed = state.status == ShareStatus.REMOVED
    val failed = state.status == ShareStatus.FAILED
    if (removed) {
        LaunchedEffect(Unit) {
            delay(800)
            runCatching { sheetState.hide() }.getOrNull()
            onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = { if (state.saving) onRequestFinish() else onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            HeaderRow(state = state, onUndo = onUndo)
            if (failed) {
                Text(
                    text = state.failureReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                val faded = Modifier.graphicsLayer { alpha = if (removed) 0.38f else 1f }
                Box(modifier = faded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(12.dp),
                            )
                            .padding(12.dp),
                    ) {
                        when (state.kind) {
                            ShareKind.TEXT -> TextPreview(state = state)
                            ShareKind.LINK -> LinkPreview(state = state)
                            ShareKind.SINGLE_IMAGE -> SingleImagePreview(state = state)
                            ShareKind.MULTI_IMAGE -> MultiImagePreview(state = state)
                        }
                    }
                }
                if ((state.kind == ShareKind.TEXT || state.kind == ShareKind.LINK) && state.truncated) {
                    Text(
                        text = "Long text: saved the first 50,000 characters",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            val sharesImages = state.kind == ShareKind.SINGLE_IMAGE || state.kind == ShareKind.MULTI_IMAGE
            if (!failed && sharesImages) {
                Spacer(modifier = Modifier.height(16.dp))
                SaveAsRow(
                    sticker = state.saveAsSticker,
                    enabled = !state.saving && !removed,
                    onSelect = onSelectSaveAs,
                    modifier = Modifier.graphicsLayer { alpha = if (removed) 0.38f else 1f },
                )
            }
            // Stickers have no folder.
            if (!failed && !(sharesImages && state.saveAsSticker)) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.graphicsLayer { alpha = if (removed) 0.38f else 1f }) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (categories.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(24.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                            }
                            item {
                                ShareCategoryChip(
                                    label = "None",
                                    selected = state.selectedCategoryId == null,
                                    onClick = { onSelectCategory(null) },
                                )
                            }
                            items(categories, key = { it.id }) { category ->
                                ShareCategoryChip(
                                    label = category.name,
                                    selected = state.selectedCategoryId == category.id,
                                    onClick = { onSelectCategory(category.id) },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!failed && !removed) {
                    TextButton(onClick = onEdit) {
                        Text("Edit in Pastille")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                FilledTonalButton(
                    onClick = {
                        if (state.saving) {
                            onRequestFinish()
                        } else {
                            scope.launch {
                                runCatching { sheetState.hide() }.getOrNull()
                                onDismiss()
                            }
                        }
                    },
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(state: ShareUiState, onUndo: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val header = when {
        state.saving -> "Saving…"
        state.status == ShareStatus.FAILED -> "Couldn't save this"
        state.status == ShareStatus.REMOVED -> "Removed"
        state.status == ShareStatus.ALREADY -> "Already in Pastille"
        state.saveAsSticker && state.savedCount == 1 -> "Saved as a sticker"
        state.saveAsSticker -> "Saved ${state.savedCount} stickers"
        state.kind == ShareKind.MULTI_IMAGE -> {
            val saved = state.imageFiles.size
            if (state.totalImages > saved) {
                "Saved $saved of ${state.totalImages} images"
            } else {
                "Saved $saved images"
            }
        }
        else -> "Saved to Pastille"
    }
    val headerColor = when (state.status) {
        ShareStatus.FAILED -> colorScheme.error
        ShareStatus.REMOVED -> colorScheme.onSurfaceVariant
        else -> colorScheme.onSurface
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(colorScheme.primary, CircleShape),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = header,
            style = MaterialTheme.typography.titleMedium,
            color = headerColor,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (state.canUndo) {
            TextButton(onClick = onUndo, enabled = !state.saving) {
                Text("Undo")
            }
        }
    }
}

@Composable
private fun TextPreview(state: ShareUiState) {
    if (state.textTitle.isNotEmpty()) {
        Text(
            text = state.textTitle,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    if (state.textTitle.isNotEmpty() && state.textBody.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
    }
    if (state.textBody.isNotEmpty()) {
        Text(
            text = state.textBody,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LinkPreview(state: ShareUiState) {
    if (state.textTitle.isNotEmpty()) {
        Text(
            text = state.textTitle,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.Link,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = state.linkHost,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = state.linkUrl,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SingleImagePreview(state: ShareUiState) {
    val file = state.imageFiles.firstOrNull()
    val title = state.imageTitles.firstOrNull().orEmpty()
    if (state.saving && file == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            ThumbnailPlaceholder(shimmer = true)
        }
        return
    }
    if (file == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(96.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            ImageThumbnail(
                fileName = file,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        if (title.isNotEmpty()) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MultiImagePreview(state: ShareUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.saving && state.imageFiles.isEmpty()) {
            repeat(maxOf(state.totalImages.coerceAtMost(4), 1)) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    ThumbnailPlaceholder(shimmer = true)
                }
            }
        } else {
            state.imageFiles.take(4).forEachIndexed { index, file ->
                if (index == 3 && state.imageFiles.size > 4) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "+${state.imageFiles.size - 3}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    ) {
                        ImageThumbnail(
                            fileName = file,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}

// "Save as: Image / Sticker", single-select, remembered for the next share.
@Composable
private fun SaveAsRow(sticker: Boolean, enabled: Boolean, onSelect: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Save as",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilterChip(
            selected = !sticker,
            onClick = { onSelect(false) },
            enabled = enabled,
            label = { Text("Image") },
            leadingIcon = {
                Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
            },
            modifier = Modifier.semantics { role = Role.RadioButton },
        )
        FilterChip(
            selected = sticker,
            onClick = { onSelect(true) },
            enabled = enabled,
            label = { Text("Sticker") },
            leadingIcon = {
                Icon(Icons.Rounded.EmojiEmotions, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
            },
            modifier = Modifier.semantics { role = Role.RadioButton },
        )
    }
}

@Composable
private fun ShareCategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = null,
    )
}
