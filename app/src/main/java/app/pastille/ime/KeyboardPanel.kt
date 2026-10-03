package app.pastille.ime

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardBackspace
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pastille.model.SnippetRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun KeyboardPanel(
    snippets: List<SnippetRecord>,
    screenshots: List<ScreenshotItem>,
    hasScreenshotPermission: Boolean,
    onSaveClipboard: () -> Unit,
    onOpenApp: () -> Unit,
    onOpenPermissions: () -> Unit,
    onBack: () -> Unit,
    onSnippetTap: (SnippetRecord) -> Unit,
    onSnippetLongPress: (SnippetRecord) -> Unit,
    onScreenshotTap: (ScreenshotItem) -> Unit,
) {
    var showPinnedOnly by remember { mutableStateOf(false) }
    val visible = if (showPinnedOnly) snippets.filter { it.pinned } else snippets

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = { showPinnedOnly = false },
                label = { Text("All") },
                enabled = showPinnedOnly,
            )
            Spacer(Modifier.width(8.dp))
            AssistChip(
                onClick = { showPinnedOnly = true },
                label = { Text("Pinned") },
                enabled = !showPinnedOnly,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSaveClipboard) {
                Icon(Icons.Filled.ContentPaste, contentDescription = "Save clipboard")
            }
            IconButton(onClick = onOpenApp) {
                Icon(Icons.Filled.Settings, contentDescription = "Open Pastille")
            }
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardBackspace, contentDescription = "Previous keyboard")
            }
        }

        Spacer(Modifier.height(4.dp))

        if (hasScreenshotPermission) {
            if (screenshots.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(screenshots, key = { it.uri.toString() }) { item ->
                        ScreenshotThumb(item = item, onTap = { onScreenshotTap(item) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        } else {
            TextButton(onClick = onOpenPermissions) {
                Text("Allow photo access for screenshots")
            }
        }

        if (visible.isEmpty()) {
            Text(
                text = "Add your first snippet in the Pastille app",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.heightIn(max = 240.dp),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SnippetCard(
    snippet: SnippetRecord,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    Card(
        modifier = Modifier.combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        Column(Modifier.padding(10.dp)) {
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

@Composable
private fun ScreenshotThumb(item: ScreenshotItem, onTap: () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    var bitmap by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.uri) {
        bitmap = withContext(Dispatchers.IO) {
            ScreenshotReader.loadThumbnail(resolver, item.uri)
        }
    }
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp),
            )
        } else {
            Icon(Icons.Filled.Image, contentDescription = item.displayName)
        }
    }
}
