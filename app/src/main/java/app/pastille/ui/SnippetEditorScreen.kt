package app.pastille.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import app.pastille.data.SnippetRepository
import app.pastille.model.SnippetRecord
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetEditorScreen(
    snippetId: Long?,
    repository: SnippetRepository,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(snippetId == null) }
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(false) }

    LaunchedEffect(snippetId) {
        if (snippetId != null) {
            val record = repository.get(snippetId)
            if (record != null) {
                title = record.title
                text = record.text
                pinned = record.pinned
            }
            ready = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(if (snippetId == null) "New snippet" else "Edit snippet") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
                            Icon(Icons.Filled.Delete, contentDescription = "Delete snippet")
                        }
                    }
                    IconButton(
                        onClick = {
                            if (text.isBlank()) return@IconButton
                            scope.launch {
                                val existing = if (snippetId != null) repository.get(snippetId) else null
                                repository.upsert(
                                    (existing ?: SnippetRecord(text = text)).copy(
                                        title = title,
                                        text = text,
                                        pinned = pinned,
                                    ),
                                )
                                onDone()
                            }
                        },
                        enabled = ready && text.isNotBlank(),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Save snippet")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title (optional)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("Text") },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = pinned, onCheckedChange = { pinned = it })
                Text("Pinned")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
