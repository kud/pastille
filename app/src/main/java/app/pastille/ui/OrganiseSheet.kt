package app.pastille.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * Swipe right on a row opens this: where the snippet lives. Tapping a folder moves it at once and
 * closes the sheet; there is no Save button.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OrganiseSheet(
    snippet: SnippetRecord,
    folders: List<CategoryRecord>,
    onMove: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text(
                text = rowTitle(snippet.title, snippet.text).ifBlank { if (snippet.isImage) "Image" else "Snippet" },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
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
        }
    }
}
