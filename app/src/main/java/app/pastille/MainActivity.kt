package app.pastille

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.pastille.data.SnippetRepository
import app.pastille.model.SnippetRecord
import app.pastille.ui.SnippetEditorScreen
import app.pastille.ui.SnippetListScreen
import app.pastille.ui.theme.PastilleTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startEditId = intent
            ?.takeIf { it.hasExtra(EXTRA_EDIT_SNIPPET_ID) }
            ?.getLongExtra(EXTRA_EDIT_SNIPPET_ID, -1)
            ?.takeIf { it >= 0 }
        setContent {
            PastilleTheme {
                PastilleApp(startEditId = startEditId)
            }
        }
    }

    companion object {
        const val EXTRA_EDIT_SNIPPET_ID = "app.pastille.EXTRA_EDIT_SNIPPET_ID"
    }
}

@Composable
private fun PastilleApp(startEditId: Long?) {
    var editingId by remember { mutableStateOf(startEditId) }
    var creating by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val repository = remember { SnippetRepository.forContext(context) }

    if (editingId != null || creating) {
        SnippetEditorScreen(
            snippetId = editingId,
            repository = repository,
            onDone = {
                editingId = null
                creating = false
            },
        )
    } else {
        SnippetListScreen(
            repository = repository,
            onCreate = { creating = true },
            onEdit = { snippet: SnippetRecord -> editingId = snippet.id },
        )
    }
}
