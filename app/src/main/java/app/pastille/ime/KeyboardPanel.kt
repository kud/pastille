package app.pastille.ime

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.ContentPasteGo
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pastille.model.SnippetRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun KeyboardPanel(
    snippets: List<SnippetRecord>,
    screenshots: List<ScreenshotItem>,
    hasScreenshotPermission: Boolean,
    clipboardHasText: Boolean,
    statusMessage: String?,
    onStatusShown: () -> Unit,
    onNewSnippet: () -> Unit,
    onSaveClipboard: () -> Unit,
    onOpenApp: () -> Unit,
    onOpenPermissions: () -> Unit,
    onSwitchKeyboard: () -> Unit,
    onSnippetTap: (SnippetRecord) -> Unit,
    onSnippetLongPress: (SnippetRecord) -> Unit,
    onScreenshotTap: (ScreenshotItem) -> Unit,
) {
    var showPinnedOnly by remember { mutableStateOf(false) }
    val visible = if (showPinnedOnly) snippets.filter { it.pinned } else snippets

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(1500)
            onStatusShown()
        }
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
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterToggleChip(
                    label = "All",
                    selected = !showPinnedOnly,
                    onClick = { showPinnedOnly = false },
                )
                Spacer(Modifier.width(8.dp))
                FilterToggleChip(
                    label = "Pinned",
                    selected = showPinnedOnly,
                    onClick = { showPinnedOnly = true },
                )
                Spacer(Modifier.weight(1f))
                AssistChip(
                    onClick = onNewSnippet,
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
                ToolbarAction(
                    icon = Icons.Outlined.ContentPasteGo,
                    label = "Save clipboard as snippet",
                    onClick = onSaveClipboard,
                )
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

            if (statusMessage != null) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onSaveClipboard, enabled = clipboardHasText) {
                            Text("Save clipboard")
                        }
                        OutlinedButton(onClick = onNewSnippet) {
                            Text("New snippet")
                        }
                    }
                }
            } else if (visible.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "No pinned snippets. Long-press a snippet to pin it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.heightIn(max = 240.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.id }) { snippet ->
                        SnippetCard(
                            snippet = snippet,
                            onTap = { onSnippetTap(snippet) },
                            onLongPress = { onSnippetLongPress(snippet) },
                        )
                    }
                }
            }
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
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        modifier = Modifier.heightIn(min = 56.dp).combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        Column(Modifier.padding(12.dp)) {
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
                    text = snippet.title.ifBlank { snippet.text },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (snippet.title.isNotBlank()) {
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
