package app.pastille.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pastille.data.SnippetRepository
import app.pastille.images.ImageThumbnail
import app.pastille.model.SnippetRecord
import app.pastille.share.autoTitle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetEditorScreen(
    snippetId: Long?,
    repository: SnippetRepository,
    onDone: () -> Unit,
    initialCategoryId: Long? = null,
) {
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(snippetId == null) }
    var existing by remember { mutableStateOf<SnippetRecord?>(null) }
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf(initialCategoryId) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var showCreateCategory by remember { mutableStateOf(false) }
    val contentFocus = remember { FocusRequester() }
    val categories by remember { repository.observeCategories() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(snippetId) {
        if (snippetId != null) {
            val record = repository.get(snippetId)
            if (record != null) {
                existing = record
                title = record.title
                text = record.text
                categoryId = record.categoryId
            }
            ready = true
        }
    }
    LaunchedEffect(Unit) {
        if (snippetId == null) contentFocus.requestFocus()
    }
    val isImage = existing?.isImage == true
    val selectedCategoryName = categories.firstOrNull { it.id == categoryId }?.name ?: "None (top level)"
    val imageRatio = remember(existing?.imageWidth, existing?.imageHeight) {
        val width = existing?.imageWidth
        val height = existing?.imageHeight
        if (width != null && height != null && width > 0 && height > 0) {
            width.toFloat() / height
        } else {
            1f
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(if (snippetId == null) "New snippet" else "Edit snippet") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (snippetId != null) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    repository.delete(snippetId)
                                    onDone()
                                }
                            },
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete snippet")
                        }
                    }
                    IconButton(
                        onClick = {
                            if (text.isBlank() && !isImage) return@IconButton
                            scope.launch {
                                val existing = if (snippetId != null) repository.get(snippetId) else null
                                repository.upsert(
                                    (existing ?: SnippetRecord(text = text)).copy(
                                        title = title,
                                        text = text,
                                        categoryId = categoryId,
                                    ),
                                )
                                onDone()
                            }
                        },
                        enabled = ready && (text.isNotBlank() || isImage),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = "Save snippet")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
                placeholder = {
                    Text(autoTitle(text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                supportingText = { Text("Shown on the keyboard button") },
                singleLine = true,
            )
            if (isImage) {
                val imageFile = existing?.imageFile
                if (imageFile != null) {
                    ImageThumbnail(
                        fileName = imageFile,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).aspectRatio(imageRatio),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .focusRequester(contentFocus),
                    label = { Text("Content") },
                    minLines = 6,
                )
            }
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedCategoryName,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    label = { Text("Folder") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    singleLine = true,
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("None (top level)") },
                        onClick = {
                            categoryId = null
                            categoryExpanded = false
                        },
                    )
                    categories.sortedBy { it.position }.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                categoryId = category.id
                                categoryExpanded = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("New folder…") },
                        onClick = {
                            categoryExpanded = false
                            showCreateCategory = true
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (showCreateCategory) {
        CategoryNameDialog(
            initialName = "",
            title = "New category",
            confirmLabel = "Create",
            onConfirm = { name ->
                val id = repository.createCategory(name)
                categoryId = id
                null
            },
            onDismiss = { showCreateCategory = false },
        )
    }
}
