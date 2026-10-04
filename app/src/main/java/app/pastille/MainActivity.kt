package app.pastille

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.pastille.data.SnippetRepository
import app.pastille.model.SnippetRecord
import app.pastille.ui.SettingsScreen
import app.pastille.ui.SnippetEditorScreen
import app.pastille.ui.SnippetListScreen
import app.pastille.ui.theme.PastilleTheme

sealed interface LaunchRequest {
    data class NewSnippet(val categoryId: Long? = null) : LaunchRequest
    data class Edit(val id: Long) : LaunchRequest
    data object Settings : LaunchRequest
}

fun Intent?.toLaunchRequest(): LaunchRequest? {
    if (this == null) return null
    if (getBooleanExtra(MainActivity.EXTRA_OPEN_SETTINGS, false)) return LaunchRequest.Settings
    if (getBooleanExtra(MainActivity.EXTRA_NEW_SNIPPET, false)) {
        val categoryId = if (hasExtra(MainActivity.EXTRA_CATEGORY_ID)) {
            getLongExtra(MainActivity.EXTRA_CATEGORY_ID, -1).takeIf { it >= 0 }
        } else {
            null
        }
        return LaunchRequest.NewSnippet(categoryId)
    }
    if (hasExtra(MainActivity.EXTRA_EDIT_SNIPPET_ID)) {
        val id = getLongExtra(MainActivity.EXTRA_EDIT_SNIPPET_ID, -1)
        if (id >= 0) return LaunchRequest.Edit(id)
    }
    return null
}

class MainActivity : ComponentActivity() {

    private var launchRequest by mutableStateOf<LaunchRequest?>(null)
    private var fromKeyboard by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            launchRequest = intent.toLaunchRequest()
            fromKeyboard = intent.getBooleanExtra(EXTRA_FROM_KEYBOARD, false)
        } else {
            fromKeyboard = savedInstanceState.getBoolean(EXTRA_FROM_KEYBOARD, false)
        }
        setContent {
            PastilleTheme {
                PastilleApp(
                    launchRequest = launchRequest,
                    onLaunchRequestHandled = { launchRequest = null },
                    fromKeyboard = fromKeyboard,
                    onReturnToKeyboard = {
                        fromKeyboard = false
                        finish()
                    },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(EXTRA_FROM_KEYBOARD, fromKeyboard)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequest = intent.toLaunchRequest()
        fromKeyboard = intent.getBooleanExtra(EXTRA_FROM_KEYBOARD, false)
    }

    companion object {
        const val EXTRA_EDIT_SNIPPET_ID = "app.pastille.EXTRA_EDIT_SNIPPET_ID"
        const val EXTRA_NEW_SNIPPET = "app.pastille.EXTRA_NEW_SNIPPET"
        const val EXTRA_CATEGORY_ID = "app.pastille.EXTRA_CATEGORY_ID"
        const val EXTRA_OPEN_SETTINGS = "app.pastille.EXTRA_OPEN_SETTINGS"
        const val EXTRA_FROM_KEYBOARD = "app.pastille.EXTRA_FROM_KEYBOARD"
        val LAUNCH_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
}

@Composable
private fun PastilleApp(
    launchRequest: LaunchRequest?,
    onLaunchRequestHandled: () -> Unit,
    fromKeyboard: Boolean,
    onReturnToKeyboard: () -> Unit,
) {
    var editingId: Long? by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating: Boolean by rememberSaveable { mutableStateOf(false) }
    var newCategoryId: Long? by rememberSaveable { mutableStateOf<Long?>(null) }
    var showingSettings by rememberSaveable { mutableStateOf(false) }
    var pendingUndo by remember { mutableStateOf<SnippetRecord?>(null) }
    val context = LocalContext.current
    val repository = remember { SnippetRepository.forContext(context) }
    val screenStates = rememberSaveableStateHolder()

    LaunchedEffect(launchRequest) {
        when (launchRequest) {
            is LaunchRequest.NewSnippet -> {
                editingId = null
                creating = true
                newCategoryId = launchRequest.categoryId
            }
            is LaunchRequest.Edit -> {
                editingId = launchRequest.id
                creating = false
                newCategoryId = null
            }
            LaunchRequest.Settings -> {
                editingId = null
                creating = false
                newCategoryId = null
                showingSettings = true
                onLaunchRequestHandled()
                return@LaunchedEffect
            }
            null -> return@LaunchedEffect
        }
        showingSettings = false
        onLaunchRequestHandled()
    }

    fun closeEditor() {
        editingId = null
        creating = false
        newCategoryId = null
        if (fromKeyboard) onReturnToKeyboard()
    }

    fun closeSettings() {
        showingSettings = false
        if (fromKeyboard) onReturnToKeyboard()
    }

    BackHandler(enabled = editingId != null || creating || showingSettings) {
        if (editingId != null || creating) closeEditor() else if (showingSettings) closeSettings()
    }

    if (editingId != null || creating) {
        SnippetEditorScreen(
            snippetId = editingId,
            repository = repository,
            onDone = ::closeEditor,
            onDeleted = { pendingUndo = it; closeEditor() },
            initialCategoryId = newCategoryId,
        )
    } else if (showingSettings) {
        SettingsScreen(onBack = ::closeSettings)
    } else {
        // Keeps the list's open folder tab and search while the editor or settings are on screen.
        screenStates.SaveableStateProvider("list") {
            SnippetListScreen(
                repository = repository,
                onCreate = { categoryId ->
                    creating = true
                    newCategoryId = categoryId
                },
                onEdit = { snippet: SnippetRecord -> editingId = snippet.id },
                onOpenSettings = { showingSettings = true },
                deletedSnippet = pendingUndo,
                onDeletedShown = { pendingUndo = null },
            )
        }
    }
}
