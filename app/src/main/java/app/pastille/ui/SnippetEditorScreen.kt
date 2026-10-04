package app.pastille.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextButtonDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pastille.data.SnippetRepository
import app.pastille.images.ImageThumbnail
import app.pastille.model.SnippetRecord
import app.pastille.share.autoTitle
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
fun SnippetEditorScreen(
    snippetId: Long?,
    repository: SnippetRepository,
    onDone: () -> Unit,
    onDeleted: (SnippetRecord) -> Unit,
    initialCategoryId: Long? = null,
) {
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as? Activity
    var ready by remember { mutableStateOf(snippetId == null) }
    var existing by remember { mutableStateOf<SnippetRecord?>(null) }
    var title by rememberSaveable(snippetId) { mutableStateOf("") }
    var text by rememberSaveable(snippetId) { mutableStateOf("") }
    var categoryId by rememberSaveable(snippetId) { mutableStateOf(initialCategoryId) }
    // Survives recreation so a reload never overwrites what was typed before a rotation.
    var loaded by rememberSaveable(snippetId) { mutableStateOf(false) }
    var savedId by rememberSaveable(snippetId) { mutableStateOf<Long?>(null) }
    var deleted by remember { mutableStateOf(false) }
    var folderExpanded by remember { mutableStateOf(false) }
    var showCreateCategory by remember { mutableStateOf(false) }
    val contentFocus = remember { FocusRequester() }
    val saveMutex = remember { Mutex() }
    val categories by remember { repository.observeCategories() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(snippetId) {
        if (snippetId != null) {
            val record = repository.get(snippetId)
            if (record != null) {
                existing = record
                if (!loaded) {
                    title = record.title
                    text = record.text
                    categoryId = record.categoryId
                }
            }
            loaded = true
            ready = true
        }
    }
    LaunchedEffect(Unit) {
        if (snippetId == null) contentFocus.requestFocus()
    }
    val isImage = existing?.isImage == true
    val selectedCategoryName = categories.firstOrNull { it.id == categoryId }?.name ?: "No folder"
    val imageRatio = remember(existing?.imageWidth, existing?.imageHeight) {
        val width = existing?.imageWidth
        val height = existing?.imageHeight
        if (width != null && height != null && width > 0 && height > 0) {
            width.toFloat() / height
        } else {
            1f
        }
    }

    suspend fun save() {
        saveMutex.withLock {
            if (deleted) return
            if (snippetId == null) {
                if (text.isBlank()) return
                val targetId = savedId
                if (targetId != null) {
                    val current = repository.get(targetId) ?: return
                    if (current.title == title && current.text == text && current.categoryId == categoryId) return
                    repository.upsert(current.copy(title = title, text = text, categoryId = categoryId))
                    existing = repository.get(targetId)
                } else {
                    val id = repository.upsert(
                        SnippetRecord(text = text, title = title, categoryId = categoryId),
                    )
                    savedId = id
                    existing = repository.get(id)
                }
            } else {
                if (!ready) return
                if (!isImage && text.isBlank()) return
                val current = repository.get(snippetId) ?: return
                if (current.title == title && current.text == text && current.categoryId == categoryId) {
                    existing = current
                    return
                }
                repository.upsert(current.copy(title = title, text = text, categoryId = categoryId))
                existing = repository.get(snippetId)
            }
        }
    }

    fun close() {
        scope.launch {
            save()
            onDone()
        }
    }

    fun deleteSnippet() {
        val id = snippetId ?: return
        scope.launch {
            val record = saveMutex.withLock {
                deleted = true
                repository.get(id).also { repository.delete(id) }
            }
            if (record != null) onDeleted(record) else onDone()
        }
    }

    BackHandler {
        close()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) {
            scope.launch(NonCancellable) { save() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(
                onClick = ::close,
                modifier = Modifier.size(48.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }
            }
        }
        TextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = {
                Text(
                    text = if (text.isBlank()) "Title (optional)" else autoTitle(text),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = MaterialTheme.colorScheme.outline,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (isImage) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                val imageFile = existing?.imageFile
                if (imageFile != null) {
                    ImageThumbnail(
                        fileName = imageFile,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).aspectRatio(imageRatio),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        } else {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(contentFocus),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (text.isEmpty()) {
                            Text(
                                text = "Content",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    TextButton(
                        onClick = { folderExpanded = true },
                        colors = TextButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(selectedCategoryName, style = MaterialTheme.typography.labelLarge)
                    }
                    DropdownMenu(
                        expanded = folderExpanded,
                        onDismissRequest = { folderExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("None (top level)") },
                            onClick = {
                                categoryId = null
                                folderExpanded = false
                            },
                        )
                        categories.sortedBy { it.position }.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    categoryId = category.id
                                    folderExpanded = false
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("New folder…") },
                            onClick = {
                                folderExpanded = false
                                showCreateCategory = true
                            },
                        )
                    }
                }
                if (snippetId != null) {
                    IconButton(onClick = ::deleteSnippet) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete snippet",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (!isImage) {
                    Text(
                        text = "Characters: ${text.length}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
    }
    if (showCreateCategory) {
        CategoryNameDialog(
            initialName = "",
            title = "New folder",
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
