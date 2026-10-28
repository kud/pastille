package app.pastille.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import android.widget.Toast
import app.pastille.share.shareSnippet
import kotlinx.coroutines.delay
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    var tags by rememberSaveable(snippetId) { mutableStateOf(listOf<String>()) }
    // Survives recreation so a reload never overwrites what was typed before a rotation.
    var loaded by rememberSaveable(snippetId) { mutableStateOf(false) }
    var savedId by rememberSaveable(snippetId) { mutableStateOf<Long?>(null) }
    var deleted by remember { mutableStateOf(false) }
    var folderExpanded by remember { mutableStateOf(false) }
    var showCreateCategory by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // What is on disk, as far as this screen knows: the footer says "Saved" only when it matches.
    var savedState by remember { mutableStateOf<List<Any?>?>(null) }
    val context = LocalContext.current
    val contentFocus = remember { FocusRequester() }
    val saveMutex = remember { Mutex() }
    val categories by remember { repository.observeCategories() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val allTags by remember { repository.observeTagNames() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(snippetId) {
        if (snippetId != null) {
            val record = repository.get(snippetId)
            if (record != null) {
                existing = record
                savedState = listOf(record.title, record.text, record.categoryId, record.tags)
                if (!loaded) {
                    title = record.title
                    text = record.text
                    categoryId = record.categoryId
                    tags = record.tags
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
                    if (current.title == title && current.text == text && current.categoryId == categoryId && current.tags == tags) return
                    repository.upsert(current.copy(title = title, text = text, categoryId = categoryId))
                    repository.setTags(targetId, tags)
                    existing = repository.get(targetId)
                    savedState = listOf(title, text, categoryId, tags)
                } else {
                    val id = repository.upsert(
                        SnippetRecord(text = text, title = title, categoryId = categoryId),
                    )
                    repository.setTags(id, tags)
                    savedId = id
                    existing = repository.get(id)
                    savedState = listOf(title, text, categoryId, tags)
                }
            } else {
                if (!ready) return
                if (!isImage && text.isBlank()) return
                val current = repository.get(snippetId) ?: return
                if (current.title == title && current.text == text && current.categoryId == categoryId && current.tags == tags) {
                    existing = current
                    savedState = listOf(title, text, categoryId, tags)
                    return
                }
                repository.upsert(current.copy(title = title, text = text, categoryId = categoryId))
                repository.setTags(snippetId, tags)
                existing = repository.get(snippetId)
                savedState = listOf(title, text, categoryId, tags)
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
    // Sacha's footer promises "Saved", so the editor saves as you pause, not only on leaving.
    LaunchedEffect(title, text, categoryId, tags, ready) {
        if (!ready || deleted) return@LaunchedEffect
        delay(700)
        save()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) {
            scope.launch(NonCancellable) { save() }
        }
    }

    val isSaved = savedState == listOf(title, text, categoryId, tags)
    val status = when {
        isSaved -> "Saved"
        snippetId == null && text.isBlank() -> "Not saved until it has text"
        else -> "Saving…"
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Sacha: back on the left (it saves), delete and ⋮ on the right.
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = ::close) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Save and go back")
            }
            Spacer(modifier = Modifier.weight(1f))
            if (snippetId != null) {
                IconButton(onClick = ::deleteSnippet) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete snippet")
                }
            }
            val record = existing
            if (record != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                val current = record.copy(title = title, text = text)
                                if (!shareSnippet(context, current)) {
                                    Toast.makeText(context, "That image is gone", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }
            }
        }
        TextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            placeholder = {
                Text(
                    text = if (text.isBlank()) "Title (optional)" else autoTitle(text),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        // Sacha: a folder dropdown chip under the title, the tags beside it.
        Box(modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
            Surface(
                onClick = { folderExpanded = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.heightIn(min = 48.dp).padding(vertical = 4.dp)
                    .semantics { stateDescription = "Folder: $selectedCategoryName" },
            ) {
                Row(
                    modifier = Modifier.height(40.dp).padding(start = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(selectedCategoryName, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
                }
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
        TagEditor(
            tags = tags,
            allTags = allTags,
            onTagsChange = { tags = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        // Sacha: the body sits on its own rounded surface.
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            if (isImage) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
                        .fillMaxSize()
                        .padding(16.dp)
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
        }
        // Sacha: "Saved · 52 characters". Polite live region, so TalkBack hears it settle.
        Text(
            text = if (isImage) status else "$status · ${text.length} ${if (text.length == 1) "character" else "characters"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 14.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
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
