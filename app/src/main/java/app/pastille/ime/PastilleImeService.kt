package app.pastille.ime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.pastille.MainActivity
import app.pastille.data.SnippetRepository
import app.pastille.model.SnippetRecord
import app.pastille.ui.theme.PastilleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PastilleImeService :
    InputMethodService(),
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val viewModelStoreHolder = ViewModelStore()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Bumped each time the keyboard opens, so the screenshot row picks up new ones.
    private val openCount = mutableIntStateOf(0)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = viewModelStoreHolder

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        val view = ComposeView(this)
        val decorView = window.window?.decorView
        if (decorView != null) {
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
        } else {
            view.setViewTreeLifecycleOwner(this)
            view.setViewTreeSavedStateRegistryOwner(this)
            view.setViewTreeViewModelStoreOwner(this)
        }
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        view.setContent {
            PastilleTheme {
                PanelHost()
            }
        }
        return view
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        openCount.intValue++
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStoreHolder.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    @Composable
    private fun PanelHost() {
        val context = LocalContext.current
        val repository = remember { SnippetRepository.forContext(context) }
        val flow = remember { repository.observeSnippets() }
        val snippets by flow.collectAsState(initial = emptyList())
        var screenshots by remember { mutableStateOf(emptyList<ScreenshotItem>()) }
        var canReadScreenshots by remember {
            mutableStateOf(ScreenshotReader.hasPermission(context))
        }

        LaunchedEffect(openCount.intValue) {
            val items = withContext(Dispatchers.IO) {
                ScreenshotReader.readRecent(context)
            }
            screenshots = items
            canReadScreenshots = ScreenshotReader.hasPermission(context)
        }

        KeyboardPanel(
            snippets = snippets,
            screenshots = screenshots,
            hasScreenshotPermission = canReadScreenshots,
            onSaveClipboard = ::saveClipboardAsSnippet,
            onOpenApp = ::openApp,
            onOpenPermissions = ::openApp,
            onBack = ::switchBack,
            onSnippetTap = ::pasteSnippet,
            onSnippetLongPress = ::openSnippetInEditor,
            onScreenshotTap = ::shareScreenshot,
        )
    }

    private fun pasteSnippet(snippet: SnippetRecord) {
        currentInputConnection?.commitText(snippet.text, 1)
        serviceScope.launch { SnippetRepository.forContext(this@PastilleImeService).recordUse(snippet.id) }
    }

    private fun openSnippetInEditor(snippet: SnippetRecord) {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_EDIT_SNIPPET_ID, snippet.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun saveClipboardAsSnippet() {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        val clip = clipboard.primaryClip ?: return
        val text = (0 until clip.itemCount)
            .mapNotNull { clip.getItemAt(it)?.coerceToText(this)?.toString() }
            .firstOrNull { it.isNotBlank() }
            ?: return
        serviceScope.launch {
            SnippetRepository.forContext(this@PastilleImeService).saveClipboardText(text)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@PastilleImeService, "Snippet saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareScreenshot(item: ScreenshotItem) {
        val connection = currentInputConnection
        val editorInfo = currentInputEditorInfo
        val mimeTypes = if (editorInfo != null) {
            EditorInfoCompat.getContentMimeTypes(editorInfo).toSet()
        } else {
            emptySet()
        }
        val mimeType = contentResolver.getType(item.uri) ?: "image/png"
        val acceptsImage = mimeTypes.any { ClipDescription.compareMimeTypes(mimeType, it) }
        val contentUri = try {
            copyToSharedCache(item, mimeType)
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't read that screenshot", Toast.LENGTH_SHORT).show()
            return
        }
        if (connection != null && editorInfo != null && acceptsImage) {
            val info = InputContentInfoCompat(
                contentUri,
                ClipDescription("Pastille screenshot", arrayOf(mimeType)),
                null,
            )
            val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
            if (InputConnectionCompat.commitContent(connection, editorInfo, info, flags, null)) {
                return
            }
        }
        // Only Pastille's own FileProvider URI travels with the clip's read grant;
        // a MediaStore URI would be unreadable to the app that pastes it.
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newUri(contentResolver, "Pastille screenshot", contentUri)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied, long-press to paste", Toast.LENGTH_SHORT).show()
    }

    private fun copyToSharedCache(item: ScreenshotItem, mimeType: String): Uri {
        val dir = File(cacheDir, "shared").apply { mkdirs() }
        val extension = if (mimeType == "image/jpeg") "jpg" else mimeType.substringAfter('/')
        val out = File(dir, "screenshot-${System.currentTimeMillis()}.$extension")
        contentResolver.openInputStream(item.uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return FileProvider.getUriForFile(this, "$packageName.fileprovider", out)
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun switchBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToPreviousInputMethod()
        } else {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
    }
}
