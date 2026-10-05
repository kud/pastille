package app.pastille.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import app.pastille.images.AnimatedSticker
import app.pastille.ime.PastilleMotion
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pastille.model.CategoryRecord
import app.pastille.model.SnippetRecord

const val TOP_LEVEL_LABEL = "None (top level)"

/** The snackbar after a move from the Organise sheet: "Moved to Personal". */
fun movedMessage(folderName: String?): String = "Moved to ${folderName ?: "top level"}"

/**
 * Swipe right on a row opens this: where the snippet lives and what it is tagged. Tapping a folder
 * moves it at once and the tags apply as they change; there is no Save button. The sheet stays open
 * and closes on a swipe down or a tap outside.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OrganiseSheet(
    snippet: SnippetRecord,
    folders: List<CategoryRecord>,
    allTags: List<String>,
    onMove: (Long?) -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    onSetSticker: (Boolean) -> Unit = {},
    onRename: (String) -> Unit = {},
    onDelete: () -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().imePadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text(
                text = rowTitle(snippet.title, snippet.text).ifBlank { if (snippet.isImage) "Image" else "Snippet" },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            if (snippet.sticker) {
                StickerOrganise(snippet = snippet, onSetSticker = onSetSticker, onRename = onRename, onDelete = onDelete)
                return@Column
            }
            Text(
                text = "Folder",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = snippet.categoryId == null,
                    onClick = { onMove(null) },
                    label = { Text(TOP_LEVEL_LABEL) },
                )
                folders.forEach { folder ->
                    FilterChip(
                        selected = snippet.categoryId == folder.id,
                        onClick = { onMove(folder.id) },
                        label = { Text(folder.name) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Tags",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            TagEditor(tags = snippet.tags, allTags = allTags, onTagsChange = onTagsChange)
            // Only images can be stickers.
            if (snippet.isImage) {
                Spacer(Modifier.height(8.dp))
                StickerSwitch(checked = false, onChange = onSetSticker)
            }
        }
    }
}

// A sticker has no folder or tags: a preview, its description, the switch back, and Delete.
@Composable
private fun StickerOrganise(
    snippet: SnippetRecord,
    onSetSticker: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val imageFile = snippet.imageFile ?: return
    val density = LocalDensity.current
    val reduceMotion = remember { PastilleMotion.reduceMotion() }
    var description by remember(snippet.id) { mutableStateOf(snippet.title) }
    val latest by rememberUpdatedState(description)
    DisposableEffect(snippet.id) {
        onDispose { if (latest.trim() != snippet.title && latest.isNotBlank()) onRename(latest) }
    }
    AnimatedSticker(
        fileName = imageFile,
        contentDescription = snippet.title.ifBlank { "Sticker" },
        animate = !reduceMotion,
        targetPx = with(density) { 320.dp.roundToPx() },
        modifier = Modifier.fillMaxWidth().height(160.dp),
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("Description") },
        supportingText = { Text("Read aloud by TalkBack, and used by search") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    StickerSwitch(checked = true, onChange = onSetSticker)
    Spacer(Modifier.height(8.dp))
    TextButton(
        onClick = onDelete,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text("Delete")
    }
}

@Composable
private fun StickerSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Sticker", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Show in the Stickers tab",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
